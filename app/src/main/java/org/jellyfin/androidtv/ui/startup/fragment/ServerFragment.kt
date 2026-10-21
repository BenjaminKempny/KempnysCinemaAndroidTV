package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinema.CinemaButton
import org.jellyfin.androidtv.ui.cinema.CinemaColors
import org.jellyfin.androidtv.ui.cinema.CinemaDimens
import org.jellyfin.androidtv.ui.cinema.CinemaInitialFocus
import org.jellyfin.androidtv.ui.cinema.CinemaPanel
import org.jellyfin.androidtv.ui.cinema.CinemaSectionTitle
import org.jellyfin.androidtv.ui.cinema.CinemaStartupFrame
import org.jellyfin.androidtv.ui.cinema.CinemaStartupUserCard
import org.jellyfin.androidtv.ui.cinema.CinemaTheme
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.androidtv.util.createBundle
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Account picker ("Who is watching?") in the cinema design. Uses [CinemaStartupFrame] so
 * it looks exactly like the library pages that follow after signing in.
 */
class ServerFragment : Fragment() {
	companion object {
		const val ARG_SERVER_ID = "server_id"
	}

	private val startupViewModel: StartupViewModel by activityViewModel()
	private val markdownRenderer: MarkdownRenderer by inject()
	private val authenticationRepository: AuthenticationRepository by inject()
	private val serverUserRepository: ServerUserRepository by inject()
	private val backgroundService: BackgroundService by inject()

	private val serverIdArgument get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }?.toUUIDOrNull()
	private val server = MutableStateFlow<Server?>(null)

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val initialServer = serverIdArgument?.let(startupViewModel::getServer)

		if (initialServer == null) {
			navigateFragment<SelectServerFragment>(keepToolbar = true, keepHistory = false)
			return null
		}

		server.value = initialServer
		startupViewModel.loadUsers(initialServer)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				val current = server.value ?: return@repeatOnLifecycle
				val updated = startupViewModel.updateServer(current)
				if (updated) startupViewModel.getServer(current.id)?.let { server.value = it }
			}
		}

		return content {
			val currentServer by server.collectAsState()
			val users by startupViewModel.users.collectAsState()

			CinemaTheme {
				currentServer?.let { server ->
					ServerScreen(
						server = server,
						users = users,
						imageUrl = { user -> startupViewModel.getUserImage(server, user) },
						disclaimer = server.loginDisclaimer?.let { markdownRenderer.toMarkdownSpanned(it).toString() },
						onSelectUser = { user -> onUserSelected(server, user) },
						onAddUser = {
							navigateFragment<UserLoginFragment>(
								args = createBundle {
									putString(UserLoginFragment.ARG_SERVER_ID, server.id.toString())
									putString(UserLoginFragment.ARG_USERNAME, null)
								}
							)
						},
						onSelectServer = { navigateFragment<SelectServerFragment>(keepToolbar = true) },
						onHelp = {
							parentFragmentManager.commit {
								addToBackStack(null)
								replace<ConnectHelpAlertFragment>(R.id.content_view)
							}
						},
						onSignOut = { user ->
							if (user is PrivateUser) authenticationRepository.logout(user)
							startupViewModel.loadUsers(server)
						},
						onRemove = { user ->
							if (user is PrivateUser) serverUserRepository.deleteStoredUser(user)
							startupViewModel.loadUsers(server)
						},
					)
				}
			}
		}
	}

	private fun onUserSelected(server: Server, user: User) {
		startupViewModel.authenticate(server, user).onEach { state ->
			when (state) {
				// Ignored states
				AuthenticatingState -> Unit
				AuthenticatedState -> Unit
				// Actions
				RequireSignInState -> navigateFragment<UserLoginFragment>(createBundle {
					putString(UserLoginFragment.ARG_SERVER_ID, server.id.toString())
					putString(UserLoginFragment.ARG_USERNAME, user.name)
				})
				// Errors
				ServerUnavailableState,
				is ApiClientErrorLoginState -> Toast.makeText(context, R.string.server_connection_failed, Toast.LENGTH_LONG).show()

				is ServerVersionNotSupported -> Toast.makeText(
					context,
					getString(
						R.string.server_issue_outdated_version,
						state.server.version,
						ServerRepository.recommendedServerVersion.toString()
					),
					Toast.LENGTH_LONG
				).show()
			}
		}.launchIn(lifecycleScope)
	}

	private inline fun <reified F : Fragment> navigateFragment(
		args: Bundle = createBundle(),
		keepToolbar: Boolean = false,
		keepHistory: Boolean = true,
	) {
		requireActivity()
			.supportFragmentManager
			.commit {
				setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
				if (keepToolbar) {
					replace<StartupToolbarFragment>(R.id.content_view)
					add<F>(R.id.content_view, null, args)
				} else {
					replace<F>(R.id.content_view, null, args)
				}

				if (keepHistory) addToBackStack(null)
			}
	}

	override fun onResume() {
		super.onResume()

		startupViewModel.reloadStoredServers()
		backgroundService.clearBackgrounds()

		val server = serverIdArgument?.let(startupViewModel::getServer)
		if (server != null) startupViewModel.loadUsers(server)
		else navigateFragment<SelectServerFragment>(keepToolbar = true)
	}
}

