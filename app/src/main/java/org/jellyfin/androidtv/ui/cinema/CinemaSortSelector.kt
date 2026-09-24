package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.popover.Popover
import org.jellyfin.androidtv.ui.base.popover.PopoverMenu
import org.jellyfin.androidtv.ui.base.popover.PopoverMenuCheckboxItem

/**
 * Sort control of the catalog toolbar. The web uses a native `<select>`; on TV that is
 * replaced by a D-Pad navigable popover list (spec §2.4).
 */
@Composable
fun CinemaSortSelector(
	sort: CinemaSort,
	onSelect: (CinemaSort) -> Unit,
	modifier: Modifier = Modifier,
) {
	var expanded by remember { mutableStateOf(false) }

	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text(
			text = stringResource(R.string.lbl_sort_by),
			color = CinemaColors.Muted,
			fontSize = CinemaDimens.CardSubtitleSize,
		)

		Box {
			CinemaButton(
				text = stringResource(sort.labelRes),
				icon = painterResource(R.drawable.ic_sort),
				onClick = { expanded = true },
			)

			Popover(
				expanded = expanded,
				onDismissRequest = { expanded = false },
				backgroundColor = CinemaColors.Surface,
				modifier = Modifier.width(260.dp),
			) {
				PopoverMenu {
					CinemaSort.entries.forEach { option ->
						PopoverMenuCheckboxItem(
							selected = option == sort,
							onClick = {
								expanded = false
								onSelect(option)
							},
						) {
							Text(stringResource(option.labelRes))
						}
					}
				}
			}
		}
	}
}

private val CinemaSort.labelRes: Int
	get() = when (this) {
		CinemaSort.Name -> R.string.cinema_sort_name
		CinemaSort.Added -> R.string.cinema_sort_added
		CinemaSort.Year -> R.string.cinema_sort_year
	}
