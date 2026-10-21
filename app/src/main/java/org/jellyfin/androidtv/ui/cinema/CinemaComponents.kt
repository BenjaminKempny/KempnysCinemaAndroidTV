package org.jellyfin.androidtv.ui.cinema

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.Text

/**
 * `.cinemaShell` — the rounded panel that all cinema content lives in.
 * Radius 30 dp, gradient background, hairline border. The web applies a 24 px backdrop
 * blur here; per spec A6 the TV port always uses the static fallback colour because a
 * real backdrop blur is far too expensive on TV GPUs.
 */
@Composable
fun CinemaShell(
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	val performanceMode = LocalCinemaPerformanceMode.current
	Box(
		modifier = modifier
			.clip(CinemaDimens.ShellShape)
			.then(if (performanceMode) Modifier.background(CinemaColors.Surface) else Modifier.background(CinemaColors.ShellBackground))
			.then(if (performanceMode) Modifier else Modifier.border(1.dp, CinemaColors.Border, CinemaDimens.ShellShape)),
	) {
		content()
	}
}

/** A flat surface panel, used for the rails/sections inside the shell. */
@Composable
fun CinemaPanel(
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	val performanceMode = LocalCinemaPerformanceMode.current
	Box(
		modifier = modifier
			.clip(CinemaDimens.PanelShape)
			.background(if (performanceMode) CinemaColors.Surface else CinemaColors.Surface.copy(alpha = 0.55f))
			.then(if (performanceMode) Modifier else Modifier.border(1.dp, CinemaColors.Border, CinemaDimens.PanelShape)),
	) {
		content()
	}
}

/** `.cinemaTag` — meta chip used by the hero and the detail screen. */
@Composable
fun CinemaTag(
	text: String,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.clip(CinemaDimens.PillShape)
			.background(CinemaColors.Tag)
			.border(1.dp, CinemaColors.TagBorder, CinemaDimens.PillShape)
			.padding(horizontal = 14.dp, vertical = 5.dp),
	) {
		Text(
			text = text,
			color = CinemaColors.TextSoft,
			fontSize = CinemaDimens.TagTextSize,
			fontWeight = FontWeight.Medium,
			maxLines = 1,
		)
	}
}

/**
 * A5 — pill button. On focus the fill switches to the high-contrast [CinemaColors.AccentFocused]
 * tone, an outer focus ring is drawn and the button grows slightly, so the focused button
 * stays clearly visible on TV — also for the lavender primary buttons of the light palette.
 */
