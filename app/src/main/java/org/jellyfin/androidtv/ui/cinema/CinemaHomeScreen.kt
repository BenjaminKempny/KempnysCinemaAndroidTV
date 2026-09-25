package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
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
	// The hero is recycled by the LazyColumn whenever it leaves the viewport. Without
	// this guard it would request the focus again on every re-entry, yanking the user
	// out of the catalog grid (and out of the tab row) back to the spotlight.
	var heroFocusRequested by rememberSaveable { mutableStateOf(false) }
	val tabs = listOf(
		stringResource(R.string.cinema_tab_all),
		stringResource(R.string.cinema_tab_collections),
		stringResource(R.string.cinema_tab_genres),
	)

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
					.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
			) {
				LazyColumn(
					state = listState,
					// Restores focus to the last focused row instead of letting it fall
					// back to the header (which would scroll the page to the top).
					modifier = Modifier
						.fillMaxSize()
						.focusRestorer(),
					contentPadding = PaddingValues(
						start = CinemaDimens.PageGutter,
						end = CinemaDimens.PageGutter,
						top = CinemaDimens.PageGutter,
						bottom = CinemaDimens.Overscan,
					),
					verticalArrangement = Arrangement.spacedBy(CinemaDimens.SectionGap),
				) {
					item(key = "header") {
						CinemaHeader(
							tabs = tabs,
							selectedIndex = state.view.ordinal,
							onSelect = { onSelectView(CinemaView.entries[it]) },
							title = stringResource(
								when (state.view) {
									CinemaView.All -> when (state.mediaType) {
										CinemaMediaType.Movies -> R.string.lbl_movies
										CinemaMediaType.Shows -> R.string.lbl_tv_series
									}

									CinemaView.Collections -> R.string.lbl_collections
									CinemaView.Genres -> R.string.lbl_genres
								}
							),
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
							requestHeroFocus = !heroFocusRequested,
							onHeroFocusRequested = { heroFocusRequested = true },
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

	// Infinite scroll — load the next page once the user actually approaches the end of
	// the catalog. Guarded by `catalogHasMore` so it stays idle until there is a catalog
	// and stops once everything is loaded.
	val shouldLoadMore by remember {
		derivedStateOf {
			val info = listState.layoutInfo
			val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
			info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
		}
	}

	LaunchedEffect(shouldLoadMore, state.view, state.catalogHasMore) {
		if (shouldLoadMore && state.view == CinemaView.All && state.catalogHasMore) {
			onLoadNextPage()
		}
	}
}

@Composable
private fun CinemaHeader(
	tabs: List<String>,
	selectedIndex: Int,
	onSelect: (Int) -> Unit,
	title: String,
) {
	Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
		Box(Modifier.fillMaxWidth()) {
			Image(
				painter = painterResource(R.drawable.kempnys_logo),
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
				modifier = Modifier.align(Alignment.Center),
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
	requestHeroFocus: Boolean,
	onHeroFocusRequested: () -> Unit,
) {
	if (state.hero.isNotEmpty()) {
		item(key = "hero") {
			CinemaHero(
				items = state.hero,
				onPlay = actions.onPlayHeroItem,
				onDetails = actions.onOpenHeroItem,
				playFocusRequester = heroFocusRequester,
				requestInitialFocus = requestHeroFocus,
				onInitialFocusRequested = onHeroFocusRequested,
			)
		}
	}

	if (state.continueWatching.isNotEmpty()) {
		item(key = "continue-watching") {
			CinemaPanel(Modifier.fillMaxWidth()) {
				Column(
					modifier = Modifier.padding(vertical = 24.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					CinemaSectionTitle(
						text = stringResource(R.string.lbl_continue_watching),
						modifier = Modifier.padding(horizontal = 24.dp),
					)

					LazyRow(
						modifier = Modifier
							.fillMaxWidth()
							.focusRestorer(),
						contentPadding = PaddingValues(horizontal = 24.dp),
						horizontalArrangement = Arrangement.spacedBy(CinemaDimens.RowGap),
					) {
						items(state.continueWatching, key = { it.id }) { item ->
							CinemaWideCard(
								title = item.cinemaTitle,
								subtitle = item.cinemaSubtitle,
								imageUrl = thumbUrl(item),
								progress = item.cinemaProgress,
								onClick = { actions.onPlayItem(item) },
								onLongClick = { actions.onItemMenu(item) },
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

		cinemaGrid(
			items = state.catalog,
			posterUrl = posterUrl,
			onOpenItem = actions.onOpenItem,
			keyPrefix = "catalog",
			onItemMenu = actions.onItemMenu,
		)
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

	cinemaGrid(
		items = state.collections,
		posterUrl = posterUrl,
		onOpenItem = actions.onOpenItem,
		keyPrefix = "collection",
		// Collections have no release year of their own.
		subtitle = { null },
	)
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
				items(row.items, key = { it.id }) { item ->
					CinemaPosterCard(
						title = item.cinemaTitle,
						subtitle = item.cinemaSubtitle,
						imageUrl = posterUrl(item),
						progress = item.cinemaProgress,
						onClick = { actions.onOpenItem(item) },
						onLongClick = { actions.onItemMenu(item) },
						width = CinemaDimens.RowCardWidth,
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
					.width(CinemaDimens.GridCardWidth)
					.aspectRatio(CinemaDimens.PosterAspect)
					.clip(CinemaDimens.CardShape)
					.cinemaSkeleton(),
			)
		}
	}
}

private const val LOAD_MORE_THRESHOLD = 3
private const val SKELETON_CARDS = CinemaDimens.GridColumns
