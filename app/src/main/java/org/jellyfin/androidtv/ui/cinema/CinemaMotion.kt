package org.jellyfin.androidtv.ui.cinema

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion specification ported from the web (spec §1.3). Every easing curve in the web
 * codebase is a cubic-bezier and maps 1:1 onto [CubicBezierEasing].
 */
object CinemaMotion {
	/** `cubic-bezier(0.22, 1, 0.36, 1)` — easeOutQuint, used by the nav pill (A1). */
	val EaseOutQuint: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

	/** `cubic-bezier(0.25, 0.46, 0.45, 0.94)` — easeOutQuad, used by the focus zoom (A3/A4). */
	val EaseOutQuad: Easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)

	/** A1 — nav pill slide. */
	const val NavPillDuration = 360
	const val NavPillOpacityDuration = 200

	/** A2 — card focus glow. */
	const val FocusGlowDuration = 120

	/** A3/A4 — card and tab focus zoom. */
	const val FocusZoomDuration = 350
	const val FocusZoomScale = 1.05f

	/** A5 — button hover/focus. */
	const val ButtonFocusDuration = 160

	/** A7 — skeleton shimmer. */
	const val ShimmerDuration = 2000

	/** Hero auto-rotation crossfade and interval (spec §2.2). */
	const val HeroCrossfadeDuration = 400
	const val HeroRotationIntervalMs = 8_000L

	fun <T> navPill(): FiniteAnimationSpec<T> = tween(NavPillDuration, easing = EaseOutQuint)
	fun <T> focusZoom(): FiniteAnimationSpec<T> = tween(FocusZoomDuration, easing = EaseOutQuad)
	fun <T> focusGlow(): FiniteAnimationSpec<T> = tween(FocusGlowDuration, easing = EaseOutQuad)
	fun <T> buttonFocus(): FiniteAnimationSpec<T> = tween(ButtonFocusDuration, easing = EaseOutQuad)
}

/**
 * A8 — respects the system "remove animations" accessibility setting. When the global
 * animator duration scale is 0 all cinema animations are skipped.
 */
@Composable
fun rememberReducedMotion(): Boolean {
	val context = LocalContext.current
	return remember(context) {
		Settings.Global.getFloat(
			context.contentResolver,
			Settings.Global.ANIMATOR_DURATION_SCALE,
			1f,
		) == 0f
	}
}
