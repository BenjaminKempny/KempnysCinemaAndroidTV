package org.jellyfin.androidtv.ui.cinema

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

/** The media type the cinema home is currently scoped to (web: `media` query param). */
enum class CinemaMediaType(val itemKind: BaseItemKind, val collectionType: CollectionType) {
	Movies(BaseItemKind.MOVIE, CollectionType.MOVIES),
	Shows(BaseItemKind.SERIES, CollectionType.TVSHOWS),
}

/** The active segment of the `All | Collections | Genres` control (web: `view` query param). */
enum class CinemaView { All, Collections, Genres }

/** Sort options offered by the catalog toolbar (web: `name` / `added` / `year`). */
enum class CinemaSort(val sortBy: ItemSortBy, val sortOrder: SortOrder) {
	Name(ItemSortBy.SORT_NAME, SortOrder.ASCENDING),
	Added(ItemSortBy.DATE_CREATED, SortOrder.DESCENDING),
	Year(ItemSortBy.PREMIERE_DATE, SortOrder.DESCENDING),
}

/** One horizontal genre rail. */
data class CinemaGenreRow(
	val name: String,
	val items: List<BaseItemDto>,
)

data class CinemaHomeState(
	val mediaType: CinemaMediaType = CinemaMediaType.Movies,
	val view: CinemaView = CinemaView.All,
	val sort: CinemaSort = CinemaSort.Name,
	val loading: Boolean = true,
	val hero: List<CinemaHeroItem> = emptyList(),
	val continueWatching: List<BaseItemDto> = emptyList(),
	val catalog: List<BaseItemDto> = emptyList(),
	val catalogTotal: Int = 0,
	val catalogLoadingMore: Boolean = false,
	val collections: List<BaseItemDto> = emptyList(),
	val genreRows: List<CinemaGenreRow> = emptyList(),
	val libraryId: UUID? = null,
) {
	val catalogHasMore get() = catalog.size < catalogTotal
}

/**
 * Data layer for the cinema home screen. Mirrors the web queries in
 * `src/apps/modern/features/home/api.ts` and `librarySource.ts`.
 */
