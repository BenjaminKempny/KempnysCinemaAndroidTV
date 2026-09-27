package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Minimal scrolling: a visible button must not pull its section above the TV viewport. */
internal object CinemaBringIntoViewSpec : BringIntoViewSpec

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CinemaLazyColumn(
	modifier: Modifier = Modifier,
	state: LazyListState = rememberLazyListState(),
	contentPadding: PaddingValues = PaddingValues(),
	verticalArrangement: Arrangement.Vertical = Arrangement.Top,
	content: LazyListScope.() -> Unit,
) {
	CompositionLocalProvider(LocalBringIntoViewSpec provides CinemaBringIntoViewSpec) {
		LazyColumn(
			modifier = modifier,
			state = state,
			contentPadding = contentPadding,
			verticalArrangement = verticalArrangement,
			content = content,
		)
	}
}

/**
 * Replace a child's requested bounds with the whole section. There is only one request,
 * not an onFocusChanged scroll job racing Compose's automatic focus scrolling.
 */
internal fun Modifier.cinemaFocusSection(topInset: Dp = 0.dp): Modifier = this.then(CinemaFocusSectionElement(topInset))

private data class CinemaFocusSectionElement(val topInset: Dp) : ModifierNodeElement<CinemaFocusSectionNode>() {
	override fun create() = CinemaFocusSectionNode(topInset)
	override fun update(node: CinemaFocusSectionNode) { node.topInset = topInset }
	override fun InspectorInfo.inspectableProperties() {
		name = "cinemaFocusSection"
	}
}

private class CinemaFocusSectionNode(var topInset: Dp) : Modifier.Node(), BringIntoViewModifierNode {
	override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) {
		if (!childCoordinates.isAttached || boundsProvider() == null) return
		this.bringIntoView {
			if (!isAttached) return@bringIntoView null
			val size = requireLayoutCoordinates().size
			val inset = with(requireDensity()) { topInset.toPx() }
			Rect(0f, -inset, size.width.toFloat(), size.height.toFloat())
		}
	}
}

