package org.jellyfin.androidtv.data.contentwarning

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Looks up content warnings for movies from DoesTheDogDie and (optionally) the IMDb Parents Guide
 * and merges them into a single report. Movies are identified by their IMDb id (preferred) or TMDB id.
 *
 * Every source is cached separately — including "not listed" answers — in memory and on disk to keep
 * the number of requests low. A failing source never hides the results of the other one.
 */
class ContentWarningRepository(
	context: Context,
	private val isImdbEnabled: () -> Boolean = { true },
	private val apiKey: String = BuildConfig.DTDD_API_KEY,
) {
	private val cacheDir = File(context.filesDir, CACHE_DIR)
	private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
	private val dtddMutex = Mutex()
	private val imdbMutex = Mutex()
	private val json = Json { ignoreUnknownKeys = true }
	private val dtddApi by lazy { DoesTheDogDieApi(apiKey) }
	private val imdbScraper by lazy { ImdbParentsGuideScraper() }

	@Volatile private var lastImdbRequest = 0L
	@Volatile private var imdbBlockedUntil = 0L

	val isConfigured get() = apiKey.isNotBlank()

	suspend fun getWarnings(item: BaseItemDto): ContentWarningResult = withContext(Dispatchers.IO) {
		val imdbId = item.providerId("Imdb")?.takeIf { it.startsWith("tt") }
		val tmdbId = item.providerId("Tmdb")?.toIntOrNull()
		val dtddKey = when {
			imdbId != null -> "dtdd_imdb_$imdbId"
			tmdbId != null -> "dtdd_tmdb_$tmdbId"
			else -> return@withContext ContentWarningResult.MissingIds
		}

		val (dtdd, imdb) = coroutineScope {
			val dtdd = async { loadDtdd(dtddKey, imdbId, tmdbId, item) }
			val imdb = async {
				if (imdbId != null && isImdbEnabled()) loadImdb(imdbId) else SourceResult.Skipped
			}
			dtdd.await() to imdb.await()
		}

		combine(dtdd, imdb)
	}

	private fun combine(dtdd: SourceResult, imdb: SourceResult): ContentWarningResult {
		val merged = ContentWarningMerger.merge(
			imdb = (imdb as? SourceResult.Found)?.report,
			dtdd = (dtdd as? SourceResult.Found)?.report,
		)
		if (merged != null) return ContentWarningResult.Available(merged)

		val results = listOf(dtdd, imdb).filter { it != SourceResult.Skipped }
		return when {
			results.any { it == SourceResult.Error } -> ContentWarningResult.Error
			results.any { it == SourceResult.NotFound } -> ContentWarningResult.NotFound
			else -> ContentWarningResult.NotConfigured
		}
	}

	private suspend fun loadDtdd(
		cacheKey: String,
		imdbId: String?,
		tmdbId: Int?,
		item: BaseItemDto,
	): SourceResult {
		// Serve cached data even without an API key, it may have been fetched by earlier builds.
		readCache(cacheKey)?.let { return it.toSourceResult() }
		if (!isConfigured) return SourceResult.NotConfigured

		return dtddMutex.withLock {
			// Another coroutine may have fetched the same movie while we waited.
			readCache(cacheKey)?.let { return@withLock it.toSourceResult() }

			try {
				val match = findMedia(imdbId, tmdbId, item)
				val report = match?.let { dtddApi.getReport(it.id, it.name ?: item.name) }
				storeAndReturn(cacheKey, report)
			} catch (error: DoesTheDogDieException) {
				Timber.w(error, "DoesTheDogDie lookup failed for %s", cacheKey)
				if (error.code == 401 || error.code == 403) SourceResult.NotConfigured else SourceResult.Error
			} catch (error: IOException) {
				Timber.w(error, "DoesTheDogDie lookup failed for %s", cacheKey)
				SourceResult.Error
			} catch (error: IllegalArgumentException) {
				// Malformed JSON (kotlinx.serialization reports parse errors as IAE subclasses).
				Timber.w(error, "DoesTheDogDie returned invalid data for %s", cacheKey)
				SourceResult.Error
			}
		}
	}

	private suspend fun loadImdb(imdbId: String): SourceResult {
		val cacheKey = "imdbpg_$imdbId"
		readCache(cacheKey)?.let { return it.toSourceResult() }
		if (System.currentTimeMillis() < imdbBlockedUntil) return SourceResult.Error

		return imdbMutex.withLock {
			readCache(cacheKey)?.let { return@withLock it.toSourceResult() }

			// Be polite: keep a minimum gap between scraping requests.
			val wait = lastImdbRequest + IMDB_MIN_INTERVAL_MS - System.currentTimeMillis()
			if (wait > 0) delay(wait)
			lastImdbRequest = System.currentTimeMillis()

			try {
				storeAndReturn(cacheKey, imdbScraper.getReport(imdbId))
			} catch (error: ImdbParentsGuideException) {
				Timber.w(error, "IMDb Parents Guide lookup failed for %s", imdbId)
				if (error.isBlocked) imdbBlockedUntil = System.currentTimeMillis() + IMDB_BACKOFF_MS
				SourceResult.Error
			} catch (error: IOException) {
				Timber.w(error, "IMDb Parents Guide lookup failed for %s", imdbId)
				SourceResult.Error
			} catch (error: IllegalArgumentException) {
				Timber.w(error, "IMDb Parents Guide returned invalid data for %s", imdbId)
				SourceResult.Error
			}
		}
	}

	private fun storeAndReturn(cacheKey: String, report: ContentWarningReport?): SourceResult {
		val entry = CacheEntry(
			version = CACHE_VERSION,
			fetchedAt = System.currentTimeMillis(),
			report = report?.takeIf { it.groups.isNotEmpty() },
		)
		writeCache(cacheKey, entry)
		return entry.toSourceResult()
	}

	private fun findMedia(imdbId: String?, tmdbId: Int?, item: BaseItemDto): DoesTheDogDieSearchItem? {
		if (imdbId != null) {
			dtddApi.searchByImdb(imdbId)
				.firstOrNull { it.imdbId.equals(imdbId, ignoreCase = true) }
				?.let { return it }
		}

		// No IMDb hit: search by title and match the TMDB id, then title + year.
		val title = item.originalTitle?.takeIf { it.isNotBlank() } ?: item.name ?: return null
		val candidates = buildList {
			addAll(dtddApi.searchByName(title))
			val name = item.name
			if (name != null && !name.equals(title, ignoreCase = true)) addAll(dtddApi.searchByName(name))
		}.distinctBy { it.id }
			.filter { it.itemType == null || it.itemType.contains("movie", ignoreCase = true) }

		if (tmdbId != null) candidates.firstOrNull { it.tmdbId == tmdbId }?.let { return it }
		if (imdbId != null) candidates.firstOrNull { it.imdbId.equals(imdbId, ignoreCase = true) }?.let { return it }

		val year = item.productionYear ?: return null
		return candidates.firstOrNull { candidate ->
			candidate.releaseYear == year &&
				(candidate.name.equals(item.name, ignoreCase = true) || candidate.name.equals(title, ignoreCase = true))
		}
	}

	private fun readCache(key: String): CacheEntry? {
		val entry = memoryCache[key] ?: readDiskCache(key)?.also { memoryCache[key] = it } ?: return null
		val maxAge = if (entry.report != null) FOUND_TTL else NOT_FOUND_TTL
		val expired = entry.version != CACHE_VERSION || System.currentTimeMillis() - entry.fetchedAt > maxAge
		if (expired) {
			memoryCache.remove(key)
			return null
		}
		return entry
	}

	private fun readDiskCache(key: String): CacheEntry? = try {
		File(cacheDir, "$key.json").takeIf { it.exists() }
			?.readText()
			?.let { json.decodeFromString<CacheEntry>(it) }
	} catch (error: Exception) {
		Timber.w(error, "Ignoring corrupt content warning cache %s", key)
		null
	}

	private fun writeCache(key: String, entry: CacheEntry) {
		memoryCache[key] = entry
		try {
			cacheDir.mkdirs()
			File(cacheDir, "$key.json").writeText(json.encodeToString(entry))
		} catch (error: IOException) {
			Timber.w(error, "Unable to persist content warning cache %s", key)
		}
	}

	private fun CacheEntry.toSourceResult(): SourceResult =
		report?.let { SourceResult.Found(it) } ?: SourceResult.NotFound

	private fun BaseItemDto.providerId(name: String): String? = providerIds
		?.entries
		?.firstOrNull { it.key.equals(name, ignoreCase = true) }
		?.value
		?.trim()
		?.takeIf { it.isNotBlank() }

	private sealed interface SourceResult {
		data class Found(val report: ContentWarningReport) : SourceResult
		data object NotFound : SourceResult
		data object NotConfigured : SourceResult
		data object Error : SourceResult
		data object Skipped : SourceResult
	}

	@Serializable
	private data class CacheEntry(
		val version: Int,
		val fetchedAt: Long,
		val report: ContentWarningReport? = null,
	)

	private companion object {
		const val CACHE_DIR = "content_warnings"
		const val CACHE_VERSION = 2
		val FOUND_TTL = TimeUnit.DAYS.toMillis(30)
		val NOT_FOUND_TTL = TimeUnit.DAYS.toMillis(3)
		const val IMDB_MIN_INTERVAL_MS = 2_000L
		val IMDB_BACKOFF_MS = TimeUnit.MINUTES.toMillis(15)
	}
}

