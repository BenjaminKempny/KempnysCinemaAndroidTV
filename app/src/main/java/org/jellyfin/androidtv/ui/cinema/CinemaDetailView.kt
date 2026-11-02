package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import org.jellyfin.androidtv.data.contentwarning.ContentWarningCategory
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.sdns.SdnsRatingDialog
import org.jellyfin.androidtv.ui.sdns.toSdnsQuery
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.UUID

data class CinemaDetailActions(
	val onBack: () -> Unit,
	val onHome: () -> Unit,
	/** Plays [CinemaDetailState.playTarget]. */
	val onPlay: () -> Unit,
	/** Plays [CinemaDetailState.trailer] (movies only). */
	val onPlayTrailer: () -> Unit = {},
	/** Plays a specific episode (next up card, episode list). */
	val onPlayItem: (BaseItemDto) -> Unit,
	val onOpenItem: (BaseItemDto) -> Unit,
	/** Opens the detail page of a cast or crew member. */
	val onOpenPerson: (BaseItemPerson) -> Unit,
	val onSelectSort: (CinemaSort) -> Unit,
	/** Switches the season whose episodes are listed on the series page. */
	val onSelectSeason: (UUID) -> Unit,
)

/**
 * The cinema detail capsule (spec §2.5) and — for box sets — the collection browser.
 *
 * Layout per item type (reference screenshots):
 * - Movie / episode: capsule -> info ribbon -> overview -> cast & crew
 * - Series: capsule -> info ribbon -> next up -> episodes (season dropdown) -> cast & crew
 *
 * Seasons have no page of their own; they open the series with that season selected.
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
	val seasonSelectorFocus = remember { SeasonSelectorFocus() }
	var contentWarningCategory by rememberSaveable(item?.id) { mutableStateOf<ContentWarningCategory?>(null) }
	val focusMemory = LocalCinemaFocusMemory.current
	val listState = rememberCinemaLazyListState("detail")

	LaunchedEffect(item?.id, state.isCollection, personFocusReady(state)) {
		// Returning from a season, collection entry or person gives the focus back to that
		// card instead of the play button.
		if (focusMemory?.isRestoring == true) return@LaunchedEffect
		if (item != null && !state.isCollection) runCatching { playFocusRequester.requestFocus() }
	}

	CinemaBackground(modifier, backdropUrl = state.blurredBackdropUrl) {
		CinemaLazyColumn(
			state = listState,
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
						seasonSelectorFocus = seasonSelectorFocus,
						contentWarningCategory = contentWarningCategory,
						onSelectContentWarningCategory = { contentWarningCategory = it },
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

			CinemaPageTitle(
				text = collection.name.orEmpty(),
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
	seasonSelectorFocus: SeasonSelectorFocus,
	contentWarningCategory: ContentWarningCategory?,
	onSelectContentWarningCategory: (ContentWarningCategory?) -> Unit,
) {
	val isPerson = detailItem.type == BaseItemKind.PERSON
	// Series don't show a description at all.
	val hasOverview = detailItem.type != BaseItemKind.SERIES && !detailItem.overview.isNullOrBlank()

	item(key = "hero") {
		CinemaDetailHero(
			item = detailItem,
			state = state,
			onPlay = actions.onPlay,
			onPlayTrailer = actions.onPlayTrailer,
			playFocusRequester = playFocusRequester,
			// Without a next up card the season dropdown is the next stop below the play button.
			downFocus = seasonSelectorFocus.takeIf {
				detailItem.type == BaseItemKind.SERIES && state.nextUp == null
			},
		)
	}

	if (!isPerson) item(key = "ribbon") { CinemaDetailRibbon(detailItem) }

	state.contentWarnings?.let { warnings ->
		contentWarningsSection(
			result = warnings,
			selectedCategory = contentWarningCategory,
			onSelectCategory = onSelectContentWarningCategory,
		)
	}

	if (hasOverview) {
		item(key = "overview") {
			CinemaDetailOverview(
				item = detailItem,
				// People have no play button: the biography takes the initial focus instead.
				focusRequester = if (isPerson) playFocusRequester else null,
			)
		}
	}

	when (detailItem.type) {
		BaseItemKind.SERIES -> seriesSections(state, actions, thumbUrl, seasonSelectorFocus)
		BaseItemKind.PERSON -> filmography(
			state = state,
			actions = actions,
			posterUrl = posterUrl,
			firstItemFocusRequester = if (hasOverview) null else playFocusRequester,
		)

		else -> Unit
	}

	val people = detailItem.cinemaPeople
	if (people.isNotEmpty()) {
		item(key = "cast") {
			CinemaDetailSection(title = stringResource(R.string.lbl_cast_crew)) {
				LazyRow(
					state = rememberCinemaLazyListState("cast"),
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
							onClick = { actions.onOpenPerson(person) },
							focusKey = "person-${person.id}-${person.type}-${person.role}",
						)
					}
				}
			}
		}
	}
}

/** All movies and series a person appears in, as a sortable poster grid. */
private fun LazyListScope.filmography(
	state: CinemaDetailState,
	actions: CinemaDetailActions,
	posterUrl: (BaseItemDto) -> String?,
	firstItemFocusRequester: FocusRequester?,
) {
	item(key = "filmography-title") {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			CinemaSectionTitle(
				text = stringResource(R.string.cinema_filmography),
				modifier = Modifier.weight(1f),
			)

			if (state.children.isNotEmpty()) {
				CinemaSortSelector(sort = state.sort, onSelect = actions.onSelectSort)
			}
		}
	}

	if (state.children.isEmpty()) {
		if (!state.loading) {
			item(key = "filmography-empty") {
				Text(
					text = stringResource(R.string.cinema_empty),
					color = CinemaColors.Muted,
					fontSize = CinemaDimens.BodySize,
				)
			}
		}
		return
	}

	cinemaGrid(
		items = state.children,
		posterUrl = posterUrl,
		onOpenItem = actions.onOpenItem,
		keyPrefix = "filmography",
		firstItemFocusRequester = firstItemFocusRequester,
	)
}

