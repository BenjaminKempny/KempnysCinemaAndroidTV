package org.jellyfin.androidtv.ui.cinema

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.contentwarning.ContentWarningRepository
import org.jellyfin.androidtv.data.contentwarning.ContentWarningResult
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

data class CinemaDetailState(
	val loading: Boolean = true,
	val item: BaseItemDto? = null,
	val backdropUrl: String? = null,
	/** Strongly blurred artwork filling the whole page behind the content. */
	val blurredBackdropUrl: String? = null,
	val posterUrl: String? = null,
	/** Seasons for a series, episodes for a season, members for a collection — empty for movies. */
	val children: List<BaseItemDto> = emptyList(),
	/** The episode to continue with (series only). */
	val nextUp: BaseItemDto? = null,
	/** Season currently shown in the series episode list. */
	val selectedSeasonId: UUID? = null,
	/** Episodes of [selectedSeasonId] (series only). */
	val episodes: List<BaseItemDto> = emptyList(),
	/** Collections default to release date ascending (oldest first). */
	val sort: CinemaSort = CinemaSort.Year,
	/** DoesTheDogDie content warnings — movies only, `null` for every other item type. */
	val contentWarnings: ContentWarningResult? = null,
	/** First local trailer of a movie (stored next to the file on the server), `null` otherwise. */
	val trailer: BaseItemDto? = null,
) {
	/** Collections render as a plain library grid instead of the media detail capsule. */
	val isCollection get() = item?.type == BaseItemKind.BOX_SET

	/** People show their filmography instead of a play button. */
	val isPerson get() = item?.type == BaseItemKind.PERSON

	/**
	 * The item the play button starts: the next up episode of a series (falling back to the
	 * first unwatched episode of the selected season), the item itself otherwise.
	 */
	val playTarget: BaseItemDto?
		get() = when (item?.type) {
			BaseItemKind.SERIES -> nextUp
				?: episodes.firstOrNull { it.userData?.played != true }
				?: episodes.firstOrNull()
				?: item

			else -> item
		}
}

