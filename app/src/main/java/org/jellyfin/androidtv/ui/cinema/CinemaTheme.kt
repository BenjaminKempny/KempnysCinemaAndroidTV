package org.jellyfin.androidtv.ui.cinema

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A full set of colour tokens for the cinema design. Two implementations exist: the ported
 * dark token set ([CinemaDarkPalette]) and a light counterpart ([CinemaLightPalette]).
 *
 * Tokens prefixed with `onMedia` are always rendered on top of artwork with a dark scrim and
 * therefore stay light in both palettes.
 */
@Immutable
data class CinemaPalette(
	val isLight: Boolean,
	val pageBase: Color,
	val pageGradientTop: Color,
	val pageGradientMid: Color,
	val shellTop: Color,
	val shellBottom: Color,
	val border: Color,
	val borderStrong: Color,
	val text: Color,
	val textSoft: Color,
	val muted: Color,
	val accent: Color,
	val accentText: Color,
	val rail: Color,
	val railBlur: Color,
	val navActive: Color,
	val navActiveText: Color,
	val button: Color,
	val buttonFocused: Color,
	val heroBackground: Color,
	val cardPlaceholder: Color,
	val input: Color,
	val surface: Color,
	val focus: Color,
	val focusRing: Color,
	val focusSeparator: Color,
	val focusGlow: Color,
	val tag: Color,
	val tagBorder: Color,
	val star: Color,
	val onMedia: Color,
	val onMediaSoft: Color,
	val onMediaMuted: Color,
	val pageBackground: Brush,
	val shellBackground: Brush,
	val heroScrimHorizontal: Brush,
	val heroScrimVertical: Brush,
	val wideCardScrim: Brush,
	val shimmerColors: List<Color>,
)

/**
 * Scrims sit on top of backdrops and posters. They stay dark in both palettes so the artwork
 * keeps its contrast and the overlaid text remains readable.
 */
private val HeroScrimHorizontalBrush = Brush.horizontalGradient(
	0f to Color(0xD9040A12),
	0.6f to Color(0x52040A12),
	1f to Color(0x14040A12),
)

private val HeroScrimVerticalBrush = Brush.verticalGradient(
	0f to Color.Transparent,
	0.2f to Color.Transparent,
	1f to Color(0xE00B0B13),
)

private val WideCardScrimBrush = Brush.verticalGradient(
	listOf(Color.Transparent, Color(0xE6030910)),
)

private val OnMedia = Color(0xFFF6F8FC)
private val OnMediaSoft = Color(0xFFD2DDEA)
private val OnMediaMuted = Color(0xFFB6C0CD)

/**
 * Colour tokens ported from the web dark token set (`_cinemaTokens.scss`).
 * Values mirror `res/values/cinema_colors.xml` so views and Compose stay in sync.
 */
val CinemaDarkPalette = CinemaPalette(
	isLight = false,
	pageBase = Color(0xFF080C12),
	pageGradientTop = Color(0xFF243142),
	pageGradientMid = Color(0xFF111821),
	shellTop = Color(0xEB28313E),
	shellBottom = Color(0xF50E131B),
	border = Color(0x24E5F0FF),
	borderStrong = Color(0x38E5F0FF),
	text = Color(0xFFF6F8FC),
	textSoft = Color(0xFFD2DDEA),
	muted = Color(0xFFB6C0CD),
	accent = Color(0xFFE1EDFA),
	accentText = Color(0xFF142232),
	rail = Color(0xFF1A222E),
	railBlur = Color(0xB31C2531),
	navActive = Color(0x29D8EAFF),
	navActiveText = Color(0xFFF0F7FF),
	button = Color(0xE0323D4C),
	buttonFocused = Color(0xE048586E),
	heroBackground = Color(0xFF172330),
	cardPlaceholder = Color(0xFF24303E),
	input = Color(0xFF202A38),
	surface = Color(0xFF1B2532),
	focus = Color(0xFFD4E8FF),
	focusRing = Color(0x40A9CFFF),
	focusSeparator = Color(0xCC0A1423),
	focusGlow = Color(0x80A9CFFF),
	tag = Color(0xB81A232F),
	tagBorder = Color(0x38E4EFFF),
	star = Color(0xFFF5B82E),
	onMedia = OnMedia,
	onMediaSoft = OnMediaSoft,
	onMediaMuted = OnMediaMuted,
	pageBackground = Brush.verticalGradient(
		0f to Color(0xFF243142),
		0.55f to Color(0xFF111821),
		1f to Color(0xFF080C12),
	),
	shellBackground = Brush.verticalGradient(listOf(Color(0xEB28313E), Color(0xF50E131B))),
	heroScrimHorizontal = HeroScrimHorizontalBrush,
	heroScrimVertical = HeroScrimVerticalBrush,
	wideCardScrim = WideCardScrimBrush,
	shimmerColors = listOf(
		Color(0xFF1C2735),
		Color(0xFF2B394A),
		Color(0xFF1C2735),
	),
)