class CinemaHomeViewModel(
	private val api: ApiClient,
	private val userViewsRepository: UserViewsRepository,
	private val imageHelper: ImageHelper,
) : ViewModel() {
	private val _state = MutableStateFlow(CinemaHomeState())
	val state: StateFlow<CinemaHomeState> = _state.asStateFlow()

	private var loadJob: Job? = null
	private var pageJob: Job? = null

	init {
		load()
	}

	fun setMediaType(mediaType: CinemaMediaType) {
		if (_state.value.mediaType == mediaType) return
		_state.update { it.copy(mediaType = mediaType) }
		load()
	}

	fun setView(view: CinemaView) {
		if (_state.value.view == view) return
		_state.update { it.copy(view = view) }
		load()
	}

	fun setSort(sort: CinemaSort) {
		if (_state.value.sort == sort) return
		_state.update { it.copy(sort = sort) }
		load()
	}

	fun refresh() = load()

	/**
	 * Refreshes only the Continue Watching row. Called when returning from playback or a
	 * detail screen — a full reload would reset the scroll position and the focus memory
	 * (spec §2.7) and re-randomise the spotlight.
	 */
	fun refreshContinueWatching() {
		val snapshot = _state.value
		if (snapshot.view != CinemaView.All) return

		viewModelScope.launch(Dispatchers.IO) {
			try {
				val resume = loadContinueWatching(snapshot.libraryId)
				_state.update { it.copy(continueWatching = resume) }
			} catch (error: ApiClientException) {
				Timber.w(error, "Failed to refresh continue watching")
			}
		}
	}

	private fun load() {
		loadJob?.cancel()
		pageJob?.cancel()
		_state.update { it.copy(loading = true, catalogLoadingMore = false) }

		loadJob = viewModelScope.launch(Dispatchers.IO) {
			val snapshot = _state.value
			try {
				val libraryId = resolveLibraryId(snapshot.mediaType)

				when (snapshot.view) {
					CinemaView.All -> {
						val hero = loadHero(libraryId, snapshot.mediaType)
						val resume = loadContinueWatching(libraryId)
						val page = loadCatalogPage(libraryId, snapshot, startIndex = 0)

						_state.update {
							it.copy(
								loading = false,
								libraryId = libraryId,
								hero = hero,
								continueWatching = resume,
								catalog = page.items,
								catalogTotal = page.total,
								collections = emptyList(),
								genreRows = emptyList(),
							)
						}
					}

					CinemaView.Collections -> {
						val collections = loadCollections()
						_state.update {
							it.copy(
								loading = false,
								libraryId = libraryId,
								collections = collections,
								hero = emptyList(),
								continueWatching = emptyList(),
								catalog = emptyList(),
								genreRows = emptyList(),
							)
						}
					}

					CinemaView.Genres -> {
						val rows = loadGenreRows(libraryId, snapshot.mediaType)
						_state.update {
							it.copy(
								loading = false,
								libraryId = libraryId,
								genreRows = rows,
								hero = emptyList(),
								continueWatching = emptyList(),
								catalog = emptyList(),
								collections = emptyList(),
							)
						}
					}
				}
			} catch (error: ApiClientException) {
				Timber.w(error, "Failed to load cinema home")
				_state.update { it.copy(loading = false) }
			}
		}
	}

	/** Infinite scroll — appends the next catalog page (web: `InfiniteScroll.tsx`). */
	fun loadNextCatalogPage() {
		val snapshot = _state.value
		if (snapshot.loading || loadJob?.isActive == true) return
		if (snapshot.catalogLoadingMore || !snapshot.catalogHasMore) return
		if (pageJob?.isActive == true) return

		_state.update { it.copy(catalogLoadingMore = true) }
		pageJob = viewModelScope.launch(Dispatchers.IO) {
			try {
				val startIndex = snapshot.catalog.size
				val page = loadCatalogPage(snapshot.libraryId, snapshot, startIndex = startIndex)
				_state.update { current ->
					// A full reload may have replaced the catalog while this page was in
					// flight — appending then would corrupt the order, so drop the result.
					val stillValid = current.view == snapshot.view &&
						current.sort == snapshot.sort &&
						current.mediaType == snapshot.mediaType &&
						current.catalog.size == startIndex

					if (stillValid) {
						current.copy(
							catalog = current.catalog + page.items,
							catalogTotal = page.total,
							catalogLoadingMore = false,
						)
					} else {
						current.copy(catalogLoadingMore = false)
					}
				}
			} catch (error: ApiClientException) {
				Timber.w(error, "Failed to load next cinema catalog page")
				_state.update { it.copy(catalogLoadingMore = false) }
			}
		}
	}

	private suspend fun resolveLibraryId(mediaType: CinemaMediaType): UUID? = runCatching {
		userViewsRepository.views.first()
			.firstOrNull { it.collectionType == mediaType.collectionType }
			?.id
	}.getOrNull()

	private suspend fun loadHero(libraryId: UUID?, mediaType: CinemaMediaType): List<CinemaHeroItem> {
		val result by api.itemsApi.getItems(
			parentId = libraryId,
			includeItemTypes = setOf(mediaType.itemKind),
			recursive = true,
			sortBy = setOf(ItemSortBy.RANDOM),
			fields = ItemRepository.browseFields,
			imageTypeLimit = 1,
			limit = HERO_LIMIT,
			enableTotalRecordCount = false,
		)

		return result.items.map { item -> item.toHeroItem() }
	}

	private suspend fun loadContinueWatching(libraryId: UUID?): List<BaseItemDto> {
		val result by api.itemsApi.getResumeItems(
			parentId = libraryId,
			limit = RESUME_LIMIT,
			fields = ItemRepository.browseFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			mediaTypes = setOf(MediaType.VIDEO),
			excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
		)
		return result.items
	}

	private data class Page(val items: List<BaseItemDto>, val total: Int)

	private suspend fun loadCatalogPage(
		libraryId: UUID?,
		snapshot: CinemaHomeState,
		startIndex: Int,
	): Page {
		val result by api.itemsApi.getItems(
			parentId = libraryId,
			includeItemTypes = setOf(snapshot.mediaType.itemKind),
			recursive = true,
			sortBy = setOf(snapshot.sort.sortBy),
			sortOrder = setOf(snapshot.sort.sortOrder),
			fields = ItemRepository.browseFields,
			imageTypeLimit = 1,
			startIndex = startIndex,
			limit = CATALOG_PAGE_SIZE,
		)

		return Page(result.items, result.totalRecordCount)
	}

	private suspend fun loadCollections(): List<BaseItemDto> {
		val result by api.itemsApi.getItems(
			includeItemTypes = setOf(BaseItemKind.BOX_SET),
			recursive = true,
			sortBy = setOf(ItemSortBy.SORT_NAME),
			fields = ItemRepository.browseFields,
			imageTypeLimit = 1,
			limit = COLLECTION_LIMIT,
		)
		return result.items
	}

	/**
	 * Genre rails: at most 12 genres, 20 items each, sorted by premiere date descending
	 * (web: `genreRows.ts:24-25`).
	 */
	private suspend fun loadGenreRows(libraryId: UUID?, mediaType: CinemaMediaType): List<CinemaGenreRow> {
		val genres by api.itemsApi.getItems(
			parentId = libraryId,
			includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			recursive = true,
			fields = ItemRepository.browseFields,
			limit = GENRE_SAMPLE_SIZE,
			sortBy = setOf(ItemSortBy.RANDOM),
			enableTotalRecordCount = false,
		)

		val genreNames = genres.items
			.flatMap { it.genres.orEmpty() }
			.groupingBy { it }
			.eachCount()
			.entries
			.sortedByDescending { it.value }
			.take(GENRE_ROW_LIMIT)
			.map { it.key }
			.sorted()

		return genreNames.mapNotNull { genre ->
			runCatching {
				val result by api.itemsApi.getItems(
					parentId = libraryId,
					includeItemTypes = setOf(mediaType.itemKind),
					recursive = true,
					genres = listOf(genre),
					sortBy = setOf(ItemSortBy.PREMIERE_DATE),
					sortOrder = setOf(SortOrder.DESCENDING),
					fields = ItemRepository.browseFields,
					imageTypeLimit = 1,
					limit = GENRE_ITEM_LIMIT,
					enableTotalRecordCount = false,
				)
				CinemaGenreRow(genre, result.items)
			}.getOrNull()?.takeIf { it.items.isNotEmpty() }
		}
	}

	/** Fetches the full item before navigating to playback, matching the web behaviour. */
	suspend fun getFullItem(id: UUID): BaseItemDto? = runCatching {
		val item by api.userLibraryApi.getItem(itemId = id)
		item
	}.getOrNull()

	fun posterUrl(item: BaseItemDto): String? =
		imageHelper.getPrimaryImageUrl(item, preferParentThumb = false, fillWidth = POSTER_IMAGE_WIDTH)

	fun thumbUrl(item: BaseItemDto): String? =
		imageHelper.getPrimaryImageUrl(item, preferParentThumb = true, fillWidth = THUMB_IMAGE_WIDTH)

	/** Hero uses the backdrop and only falls back to the poster when none is available. */
	private fun heroBackdropUrl(item: BaseItemDto): String? {
		val backdrop = item.itemBackdropImages.firstOrNull() ?: item.parentBackdropImages.firstOrNull()
		return backdrop?.getUrl(api, maxWidth = HERO_IMAGE_WIDTH)
			?: imageHelper.getPrimaryImageUrl(item, preferParentThumb = false, fillWidth = HERO_IMAGE_WIDTH)
	}

	private fun BaseItemDto.toHeroItem(): CinemaHeroItem {
		val tags = buildList {
			addAll(genres.orEmpty().take(HERO_TAG_LIMIT))
			productionYear?.let { add(it.toString()) }
			officialRating?.let { add(it) }
		}

		return CinemaHeroItem(
			id = id,
			title = name.orEmpty(),
			overview = overview,
			backdropUrl = heroBackdropUrl(this),
			tags = tags,
			resumable = (userData?.playbackPositionTicks ?: 0L) > 0L,
		)
	}

	private companion object {
		const val HERO_LIMIT = 8
		const val HERO_TAG_LIMIT = 3
		const val RESUME_LIMIT = 20
		const val CATALOG_PAGE_SIZE = 60
		const val COLLECTION_LIMIT = 200
		const val GENRE_ROW_LIMIT = 12
		const val GENRE_ITEM_LIMIT = 20
		const val GENRE_SAMPLE_SIZE = 300
		const val POSTER_IMAGE_WIDTH = 360
		const val THUMB_IMAGE_WIDTH = 520
		const val HERO_IMAGE_WIDTH = 1600
	}
}

/** Playback progress of an item as a 0..1 fraction, or `null` when it was never started. */
val BaseItemDto.cinemaProgress: Float?
	get() {
		val position = userData?.playbackPositionTicks ?: return null
		val total = runTimeTicks ?: return null
		if (position <= 0L || total <= 0L) return null
		return (position.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
	}

/** Card subtitle: episodes render as `S1 E2 / Title`, everything else shows the year. */
val BaseItemDto.cinemaSubtitle: String?
	get() = when (type) {
		BaseItemKind.EPISODE -> buildString {
			parentIndexNumber?.let { append("S").append(it) }
			indexNumber?.let {
				if (isNotEmpty()) append(" ")
				append("E").append(it)
			}
			name?.takeIf { it.isNotBlank() }?.let {
				if (isNotEmpty()) append(" / ")
				append(it)
			}
		}.takeIf { it.isNotBlank() }

		else -> productionYear?.toString()
	}

/** Card title: episodes show the series name, everything else its own name. */
val BaseItemDto.cinemaTitle: String
	get() = when (type) {
		BaseItemKind.EPISODE -> seriesName ?: name.orEmpty()
		else -> name.orEmpty()
	}
