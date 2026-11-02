package org.jellyfin.androidtv.ui.cinema

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import timber.log.Timber
import java.time.Year
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

/**
 * The four quiz questions (mood, company, duration, era) followed by the shuffle animation and
 * the result. [Genres] is the optional "pick genres yourself" detour of the mood question.
 */
enum class RecommendationStep { Mood, Genres, Company, Duration, Era, Loading, Result }

/** `true` when the (server language specific) genre name contains one of the [keywords]. */
private fun genreMatches(genre: String, keywords: List<String>): Boolean {
	val name = genre.lowercase()
	return keywords.any { it in name }
}

private fun BaseItemDto.hasGenreLike(keywords: List<String>): Boolean =
	keywords.isNotEmpty() && genres.orEmpty().any { genreMatches(it, keywords) }

/**
 * Question 1: a mood instead of a raw genre list. Every mood maps to the genres of the library
 * via keywords, so it works with English and German genre names alike.
 */
enum class RecommendationMood(
	@StringRes val label: Int,
	@StringRes val hint: Int,
	val emoji: String,
	private val keywords: List<String>,
) {
	Laugh(R.string.reco_mood_laugh, R.string.reco_mood_laugh_hint, "😂", listOf("kom", "comed")),
	Thrill(R.string.reco_mood_thrill, R.string.reco_mood_thrill_hint, "😱", listOf("thrill", "krimi", "crime", "myster")),
	Scare(R.string.reco_mood_scare, R.string.reco_mood_scare_hint, "👻", listOf("horror")),
	Action(R.string.reco_mood_action, R.string.reco_mood_action_hint, "💥", listOf("action", "abenteuer", "advent")),
	Wonder(R.string.reco_mood_wonder, R.string.reco_mood_wonder_hint, "🌌", listOf("science", "sci-fi", "fantasy")),
	Feel(R.string.reco_mood_feel, R.string.reco_mood_feel_hint, "🥹", listOf("drama", "roman", "liebe")),
	Learn(R.string.reco_mood_learn, R.string.reco_mood_learn_hint, "📚", listOf("dokument", "documentary", "histor", "biogra"));

	/** The library genres that belong to this mood. */
	fun genresIn(available: List<String>): Set<String> =
		available.filter { genreMatches(it, keywords) }.toSet()
}

/**
 * Question 2: who is watching. Steers the weighting towards fitting genres; [Kids] additionally
 * applies a hard age limit that is never relaxed.
 */
enum class RecommendationCompany(
	@StringRes val label: Int,
	@StringRes val hint: Int,
	val emoji: String,
	private val boost: List<String>,
	private val avoid: List<String> = emptyList(),
	val maxOfficialRating: String? = null,
) {
	Alone(R.string.reco_company_alone, R.string.reco_company_alone_hint, "🧑", emptyList()),
	Couple(R.string.reco_company_couple, R.string.reco_company_couple_hint, "💑", listOf("roman", "liebe", "kom", "comed", "drama")),
	Friends(R.string.reco_company_friends, R.string.reco_company_friends_hint, "🍻", listOf("kom", "comed", "action", "horror", "abenteuer", "advent")),
	Kids(
		R.string.reco_company_kids,
		R.string.reco_company_kids_hint,
		"🧒",
		boost = listOf("famil", "anim", "kinder", "kids", "zeichentrick"),
		avoid = listOf("horror", "thrill", "erot", "krimi", "crime", "krieg", "war"),
		maxOfficialRating = "PG",
	);

	fun allows(item: BaseItemDto): Boolean = !item.hasGenreLike(avoid)

	/** 0..1: how well the movie fits the audience; neutral when there is no preference. */
	fun fit(item: BaseItemDto): Double = when {
		boost.isEmpty() -> 0.5
		item.hasGenreLike(boost) -> 1.0
		else -> 0.3
	}
}

