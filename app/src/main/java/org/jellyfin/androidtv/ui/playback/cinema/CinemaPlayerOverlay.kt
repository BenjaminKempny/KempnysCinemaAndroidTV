package org.jellyfin.androidtv.ui.playback.cinema

import android.os.Build
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinema.rememberReducedMotion
import org.jellyfin.androidtv.util.getTimeFormatter
import org.koin.compose.koinInject
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Cinema player overlay for regular videos.
 *
 * The video keeps the full screen. With the controls open the big title logo sits in the top
 * start corner on a soft gradient (or inside a real letterbox bar), play / pause in the center
 * and the scrubbable timeline with the action row at the bottom. Audio, subtitles and "more"
 * open as glass panels above the action row. Logo and controls hide
 * together after [AUTO_HIDE_MS] while the video plays and stay while it is paused.
 */
@Composable
fun CinemaPlayerOverlay(
	state: CinemaPlayerOverlayState,
	host: CinemaPlayerHost,
	modifier: Modifier = Modifier,
) {
	val reducedMotion = rememberReducedMotion()
	val rootFocus = remember { FocusRequester() }
	val playFocus = remember { FocusRequester() }
	val timelineFocus = remember { FocusRequester() }
	val audioFocus = remember { FocusRequester() }
	val subtitleFocus = remember { FocusRequester() }
	val moreFocus = remember { FocusRequester() }
	val consumedKeys = remember { mutableSetOf<Int>() }

	var snapshot by remember { mutableStateOf(host.snapshot()) }
	LaunchedEffect(host) {
		while (isActive) {
			snapshot = host.snapshot()
			delay(if (state.visible) POLL_VISIBLE_MS else POLL_HIDDEN_MS)
		}
	}

	// Scrubbing: every step moves a preview position, the player only seeks once the user
	// stops for a moment.
	val scrub: (direction: Int, repeat: Int) -> Unit = { direction, repeat ->
		val baseMs = (if (direction < 0) host.rewindSeconds() else host.forwardSeconds()).coerceAtLeast(MIN_SCRUB_S) * 1000L
		val current = snapshot
		state.scrub(direction * scrubStep(baseMs, repeat, current.durationMs), current.positionMs, current.durationMs)
	}
	LaunchedEffect(state.scrubToken) {
		if (state.scrubPositionMs == null) return@LaunchedEffect
		delay(SCRUB_COMMIT_MS)
		state.commitScrub()?.let { target ->
			host.seekTo(target)
			snapshot = host.snapshot()
		}
	}
	// Keep the committed position until the player reports it, so the thumb does not jump back.
	LaunchedEffect(snapshot, state.pendingSeekMs) {
		val pending = state.pendingSeekMs ?: return@LaunchedEffect
		val reached = abs(snapshot.positionMs - pending) < PENDING_SEEK_TOLERANCE_MS
		val expired = System.currentTimeMillis() - state.pendingSeekAt > PENDING_SEEK_TIMEOUT_MS
		if (reached || expired) state.clearPendingSeek()
	}
	val displayPosition = state.scrubPositionMs ?: state.pendingSeekMs ?: snapshot.positionMs

	// Pausing (also via media keys) brings up logo, timeline and controls.
	LaunchedEffect(snapshot.paused) {
		if (snapshot.paused && !state.controlsVisible) state.showControls()
	}

	// Cinematic start: logo and metadata once per item, as soon as the video runs.
	var introPlayedFor by remember { mutableStateOf<String?>(null) }
	val infoKey = state.info?.key
	LaunchedEffect(infoKey, snapshot.playing) {
		if (infoKey != null && snapshot.playing && introPlayedFor != infoKey) {
			introPlayedFor = infoKey
			state.showIntro()
		}
	}

	// Auto hide while playing. Popups of the player take the window focus and pause it, the
	// same goes for the own panels and an active scrub.
	val windowFocused = LocalWindowInfo.current.isWindowFocused
	val holdOpen = state.panel != null || state.scrubPositionMs != null
	LaunchedEffect(state.visible, state.introOnly, state.autoHideEnabled, state.interaction, snapshot.playing, windowFocused, holdOpen) {
		if (!state.visible || !snapshot.playing || !windowFocused || holdOpen) return@LaunchedEffect
		if (!state.introOnly && !state.autoHideEnabled) return@LaunchedEffect
		delay(if (state.introOnly) INTRO_DURATION_MS else AUTO_HIDE_MS)
		state.hide()
	}

	// Place the focus: on a control while the controls are open, on the root otherwise so
	// the D-Pad keeps working on the bare video.
	LaunchedEffect(state.controlsVisible, state.openCount) {
		withFrameNanos { }
		if (state.panel != null) return@LaunchedEffect
		if (state.controlsVisible) {
			val target = if (state.focusTarget == CinemaPlayerFocusTarget.Timeline && host.canSeek()) timelineFocus else playFocus
			runCatching { target.requestFocus() }
		} else {
			runCatching { rootFocus.requestFocus() }
		}
	}

	// Closing a panel gives the focus back to the button that opened it.
	var panelWasOpen by remember { mutableStateOf(false) }
	LaunchedEffect(state.panel) {
		if (state.panel != null) {
			panelWasOpen = true
			return@LaunchedEffect
		}
		if (!panelWasOpen) return@LaunchedEffect
		panelWasOpen = false
		if (!state.controlsVisible) return@LaunchedEffect
		withFrameNanos { }
		val target = when (state.panelOrigin) {
			CinemaPlayerPanel.Audio -> audioFocus
			CinemaPlayerPanel.Subtitles -> subtitleFocus
			else -> moreFocus
		}
		if (runCatching { target.requestFocus() }.isFailure) runCatching { playFocus.requestFocus() }
	}

	BoxWithConstraints(
		modifier = modifier
			.fillMaxSize()
			.focusRequester(rootFocus)
			.onPreviewKeyEvent { event -> handleRootKey(event, state, host, consumedKeys, scrub) }
			.focusable(),
	) {
		val info = state.info
		val controlsActive = state.controlsVisible
		val interactive = controlsActive && state.panel == null
		val chromeAlpha by animateFloatAsState(
			targetValue = if (state.visible) 1f else 0f,
			animationSpec = tween(if (reducedMotion) 0 else if (state.visible) LOGO_ENTER_MS else LOGO_EXIT_MS),
			label = "cinemaPlayerChrome",
		)
		val controlsAlpha by animateFloatAsState(
			targetValue = if (controlsActive) 1f else 0f,
			animationSpec = tween(if (reducedMotion) 0 else CONTROLS_FADE_MS),
			label = "cinemaPlayerControls",
		)
		val dimAlpha by animateFloatAsState(
			targetValue = if (controlsActive && snapshot.paused) PAUSED_DIM else 0f,
			animationSpec = tween(if (reducedMotion) 0 else LOGO_ENTER_MS),
			label = "cinemaPlayerDim",
		)

		val letterbox = letterboxHeight(info?.videoAspect, snapshot.zoomFit, maxWidth, maxHeight)
		val usesLetterbox = letterbox >= MIN_LETTERBOX
		val logoMaxHeight = if (usesLetterbox) (letterbox - 32.dp).coerceIn(64.dp, LOGO_MAX_HEIGHT) else LOGO_MAX_HEIGHT
		val topPadding = if (usesLetterbox) ((letterbox - logoMaxHeight) / 2).coerceAtLeast(16.dp) else TOP_PADDING

		// Paused: darken the video slightly.
		Box(
			Modifier
				.fillMaxSize()
				.graphicsLayer { alpha = dimAlpha }
				.background(Color.Black),
		)

		// Top gradient — soft, no hard bar. A letterbox bar is black already, so less is needed.
		Box(
			Modifier
				.fillMaxWidth()
				.height(maxHeight * TOP_GRADIENT_FRACTION)
				.graphicsLayer { alpha = chromeAlpha }
				.background(
					Brush.verticalGradient(
						0f to Color.Black.copy(alpha = if (usesLetterbox) 0.5f else 0.75f),
						0.5f to Color.Black.copy(alpha = if (usesLetterbox) 0.18f else 0.3f),
						1f to Color.Transparent,
					)
				)
				.background(
					Brush.horizontalGradient(
						0f to Color.Black.copy(alpha = 0.35f),
						0.6f to Color.Transparent,
					)
				),
		)

		// Bottom gradient for the timeline and the action row.
		Box(
			Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth()
				.height(maxHeight * BOTTOM_GRADIENT_FRACTION)
				.graphicsLayer { alpha = controlsAlpha }
				.background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))),
		)

		if (info != null) {
			PlayerTopBar(
				info = info,
				state = state,
				paused = snapshot.paused,
				controlsActive = controlsActive,
				interactive = interactive,
				controlsAlpha = controlsAlpha,
				chromeAlpha = chromeAlpha,
				logoMaxHeight = logoMaxHeight,
				topPadding = topPadding,
				reducedMotion = reducedMotion,
				onBack = host::close,
			)
		}

		// Center: play / pause only. Seeking happens directly on the timeline.
		PlayerCircleButton(
			icon = painterResource(if (snapshot.playing) R.drawable.ic_pause else R.drawable.ic_play),
			contentDescription = stringResource(if (snapshot.playing) R.string.lbl_pause else R.string.lbl_play),
			size = 80.dp,
			iconSize = 34.dp,
			active = interactive,
			onClick = { state.poke(); host.playPause() },
			modifier = Modifier
				.align(Alignment.Center)
				.graphicsLayer { alpha = controlsAlpha }
				.focusRequester(playFocus),
		)

		PlayerBottomBar(
			snapshot = snapshot,
			positionMs = displayPosition,
			scrubbing = state.scrubPositionMs != null,
			host = host,
			active = interactive,
			timelineFocus = timelineFocus,
			audioFocus = audioFocus,
			subtitleFocus = subtitleFocus,
			moreFocus = moreFocus,
			onInteraction = state::poke,
			onScrub = scrub,
			onTimelineSelect = {
				state.poke()
				val target = state.commitScrub()
				if (target != null) {
					host.seekTo(target)
					snapshot = host.snapshot()
				} else {
					host.playPause()
				}
			},
			onOpenPanel = { state.openPanel(it) },
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.graphicsLayer { alpha = controlsAlpha },
		)

		PlayerPanelHost(
			state = state,
			host = host,
			reducedMotion = reducedMotion,
			maxHeight = maxHeight,
		)
	}
}

