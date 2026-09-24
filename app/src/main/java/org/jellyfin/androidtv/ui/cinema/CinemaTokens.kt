package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colour tokens ported from the web dark token set (`_cinemaTokens.scss`).
 * Values mirror `res/values/cinema_colors.xml` so views and Compose stay in sync.
 */
@Immutable
object CinemaColors {
	val PageBase = Color(0xFF080C12)
	val PageGradientTop = Color(0xFF243142)
	val PageGradientMid = Color(0xFF111821)
	val ShellTop = Color(0xEB28313E)
	val ShellBottom = Color(0xF50E131B)
	val Border = Color(0x24E5F0FF)
	val BorderStrong = Color(0x38E5F0FF)
	val Text = Color(0xFFF6F8FC)
	val TextSoft = Color(0xFFD2DDEA)
	val Muted = Color(0xFFB6C0CD)
	val Accent = Color(0xFFE1EDFA)
	val AccentText = Color(0xFF142232)
	val Rail = Color(0xFF1A222E)
	val RailBlur = Color(0xB31C2531)
	val NavActive = Color(0x29D8EAFF)
	val NavActiveText = Color(0xFFF0F7FF)
	val Button = Color(0xE0323D4C)
	val ButtonFocused = Color(0xE5E5F0FF)
	val HeroBackground = Color(0xFF172330)
	val CardPlaceholder = Color(0xFF24303E)
	val Input = Color(0xFF202A38)
	val Surface = Color(0xFF1B2532)
	val Focus = Color(0xFFD4E8FF)
	val FocusRing = Color(0x40A9CFFF)
	val FocusSeparator = Color(0xCC0A1423)
	val FocusGlow = Color(0x80A9CFFF)

	val Tag = Color(0xB81A232F)
	val TagBorder = Color(0x38E4EFFF)

	/** Page background: `--cinema-page-bg` gradient stack flattened to a vertical gradient. */
	val PageBackground = Brush.verticalGradient(
		0f to PageGradientTop,
		0.55f to PageGradientMid,
		1f to PageBase,
	)

	/** `.cinemaShell` background gradient. */
	val ShellBackground = Brush.verticalGradient(listOf(ShellTop, ShellBottom))

	/** Hero scrim layer 1 — horizontal, start -> end (`cinema.scss:318`). */
	val HeroScrimHorizontal = Brush.horizontalGradient(
		0f to Color(0xD9040A12),
		0.6f to Color(0x52040A12),
		1f to Color(0x14040A12),
	)

	/** Hero scrim layer 2 — vertical, bottom -> top, transparent at 80%. */
	val HeroScrimVertical = Brush.verticalGradient(
		0f to Color.Transparent,
		0.2f to Color.Transparent,
		1f to Color(0xE00B0B13),
	)

	/** Wide card scrim (`cinema.scss:578`) — applied to the bottom 66% of the card. */
	val WideCardScrim = Brush.verticalGradient(
		listOf(Color.Transparent, Color(0xE6030910)),
	)

	/** Skeleton shimmer gradient (`cinema.scss:762`). */
	val ShimmerColors = listOf(
		Color(0xFF1C2735),
		Color(0xFF2B394A),
		Color(0xFF1C2735),
	)
}

/**
 * Geometry tokens (spec §1.2). CSS pixel values are carried over 1:1 but typography and
 * focus targets are scaled for a 10-foot viewing distance.
 */
@Immutable
object CinemaDimens {
	val CardRadius = 14.dp
	val ThemeCardRadius = 12.dp
	val HeroRadius = 26.dp
	val ShellRadius = 30.dp
	val PosterRadius = 18.dp
	val PanelRadius = 26.dp
	val InputRadius = 18.dp

	val CardShape = RoundedCornerShape(CardRadius)
	val HeroShape = RoundedCornerShape(HeroRadius)
	val ShellShape = RoundedCornerShape(ShellRadius)
	val PanelShape = RoundedCornerShape(PanelRadius)
	val PillShape = RoundedCornerShape(percent = 50)

	val RailWidth = 64.dp
	val RailWidthExpanded = 240.dp
	val RailItemSize = 48.dp
	val RailIconSize = 23.dp
	val RailGap = 12.dp

	val RowGap = 18.dp
	val PosterCardWidth = 240.dp
	const val PosterAspect = 2f / 3f
	const val WideAspect = 16f / 10f
	const val HeroAspect = 2.35f
	val HeroMinHeight = 450.dp

	val GridMinCellWidth = 155.dp
	val GridVerticalGap = 26.dp
	val GridHorizontalGap = 20.dp

	/** Overscan safe padding for TV. */
	val Overscan = 48.dp

	val TabHeight = 48.dp
	val ButtonHeight = 48.dp
	val ProgressHeight = 3.dp
	val PlayBadgeSize = 36.dp

	// Typography — web rem values scaled ~1.3x for 10-foot viewing (spec §1.2 note).
	val HeroTitleSize = 56.sp
	val PageTitleSize = 44.sp
	val SectionTitleSize = 26.sp
	val CardTitleSize = 19.sp
	val CardSubtitleSize = 17.sp
	val TabTextSize = 18.sp
	val ButtonTextSize = 18.sp
	val BodySize = 18.sp
	val EyebrowSize = 14.sp
	val TagTextSize = 15.sp
}
