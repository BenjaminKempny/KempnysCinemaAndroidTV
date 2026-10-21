package org.jellyfin.androidtv.ui.cinema

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.settings.Routes

/**
 * Page frame for the screens shown before a session exists (account picker, login).
 *
 * It is a 1:1 copy of the library page layout ([CinemaHomeScreen]): the same page
 * background, the same icon rail at the same position and the same rounded shell with the
 * logo/header row and page title. Switching from the login into the library therefore only
 * swaps the shell content instead of the whole screen.
 *
 * The rail shows every entry so its size and position match the library page; without a
 * session only the profile entry (this page) and the settings entry can be used. The
 * settings open inside the shell, exactly like on the library pages.
 */
@Composable
fun CinemaStartupFrame(
	title: String,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	/** Shown centred in the header row, where the library pages show the tab row. */
	headerCenter: (@Composable () -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	val railFocusRequester = remember { FocusRequester() }
	val contentFocusRequester = remember { FocusRequester() }
	// The settings are shown inside the shell (like on the library pages), not as dialog.
	var settingsOpen by rememberSaveable { mutableStateOf(false) }
	var settingsWereOpen by remember { mutableStateOf(false) }
	BackHandler(enabled = settingsOpen) { settingsOpen = false }
	LaunchedEffect(settingsOpen) {
		if (settingsOpen) {
			settingsWereOpen = true
		} else if (settingsWereOpen) {
			// Hand the focus back to the page once its content is composed again.
			repeat(RETURN_FOCUS_MAX_FRAMES) {
				withFrameNanos { }
				if (runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
			}
		}
	}

	CinemaBackground(modifier.fillMaxSize()) {
		Row(Modifier.fillMaxSize()) {
			CinemaRail(
				selected = if (settingsOpen) CinemaRailItem.Settings else CinemaRailItem.Profile,
				onSelect = { settingsOpen = it == CinemaRailItem.Settings },
				focusRequester = railFocusRequester,
				enabledItems = setOf(CinemaRailItem.Profile, CinemaRailItem.Settings),
				selectOnFocus = false,
			)

			if (settingsOpen) {
				CinemaSettingsPane(
					defaultRoute = Routes.AUTHENTICATION_FROM_LOGIN,
					railFocusRequester = railFocusRequester,
					requestInitialFocus = true,
				)
				return@Row
			}

			CinemaShell(
				modifier = Modifier
					.fillMaxSize()
					.focusRequester(contentFocusRequester)
					.keepVerticalFocusInside()
					.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
			) {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.padding(
							start = CinemaDimens.PageGutter,
							end = CinemaDimens.PageGutter,
							top = CinemaDimens.PageGutter,
							bottom = CinemaDimens.Overscan,
						),
					verticalArrangement = Arrangement.spacedBy(CinemaDimens.SectionGap),
				) {
					CinemaStartupHeader(title = title, subtitle = subtitle, center = headerCenter)
					content()
				}
			}
		}
	}
}

/** Same geometry as the private `CinemaHeader` of the library pages. */
@Composable
internal fun CinemaStartupHeader(
	title: String,
	subtitle: String?,
	center: (@Composable () -> Unit)?,
) {
	Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
		Box(Modifier.fillMaxWidth()) {
			Image(
				painter = painterResource(
					if (CinemaThemeState.isLight) R.drawable.kempnys_logo_header_light
					else R.drawable.kempnys_logo_header
				),
				contentDescription = null,
				modifier = Modifier
					.align(Alignment.CenterStart)
					.size(52.dp),
			)

			if (center != null) {
				Box(Modifier.align(Alignment.Center)) { center() }
			}
		}

		Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text(
				text = title,
				color = CinemaColors.Text,
				fontSize = CinemaDimens.PageTitleSize,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
			)

			if (!subtitle.isNullOrBlank()) {
				Text(
					text = subtitle,
					color = CinemaColors.Muted,
					fontSize = CinemaDimens.BodySize,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

/** Round account card of the account picker (same look as the profile page). */
@Composable
fun CinemaStartupUserCard(
	name: String,
	imageUrl: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onLongClick: (() -> Unit)? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	val border by animateColorAsState(
		targetValue = if (focused) CinemaColors.Focus else CinemaColors.Border,
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaStartupUserBorder",
	)

	Column(
		modifier = modifier
			.width(CinemaDimens.RowCardWidth)
			.clip(CinemaDimens.CardShape)
			.onFocusChanged { focused = it.isFocused }
			.combinedClickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
				onLongClick = onLongClick,
			)
			.padding(vertical = 12.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Box(
			modifier = Modifier
				.size(USER_AVATAR_SIZE)
				.clip(CinemaDimens.PillShape)
				.background(CinemaColors.CardPlaceholder)
				.border(if (focused) 3.dp else 2.dp, border, CinemaDimens.PillShape),
			contentAlignment = Alignment.Center,
		) {
			when {
				imageUrl != null -> CinemaAsyncImage(
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
					modifier = Modifier.size(USER_AVATAR_SIZE / 2),
				)
			}
		}

		Text(
			text = name,
			color = if (focused) CinemaColors.Text else CinemaColors.Muted,
			fontSize = CinemaDimens.CardTitleSize,
			fontWeight = FontWeight.SemiBold,
			textAlign = TextAlign.Center,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

/** Pill shaped text input matching the search field. */
@Composable
fun CinemaTextInput(
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	password: Boolean = false,
	imeAction: ImeAction = ImeAction.Next,
	onImeAction: () -> Unit = {},
	width: Dp = INPUT_WIDTH,
) {
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier.alpha(if (enabled) 1f else DISABLED_INPUT_ALPHA),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Text(
			text = label,
			color = CinemaColors.Muted,
			fontSize = CinemaDimens.MetaSize,
			fontWeight = FontWeight.SemiBold,
		)

		BasicTextField(
			value = value,
			onValueChange = onValueChange,
			enabled = enabled,
			singleLine = true,
			visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
			modifier = Modifier
				.width(width)
				.height(CinemaDimens.ButtonHeight + 8.dp)
				.focusProperties { canFocus = enabled }
				.onFocusChanged { focused = it.isFocused },
			keyboardActions = KeyboardActions { onImeAction() },
			keyboardOptions = KeyboardOptions.Default.copy(
				keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
				imeAction = imeAction,
				showKeyboardOnFocus = true,
			),
			textStyle = TextStyle(
				color = CinemaColors.Text,
				fontSize = CinemaDimens.BodySize,
			),
			cursorBrush = SolidColor(CinemaColors.Accent),
			decorationBox = { innerTextField ->
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxSize()
						.clip(CinemaDimens.PillShape)
						.background(if (focused) CinemaColors.ButtonFocused else CinemaColors.Button)
						.border(
							width = if (focused) 2.dp else 1.dp,
							color = if (focused) CinemaColors.Focus else CinemaColors.Border,
							shape = CinemaDimens.PillShape,
						)
						.padding(horizontal = 22.dp),
				) {
					Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
						innerTextField()
					}
					Spacer(Modifier.width(4.dp))
				}
			},
		)
	}
}

private val USER_AVATAR_SIZE = 96.dp
private val INPUT_WIDTH = 460.dp
private const val DISABLED_INPUT_ALPHA = 0.6f

/**
 * Loading state between the login and the library: the library frame (rail + shell) with
 * the logo in the middle, so the page does not flash between the two screens.
 */
@Composable
fun CinemaSplashFrame(modifier: Modifier = Modifier) {
	CinemaBackground(modifier.fillMaxSize()) {
		Row(Modifier.fillMaxSize()) {
			CinemaRail(
				selected = CinemaRailItem.Movies,
				onSelect = {},
				enabledItems = emptySet(),
				selectOnFocus = false,
			)

			CinemaShell(
				modifier = Modifier
					.fillMaxSize()
					.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
			) {
				Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
					Image(
						painter = painterResource(R.drawable.kempnys_logo),
						contentDescription = null,
						modifier = Modifier.size(SPLASH_LOGO_SIZE),
					)
				}
			}
		}
	}
}

private val SPLASH_LOGO_SIZE = 320.dp

/**
 * Requests the focus for [requester] once the hosting view owns the window focus. A plain
 * `requestFocus()` in the first frame is lost on the startup screens because the fragment
 * view is attached before the window focus arrives.
 */
@Composable
fun CinemaInitialFocus(requester: FocusRequester, key: Any? = Unit) {
	val view = androidx.compose.ui.platform.LocalView.current
	androidx.compose.runtime.LaunchedEffect(key) {
		repeat(INITIAL_FOCUS_MAX_FRAMES) {
			androidx.compose.runtime.withFrameNanos { }
			if (view.hasWindowFocus() && runCatching { requester.requestFocus() }.getOrDefault(false) && view.hasFocus()) {
				return@LaunchedEffect
			}
		}
	}
}

private const val INITIAL_FOCUS_MAX_FRAMES = 180
private const val RETURN_FOCUS_MAX_FRAMES = 60



