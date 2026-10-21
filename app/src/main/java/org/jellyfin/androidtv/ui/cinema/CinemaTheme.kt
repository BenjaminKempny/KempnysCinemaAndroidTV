package org.jellyfin.androidtv.ui.cinema

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.koin.compose.koinInject

/** Each fragment owns a composition, including screens opened outside the home shell. */
@Composable
fun CinemaTheme(content: @Composable () -> Unit) {
	val preferences = koinInject<UserPreferences>()
	val performanceMode by rememberPreference(preferences, UserPreferences.cinemaPerformanceModeEnabled)
	CompositionLocalProvider(LocalCinemaPerformanceMode provides performanceMode) {
		JellyfinTheme(content = content)
	}
}

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
	/** High-contrast fill of a focused button / pill, clearly distinct from [accent]. */
	val accentFocused: Color,
	val accentFocusedText: Color,
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
	/** Fill for page titles ("Filme", "Serien" …). `null` renders plain [text]. */
	val titleBrush: Brush?,
	/** Small pill marker drawn in front of section titles. `null` hides it. */
	val sectionMarker: Brush?,
	/** Colour of section titles. */
	val sectionTitle: Color,
	/** Fill of progress bars on cards. */
	val progress: Brush,
	/** Secondary warm accent (badges, small highlights). */
	val highlight: Color,
	val highlightText: Color,
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
	accentFocused = Color(0xFFFFFFFF),
	accentFocusedText = Color(0xFF0B1420),
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
	titleBrush = null,
	sectionMarker = null,
	sectionTitle = Color(0xFFF6F8FC),
	progress = SolidColor(Color(0xFFE1EDFA)),
	highlight = Color(0xFFE1EDFA),
	highlightText = Color(0xFF142232),
)

/**
 * Pastel tones shared by the light cinema palette and the light settings colour scheme.
 * Soft lavender is the lead hue, a warm peach / butter family is the second accent so the
 * page gets a little sunshine without becoming loud. Text uses a deep plum ink instead of
 * pure black so the page stays gentle but readable from the couch.
 */
object CinemaPastel {
	val Blush = Color(0xFFFCEEF3)
	val Lavender = Color(0xFFF1EDFB)
	val Mint = Color(0xFFE7F5F1)
	val Butter = Color(0xFFFFF5DD)

	val LavenderSoft = Color(0xFFE6DFF8)
	val LavenderMid = Color(0xFFC9BDF0)
	val LavenderStrong = Color(0xFF7F6CD0)
	val LavenderDeep = Color(0xFF6A55C2)

	val PeachSoft = Color(0xFFFFEBDC)
	val Peach = Color(0xFFFFC9A3)
	val PeachStrong = Color(0xFFF29A5E)
	val PeachDeep = Color(0xFFD9763A)
	val Apricot = Color(0xFFFFD98A)

	val Ink = Color(0xFF2B2540)
	val InkSoft = Color(0xFF4A4363)
	val InkMuted = Color(0xFF787291)

	val Paper = Color(0xFFFFFCFE)

	/** Diagonal blush -> lavender -> butter wash used as page background. */
	val PageBrush: Brush
		get() = Brush.linearGradient(
			0f to Blush,
			0.5f to Lavender,
			1f to Butter,
		)

	/** Lavender -> peach ink for page titles. Deep enough to stay readable on the pastel page. */
	val TitleBrush: Brush
		get() = Brush.horizontalGradient(listOf(LavenderDeep, Color(0xFFB0679E), PeachDeep))

	/** Vertical lavender -> peach pill in front of section titles. */
	val MarkerBrush: Brush
		get() = Brush.verticalGradient(listOf(LavenderStrong, PeachStrong))

	/** Lavender -> peach fill for progress bars. */
	val ProgressBrush: Brush
		get() = Brush.horizontalGradient(listOf(LavenderStrong, PeachStrong))
}

/**
 * Light counterpart of [CinemaDarkPalette]. Uses a soft pastel family (lavender, blush,
 * peach, butter) on paper-white surfaces with plum ink for text.
 */
val CinemaLightPalette = CinemaPalette(
	isLight = true,
	pageBase = Color(0xFFF6F2FC),
	pageGradientTop = CinemaPastel.Blush,
	pageGradientMid = CinemaPastel.Lavender,
	shellTop = Color(0xEBFFFCFE),
	shellBottom = Color(0xF2F4F0FC),
	border = Color(0x337F6CD0),
	borderStrong = Color(0x597F6CD0),
	text = CinemaPastel.Ink,
	textSoft = CinemaPastel.InkSoft,
	muted = CinemaPastel.InkMuted,
	accent = Color(0xFFB9AEEC),
	accentText = Color(0xFF231C47),
	// The pastel lavender is too close to its focused variant — a deep plum fill with white
	// text makes the focused button unmistakable from the couch.
	accentFocused = CinemaPastel.LavenderDeep,
	accentFocusedText = Color(0xFFFFFFFF),
	rail = CinemaPastel.Paper,
	railBlur = Color(0xE6FFFCFE),
	navActive = Color(0x59C9BDF0),
	navActiveText = Color(0xFF3B2F63),
	button = Color(0xE6FFFFFF),
	buttonFocused = CinemaPastel.LavenderSoft,
	heroBackground = Color(0xFFE6DFF5),
	cardPlaceholder = Color(0xFFEDE7F7),
	input = CinemaPastel.Paper,
	surface = CinemaPastel.Paper,
	focus = CinemaPastel.LavenderStrong,
	focusRing = Color(0x667F6CD0),
	focusSeparator = Color(0xE6FFFFFF),
	focusGlow = Color(0x807F6CD0),
	tag = Color(0xF2FFF4EA),
	tagBorder = Color(0x66F29A5E),
	star = Color(0xFFF2A33A),
	onMedia = OnMedia,
	onMediaSoft = OnMediaSoft,
	onMediaMuted = OnMediaMuted,
	pageBackground = CinemaPastel.PageBrush,
	shellBackground = Brush.verticalGradient(listOf(Color(0xEBFFFCFE), Color(0xF2F4F0FC))),
	heroScrimHorizontal = HeroScrimHorizontalBrush,
	heroScrimVertical = HeroScrimVerticalBrush,
	wideCardScrim = WideCardScrimBrush,
	shimmerColors = listOf(
		Color(0xFFECE6F7),
		Color(0xFFFFF6EC),
		Color(0xFFECE6F7),
	),
	titleBrush = CinemaPastel.TitleBrush,
	sectionMarker = CinemaPastel.MarkerBrush,
	sectionTitle = Color(0xFF3B2F63),
	progress = CinemaPastel.ProgressBrush,
	highlight = CinemaPastel.Peach,
	highlightText = Color(0xFF5A2E10),
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
