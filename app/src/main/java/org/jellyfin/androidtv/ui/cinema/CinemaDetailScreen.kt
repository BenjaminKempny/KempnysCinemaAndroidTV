package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class CinemaDetailActions(
	val onBack: () -> Unit,
	val onHome: () -> Unit,
	val onPlay: () -> Unit,
	val onToggleFavorite: () -> Unit,
	val onTogglePlayed: () -> Unit,
	val onOpenItem: (BaseItemDto) -> Unit,
)

/**
 * The cinema detail capsule (spec §2.5).
 *
 * The web builds this by rewriting the legacy DOM at runtime; that is a pure web
 * workaround, so the target structure is built directly here: a rounded capsule holding
 * the poster on the left and title, meta and actions on the right over a scrimmed
 * backdrop, followed by the info ribbon, the description and related rows.
 */
@Composable
fun CinemaDetailScreen(
	state: CinemaDetailState,
	actions: CinemaDetailActions,
	posterUrl: (BaseItemDto) -> String?,
	modifier: Modifier = Modifier,
) {
	val item = state.item
	val playFocusRequester = remember { FocusRequester() }

	LaunchedEffect(item?.id) {
		if (item != null) runCatching { playFocusRequester.requestFocus() }
	}

	CinemaBackground(modifier) {
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = CinemaDimens.Overscan,
				end = CinemaDimens.Overscan,
				top = 28.dp,
				bottom = CinemaDimens.Overscan,
			),
			verticalArrangement = Arrangement.spacedBy(22.dp),
		) {
			item(key = "nav") {
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					CinemaIconButton(
						icon = painterResource(R.drawable.ic_arrow_back),
						contentDescription = stringResource(R.string.cinema_back),
						onClick = actions.onBack,
						size = 44.dp,
					)
					CinemaIconButton(
						icon = painterResource(R.drawable.ic_house),
						contentDescription = stringResource(R.string.lbl_home),
						onClick = actions.onHome,
						size = 44.dp,
					)
				}
			}

			if (item != null) {
				item(key = "hero") {
					CinemaDetailHero(
						item = item,
						state = state,
						actions = actions,
						playFocusRequester = playFocusRequester,
					)
				}

				item(key = "ribbon") { CinemaDetailRibbon(item) }

				if (!item.overview.isNullOrBlank()) {
					item(key = "overview") {
						CinemaPanel(Modifier.fillMaxWidth()) {
							Column(
								modifier = Modifier.padding(22.dp),
								verticalArrangement = Arrangement.spacedBy(12.dp),
							) {
								item.taglines?.firstOrNull()?.let { tagline ->
									Text(
										text = tagline,
										color = CinemaColors.Text,
										fontSize = CinemaDimens.SectionTitleSize,
										fontWeight = FontWeight.SemiBold,
									)
								}

								Text(
									text = item.overview.orEmpty(),
									color = CinemaColors.TextSoft,
									fontSize = CinemaDimens.BodySize,
									lineHeight = 28.sp,
								)
							}
						}
					}
				}

				if (state.children.isNotEmpty()) {
					item(key = "children") {
						CinemaDetailRow(
							title = stringResource(
								if (item.type == BaseItemKind.SERIES) R.string.lbl_seasons
								else R.string.lbl_episodes
							),
							items = state.children,
							posterUrl = posterUrl,
							onOpenItem = actions.onOpenItem,
						)
					}
				}

				if (state.similar.isNotEmpty()) {
					item(key = "similar") {
						CinemaDetailRow(
							title = stringResource(R.string.cinema_similar),
							items = state.similar,
							posterUrl = posterUrl,
							onOpenItem = actions.onOpenItem,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun CinemaDetailHero(
	item: BaseItemDto,
	state: CinemaDetailState,
	actions: CinemaDetailActions,
	playFocusRequester: FocusRequester,
) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(DETAIL_HERO_HEIGHT)
			.clip(CinemaDimens.HeroShape)
			.background(CinemaColors.HeroBackground)
			.border(1.dp, CinemaColors.Border, CinemaDimens.HeroShape),
	) {
		if (state.backdropUrl != null) {
			AsyncImage(
				model = state.backdropUrl,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)
		}

		Box(Modifier.fillMaxSize().background(CinemaColors.HeroScrimHorizontal))

		Row(
			modifier = Modifier
				.fillMaxSize()
				.padding(20.dp),
			horizontalArrangement = Arrangement.spacedBy(28.dp),
		) {
			Box(
				modifier = Modifier
					.fillMaxHeight()
					.aspectRatio(CinemaDimens.PosterAspect)
					.clip(RoundedCornerShape(CinemaDimens.PosterRadius))
					.background(CinemaColors.CardPlaceholder),
			) {
				if (state.posterUrl != null) {
					AsyncImage(
						model = state.posterUrl,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}

			Column(
				modifier = Modifier.padding(top = 42.dp),
				verticalArrangement = Arrangement.spacedBy(18.dp),
			) {
				Text(
					text = item.name.orEmpty(),
					color = CinemaColors.Text,
					fontSize = 46.sp,
					fontWeight = FontWeight.Bold,
					maxLines = 2,
				)

				CinemaDetailMeta(item)

				Row(
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					val resumable = (item.userData?.playbackPositionTicks ?: 0L) > 0L
					CinemaButton(
						text = stringResource(if (resumable) R.string.cinema_resume else R.string.lbl_play),
						icon = painterResource(if (resumable) R.drawable.ic_resume else R.drawable.ic_play),
						primary = true,
						onClick = actions.onPlay,
						modifier = Modifier.focusRequester(playFocusRequester),
					)

					CinemaIconButton(
						icon = painterResource(R.drawable.ic_watch),
						contentDescription = stringResource(R.string.lbl_watched),
						onClick = actions.onTogglePlayed,
						active = state.played,
					)

					CinemaIconButton(
						icon = painterResource(R.drawable.ic_heart),
						contentDescription = stringResource(R.string.lbl_favorite),
						onClick = actions.onToggleFavorite,
						active = state.favorite,
					)
				}
			}
		}
	}
}

/** Meta line: year, runtime, rating badge, community and critic rating, end time. */
@Composable
private fun CinemaDetailMeta(item: BaseItemDto) {
	val parts = buildList {
		item.productionYear?.let { add(it.toString()) }
		item.runTimeTicks?.let { add(formatRuntime(it)) }
		item.communityRating?.let { add("★ %.1f".format(it)) }
		item.criticRating?.let { add("$it%") }
		endTime(item.runTimeTicks, item.userData?.playbackPositionTicks)?.let { add(it) }
	}

	Row(
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		item.officialRating?.let { CinemaTag(it) }

		parts.forEach { part ->
			Text(
				text = part,
				color = CinemaColors.TextSoft,
				fontSize = CinemaDimens.CardSubtitleSize,
				maxLines = 1,
			)
		}
	}
}

/** The info ribbon below the capsule: director, writer, studios, genres. */
@Composable
private fun CinemaDetailRibbon(item: BaseItemDto) {
	val director = item.people
		?.filter { it.type == PersonKind.DIRECTOR }
		?.mapNotNull { it.name }
		?.take(MAX_PEOPLE)
		.orEmpty()
	val writer = item.people
		?.filter { it.type == PersonKind.WRITER }
		?.mapNotNull { it.name }
		?.take(MAX_PEOPLE)
		.orEmpty()
	val studios = item.studios?.mapNotNull { it.name }.orEmpty()
	val genres = item.genres.orEmpty()
	val audio = item.mediaStreams
		?.filter { it.type == MediaStreamType.AUDIO }
		?.mapNotNull { it.displayTitle }
		?.take(1)
		.orEmpty()

	val rows = listOfNotNull(
		director.takeIf { it.isNotEmpty() }?.let { R.string.cinema_director to it },
		writer.takeIf { it.isNotEmpty() }?.let { R.string.cinema_writer to it },
		studios.takeIf { it.isNotEmpty() }?.let { R.string.cinema_studios to it },
		genres.takeIf { it.isNotEmpty() }?.let { R.string.lbl_genres to it },
		audio.takeIf { it.isNotEmpty() }?.let { R.string.cinema_audio to it },
	)

	if (rows.isEmpty()) return

	CinemaPanel(Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.padding(22.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			rows.forEach { (labelRes, values) ->
				Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
					Text(
						text = stringResource(labelRes),
						color = CinemaColors.Muted,
						fontSize = CinemaDimens.CardSubtitleSize,
						modifier = Modifier.width(RIBBON_LABEL_WIDTH),
					)
					Text(
						text = values.joinToString(", "),
						color = CinemaColors.Text,
						fontSize = CinemaDimens.CardSubtitleSize,
						fontWeight = FontWeight.SemiBold,
					)
				}
			}
		}
	}
}

@Composable
private fun CinemaDetailRow(
	title: String,
	items: List<BaseItemDto>,
	posterUrl: (BaseItemDto) -> String?,
	onOpenItem: (BaseItemDto) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		CinemaSectionTitle(title)

		LazyRow(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(CinemaDimens.RowGap),
		) {
			items(items, key = { it.id }) { child ->
				CinemaPosterCard(
					title = child.cinemaTitle,
					subtitle = child.cinemaSubtitle,
					imageUrl = posterUrl(child),
					progress = child.cinemaProgress,
					onClick = { onOpenItem(child) },
					width = DETAIL_CARD_WIDTH,
				)
			}
		}
	}
}

private fun formatRuntime(ticks: Long): String {
	val totalMinutes = ticks / TICKS_PER_MINUTE
	val hours = totalMinutes / MINUTES_PER_HOUR
	val minutes = totalMinutes % MINUTES_PER_HOUR
	return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun endTime(runTimeTicks: Long?, positionTicks: Long?): String? {
	if (runTimeTicks == null || runTimeTicks <= 0L) return null
	val remaining = runTimeTicks - (positionTicks ?: 0L)
	if (remaining <= 0L) return null

	val end = LocalDateTime.now().plusMinutes(remaining / TICKS_PER_MINUTE)
	return end.format(DateTimeFormatter.ofPattern("HH:mm"))
}

private const val TICKS_PER_MINUTE = 600_000_000L
private const val MINUTES_PER_HOUR = 60L
private const val MAX_PEOPLE = 3
private val DETAIL_HERO_HEIGHT = 330.dp
private val DETAIL_CARD_WIDTH = 170.dp
private val RIBBON_LABEL_WIDTH = 120.dp
