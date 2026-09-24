package org.jellyfin.androidtv.ui.cinema

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.UUID

/** Host fragment for the cinema detail screen. Replaces `FullDetailsFragment`. */
class CinemaDetailFragment : Fragment() {
	private val viewModel by viewModel<CinemaDetailViewModel>()
	private val navigationRepository by inject<NavigationRepository>()
	private val playbackLauncher by inject<PlaybackLauncher>()

	private var loaded = false

	private val itemId by lazy {
		requireNotNull(requireArguments().getString(ARGUMENT_ITEM_ID)).let(UUID::fromString)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		viewModel.load(itemId)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val state by viewModel.state.collectAsState()

		JellyfinTheme {
			CinemaDetailScreen(
				state = state,
				actions = CinemaDetailActions(
					onBack = { navigationRepository.goBack() },
					onHome = { navigationRepository.reset(Destinations.home) },
					onPlay = ::play,
					onToggleFavorite = viewModel::toggleFavorite,
					onTogglePlayed = viewModel::togglePlayed,
					onOpenItem = ::openItem,
					onSelectSort = viewModel::setSort,
				),
				posterUrl = viewModel::posterUrl,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}

	override fun onResume() {
		super.onResume()
		// Playback position, watched and favorite state can change while away. The very
		// first resume follows onCreate's load, so reloading again would be wasteful.
		if (loaded) viewModel.refresh() else loaded = true
	}

	private fun openItem(item: BaseItemDto) {
		navigationRepository.navigate(Destinations.itemDetails(item.id))
	}

	private fun play() {
		val item = viewModel.state.value.item ?: return
		val position = item.userData?.playbackPositionTicks
			?.takeIf { it > 0L }
			?.let { (it / TICKS_PER_MILLISECOND).toInt() }

		playbackLauncher.launch(
			context = requireContext(),
			items = listOf(item),
			position = position,
		)
	}

	companion object {
		const val ARGUMENT_ITEM_ID = "item_id"
		private const val TICKS_PER_MILLISECOND = 10_000L
	}
}
