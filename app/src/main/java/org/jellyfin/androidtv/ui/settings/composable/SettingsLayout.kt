package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalShapes
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.cinema.CinemaColors
import org.jellyfin.androidtv.ui.cinema.CinemaThemeState
import org.jellyfin.design.Tokens

/**
 * Compact side panel used by contextual settings (e.g. grid or player options).
 */
@Composable
fun SettingsLayout(
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	Box(
		modifier = modifier
			.padding(Tokens.Space.spaceMd)
			.clip(LocalShapes.current.large)
			.background(JellyfinTheme.colorScheme.surface)
			.width(350.dp)
			.fillMaxHeight()
			.focusRestorer(),
		content = content
	)
}

/**
 * Full-screen settings page. Uses the cinema page background and centres the settings
 * content in a comfortable reading column with a page header.
 */
@Composable
fun SettingsPageLayout(
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	val isLight = CinemaThemeState.isLight
	val colorScheme = JellyfinTheme.colorScheme
	val cardShape = RoundedCornerShape(28.dp)

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(CinemaColors.PageBackground),
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 48.dp, vertical = 27.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			// Page header
			Row(
				modifier = Modifier
					.widthIn(max = 760.dp)
					.fillMaxWidth()
					.padding(horizontal = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Box(
					modifier = Modifier
						.size(44.dp)
						.clip(RoundedCornerShape(14.dp))
						.background(
							if (isLight) Brush.linearGradient(listOf(Color(0xFFE6DFF8), Color(0xFFFCE3EC)))
							else Brush.linearGradient(listOf(colorScheme.listButtonFocused, colorScheme.surface))
						),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
						contentDescription = null,
						tint = colorScheme.listHeadline,
						modifier = Modifier.size(24.dp),
					)
				}

				Text(
					text = stringResource(R.string.lbl_settings),
					color = colorScheme.listHeader,
					fontSize = 30.sp,
					fontWeight = FontWeight.W700,
				)
			}

			Spacer(Modifier.height(18.dp))

			// Content card
			Box(
				modifier = Modifier
					.widthIn(max = 760.dp)
					.fillMaxWidth()
					.weight(1f)
					.clip(cardShape)
					.background(
						if (isLight) Color(0x99FFFFFF) else colorScheme.surface.copy(alpha = 0.85f)
					)
					.border(
						width = 1.dp,
						color = if (isLight) Color(0xCCFFFFFF) else Color(0x1FFFFFFF),
						shape = cardShape,
					)
					.padding(horizontal = 12.dp, vertical = 8.dp)
					.focusRestorer(),
				content = content,
			)
		}
	}
}

