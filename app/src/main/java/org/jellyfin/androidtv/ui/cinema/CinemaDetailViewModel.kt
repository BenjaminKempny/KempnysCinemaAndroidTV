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
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

data class CinemaDetailState(
	val loading: Boolean = true,
	val item: BaseItemDto? = null,
	val backdropUrl: String? = null,
	val posterUrl: String? = null,
	/** Seasons for a series, episodes for a season — empty for movies. */
	val children: List<BaseItemDto> = emptyList(),
	val similar: List<BaseItemDto> = emptyList(),
	val favorite: Boolean = false,
	val played: Boolean = false,
	val sort: CinemaSort = CinemaSort.Name,
) {
	/** Collections render as a plain library grid instead of the media detail capsule. */
	val isCollection get() = item?.type == BaseItemKind.BOX_SET
}

/** Data layer for the cinema detail screen (spec §2.5). */
class CinemaDetailViewModel(
	private val api: ApiClient,
	private val imageHelper: ImageHelper,
	private val itemMutationRepository: ItemMutationRepository,
) : ViewModel() {
	private val _state = MutableStateFlow(CinemaDetailState())
	val state: StateFlow<CinemaDetailState> = _state.asStateFlow()

	private var itemId: UUID? = null
	private var childrenJob: Job? = null

	fun load(id: UUID) {
		itemId = id
		_state.update { it.copy(loading = true) }

		viewModelScope.launch(Dispatchers.IO) {
			try {
				val item by api.userLibraryApi.getItem(itemId = id)

				_state.update {
					it.copy(
						loading = false,
						item = item,
						backdropUrl = backdropUrl(item),
						posterUrl = imageHelper.getPrimaryImageUrl(
							item,
							preferParentThumb = false,
							fillWidth = POSTER_WIDTH,
						),
						favorite = item.userData?.isFavorite == true,
						played = item.userData?.played == true,
					)
				}

				loadChildren(item)
				// Collections are a browsing surface, not a media page — no recommendations.
				if (item.type != BaseItemKind.BOX_SET) loadSimilar(id)
			} catch (error: ApiClientException) {
				Timber.w(error, "Failed to load cinema detail item")
				_state.update { it.copy(loading = false) }
			}
		}
	}

	/** Refreshes user data after returning from playback. */
	fun refresh() {
		itemId?.let(::load)
	}

	fun setSort(sort: CinemaSort) {
		if (_state.value.sort == sort) return
		_state.update { it.copy(sort = sort) }

		val item = _state.value.item ?: return
		// Rapid sort changes would otherwise race and let the slower, stale response win.
		childrenJob?.cancel()
		childrenJob = viewModelScope.launch(Dispatchers.IO) { loadChildren(item) }
	}

	fun toggleFavorite() {
		val id = itemId ?: return
		val target = !_state.value.favorite
		_state.update { it.copy(favorite = target) }

		viewModelScope.launch {
			runCatching { itemMutationRepository.setFavorite(id, target) }
				.onFailure {
					Timber.w(it, "Failed to toggle favorite")
					_state.update { state -> state.copy(favorite = !target) }
				}
		}
	}

	fun togglePlayed() {
		val id = itemId ?: return
		val target = !_state.value.played
		_state.update { it.copy(played = target) }

		viewModelScope.launch {
			runCatching { itemMutationRepository.setPlayed(id, target) }
				.onFailure {
					Timber.w(it, "Failed to toggle played")
					_state.update { state -> state.copy(played = !target) }
				}
		}
	}

	private suspend fun loadChildren(item: BaseItemDto) {
		val childTypes = when (item.type) {
			BaseItemKind.SERIES -> setOf(BaseItemKind.SEASON)
			BaseItemKind.SEASON -> setOf(BaseItemKind.EPISODE)
			BaseItemKind.BOX_SET -> setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
			else -> return
		}
		val sort = _state.value.sort
		// Seasons and episodes keep their natural order, collections follow the toolbar.
		val sortBy = if (item.type == BaseItemKind.BOX_SET) sort.sortBy else ItemSortBy.SORT_NAME
		val sortOrder = if (item.type == BaseItemKind.BOX_SET) sort.sortOrder else SortOrder.ASCENDING

		runCatching {
			val result by api.itemsApi.getItems(
				parentId = item.id,
				includeItemTypes = childTypes,
				recursive = item.type == BaseItemKind.BOX_SET,
				sortBy = setOf(sortBy),
				sortOrder = setOf(sortOrder),
				fields = ItemRepository.browseFields,
				imageTypeLimit = 1,
				enableTotalRecordCount = false,
			)
			result.items
		}.onSuccess { children ->
			_state.update { current ->
				// A newer sort may have been applied while this request was in flight.
				if (current.sort == sort) current.copy(children = children) else current
			}
		}
	}

	private suspend fun loadSimilar(id: UUID) {
		runCatching {
			val result by api.libraryApi.getSimilarItems(
				itemId = id,
				limit = SIMILAR_LIMIT,
				fields = ItemRepository.browseFields,
			)
			result.items
		}.onSuccess { similar ->
			_state.update { it.copy(similar = similar) }
		}
	}

	fun posterUrl(item: BaseItemDto): String? =
		imageHelper.getPrimaryImageUrl(item, preferParentThumb = false, fillWidth = CARD_WIDTH)

	private fun backdropUrl(item: BaseItemDto): String? {
		val backdrop = item.itemBackdropImages.firstOrNull() ?: item.parentBackdropImages.firstOrNull()
		return backdrop?.getUrl(api, maxWidth = BACKDROP_WIDTH)
			?: imageHelper.getPrimaryImageUrl(item, preferParentThumb = false, fillWidth = BACKDROP_WIDTH)
	}

	private companion object {
		const val POSTER_WIDTH = 420
		const val CARD_WIDTH = 360
		const val BACKDROP_WIDTH = 1600
		const val SIMILAR_LIMIT = 20
	}
}
