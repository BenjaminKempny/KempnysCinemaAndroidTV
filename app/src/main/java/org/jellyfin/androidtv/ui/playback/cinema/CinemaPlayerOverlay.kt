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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import org.jellyfin.androidtv.ui.cinema.KempnyKinoBrand
import org.jellyfin.androidtv.ui.cinema.rememberReducedMotion
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

	// Closing the menu gives the focus back to the "⋯" button that opened it.
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
		if (runCatching { moreFocus.requestFocus() }.isFailure) runCatching { playFocus.requestFocus() }
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

		// Paused: darken the video slightly.
		Box(
			Modifier
				.fillMaxSize()
				.graphicsLayer { alpha = dimAlpha }
				.background(Color.Black),
		)

		// Top shade — strong and tall, also behind the intro logo.
		Box(
			Modifier
				.fillMaxWidth()
				.height(maxHeight * TOP_SHADE_FRACTION)
				.graphicsLayer { alpha = chromeAlpha }
				.background(
					Brush.verticalGradient(
						0f to Color.Black.copy(alpha = 0.88f),
						0.4f to Color.Black.copy(alpha = 0.55f),
						1f to Color.Transparent,
					)
				),
		)

		// Soft side shade so the logo at the start edge reads on bright scenes.
		Box(
			Modifier
				.fillMaxHeight()
				.width(maxWidth * SIDE_SHADE_FRACTION)
				.graphicsLayer { alpha = chromeAlpha }
				.background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent)),
		)

		// Bottom shade for the timeline and the action row.
		Box(
			Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth()
				.height(maxHeight * BOTTOM_SHADE_FRACTION)
				.graphicsLayer { alpha = controlsAlpha }
				.background(
					Brush.verticalGradient(
						0f to Color.Transparent,
						0.45f to Color.Black.copy(alpha = 0.6f),
						1f to Color.Black.copy(alpha = 0.92f),
					)
				),
		)

		if (info != null) {
			PlayerHeader(
				info = info,
				interactive = interactive,
				controlsAlpha = controlsAlpha,
				chromeAlpha = chromeAlpha,
				onBack = host::close,
			)

			PlayerLogoBlock(
				info = info,
				state = state,
				paused = snapshot.paused,
				controlsActive = controlsActive,
				reducedMotion = reducedMotion,
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = EDGE, top = maxHeight * LOGO_TOP_FRACTION),
			)
		}

		// Center: play / pause only. Seeking happens directly on the timeline.
		PlayerCircleButton(
			icon = painterResource(if (snapshot.playing) R.drawable.ic_pause else R.drawable.ic_play),
			contentDescription = stringResource(if (snapshot.playing) R.string.lbl_pause else R.string.lbl_play),
			size = PLAY_SIZE,
			iconSize = 24.dp,
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
			onOpenOptions = { state.openPanel(CinemaPlayerPanel.Options) },
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

// region header

/** Back button, small spaced title with the meta line below, badges in the top end corner. */
@Composable
private fun BoxScope.PlayerHeader(
	info: CinemaPlayerInfo,
	interactive: Boolean,
	controlsAlpha: Float,
	chromeAlpha: Float,
	onBack: () -> Unit,
) {
	Row(
		modifier = Modifier
			.align(Alignment.TopStart)
			.padding(start = EDGE - 8.dp, top = HEADER_TOP, end = 360.dp)
			.graphicsLayer { alpha = controlsAlpha },
		verticalAlignment = Alignment.CenterVertically,
	) {
		PlayerCircleButton(
			icon = painterResource(R.drawable.ic_arrow_back),
			contentDescription = stringResource(R.string.cinema_back),
			size = BACK_SIZE,
			iconSize = 26.dp,
			active = interactive,
			onClick = onBack,
			idleBackground = Color.Transparent,
			idleBorder = Color.Transparent,
		)
		Spacer(Modifier.width(18.dp))

		Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
			Text(
				text = info.title.uppercase(),
				color = Color.White,
				fontSize = 20.sp,
				fontWeight = FontWeight.SemiBold,
				letterSpacing = 0.16.em,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				style = TEXT_SHADOW,
			)
			info.metaPrimary?.let {
				Text(
					text = it,
					color = Color.White.copy(alpha = 0.72f),
					fontSize = 15.sp,
					letterSpacing = 0.06.em,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					style = TEXT_SHADOW,
				)
			}
		}
	}

	if (info.badges.isNotEmpty()) {
		Row(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = HEADER_TOP + 8.dp, end = EDGE)
				.graphicsLayer { alpha = chromeAlpha },
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			info.badges.forEach { PlayerBadge(it) }
		}
	}
}

