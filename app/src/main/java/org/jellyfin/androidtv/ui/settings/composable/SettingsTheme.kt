package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.ColorScheme
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Typography
import org.jellyfin.androidtv.ui.base.TypographyDefaults
import org.jellyfin.androidtv.ui.base.colorScheme
import org.jellyfin.androidtv.ui.cinema.CinemaPastel
import org.jellyfin.androidtv.ui.cinema.CinemaThemeState

/**
 * Pastel colour scheme used by the settings when the light cinema appearance is enabled.
 */
fun settingsLightColorScheme(): ColorScheme = colorScheme().copy(
	background = Color(0xFFF6F2FC),
	onBackground = CinemaPastel.Ink,
	button = CinemaPastel.LavenderMid,
	onButton = CinemaPastel.Ink,
	buttonFocused = CinemaPastel.LavenderStrong,
	onButtonFocused = Color.White,
	buttonDisabled = Color(0x33A9A3BF),
	onButtonDisabled = Color(0xFF9A94AE),
	buttonActive = Color(0x66C9BDF0),
	onButtonActive = CinemaPastel.Ink,
	input = CinemaPastel.Paper,
	onInput = CinemaPastel.InkSoft,
	inputFocused = CinemaPastel.LavenderSoft,
	onInputFocused = CinemaPastel.Ink,
	rangeControlBackground = CinemaPastel.LavenderSoft,
	rangeControlFill = CinemaPastel.LavenderStrong,
	rangeControlKnob = Color(0xFF5E4BB0),
	seekbarBuffer = CinemaPastel.LavenderMid,
	badge = CinemaPastel.Peach,
	onBadge = Color(0xFF5A2E10),
	listHeader = CinemaPastel.PeachDeep,
	listOverline = CinemaPastel.LavenderStrong,
	listHeadline = CinemaPastel.Ink,
	listCaption = CinemaPastel.InkMuted,
	listButton = Color(0xB3FFFFFF),
	listButtonFocused = CinemaPastel.LavenderSoft,
	surface = CinemaPastel.Paper,
	scrim = Color(0x80E9E3F5),
)

/**
 * Slightly larger list typography for the full-page settings so they read well at
 * 10-foot distance.
 */
private val SettingsPageTypography = Typography(
	listHeader = TypographyDefaults.ListHeader.copy(fontSize = 22.sp, lineHeight = 28.sp),
	listOverline = TypographyDefaults.ListOverline.copy(fontSize = 12.sp, lineHeight = 16.sp),
	listHeadline = TypographyDefaults.ListHeadline.copy(fontSize = 17.sp, lineHeight = 30.sp),
	listCaption = TypographyDefaults.ListCaption.copy(fontSize = 13.sp, lineHeight = 17.sp),
)

/**
 * Applies the settings theme: pastel colours when the light cinema appearance is active and,
 * for the full page, a larger typography scale.
 */
@Composable
fun SettingsTheme(
	fullPage: Boolean,
	content: @Composable () -> Unit,
) {
	val colorScheme = if (CinemaThemeState.isLight) settingsLightColorScheme() else JellyfinTheme.colorScheme
	val typography = if (fullPage) SettingsPageTypography else JellyfinTheme.typography

	JellyfinTheme(
		colorScheme = colorScheme,
		typography = typography,
		content = content,
	)
}
