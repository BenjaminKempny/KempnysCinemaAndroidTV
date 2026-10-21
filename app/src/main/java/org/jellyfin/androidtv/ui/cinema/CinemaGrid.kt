package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Renders a poster grid inside a [LazyListScope].
 *
 * The catalog is chunked into plain rows instead of using a nested grid: a
 * `LazyVerticalGrid` cannot live inside a `LazyColumn` and would also break D-Pad focus
 * traversal between rows.
 */
fun LazyListScope.cinemaGrid(
	items: List<BaseItemDto>,
	posterUrl: (BaseItemDto) -> String?,
	onOpenItem: (BaseItemDto) -> Unit,
	keyPrefix: String,
	onItemMenu: ((BaseItemDto) -> Unit)? = null,
	subtitle: (BaseItemDto) -> String? = { it.cinemaSubtitle },
	/** `DPAD_LEFT` on the first column enters the sidebar instead of the scroll container. */
	railFocusRequester: FocusRequester? = null,
	/** Attached to the very first card, e.g. to focus it initially. */
	firstItemFocusRequester: FocusRequester? = null,
) {
	val columns = CinemaDimens.GridColumns
	val rowCount = (items.size + columns - 1) / columns

	items(rowCount, key = { "$keyPrefix-row-$it" }, contentType = { "cinemaGridRow" }) { rowIndex ->
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(CinemaDimens.GridHorizontalGap),
		) {
			val start = rowIndex * columns
			val rowItems = items.subList(start, minOf(start + columns, items.size))
			rowItems.forEachIndexed { columnIndex, item ->
				key(item.id) {
					CinemaPosterCard(
						title = item.cinemaTitle,
						subtitle = subtitle(item),
						imageUrl = posterUrl(item),
						progress = item.cinemaProgress,
						onClick = { onOpenItem(item) },
						onLongClick = onItemMenu?.let { menu -> { menu(item) } },
						width = CinemaDimens.GridCardWidth,
						focusKey = "$keyPrefix-${item.id}",
						modifier = Modifier
							.weight(1f)
							.railOnLeft(railFocusRequester, columnIndex == 0)
							.then(
								if (firstItemFocusRequester != null && start + columnIndex == 0) {
									Modifier.focusRequester(firstItemFocusRequester)
								} else {
									Modifier
								}
							),
					)
				}
			}
			// Reserve unused columns so a partial row has the same poster sizes as a full row.
			repeat(columns - rowItems.size) {
				Spacer(Modifier.weight(1f))
			}
		}
	}
}
