package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jellyfin.androidtv.ui.base.dialog.DialogBase

/**
 * Hosts the settings. With [fullPage] the settings are shown as a regular full-screen page,
 * otherwise as a compact side panel (used for contextual options like grid or player settings).
 */
@Composable
fun SettingsDialog(
	visible: Boolean,
	onDismissRequest: () -> Unit,
	modifier: Modifier = Modifier,
	fullPage: Boolean = false,
	screen: @Composable BoxScope.() -> Unit,
) {
	val duration = 400
	SettingsTheme(fullPage = fullPage) {
		if (fullPage) {
			DialogBase(
				visible = visible,
				onDismissRequest = onDismissRequest,
				modifier = modifier,
				contentAlignment = Alignment.Center,
				enterTransition = fadeIn(tween(duration)) + scaleIn(
					initialScale = 0.97f,
					animationSpec = tween(duration, easing = FastOutSlowInEasing),
				),
				exitTransition = fadeOut(tween(duration)) + scaleOut(
					targetScale = 0.97f,
					animationSpec = tween(duration, easing = FastOutSlowInEasing),
				),
			) {
				SettingsPageLayout {
					screen()
				}
			}
		} else {
			DialogBase(
				visible = visible,
				onDismissRequest = onDismissRequest,
				modifier = modifier,
				contentAlignment = Alignment.TopEnd,
				enterTransition = slideInHorizontally(
					initialOffsetX = { it },
					animationSpec = tween(duration, easing = FastOutSlowInEasing)
				) + fadeIn(
					animationSpec = tween(duration)
				),
				exitTransition = slideOutHorizontally(
					targetOffsetX = { it },
					animationSpec = tween(duration, easing = FastOutSlowInEasing)
				) + fadeOut(
					animationSpec = tween(duration)
				),
			) {
				SettingsLayout {
					screen()
				}
			}
		}
	}
}