/** Key that changes once the person page has something to focus initially. */
private fun personFocusReady(state: CinemaDetailState): Boolean {
	val item = state.item ?: return false
	if (!state.isPerson) return false
	val hasOverview = !item.overview.isNullOrBlank()
	return hasOverview || state.children.isNotEmpty()
}

private fun LazyListScope.seriesSections(
	state: CinemaDetailState,
	actions: CinemaDetailActions,
	thumbUrl: (BaseItemDto) -> String?,
	seasonSelectorFocus: SeasonSelectorFocus,
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
						focusKey = "next-up",
						// Moving down goes to the season dropdown first, then into the episodes.
						modifier = seasonSelectorFocus.downModifier,
					)
				}
			}
		}
	}

	if (state.children.isEmpty()) return

	item(key = "episodes-title") {
		DisposableEffect(seasonSelectorFocus) {
			seasonSelectorFocus.attached = true
			onDispose { seasonSelectorFocus.attached = false }
		}

		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			CinemaSectionTitle(
				text = stringResource(R.string.lbl_episodes),
				modifier = Modifier.weight(1f),
			)

			CinemaSeasonSelector(
				seasons = state.children,
				selectedId = state.selectedSeasonId,
				onSelect = actions.onSelectSeason,
				buttonModifier = Modifier.focusRequester(seasonSelectorFocus.requester),
			)
		}
	}

	items(state.episodes, key = { "episode-${it.id}" }) { episode ->
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
			focusKey = "episode-${episode.id}",
		)
	}
}

@Composable
private fun CinemaDetailHero(
	item: BaseItemDto,
	state: CinemaDetailState,
	onPlay: () -> Unit,
	playFocusRequester: FocusRequester,
	downFocus: SeasonSelectorFocus? = null,
	onPlayTrailer: () -> Unit = {},
) {
	val (title, subtitle) = when (item.type) {
		BaseItemKind.EPISODE -> item.name.orEmpty() to
			listOfNotNull(item.seriesName, item.cinemaEpisodeCode).joinToString(" · ").ifBlank { null }

		else -> item.name.orEmpty() to null
	}

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(DETAIL_HERO_HEIGHT)
			.cinemaFocusSection(topInset = 28.dp + NAV_BUTTON_SIZE + 24.dp)
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

				if (item.type == BaseItemKind.PERSON) {
					CinemaPersonMeta(item)
				} else {
					CinemaDetailMeta(item)

					val target = state.playTarget
					val resumable = (target?.userData?.playbackPositionTicks ?: 0L) > 0L
					Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
						CinemaButton(
							text = stringResource(if (resumable) R.string.cinema_resume else R.string.lbl_play),
							icon = painterResource(if (resumable) R.drawable.ic_resume else R.drawable.ic_play),
							primary = true,
							onClick = onPlay,
							modifier = Modifier
								.then(downFocus?.downModifier ?: Modifier)
								.focusRequester(playFocusRequester),
						)

						// Only movies with a downloaded local trailer get the button.
						if (item.type == BaseItemKind.MOVIE && state.trailer != null) {
							CinemaButton(
								text = stringResource(R.string.cinema_trailer),
								icon = painterResource(R.drawable.ic_trailer),
								onClick = onPlayTrailer,
							)
						}

						// Rate in ScreenDiceNSpice, always available for movies.
						if (item.type == BaseItemKind.MOVIE) {
							var showRating by remember(item.id) { mutableStateOf(false) }
							CinemaButton(
								text = stringResource(R.string.sdns_rate_button),
								icon = painterResource(R.drawable.ic_star),
								onClick = { showRating = true },
							)
							if (showRating) {
								SdnsRatingDialog(
									query = item.toSdnsQuery(),
									onDismiss = { showRating = false },
								)
							}
						}
					}
				}
			}
		}
	}
}

