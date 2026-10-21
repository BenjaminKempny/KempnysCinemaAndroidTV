package org.jellyfin.androidtv.ui.cinema

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon

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
 * Icon only — it never expands. A single pill slides vertically behind the icons: it
 * follows the D-Pad focus while the rail is focused and rests on the active entry
 * otherwise. Timing matches the top bar pill (A1: 360 ms, easeOutQuint) so both
 * indicators feel like one system.
 */
@Composable
fun CinemaRail(
	selected: CinemaRailItem,
	onSelect: (CinemaRailItem) -> Unit,
	modifier: Modifier = Modifier,
	/** Lets the content hand `DPAD_LEFT` over to the rail (spec §2.1). */
	focusRequester: FocusRequester? = null,
	/**
	 * Entries that can be focused. The others are still drawn (dimmed) so screens without a
	 * session — login, account picker — show the very same rail as the library pages.
	 */
	enabledItems: Set<CinemaRailItem> = CinemaRailItem.entries.toSet(),
	/** Library pages switch on focus; screens with dialogs behind an entry need a click. */
	selectOnFocus: Boolean = true,
) {
	val items = CinemaRailItem.entries
	val requesters = remember { List(items.size) { FocusRequester() } }
	var focusedItem by remember { mutableStateOf<CinemaRailItem?>(null) }
	val reducedMotion = rememberReducedMotion()
	LaunchedEffect(focusedItem) {
		if (selectOnFocus) focusedItem?.takeIf { it != selected }?.let(onSelect)
	}
	val entryItem = selected.takeIf { it in enabledItems } ?: items.firstOrNull { it in enabledItems }

	val activeItem = focusedItem ?: selected
	val activeIndex = items.indexOf(activeItem).coerceAtLeast(0)
	val step = CinemaDimens.RailItemSize + CinemaDimens.RailGap

	val pillOffset by animateDpAsState(
		targetValue = step * activeIndex,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.navPill(),
		label = "cinemaRailPillOffset",
	)
	val pillColor by animateColorAsState(
		targetValue = if (focusedItem != null) CinemaColors.Accent else CinemaColors.NavActive,
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaRailPillColor",
	)

	Box(
		modifier = modifier
			.fillMaxHeight()
			.padding(start = 24.dp, top = 24.dp, bottom = 24.dp),
		contentAlignment = Alignment.CenterStart,
	) {
		Box(
			modifier = Modifier
				.clip(CinemaDimens.ShellShape)
				.background(CinemaColors.Rail)
				.border(1.dp, CinemaColors.Border, CinemaDimens.ShellShape)
				.padding(vertical = 12.dp, horizontal = 8.dp)
				.onFocusChanged { if (!it.hasFocus) focusedItem = null }
				.focusProperties {
					onEnter = { entryItem?.let { requesters[it.ordinal].requestFocus() } }
				}
				.focusGroup(),
		) {
			// The sliding pill lives behind the icons.
			Box(
				modifier = Modifier
					.offset { IntOffset(0, pillOffset.roundToPx()) }
					.size(CinemaDimens.RailItemSize)
					.clip(CinemaDimens.PillShape)
					.background(pillColor),
			)

			Column(verticalArrangement = Arrangement.spacedBy(CinemaDimens.RailGap)) {
				items.forEach { item ->
					val enabled = item in enabledItems
					CinemaRailButton(
						item = item,
						active = item == activeItem,
						railFocused = focusedItem != null,
						enabled = enabled,
						onFocused = { focusedItem = item },
						onClick = { onSelect(item) },
						modifier = Modifier
							.focusRequester(requesters[item.ordinal])
							.then(if (item == entryItem && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
					)
				}
			}
		}
	}
}

@Composable
private fun CinemaRailButton(
	item: CinemaRailItem,
	active: Boolean,
	railFocused: Boolean,
	onFocused: () -> Unit,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
) {
	val interactionSource = remember { MutableInteractionSource() }

	val contentColor by animateColorAsState(
		targetValue = when {
			active && railFocused -> CinemaColors.AccentText
			active -> CinemaColors.NavActiveText
			!enabled -> CinemaColors.Muted.copy(alpha = DISABLED_ALPHA)
			else -> CinemaColors.Muted
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "cinemaRailIconTint",
	)

	Box(
		modifier = modifier
			.size(CinemaDimens.RailItemSize)
			.onFocusChanged { if (it.isFocused) onFocused() }
			.focusProperties { canFocus = enabled }
			.clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			painter = painterResource(item.iconRes),
			contentDescription = stringResource(item.labelRes),
			tint = contentColor,
			modifier = Modifier.size(CinemaDimens.RailIconSize),
		)
	}
}
private const val DISABLED_ALPHA = 0.35f
