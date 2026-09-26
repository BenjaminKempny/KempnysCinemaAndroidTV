package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind

data class CinemaDetailActions(
	val onBack: () -> Unit,
	val onHome: () -> Unit,
	/** Plays [CinemaDetailState.playTarget]. */
	val onPlay: () -> Unit,
	/** Plays a specific episode (next up card, episode list). */
	val onPlayItem: (BaseItemDto) -> Unit,
	val onOpenItem: (BaseItemDto) -> Unit,
	val onSelectSort: (CinemaSort) -> Unit,
)

/**
 * The cinema detail capsule (spec §2.5) and — for box sets — the collection browser.
 *
 * Layout per item type (reference screenshots):
 * - Movie / episode: capsule -> info ribbon -> overview -> cast & crew
 * - Series: capsule -> info ribbon -> overview -> next up -> seasons -> cast & crew
 * - Season: capsule -> overview -> episode list
 *
 * The capsule only carries the play button; there are no recommendation rows.
 */
@Composable
fun CinemaDetailScreen(
	state: CinemaDetailState,
	actions: CinemaDetailActions,
	posterUrl: (BaseItemDto) -> String?,
	thumbUrl: (BaseItemDto) -> String?,
	personImageUrl: (BaseItemPerson) -> String?,
	modifier: Modifier = Modifier,
) {
	val item = state.item
	val playFocusRequester = remember { FocusRequester() }

	LaunchedEffect(item?.id, state.isCollection) {
		if (item != null && !state.isCollection) runCatching { playFocusRequester.requestFocus() }
	}

	CinemaBackground(modifier) {
		CinemaLazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.focusRestorer(),
			contentPadding = PaddingValues(
				start = CinemaDimens.Overscan,
				end = CinemaDimens.Overscan,
				top = 28.dp,
				bottom = CinemaDimens.Overscan,
			),
			verticalArrangement = Arrangement.spacedBy(24.dp),
		) {
			item(key = "nav") {
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					CinemaIconButton(
						icon = painterResource(R.drawable.ic_arrow_back),
						contentDescription = stringResource(R.string.cinema_back),
						onClick = actions.onBack,
						size = NAV_BUTTON_SIZE,
					)
					CinemaIconButton(
						icon = painterResource(R.drawable.ic_house),
						contentDescription = stringResource(R.string.lbl_home),
						onClick = actions.onHome,
						size = NAV_BUTTON_SIZE,
					)
				}
			}

			if (item != null) {
				if (state.isCollection) {
					collectionContent(state, actions, item, posterUrl)
				} else {
					detailContent(
						state = state,
						actions = actions,
						detailItem = item,
						posterUrl = posterUrl,
						thumbUrl = thumbUrl,
						personImageUrl = personImageUrl,
						playFocusRequester = playFocusRequester,
					)
				}
			}
		}
	}
}

// region collection

