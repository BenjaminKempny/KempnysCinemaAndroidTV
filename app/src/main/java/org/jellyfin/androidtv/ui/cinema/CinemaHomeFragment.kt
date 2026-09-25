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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Host fragment for the cinema home screen. Replaces the leanback based
 * `HomeFragment`/`HomeRowsFragment` pair.
 */
class CinemaHomeFragment : Fragment() {
	private val viewModel by viewModel<CinemaHomeViewModel>()
	private val settingsViewModel by activityViewModel<SettingsViewModel>()
	private val navigationRepository by inject<NavigationRepository>()
	private val playbackLauncher by inject<PlaybackLauncher>()
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val state by viewModel.state.collectAsState()

		JellyfinTheme {
			CinemaHomeScreen(
				state = state,
				actions = CinemaHomeActions(
					onOpenItem = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
					onPlayItem = ::play,
					onOpenHeroItem = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
					onPlayHeroItem = { hero -> playById(hero.id) },
					// Secondary actions are not part of the card's focus target, they open
					// the detail screen where the full action row lives (spec §2.7).
					onItemMenu = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
					onRailSelect = ::onRailSelect,
				),
				posterUrl = viewModel::posterUrl,
				thumbUrl = viewModel::thumbUrl,
				onSelectView = viewModel::setView,
				onSelectSort = viewModel::setSort,
				onLoadNextPage = viewModel::loadNextCatalogPage,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server -> notificationRepository.updateServerNotifications(server) }
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}

	override fun onResume() {
		super.onResume()
		// Resume positions and watched flags change outside of this screen. Only the
		// Continue Watching row is reloaded so scroll and focus position survive.
		viewModel.refreshContinueWatching()
	}

	private fun onRailSelect(item: CinemaRailItem) = when (item) {
		CinemaRailItem.Search -> navigationRepository.navigate(Destinations.search())
		CinemaRailItem.Movies -> viewModel.setMediaType(CinemaMediaType.Movies)
		CinemaRailItem.Shows -> viewModel.setMediaType(CinemaMediaType.Shows)
		// The web profile page manages avatar and password; on Android TV the closest
		// equivalent is the cinema styled account switcher.
		CinemaRailItem.Profile -> navigationRepository.navigate(Destinations.profile)
		CinemaRailItem.Settings -> settingsViewModel.show()
	}


	private fun play(item: org.jellyfin.sdk.model.api.BaseItemDto) = playById(item.id)

	private fun playById(id: java.util.UUID) {
		viewLifecycleOwner.lifecycleScope.launch {
			val item = viewModel.getFullItem(id) ?: return@launch
			val position = item.userData?.playbackPositionTicks
				?.takeIf { it > 0L }
				?.let { (it / TICKS_PER_MILLISECOND).toInt() }

			playbackLauncher.launch(
				context = requireContext(),
				items = listOf(item),
				position = position,
			)
		}
	}

	private companion object {
		const val TICKS_PER_MILLISECOND = 10_000L
	}
}
