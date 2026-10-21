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

/** Minimal scrolling: only scroll as far as needed to bring the focused item into view. */
internal object CinemaBringIntoViewSpec : BringIntoViewSpec

/**
 * Vertical pages: the focused section (or card) is always placed in the vertical centre of
 * the viewport, so it never ends up glued to the top or bottom edge. Sections taller than
 * the viewport are aligned to the top instead so their title stays visible. The list clamps
 * the result at its start / end, so the first and last section simply stop at the edge.
 */
internal object CinemaCenterBringIntoViewSpec : BringIntoViewSpec {
	override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
		if (size >= containerSize) return offset
		return offset - (containerSize - size) / 2f
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CinemaLazyColumn(
	modifier: Modifier = Modifier,
	state: LazyListState = rememberLazyListState(),
	contentPadding: PaddingValues = PaddingValues(),
	verticalArrangement: Arrangement.Vertical = Arrangement.Top,
	content: LazyListScope.() -> Unit,
) {
	CompositionLocalProvider(LocalBringIntoViewSpec provides CinemaCenterBringIntoViewSpec) {
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
 * Horizontal rails inside a [CinemaLazyColumn] keep the minimal scrolling — only the
 * vertical page centres its sections. Wrap every `LazyRow` with this.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CinemaRowScroll(content: @Composable () -> Unit) {
	CompositionLocalProvider(LocalBringIntoViewSpec provides CinemaBringIntoViewSpec, content = content)
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

