package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.sdk.model.api.BaseItemDto

/** Callbacks the host fragment wires up to navigation and playback. */
data class CinemaHomeActions(
	val onOpenItem: (BaseItemDto) -> Unit,
	val onPlayItem: (BaseItemDto) -> Unit,
	val onOpenHeroItem: (CinemaHeroItem) -> Unit,
	val onPlayHeroItem: (CinemaHeroItem) -> Unit,
	val onItemMenu: (BaseItemDto) -> Unit,
	val onRailSelect: (CinemaRailItem) -> Unit,
)

/**
 * The cinema home screen (`CinemaHome.tsx`).
 *
 * Layout: a fixed icon rail on the left, the rounded shell on the right holding the app
 * icon, the `All | Collections | Genres` segmented control and the scrolling content.
 * Content order in the `all` view is Spotlight hero -> Continue Watching -> catalog grid,
 * exactly as on the web.
 */
@Composable
fun CinemaHomeScreen(
	state: CinemaHomeState,
	actions: CinemaHomeActions,
	posterUrl: (BaseItemDto) -> String?,
	thumbUrl: (BaseItemDto) -> String?,
	onSelectView: (CinemaView) -> Unit,
	onSelectSort: (CinemaSort) -> Unit,
	onLoadNextPage: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val listState = rememberLazyListState()
	val heroFocusRequester = remember { FocusRequester() }
	val continueWatchingFocusRequester = remember { FocusRequester() }
	val tabs = listOf(
		stringResource(R.string.cinema_tab_all),
		stringResource(R.string.cinema_tab_collections),
		stringResource(R.string.cinema_tab_genres),
	)

	// Gerichteter Sprung: DOWN aus den Tabs überspringt den (zufälligen) Spotlight und
	// landet direkt auf Continue Watching (Web: CinemaHome.tsx:67).
	//
	// The target card lives in a different LazyColumn item that is usually not composed
	// yet, so the row is scrolled into view first and the focus request is only made
	// once composition had a chance to attach the requester.
	val coroutineScope = rememberCoroutineScope()
	val jumpToContinueWatching: (() -> Unit)? =
		if (state.view == CinemaView.All && state.continueWatching.isNotEmpty()) {
			{
				val targetIndex = if (state.hero.isNotEmpty()) 2 else 1
				coroutineScope.launch {
					listState.animateScrollToItem(targetIndex)
					withFrameNanos { }
					runCatching { continueWatchingFocusRequester.requestFocus() }
				}
				Unit
			}
		} else {
			null
		}

	val railSelection = when (state.mediaType) {
		CinemaMediaType.Movies -> CinemaRailItem.Movies
		CinemaMediaType.Shows -> CinemaRailItem.Shows
	}

	CinemaBackground(modifier) {
		Row(Modifier.fillMaxSize()) {
			CinemaRail(
				selected = railSelection,
				onSelect = actions.onRailSelect,
			)

			CinemaShell(
				modifier = Modifier
					.fillMaxSize()
					.padding(end = 24.dp, top = 24.dp, bottom = 24.dp),
			) {
				LazyColumn(
					state = listState,
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(
						start = 28.dp,
						end = 28.dp,
						top = 24.dp,
						bottom = CinemaDimens.Overscan,
					),
					verticalArrangement = Arrangement.spacedBy(28.dp),
				) {
					item(key = "header") {
						CinemaHeader(
							tabs = tabs,
							selectedIndex = state.view.ordinal,
							onSelect = { onSelectView(CinemaView.entries[it]) },
							title = stringResource(
								when (state.mediaType) {
									CinemaMediaType.Movies -> R.string.lbl_movies
									CinemaMediaType.Shows -> R.string.lbl_tv_series
								}
							),
							onJumpDown = jumpToContinueWatching,
						)
					}

					when (state.view) {
						CinemaView.All -> allView(
							state = state,
							actions = actions,
							onSelectSort = onSelectSort,
							posterUrl = posterUrl,
							thumbUrl = thumbUrl,
							heroFocusRequester = heroFocusRequester,
							continueWatchingFocusRequester = continueWatchingFocusRequester,
						)

						CinemaView.Collections -> collectionsView(state, actions, posterUrl)
						CinemaView.Genres -> genresView(state, actions, posterUrl)
					}

					if (state.loading) {
						item(key = "skeleton") { CinemaSkeletonRow() }
					}
				}
			}
		}
	}

	// Infinite scroll — trigger a page load as the end of the catalog comes into view.
	if (state.view == CinemaView.All) {
		val nearEnd by remember(listState) {
			derivedStateOf {
				val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
				last >= listState.layoutInfo.totalItemsCount - 2
			}
		}

		LaunchedEffect(listState) {
			snapshotFlow { nearEnd }
				.distinctUntilChanged()
				.collect { if (it) onLoadNextPage() }
		}
	}
}

