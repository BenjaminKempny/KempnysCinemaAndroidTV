package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ApiClientErrorLoginState
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.auth.model.LoginState
import org.jellyfin.androidtv.auth.model.PendingQuickConnectState
import org.jellyfin.androidtv.auth.model.QuickConnectState
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.ServerUnavailableState
import org.jellyfin.androidtv.auth.model.ServerVersionNotSupported
import org.jellyfin.androidtv.auth.model.UnavailableQuickConnectState
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinema.CinemaButton
import org.jellyfin.androidtv.ui.cinema.CinemaColors
import org.jellyfin.androidtv.ui.cinema.CinemaDimens
import org.jellyfin.androidtv.ui.cinema.CinemaInitialFocus
import org.jellyfin.androidtv.ui.cinema.CinemaPanel
import org.jellyfin.androidtv.ui.cinema.CinemaStartupFrame
import org.jellyfin.androidtv.ui.cinema.CinemaTabRow
import org.jellyfin.androidtv.ui.cinema.CinemaTextInput
import org.jellyfin.androidtv.ui.cinema.CinemaTheme
import org.jellyfin.androidtv.ui.startup.UserLoginViewModel
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Sign in screen in the cinema design. Quick Connect and username/password are offered as
 * the segmented control in the header — the same place the library pages show
 * `All | Collections | Genres`.
 */
class UserLoginFragment : Fragment() {
	companion object {
		const val ARG_USERNAME = "user_name"
		const val ARG_SERVER_ID = "server_id"
		const val ARG_SKIP_QUICKCONNECT = "skip_quickconnect"
	}

	private val userLoginViewModel: UserLoginViewModel by activityViewModel()
	private val backgroundService: BackgroundService by inject()