// endregion

// region logo

/**
 * The film logo at the start edge below the header, with the meta line and the paused hint
 * below it. Also used for the cinematic intro.
 */
@Composable
private fun PlayerLogoBlock(
	info: CinemaPlayerInfo,
	state: CinemaPlayerOverlayState,
	paused: Boolean,
	controlsActive: Boolean,
	reducedMotion: Boolean,
	modifier: Modifier = Modifier,
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

	Column(modifier = modifier) {
		Box(Modifier.reveal(logoReveal)) {
			PlayerLogoOrTitle(info = info, maxHeight = LOGO_MAX_HEIGHT)
		}

		Column(
			modifier = Modifier
				.padding(top = 16.dp)
				.reveal(metaReveal),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			// Year · runtime · rating already sit in the header; only the episode title stays.
			info.metaSecondary?.let { PlayerMetaText(it, 18.sp, Color.White.copy(alpha = 0.75f)) }
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
			fontSize = 44.sp,
			lineHeight = 48.sp,
			fontWeight = FontWeight.Bold,
			letterSpacing = (-0.02).em,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.widthIn(max = LOGO_MAX_WIDTH),
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
	// stretched — and sticks to the start edge.
	Box(Modifier.size(width = LOGO_MAX_WIDTH, height = maxHeight)) {
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
			.clip(PILL_SHAPE)
			.background(Color.Black.copy(alpha = 0.3f))
			.border(1.dp, Color.White.copy(alpha = 0.55f), PILL_SHAPE)
			.padding(horizontal = 16.dp, vertical = 6.dp),
	) {
		Text(
			text = text,
			color = Color.White,
			fontSize = 15.sp,
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
	moreFocus: FocusRequester,
	onInteraction: () -> Unit,
	onScrub: (direction: Int, repeat: Int) -> Unit,
	onTimelineSelect: () -> Unit,
	onOpenOptions: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val duration = snapshot.durationMs
	val position = positionMs.coerceIn(0, duration.coerceAtLeast(0))
	val showHours = duration >= HOUR_MS
	val preview = rememberTrickplayPreview(host, position, enabled = scrubbing)

	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(start = EDGE, end = EDGE, bottom = BOTTOM_PADDING),
	) {
		if (host.canSeek() && duration > 0) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				PlayerTimeText(formatPlayerTime(position, showHours))
				Spacer(Modifier.width(22.dp))
				PlayerTimeline(
					progress = position.toFloat() / duration,
					playedProgress = (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f),
					buffered = (snapshot.bufferedMs.toFloat() / duration).coerceIn(0f, 1f),
					scrubbing = scrubbing,
					scrubLabel = formatPlayerTime(position, showHours),
					preview = preview,
					active = active,
					focusRequester = timelineFocus,
					onScrub = { direction, repeat -> onInteraction(); onScrub(direction, repeat) },
					onSelect = onTimelineSelect,
					modifier = Modifier.weight(1f),
				)
				Spacer(Modifier.width(22.dp))
				PlayerTimeText(formatPlayerTime(duration, showHours))
			}
			Spacer(Modifier.height(ROW_GAP))
		}

		Row(verticalAlignment = Alignment.CenterVertically) {
			KempnyKinoBrand(
				markHeight = 30.dp,
				titleSize = 16.sp,
				subtitleSize = 12.sp,
				shadow = true,
			)

			Spacer(Modifier.weight(1f))

			PlayerCircleButton(
				icon = painterResource(R.drawable.ic_more),
				contentDescription = stringResource(R.string.cinema_player_options),
				size = MORE_SIZE,
				iconSize = 26.dp,
				active = active,
				onClick = { onInteraction(); onOpenOptions() },
				modifier = Modifier.focusRequester(moreFocus),
			)
		}
	}
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

/** Loaded trickplay thumbnail: the tile sheet plus the region of the frame inside it. */
@Stable
private class TrickplayPreview(
	val sheet: ImageBitmap,
	val frame: CinemaTrickplayFrame,
)

/**
 * Trickplay thumbnail for [positionMs] while [enabled] (scrubbing). Keeps showing the last
 * loaded frame until the next tile sheet arrives so the preview does not flicker.
 */
@Composable
private fun rememberTrickplayPreview(host: CinemaPlayerHost, positionMs: Long, enabled: Boolean): TrickplayPreview? {
	var preview by remember { mutableStateOf<TrickplayPreview?>(null) }
	val frame = remember(enabled, positionMs / TRICKPLAY_BUCKET_MS) {
		if (enabled) host.trickplayFrame(positionMs) else null
	}
	LaunchedEffect(frame) {
		if (frame == null) {
			if (!enabled) preview = null
			return@LaunchedEffect
		}
		val sheet = runCatching { host.loadTrickplayTile(frame.tileKey) }.getOrNull() ?: return@LaunchedEffect
		preview = TrickplayPreview(sheet, frame)
	}
	return if (enabled) preview else null
}

/**
 * Timeline — 5 dp, 8 dp while focused, almost as wide as the screen. Left / right scrub along
 * the track (holding the key accelerates), the player seeks once the user stops. OK confirms a
 * scrub or toggles playback. While scrubbing a trickplay thumbnail (when available) and the
 * target time float above the thumb.
 */
@Composable
private fun PlayerTimeline(
	progress: Float,
	playedProgress: Float,
	buffered: Float,
	scrubbing: Boolean,
	scrubLabel: String,
	preview: TrickplayPreview?,
	active: Boolean,
	focusRequester: FocusRequester,
	onScrub: (direction: Int, repeat: Int) -> Unit,
	onSelect: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val trackHeight by animateDpAsState(if (focused) 8.dp else 5.dp, tween(150), label = "cinemaTimelineHeight")
	val thumbRadius by animateDpAsState(
		when {
			scrubbing -> 13.dp
			focused -> 11.dp
			else -> 7.dp
		},
		tween(150),
		label = "cinemaTimelineThumb",
	)
	val bubbleAlpha by animateFloatAsState(if (scrubbing) 1f else 0f, tween(150), label = "cinemaTimelineBubble")

	BoxWithConstraints(
		modifier = modifier
			.height(TIMELINE_HEIGHT)
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
		val trackWidth = maxWidth

		Canvas(Modifier.fillMaxWidth().height(TIMELINE_HEIGHT)) {
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

		// Thumbnail + time above the thumb while scrubbing. Placed so its bottom edge sits just
		// above the track and its centre follows the thumb (clamped to the track).
		if (bubbleAlpha > 0f) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(6.dp),
				modifier = Modifier
					.align(Alignment.TopStart)
					.graphicsLayer { alpha = bubbleAlpha }
					.layout { measurable, constraints ->
						val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
						val center = (trackWidth * clamped).roundToPx()
						val x = (center - placeable.width / 2)
							.coerceIn(-placeable.width / 4, trackWidth.roundToPx() - placeable.width * 3 / 4)
						layout(0, 0) { placeable.place(x, -placeable.height - 2.dp.roundToPx()) }
					},
			) {
				if (preview != null) TrickplayThumbnail(preview)

				Box(
					modifier = Modifier
						.width(108.dp)
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
}

@Composable
private fun TrickplayThumbnail(preview: TrickplayPreview) {
	val frame = preview.frame
	val aspect = if (frame.height > 0) frame.width.toFloat() / frame.height else 16f / 9f
	Canvas(
		Modifier
			.width(TRICKPLAY_WIDTH)
			.height(TRICKPLAY_WIDTH / aspect)
			.clip(THUMB_SHAPE)
			.border(2.dp, Color.White.copy(alpha = 0.85f), THUMB_SHAPE),
	) {
		drawImage(
			image = preview.sheet,
			srcOffset = IntOffset(frame.x, frame.y),
			srcSize = IntSize(
				frame.width.coerceAtMost(preview.sheet.width - frame.x),
				frame.height.coerceAtMost(preview.sheet.height - frame.y),
			),
			dstSize = IntSize(size.width.toInt(), size.height.toInt()),
		)
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
 * Glass menu that opens above the "⋯" button at the bottom end, with a small arrow pointing
 * at the button. Sub panels (audio, subtitles …) replace it in place.
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
				.background(Brush.horizontalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))),
		)
	}

	AnimatedVisibility(
		visible = panel != null,
		enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(PANEL_ENTER_MS)) + slideInVertically(tween(PANEL_ENTER_MS, easing = ENTER_EASING)) { it / 8 },
		exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(PANEL_EXIT_MS)) + slideOutVertically(tween(PANEL_EXIT_MS)) { it / 12 },
		modifier = Modifier
			.align(Alignment.BottomEnd)
			.padding(end = EDGE, bottom = PANEL_BOTTOM),
	) {
		val current = shownPanel.value ?: return@AnimatedVisibility
		Column(horizontalAlignment = Alignment.End) {
			AnimatedContent(
				targetState = current,
				transitionSpec = {
					if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
					else fadeIn(tween(PANEL_SWITCH_MS)) togetherWith fadeOut(tween(PANEL_SWITCH_MS))
				},
				contentAlignment = Alignment.BottomEnd,
				label = "cinemaPlayerPanelContent",
			) { target ->
				val content = remember(target) { host.panelContent(target) }
				if (content == null) {
					LaunchedEffect(target) { state.dismissPanel() }
					return@AnimatedContent
				}

				PlayerPanel(
					content = content,
					showTitle = target != CinemaPlayerPanel.Options,
					maxHeight = maxHeight - PANEL_BOTTOM - PANEL_ARROW_HEIGHT - HEADER_TOP,
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

			// Arrow pointing down at the centre of the "⋯" button.
			Canvas(
				Modifier
					.padding(end = MORE_SIZE / 2 - PANEL_ARROW_WIDTH / 2)
					.size(PANEL_ARROW_WIDTH, PANEL_ARROW_HEIGHT),
			) {
				val path = Path().apply {
					moveTo(0f, 0f)
					lineTo(size.width, 0f)
					lineTo(size.width / 2, size.height)
					close()
				}
				drawPath(path, PANEL_ARROW)
			}
		}
	}
}

@Composable
private fun PlayerPanel(
	content: CinemaPlayerPanelContent,
	showTitle: Boolean,
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
			.padding(vertical = 10.dp),
	) {
		if (showTitle) {
			Row(
				modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 10.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Icon(
					painter = painterResource(R.drawable.ic_arrow_back),
					contentDescription = null,
					tint = Color.White.copy(alpha = 0.6f),
					modifier = Modifier.size(18.dp),
				)
				Text(
					text = content.title,
					color = Color.White,
					fontSize = 20.sp,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			PanelDivider()
			Spacer(Modifier.height(6.dp))
		}

		Column(
			modifier = Modifier
				.focusGroup()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 10.dp),
		) {
			var previousWasRow = false
			content.options.forEachIndexed { index, option ->
				if (option.header) {
					PlayerPanelHeader(option.label)
					previousWasRow = false
				} else {
					if (previousWasRow) PanelDivider()
					PlayerPanelRow(option = option, focusRequester = requesters[index], onClick = { onSelect(option) })
					previousWasRow = true
				}
			}
		}
	}
}

@Composable
private fun PanelDivider() = Box(
	Modifier
		.padding(horizontal = 12.dp)
		.fillMaxWidth()
		.height(1.dp)
		.background(Color.White.copy(alpha = 0.09f))
)

@Composable
private fun PlayerPanelHeader(text: String) = Text(
	text = text.uppercase(),
	color = Color.White.copy(alpha = 0.5f),
	fontSize = 12.sp,
	fontWeight = FontWeight.SemiBold,
	letterSpacing = 0.14.em,
	maxLines = 1,
	modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp),
)

/** Icon, label with the current value below it, chevron for sub panels or a check mark. */
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
			.heightIn(min = 56.dp)
			.padding(vertical = 3.dp)
			.focusRequester(focusRequester)
			.clip(ROW_SHAPE)
			.background(background)
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.padding(horizontal = 14.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
	) {
		option.icon?.let {
			Icon(painter = painterResource(it), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
		}

		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(
				text = option.label,
				color = tint,
				fontSize = 17.sp,
				fontWeight = if (option.selected || focused || option.opens != null) FontWeight.SemiBold else FontWeight.Medium,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			option.detail?.let {
				Text(text = it, color = softTint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}

		if (option.opens != null) {
			Icon(
				painter = painterResource(R.drawable.ic_arrow_forward),
				contentDescription = null,
				tint = softTint,
				modifier = Modifier.size(18.dp),
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

/** Round glass button; white with dark content while focused. */
@Composable
private fun PlayerCircleButton(
	icon: Painter,
	contentDescription: String?,
	size: Dp,
	iconSize: Dp,
	active: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	idleBackground: Color = Color.Black.copy(alpha = 0.38f),
	idleBorder: Color = Color.White.copy(alpha = 0.2f),
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
			.border(1.dp, if (focused) Color.Transparent else idleBorder, CircleShape)
			.focusProperties { canFocus = active }
			.clickable(interactionSource = interactionSource, indication = null, enabled = active, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			painter = icon,
			contentDescription = contentDescription,
			tint = if (focused) FOCUSED_CONTENT else Color.White,
			modifier = Modifier.size(iconSize),
		)
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
			state.handleBack() -> Unit
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

// Shades: strong at the screen edges, fading out towards the picture.
private const val TOP_SHADE_FRACTION = 0.5f
private const val BOTTOM_SHADE_FRACTION = 0.55f
private const val SIDE_SHADE_FRACTION = 0.55f

private val ENTER_EASING = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Layout (dp on a 960 x 540 dp TV canvas).
private val EDGE = 40.dp
private val HEADER_TOP = 28.dp
private val BACK_SIZE = 52.dp
private const val LOGO_TOP_FRACTION = 0.18f
private val LOGO_MAX_WIDTH = 420.dp
private val LOGO_MAX_HEIGHT = 120.dp
private val PLAY_SIZE = 58.dp
private val MORE_SIZE = 56.dp
private val BOTTOM_PADDING = 24.dp
private val ROW_GAP = 12.dp
private val TIMELINE_HEIGHT = 32.dp

private val PILL_SHAPE = RoundedCornerShape(percent = 50)
private val FOCUSED_CONTENT = Color(0xFF142232)
private val TIMELINE_PROGRESS = Color(0xFFE1EDFA)
private val TEXT_SHADOW = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 8f))

// Scrubbing
private const val SCRUB_COMMIT_MS = 700L
private const val MIN_SCRUB_S = 5
private const val PENDING_SEEK_TOLERANCE_MS = 2_500L
private const val PENDING_SEEK_TIMEOUT_MS = 4_000L

// Trickplay preview
private const val TRICKPLAY_BUCKET_MS = 1_000L
private val TRICKPLAY_WIDTH = 176.dp
private val THUMB_SHAPE = RoundedCornerShape(10.dp)

// Glass panels, translucent so the video shows through.
private const val PANEL_ENTER_MS = 220
private const val PANEL_EXIT_MS = 160
private const val PANEL_SWITCH_MS = 140
private val PANEL_WIDTH = 400.dp
/** Panel (incl. arrow) ends just above the timeline. */
private val PANEL_BOTTOM = BOTTOM_PADDING + MORE_SIZE + ROW_GAP + TIMELINE_HEIGHT + 4.dp
private val PANEL_ARROW_WIDTH = 22.dp
private val PANEL_ARROW_HEIGHT = 10.dp
private val PANEL_SHAPE = RoundedCornerShape(22.dp)
private val ROW_SHAPE = RoundedCornerShape(14.dp)
private val GLASS_PANEL = Brush.verticalGradient(listOf(Color(0xE61E2530), Color(0xF00C1118)))
private val PANEL_ARROW = Color(0xF00C1118)
private val GLASS_BUBBLE = Color(0xD90E141C)
private val GLASS_BORDER = Color.White.copy(alpha = 0.14f)

