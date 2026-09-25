package org.jellyfin.androidtv.ui.cinema

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.model.AutomaticAuthenticateMethod
import org.jellyfin.androidtv.auth.model.LoginState
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.RequireSignInState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.User
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import java.util.UUID

/** One account card on the profile screen. */
data class CinemaProfileUser(
	val id: UUID,
	val name: String,
	val imageUrl: String?,
	val isCurrent: Boolean,
	/** `true` when a stored access token allows switching without typing a password. */
	val quickSwitch: Boolean,
)

data class CinemaProfileState(
	val loading: Boolean = true,
	val serverName: String? = null,
	val serverAddress: String? = null,
	val currentUser: CinemaProfileUser? = null,
	val users: List<CinemaProfileUser> = emptyList(),
	val busy: Boolean = false,
)

/** Data layer for the cinema profile / account switcher screen. */
class CinemaProfileViewModel(
	private val sessionRepository: SessionRepository,
	private val serverRepository: ServerRepository,
	private val serverUserRepository: ServerUserRepository,
	private val authenticationRepository: AuthenticationRepository,
	private val userRepository: UserRepository,
) : ViewModel() {
	private val _state = MutableStateFlow(CinemaProfileState())
	val state: StateFlow<CinemaProfileState> = _state.asStateFlow()

	private var server: Server? = null
	private var users: List<User> = emptyList()

	init {
		load()
	}

	fun load() {
		viewModelScope.launch {
			val session = sessionRepository.currentSession.value
			val server = session?.serverId?.let { serverRepository.getServer(it) }
			this@CinemaProfileViewModel.server = server

			if (server == null) {
				_state.update { it.copy(loading = false) }
				return@launch
			}

			val stored = serverUserRepository.getStoredServerUsers(server)
			val storedIds = stored.map { it.id }
			// Public users make accounts that were never used on this device visible too;
			// they simply require a password when selected.
			val public = serverUserRepository.getPublicServerUsers(server)
				.filterNot { it.id in storedIds }

			users = (stored + public).sortedBy { it.name.lowercase() }

			val mapped = users.map { user ->
				CinemaProfileUser(
					id = user.id,
					name = user.name,
					imageUrl = authenticationRepository.getUserImageUrl(server, user),
					isCurrent = user.id == session.userId,
					quickSwitch = user is PrivateUser && user.accessToken != null,
				)
			}

			val current = mapped.firstOrNull { it.isCurrent }
				?: userRepository.currentUser.value?.let { user ->
					CinemaProfileUser(
						id = user.id,
						name = user.name.orEmpty(),
						imageUrl = null,
						isCurrent = true,
						quickSwitch = true,
					)
				}

			_state.update {
				it.copy(
					loading = false,
					serverName = server.name,
					serverAddress = server.address,
					users = mapped,
					currentUser = current,
				)
			}
		}
	}

	/**
	 * Signs in as [userId] using the stored access token. Emits [RequireSignInState] when
	 * no token is available — the caller then falls back to the full login screen.
	 */
	fun authenticate(userId: UUID): Flow<LoginState> {
		val server = server ?: return flowOf(RequireSignInState)
		val user = users.firstOrNull { it.id == userId } ?: return flowOf(RequireSignInState)

		_state.update { it.copy(busy = true) }
		return authenticationRepository.authenticate(server, AutomaticAuthenticateMethod(user))
	}

	fun setBusy(busy: Boolean) {
		_state.update { it.copy(busy = busy) }
	}

	/** Drops the stored access token of the active account so it requires a login again. */
	fun signOut() {
		val session = sessionRepository.currentSession.value ?: return
		users.firstOrNull { it.id == session.userId }?.let(authenticationRepository::logout)
	}
}

