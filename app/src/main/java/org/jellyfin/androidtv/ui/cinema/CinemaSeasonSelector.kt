package org.jellyfin.androidtv.ui.cinema

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/**
 * Season dropdown of the series page: a pill button with the current season and a chevron,
 * opening a cinema styled list right below it. D-Pad navigable, Back closes it.
 */
@Composable
fun CinemaSeasonSelector(
	seasons: List<BaseItemDto>,
	selectedId: UUID?,
	onSelect: (UUID) -> Unit,
	modifier: Modifier = Modifier,
	/** Applied to the focusable button itself, e.g. for a [FocusRequester]. */
	buttonModifier: Modifier = Modifier,
) {
	var expanded by remember { mutableStateOf(false) }
	val selected = seasons.firstOrNull { it.id == selectedId }

	Box(modifier) {
		CinemaButton(
			text = selected?.name.orEmpty(),
			trailingIcon = painterResource(R.drawable.ic_chevron_down),
			onClick = { expanded = true },
			modifier = buttonModifier,
		)

		if (expanded) {
			SeasonDropdown(
				seasons = seasons,
				selectedId = selectedId,
				onDismiss = { expanded = false },
				onSelect = { id ->
					expanded = false
					if (id != selectedId) onSelect(id)
				},
			)
		}
	}
}

@Composable
private fun SeasonDropdown(
	seasons: List<BaseItemDto>,
	selectedId: UUID?,
	onDismiss: () -> Unit,
	onSelect: (UUID) -> Unit,
) {
	val density = LocalDensity.current
	val positionProvider = remember(density) {
		DropdownPositionProvider(
			gap = with(density) { DROPDOWN_GAP.roundToPx() },
			overscan = with(density) { CinemaDimens.Overscan.roundToPx() },
		)
	}
	val initialFocus = remember { FocusRequester() }

	Popup(
		popupPositionProvider = positionProvider,
		onDismissRequest = onDismiss,
		properties = PopupProperties(
			focusable = true,
			dismissOnBackPress = true,
			dismissOnClickOutside = true,
		),
	) {
		Column(
			modifier = Modifier
				.widthIn(min = DROPDOWN_MIN_WIDTH, max = DROPDOWN_MAX_WIDTH)
				.heightIn(max = DROPDOWN_MAX_HEIGHT)
				.shadow(18.dp, CinemaDimens.PanelShape)
				.background(CinemaColors.Surface, CinemaDimens.PanelShape)
				.border(1.dp, CinemaColors.Border, CinemaDimens.PanelShape)
				.verticalScroll(rememberScrollState())
				.padding(DROPDOWN_PADDING),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			seasons.forEachIndexed { index, season ->
				val isSelected = season.id == selectedId
				val focusTarget = isSelected || (selectedId == null && index == 0)

				SeasonDropdownItem(
					text = season.name.orEmpty(),
					selected = isSelected,
					onClick = { onSelect(season.id) },
					modifier = if (focusTarget) Modifier.focusRequester(initialFocus) else Modifier,
				)
			}
		}
	}

	LaunchedEffect(initialFocus) { runCatching { initialFocus.requestFocus() } }
}

@Composable
private fun SeasonDropdownItem(
	text: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	val background by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.AccentFocused
			selected -> CinemaColors.Button
			else -> Color.Transparent
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "seasonItemBackground",
	)
	val contentColor by animateColorAsState(
		targetValue = when {
			focused -> CinemaColors.AccentFocusedText
			selected -> CinemaColors.Accent
			else -> CinemaColors.Text
		},
		animationSpec = CinemaMotion.buttonFocus(),
		label = "seasonItemContent",
	)

	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(CinemaDimens.ButtonHeight)
			.background(background, CinemaDimens.PillShape)
			.onFocusChanged { focused = it.isFocused }
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			)
			.padding(horizontal = 18.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = text,
			color = contentColor,
			fontSize = CinemaDimens.ButtonTextSize,
			fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)

		Spacer(Modifier.width(12.dp))

		if (selected) {
			Icon(
				painter = painterResource(R.drawable.ic_check),
				contentDescription = null,
				tint = contentColor,
				modifier = Modifier.size(20.dp),
			)
		} else {
			Spacer(Modifier.size(20.dp))
		}
	}
}

/**
 * Opens the list below the button, aligned to its end edge. Flips above the button when
 * there is not enough room below; always stays inside the TV overscan area.
 */
private class DropdownPositionProvider(
	private val gap: Int,
	private val overscan: Int,
) : PopupPositionProvider {
	override fun calculatePosition(
		anchorBounds: IntRect,
		windowSize: IntSize,
		layoutDirection: LayoutDirection,
		popupContentSize: IntSize,
	): IntOffset {
		val x = if (layoutDirection == LayoutDirection.Ltr) {
			anchorBounds.right - popupContentSize.width
		} else {
			anchorBounds.left
		}

		val below = anchorBounds.bottom + gap
		val above = anchorBounds.top - gap - popupContentSize.height
		val fitsBelow = below + popupContentSize.height <= windowSize.height - overscan
		val y = if (fitsBelow || above < overscan) below else above

		val maxX = (windowSize.width - popupContentSize.width - overscan).coerceAtLeast(overscan)
		val maxY = (windowSize.height - popupContentSize.height - overscan).coerceAtLeast(overscan)
		return IntOffset(x = x.coerceIn(overscan, maxX), y = y.coerceIn(overscan, maxY))
	}
}

private val DROPDOWN_GAP = 10.dp
private val DROPDOWN_PADDING = 8.dp
private val DROPDOWN_MIN_WIDTH = 240.dp
private val DROPDOWN_MAX_WIDTH = 360.dp
private val DROPDOWN_MAX_HEIGHT = 380.dp

