package org.jellyfin.androidtv.ui.cinema

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.Icon
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
	Box(
		modifier = modifier
			.clip(CinemaDimens.ShellShape)
			.background(CinemaColors.ShellBackground)
			.border(1.dp, CinemaColors.Border, CinemaDimens.ShellShape),
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
	Box(
		modifier = modifier
			.clip(CinemaDimens.PanelShape)
			.background(CinemaColors.Surface.copy(alpha = 0.55f))
			.border(1.dp, CinemaColors.Border, CinemaDimens.PanelShape),
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
 * A5 — pill button. Background animates from `cinema_button` to a slightly lighter shade
 * on focus over 160 ms (no outer focus ring).
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
			primary && focused -> CinemaColors.Accent.copy(alpha = 0.95f)
			primary -> CinemaColors.Accent
			focused -> CinemaColors.ButtonFocused
			else -> CinemaColors.Button
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaButtonBackground",
	)
	val contentColor = if (primary) CinemaColors.AccentText else CinemaColors.Text

	Row(
		modifier = modifier
			.height(CinemaDimens.ButtonHeight)
			.clip(CinemaDimens.PillShape)
			.background(background)
			.border(
				width = 1.dp,
				color = if (focused) CinemaColors.BorderStrong else CinemaColors.Border,
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
			focused && active -> CinemaColors.Accent.copy(alpha = 0.95f)
			active -> CinemaColors.Accent
			focused -> CinemaColors.ButtonFocused
			else -> CinemaColors.Button
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaIconButtonBackground",
	)

	Box(
		modifier = modifier
			.size(size)
			.clip(CinemaDimens.PillShape)
			.background(background)
			.border(1.dp, if (focused) CinemaColors.BorderStrong else CinemaColors.Border, CinemaDimens.PillShape)
			.onFocusChanged { focused = it.isFocused }
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			painter = icon,
			contentDescription = contentDescription,
			tint = if (active) CinemaColors.AccentText else CinemaColors.Text,
			modifier = Modifier.size(size * 0.44f),
		)
	}
}

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
) {
	val density = LocalDensity.current
	val tabWidths = remember(tabs.size) { mutableStateListOfZeros(tabs.size) }
	val tabRequesters = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
	val currentSelectedIndex by rememberUpdatedState(selectedIndex)
	var focusedIndex by remember { mutableStateOf<Int?>(null) }

	val pillIndex = (focusedIndex ?: selectedIndex).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
	val pillOffset = with(density) { tabWidths.take(pillIndex).sum().toDp() }
	val pillWidth = with(density) { tabWidths.getOrElse(pillIndex) { 0 }.toDp() }

	val reducedMotion = rememberReducedMotion()
	val animatedOffset by animateDpAsState(
		targetValue = pillOffset,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.navPill(),
		label = "cinemaPillOffset",
	)
	val animatedWidth by animateDpAsState(
		targetValue = pillWidth,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.navPill(),
		label = "cinemaPillWidth",
	)
	val pillColor by animateColorAsState(
		targetValue = if (focusedIndex != null) CinemaColors.Accent else CinemaColors.NavActive,
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
		if (animatedWidth > 0.dp) {
			Box(
				modifier = Modifier
					.offset(x = animatedOffset)
					.width(animatedWidth)
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
			active && focusedByRow -> CinemaColors.AccentText
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

/** Section heading used above rails and grids. */
@Composable
fun CinemaSectionTitle(
	text: String,
	modifier: Modifier = Modifier,
) = Text(
	text = text,
	modifier = modifier,
	color = CinemaColors.Text,
	fontSize = CinemaDimens.SectionTitleSize,
	fontWeight = FontWeight.Bold,
	maxLines = 1,
	overflow = TextOverflow.Ellipsis,
)

/** Full-bleed cinema page background (`--cinema-page-bg`). */
@Composable
fun CinemaBackground(
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) = Box(
	modifier = modifier
		.fillMaxSize()
		.background(CinemaColors.PageBase)
		.background(CinemaColors.PageBackground),
) {
	content()
}

internal val CinemaContentPadding = PaddingValues(
	horizontal = CinemaDimens.Overscan,
	vertical = 24.dp,
)

private fun mutableStateListOfZeros(size: Int) =
	androidx.compose.runtime.mutableStateListOf<Int>().apply { repeat(size) { add(0) } }