@Composable
private fun ServerScreen(
	server: Server,
	users: List<User>,
	imageUrl: (User) -> String?,
	disclaimer: String?,
	onSelectUser: (User) -> Unit,
	onAddUser: () -> Unit,
	onSelectServer: () -> Unit,
	onHelp: () -> Unit,
	onSignOut: (User) -> Unit,
	onRemove: (User) -> Unit,
) {
	var menuUser by remember { mutableStateOf<User?>(null) }
	val firstUserFocusRequester = remember { FocusRequester() }
	val addUserFocusRequester = remember { FocusRequester() }
	val menuFocusRequester = remember { FocusRequester() }

	CinemaInitialFocus(
		requester = if (users.isNotEmpty()) firstUserFocusRequester else addUserFocusRequester,
		key = users.isNotEmpty(),
	)
	LaunchedEffect(menuUser) {
		if (menuUser != null) runCatching { menuFocusRequester.requestFocus() }
	}
	BackHandler(enabled = menuUser != null) { menuUser = null }

	CinemaStartupFrame(
		title = stringResource(R.string.who_is_watching),
		headerCenter = {
			CinemaButton(
				text = server.name,
				icon = painterResource(R.drawable.ic_jellyfin),
				onClick = onSelectServer,
			)
		},
	) {
		val notification = when {
			!server.versionSupported -> stringResource(
				R.string.server_unsupported_notification,
				server.version.orEmpty(),
				ServerRepository.recommendedServerVersion.toString(),
			)

			!server.setupCompleted -> stringResource(R.string.server_setup_incomplete)
			else -> null
		}
		if (notification != null) {
			CinemaPanel(Modifier.fillMaxWidth()) {
				Text(
					text = notification,
					color = CinemaColors.Text,
					fontSize = CinemaDimens.BodySize,
					modifier = Modifier.padding(20.dp),
				)
			}
		}

		if (users.isEmpty()) {
			Text(
				text = stringResource(R.string.no_user_warning),
				color = CinemaColors.Muted,
				fontSize = CinemaDimens.BodySize,
			)
		} else {
			LazyRow(
				modifier = Modifier
					.fillMaxWidth()
					.focusRestorer(),
				horizontalArrangement = Arrangement.spacedBy(CinemaDimens.RowGap),
			) {
				itemsIndexed(users, key = { _, user -> user.id }) { index, user ->
					CinemaStartupUserCard(
						name = user.name,
						imageUrl = imageUrl(user),
						onClick = { onSelectUser(user) },
						onLongClick = if (user is PrivateUser) ({ menuUser = user }) else null,
						modifier = if (index == 0) Modifier.focusRequester(firstUserFocusRequester) else Modifier,
					)
				}
			}
		}

		val selectedMenuUser = menuUser
		if (selectedMenuUser != null) {
			CinemaPanel(Modifier.fillMaxWidth()) {
				Column(
					modifier = Modifier.padding(24.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					CinemaSectionTitle(stringResource(R.string.cinema_login_account_options, selectedMenuUser.name))
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						val canSignOut = selectedMenuUser is PrivateUser && selectedMenuUser.accessToken != null
						if (canSignOut) {
							CinemaButton(
								text = stringResource(R.string.lbl_sign_out),
								icon = painterResource(R.drawable.ic_logout),
								onClick = { onSignOut(selectedMenuUser); menuUser = null },
								modifier = Modifier.focusRequester(menuFocusRequester),
							)
						}
						CinemaButton(
							text = stringResource(R.string.lbl_remove),
							icon = painterResource(R.drawable.ic_trash),
							onClick = { onRemove(selectedMenuUser); menuUser = null },
							modifier = if (canSignOut) Modifier else Modifier.focusRequester(menuFocusRequester),
						)
						CinemaButton(
							text = stringResource(R.string.lbl_cancel),
							onClick = { menuUser = null },
						)
					}
				}
			}
		}

		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			CinemaButton(
				text = stringResource(R.string.add_user),
				icon = painterResource(R.drawable.ic_user_add),
				primary = true,
				onClick = onAddUser,
				modifier = Modifier.focusRequester(addUserFocusRequester),
			)
			CinemaButton(
				text = stringResource(R.string.help),
				icon = painterResource(R.drawable.ic_help),
				onClick = onHelp,
			)
		}

		if (!disclaimer.isNullOrBlank()) {
			Text(
				text = disclaimer,
				color = CinemaColors.Muted,
				fontSize = CinemaDimens.MetaSize,
				maxLines = 3,
			)
		}
	}

}

