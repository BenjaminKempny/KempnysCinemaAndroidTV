package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colour tokens ported from the web token set (`_cinemaTokens.scss`).
 *
 * Every token resolves against the palette that is currently active
 * ([CinemaThemeState.palette]), so switching between the dark and light appearance only
 * requires swapping that palette — all call sites keep working and recompose automatically
 * because the palette is held in a snapshot state.
 */
object CinemaColors {
	private inline val palette: CinemaPalette get() = CinemaThemeState.palette

	val PageBase: Color get() = palette.pageBase
	val PageGradientTop: Color get() = palette.pageGradientTop
	val PageGradientMid: Color get() = palette.pageGradientMid
	val ShellTop: Color get() = palette.shellTop
	val ShellBottom: Color get() = palette.shellBottom
	val Border: Color get() = palette.border
	val BorderStrong: Color get() = palette.borderStrong
	val Text: Color get() = palette.text
	val TextSoft: Color get() = palette.textSoft
	val Muted: Color get() = palette.muted
	val Accent: Color get() = palette.accent
	val AccentText: Color get() = palette.accentText
	val Rail: Color get() = palette.rail
	val RailBlur: Color get() = palette.railBlur
	val NavActive: Color get() = palette.navActive
	val NavActiveText: Color get() = palette.navActiveText
	val Button: Color get() = palette.button
	val ButtonFocused: Color get() = palette.buttonFocused
	val HeroBackground: Color get() = palette.heroBackground
	val CardPlaceholder: Color get() = palette.cardPlaceholder
	val Input: Color get() = palette.input
	val Surface: Color get() = palette.surface
	val Focus: Color get() = palette.focus
	val FocusRing: Color get() = palette.focusRing
	val FocusSeparator: Color get() = palette.focusSeparator
	val FocusGlow: Color get() = palette.focusGlow

	val Tag: Color get() = palette.tag
	val TagBorder: Color get() = palette.tagBorder
	val Star: Color get() = palette.star

	/** Text rendered on top of artwork (hero, wide cards). Always light, the scrim stays dark. */
	val OnMedia: Color get() = palette.onMedia
	val OnMediaSoft: Color get() = palette.onMediaSoft
	val OnMediaMuted: Color get() = palette.onMediaMuted

	/** Page background: `--cinema-page-bg` gradient stack flattened to a vertical gradient. */
	val PageBackground: Brush get() = palette.pageBackground

	/** `.cinemaShell` background gradient. */
	val ShellBackground: Brush get() = palette.shellBackground

	/** Hero scrim layer 1 — horizontal, start -> end (`cinema.scss:318`). */
	val HeroScrimHorizontal: Brush get() = palette.heroScrimHorizontal

	/** Hero scrim layer 2 — vertical, bottom -> top, transparent at 80%. */
	val HeroScrimVertical: Brush get() = palette.heroScrimVertical

	/** Wide card scrim (`cinema.scss:578`) — applied to the bottom 66% of the card. */
	val WideCardScrim: Brush get() = palette.wideCardScrim

	/** Skeleton shimmer gradient (`cinema.scss:762`). */
	val ShimmerColors: List<Color> get() = palette.shimmerColors
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
	val RailGap = 16.dp

	val RowGap = 20.dp
	val PosterCardWidth = 240.dp

	/**
	 * Grid geometry measured from the reference screenshots: seven poster columns fill the
	 * shell with a 12 dp gutter between them, so a card is ~108 dp wide on a 960 dp screen.
	 */
	const val GridColumns = 7
	val GridCardWidth = 108.dp
	/** Genre and recommendation rails use slightly larger cards than the dense grid. */
	val RowCardWidth = 150.dp

	/** Horizontal padding between the shell edge and its content. */
	val PageGutter = 32.dp

	/** Vertical rhythm between the header, hero, rails and the grid. */
	val SectionGap = 32.dp
	const val PosterAspect = 2f / 3f
	const val WideAspect = 16f / 10f
	const val HeroAspect = 2.35f
	val HeroMinHeight = 450.dp

	val GridMinCellWidth = 155.dp
	val GridVerticalGap = 28.dp
	val GridHorizontalGap = 20.dp

	/** Overscan safe padding for TV. */
	val Overscan = 48.dp

	val TabHeight = 48.dp
	val ButtonHeight = 48.dp
	val ProgressHeight = 3.dp
	val PlayBadgeSize = 36.dp

	// Typography — web rem values scaled ~1.3x for 10-foot viewing (spec §1.2 note).
	val HeroTitleSize = 56.sp
	val PageTitleSize = 38.sp
	val SectionTitleSize = 26.sp
	val CardTitleSize = 19.sp
	val CardSubtitleSize = 17.sp
	val TabTextSize = 18.sp
	val ButtonTextSize = 18.sp
	val BodySize = 18.sp
	val EyebrowSize = 14.sp
	val TagTextSize = 15.sp
	val MetaSize = 15.sp
}