private fun LazyListScope.collectionContent(
	state: CinemaDetailState,
	actions: CinemaDetailActions,
	collection: BaseItemDto,
	posterUrl: (BaseItemDto) -> String?,
) {
	item(key = "collection-header") {
		Row(
			horizontalArrangement = Arrangement.spacedBy(22.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				modifier = Modifier
					.width(COLLECTION_POSTER_WIDTH)
					.aspectRatio(CinemaDimens.PosterAspect)
					.clip(CinemaDimens.CardShape)
					.background(CinemaColors.CardPlaceholder),
			) {
				if (state.posterUrl != null) {
					CinemaAsyncImage(
						model = state.posterUrl,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}

			Text(
				text = collection.name.orEmpty(),
				color = CinemaColors.Text,
				fontSize = CinemaDimens.PageTitleSize,
				fontWeight = FontWeight.Bold,
				maxLines = 2,
			)
		}
	}

	if (state.children.isEmpty()) {
		if (!state.loading) {
			item(key = "collection-empty") {
				Text(
					text = stringResource(R.string.cinema_empty),
					color = CinemaColors.Muted,
					fontSize = CinemaDimens.BodySize,
				)
			}
		}
		return
	}

	item(key = "collection-title") {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			CinemaSectionTitle(
				text = stringResource(R.string.cinema_titles),
				modifier = Modifier.weight(1f),
			)

			CinemaSortSelector(sort = state.sort, onSelect = actions.onSelectSort)
		}
	}

	cinemaGrid(
		items = state.children,
		posterUrl = posterUrl,
		onOpenItem = actions.onOpenItem,
		keyPrefix = "collection",
	)
}

// endregion

// region media detail

private fun LazyListScope.detailContent(
	state: CinemaDetailState,
	actions: CinemaDetailActions,
	detailItem: BaseItemDto,
	posterUrl: (BaseItemDto) -> String?,
	thumbUrl: (BaseItemDto) -> String?,
	personImageUrl: (BaseItemPerson) -> String?,
	playFocusRequester: FocusRequester,
) {
	item(key = "hero") {
		CinemaDetailHero(
			item = detailItem,
			state = state,
			onPlay = actions.onPlay,
			playFocusRequester = playFocusRequester,
		)
	}

	item(key = "ribbon") { CinemaDetailRibbon(detailItem) }

	if (!detailItem.overview.isNullOrBlank() || !detailItem.tags.isNullOrEmpty()) {
		item(key = "overview") { CinemaDetailOverview(detailItem) }
	}

	when (detailItem.type) {
		BaseItemKind.SERIES -> seriesSections(state, actions, posterUrl, thumbUrl)
		BaseItemKind.SEASON -> episodeList(state, actions, thumbUrl)
		else -> Unit
	}

	val people = detailItem.cinemaPeople
	if (people.isNotEmpty()) {
		item(key = "cast") {
			CinemaDetailSection(title = stringResource(R.string.lbl_cast_crew)) {
				LazyRow(
					modifier = Modifier
						.fillMaxWidth()
						.focusRestorer(),
					contentPadding = PaddingValues(horizontal = SECTION_PADDING),
					horizontalArrangement = Arrangement.spacedBy(CinemaDimens.RowGap),
				) {
					items(people, key = { "${it.id}-${it.type}-${it.role}" }) { person ->
						CinemaPersonCard(
							name = person.name.orEmpty(),
							role = person.role?.takeIf { it.isNotBlank() } ?: person.type.label(),
							imageUrl = personImageUrl(person),
							onClick = {},
						)
					}
				}
			}
		}
	}
}

private fun LazyListScope.seriesSections(
	state: CinemaDetailState,
	actions: CinemaDetailActions,
	posterUrl: (BaseItemDto) -> String?,
	thumbUrl: (BaseItemDto) -> String?,
) {
	state.nextUp?.let { nextUp ->
		item(key = "next-up") {
			CinemaDetailSection(title = stringResource(R.string.lbl_next_up)) {
				Box(Modifier.padding(horizontal = SECTION_PADDING)) {
					CinemaWideCard(
						title = listOfNotNull(nextUp.cinemaEpisodeCode, nextUp.name).joinToString(" - "),
						subtitle = nextUp.runTimeTicks?.let(::formatCinemaRuntime),
						imageUrl = thumbUrl(nextUp),
						progress = nextUp.cinemaProgress,
						onClick = { actions.onPlayItem(nextUp) },
						onLongClick = { actions.onOpenItem(nextUp) },
						width = NEXT_UP_WIDTH,
					)
				}
			}
		}
	}

	if (state.children.isNotEmpty()) {
		item(key = "seasons") {
			CinemaDetailSection(title = stringResource(R.string.lbl_seasons)) {
				LazyRow(
					modifier = Modifier
						.fillMaxWidth()
						.focusRestorer(),
					contentPadding = PaddingValues(horizontal = SECTION_PADDING),
					horizontalArrangement = Arrangement.spacedBy(CinemaDimens.RowGap),
				) {
					items(state.children, key = { it.id }) { season ->
						CinemaPosterCard(
							title = season.name.orEmpty(),
							subtitle = null,
							imageUrl = posterUrl(season),
							onClick = { actions.onOpenItem(season) },
							width = CinemaDimens.RowCardWidth,
							badge = (season.childCount ?: season.userData?.unplayedItemCount)
								?.takeIf { it > 0 }
								?.toString(),
						)
					}
				}
			}
		}
	}
}

private fun LazyListScope.episodeList(
	state: CinemaDetailState,
	actions: CinemaDetailActions,
	thumbUrl: (BaseItemDto) -> String?,
) {
	if (state.children.isEmpty()) return

	item(key = "episodes-title") { CinemaSectionTitle(stringResource(R.string.lbl_episodes)) }

	items(state.children, key = { "episode-${it.id}" }) { episode ->
		CinemaEpisodeRow(
			title = listOfNotNull(episode.indexNumber?.let { "$it." }, episode.name).joinToString(" "),
			meta = listOfNotNull(episode.runTimeTicks?.takeIf { it > 0L }?.let(::formatCinemaRuntime)),
			rating = episode.communityRating,
			overview = episode.overview,
			imageUrl = thumbUrl(episode),
			progress = episode.cinemaProgress,
			played = episode.userData?.played == true,
			onClick = { actions.onPlayItem(episode) },
			onLongClick = { actions.onOpenItem(episode) },
		)
	}
}

@Composable
private fun CinemaDetailHero(
	item: BaseItemDto,
	state: CinemaDetailState,
	onPlay: () -> Unit,
	playFocusRequester: FocusRequester,
) {
	val (title, subtitle) = when (item.type) {
		BaseItemKind.SEASON -> (item.seriesName ?: item.name.orEmpty()) to item.name
		BaseItemKind.EPISODE -> item.name.orEmpty() to
			listOfNotNull(item.seriesName, item.cinemaEpisodeCode).joinToString(" · ").ifBlank { null }

		else -> item.name.orEmpty() to null
	}

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(DETAIL_HERO_HEIGHT)
			.cinemaFocusSection()
			.clip(CinemaDimens.HeroShape)
			.background(CinemaColors.HeroBackground)
			.border(1.dp, CinemaColors.Border, CinemaDimens.HeroShape),
	) {
		if (state.backdropUrl != null) {
			CinemaAsyncImage(
				model = state.backdropUrl,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)
		}

		Box(Modifier.fillMaxSize().background(CinemaColors.HeroScrimHorizontal))

		Row(
			modifier = Modifier.fillMaxSize(),
			horizontalArrangement = Arrangement.spacedBy(36.dp),
		) {
			Box(
				modifier = Modifier
					.fillMaxHeight()
					.aspectRatio(CinemaDimens.PosterAspect)
					.clip(RoundedCornerShape(CinemaDimens.HeroRadius))
					.background(CinemaColors.CardPlaceholder),
			) {
				if (state.posterUrl != null) {
					CinemaAsyncImage(
						model = state.posterUrl,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}

			Column(
				modifier = Modifier
					.fillMaxHeight()
					.padding(vertical = 24.dp, horizontal = 8.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
			) {
				Text(
					text = title,
					color = CinemaColors.OnMedia,
					fontSize = DETAIL_TITLE_SIZE,
					lineHeight = DETAIL_TITLE_LINE_HEIGHT,
					fontWeight = FontWeight.Bold,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)

				if (!subtitle.isNullOrBlank()) {
					Text(
						text = subtitle,
						color = CinemaColors.OnMedia,
						fontSize = CinemaDimens.SectionTitleSize,
						fontWeight = FontWeight.SemiBold,
						maxLines = 1,
					)
				}

				CinemaDetailMeta(item)

				val target = state.playTarget
				val resumable = (target?.userData?.playbackPositionTicks ?: 0L) > 0L
				CinemaButton(
					text = stringResource(if (resumable) R.string.cinema_resume else R.string.lbl_play),
					icon = painterResource(if (resumable) R.drawable.ic_resume else R.drawable.ic_play),
					primary = true,
					onClick = onPlay,
					modifier = Modifier.focusRequester(playFocusRequester),
				)
			}
		}
	}
}

/** Meta line: year (range), age badge, runtime, rating, audio languages, end time. */
@Composable
private fun CinemaDetailMeta(item: BaseItemDto) {
	val hasRuntime = item.type != BaseItemKind.SERIES && item.type != BaseItemKind.SEASON
	val parts = buildList {
		// Seasons inherit the series' year, which is noise on the season page.
		if (item.type != BaseItemKind.SEASON) item.cinemaYearLabel?.let(::add)
		if (hasRuntime) item.runTimeTicks?.takeIf { it > 0L }?.let { add(formatCinemaRuntime(it)) }
	}
	val trailing = buildList {
		item.cinemaAudioLanguages.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
		item.criticRating?.let { add("$it%") }
		if (hasRuntime) {
			cinemaEndTime(item.runTimeTicks, item.userData?.playbackPositionTicks)?.let {
				add(stringResource(R.string.cinema_ends_at, it))
			}
		}
	}

	val officialRating = item.officialRating
	CinemaMetaLine(
		parts = parts,
		rating = item.communityRating,
		leading = if (officialRating != null) {
			{ CinemaTag(officialRating) }
		} else {
			null
		},
		trailing = trailing,
		onMedia = true,
	)
}

/** The info ribbon below the capsule: director, writer, studios, genres. */
@Composable
private fun CinemaDetailRibbon(item: BaseItemDto) {
	val director = item.people
		?.filter { it.type == PersonKind.DIRECTOR }
		?.mapNotNull { it.name }
		?.distinct()
		?.take(MAX_PEOPLE)
		.orEmpty()
	val writer = item.people
		?.filter { it.type == PersonKind.WRITER }
		?.mapNotNull { it.name }
		?.distinct()
		?.take(MAX_PEOPLE)
		.orEmpty()
	val studios = item.studios?.mapNotNull { it.name }.orEmpty()
	val genres = item.genres.orEmpty()

	val rows = listOfNotNull(
		director.takeIf { it.isNotEmpty() }?.let { R.string.cinema_director to it },
		writer.takeIf { it.isNotEmpty() }?.let { R.string.cinema_writer to it },
		studios.takeIf { it.isNotEmpty() }?.let { R.string.cinema_studios to it },
		genres.takeIf { it.isNotEmpty() }?.let { R.string.lbl_genres to it },
	)

	if (rows.isEmpty()) return

	CinemaPanel(Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			rows.forEach { (labelRes, values) ->
				Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
					Text(
						text = stringResource(labelRes),
						color = CinemaColors.Muted,
						fontSize = CinemaDimens.MetaSize,
						modifier = Modifier.width(RIBBON_LABEL_WIDTH),
					)
					Text(
						text = values.joinToString(", "),
						color = CinemaColors.Text,
						fontSize = CinemaDimens.MetaSize,
						fontWeight = FontWeight.SemiBold,
					)
				}
			}
		}
	}
}

/**
 * Tagline, overview and tags. The panel is a focus target of its own so moving down from
 * the play button scrolls the description into view instead of jumping past it.
 */
@Composable
private fun CinemaDetailOverview(item: BaseItemDto) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.clip(CinemaDimens.PanelShape)
			.background(CinemaColors.Surface.copy(alpha = 0.55f))
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = if (focused) CinemaColors.Focus else CinemaColors.Border,
				shape = CinemaDimens.PanelShape,
			)
			.onFocusChanged { focused = it.isFocused }
			.focusable(),
	) {
		Column(
			modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			item.taglines?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { tagline ->
				Text(
					text = tagline,
					color = CinemaColors.Text,
					fontSize = CinemaDimens.SectionTitleSize,
					fontWeight = FontWeight.SemiBold,
				)
			}

			item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
				Text(
					text = overview,
					color = CinemaColors.TextSoft,
					fontSize = CinemaDimens.BodySize,
					lineHeight = OVERVIEW_LINE_HEIGHT,
				)
			}

			item.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(
						text = stringResource(R.string.cinema_tags),
						color = CinemaColors.Muted,
						fontSize = CinemaDimens.MetaSize,
					)
					Text(
						text = tags.joinToString(", "),
						color = CinemaColors.Text,
						fontSize = CinemaDimens.MetaSize,
						fontWeight = FontWeight.SemiBold,
					)
				}
			}
		}
	}
}