// region top bar

@Composable
private fun PlayerTopBar(
	info: CinemaPlayerInfo,
	state: CinemaPlayerOverlayState,
	paused: Boolean,
	controlsActive: Boolean,
	interactive: Boolean,
	controlsAlpha: Float,
	chromeAlpha: Float,
	logoMaxHeight: Dp,
	topPadding: Dp,
	reducedMotion: Boolean,
	onBack: () -> Unit,
) {
	val intro = state.introOnly
	val logoReveal = rememberReveal(
		visible = state.visible,
		enterOffset = if (intro) INTRO_LOGO_OFFSET else LOGO_OFFSET,
		enterDurationMs = if (intro) INTRO_LOGO_ENTER_MS else LOGO_ENTER_MS,
		delayMs = 0,
		reducedMotion = reducedMotion,
	)
	val metaReveal = rememberReveal(
		visible = state.visible,
		enterOffset = if (intro) INTRO_LOGO_OFFSET else LOGO_OFFSET,
		enterDurationMs = if (intro) INTRO_LOGO_ENTER_MS else LOGO_ENTER_MS,
		delayMs = META_DELAY_MS,
		reducedMotion = reducedMotion,
		scaleFrom = 1f,
	)

	Box(Modifier.fillMaxWidth()) {
		Column(Modifier.padding(start = BACK_START, top = topPadding, end = 320.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				// The back button only belongs to the open controls.
				PlayerCircleButton(
					icon = painterResource(R.drawable.ic_arrow_back),
					contentDescription = stringResource(R.string.cinema_back),
					size = BACK_SIZE,
					iconSize = 24.dp,
					active = interactive,
					onClick = onBack,
					idleBackground = Color.Black.copy(alpha = 0.3f),
					modifier = Modifier.graphicsLayer { alpha = controlsAlpha },
				)
				Spacer(Modifier.width(BACK_GAP))

				Box(Modifier.reveal(logoReveal)) {
					PlayerLogoOrTitle(info = info, maxHeight = logoMaxHeight)
				}
			}

			Column(
				modifier = Modifier
					.padding(start = BACK_SIZE + BACK_GAP, top = 14.dp)
					.reveal(metaReveal),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				info.metaPrimary?.let { PlayerMetaText(it, 18.sp, Color.White.copy(alpha = 0.82f)) }
				info.metaSecondary?.let { PlayerMetaText(it, 16.sp, Color.White.copy(alpha = 0.68f)) }
				if (paused && controlsActive) {
					Text(
						text = stringResource(R.string.cinema_player_paused).uppercase(),
						color = Color.White.copy(alpha = 0.88f),
						fontSize = 14.sp,
						fontWeight = FontWeight.SemiBold,
						letterSpacing = 0.18.em,
						maxLines = 1,
						modifier = Modifier.padding(top = 6.dp),
						style = TEXT_SHADOW,
					)
				}
			}
		}

		if (info.badges.isNotEmpty()) {
			Row(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(top = topPadding + 8.dp, end = 64.dp)
					.graphicsLayer { alpha = chromeAlpha },
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				info.badges.forEach { PlayerBadge(it) }
			}
		}
	}
}

