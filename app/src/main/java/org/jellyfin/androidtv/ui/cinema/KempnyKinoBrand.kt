package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R

/** Brand colours of the "Kempny Kino" logo: warm yellow -> peach -> pink -> violet. */
object KempnyBrandColors {
	val Yellow = Color(0xFFFFD45E)
	val Peach = Color(0xFFF5B98A)
	val Pink = Color(0xFFD58DB5)
	val Violet = Color(0xFF9B6CF0)
	val VioletDeep = Color(0xFF8A62E6)

	val TextBrush: Brush
		get() = Brush.horizontalGradient(listOf(Yellow, Peach, Pink, Violet))
}

/**
 * The "Kempny Kino" logo: the gradient K mark next to the two line word mark.
 *
 * Drawn in Compose so it stays crisp at any size and needs no bitmap.
 */
@Composable
fun KempnyKinoBrand(
	modifier: Modifier = Modifier,
	markHeight: Dp = 36.dp,
	titleSize: TextUnit = 18.sp,
	subtitleSize: TextUnit = 14.sp,
	shadow: Boolean = false,
) {
	val textShadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 8f) else null

	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(markHeight * 0.32f),
	) {
		KempnyMark(Modifier.height(markHeight).width(markHeight * MARK_ASPECT))

		Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
			BasicText(
				text = "Kempny",
				style = TextStyle(
					brush = KempnyBrandColors.TextBrush,
					fontSize = titleSize,
					fontWeight = FontWeight.Bold,
					letterSpacing = 0.01.em,
					shadow = textShadow,
				),
				maxLines = 1,
			)
			BasicText(
				text = "Kino",
				style = TextStyle(
					brush = KempnyBrandColors.TextBrush,
					fontSize = subtitleSize,
					fontWeight = FontWeight.Normal,
					letterSpacing = 0.04.em,
					shadow = textShadow,
				),
				maxLines = 1,
			)
		}
	}
}

/** Only the gradient K mark — the same artwork as the app icon. */
@Composable
fun KempnyMark(modifier: Modifier = Modifier) {
	Image(
		painter = painterResource(R.drawable.kempnys_logo_header),
		contentDescription = null,
		contentScale = ContentScale.Fit,
		modifier = modifier,
	)
}

private const val MARK_ASPECT = 0.84f
