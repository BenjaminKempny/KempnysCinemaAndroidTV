package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.search.SearchResultGroup
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Cinema styled search (spec §2.6): a single input at the top, results as one rail per
 * result group. Replaces the leanback rows based search screen.
 */
@Composable
fun CinemaSearchScreen(
	query: String,
	results: Collection<SearchResultGroup>,
	onQueryChange: (String) -> Unit,
	onQuerySubmit: () -> Unit,
	onOpenItem: (BaseItemDto) -> Unit,
	posterUrl: (BaseItemDto) -> CinemaArtwork?,
	modifier: Modifier = Modifier,
) {
	val inputFocusRequester = remember { FocusRequester() }
	val groups = results.filter { it.items.isNotEmpty() }

	LaunchedEffect(Unit) { runCatching { inputFocusRequester.requestFocus() } }

	CinemaBackground(modifier) {
		Column(Modifier.fillMaxSize()) {
			CinemaSearchInput(
				query = query,
				onQueryChange = onQueryChange,
				onQuerySubmit = onQuerySubmit,
				modifier = Modifier
					.padding(
						start = CinemaDimens.Overscan,
						end = CinemaDimens.Overscan,
						top = CinemaDimens.Overscan,
						bottom = 20.dp,
					)
					.fillMaxWidth()
					.focusRequester(inputFocusRequester),
			)

			if (groups.isEmpty()) {
				Text(
					text = stringResource(
						if (query.isBlank()) R.string.cinema_search_hint else R.string.cinema_empty
					),
					color = CinemaColors.Muted,
					fontSize = CinemaDimens.BodySize,
					modifier = Modifier.padding(horizontal = CinemaDimens.Overscan),
				)
				return@Column
			}

			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.focusRestorer(),
				contentPadding = PaddingValues(
					start = CinemaDimens.Overscan,
					end = CinemaDimens.Overscan,
					bottom = CinemaDimens.Overscan,
				),
				verticalArrangement = Arrangement.spacedBy(22.dp),
			) {
				items(groups, key = { it.labelRes }) { group ->
					Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
						CinemaSectionTitle(stringResource(group.labelRes))

						LazyRow(
							modifier = Modifier
								.fillMaxWidth()
								.focusRestorer(),
							horizontalArrangement = Arrangement.spacedBy(CinemaDimens.RowGap),
						) {
							items(group.items.toList(), key = { it.id }) { item ->
								CinemaPosterCard(
									title = item.cinemaTitle,
									subtitle = item.cinemaSubtitle,
									imageUrl = posterUrl(item),
									progress = item.cinemaProgress,
									onClick = { onOpenItem(item) },
									width = CinemaDimens.RowCardWidth,
								)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun CinemaSearchInput(
	query: String,
	onQueryChange: (String) -> Unit,
	onQuerySubmit: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }

	BasicTextField(
		value = query,
		onValueChange = onQueryChange,
		singleLine = true,
		modifier = modifier
			.height(CinemaDimens.ButtonHeight + 8.dp)
			.onFocusChanged { focused = it.isFocused },
		keyboardActions = KeyboardActions { onQuerySubmit() },
		keyboardOptions = KeyboardOptions.Default.copy(
			keyboardType = KeyboardType.Text,
			imeAction = ImeAction.Search,
			showKeyboardOnFocus = true,
		),
		textStyle = TextStyle(
			color = CinemaColors.Text,
			fontSize = CinemaDimens.BodySize,
		),
		cursorBrush = SolidColor(CinemaColors.Accent),
		decorationBox = { innerTextField ->
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxSize()
					.clip(CinemaDimens.PillShape)
					.background(if (focused) CinemaColors.ButtonFocused else CinemaColors.Button)
					.border(
						width = 1.dp,
						color = if (focused) CinemaColors.Accent else CinemaColors.Border,
						shape = CinemaDimens.PillShape,
					)
					.padding(horizontal = 20.dp),
			) {
				Icon(
					painter = painterResource(R.drawable.ic_search),
					contentDescription = null,
					tint = if (focused) CinemaColors.AccentText else CinemaColors.Muted,
					modifier = Modifier.size(22.dp),
				)
				Spacer(Modifier.width(14.dp))

				Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
					if (query.isEmpty()) {
						Text(
							text = stringResource(R.string.lbl_search),
							color = CinemaColors.Muted,
							fontSize = CinemaDimens.BodySize,
						)
					}
					innerTextField()
				}
			}
		},
	)
}