/** The original logo when there is one, otherwise the title set in big type — never both. */
@Composable
private fun PlayerLogoOrTitle(
	info: CinemaPlayerInfo,
	maxHeight: Dp,
) {
	var logoFailed by remember(info.logoUrl) { mutableStateOf(false) }
	val logoUrl = info.logoUrl

	if (logoUrl != null && !logoFailed) {
		PlayerLogo(url = logoUrl, contentDescription = info.title, maxHeight = maxHeight, onError = { logoFailed = true })
	} else {
		Text(
			text = info.title,
			color = Color.White,
			fontSize = 40.sp,
			lineHeight = 44.sp,
			fontWeight = FontWeight.Bold,
			letterSpacing = (-0.02).em,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.widthIn(max = 680.dp),
			style = TEXT_SHADOW,
		)
	}
}

@Composable
private fun PlayerLogo(
	url: String,
	contentDescription: String,
	maxHeight: Dp,
	onError: () -> Unit,
) {
	val imageLoader = koinInject<ImageLoader>()
	val context = LocalContext.current
	val request = remember(context, url) { ImageRequest.Builder(context).data(url).build() }
	val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

	// Fixed box, the logo is fitted into it with its own aspect ratio — never cropped or
	// stretched.
	Box(
		Modifier
			.size(width = LOGO_MAX_WIDTH, height = maxHeight)
			// Very soft scrim behind the logo for bright scenes, not a visible card.
			.drawBehind {
				drawRect(
					Brush.radialGradient(
						colors = listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent),
						center = Offset(size.width * 0.35f, size.height / 2),
						radius = size.width * 0.6f,
					)
				)
			},
	) {
		// Subtle drop shadow: a dark, blurred copy of the logo.
		AsyncImage(
			model = request,
			imageLoader = imageLoader,
			contentDescription = null,
			contentScale = ContentScale.Fit,
			alignment = Alignment.CenterStart,
			colorFilter = ColorFilter.tint(Color.Black.copy(alpha = if (blurSupported) 0.5f else 0.3f)),
			modifier = Modifier
				.matchParentSize()
				.offset(y = if (blurSupported) 2.dp else 1.dp)
				.then(if (blurSupported) Modifier.blur(6.dp) else Modifier),
		)
		AsyncImage(
			model = request,
			imageLoader = imageLoader,
			contentDescription = contentDescription,
			contentScale = ContentScale.Fit,
			alignment = Alignment.CenterStart,
			modifier = Modifier.matchParentSize(),
			onError = { onError() },
		)
	}
}

