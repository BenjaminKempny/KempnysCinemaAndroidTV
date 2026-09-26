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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text

/**
 * Default hero height. The caller passes the height that is actually left below the
 * header so the slide always fits into the viewport — see [CinemaHeroMinHeight].
 */
val CinemaHeroMaxHeight = 320.dp

/** Never shrink the slide below this, the buttons would not fit anymore. */
val CinemaHeroMinHeight = 230.dp

/** Line height as a multiple of the font size. */
private const val TITLE_LINE_HEIGHT_FACTOR = 1.08f

/**
 * Poor man's auto sizing for the spotlight title. Compose only gained `TextAutoSize` for
 * `BasicText`, which the themed [Text] wrapper does not expose, so the size is picked
 * from the character count instead — close enough for a single headline.
 */
private fun heroTitleSize(title: String) = when {
	title.length > 46 -> 30.sp
	title.length > 34 -> 36.sp
	title.length > 22 -> 44.sp
	else -> CinemaDimens.HeroTitleSize
}

/** Data required to render one spotlight slide. */
data class CinemaHeroItem(
	val id: java.util.UUID,
	val title: String,
	val overview: String?,
	val backdropUrl: String?,
	/** Genres, rendered as chips. */
	val tags: List<String>,
	/** Plain meta line: year, runtime, community rating, end time. */
	val meta: List<String> = emptyList(),
	/** Age rating badge (e.g. `FSK-16`). */
	val officialRating: String? = null,
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
	heroHeight: Dp = CinemaHeroMaxHeight,
	playFocusRequester: FocusRequester = remember { FocusRequester() },
	requestInitialFocus: Boolean = true,
	onInitialFocusRequested: () -> Unit = {},
	/** DPAD_LEFT on the play button leaves the content and enters the sidebar (spec §2.1). */
	leftFocusRequester: FocusRequester? = null,
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

	// The hero lives inside a LazyColumn and is disposed / recomposed whenever it leaves
	// and re-enters the viewport. The initial focus must therefore only be requested once
	// per screen (tracked by the caller) — otherwise it steals the focus back from the
	// catalog and scrolls the page to the top.
	LaunchedEffect(Unit) {
		if (requestInitialFocus) {
			runCatching { playFocusRequester.requestFocus() }
			onInitialFocusRequested()
		}
	}

	val item = items[index.coerceIn(items.indices)]

	Box(
		modifier = modifier
			.fillMaxWidth()
			// The height is handed down from the shell so the slide fits below the header.
			.height(heroHeight)
			.clip(CinemaDimens.HeroShape)
			.background(CinemaColors.HeroBackground)
			.border(1.dp, CinemaColors.Border, CinemaDimens.HeroShape)
			// Compose scrolls the focused button into view; avoid a second scroll request.
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
				CinemaAsyncImage(
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
				.fillMaxHeight()
				.fillMaxWidth(0.7f)
				.widthIn(max = 740.dp)
				.padding(start = 40.dp, end = 24.dp, top = 32.dp, bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
		) {
			Text(
				text = stringResource(R.string.cinema_spotlight).uppercase(),
				color = CinemaColors.OnMediaMuted,
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

			// Long titles previously pushed the play buttons out of the slide. The size
			// shrinks with the title length and the text only ever claims the space that
			// is left over after the buttons have been measured.
			val titleSize = heroTitleSize(item.title)

			Text(
				text = item.title,
				color = CinemaColors.OnMedia,
				fontSize = titleSize,
				lineHeight = titleSize * TITLE_LINE_HEIGHT_FACTOR,
				fontWeight = FontWeight.Bold,
				letterSpacing = (-0.055).em,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f, fill = false),
			)

			if (item.meta.isNotEmpty()) {
				Text(
					text = item.meta.joinToString("  ·  "),
					color = CinemaColors.OnMediaSoft,
					fontSize = CinemaDimens.MetaSize,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			Row(
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				CinemaButton(
					text = stringResource(if (item.resumable) R.string.cinema_resume else R.string.lbl_play),
					icon = painterResource(if (item.resumable) R.drawable.ic_resume else R.drawable.ic_play),
					primary = true,
					onClick = { onPlay(item) },
					modifier = Modifier
						.focusRequester(playFocusRequester)
						.focusProperties { if (leftFocusRequester != null) left = leftFocusRequester },
				)
				CinemaButton(
					text = stringResource(R.string.cinema_details),
					icon = painterResource(R.drawable.ic_info),
					onClick = { onDetails(item) },
				)

				// The web puts two mouse-only arrows in the bottom right corner. On TV
				// they sit next to the play button instead: a bottom aligned row would
				// be the last thing reachable with the D-Pad and pushed the visible part
				// of the slide off screen.
				if (items.size > 1) {
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
}