/** Question 3. Movies without a known runtime are kept but ranked lower. */
enum class RecommendationDuration(
	@StringRes val label: Int,
	@StringRes val hint: Int,
	val emoji: String,
	private val minMinutes: Int?,
	private val maxMinutes: Int?,
) {
	Quick(R.string.reco_duration_short, R.string.reco_duration_short_hint, "⚡", null, 95),
	Normal(R.string.reco_duration_normal, R.string.reco_duration_normal_hint, "🍿", 85, 130),
	Epic(R.string.reco_duration_long, R.string.reco_duration_long_hint, "🛋️", 125, null),
	Whatever(R.string.reco_any, R.string.reco_any_hint, "🤷", null, null);

	fun matches(minutes: Int?): Boolean {
		if (this == Whatever || minutes == null) return true
		if (minMinutes != null && minutes < minMinutes) return false
		if (maxMinutes != null && minutes > maxMinutes) return false
		return true
	}
}

/** Question 4: a rough era instead of single decades. */
enum class RecommendationEra(
	@StringRes val label: Int,
	val emoji: String,
	val years: IntRange?,
) {
	New(R.string.reco_era_new, "🚀", (Year.now().value - 2)..(Year.now().value + 1)),
	Modern(R.string.reco_era_modern, "📱", 2000..(Year.now().value + 1)),
	Classic(R.string.reco_era_classic, "📼", 1900..1999),
	Whatever(R.string.reco_any, "🎲", null),
}

/** Filters that had to be dropped because the strict selection found too few movies. */
enum class RelaxedFilter(@StringRes val label: Int) {
	Era(R.string.reco_relaxed_era),
	Duration(R.string.reco_relaxed_duration),
	Genre(R.string.reco_relaxed_genre),
}

data class Recommendation(
	val item: BaseItemDto,
	/** Selected genres the movie actually has — shown as "passt zu" chips. */
	val matchedGenres: List<String>,
)

data class RecommendationState(
	val step: RecommendationStep = RecommendationStep.Mood,
	val genresLoading: Boolean = true,
	val availableGenres: List<String> = emptyList(),
	/** `null` for "surprise me" or when the genres were picked manually. */
	val mood: RecommendationMood? = null,
	/** The effective genre filter, derived from [mood] or picked manually. */
	val selectedGenres: Set<String> = emptySet(),
	val company: RecommendationCompany? = null,
	val duration: RecommendationDuration? = null,
	val era: RecommendationEra? = null,
	val results: List<Recommendation> = emptyList(),
	val relaxed: List<RelaxedFilter> = emptyList(),
) {
	/** Only moods that exist in the library are offered. */
	val availableMoods: List<RecommendationMood>
		get() = RecommendationMood.entries.filter { it.genresIn(availableGenres).isNotEmpty() }

	/** 1-based question number for the progress indicator, `null` outside the quiz. */
	val questionNumber: Int?
		get() = when (step) {
			RecommendationStep.Mood, RecommendationStep.Genres -> 1
			RecommendationStep.Company -> 2
			RecommendationStep.Duration -> 3
			RecommendationStep.Era -> 4
			else -> null
		}
}

/**
 * Movie recommendation quiz. The answers narrow the library down, then three movies are
 * picked that fit the answers but differ from each other as much as possible
 * (genres, year, runtime, rating) — a greedy maximal marginal relevance selection.
 */
