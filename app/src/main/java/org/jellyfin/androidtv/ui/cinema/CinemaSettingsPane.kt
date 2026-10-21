package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.navigation.ProvideRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsRouterContent
import org.jellyfin.androidtv.ui.settings.composable.SettingsTheme
import org.jellyfin.androidtv.ui.settings.routes

/**
 * The settings rendered as a regular cinema page: same shell, logo/header row and page
 * title as the library pages, so the settings entry of the rail no longer opens a separate
 * full-screen dialog.
 *
 * Back inside a settings sub page pops the settings router; on the settings root the host
 * handles back (return to the library / login page).
 */
@Composable
fun CinemaSettingsPane(
	modifier: Modifier = Modifier,
	defaultRoute: String = Routes.MAIN,
	/** `DPAD_LEFT` from the settings list goes back into the rail. */
	railFocusRequester: FocusRequester? = null,
	/** Moves the focus into the settings list once it is shown (used after a click). */
	requestInitialFocus: Boolean = false,
) {
	val contentFocusRequester = remember { FocusRequester() }
	if (requestInitialFocus) CinemaInitialFocus(contentFocusRequester)

	CinemaShell(
		modifier = modifier
			.fillMaxSize()
			.keepVerticalFocusInside()
			.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(
					start = CinemaDimens.PageGutter,
					end = CinemaDimens.PageGutter,
					top = CinemaDimens.PageGutter,
					bottom = CinemaDimens.Overscan,
				),
			verticalArrangement = Arrangement.spacedBy(CinemaDimens.SectionGap),
		) {
			// No extra page header: the settings root already shows its own title block.
			Box(
				modifier = Modifier
					.weight(1f)
					.widthIn(max = SETTINGS_MAX_WIDTH)
					.fillMaxWidth()
					.focusRequester(contentFocusRequester)
					.focusRestorer()
					.focusProperties {
						onExit = {
							if (requestedFocusDirection == FocusDirection.Left && railFocusRequester != null) {
								railFocusRequester.requestFocus()
							}
						}
					}
					.focusGroup(),
			) {
				SettingsTheme(fullPage = true) {
					ProvideRouter(routes, defaultRoute) {
						SettingsRouterContent()
					}
				}
			}
		}
	}
}

private val SETTINGS_MAX_WIDTH = 820.dp