/** Rounded section panel with a title — used for next up, seasons and cast. */
@Composable
private fun CinemaDetailSection(
	title: String,
	content: @Composable () -> Unit,
) {
	CinemaPanel(Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.padding(vertical = 22.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			CinemaSectionTitle(
				text = title,
				modifier = Modifier.padding(horizontal = SECTION_PADDING),
			)
			content()
		}
	}
}

/** Actors first, then everyone else; each person only once per role. */
private val BaseItemDto.cinemaPeople: List<BaseItemPerson>
	get() {
		val all = people.orEmpty().filter { !it.name.isNullOrBlank() }
		val actors = all.filter { it.type == PersonKind.ACTOR || it.type == PersonKind.GUEST_STAR }
		val crew = all.filter { it.type != PersonKind.ACTOR && it.type != PersonKind.GUEST_STAR }
		return (actors + crew)
			.distinctBy { "${it.id}-${it.type}" }
			.take(MAX_CAST)
	}

@Composable
private fun PersonKind.label(): String? = when (this) {
	PersonKind.DIRECTOR -> stringResource(R.string.cinema_director)
	PersonKind.WRITER -> stringResource(R.string.cinema_writer)
	PersonKind.GUEST_STAR -> stringResource(R.string.lbl_guest_stars)
	else -> null
}

// endregion

private const val MAX_PEOPLE = 3
private const val MAX_CAST = 40
private val DETAIL_HERO_HEIGHT = 320.dp
private val DETAIL_TITLE_SIZE = 44.sp
private val DETAIL_TITLE_LINE_HEIGHT = 50.sp
private val OVERVIEW_LINE_HEIGHT = 27.sp
private val NAV_BUTTON_SIZE = 44.dp
private val COLLECTION_POSTER_WIDTH = 96.dp
private val RIBBON_LABEL_WIDTH = 110.dp
private val SECTION_PADDING = 24.dp
private val NEXT_UP_WIDTH = 320.dp


