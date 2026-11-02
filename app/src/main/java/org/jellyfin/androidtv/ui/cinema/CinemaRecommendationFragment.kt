package org.jellyfin.androidtv.ui.cinema

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Host fragment for the "Was soll ich schauen?" movie quiz. */
class CinemaRecommendationFragment : Fragment() {
	private val viewModel by viewModel<CinemaRecommendationViewModel>()
	private val navigationRepository by inject<NavigationRepository>()
	private val playbackLauncher by inject<PlaybackLauncher>()
	private var playJob: Job? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val state by viewModel.state.collectAsState()

		// DPAD back walks the quiz backwards before leaving the screen.
		BackHandler(enabled = state.step != RecommendationStep.Mood) { viewModel.previous() }

		CinemaTheme {
			CinemaRecommendationScreen(
				state = state,
				actions = RecommendationActions(
					onBack = { if (!viewModel.previous()) navigationRepository.goBack() },
					onHome = { navigationRepository.reset(Destinations.home) },
					onSelectMood = viewModel::selectMood,
					onOpenGenres = viewModel::openGenres,
					onToggleGenre = viewModel::toggleGenre,
					onClearGenres = viewModel::clearGenres,
					onSelectCompany = viewModel::selectCompany,
					onSelectDuration = viewModel::selectDuration,
					onSelectEra = viewModel::selectEra,
					onNext = viewModel::next,
					onPrevious = { viewModel.previous() },
					onReshuffle = viewModel::reshuffle,
					onRestart = viewModel::restart,
					onOpenItem = { navigationRepository.navigate(Destinations.itemDetails(it.id, CinemaMediaType.Movies)) },
					onPlayItem = ::play,
				),
				posterUrl = viewModel::posterUrl,
				backdropUrl = viewModel::backdropUrl,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}

	private fun play(item: BaseItemDto) {
		if (playJob?.isActive == true) return
		playJob = viewLifecycleOwner.lifecycleScope.launch {
			val full = viewModel.getFullItem(item.id) ?: return@launch
			val context = context ?: return@launch
			val position = full.userData?.playbackPositionTicks
				?.takeIf { it > 0L }
				?.let { (it / TICKS_PER_MILLISECOND).toInt() }
			playbackLauncher.launch(context = context, items = listOf(full), position = position)
		}
	}

	private companion object {
		const val TICKS_PER_MILLISECOND = 10_000L
	}
}

