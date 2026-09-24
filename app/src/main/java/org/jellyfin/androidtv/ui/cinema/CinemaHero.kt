package org.jellyfin.androidtv.ui.cinema

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

/** Data required to render one spotlight slide. */
data class CinemaHeroItem(
	val id: java.util.UUID,
	val title: String,
	val overview: String?,
	val backdropUrl: String?,
	val tags: List<String>,
	val resumable: Boolean,
)

/**
 * Spotlight hero (`HeroContent`, `cinema.scss:305-388`).
 *
 * Differences from the web, per spec §2.2:
 * - The two arrow buttons are a mouse affordance and are **dropped**. Rotation happens
 *   automatically every 8 s and pauses while the hero holds focus; manual paging is done
 *   with DPAD_LEFT/RIGHT while the play button is focused.
 * - Initial focus is placed on the play button.
 */
@Composable
fun CinemaHero(
	items: List<CinemaHeroItem>,
	onPlay: (CinemaHeroItem) -> Unit,
	onDetails: (CinemaHeroItem) -> Unit,
	modifier: Modifier = Modifier,
	playFocusRequester: FocusRequester = remember { FocusRequester() },
	requestInitialFocus: Boolean = true,
) {
	if (items.isEmpty()) return

	var index by remember(items) { mutableStateOf(0) }
	var hasFocus by remember { mutableStateOf(false) }
	val reducedMotion = rememberReducedMotion()

	// Auto rotation, paused while focused.
	LaunchedEffect(items, hasFocus) {
		if (hasFocus) return@LaunchedEffect
		while (true) {
			delay(CinemaMotion.HeroRotationIntervalMs)
			index = (index + 1) % items.size
		}
	}

	LaunchedEffect(requestInitialFocus) {
		if (requestInitialFocus) runCatching { playFocusRequester.requestFocus() }
	}

	val item = items[index.coerceIn(items.indices)]

	Box(
		modifier = modifier
			.fillMaxWidth()
			.heightIn(min = CinemaDimens.HeroMinHeight)
			.aspectRatio(CinemaDimens.HeroAspect)
			.clip(CinemaDimens.HeroShape)
			.background(CinemaColors.HeroBackground)
			.border(1.dp, CinemaColors.Border, CinemaDimens.HeroShape)
			.onFocusChanged { hasFocus = it.hasFocus }
			.focusGroup(),
	) {
		Crossfade(
			targetState = item.backdropUrl,
			animationSpec = tween(if (reducedMotion) 0 else CinemaMotion.HeroCrossfadeDuration),
			label = "cinemaHeroBackdrop",
			modifier = Modifier.fillMaxSize(),
		) { url ->
			if (url != null) {
				AsyncImage(
					model = url,
					contentDescription = null,
					contentScale = ContentScale.Crop,
					// object-position: center 35%
					alignment = BiasAlignment(0f, -0.3f),
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		// Double scrim (spec §1.1).
		Box(Modifier.fillMaxSize().background(CinemaColors.HeroScrimHorizontal))
		Box(Modifier.fillMaxSize().background(CinemaColors.HeroScrimVertical))

		Column(
			modifier = Modifier
				.align(Alignment.CenterStart)
				.fillMaxWidth(0.7f)
				.widthIn(max = 740.dp)
				.padding(start = 40.dp, end = 24.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			Text(
				text = stringResource(R.string.cinema_spotlight).uppercase(),
				color = CinemaColors.Muted,
				fontSize = CinemaDimens.EyebrowSize,
				fontWeight = FontWeight.SemiBold,
				letterSpacing = 0.22.em,
				maxLines = 1,
			)

			if (item.tags.isNotEmpty()) {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					item.tags.take(5).forEach { CinemaTag(it) }
				}
			}

			Text(
				text = item.title,
				color = CinemaColors.Text,
				fontSize = CinemaDimens.HeroTitleSize,
				lineHeight = 60.sp,
				fontWeight = FontWeight.Bold,
				letterSpacing = (-0.055).em,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
			)

			if (!item.overview.isNullOrBlank()) {
				Text(
					text = item.overview,
					color = CinemaColors.TextSoft,
					fontSize = CinemaDimens.BodySize,
					lineHeight = 26.sp,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
				)
			}

			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				CinemaButton(
					text = stringResource(if (item.resumable) R.string.cinema_resume else R.string.lbl_play),
					icon = painterResource(if (item.resumable) R.drawable.ic_resume else R.drawable.ic_play),
					primary = true,
					onClick = { onPlay(item) },
					modifier = Modifier.focusRequester(playFocusRequester),
				)
				CinemaButton(
					text = stringResource(R.string.cinema_details),
					icon = painterResource(R.drawable.ic_info),
					onClick = { onDetails(item) },
				)
			}
		}

		// The web uses two mouse-only arrow buttons here. On TV they stay, but as real
		// D-Pad targets so the rotation can also be driven manually.
		if (items.size > 1) {
			Row(
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(24.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				CinemaIconButton(
					icon = painterResource(R.drawable.ic_arrow_back),
					contentDescription = stringResource(R.string.cinema_previous),
					onClick = { index = (index - 1 + items.size) % items.size },
					size = 44.dp,
				)
				CinemaIconButton(
					icon = painterResource(R.drawable.ic_arrow_forward),
					contentDescription = stringResource(R.string.cinema_next),
					onClick = { index = (index + 1) % items.size },
					size = 44.dp,
				)
			}
		}
	}
}