class CinemaRecommendationViewModel(
	private val api: ApiClient,
	private val userViewsRepository: UserViewsRepository,
) : ViewModel() {
	private val _state = MutableStateFlow(RecommendationState())
	val state: StateFlow<RecommendationState> = _state.asStateFlow()

	private var libraryId: UUID? = null
	private var candidates: List<BaseItemDto> = emptyList()
	private val shownIds = mutableSetOf<UUID>()
	private var searchJob: Job? = null

	init {
		loadGenres()
	}

	// region quiz input

	/** `null` means "surprise me". */
	fun selectMood(mood: RecommendationMood?) = _state.update {
		it.copy(
			mood = mood,
			selectedGenres = mood?.genresIn(it.availableGenres).orEmpty(),
			step = RecommendationStep.Company,
		)
	}

	/** Opens the manual genre picker, pre-filled with the genres of the current mood. */
	fun openGenres() = _state.update { it.copy(mood = null, step = RecommendationStep.Genres) }

	fun toggleGenre(genre: String) = _state.update {
		it.copy(selectedGenres = if (genre in it.selectedGenres) it.selectedGenres - genre else it.selectedGenres + genre)
	}

	fun clearGenres() = _state.update { it.copy(selectedGenres = emptySet()) }

	fun selectCompany(company: RecommendationCompany) =
		_state.update { it.copy(company = company, step = RecommendationStep.Duration) }

	fun selectDuration(duration: RecommendationDuration) =
		_state.update { it.copy(duration = duration, step = RecommendationStep.Era) }

	fun selectEra(era: RecommendationEra) {
		_state.update { it.copy(era = era) }
		search()
	}

	/** Only the multi-select genre picker needs an explicit "next". */
	fun next() {
		if (_state.value.step == RecommendationStep.Genres) {
			_state.update { it.copy(step = RecommendationStep.Company) }
		}
	}

	/** @return `false` when there is no previous step and the screen should close. */
	fun previous(): Boolean {
		val previous = when (_state.value.step) {
			RecommendationStep.Mood -> return false
			RecommendationStep.Genres, RecommendationStep.Company -> RecommendationStep.Mood
			RecommendationStep.Duration -> RecommendationStep.Company
			RecommendationStep.Era -> RecommendationStep.Duration
			RecommendationStep.Loading, RecommendationStep.Result -> {
				searchJob?.cancel()
				RecommendationStep.Era
			}
		}
		_state.update { it.copy(step = previous) }
		return true
	}

	fun restart() {
		searchJob?.cancel()
		candidates = emptyList()
		shownIds.clear()
		_state.update {
			RecommendationState(genresLoading = it.genresLoading, availableGenres = it.availableGenres)
		}
	}

	// endregion

	// region search

	/** Picks three new movies from the same candidate pool, excluding everything shown so far. */
	fun reshuffle() {
		if (candidates.isEmpty()) return
		searchJob?.cancel()
		searchJob = viewModelScope.launch {
			_state.update { it.copy(step = RecommendationStep.Loading) }
			delay(SHUFFLE_ANIMATION_MS)
			var pool = candidates.filter { it.id !in shownIds }
			// Everything was shown once: start over instead of showing fewer cards.
			if (pool.size < RESULT_COUNT) {
				shownIds.clear()
				pool = candidates
			}
			publish(pool)
		}
	}

	private fun search() {
		searchJob?.cancel()
		shownIds.clear()
		_state.update { it.copy(step = RecommendationStep.Loading, results = emptyList(), relaxed = emptyList()) }

		searchJob = viewModelScope.launch {
			val started = System.currentTimeMillis()
			val (pool, relaxed) = withContext(Dispatchers.IO) { findCandidates(_state.value) }
			candidates = pool

			// Keep the shuffle animation visible for a moment, it is part of the fun.
			val elapsed = System.currentTimeMillis() - started
			if (elapsed < SHUFFLE_ANIMATION_MS) delay(SHUFFLE_ANIMATION_MS - elapsed)

			_state.update { it.copy(relaxed = relaxed) }
			publish(pool)
		}
	}

	private fun publish(pool: List<BaseItemDto>) {
		val snapshot = _state.value
		val picked = pickDiverse(pool, snapshot)
		shownIds += picked.map { it.id }
		_state.update {
			it.copy(
				step = RecommendationStep.Result,
				results = picked.map { item ->
					Recommendation(
						item = item,
						matchedGenres = item.genres.orEmpty().filter { genre -> genre in snapshot.selectedGenres },
					)
				},
			)
		}
	}

	/**
	 * Runs the strict query first and drops filters one by one (era -> duration -> genre)
	 * until enough movies are found. The age limit for kids is never dropped.
	 */
	private suspend fun findCandidates(state: RecommendationState): Pair<List<BaseItemDto>, List<RelaxedFilter>> {
		val library = resolveLibraryId()
		val relaxed = mutableListOf<RelaxedFilter>()
		val relaxOrder = buildList {
			if (state.era?.years != null) add(RelaxedFilter.Era)
			if (state.duration != null && state.duration != RecommendationDuration.Whatever) add(RelaxedFilter.Duration)
			if (state.selectedGenres.isNotEmpty()) add(RelaxedFilter.Genre)
		}

		var result = query(library, state, relaxed)
		for (filter in relaxOrder) {
			if (result.size >= RESULT_COUNT) break
			relaxed += filter
			// Keep what was already found; it matches more of the answers.
			result = (result + query(library, state, relaxed)).distinctBy { it.id }
		}
		return result to relaxed
	}

	private suspend fun query(
		library: UUID?,
		state: RecommendationState,
		relaxed: List<RelaxedFilter>,
	): List<BaseItemDto> = try {
		val genres = state.selectedGenres.takeUnless { RelaxedFilter.Genre in relaxed }.orEmpty()
		val years = state.era?.years.takeUnless { RelaxedFilter.Era in relaxed }?.toList().orEmpty()
		val duration = state.duration.takeUnless { RelaxedFilter.Duration in relaxed } ?: RecommendationDuration.Whatever
		val company = state.company ?: RecommendationCompany.Alone

		val response by api.itemsApi.getItems(
			parentId = library,
			includeItemTypes = setOf(BaseItemKind.MOVIE),
			collapseBoxSetItems = false,
			recursive = true,
			genres = genres.toList(),
			years = years,
			maxOfficialRating = company.maxOfficialRating,
			sortBy = setOf(ItemSortBy.RANDOM),
			fields = ItemRepository.browseFields,
			imageTypeLimit = 1,
			limit = CANDIDATE_LIMIT,
			enableTotalRecordCount = false,
		)
		response.items.filter {
			it.type == BaseItemKind.MOVIE && duration.matches(it.runtimeMinutes) && company.allows(it)
		}
	} catch (error: ApiClientException) {
		Timber.w(error, "Failed to query recommendation candidates")
		emptyList()
	}

	/**
	 * Greedy maximal marginal relevance: the first pick is a well-fitting movie (with a bit
	 * of randomness so every round feels fresh), every further pick balances its own fit
	 * against the distance to the movies already chosen.
	 */
	private fun pickDiverse(pool: List<BaseItemDto>, state: RecommendationState): List<BaseItemDto> {
		if (pool.size <= RESULT_COUNT) return pool.shuffled()

		val scored = pool.associateWith { relevance(it, state) + Random.nextDouble(0.0, RANDOM_JITTER) }
		val first = scored.entries.sortedByDescending { it.value }.take(FIRST_PICK_POOL).random().key
		val picked = mutableListOf(first)

		while (picked.size < RESULT_COUNT) {
			val next = scored
				.filterKeys { it !in picked }
				.maxByOrNull { (item, score) ->
					val distance = picked.minOf { distance(item, it) }
					RELEVANCE_WEIGHT * score + (1 - RELEVANCE_WEIGHT) * distance
				}
				?.key ?: break
			picked += next
		}
		return picked
	}

	/** 0..1: how well a movie fits the answers. The rating is only a soft preference. */
	private fun relevance(item: BaseItemDto, state: RecommendationState): Double {
		val selectedGenres = state.selectedGenres
		val genreFit = if (selectedGenres.isEmpty()) 0.5 else {
			val genres = item.genres.orEmpty()
			val matched = genres.count { it in selectedGenres }
			// Hitting the mood at all matters most, being mostly about it is a bonus.
			(if (matched > 0) 0.6 else 0.0) + 0.4 * (if (genres.isEmpty()) 0.0 else matched.toDouble() / genres.size)
		}
		val companyFit = (state.company ?: RecommendationCompany.Alone).fit(item)
		val ratingFit = (item.communityRating?.toDouble() ?: 6.0) / 10.0
		val durationFit = when {
			state.duration == null || state.duration == RecommendationDuration.Whatever -> 1.0
			item.runtimeMinutes == null -> 0.3
			else -> 1.0
		}
		return 0.45 * genreFit + 0.2 * companyFit + 0.25 * ratingFit + 0.1 * durationFit
	}

	/** 0..1: how different two movies are. */
	private fun distance(a: BaseItemDto, b: BaseItemDto): Double {
		val genresA = a.genres.orEmpty().toSet()
		val genresB = b.genres.orEmpty().toSet()
		val union = (genresA + genresB).size
		val genreDistance = if (union == 0) 0.5 else 1.0 - (genresA intersect genresB).size.toDouble() / union

		val yearDistance = if (a.productionYear == null || b.productionYear == null) 0.5
		else (abs(a.productionYear!! - b.productionYear!!) / 30.0).coerceAtMost(1.0)

		val runtimeA = a.runtimeMinutes
		val runtimeB = b.runtimeMinutes
		val runtimeDistance = if (runtimeA == null || runtimeB == null) 0.5
		else (abs(runtimeA - runtimeB) / 60.0).coerceAtMost(1.0)

		val ratingDistance = (abs((a.communityRating ?: 6f) - (b.communityRating ?: 6f)) / 3.0).coerceAtMost(1.0)

		return 0.5 * genreDistance + 0.25 * yearDistance + 0.15 * runtimeDistance + 0.1 * ratingDistance
	}

	// endregion

	private fun loadGenres() {
		viewModelScope.launch(Dispatchers.IO) {
			val genres = try {
				val response by api.genresApi.getGenres(
					parentId = resolveLibraryId(),
					includeItemTypes = setOf(BaseItemKind.MOVIE),
					sortBy = setOf(ItemSortBy.SORT_NAME),
					enableImages = false,
					enableTotalRecordCount = false,
				)
				response.items.mapNotNull { it.name?.takeIf(String::isNotBlank) }.distinct()
			} catch (error: ApiClientException) {
				Timber.w(error, "Failed to load genres")
				emptyList()
			}
			_state.update { it.copy(genresLoading = false, availableGenres = genres) }
		}
	}

	private suspend fun resolveLibraryId(): UUID? {
		libraryId?.let { return it }
		return try {
			userViewsRepository.views.first()
				.filter { it.collectionType == CollectionType.MOVIES || it.collectionType == null }
				// Several movie libraries: query everything, filtered by item type.
				.singleOrNull()
				?.id
				.also { libraryId = it }
		} catch (error: ApiClientException) {
			Timber.w(error, "Failed to resolve movie library")
			null
		}
	}

	/** Fetches the complete item (media sources) right before playback. */
	suspend fun getFullItem(id: UUID): BaseItemDto? = withContext(Dispatchers.IO) {
		try {
			val item by api.userLibraryApi.getItem(itemId = id)
			item
		} catch (error: ApiClientException) {
			Timber.w(error, "Failed to load item %s", id)
			null
		}
	}

	fun posterUrl(item: BaseItemDto): String? = item.cinemaPosterUrl(api, POSTER_WIDTH)

	fun backdropUrl(item: BaseItemDto): String? = item.cinemaBlurredBackdropUrl(api, BACKDROP_WIDTH, BACKDROP_BLUR)

	private companion object {
		const val RESULT_COUNT = 3
		const val CANDIDATE_LIMIT = 300
		const val FIRST_PICK_POOL = 8
		const val RANDOM_JITTER = 0.15
		/** Lower favours diversity over fit. */
		const val RELEVANCE_WEIGHT = 0.45
		const val SHUFFLE_ANIMATION_MS = 1400L
		const val POSTER_WIDTH = 360
		const val BACKDROP_WIDTH = 480
		const val BACKDROP_BLUR = 30
	}
}

private const val TICKS_PER_MINUTE = 600_000_000L

/** Runtime in whole minutes, `null` when unknown. */
val BaseItemDto.runtimeMinutes: Int?
	get() = runTimeTicks?.takeIf { it > 0L }?.let { (it / TICKS_PER_MINUTE).toInt() }

