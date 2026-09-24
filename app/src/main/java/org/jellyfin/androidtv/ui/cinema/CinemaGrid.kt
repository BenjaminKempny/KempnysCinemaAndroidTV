package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
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
) {
	val rows = items.chunked(CinemaDimens.GridColumns)

	items(rows.size, key = { "$keyPrefix-row-$it" }) { rowIndex ->
		Row(horizontalArrangement = Arrangement.spacedBy(CinemaDimens.GridHorizontalGap)) {
			rows[rowIndex].forEach { item ->
				CinemaPosterCard(
					title = item.cinemaTitle,
					subtitle = subtitle(item),
					imageUrl = posterUrl(item),
					progress = item.cinemaProgress,
					onClick = { onOpenItem(item) },
					onLongClick = onItemMenu?.let { menu -> { menu(item) } },
					width = CinemaDimens.GridCardWidth,
					modifier = Modifier,
				)
			}
		}
	}
}
