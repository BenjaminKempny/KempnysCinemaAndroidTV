package org.jellyfin.androidtv.ui.cinema

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text

/** Callbacks the host fragment wires up. */
data class CinemaProfileActions(
	val onBack: () -> Unit,
	val onHome: () -> Unit,
	val onSelectUser: (CinemaProfileUser) -> Unit,
	val onManageAccounts: () -> Unit,
	val onSignOut: () -> Unit,
	val onSettings: () -> Unit,
)

/**
 * The profile / account switcher in cinema styling: the active account as a large
 * capsule, every other account of the server as a round avatar card below it.
 */
@Composable
fun CinemaProfileScreen(
	state: CinemaProfileState,
	actions: CinemaProfileActions,
	modifier: Modifier = Modifier,
) {
	val firstUserFocusRequester = remember { FocusRequester() }
	var focusRequested by remember { mutableStateOf(false) }

	LaunchedEffect(state.users.isNotEmpty()) {
		if (state.users.isNotEmpty() && !focusRequested) {
			focusRequested = runCatching { firstUserFocusRequester.requestFocus() }.isSuccess
		}
	}

	CinemaBackground(modifier) {
		CinemaShell(
			modifier = Modifier
				.fillMaxSize()
				.padding(24.dp),
		) {
			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.focusRestorer(),
				contentPadding = PaddingValues(
					start = CinemaDimens.PageGutter,
					end = CinemaDimens.PageGutter,
					top = CinemaDimens.PageGutter,
					bottom = CinemaDimens.Overscan,
				),
				verticalArrangement = Arrangement.spacedBy(CinemaDimens.SectionGap),
			) {
				item(key = "nav") {
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						CinemaIconButton(
							icon = painterResource(R.drawable.ic_arrow_back),
							contentDescription = stringResource(R.string.cinema_back),
							onClick = actions.onBack,
						)
						CinemaIconButton(
							icon = painterResource(R.drawable.ic_house),
							contentDescription = stringResource(R.string.lbl_home),
							onClick = actions.onHome,
						)
					}
				}

				item(key = "current") {
					CurrentAccountCapsule(state = state, actions = actions)
				}

				if (state.users.size > 1) {
					item(key = "users-title") {
						CinemaSectionTitle(stringResource(R.string.cinema_profile_accounts))
					}

					item(key = "users") {
						LazyRow(
							modifier = Modifier
								.fillMaxWidth()
								.focusRestorer(),
							horizontalArrangement = Arrangement.spacedBy(CinemaDimens.RowGap),
						) {
							items(state.users, key = { it.id }) { user ->
								CinemaAccountCard(
									user = user,
									onClick = { actions.onSelectUser(user) },
									modifier = if (user.id == state.users.first().id) {
										Modifier.focusRequester(firstUserFocusRequester)
									} else {
										Modifier
									},
								)
							}
						}
					}
				}

				if (!state.loading && state.users.isEmpty()) {
					item(key = "empty") {
						Text(
							text = stringResource(R.string.cinema_empty),
							color = CinemaColors.Muted,
							fontSize = CinemaDimens.BodySize,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun CurrentAccountCapsule(
	state: CinemaProfileState,
	actions: CinemaProfileActions,
) {
	CinemaPanel(Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.padding(28.dp),
			horizontalArrangement = Arrangement.spacedBy(24.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			AccountAvatar(
				imageUrl = state.currentUser?.imageUrl,
				name = state.currentUser?.name.orEmpty(),
				size = CURRENT_AVATAR_SIZE,
			)

			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				Text(
					text = stringResource(R.string.cinema_profile_signed_in).uppercase(),
					color = CinemaColors.Muted,
					fontSize = CinemaDimens.EyebrowSize,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
				)

				Text(
					text = state.currentUser?.name.orEmpty(),
					color = CinemaColors.Text,
					fontSize = CinemaDimens.PageTitleSize,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)

				val server = listOfNotNull(state.serverName, state.serverAddress)
					.distinct()
					.joinToString("  ·  ")
				if (server.isNotBlank()) {
					Text(
						text = server,
						color = CinemaColors.TextSoft,
						fontSize = CinemaDimens.MetaSize,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				CinemaButton(
					text = stringResource(R.string.lbl_switch_user),
					icon = painterResource(R.drawable.ic_switch_users),
					primary = true,
					onClick = actions.onManageAccounts,
				)
				CinemaButton(
					text = stringResource(R.string.lbl_settings),
					icon = painterResource(R.drawable.ic_settings),
					onClick = actions.onSettings,
				)
				CinemaButton(
					text = stringResource(R.string.lbl_sign_out),
					icon = painterResource(R.drawable.ic_logout),
					onClick = actions.onSignOut,
				)
			}
		}
	}
}

/** A round avatar card, mirroring the web account picker. */
@Composable
private fun CinemaAccountCard(
	user: CinemaProfileUser,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	val border by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.Focus
			user.isCurrent -> CinemaColors.Accent
			else -> CinemaColors.Border
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaAccountBorder",
	)

	Column(
		modifier = modifier
			.width(CinemaDimens.RowCardWidth)
			.clip(CinemaDimens.CardShape)
			.onFocusChanged { focused = it.isFocused }
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.padding(vertical = 12.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Box(
			modifier = Modifier
				.size(ACCOUNT_AVATAR_SIZE)
				.clip(CinemaDimens.PillShape)
				.border(BORDER_WIDTH, border, CinemaDimens.PillShape),
			contentAlignment = Alignment.Center,
		) {
			AccountAvatar(
				imageUrl = user.imageUrl,
				name = user.name,
				size = ACCOUNT_AVATAR_SIZE,
			)
		}

		Text(
			text = user.name,
			color = if (focused || user.isCurrent) CinemaColors.Text else CinemaColors.Muted,
			fontSize = CinemaDimens.CardTitleSize,
			fontWeight = FontWeight.SemiBold,
			textAlign = TextAlign.Center,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		if (!user.quickSwitch && !user.isCurrent) {
			Text(
				text = stringResource(R.string.cinema_profile_requires_login),
				color = CinemaColors.Muted,
				fontSize = CinemaDimens.MetaSize,
				textAlign = TextAlign.Center,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun AccountAvatar(
	imageUrl: String?,
	name: String,
	size: Dp,
) {
	Box(
		modifier = Modifier
			.size(size)
			.clip(CinemaDimens.PillShape)
			.background(CinemaColors.CardPlaceholder),
		contentAlignment = Alignment.Center,
	) {
		when {
			imageUrl != null -> AsyncImage(
				model = imageUrl,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)

			name.isNotBlank() -> Text(
				text = name.take(1).uppercase(),
				color = CinemaColors.TextSoft,
				fontSize = CinemaDimens.PageTitleSize,
				fontWeight = FontWeight.Bold,
			)

			else -> Icon(
				painter = painterResource(R.drawable.ic_user),
				contentDescription = null,
				tint = CinemaColors.Muted,
				modifier = Modifier.size(size / 2),
			)
		}
	}
}

private val CURRENT_AVATAR_SIZE = 116.dp
private val ACCOUNT_AVATAR_SIZE = 96.dp
private val BORDER_WIDTH = 2.dp






