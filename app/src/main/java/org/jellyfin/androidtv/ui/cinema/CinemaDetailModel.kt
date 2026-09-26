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
import timber.log.Timber
import java.util.UUID

data class CinemaDetailState(
	val loading: Boolean = true,
	val item: BaseItemDto? = null,
	val backdropUrl: String? = null,
	val posterUrl: String? = null,
	/** Seasons for a series, episodes for a season, members for a collection — empty for movies. */
	val children: List<BaseItemDto> = emptyList(),
	/** The episode to continue with (series only). */
	val nextUp: BaseItemDto? = null,
	val sort: CinemaSort = CinemaSort.Name,
) {
	/** Collections render as a plain library grid instead of the media detail capsule. */
	val isCollection get() = item?.type == BaseItemKind.BOX_SET

	/**
	 * The item the play button starts: the next up episode of a series, the first
	 * unwatched episode of a season, the item itself otherwise.
	 */
	val playTarget: BaseItemDto?
		get() = when (item?.type) {
			BaseItemKind.SERIES -> nextUp ?: item
			BaseItemKind.SEASON -> children.firstOrNull { it.userData?.played != true }
				?: children.firstOrNull()
				?: item

			else -> item
		}
}

/** Data layer for the cinema detail screen (spec §2.5). */
class CinemaDetailViewModel(
	private val api: ApiClient,
) : ViewModel() {
	private val _state = MutableStateFlow(CinemaDetailState())
	val state: StateFlow<CinemaDetailState> = _state.asStateFlow()

	private var itemId: UUID? = null
	private var collectionMediaType: CinemaMediaType? = null
	private var childrenJob: Job? = null

	fun load(id: UUID, mediaType: CinemaMediaType? = null) {
		itemId = id
		collectionMediaType = mediaType
		_state.update { it.copy(loading = true) }

		viewModelScope.launch(Dispatchers.IO) {
			try {
				val item by api.userLibraryApi.getItem(itemId = id)

				_state.update {
					it.copy(
						loading = false,
						item = item,
						backdropUrl = backdropUrl(item),
						posterUrl = heroPosterUrl(item),
					)
				}

				loadChildren(item)
				if (item.type == BaseItemKind.SERIES) loadNextUp(item.id)
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

				BaseItemKind.SEASON -> {
					val seriesId = item.seriesId ?: return
					val result by api.tvShowsApi.getEpisodes(
						seriesId = seriesId,
						seasonId = item.id,
						fields = ItemRepository.browseFields,
						enableUserData = true,
					)
					result.items
				}

				BaseItemKind.BOX_SET -> {
					val itemTypes = collectionMediaType?.let { setOf(it.itemKind) }
						?: setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
					val result by api.itemsApi.getItems(
						parentId = item.id,
						includeItemTypes = itemTypes,
						collapseBoxSetItems = false,
						recursive = true,
						sortBy = setOf(sort.sortBy),
						sortOrder = setOf(sort.sortOrder),
						fields = ItemRepository.browseFields,
						imageTypeLimit = 1,
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
	}
}

