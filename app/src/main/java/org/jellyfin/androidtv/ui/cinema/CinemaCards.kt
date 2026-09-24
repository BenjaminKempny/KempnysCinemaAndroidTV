package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text

/**
 * Poster card (`.cinemaCard`) — 2/3 aspect, 14 dp radius, title and year *below* the
 * image. Selecting it navigates to the detail screen.
 *
 * Per spec §2.7 the card is a **single focus target**: nothing inside it is focusable,
 * secondary actions run through long press / `KEYCODE_MENU`.
 */
@Composable
fun CinemaPosterCard(
	title: String,
	subtitle: String?,
	imageUrl: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onLongClick: (() -> Unit)? = null,
	width: Dp = CinemaDimens.PosterCardWidth,
	progress: Float? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(width)
			.cinemaFocusZoom(focused)
			.onFocusChanged { focused = it.isFocused }
			.focusable(true, interactionSource)
			.cinemaClickable(interactionSource, onClick, onLongClick),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(CinemaDimens.PosterAspect)
				.cinemaFocusGlow(focused, CinemaDimens.CardRadius)
				.cinemaFocusRing(focused, CinemaDimens.CardRadius)
				.clip(CinemaDimens.CardShape)
				.background(CinemaColors.CardPlaceholder)
				.border(1.dp, CinemaColors.Border, CinemaDimens.CardShape),
		) {
			CardArtwork(imageUrl, title)
		}

		// The web moves the progress bar out of the overlay and into the normal flow for
		// the TV layout so it is never hidden by the focus ring (cinema.scss:977-981).
		if (progress != null) CinemaProgressBar(progress, Modifier.fillMaxWidth())

		Text(
			text = title,
			color = CinemaColors.Text,
			fontSize = CinemaDimens.CardTitleSize,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		if (subtitle != null) {
			Text(
				text = subtitle,
				color = CinemaColors.Muted,
				fontSize = CinemaDimens.CardSubtitleSize,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

/**
 * Wide card (`.cinemaCard-wide`) used by Continue Watching — 16/10 aspect, text *inside*
 * the image on a gradient scrim, play badge bottom right, progress bar at the bottom.
 *
 * Wide cards are `playOnSelect`: the whole card starts playback at the resume position.
 */
@Composable
fun CinemaWideCard(
	title: String,
	subtitle: String?,
	imageUrl: String?,
	progress: Float?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onLongClick: (() -> Unit)? = null,
	width: Dp = 340.dp,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(width)
			.cinemaFocusZoom(focused)
			.onFocusChanged { focused = it.isFocused }
			.focusable(true, interactionSource)
			.cinemaClickable(interactionSource, onClick, onLongClick),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(CinemaDimens.WideAspect)
				.cinemaFocusGlow(focused, CinemaDimens.CardRadius)
				.cinemaFocusRing(focused, CinemaDimens.CardRadius)
				.clip(CinemaDimens.CardShape)
				.background(CinemaColors.CardPlaceholder)
				.border(1.dp, CinemaColors.Border, CinemaDimens.CardShape),
		) {
			CardArtwork(imageUrl, title)

			// Scrim covers the bottom 66% of the card.
			Box(
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.fillMaxWidth()
					.fillMaxHeight(0.66f)
					.background(CinemaColors.WideCardScrim),
			)

			Column(
				modifier = Modifier
					.align(Alignment.BottomStart)
					.fillMaxWidth()
					.padding(start = 14.dp, end = 62.dp, bottom = 12.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = title,
					color = CinemaColors.Text,
					fontSize = CinemaDimens.CardTitleSize,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (subtitle != null) {
					Text(
						text = subtitle,
						color = CinemaColors.Muted,
						fontSize = CinemaDimens.CardSubtitleSize,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Box(
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(14.dp)
					.size(CinemaDimens.PlayBadgeSize)
					.clip(CinemaDimens.PillShape)
					.background(CinemaColors.Accent),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					painter = painterResource(R.drawable.ic_play),
					contentDescription = null,
					tint = CinemaColors.AccentText,
					modifier = Modifier.size(18.dp),
				)
			}
		}

		if (progress != null) CinemaProgressBar(progress, Modifier.fillMaxWidth())
		else Spacer(Modifier.height(CinemaDimens.ProgressHeight))
	}
}

/** 3 dp progress track with pill caps (`cinema.scss` progress bar). */
@Composable
fun CinemaProgressBar(
	progress: Float,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.height(CinemaDimens.ProgressHeight)
			.clip(CinemaDimens.PillShape)
			.background(CinemaColors.Border),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth(progress.coerceIn(0f, 1f))
				.fillMaxHeight()
				.clip(CinemaDimens.PillShape)
				.background(CinemaColors.Accent),
		)
	}
}

@Composable
private fun CardArtwork(imageUrl: String?, contentDescription: String?) {
	if (imageUrl == null) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(CinemaColors.CardPlaceholder),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				painter = painterResource(R.drawable.ic_clapperboard),
				contentDescription = null,
				tint = CinemaColors.Muted,
				modifier = Modifier.size(42.dp),
			)
		}
	} else {
		AsyncImage(
			model = imageUrl,
			contentDescription = contentDescription,
			contentScale = ContentScale.Crop,
			modifier = Modifier.fillMaxSize(),
		)
	}
}

private fun Modifier.cinemaClickable(
	interactionSource: MutableInteractionSource,
	onClick: () -> Unit,
	onLongClick: (() -> Unit)?,
): Modifier = if (onLongClick == null) {
	clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
} else {
	combinedClickable(
		interactionSource = interactionSource,
		indication = null,
		onLongClick = onLongClick,
		onClick = onClick,
	)
}