@Composable
fun CinemaButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	icon: Painter? = null,
	primary: Boolean = false,
	enabled: Boolean = true,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	val background by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.AccentFocused
			primary -> CinemaColors.Accent
			else -> CinemaColors.Button
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaButtonBackground",
	)
	val contentColor by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.AccentFocusedText
			primary -> CinemaColors.AccentText
			else -> CinemaColors.Text
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaButtonContent",
	)

	Row(
		modifier = modifier
			.height(CinemaDimens.ButtonHeight)
			.cinemaButtonFocus(focused, CinemaDimens.ButtonHeight / 2)
			.clip(CinemaDimens.PillShape)
			.background(background)
			.border(
				width = 1.dp,
				color = if (focused) Color.Transparent else CinemaColors.Border,
				shape = CinemaDimens.PillShape,
			)
			.onFocusChanged { focused = it.isFocused }
			.clickable(
				enabled = enabled,
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
			.padding(horizontal = 22.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(9.dp),
	) {
		if (icon != null) {
			Icon(
				painter = icon,
				contentDescription = null,
				tint = contentColor,
				modifier = Modifier.size(21.dp),
			)
		}

		Text(
			text = text,
			color = contentColor,
			fontSize = CinemaDimens.ButtonTextSize,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

/** Circular icon-only button (back / home / hero arrows). */
@Composable
fun CinemaIconButton(
	icon: Painter,
	contentDescription: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	size: Dp = CinemaDimens.ButtonHeight,
	active: Boolean = false,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	val background by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.AccentFocused
			active -> CinemaColors.Accent
			else -> CinemaColors.Button
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaIconButtonBackground",
	)
	val tint by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.AccentFocusedText
			active -> CinemaColors.AccentText
			else -> CinemaColors.Text
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaIconButtonTint",
	)

	Box(
		modifier = modifier
			.size(size)
			.cinemaButtonFocus(focused, size / 2)
			.clip(CinemaDimens.PillShape)
			.background(background)
			.border(1.dp, if (focused) Color.Transparent else CinemaColors.Border, CinemaDimens.PillShape)
			.onFocusChanged { focused = it.isFocused }
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			painter = icon,
			contentDescription = contentDescription,
			tint = tint,
			modifier = Modifier.size(size * 0.44f),
		)
	}
}

/**
 * Shared focus treatment of all cinema buttons: a slight zoom plus the accent focus ring
 * drawn outside the pill. Must be applied before the clip so the ring is not cut off.
 */
@Composable
private fun Modifier.cinemaButtonFocus(focused: Boolean, cornerRadius: Dp): Modifier {
	val reducedMotion = rememberReducedMotion()
	val scale by animateFloatAsState(
		targetValue = if (focused) BUTTON_FOCUS_SCALE else 1f,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.buttonFocus(),
		label = "cinemaButtonScale",
	)
	return this
		.graphicsLayer {
			scaleX = scale
			scaleY = scale
		}
		.cinemaFocusRing(focused, cornerRadius)
}

private const val BUTTON_FOCUS_SCALE = 1.06f

/**
 * A1 — the segmented control (`Alle | Sammlungen | Genres`) with a pill that *slides*
 * to the new position instead of being redrawn. Offset and width animate over 360 ms
 * with an easeOutQuint curve.
 *
 * Per spec the trigger changes from click to focus on TV: the pill follows the D-Pad
 * focus, falling back to the selected tab when the row is unfocused.
 */
@Composable
fun CinemaTabRow(
	tabs: List<String>,
	selectedIndex: Int,
	onSelect: (Int) -> Unit,
	modifier: Modifier = Modifier,
	/** `DPAD_LEFT` on the first tab enters the sidebar (spec §2.1). */
	leftFocusRequester: FocusRequester? = null,
) {
	val density = LocalDensity.current
	val tabWidths = remember(tabs.size) { mutableStateListOfZeros(tabs.size) }
	val tabRequesters = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
	val currentSelectedIndex by rememberUpdatedState(selectedIndex)
	var focusedIndex by remember { mutableStateOf<Int?>(null) }
	LaunchedEffect(focusedIndex) {
		focusedIndex?.takeIf { it != currentSelectedIndex }?.let(onSelect)
	}

	val pillIndex = (focusedIndex ?: selectedIndex).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
	val pillOffset = with(density) { tabWidths.take(pillIndex).sum().toDp() }
	val pillWidth = with(density) { tabWidths.getOrElse(pillIndex) { 0 }.toDp() }

	val reducedMotion = rememberReducedMotion()
	val animatedOffset = animateDpAsState(
		targetValue = pillOffset,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.navPill(),
		label = "cinemaPillOffset",
	)
	val animatedWidth = animateDpAsState(
		targetValue = pillWidth,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.navPill(),
		label = "cinemaPillWidth",
	)
	val pillColor by animateColorAsState(
		targetValue = if (focusedIndex != null) CinemaColors.AccentFocused else CinemaColors.NavActive,
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaPillColor",
	)

	Box(
		modifier = modifier
			.clip(CinemaDimens.PillShape)
			.background(CinemaColors.Rail)
			.border(1.dp, CinemaColors.Border, CinemaDimens.PillShape)
			.padding(5.dp)
			// Tracking focus on the group avoids the flicker that per-tab enter/exit
			// callbacks would cause while moving between two tabs.
			.onFocusChanged { if (!it.hasFocus) focusedIndex = null }
			// Entering the row with the D-Pad always lands on the active tab instead of
			// the geometrically closest one (usually "All").
			.focusProperties {
				onEnter = {
					// The requester is detached while the header is recycled by the
					// LazyColumn; without the guard the focus search would bail out and
					// fall through to the first focusable on the page (the hero).
					runCatching { tabRequesters.getOrNull(currentSelectedIndex)?.requestFocus() }
				}
			}
			.focusGroup(),
	) {
		// The sliding pill lives behind the labels.
		if (pillWidth > 0.dp) {
			Box(
				modifier = Modifier
					.offset { IntOffset(animatedOffset.value.roundToPx(), 0) }
					.layout { measurable, constraints ->
						val width = constraints.constrainWidth(animatedWidth.value.roundToPx())
						val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
						layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
					}
					.height(CinemaDimens.TabHeight)
					.clip(CinemaDimens.PillShape)
					.background(pillColor),
			)
		}

		Row {
			tabs.forEachIndexed { index, title ->
				CinemaTab(
					title = title,
					active = pillIndex == index,
					focusedByRow = focusedIndex != null,
					onFocused = { focusedIndex = index },
					onClick = { onSelect(index) },
					modifier = Modifier
						.focusRequester(tabRequesters[index])
						.railOnLeft(leftFocusRequester, index == 0)
						.onSizeChanged { tabWidths[index] = it.width },
				)
			}
		}
	}
}

@Composable
private fun CinemaTab(
	title: String,
	active: Boolean,
	focusedByRow: Boolean,
	onFocused: () -> Unit,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }

	val contentColor by animateColorAsState(
		targetValue = when {
			active && focusedByRow -> CinemaColors.AccentFocusedText
			active -> CinemaColors.NavActiveText
			else -> CinemaColors.Muted
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaTabTint",
	)

	Box(
		modifier = modifier
			.height(CinemaDimens.TabHeight)
			.defaultMinSize(minWidth = 140.dp)
			.onFocusChanged { if (it.isFocused) onFocused() }
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
			.padding(horizontal = 26.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = title,
			color = contentColor,
			fontSize = CinemaDimens.TabTextSize,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1,
		)
	}
}

/**
 * Section heading used above rails and grids. In the light palette a small lavender -> peach
 * pill sits in front of the title and the title itself uses a deeper plum.
 */
@Composable
fun CinemaSectionTitle(
	text: String,
	modifier: Modifier = Modifier,
) {
	val marker = CinemaColors.SectionMarker
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		if (marker != null) {
			Box(
				modifier = Modifier
					.width(6.dp)
					.height(24.dp)
					.clip(CinemaDimens.PillShape)
					.background(marker),
			)
		}

		Text(
			text = text,
			modifier = Modifier.weight(1f, fill = false),
			color = CinemaColors.SectionTitle,
			fontSize = CinemaDimens.SectionTitleSize,
			fontWeight = FontWeight.Bold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

/**
 * Large page title ("Filme", "Serien" …). Uses the palette's gradient ink when available
 * (light palette: lavender -> peach), otherwise plain text colour.
 */
@Composable
fun CinemaPageTitle(
	text: String,
	modifier: Modifier = Modifier,
	fontSize: TextUnit = CinemaDimens.PageTitleSize,
	maxLines: Int = 1,
) {
	val brush = CinemaColors.TitleBrush
	if (brush == null) {
		Text(
			text = text,
			modifier = modifier,
			color = CinemaColors.Text,
			fontSize = fontSize,
			fontWeight = FontWeight.Bold,
			maxLines = maxLines,
			overflow = TextOverflow.Ellipsis,
		)
	} else {
		// The shared Text composable always resolves a solid colour, so the brush needs BasicText.
		BasicText(
			text = text,
			modifier = modifier,
			style = LocalTextStyle.current.merge(
				TextStyle(
					brush = brush,
					fontSize = fontSize,
					fontWeight = FontWeight.Bold,
				)
			),
			maxLines = maxLines,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

/**
 * Full-bleed cinema page background (`--cinema-page-bg`).
 *
 * When a [backdropUrl] is given (detail and collection pages) the blurred artwork of the
 * item fills the page and a palette-tinted scrim keeps the content readable in both themes.
 */
@Composable
fun CinemaBackground(
	modifier: Modifier = Modifier,
	backdropUrl: String? = null,
	content: @Composable () -> Unit,
) {
	val performanceMode = LocalCinemaPerformanceMode.current
	Box(
		modifier = modifier
			.fillMaxSize()
			.background(CinemaColors.PageBase)
			.then(if (performanceMode) Modifier else Modifier.background(CinemaColors.PageBackground)),
	) {
		if (backdropUrl != null) {
			CinemaAsyncImage(
				model = backdropUrl,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)

			val base = CinemaColors.PageBase
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(
						Brush.verticalGradient(
							0f to base.copy(alpha = BACKDROP_SCRIM_TOP),
							0.5f to base.copy(alpha = BACKDROP_SCRIM_MID),
							1f to base.copy(alpha = BACKDROP_SCRIM_BOTTOM),
						)
					),
			)
		}

		content()
	}
}

private const val BACKDROP_SCRIM_TOP = 0.45f
private const val BACKDROP_SCRIM_MID = 0.65f
private const val BACKDROP_SCRIM_BOTTOM = 0.88f

internal val CinemaContentPadding = PaddingValues(
	horizontal = CinemaDimens.Overscan,
	vertical = 24.dp,
)

private fun mutableStateListOfZeros(size: Int) =
	androidx.compose.runtime.mutableStateListOf<Int>().apply { repeat(size) { add(0) } }
