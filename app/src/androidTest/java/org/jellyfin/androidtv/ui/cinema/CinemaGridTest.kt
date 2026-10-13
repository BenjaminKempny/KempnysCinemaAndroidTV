package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CinemaGridTest {
	@get:Rule
	val compose = createComposeRule()

	@Test
	fun fullAndPartialRowsHaveEqualCardSizes() {
		showGrid()
		val cards = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
		assertEquals(11, cards.size)
		val first = cards.first().boundsInRoot
		cards.forEach { card ->
			// Row weights can differ by one physical pixel due to rounding, never by a card fraction.
			assertEquals(first.width, card.boundsInRoot.width, 1f)
			assertEquals(first.height, card.boundsInRoot.height, 2f)
		}
	}

	@Test
	fun dpadReachesRightmostCardAndPartialRow() {
		showGrid()
		compose.onNodeWithText("Item 0").performSemanticsAction(SemanticsActions.RequestFocus)
		compose.onNodeWithText("Item 0").performKeyInput { repeat(6) { pressKey(Key.DirectionRight) } }
		compose.onNodeWithText("Item 6").assertIsFocused()
		compose.onNodeWithText("Item 6").performKeyInput { repeat(3) { pressKey(Key.DirectionLeft) } }
		compose.onNodeWithText("Item 3").performKeyInput { pressKey(Key.DirectionDown) }
		compose.onNodeWithText("Item 10").assertIsFocused()
	}

	private fun showGrid() {
		val items = (0..10).map {
			BaseItemDto(id = UUID(0, it.toLong()), type = BaseItemKind.MOVIE, name = "Item $it", productionYear = 2000)
		}
		compose.setContent {
			CompositionLocalProvider(LocalCinemaPerformanceMode provides true) {
				JellyfinTheme {
					// Smaller than 7 * GridCardWidth + gaps: previously the rightmost card was squeezed.
					LazyColumn(Modifier.requiredSize(600.dp, 400.dp)) {
						cinemaGrid(items, posterUrl = { null }, onOpenItem = {}, keyPrefix = "test")
					}
				}
			}
		}
	}
}