@Composable
private fun PlayerMetaText(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color) = Text(
	text = text,
	color = color,
	fontSize = size,
	maxLines = 1,
	overflow = TextOverflow.Ellipsis,
	style = TEXT_SHADOW,
)

@Composable
private fun PlayerBadge(text: String) {
	Box(
		modifier = Modifier
			.clip(BADGE_SHAPE)
			.background(Color.Black.copy(alpha = 0.28f))
			.border(1.dp, Color.White.copy(alpha = 0.4f), BADGE_SHAPE)
			.padding(horizontal = 9.dp, vertical = 3.dp),
	) {
		Text(
			text = text,
			color = Color.White,
			fontSize = 13.sp,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1,
		)
	}
}

// endregion

// region bottom bar

@Composable
private fun PlayerBottomBar(
	snapshot: CinemaPlaybackSnapshot,
	positionMs: Long,
	scrubbing: Boolean,
	host: CinemaPlayerHost,
	active: Boolean,
	timelineFocus: FocusRequester,
	audioFocus: FocusRequester,
	subtitleFocus: FocusRequester,
	moreFocus: FocusRequester,
	onInteraction: () -> Unit,
	onScrub: (direction: Int, repeat: Int) -> Unit,
	onTimelineSelect: () -> Unit,
	onOpenPanel: (CinemaPlayerPanel) -> Unit,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val duration = snapshot.durationMs
	val position = positionMs.coerceIn(0, duration.coerceAtLeast(0))
	val showHours = duration >= HOUR_MS

	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(start = SIDE_PADDING, end = SIDE_PADDING, bottom = 44.dp),
	) {
		if (host.canSeek() && duration > 0) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				PlayerTimeText(formatPlayerTime(position, showHours))
				Spacer(Modifier.width(20.dp))
				PlayerTimeline(
					progress = position.toFloat() / duration,
					playedProgress = (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f),
					buffered = (snapshot.bufferedMs.toFloat() / duration).coerceIn(0f, 1f),
					scrubbing = scrubbing,
					scrubLabel = formatPlayerTime(position, showHours),
					active = active,
					focusRequester = timelineFocus,
					onScrub = { direction, repeat -> onInteraction(); onScrub(direction, repeat) },
					onSelect = onTimelineSelect,
					modifier = Modifier.weight(1f),
				)
				Spacer(Modifier.width(20.dp))
				PlayerTimeText(formatPlayerTime(duration, showHours))
			}
			Spacer(Modifier.height(14.dp))
		}

		Row(verticalAlignment = Alignment.CenterVertically) {
			val remainingLine = if (duration > 0) {
				val remainingMs = ((duration - position) / snapshot.speed.coerceAtLeast(0.1f)).roundToLong().coerceAtLeast(0)
				val endTime = context.getTimeFormatter().format(LocalDateTime.now().plusNanos(remainingMs * 1_000_000))
				listOf(
					stringResource(R.string.cinema_player_remaining, formatRemaining(remainingMs)),
					stringResource(R.string.cinema_player_ends_at, endTime),
				).joinToString(" · ")
			} else {
				null
			}

			Box(Modifier.weight(1f)) {
				if (remainingLine != null) {
					Text(
						text = remainingLine,
						color = Color.White.copy(alpha = 0.75f),
						fontSize = 16.sp,
						maxLines = 1,
						style = TEXT_SHADOW,
					)
				}
			}

			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				if (host.hasAudioChoice()) {
					PlayerPillButton(
						icon = painterResource(R.drawable.ic_select_audio),
						text = stringResource(R.string.cinema_player_audio),
						active = active,
						onClick = { onInteraction(); onOpenPanel(CinemaPlayerPanel.Audio) },
						modifier = Modifier.focusRequester(audioFocus),
					)
				}
				if (host.hasSubtitles()) {
					PlayerPillButton(
						icon = painterResource(R.drawable.ic_subtitles),
						text = stringResource(R.string.cinema_player_subtitles),
						active = active,
						onClick = { onInteraction(); onOpenPanel(CinemaPlayerPanel.Subtitles) },
						modifier = Modifier.focusRequester(subtitleFocus),
					)
				}
				PlayerPillButton(
					icon = painterResource(R.drawable.ic_more),
					text = stringResource(R.string.cinema_player_more),
					active = active,
					onClick = { onInteraction(); onOpenPanel(CinemaPlayerPanel.More) },
					modifier = Modifier.focusRequester(moreFocus),
				)
			}
		}
	}
}

