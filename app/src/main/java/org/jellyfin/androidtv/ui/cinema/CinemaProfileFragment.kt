package org.jellyfin.androidtv.ui.cinema

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Host fragment for the cinema profile / account switcher screen. */
class CinemaProfileFragment : Fragment() {
	private val viewModel by viewModel<CinemaProfileViewModel>()
	private val settingsViewModel by activityViewModel<SettingsViewModel>()
	private val navigationRepository by inject<NavigationRepository>()
	private val sessionRepository by inject<SessionRepository>()
	private val mediaManager by inject<MediaManager>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val state by viewModel.state.collectAsState()

		JellyfinTheme {
			CinemaProfileScreen(
				state = state,
				actions = CinemaProfileActions(
					onBack = { navigationRepository.goBack() },
					onHome = { navigationRepository.reset(Destinations.home) },
					onSelectUser = ::selectUser,
					onManageAccounts = ::openAccountManager,
					onSignOut = ::signOut,
					onSettings = { settingsViewModel.show() },
				),
				modifier = Modifier.fillMaxSize(),
			)
		}
	}

	override fun onResume() {
		super.onResume()
		viewModel.load()
	}

	private fun selectUser(user: CinemaProfileUser) {
		// Selecting the active account is a no-op — re-authenticating would only restart
		// the app for no reason.
		if (user.isCurrent) {
			navigationRepository.reset(Destinations.home)
			return
		}

		viewModel.authenticate(user.id)
			.onEach { state ->
				when (state) {
					AuthenticatingState -> Unit
					// The session now belongs to the new user; the startup activity picks
					// it up and rebuilds the whole UI for them.
					AuthenticatedState -> restart()

					// No stored token (or "always authenticate" is on): the password has
					// to be entered on the login screen.
					RequireSignInState -> openAccountManager()

					ServerUnavailableState,
					is ApiClientErrorLoginState,
					-> failed(getString(R.string.server_connection_failed))

					is ServerVersionNotSupported -> failed(
						getString(
							R.string.server_issue_outdated_version,
							state.server.version,
							ServerRepository.recommendedServerVersion.toString(),
						)
					)
				}
			}
			.launchIn(lifecycleScope)
	}

	private fun failed(message: String) {
		viewModel.setBusy(false)
		Toast.makeText(context, message, Toast.LENGTH_LONG).show()
	}

	private fun signOut() {
		viewModel.signOut()
		openAccountManager()
	}

	/** Drops the current session and opens the full login screen. */
	private fun openAccountManager() {
		mediaManager.clearAudioQueue()
		sessionRepository.destroyCurrentSession()
		restart()
	}

	private fun restart() {
		val activity = activity ?: return
		activity.startActivity(ActivityDestinations.startup(activity))
		activity.finishAfterTransition()
	}
}

