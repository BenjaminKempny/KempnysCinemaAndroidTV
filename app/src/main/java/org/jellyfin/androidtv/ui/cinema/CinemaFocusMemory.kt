package org.jellyfin.androidtv.ui.cinema

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/**
 * Remembers which card opened another screen so the focus can return to it on back.
 *
 * Depending on the navigation the previous screen either gets a new view (and with it a
 * fresh Compose focus state) or keeps its composition. In the latter case the Android view
 * only regains the window focus and Compose moves the focus to an arbitrary default
 * target — often not the card that was opened, which also breaks the following D-Pad
 * navigation. The host fragment therefore keeps one [CinemaFocusMemory], provides it with
 * [ProvideCinemaFocusMemory] and calls [startRestore] from `onResume`. Cards mark
 * themselves when clicked ([markOpened]) and take the focus back once they are placed.
 */
@Stable
class CinemaFocusMemory {
	private var openedKey: String? = null
	private val scrollPositions = HashMap<String, Pair<Int, Int>>()

	/** Key of the card that should get the focus back, set while a view is restored. */
	var pendingKey: String? by mutableStateOf(null)
		private set

	/** Used by screens that reset their scroll position when the content changes. */
	internal var scrollResetKey: String? = null

	/** Spotlight slide shown when the screen was left. */
	internal var heroIndex: Int = 0

	val isRestoring: Boolean get() = pendingKey != null

	fun markOpened(key: String) {
		openedKey = key
	}

	/** Starts giving the focus back to the last opened card (no-op if there is none). */
	fun startRestore() {
		val key = openedKey ?: return
		pendingKey = key
	}

	internal fun consume(key: String) {
		if (pendingKey != key) return
		pendingKey = null
		openedKey = null
	}

	internal fun cancelRestore() {
		pendingKey = null
	}

	internal fun scrollPosition(id: String) = scrollPositions[id]

	internal fun saveScrollPosition(id: String, state: LazyListState) {
		scrollPositions[id] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
	}
}

val LocalCinemaFocusMemory = staticCompositionLocalOf<CinemaFocusMemory?> { null }

/** Provides [memory] to the cards and starts restoring the focus of the last opened card. */
@Composable
fun ProvideCinemaFocusMemory(
	memory: CinemaFocusMemory,
	content: @Composable () -> Unit,
) {
	remember(memory) {
		memory.startRestore()
		true
	}

	// Content may be gone (or still loading) — never hold on to the request forever, it
	// would otherwise steal the focus as soon as the item shows up again.
	val pendingKey = memory.pendingKey
	LaunchedEffect(memory, pendingKey) {
		if (pendingKey == null) return@LaunchedEffect
		delay(RESTORE_TIMEOUT_MS)
		memory.cancelRestore()
	}

	CompositionLocalProvider(LocalCinemaFocusMemory provides memory, content = content)
}

/**
 * Focus requester modifier for a card identified by [key]. When the card is the one that
 * opened the previous screen, it takes the focus back as soon as it has been placed.
 *
 * The request is repeated until the card holds the focus for a few frames *and* the
 * hosting Android view owns the window focus. A single request is not enough: when the
 * view regains the focus after the request, Compose moves the focus to its default
 * target again.
 */
@Composable
internal fun cinemaRestoreFocus(key: String?): Modifier {
	val memory = LocalCinemaFocusMemory.current
	if (key == null || memory == null) return Modifier

	val requester = remember { FocusRequester() }
	val view = LocalView.current
	val focusState = remember { RestoreFocusState() }
	if (memory.pendingKey == key) {
		LaunchedEffect(key) {
			// Runs until the card holds the focus or ProvideCinemaFocusMemory gives up
			// (RESTORE_TIMEOUT_MS) — returning from the player activity can take a moment
			// until the window owns the focus again.
			var stableFrames = 0
			while (memory.pendingKey == key) {
				// Wait for the lazy layout to place the card before requesting the focus.
				withFrameNanos { }
				if (memory.pendingKey != key) return@LaunchedEffect

				if (focusState.hasFocus && view.hasFocus()) {
					if (++stableFrames >= RESTORE_STABLE_FRAMES) {
						memory.consume(key)
						return@LaunchedEffect
					}
				} else {
					stableFrames = 0
					if (view.hasWindowFocus()) runCatching { requester.requestFocus() }
				}
			}
		}
	}

	return Modifier
		.focusRequester(requester)
		.onFocusChanged { focusState.hasFocus = it.hasFocus }
}

private class RestoreFocusState {
	var hasFocus = false
}

/** Wraps [action] so the card identified by [key] is remembered before it runs. */
@Composable
internal fun rememberCinemaOpenAction(key: String?, action: () -> Unit): () -> Unit {
	val memory = LocalCinemaFocusMemory.current
	if (key == null || memory == null) return action
	return {
		memory.markOpened(key)
		action()
	}
}

/**
 * [rememberLazyListState] that also survives the view being recreated by the navigation,
 * so the card that should get the focus back is actually composed.
 */
@Composable
internal fun rememberCinemaLazyListState(id: String): LazyListState {
	val memory = LocalCinemaFocusMemory.current
	val saved = remember(memory, id) { memory?.scrollPosition(id) }
	val state = rememberLazyListState(
		initialFirstVisibleItemIndex = saved?.first ?: 0,
		initialFirstVisibleItemScrollOffset = saved?.second ?: 0,
	)

	if (memory != null) {
		DisposableEffect(memory, id, state) {
			onDispose { memory.saveScrollPosition(id, state) }
		}
	}

	return state
}

private const val RESTORE_TIMEOUT_MS = 4_000L
private const val RESTORE_STABLE_FRAMES = 3