@Composable
private fun formatRemaining(ms: Long): String {
	val totalMinutes = ceil(ms / MINUTE_MS.toDouble()).toLong().coerceAtLeast(1)
	val hours = totalMinutes / 60
	val minutes = totalMinutes % 60
	return if (hours > 0) stringResource(R.string.cinema_player_hours_minutes, hours.toInt(), minutes.toInt())
	else stringResource(R.string.cinema_player_minutes, minutes.toInt())
}

private fun formatPlayerTime(ms: Long, showHours: Boolean): String {
	val totalSeconds = ms / 1000
	val hours = totalSeconds / 3600
	val minutes = (totalSeconds % 3600) / 60
	val seconds = totalSeconds % 60
	return if (showHours) "%02d:%02d:%02d".format(hours, minutes, seconds)
	else "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun PlayerTimeText(text: String) = Text(
	text = text,
	color = Color.White,
	fontSize = 18.sp,
	fontWeight = FontWeight.Medium,
	maxLines = 1,
	style = TEXT_SHADOW,
)

/**
 * Timeline — 4 dp, 7 dp while focused. Left / right scrub along the track (holding the key
 * accelerates), the player seeks once the user stops. OK confirms a scrub or toggles playback.
 */
@Composable
private fun PlayerTimeline(
	progress: Float,
	playedProgress: Float,
	buffered: Float,
	scrubbing: Boolean,
	scrubLabel: String,
	active: Boolean,
	focusRequester: FocusRequester,
	onScrub: (direction: Int, repeat: Int) -> Unit,
	onSelect: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val trackHeight by animateDpAsState(if (focused) 7.dp else 4.dp, tween(150), label = "cinemaTimelineHeight")
	val thumbRadius by animateDpAsState(
		when {
			scrubbing -> 12.dp
			focused -> 10.dp
			else -> 6.dp
		},
		tween(150),
		label = "cinemaTimelineThumb",
	)
	val bubbleAlpha by animateFloatAsState(if (scrubbing) 1f else 0f, tween(150), label = "cinemaTimelineBubble")

	BoxWithConstraints(
		modifier = modifier
			.height(28.dp)
			.focusRequester(focusRequester)
			.focusProperties { canFocus = active }
			.onKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
				when (event.nativeKeyEvent.keyCode) {
					AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { onScrub(-1, event.nativeKeyEvent.repeatCount); true }
					AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { onScrub(1, event.nativeKeyEvent.repeatCount); true }
					AndroidKeyEvent.KEYCODE_DPAD_CENTER,
					AndroidKeyEvent.KEYCODE_ENTER,
					AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> { onSelect(); true }
					else -> false
				}
			}
			.focusable(interactionSource = interactionSource),
		contentAlignment = Alignment.CenterStart,
	) {
		val clamped = progress.coerceIn(0f, 1f)

		Canvas(Modifier.fillMaxWidth().height(28.dp)) {
			val height = trackHeight.toPx()
			val top = (size.height - height) / 2
			val radius = CornerRadius(height / 2, height / 2)

			drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, height), radius)
			drawRoundRect(Color.White.copy(alpha = 0.32f), Offset(0f, top), Size(size.width * buffered, height), radius)
			if (scrubbing) {
				// The part that is already played stays solid, the scrub preview is lighter.
				drawRoundRect(TIMELINE_PROGRESS.copy(alpha = 0.55f), Offset(0f, top), Size(size.width * clamped, height), radius)
				drawRoundRect(TIMELINE_PROGRESS, Offset(0f, top), Size(size.width * min(playedProgress, clamped), height), radius)
			} else {
				drawRoundRect(TIMELINE_PROGRESS, Offset(0f, top), Size(size.width * clamped, height), radius)
			}
			drawCircle(Color.White, thumbRadius.toPx(), Offset(size.width * clamped, size.height / 2))
		}

		// Time bubble above the thumb while scrubbing.
		if (bubbleAlpha > 0f) {
			val bubbleWidth = 108.dp
			val x = (maxWidth * clamped - bubbleWidth / 2).coerceIn(-bubbleWidth / 3, maxWidth - bubbleWidth * 2 / 3)
			Box(
				modifier = Modifier
					.offset(x = x, y = (-40).dp)
					.width(bubbleWidth)
					.graphicsLayer { alpha = bubbleAlpha }
					.clip(PILL_SHAPE)
					.background(GLASS_BUBBLE)
					.border(1.dp, GLASS_BORDER, PILL_SHAPE)
					.padding(vertical = 6.dp),
				contentAlignment = Alignment.Center,
			) {
				Text(text = scrubLabel, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
			}
		}
	}
}

/** Scrub step, growing while the key is held down. */
private fun scrubStep(baseMs: Long, repeat: Int, durationMs: Long): Long = when {
	repeat < 8 -> baseMs
	repeat < 24 -> max(baseMs, 30_000L)
	repeat < 48 -> max(baseMs, 60_000L)
	else -> max(max(baseMs, 120_000L), durationMs / 60)
}

// endregion

// region panels

/**
 * Glass panel for audio, subtitles and the "more" options. It floats above the action row
 * at the bottom end, the video stays visible around and through it.
 */
