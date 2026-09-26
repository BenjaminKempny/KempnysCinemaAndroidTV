package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
	/** Small counter bubble in the top end corner (e.g. episode count of a season). */
	badge: String? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(width)
			.cinemaFocusZoom(focused)
			.onFocusChanged { focused = it.isFocused }
			.cinemaClickable(interactionSource, onClick, onLongClick),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(CinemaDimens.PosterAspect)
				.cinemaFocusRing(focused, CinemaDimens.CardRadius)
				.clip(CinemaDimens.CardShape)
				.background(CinemaColors.CardPlaceholder)
				// Hidden while focused so the accent ring is the only outline — the two
				// together read as a double border.
				.border(1.dp, if (focused) Color.Transparent else CinemaColors.Border, CinemaDimens.CardShape),
		) {
			CardArtwork(imageUrl, title)

			if (badge != null) {
				Box(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(8.dp)
						.defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
						.clip(CinemaDimens.PillShape)
						.background(CinemaColors.Accent)
						.padding(horizontal = 7.dp),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = badge,
						color = CinemaColors.AccentText,
						fontSize = CinemaDimens.MetaSize,
						fontWeight = FontWeight.SemiBold,
						maxLines = 1,
					)
				}
			}
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
			.cinemaClickable(interactionSource, onClick, onLongClick),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(CinemaDimens.WideAspect)
				.cinemaFocusRing(focused, CinemaDimens.CardRadius)
				.clip(CinemaDimens.CardShape)
				.background(CinemaColors.CardPlaceholder)
				.border(1.dp, if (focused) Color.Transparent else CinemaColors.Border, CinemaDimens.CardShape),
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
					color = CinemaColors.OnMedia,
					fontSize = CinemaDimens.CardTitleSize,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (subtitle != null) {
					Text(
						text = subtitle,
						color = CinemaColors.OnMediaMuted,
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
		CinemaAsyncImage(
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

/**
 * Cast & crew card — circular portrait, name and role below. It is focusable so the row
 * can be scrolled with the D-Pad.
 */
@Composable
fun CinemaPersonCard(
	name: String,
	role: String?,
	imageUrl: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	width: Dp = CinemaDimens.RowCardWidth,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(width)
			.cinemaFocusZoom(focused)
			.onFocusChanged { focused = it.isFocused }
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(1f)
				.clip(CircleShape)
				.background(CinemaColors.CardPlaceholder)
				.border(
					width = if (focused) 3.dp else 1.dp,
					color = if (focused) CinemaColors.Focus else CinemaColors.Border,
					shape = CircleShape,
				),
			contentAlignment = Alignment.Center,
		) {
			if (imageUrl != null) {
				CinemaAsyncImage(
					model = imageUrl,
					contentDescription = name,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize(),
				)
			} else {
				Icon(
					painter = painterResource(R.drawable.ic_user),
					contentDescription = null,
					tint = CinemaColors.Muted,
					modifier = Modifier.size(42.dp),
				)
			}
		}

		Text(
			text = name,
			color = CinemaColors.Text,
			fontSize = CinemaDimens.CardSubtitleSize,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			textAlign = TextAlign.Center,
		)

		if (!role.isNullOrBlank()) {
			Text(
				text = role,
				color = CinemaColors.Muted,
				fontSize = CinemaDimens.MetaSize,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				textAlign = TextAlign.Center,
			)
		}
	}
}

/**
 * Episode list entry of the season page — thumbnail with play overlay on the start side,
 * `1. Title`, meta line and overview on the end side. The whole row is a single focus
 * target that starts playback.
 */
@Composable
fun CinemaEpisodeRow(
	title: String,
	meta: List<String>,
	rating: Float?,
	overview: String?,
	imageUrl: String?,
	progress: Float?,
	played: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onLongClick: (() -> Unit)? = null,
) {
	val interactionSource = remember { MutableInteractionSource() }
	var focused by remember { mutableStateOf(false) }

	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(CinemaDimens.PanelShape)
			.background(if (focused) CinemaColors.ButtonFocused else CinemaColors.Surface)
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = if (focused) CinemaColors.Focus else CinemaColors.Border,
				shape = CinemaDimens.PanelShape,
			)
			.onFocusChanged { focused = it.isFocused }
			.cinemaClickable(interactionSource, onClick, onLongClick)
			.padding(16.dp),
		horizontalArrangement = Arrangement.spacedBy(20.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier.width(EPISODE_THUMB_WIDTH),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(16f / 9f)
					.clip(CinemaDimens.CardShape)
					.background(CinemaColors.CardPlaceholder),
				contentAlignment = Alignment.Center,
			) {
				CardArtwork(imageUrl, title)

				Box(
					modifier = Modifier
						.size(CinemaDimens.PlayBadgeSize + 8.dp)
						.clip(CircleShape)
						.background(if (focused) CinemaColors.Accent else CinemaColors.Button),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						painter = painterResource(R.drawable.ic_play),
						contentDescription = null,
						tint = if (focused) CinemaColors.AccentText else CinemaColors.Text,
						modifier = Modifier.size(20.dp),
					)
				}

				if (played) {
					Box(
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(6.dp)
							.size(26.dp)
							.clip(CircleShape)
							.background(CinemaColors.Accent),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							painter = painterResource(R.drawable.ic_check),
							contentDescription = null,
							tint = CinemaColors.AccentText,
							modifier = Modifier.size(16.dp),
						)
					}
				}
			}

			if (progress != null) CinemaProgressBar(progress, Modifier.fillMaxWidth())
		}

		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Text(
				text = title,
				color = CinemaColors.Text,
				fontSize = CinemaDimens.CardTitleSize,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			CinemaMetaLine(parts = meta, rating = rating)

			if (!overview.isNullOrBlank()) {
				Text(
					text = overview,
					color = CinemaColors.TextSoft,
					fontSize = CinemaDimens.MetaSize,
					lineHeight = 21.sp,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

/** `45m  ★ 7.0  Endet um 12:45` — plain meta parts with the rating rendered as a star. */
@Composable
fun CinemaMetaLine(
	parts: List<String>,
	rating: Float?,
	modifier: Modifier = Modifier,
	leading: (@Composable () -> Unit)? = null,
	trailing: List<String> = emptyList(),
	/** Set when the line is rendered on top of artwork so it stays light in both themes. */
	onMedia: Boolean = false,
) {
	if (parts.isEmpty() && rating == null && leading == null && trailing.isEmpty()) return

	val textColor = if (onMedia) CinemaColors.OnMediaSoft else CinemaColors.TextSoft

	Row(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		leading?.invoke()

		parts.forEach { MetaText(it, textColor) }

		if (rating != null) {
			Row(
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = "★",
					color = if (onMedia) CinemaDarkPalette.star else CinemaColors.Star,
					fontSize = CinemaDimens.MetaSize,
					maxLines = 1,
				)
				MetaText("%.1f".format(rating), textColor)
			}
		}

		trailing.forEach { MetaText(it, textColor) }
	}
}

@Composable
private fun MetaText(
	text: String,
	color: Color = CinemaColors.TextSoft,
) = Text(
	text = text,
	color = color,
	fontSize = CinemaDimens.MetaSize,
	maxLines = 1,
)

private val EPISODE_THUMB_WIDTH = 220.dp