	private val usernameArgument get() = arguments?.getString(ARG_USERNAME)?.ifBlank { null }
	private val serverIdArgument get() = arguments?.getString(ARG_SERVER_ID)?.ifBlank { null }
	private val skipQuickConnect get() = arguments?.getBoolean(ARG_SKIP_QUICKCONNECT) ?: false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		userLoginViewModel.forcedUsername = usernameArgument
		userLoginViewModel.setServer(serverIdArgument?.toUUIDOrNull())
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = content {
		val server by userLoginViewModel.server.collectAsState()
		val loginState by userLoginViewModel.loginState.collectAsState()
		val quickConnectState by userLoginViewModel.quickConnectState.collectAsState()

		LaunchedEffect(server) {
			// The cinema frame draws its own background; drop the server backdrop.
			backgroundService.clearBackgrounds()
		}

		CinemaTheme {
			UserLoginScreen(
				serverName = server?.name ?: "Jellyfin",
				forcedUsername = userLoginViewModel.forcedUsername,
				startWithPassword = skipQuickConnect,
				loginState = loginState,
				quickConnectState = quickConnectState,
				onStartQuickConnect = { userLoginViewModel.initiateQuickconnect() },
				onLogin = { username, password -> userLoginViewModel.login(username, password) },
				onCancel = { parentFragmentManager.popBackStack() },
			)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		userLoginViewModel.clearLoginState()
	}
}

@Composable
private fun UserLoginScreen(
	serverName: String,
	forcedUsername: String?,
	startWithPassword: Boolean,
	loginState: LoginState?,
	quickConnectState: QuickConnectState,
	onStartQuickConnect: suspend () -> Unit,
	onLogin: (String, String) -> Unit,
	onCancel: () -> Unit,
) {
	val quickConnectAvailable = quickConnectState != UnavailableQuickConnectState
	var method by rememberSaveable { mutableIntStateOf(if (startWithPassword) METHOD_PASSWORD else METHOD_QUICK_CONNECT) }
	if (!quickConnectAvailable) method = METHOD_PASSWORD

	val tabs = buildList {
		if (quickConnectAvailable) add(stringResource(R.string.cinema_login_tab_quickconnect))
		add(stringResource(R.string.cinema_login_tab_password))
	}
	val selectedTab = if (quickConnectAvailable) method else 0

	LaunchedEffect(Unit) { onStartQuickConnect() }

	CinemaStartupFrame(
		title = stringResource(R.string.lbl_sign_in),
		subtitle = stringResource(R.string.login_connect_to, serverName),
		headerCenter = {
			CinemaTabRow(
				tabs = tabs,
				selectedIndex = selectedTab,
				onSelect = { index ->
					method = if (quickConnectAvailable) index else METHOD_PASSWORD
				},
			)
		},
	) {
		when (method) {
			METHOD_QUICK_CONNECT -> QuickConnectContent(quickConnectState, onCancel)
			else -> CredentialsContent(forcedUsername, onLogin, onCancel)
		}

		loginState.messageRes()?.let { (res, args) ->
			Text(
				text = stringResource(res, *args),
				color = if (loginState == AuthenticatingState) CinemaColors.Muted else CinemaColors.Text,
				fontSize = CinemaDimens.BodySize,
				fontWeight = FontWeight.SemiBold,
			)
		}
	}

}

@Composable
private fun QuickConnectContent(
	state: QuickConnectState,
	onCancel: () -> Unit,
) {
	val cancelFocusRequester = remember { FocusRequester() }
	CinemaInitialFocus(cancelFocusRequester)

	CinemaPanel {
		Column(
			modifier = Modifier.padding(28.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			listOf(
				R.string.login_quickconnect_step_1,
				R.string.login_quickconnect_step_2,
				R.string.login_quickconnect_step_3,
			).forEach { step ->
				Text(
					text = stringResource(step),
					color = CinemaColors.TextSoft,
					fontSize = CinemaDimens.BodySize,
				)
			}

			Text(
				text = (state as? PendingQuickConnectState)?.code?.formatQuickConnectCode()
					?: stringResource(R.string.cinema_login_quickconnect_waiting),
				color = CinemaColors.Text,
				fontSize = if (state is PendingQuickConnectState) QUICK_CONNECT_CODE_SIZE else CinemaDimens.SectionTitleSize,
				fontWeight = FontWeight.Bold,
				modifier = Modifier.padding(top = 8.dp),
			)
		}
	}

	CinemaButton(
		text = stringResource(R.string.btn_cancel),
		onClick = onCancel,
		modifier = Modifier.focusRequester(cancelFocusRequester),
	)
}

@Composable
private fun CredentialsContent(
	forcedUsername: String?,
	onLogin: (String, String) -> Unit,
	onCancel: () -> Unit,
) {
	var username by rememberSaveable { mutableStateOf(forcedUsername.orEmpty()) }
	var password by rememberSaveable { mutableStateOf("") }
	var emptyUsername by remember { mutableStateOf(false) }
	val usernameFocusRequester = remember { FocusRequester() }
	val passwordFocusRequester = remember { FocusRequester() }

	val submit = {
		if (username.isBlank()) emptyUsername = true
		else {
			emptyUsername = false
			onLogin(username, password)
		}
	}

	CinemaInitialFocus(if (forcedUsername == null) usernameFocusRequester else passwordFocusRequester)

	Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
		CinemaTextInput(
			value = username,
			onValueChange = { username = it },
			label = stringResource(R.string.input_username),
			enabled = forcedUsername == null,
			imeAction = ImeAction.Next,
			onImeAction = { runCatching { passwordFocusRequester.requestFocus() } },
			modifier = Modifier.focusRequester(usernameFocusRequester),
		)

		CinemaTextInput(
			value = password,
			onValueChange = { password = it },
			label = stringResource(R.string.input_password),
			password = true,
			imeAction = ImeAction.Done,
			onImeAction = submit,
			modifier = Modifier.focusRequester(passwordFocusRequester),
		)

		if (emptyUsername) {
			Text(
				text = stringResource(R.string.login_username_field_empty),
				color = CinemaColors.Text,
				fontSize = CinemaDimens.BodySize,
				fontWeight = FontWeight.SemiBold,
			)
		}
	}

	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		CinemaButton(
			text = stringResource(R.string.action_login),
			icon = painterResource(R.drawable.ic_user),
			primary = true,
			onClick = submit,
		)
		CinemaButton(
			text = stringResource(R.string.btn_cancel),
			onClick = onCancel,
		)
	}
}

private fun LoginState?.messageRes(): Pair<Int, Array<Any>>? = when (this) {
	is ServerVersionNotSupported -> R.string.server_issue_outdated_version to arrayOf(
		server.version.orEmpty(),
		ServerRepository.recommendedServerVersion.toString(),
	)

	AuthenticatingState -> R.string.login_authenticating to emptyArray()
	RequireSignInState -> R.string.login_invalid_credentials to emptyArray()
	ServerUnavailableState,
	is ApiClientErrorLoginState -> R.string.login_server_unavailable to emptyArray()

	AuthenticatedState, null -> null
}

/** Add space after every 3 characters so "420420" becomes "420 420". */
private fun String.formatQuickConnectCode() = chunked(QUICK_CONNECT_GROUP).joinToString(" ")

private const val METHOD_QUICK_CONNECT = 0
private const val METHOD_PASSWORD = 1
private const val QUICK_CONNECT_GROUP = 3
private val QUICK_CONNECT_CODE_SIZE = 56.sp