@Composable
private fun BoxScope.PlayerPanelHost(
	state: CinemaPlayerOverlayState,
	host: CinemaPlayerHost,
	reducedMotion: Boolean,
	maxHeight: Dp,
) {
	val panel = state.panel
	// Keep the last panel while the exit animation runs.
	val shownPanel = remember { mutableStateOf<CinemaPlayerPanel?>(null) }
	if (panel != null) shownPanel.value = panel

	val scrimAlpha by animateFloatAsState(
		targetValue = if (panel != null) 1f else 0f,
		animationSpec = tween(if (reducedMotion) 0 else PANEL_ENTER_MS),
		label = "cinemaPlayerPanelScrim",
	)
	if (scrimAlpha > 0f) {
		Box(
			Modifier
				.fillMaxSize()
				.graphicsLayer { alpha = scrimAlpha }
				.background(Brush.horizontalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))),
		)
	}

	AnimatedVisibility(
		visible = panel != null,
		enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(PANEL_ENTER_MS)) + slideInHorizontally(tween(PANEL_ENTER_MS, easing = ENTER_EASING)) { it / 4 },
		exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(PANEL_EXIT_MS)) + slideOutHorizontally(tween(PANEL_EXIT_MS)) { it / 6 },
		modifier = Modifier
			.align(Alignment.BottomEnd)
			.padding(end = SIDE_PADDING - 8.dp, bottom = PANEL_BOTTOM),
	) {
		val current = shownPanel.value ?: return@AnimatedVisibility
		AnimatedContent(
			targetState = current,
			transitionSpec = {
				if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
				else fadeIn(tween(PANEL_SWITCH_MS)) togetherWith fadeOut(tween(PANEL_SWITCH_MS))
			},
			label = "cinemaPlayerPanelContent",
		) { target ->
			val content = remember(target) { host.panelContent(target) }
			if (content == null) {
				LaunchedEffect(target) { state.dismissPanel() }
				return@AnimatedContent
			}

			PlayerPanel(
				content = content,
				showBack = state.panelParent != null,
				maxHeight = maxHeight - PANEL_BOTTOM - TOP_PADDING,
				onSelect = { option ->
					state.poke()
					val opens = option.opens
					if (opens != null) {
						state.openPanel(opens, parent = target)
					} else {
						when (val next = host.selectOption(target, option)) {
							target -> Unit
							null -> state.dismissPanel()
							else -> state.openPanel(next, parent = target)
						}
					}
				},
			)
		}
	}
}