@Composable
private fun CinemaHeader(
	tabs: List<String>,
	selectedIndex: Int,
	onSelect: (Int) -> Unit,
	title: String,
	onJumpDown: (() -> Unit)?,
) {
	Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
		Box(Modifier.fillMaxWidth()) {
			Icon(
				painter = painterResource(R.drawable.app_logo),
				contentDescription = null,
				modifier = Modifier
					.align(Alignment.CenterStart)
					.size(52.dp)
					.clip(CinemaDimens.CardShape),
			)

			CinemaTabRow(
				tabs = tabs,
				selectedIndex = selectedIndex,
				onSelect = onSelect,
				modifier = Modifier
					.align(Alignment.Center)
					.onPreviewKeyEvent { event ->
						val jump = onJumpDown
						if (jump != null &&
							event.type == KeyEventType.KeyDown &&
							event.key == Key.DirectionDown
						) {
							jump()
							true
						} else {
							false
						}
					},
			)
		}

		Text(
			text = title,
			color = CinemaColors.Text,
			fontSize = CinemaDimens.PageTitleSize,
			fontWeight = FontWeight.Bold,
			maxLines = 1,
		)
	}
}

private fun LazyListScope.allView(
	state: CinemaHomeState,
	actions: CinemaHomeActions,
	onSelectSort: (CinemaSort) -> Unit,
	posterUrl: (BaseItemDto) -> String?,
	thumbUrl: (BaseItemDto) -> String?,
	heroFocusRequester: FocusRequester,
	continueWatchingFocusRequester: FocusRequester,
) {
	if (state.hero.isNotEmpty()) {
		item(key = "hero") {
			CinemaHero(
				items = state.hero,
				onPlay = actions.onPlayHeroItem,
				onDetails = actions.onOpenHeroItem,
				playFocusRequester = heroFocusRequester,
			)
		}
	}

	if (state.continueWatching.isNotEmpty()) {
		item(key = "continue-watching") {
			CinemaPanel(Modifier.fillMaxWidth()) {
				Column(
					modifier = Modifier.padding(vertical = 18.dp),
					verticalArrangement = Arrangement.spacedBy(14.dp),
				) {
					CinemaSectionTitle(
						text = stringResource(R.string.lbl_continue_watching),
						modifier = Modifier.padding(horizontal = 18.dp),
					)

					LazyRow(
						modifier = Modifier
							.fillMaxWidth()
							.focusRestorer(),
						contentPadding = PaddingValues(horizontal = 18.dp),
						horizontalArrangement = Arrangement.spacedBy(CinemaDimens.RowGap),
					) {
						itemsIndexed(state.continueWatching) { index, item ->
							CinemaWideCard(
								title = item.cinemaTitle,
								subtitle = item.cinemaSubtitle,
								imageUrl = thumbUrl(item),
								progress = item.cinemaProgress,
								onClick = { actions.onPlayItem(item) },
								onLongClick = { actions.onItemMenu(item) },
								modifier = if (index == 0) {
									Modifier.focusRequester(continueWatchingFocusRequester)
								} else {
									Modifier
								},
							)
						}
					}
				}
			}
		}
	}

	if (state.catalog.isNotEmpty()) {
		item(key = "catalog-title") {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				CinemaSectionTitle(
					text = stringResource(
						when (state.mediaType) {
							CinemaMediaType.Movies -> R.string.cinema_all_movies
							CinemaMediaType.Shows -> R.string.cinema_all_shows
						}
					),
					modifier = Modifier.weight(1f),
				)

				CinemaSortSelector(sort = state.sort, onSelect = onSelectSort)
			}
		}

		// The catalog is chunked into rows so it can live inside the outer LazyColumn.
		// A nested LazyVerticalGrid is not allowed and would also break D-Pad focus.
		val rows = state.catalog.chunked(CATALOG_COLUMNS)
		items(rows.size, key = { "catalog-row-$it" }) { rowIndex ->
			Row(horizontalArrangement = Arrangement.spacedBy(CinemaDimens.GridHorizontalGap)) {
				rows[rowIndex].forEach { item ->
					CinemaPosterCard(
						title = item.cinemaTitle,
						subtitle = item.cinemaSubtitle,
						imageUrl = posterUrl(item),
						progress = item.cinemaProgress,
						onClick = { actions.onOpenItem(item) },
						onLongClick = { actions.onItemMenu(item) },
						width = CATALOG_CARD_WIDTH,
					)
				}
			}
		}
	}
}