/** Data layer for the cinema detail screen (spec §2.5). */
class CinemaDetailViewModel(
	private val api: ApiClient,
	private val contentWarningRepository: ContentWarningRepository? = null,
) : ViewModel() {
	private val _state = MutableStateFlow(CinemaDetailState())
	val state: StateFlow<CinemaDetailState> = _state.asStateFlow()

	private var itemId: UUID? = null
	private var collectionMediaType: CinemaMediaType? = null
	private var childrenJob: Job? = null
	private var episodesJob: Job? = null

	fun load(id: UUID, mediaType: CinemaMediaType? = null) {
		itemId = id
		collectionMediaType = mediaType
		_state.update { it.copy(loading = true) }

		viewModelScope.launch(Dispatchers.IO) {
			try {
				val loaded by api.userLibraryApi.getItem(itemId = id)
				// Seasons have no page of their own: show the series with that season selected.
				val seasonSeriesId = loaded.seriesId.takeIf { loaded.type == BaseItemKind.SEASON }
				val preselectedSeason = if (seasonSeriesId != null) loaded.id else null
				val item = if (seasonSeriesId != null) {
					api.userLibraryApi.getItem(itemId = seasonSeriesId).content
				} else {
					loaded
				}
				itemId = item.id

				_state.update {
					it.copy(
						loading = false,
						item = item,
						backdropUrl = backdropUrl(item),
						blurredBackdropUrl = item.cinemaBlurredBackdropUrl(api, BLURRED_BACKDROP_WIDTH, BLURRED_BACKDROP_RADIUS),
						posterUrl = heroPosterUrl(item),
					)
				}

				loadChildren(item)
				if (item.type == BaseItemKind.SERIES) {
					// Next up decides the default season, so it has to be known first.
					loadNextUp(item.id)
					selectDefaultSeason(item.id, preselectedSeason)
				}
				if (item.type == BaseItemKind.MOVIE) {
					loadTrailer(item)
					loadContentWarnings(item)
				}
			} catch (error: ApiClientException) {
				Timber.w(error, "Failed to load cinema detail item")
				_state.update { it.copy(loading = false) }
			}
		}
	}

	/** Refreshes user data after returning from playback. */
	fun refresh() {
		itemId?.let { load(it, collectionMediaType) }
	}

	fun setSort(sort: CinemaSort) {
		if (_state.value.sort == sort) return
		_state.update { it.copy(sort = sort) }

		val item = _state.value.item ?: return
		// Rapid sort changes would otherwise race and let the slower, stale response win.
		childrenJob?.cancel()
		childrenJob = viewModelScope.launch(Dispatchers.IO) { loadChildren(item) }
	}

	private suspend fun loadChildren(item: BaseItemDto) {
		val sort = _state.value.sort

		runCatching {
			when (item.type) {
				BaseItemKind.SERIES -> {
					val result by api.tvShowsApi.getSeasons(
						seriesId = item.id,
						fields = ItemRepository.browseFields,
						enableUserData = true,
					)
					result.items
				}

				BaseItemKind.BOX_SET -> {
					val itemTypes = collectionMediaType?.let { setOf(it.itemKind) }
						?: setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
					// Inside a collection the default sort runs chronologically by release date
					// (oldest release first). The production year only breaks ties for items
					// without a premiere date.
					val sortOrder = if (sort == CinemaSort.Year) SortOrder.ASCENDING else sort.sortOrder
					val sortBy = if (sort == CinemaSort.Year) {
						listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME)
					} else {
						listOf(sort.sortBy)
					}
					val result by api.itemsApi.getItems(
						parentId = item.id,
						includeItemTypes = itemTypes,
						collapseBoxSetItems = false,
						recursive = true,
						sortBy = sortBy,
						sortOrder = sortBy.map { sortOrder },
						fields = ItemRepository.browseFields,
						imageTypeLimit = 1,
						enableTotalRecordCount = false,
					)
					result.items.filter { it.type in itemTypes }
				}

				BaseItemKind.PERSON -> {
					// Filmography: every movie and series the person is credited in.
					val itemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
					val sortBy = if (sort == CinemaSort.Year) {
						listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME)
					} else {
						listOf(sort.sortBy)
					}
					val result by api.itemsApi.getItems(
						personIds = setOf(item.id),
						includeItemTypes = itemTypes,
						recursive = true,
						sortBy = sortBy,
						sortOrder = sortBy.map { sort.sortOrder },
						fields = ItemRepository.browseFields,
						imageTypeLimit = 1,
						enableUserData = true,
						enableTotalRecordCount = false,
					)
					result.items.filter { it.type in itemTypes }
				}

				else -> return
			}
		}.onSuccess { children ->
			_state.update { current ->
				// A newer sort may have been applied while this request was in flight.
				if (current.sort == sort) current.copy(children = children) else current
			}
		}.onFailure {
			Timber.w(it, "Failed to load cinema detail children")
		}
	}

	fun selectSeason(seasonId: UUID) {
		val current = _state.value
		if (current.selectedSeasonId == seasonId) return
		val seriesId = current.item?.takeIf { it.type == BaseItemKind.SERIES }?.id ?: return
		_state.update { it.copy(selectedSeasonId = seasonId, episodes = emptyList()) }

		episodesJob?.cancel()
		episodesJob = viewModelScope.launch(Dispatchers.IO) { loadEpisodes(seriesId, seasonId) }
	}

	/**
	 * Picks the season shown below the series: an explicitly requested one, the one already
	 * selected (refresh), the season of the next up episode, otherwise season 1.
	 */
	private suspend fun selectDefaultSeason(seriesId: UUID, preselected: UUID?) {
		val current = _state.value
		val seasons = current.children
		if (seasons.isEmpty()) {
			_state.update { it.copy(selectedSeasonId = null, episodes = emptyList()) }
			return
		}

		fun UUID?.valid() = this?.takeIf { id -> seasons.any { it.id == id } }
		val seasonId = preselected.valid()
			?: current.selectedSeasonId.valid()
			?: current.nextUp?.seasonId.valid()
			?: seasons.firstOrNull { it.indexNumber == 1 }?.id
			?: seasons.first().id

		_state.update { it.copy(selectedSeasonId = seasonId) }
		loadEpisodes(seriesId, seasonId)
	}

	private suspend fun loadEpisodes(seriesId: UUID, seasonId: UUID) {
		runCatching {
			val result by api.tvShowsApi.getEpisodes(
				seriesId = seriesId,
				seasonId = seasonId,
				fields = ItemRepository.browseFields,
				enableUserData = true,
			)
			result.items
		}.onSuccess { episodes ->
			_state.update { if (it.selectedSeasonId == seasonId) it.copy(episodes = episodes) else it }
		}.onFailure {
			Timber.w(it, "Failed to load episodes")
		}
	}

	/** Local trailers are downloaded next to the movie file and picked up by the server. */
	private suspend fun loadTrailer(item: BaseItemDto) {
		if ((item.localTrailerCount ?: 0) <= 0) {
			_state.update { if (it.item?.id == item.id) it.copy(trailer = null) else it }
			return
		}

		runCatching {
			val trailers by api.userLibraryApi.getLocalTrailers(itemId = item.id)
			trailers.firstOrNull()
		}.onSuccess { trailer ->
			_state.update { if (it.item?.id == item.id) it.copy(trailer = trailer) else it }
		}.onFailure {
			Timber.w(it, "Failed to load trailer")
		}
	}

	private suspend fun loadContentWarnings(item: BaseItemDto) {
		val repository = contentWarningRepository ?: return
		// Keep already loaded warnings visible while refreshing after playback.
		if (_state.value.contentWarnings !is ContentWarningResult.Available) {
			_state.update { it.copy(contentWarnings = ContentWarningResult.Loading) }
		}

		val result = runCatching { repository.getWarnings(item) }
			.onFailure { Timber.w(it, "Failed to load content warnings") }
			.getOrDefault(ContentWarningResult.Error)

		_state.update { current ->
			if (current.item?.id == item.id) current.copy(contentWarnings = result) else current
		}
	}

	private suspend fun loadNextUp(seriesId: UUID) {
		runCatching {
			val result by api.tvShowsApi.getNextUp(
				seriesId = seriesId,
				limit = 1,
				fields = ItemRepository.browseFields,
				enableTotalRecordCount = false,
			)
			result.items.firstOrNull()
		}.onSuccess { nextUp ->
			_state.update { it.copy(nextUp = nextUp) }
		}.onFailure {
			Timber.w(it, "Failed to load next up episode")
		}
	}

	fun posterUrl(item: BaseItemDto): String? = item.cinemaPosterUrl(api, CARD_WIDTH)

	/** Landscape artwork for episode rows and the next up card. */
	fun thumbUrl(item: BaseItemDto): String? = item.cinemaThumbUrl(api, THUMB_WIDTH)

	fun personImageUrl(person: BaseItemPerson): String? =
		person.primaryImage?.getUrl(api, fillWidth = PERSON_WIDTH, fillHeight = PERSON_WIDTH)

	/** Episodes have landscape artwork, the capsule shows the series poster instead. */
	private fun heroPosterUrl(item: BaseItemDto): String? {
		if (item.type == BaseItemKind.EPISODE) {
			item.seriesPrimaryImage?.getUrl(api, fillWidth = POSTER_WIDTH)?.let { return it }
		}
		return item.cinemaPosterUrl(api, POSTER_WIDTH)
	}

	private fun backdropUrl(item: BaseItemDto): String? = item.cinemaBackdropUrl(api, BACKDROP_WIDTH)

	private companion object {
		const val POSTER_WIDTH = 420
		const val CARD_WIDTH = 360
		const val THUMB_WIDTH = 480
		const val PERSON_WIDTH = 240
		const val BACKDROP_WIDTH = 1600
		// Small source + strong blur: cheap to transfer and smooth when scaled up.
		const val BLURRED_BACKDROP_WIDTH = 480
		const val BLURRED_BACKDROP_RADIUS = 30
	}
}
