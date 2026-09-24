package org.jellyfin.androidtv.ui.cinema

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.ui.search.SearchViewModel
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Host fragment for the cinema search screen. */
class CinemaSearchFragment : Fragment() {
	private val viewModel by viewModel<SearchViewModel>()
	private val navigationRepository by inject<NavigationRepository>()
	private val imageHelper by inject<ImageHelper>()
	private val playbackLauncher by inject<PlaybackLauncher>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.getString(ARGUMENT_QUERY)
			?.takeIf { it.isNotBlank() }
			?.let(viewModel::searchImmediately)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val results by viewModel.searchResultsFlow.collectAsState()
		var query by rememberSaveable { mutableStateOf(arguments?.getString(ARGUMENT_QUERY).orEmpty()) }

		JellyfinTheme {
			CinemaSearchScreen(
				query = query,
				results = results,
				onQueryChange = {
					query = it
					viewModel.searchDebounced(it)
				},
				onQuerySubmit = { viewModel.searchImmediately(query) },
				onOpenItem = ::openItem,
				posterUrl = ::posterUrl,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}

	/**
	 * Search returns every library kind, so a plain jump to the detail screen would strand
	 * music, photos and folders on a page that cannot render them. Dispatch by item type
	 * the same way the leanback `ItemLauncher` did.
	 */
	private fun openItem(item: BaseItemDto) {
		val destination = when (item.type) {
			BaseItemKind.MUSIC_ALBUM,
			BaseItemKind.PLAYLIST,
			-> Destinations.itemList(item.id)

			BaseItemKind.SEASON,
			BaseItemKind.PHOTO_ALBUM,
			-> Destinations.folderBrowser(item)

			BaseItemKind.PHOTO -> Destinations.photoPlayer(item.id, false, null, null)

			BaseItemKind.AUDIO,
			BaseItemKind.LIVE_TV_CHANNEL,
			-> {
				playbackLauncher.launch(requireContext(), listOf(item))
				return
			}

			else -> Destinations.itemDetails(item.id)
		}

		navigationRepository.navigate(destination)
	}

	private fun posterUrl(item: BaseItemDto) =
		imageHelper.getPrimaryImageUrl(item, preferParentThumb = false, fillWidth = POSTER_IMAGE_WIDTH)

	companion object {
		const val ARGUMENT_QUERY = "query"
		private const val POSTER_IMAGE_WIDTH = 360
	}
}
