package org.jellyfin.androidtv.data.sdns

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** A row of `public.movies` in ScreenDiceNSpice (only the columns this client needs). */
data class SdnsMovie(
	val id: String,
	val title: String,
	val year: Int?,
	val imdbId: String?,
	val seen: Boolean,
	val rating: Double?,
)

/** A row of `public.profiles` in ScreenDiceNSpice. */
data class SdnsProfile(
	val id: String,
	val name: String,
)

/** State of a movie inside ScreenDiceNSpice. */
enum class SdnsStatus {
	/** Not part of the profile's collection. */
	NOT_FOUND,

	/** `seen = false` */
	WATCHLIST,

	/** `seen = true AND rating IS NULL` */
	SEEN,

	/** `seen = true AND rating IS NOT NULL` */
	RATED,
}

/** The movie that should be rated, as known by Jellyfin. */
data class SdnsMovieQuery(
	val title: String,
	val year: Int?,
	val imdbId: String?,
)

/**
 * Pure matching and status logic, mirrors the behaviour of the ScreenDiceNSpice app.
 */
object SdnsLogic {
	const val MIN_RATING = 0.0
	const val MAX_RATING = 10.0
	const val RATING_STEP = 0.5
	private const val YEAR_TOLERANCE = 1

	fun status(movie: SdnsMovie?): SdnsStatus = when {
		movie == null -> SdnsStatus.NOT_FOUND
		!movie.seen -> SdnsStatus.WATCHLIST
		movie.rating != null -> SdnsStatus.RATED
		else -> SdnsStatus.SEEN
	}

	/** Validates a rating (0–10) and rounds it to one decimal place (`numeric(3,1)`). */
	fun validateRating(rating: Double): Double {
		require(!rating.isNaN() && rating in MIN_RATING..MAX_RATING) { "Die Bewertung muss zwischen 0 und 10 liegen" }
		return (rating * 10).roundToLong() / 10.0
	}

	/** Snaps a rating to the 0,5 steps of the rating widget. */
	fun snapToStep(rating: Double): Double =
		((rating / RATING_STEP).roundToLong() * RATING_STEP).coerceIn(MIN_RATING, MAX_RATING)

	/* Star scale of the UI (0,5–5 in 0,5 steps), stored as 0–10 like in ScreenDiceNSpice. */
	const val MIN_STARS = 0.5
	const val MAX_STARS = 5.0
	const val STAR_STEP = 0.5

	/** Same as `ratingToStars()` in ScreenDiceNSpice: 0–10 -> 0–5 stars in 0,5 steps (8.6 -> 4.5). */
	fun ratingToStars(rating: Double?): Double {
		if (rating == null) return 0.0
		return (Math.round(rating) / 2.0).coerceIn(0.0, MAX_STARS)
	}

	/** Same as `starsToRating()` in ScreenDiceNSpice: 0–5 stars -> 0–10. */
	fun starsToRating(stars: Double): Double = (stars * 2).coerceIn(MIN_RATING, MAX_RATING)

	/** Clamps and snaps a star value to the allowed 0,5–5 range. */
	fun clampStars(stars: Double): Double =
		((stars / STAR_STEP).roundToLong() * STAR_STEP).coerceIn(MIN_STARS, MAX_STARS)

	/** IMDb ids are only valid with the `tt` prefix. */
	fun normalizeImdbId(imdbId: String?): String? = imdbId
		?.trim()
		?.lowercase(Locale.ROOT)
		?.takeIf { it.matches(Regex("tt\\d+")) }

	/** Lowercase, without accents, punctuation and duplicate whitespace. */
	fun normalizeTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFD)
		.replace(Regex("\\p{M}+"), "")
		.lowercase(Locale.ROOT)
		.replace("&", " and ")
		.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
		.trim()

	/**
	 * Finds the movie matching [query] in [candidates].
	 *
	 * 1. By IMDb id.
	 * 2. Fallback: normalized title and year with ±1 year tolerance. Candidates with a different
	 *    IMDb id are never matched by title.
	 */
	fun match(candidates: List<SdnsMovie>, query: SdnsMovieQuery): SdnsMovie? {
		val imdbId = normalizeImdbId(query.imdbId)
		if (imdbId != null) {
			candidates.firstOrNull { normalizeImdbId(it.imdbId) == imdbId }?.let { return it }
		}

		val title = normalizeTitle(query.title)
		if (title.isEmpty()) return null

		return candidates
			.asSequence()
			.filter { normalizeTitle(it.title) == title }
			.filter { candidate ->
				val candidateImdb = normalizeImdbId(candidate.imdbId)
				imdbId == null || candidateImdb == null || candidateImdb == imdbId
			}
			.filter { query.year == null || it.year == null || abs(it.year - query.year) <= YEAR_TOLERANCE }
			.minByOrNull { candidate ->
				if (query.year != null && candidate.year != null) abs(candidate.year - query.year) else YEAR_TOLERANCE + 1
			}
	}

	/** Rating a movie always marks it as seen. */
	fun ratePatch(rating: Double): Map<String, Any?> = mapOf("rating" to validateRating(rating), "seen" to true)

	/** Watchlist -> seen, keeps an existing rating. */
	fun seenPatch(): Map<String, Any?> = mapOf("seen" to true)

	/** Back to the watchlist. */
	fun watchlistPatch(): Map<String, Any?> = mapOf("seen" to false, "rating" to null)

	/**
	 * Same as `slugify()` in ScreenDiceNSpice (`lib/utils.ts`): lowercase, without accents,
	 * non alphanumerics replaced by `-`, plus a random 5 character base36 suffix.
	 */
	fun slugify(title: String, random: kotlin.random.Random = kotlin.random.Random.Default): String {
		val base = Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
			.replace(Regex("[\\u0300-\\u036f]"), "")
			.replace(Regex("[^a-z0-9]+"), "-")
			.trim('-')
		val suffix = (1..SLUG_SUFFIX_LENGTH).map { SLUG_ALPHABET[random.nextInt(SLUG_ALPHABET.length)] }.joinToString("")
		return "$base-$suffix"
	}

	private const val SLUG_SUFFIX_LENGTH = 5
	private const val SLUG_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

	/** Payload for a movie that does not exist in the profile yet. */
	fun insertPayload(profileId: String, query: SdnsMovieQuery, rating: Double?, slug: String = slugify(query.title)): Map<String, Any?> = buildMap {
		put("profile_id", profileId)
		put("title", query.title)
		put("slug", slug)
		put("year", query.year)
		put("imdb_id", normalizeImdbId(query.imdbId))
		putAll(if (rating == null) seenPatch() else ratePatch(rating))
	}
}