@Composable
private fun PlayerPanel(
	content: CinemaPlayerPanelContent,
	showBack: Boolean,
	maxHeight: Dp,
	onSelect: (CinemaPlayerOption) -> Unit,
) {
	val requesters = remember(content) { content.options.map { FocusRequester() } }
	val initialIndex = remember(content) {
		content.options.indexOfFirst { it.selected && !it.header }
			.takeIf { it >= 0 }
			?: content.options.indexOfFirst { !it.header }
	}
	LaunchedEffect(content) {
		withFrameNanos { }
		if (initialIndex >= 0) runCatching { requesters[initialIndex].requestFocus() }
	}

	Column(
		modifier = Modifier
			.width(PANEL_WIDTH)
			.heightIn(max = maxHeight)
			.clip(PANEL_SHAPE)
			.background(GLASS_PANEL)
			.border(1.dp, GLASS_BORDER, PANEL_SHAPE)
			.padding(vertical = 20.dp),
	) {
		Row(
			modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			if (showBack) {
				Icon(
					painter = painterResource(R.drawable.ic_arrow_back),
					contentDescription = null,
					tint = Color.White.copy(alpha = 0.6f),
					modifier = Modifier.size(18.dp),
				)
			}
			Text(
				text = content.title,
				color = Color.White,
				fontSize = 22.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}

		Box(
			Modifier
				.padding(horizontal = 24.dp)
				.fillMaxWidth()
				.height(1.dp)
				.background(Color.White.copy(alpha = 0.1f))
		)
		Spacer(Modifier.height(8.dp))

		Column(
			modifier = Modifier
				.focusGroup()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 12.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			content.options.forEachIndexed { index, option ->
				if (option.header) PlayerPanelHeader(option.label)
				else PlayerPanelRow(option = option, focusRequester = requesters[index], onClick = { onSelect(option) })
			}
		}
	}
}

@Composable
private fun PlayerPanelHeader(text: String) = Text(
	text = text.uppercase(),
	color = Color.White.copy(alpha = 0.5f),
	fontSize = 12.sp,
	fontWeight = FontWeight.SemiBold,
	letterSpacing = 0.14.em,
	maxLines = 1,
	modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
)

@Composable
private fun PlayerPanelRow(
	option: CinemaPlayerOption,
	focusRequester: FocusRequester,
	onClick: () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val background by animateColorAsState(
		when {
			focused -> Color.White
			option.selected -> Color.White.copy(alpha = 0.08f)
			else -> Color.Transparent
		},
		tween(120),
		label = "cinemaPanelRowBackground",
	)
	val tint = if (focused) FOCUSED_CONTENT else Color.White
	val softTint = if (focused) FOCUSED_CONTENT.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f)

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 52.dp)
			.focusRequester(focusRequester)
			.clip(ROW_SHAPE)
			.background(background)
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.padding(horizontal = 14.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		option.icon?.let {
			Icon(painter = painterResource(it), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
		}

		Column(Modifier.weight(1f)) {
			Text(
				text = option.label,
				color = tint,
				fontSize = 17.sp,
				fontWeight = if (option.selected || focused) FontWeight.SemiBold else FontWeight.Medium,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			if (option.detail != null && option.opens == null) {
				Text(text = option.detail, color = softTint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}

		if (option.opens != null) {
			option.detail?.let {
				Text(text = it, color = softTint, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
			}
			Icon(
				painter = painterResource(R.drawable.ic_arrow_forward),
				contentDescription = null,
				tint = softTint,
				modifier = Modifier.size(16.dp),
			)
		} else if (option.selected) {
			Icon(
				painter = painterResource(R.drawable.ic_check),
				contentDescription = null,
				tint = if (focused) FOCUSED_CONTENT else TIMELINE_PROGRESS,
				modifier = Modifier.size(20.dp),
			)
		}
	}
}

// endregion

// region buttons

@Composable
private fun PlayerCircleButton(
	icon: Painter,
	contentDescription: String?,
	size: Dp,
	iconSize: Dp,
	active: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	label: String? = null,
	idleBackground: Color = Color.Black.copy(alpha = 0.38f),
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(150), label = "cinemaPlayerButtonScale")

	Box(
		modifier = modifier
			.size(size)
			.graphicsLayer { scaleX = scale; scaleY = scale }
			.clip(CircleShape)
			.background(if (focused) Color.White else idleBackground)
			.border(1.dp, if (focused) Color.Transparent else Color.White.copy(alpha = 0.16f), CircleShape)
			.focusProperties { canFocus = active }
			.clickable(interactionSource = interactionSource, indication = null, enabled = active, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		val tint = if (focused) FOCUSED_CONTENT else Color.White
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Icon(painter = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
			if (label != null) {
				Text(text = label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
			}
		}
	}
}

@Composable
private fun PlayerPillButton(
	icon: Painter,
	text: String,
	active: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(150), label = "cinemaPlayerPillScale")
	val tint = if (focused) FOCUSED_CONTENT else Color.White

	Row(
		modifier = modifier
			.height(48.dp)
			.graphicsLayer { scaleX = scale; scaleY = scale }
			.clip(PILL_SHAPE)
			.background(if (focused) Color.White else Color.Black.copy(alpha = 0.38f))
			.border(1.dp, if (focused) Color.Transparent else Color.White.copy(alpha = 0.16f), PILL_SHAPE)
			.focusProperties { canFocus = active }
			.clickable(interactionSource = interactionSource, indication = null, enabled = active, onClick = onClick)
			.padding(horizontal = 20.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Icon(painter = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
		Text(text = text, color = tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
	}
}

// endregion

// region keys

private fun handleRootKey(
	event: KeyEvent,
	state: CinemaPlayerOverlayState,
	host: CinemaPlayerHost,
	consumedKeys: MutableSet<Int>,
	scrub: (direction: Int, repeat: Int) -> Unit,
): Boolean {
	val code = event.nativeKeyEvent.keyCode
	if (event.type == KeyEventType.KeyUp) return consumedKeys.remove(code)
	if (event.type != KeyEventType.KeyDown) return false

	state.poke()

	if (code in BACK_KEYS) {
		when {
			state.panel != null -> state.closePanel()
			state.scrubPositionMs != null -> state.cancelScrub()
			state.visible -> state.hide()
			host.isSkipPromptVisible() -> host.dismissSkip()
			// Not handled: the fragment closes the player.
			else -> return false
		}
		consumedKeys += code
		return true
	}

	// Open controls handle their own keys.
	if (state.controlsVisible) return false

	val handled = when (code) {
		AndroidKeyEvent.KEYCODE_DPAD_CENTER,
		AndroidKeyEvent.KEYCODE_ENTER,
		AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
			if (!state.visible && host.isSkipPromptVisible()) host.confirmSkip()
			else state.showControls()
			true
		}

		AndroidKeyEvent.KEYCODE_DPAD_UP,
		AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
			state.showControls()
			true
		}

		AndroidKeyEvent.KEYCODE_DPAD_LEFT,
		AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
			if (host.canSeek()) {
				state.showControls(CinemaPlayerFocusTarget.Timeline)
				scrub(if (code == AndroidKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1, event.nativeKeyEvent.repeatCount)
			} else {
				state.showControls()
			}
			true
		}

		else -> false
	}

	if (handled) consumedKeys += code
	return handled
}

// endregion

// region animation

/** Reversible fade + slide (+ subtle scale) used for the logo and the metadata. */
@Stable
private class Reveal {
	val alpha = Animatable(0f)
	val offset = Animatable(0f)
	val scale = Animatable(1f)
}

@Composable
private fun rememberReveal(
	visible: Boolean,
	enterOffset: Float,
	enterDurationMs: Int,
	delayMs: Int,
	reducedMotion: Boolean,
	scaleFrom: Float = LOGO_SCALE_FROM,
): Reveal {
	val reveal = remember { Reveal() }

	// Keyed on visibility only: switching from the intro to the full controls must not
	// replay the animation. Cancelling the previous run makes it reversible mid-way.
	LaunchedEffect(visible) {
		if (visible) {
			if (reveal.alpha.value == 0f) {
				reveal.offset.snapTo(enterOffset)
				reveal.scale.snapTo(scaleFrom)
			}
			if (reducedMotion) {
				reveal.alpha.snapTo(1f)
				reveal.offset.snapTo(0f)
				reveal.scale.snapTo(1f)
				return@LaunchedEffect
			}
			if (delayMs > 0) delay(delayMs.toLong())
			val spec = tween<Float>(enterDurationMs, easing = ENTER_EASING)
			launch { reveal.alpha.animateTo(1f, spec) }
			launch { reveal.offset.animateTo(0f, spec) }
			launch { reveal.scale.animateTo(1f, spec) }
		} else {
			if (reducedMotion) {
				reveal.alpha.snapTo(0f)
				return@LaunchedEffect
			}
			val spec = tween<Float>(LOGO_EXIT_MS, easing = FastOutLinearInEasing)
			launch { reveal.alpha.animateTo(0f, spec) }
			launch { reveal.offset.animateTo(LOGO_EXIT_OFFSET, spec) }
		}
	}

	return reveal
}

private fun Modifier.reveal(reveal: Reveal): Modifier = graphicsLayer {
	alpha = reveal.alpha.value
	translationY = reveal.offset.value.dp.toPx()
	scaleX = reveal.scale.value
	scaleY = reveal.scale.value
	transformOrigin = TransformOrigin(0f, 0.5f)
}

// endregion

/** Height of a black bar above the video when it is wider than the screen, else 0. */
private fun letterboxHeight(videoAspect: Float?, zoomFit: Boolean, width: Dp, height: Dp): Dp {
	if (videoAspect == null || videoAspect <= 0f || !zoomFit || height <= 0.dp) return 0.dp
	val screenAspect = width / height
	if (videoAspect <= screenAspect * 1.02f) return 0.dp
	return ((height - width / videoAspect) / 2).coerceAtLeast(0.dp)
}

private val BACK_KEYS = setOf(
	AndroidKeyEvent.KEYCODE_BACK,
	AndroidKeyEvent.KEYCODE_BUTTON_B,
	AndroidKeyEvent.KEYCODE_ESCAPE,
)

private const val POLL_VISIBLE_MS = 250L
private const val POLL_HIDDEN_MS = 500L
private const val AUTO_HIDE_MS = 4_000L
private const val INTRO_DURATION_MS = 3_500L
private const val HOUR_MS = 3_600_000L
private const val MINUTE_MS = 60_000L

private const val CONTROLS_FADE_MS = 180
private const val LOGO_ENTER_MS = 250
private const val LOGO_EXIT_MS = 160
private const val INTRO_LOGO_ENTER_MS = 320
private const val META_DELAY_MS = 80
private const val LOGO_OFFSET = 8f
private const val INTRO_LOGO_OFFSET = 12f
private const val LOGO_EXIT_OFFSET = -6f
private const val LOGO_SCALE_FROM = 0.98f
private const val PAUSED_DIM = 0.25f
private const val TOP_GRADIENT_FRACTION = 0.36f
private const val BOTTOM_GRADIENT_FRACTION = 0.40f

private val ENTER_EASING = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val LOGO_MAX_WIDTH = 400.dp
private val LOGO_MAX_HEIGHT = 112.dp
private val MIN_LETTERBOX = 112.dp
private val TOP_PADDING = 48.dp
private val SIDE_PADDING = 88.dp
private val BACK_START = 32.dp
private val BACK_SIZE = 56.dp
private val BACK_GAP = 24.dp
private val BADGE_SHAPE = RoundedCornerShape(6.dp)
private val PILL_SHAPE = RoundedCornerShape(percent = 50)
private val FOCUSED_CONTENT = Color(0xFF142232)
private val TIMELINE_PROGRESS = Color(0xFFE1EDFA)
private val TEXT_SHADOW = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 8f))

// Scrubbing
private const val SCRUB_COMMIT_MS = 700L
private const val MIN_SCRUB_S = 5
private const val PENDING_SEEK_TOLERANCE_MS = 2_500L
private const val PENDING_SEEK_TIMEOUT_MS = 4_000L

// Glass panels — same dark glass as the cinema shell, translucent so the video shows through.
private const val PANEL_ENTER_MS = 220
private const val PANEL_EXIT_MS = 160
private const val PANEL_SWITCH_MS = 140
private val PANEL_WIDTH = 440.dp
private val PANEL_BOTTOM = 150.dp
private val PANEL_SHAPE = RoundedCornerShape(24.dp)
private val ROW_SHAPE = RoundedCornerShape(14.dp)
private val GLASS_PANEL = Brush.verticalGradient(listOf(Color(0xD1242E3B), Color(0xE00C1118)))
private val GLASS_BUBBLE = Color(0xD90E141C)
private val GLASS_BORDER = Color.White.copy(alpha = 0.14f)