/**
 * Light counterpart of [CinemaDarkPalette]. Keeps the same cool blue-grey hue family but
 * inverts the luminance so the shell reads as paper with dark ink.
 */
val CinemaLightPalette = CinemaPalette(
	isLight = true,
	pageBase = Color(0xFFF3F6FA),
	pageGradientTop = Color(0xFFFFFFFF),
	pageGradientMid = Color(0xFFEFF3F9),
	shellTop = Color(0xF2FFFFFF),
	shellBottom = Color(0xF7EEF2F8),
	border = Color(0x240B1E33),
	borderStrong = Color(0x3D0B1E33),
	text = Color(0xFF0E1620),
	textSoft = Color(0xFF2E3B4B),
	muted = Color(0xFF5B677A),
	accent = Color(0xFF16283C),
	accentText = Color(0xFFF4F8FD),
	rail = Color(0xFFFFFFFF),
	railBlur = Color(0xD9FFFFFF),
	navActive = Color(0x2416283C),
	navActiveText = Color(0xFF0E1620),
	button = Color(0xE6FFFFFF),
	buttonFocused = Color(0xFFE2EAF4),
	heroBackground = Color(0xFFDCE4EE),
	cardPlaceholder = Color(0xFFDDE4EC),
	input = Color(0xFFFFFFFF),
	surface = Color(0xFFFFFFFF),
	focus = Color(0xFF1B4F8A),
	focusRing = Color(0x551B4F8A),
	focusSeparator = Color(0xCCFFFFFF),
	focusGlow = Color(0x801B4F8A),
	tag = Color(0xD6FFFFFF),
	tagBorder = Color(0x380B1E33),
	star = Color(0xFFD99400),
	onMedia = OnMedia,
	onMediaSoft = OnMediaSoft,
	onMediaMuted = OnMediaMuted,
	pageBackground = Brush.verticalGradient(
		0f to Color(0xFFFFFFFF),
		0.55f to Color(0xFFEFF3F9),
		1f to Color(0xFFE7ECF4),
	),
	shellBackground = Brush.verticalGradient(listOf(Color(0xF2FFFFFF), Color(0xF7EEF2F8))),
	heroScrimHorizontal = HeroScrimHorizontalBrush,
	heroScrimVertical = HeroScrimVerticalBrush,
	wideCardScrim = WideCardScrimBrush,
	shimmerColors = listOf(
		Color(0xFFE3E9F1),
		Color(0xFFF4F7FB),
		Color(0xFFE3E9F1),
	),
)

/**
 * Holds the palette currently used by all cinema screens. Backed by a snapshot state so any
 * composable reading [CinemaColors] recomposes when the user toggles the theme.
 *
 * The value is restored from [org.jellyfin.androidtv.preference.UserPreferences.cinemaLightThemeEnabled]
 * by the cinema fragments and updated live from the settings toggle.
 */
object CinemaThemeState {
	var palette by mutableStateOf(CinemaDarkPalette)
		private set

	var isLight: Boolean
		get() = palette.isLight
		set(value) {
			val next = if (value) CinemaLightPalette else CinemaDarkPalette
			if (next != palette) palette = next
		}
}