/** Birthday with age (or lifespan) and birthplace of a person. */
@Composable
private fun CinemaPersonMeta(item: BaseItemDto) {
	val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
	val birthDate = item.premiereDate
	val deathDate = item.endDate

	val lines = buildList {
		if (birthDate != null) {
			val age = ChronoUnit.YEARS.between(birthDate, deathDate ?: LocalDateTime.now())
			add(stringResource(R.string.person_birthday_and_age, birthDate.format(dateFormat), age))
		}
		if (deathDate != null) add(stringResource(R.string.cinema_died, deathDate.format(dateFormat)))
		item.productionLocations?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
	}

	lines.forEach { line ->
		Text(
			text = line,
			color = CinemaColors.OnMedia,
			fontSize = CinemaDimens.MetaSize,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
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
 * Tagline and overview. The panel is a focus target of its own so moving down from
 * the play button scrolls the description into view instead of jumping past it.
 *
 * Long descriptions are cut after [OVERVIEW_MAX_LINES] lines; Enter opens the full text in a
 * scrollable overlay that Back closes again.
 */
@Composable
private fun CinemaDetailOverview(
	item: BaseItemDto,
	focusRequester: FocusRequester? = null,
) {
	var focused by remember { mutableStateOf(false) }
	var truncated by remember(item.id) { mutableStateOf(false) }
	var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
	val interactionSource = remember { MutableInteractionSource() }

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
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.onFocusChanged { focused = it.isFocused }
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = { if (truncated) expanded = true },
			),
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
					maxLines = OVERVIEW_MAX_LINES,
					overflow = TextOverflow.Ellipsis,
					onTextLayout = { truncated = it.hasVisualOverflow },
				)
			}
		}
	}

	if (expanded) {
		CinemaOverviewDialog(
			item = item,
			onDismiss = { expanded = false },
		)
	}
}

/** Full screen, D-Pad scrollable view of the complete description. Back closes it. */
@Composable
private fun CinemaOverviewDialog(
	item: BaseItemDto,
	onDismiss: () -> Unit,
) {
	val scrollState = rememberScrollState()
	val scope = rememberCoroutineScope()
	val focusRequester = remember { FocusRequester() }
	val scrollStep = with(LocalDensity.current) { OVERVIEW_SCROLL_STEP.toPx() }

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(
			dismissOnBackPress = true,
			dismissOnClickOutside = true,
			usePlatformDefaultWidth = false,
			decorFitsSystemWindows = false,
		),
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black.copy(alpha = 0.75f)),
			contentAlignment = Alignment.Center,
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth(0.7f)
					.fillMaxHeight(0.85f)
					.clip(CinemaDimens.PanelShape)
					.background(CinemaColors.Surface)
					.border(2.dp, CinemaColors.Focus, CinemaDimens.PanelShape)
					.focusRequester(focusRequester)
					.onKeyEvent { event ->
						if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
						val delta = when (event.key) {
							Key.DirectionDown -> scrollStep
							Key.DirectionUp -> -scrollStep
							else -> return@onKeyEvent false
						}
						scope.launch { scrollState.animateScrollBy(delta) }
						true
					}
					.focusable(),
			) {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(scrollState)
						.padding(horizontal = 40.dp, vertical = 32.dp),
					verticalArrangement = Arrangement.spacedBy(18.dp),
				) {
					Text(
						text = item.name.orEmpty(),
						color = CinemaColors.Text,
						fontSize = CinemaDimens.SectionTitleSize,
						fontWeight = FontWeight.Bold,
					)

					item.taglines?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { tagline ->
						Text(
							text = tagline,
							color = CinemaColors.Text,
							fontSize = CinemaDimens.BodySize,
							fontWeight = FontWeight.SemiBold,
						)
					}

					Text(
						text = item.overview.orEmpty(),
						color = CinemaColors.TextSoft,
						fontSize = CinemaDimens.BodySize,
						lineHeight = OVERVIEW_LINE_HEIGHT,
					)
				}
			}
		}
	}

	LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }
}

/**
 * Lets the next up card / play button jump straight down to the season dropdown, which sits
 * at the end of the episodes title row and would otherwise be skipped by the D-Pad search.
 */
@Stable
private class SeasonSelectorFocus {
	val requester = FocusRequester()

	/** Whether the dropdown is currently composed; an unattached requester must not be used. */
	var attached by mutableStateOf(false)

	val downModifier: Modifier = Modifier.focusProperties {
		if (attached) down = requester
	}
}

/**
 * Rounded section panel with a title — used for next up, seasons and cast. Focusing any
 * card inside centres the whole panel (title included) in the viewport.
 */
@Composable
private fun CinemaDetailSection(
	title: String,
	content: @Composable () -> Unit,
) {
	CinemaPanel(Modifier.fillMaxWidth().cinemaFocusSection()) {
		Column(
			modifier = Modifier.padding(vertical = 22.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			CinemaSectionTitle(
				text = title,
				modifier = Modifier.padding(horizontal = SECTION_PADDING),
			)
			// Horizontal rails inside keep the minimal scrolling; only the page centres.
			CinemaRowScroll(content)
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
private const val OVERVIEW_MAX_LINES = 5
private val OVERVIEW_SCROLL_STEP = 120.dp
private val NAV_BUTTON_SIZE = 44.dp
private val COLLECTION_POSTER_WIDTH = 96.dp
private val RIBBON_LABEL_WIDTH = 110.dp
private val SECTION_PADDING = 24.dp
private val NEXT_UP_WIDTH = 320.dp


