package org.jellyfin.androidtv.ui.cinema

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A3 — Apple-TV style focus zoom. `scale(1.05)` over `350ms` with an easeOutQuad curve.
 * This is the core focus affordance of the cinema UI.
 *
 * Callers must set `clipToPadding = false` on the surrounding list, otherwise the scaled
 * card is clipped by the row bounds.
 */
fun Modifier.cinemaFocusZoom(
	focused: Boolean,
	scale: Float = CinemaMotion.FocusZoomScale,
): Modifier = composed {
	val reducedMotion = rememberReducedMotion()
	val target = if (focused) scale else 1f
	val animated by androidx.compose.animation.core.animateFloatAsState(
		targetValue = target,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.focusZoom(),
		label = "cinemaFocusZoom",
	)

	graphicsLayer {
		scaleX = animated
		scaleY = animated
		// Parallel elevation lift, matching the web drop-shadow on focus.
		shadowElevation = (animated - 1f) * 160f
	}
}

/**
 * A2 — soft focus glow used in the TV layout instead of the hover translation
 * (`cinema.scss:958-966`, `box-shadow: 0 0 0 6px rgba(169,207,255,0.25)`).
 */
fun Modifier.cinemaFocusGlow(
	focused: Boolean,
	cornerRadius: Dp = CinemaDimens.CardRadius,
	width: Dp = 6.dp,
): Modifier = composed {
	val reducedMotion = rememberReducedMotion()
	val color by androidx.compose.animation.animateColorAsState(
		targetValue = if (focused) CinemaColors.FocusRing else Color.Transparent,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.focusGlow(),
		label = "cinemaFocusGlow",
	)

	drawBehind {
		if (color.alpha == 0f) return@drawBehind
		val stroke = width.toPx()
		drawRoundRect(
			color = color,
			topLeft = Offset(-stroke / 2f, -stroke / 2f),
			size = Size(size.width + stroke, size.height + stroke),
			cornerRadius = CornerRadius(cornerRadius.toPx() + stroke / 2f),
			style = Stroke(width = stroke),
		)
	}
}

/**
 * §1.4 — the global D-Pad focus ring:
 * `outline: 3px solid #d4e8ff` with `3px` offset, a `6px` dark separator ring and a
 * `24px` glow. Drawn on top of the content so it survives the [cinemaFocusZoom] scale.
 */
fun Modifier.cinemaFocusRing(
	focused: Boolean,
	cornerRadius: Dp = CinemaDimens.CardRadius,
): Modifier = composed {
	val reducedMotion = rememberReducedMotion()
	val alpha by androidx.compose.animation.core.animateFloatAsState(
		targetValue = if (focused) 1f else 0f,
		animationSpec = if (reducedMotion) tween(0) else CinemaMotion.focusGlow(),
		label = "cinemaFocusRing",
	)

	drawWithContent {
		drawContent()
		if (alpha == 0f) return@drawWithContent

		val offset = 3.dp.toPx()
		val outline = 3.dp.toPx()
		val separator = 6.dp.toPx()

		// Dark separator sits underneath the accent outline and gives it contrast on
		// bright artwork.
		val separatorInset = -(offset + outline + separator / 2f)
		drawRoundRect(
			color = CinemaColors.FocusSeparator.copy(alpha = CinemaColors.FocusSeparator.alpha * alpha),
			topLeft = Offset(separatorInset, separatorInset),
			size = Size(size.width - separatorInset * 2f, size.height - separatorInset * 2f),
			cornerRadius = CornerRadius(cornerRadius.toPx() - separatorInset),
			style = Stroke(width = separator),
		)

		val outlineInset = -(offset + outline / 2f)
		drawRoundRect(
			color = CinemaColors.Focus.copy(alpha = alpha),
			topLeft = Offset(outlineInset, outlineInset),
			size = Size(size.width - outlineInset * 2f, size.height - outlineInset * 2f),
			cornerRadius = CornerRadius(cornerRadius.toPx() - outlineInset),
			style = Stroke(width = outline),
		)
	}
}

/** Convenience: the complete cinema focus treatment (zoom + glow + ring). */
fun Modifier.cinemaFocusable(
	focused: Boolean,
	cornerRadius: Dp = CinemaDimens.CardRadius,
): Modifier = this
	.cinemaFocusZoom(focused)
	.cinemaFocusGlow(focused, cornerRadius)
	.cinemaFocusRing(focused, cornerRadius)

/** A7 — skeleton shimmer, `2s` alternating ease-in-out. */
@Composable
fun rememberShimmerBrush(): Brush {
	val reducedMotion = rememberReducedMotion()
	if (reducedMotion) return Brush.linearGradient(CinemaColors.ShimmerColors)

	val transition = rememberInfiniteTransition(label = "cinemaShimmer")
	val progress by transition.animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			animation = tween(
				CinemaMotion.ShimmerDuration,
				easing = androidx.compose.animation.core.FastOutSlowInEasing,
			),
			repeatMode = RepeatMode.Reverse,
		),
		label = "cinemaShimmerProgress",
	)

	val shift = progress * 1000f
	return Brush.linearGradient(
		colors = CinemaColors.ShimmerColors,
		start = Offset(shift - 1000f, 0f),
		end = Offset(shift, 0f),
	)
}

/** Placeholder surface used while data is loading. */
fun Modifier.cinemaSkeleton(): Modifier = composed {
	background(rememberShimmerBrush())
}

/** Reads focus state from an [InteractionSource] for the cinema focus modifiers. */
@Composable
fun InteractionSource.isCinemaFocused(): Boolean = collectIsFocusedAsState().value
