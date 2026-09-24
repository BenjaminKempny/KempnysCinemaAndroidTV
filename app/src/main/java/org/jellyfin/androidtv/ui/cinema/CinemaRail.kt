package org.jellyfin.androidtv.ui.cinema

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text

/** The destinations offered by the cinema sidebar (`.cinemaSidebar`). */
enum class CinemaRailItem(
	val iconRes: Int,
	val labelRes: Int,
) {
	Search(R.drawable.ic_search, R.string.lbl_search),
	Movies(R.drawable.ic_movie, R.string.lbl_movies),
	Shows(R.drawable.ic_tv, R.string.lbl_tv_series),
	Profile(R.drawable.ic_user, R.string.cinema_profile),
	Settings(R.drawable.ic_settings, R.string.lbl_settings),
}

/**
 * The fixed, vertically centred icon rail from the web (`.cinemaSidebar`, 64 px, pill shaped).
 *
 * On TV it is built as an **expanding nav rail**: collapsed it is 64 dp and icon-only,
 * when focus enters it animates to 240 dp and reveals the labels (spec §2.1). The
 * animation reuses the nav-pill timing (360 ms, easeOutQuint) so the rail and the
 * segmented control feel like one system.
 */
@Composable
fun CinemaRail(
	selected: CinemaRailItem,
	onSelect: (CinemaRailItem) -> Unit,
	modifier: Modifier = Modifier,
) {
	var expanded by remember { mutableStateOf(false) }
	val reducedMotion = rememberReducedMotion()

	val width by animateDpAsState(
		targetValue = if (expanded) CinemaDimens.RailWidthExpanded else CinemaDimens.RailWidth,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.navPill(),
		label = "cinemaRailWidth",
	)
	val labelAlpha by animateFloatAsState(
		targetValue = if (expanded) 1f else 0f,
		animationSpec = tween(
			durationMillis = if (reducedMotion) 0 else CinemaMotion.NavPillOpacityDuration,
		),
		label = "cinemaRailLabelAlpha",
	)

	Box(
		modifier = modifier
			.width(width)
			.fillMaxHeight()
			.padding(vertical = 24.dp),
		contentAlignment = Alignment.CenterStart,
	) {
		Column(
			modifier = Modifier
				.width(width)
				.clip(CinemaDimens.ShellShape)
				.background(CinemaColors.Rail)
				.border(1.dp, CinemaColors.Border, CinemaDimens.ShellShape)
				.padding(vertical = 12.dp, horizontal = 8.dp)
				.onFocusChanged { expanded = it.hasFocus }
				.focusGroup()
				.focusRestorer(),
			verticalArrangement = Arrangement.spacedBy(CinemaDimens.RailGap),
			horizontalAlignment = Alignment.Start,
		) {
			CinemaRailItem.entries.forEach { item ->
				CinemaRailButton(
					item = item,
					active = item == selected,
					showLabel = labelAlpha > 0f,
					labelAlpha = labelAlpha,
					onClick = { onSelect(item) },
				)
			}
		}
	}
}

@Composable
private fun CinemaRailButton(
	item: CinemaRailItem,
	active: Boolean,
	showLabel: Boolean,
	labelAlpha: Float,
	onClick: () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	val background by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.Accent
			active -> CinemaColors.NavActive
			else -> Color.Transparent
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaRailButtonBackground",
	)
	val contentColor = when {
		focused -> CinemaColors.AccentText
		active -> CinemaColors.NavActiveText
		else -> CinemaColors.Muted
	}

	Row(
		modifier = Modifier
			.height(CinemaDimens.RailItemSize)
			.cinemaFocusRing(focused, cornerRadius = CinemaDimens.RailItemSize / 2)
			.clip(CinemaDimens.PillShape)
			.background(background)
			.onFocusChanged { focused = it.isFocused }
			.focusable(true, interactionSource)
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Box(
			modifier = Modifier.size(CinemaDimens.RailItemSize),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				painter = painterResource(item.iconRes),
				contentDescription = stringResource(item.labelRes),
				tint = contentColor,
				modifier = Modifier.size(CinemaDimens.RailIconSize),
			)
		}

		if (showLabel) {
			Text(
				text = stringResource(item.labelRes),
				modifier = Modifier
					.alpha(labelAlpha)
					.padding(end = 20.dp),
				color = contentColor,
				fontSize = CinemaDimens.TabTextSize,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}