private fun LazyListScope.collectionsView(
	state: CinemaHomeState,
	actions: CinemaHomeActions,
	posterUrl: (BaseItemDto) -> String?,
) {
	item(key = "collections-subtitle") {
		Text(
			text = stringResource(R.string.cinema_collections_subtitle),
			color = CinemaColors.Muted,
			fontSize = CinemaDimens.BodySize,
		)
	}

	val rows = state.collections.chunked(CATALOG_COLUMNS)
	items(rows.size, key = { "collection-row-$it" }) { rowIndex ->
		Row(horizontalArrangement = Arrangement.spacedBy(CinemaDimens.GridHorizontalGap)) {
			rows[rowIndex].forEach { item ->
				CinemaPosterCard(
					title = item.cinemaTitle,
					subtitle = item.productionYear?.toString(),
					imageUrl = posterUrl(item),
					onClick = { actions.onOpenItem(item) },
					width = CATALOG_CARD_WIDTH,
				)
			}
		}
	}
}

private fun LazyListScope.genresView(
	state: CinemaHomeState,
	actions: CinemaHomeActions,
	posterUrl: (BaseItemDto) -> String?,
) {
	items(state.genreRows, key = { it.name }) { row ->
		Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
			CinemaSectionTitle(row.name)

			LazyRow(
				modifier = Modifier
					.fillMaxWidth()
					.focusRestorer(),
				horizontalArrangement = Arrangement.spacedBy(CinemaDimens.RowGap),
			) {
				items(row.items) { item ->
					CinemaPosterCard(
						title = item.cinemaTitle,
						subtitle = item.cinemaSubtitle,
						imageUrl = posterUrl(item),
						progress = item.cinemaProgress,
						onClick = { actions.onOpenItem(item) },
						onLongClick = { actions.onItemMenu(item) },
						width = CATALOG_CARD_WIDTH,
					)
				}
			}
		}
	}
}

/** A7 — shimmering placeholder row shown while the first page is loading. */
@Composable
private fun CinemaSkeletonRow() {
	Row(horizontalArrangement = Arrangement.spacedBy(CinemaDimens.GridHorizontalGap)) {
		repeat(SKELETON_CARDS) {
			Box(
				modifier = Modifier
					.width(CATALOG_CARD_WIDTH)
					.aspectRatio(CinemaDimens.PosterAspect)
					.clip(CinemaDimens.CardShape)
					.cinemaSkeleton(),
			)
		}
	}
}

private const val CATALOG_COLUMNS = 6
private const val SKELETON_CARDS = 6
private val CATALOG_CARD_WIDTH = 185.dp
