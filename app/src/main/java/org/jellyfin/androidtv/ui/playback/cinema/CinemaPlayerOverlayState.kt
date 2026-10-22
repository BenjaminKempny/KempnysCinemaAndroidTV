package org.jellyfin.androidtv.ui.playback.cinema

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/** Which control receives the focus when the controls open. */
enum class CinemaPlayerFocusTarget {
	PlayPause,
	Timeline,
}

/**
 * Visibility state of the cinema player overlay.
 *
 * - **hidden**: only the video is visible.
 * - **intro**: logo and metadata only, shown once when an item starts (cinematic start).
 * - **controls**: logo, metadata, center controls, timeline and the action row.
 */
@Stable
class CinemaPlayerOverlayState {
	var visible by mutableStateOf(false)
		private set

	/** Only the branding (logo + metadata) is shown, no controls. */
	var introOnly by mutableStateOf(false)
		private set

	/** Disabled while a popup or panel of the player is open. */
	var autoHideEnabled by mutableStateOf(true)

	var focusTarget by mutableStateOf(CinemaPlayerFocusTarget.PlayPause)
		private set

	/** Changes on every interaction to restart the auto hide timer. */
	var interaction by mutableIntStateOf(0)
		private set

	/** Changes every time the controls are (re)opened so the focus can be placed again. */
	var openCount by mutableIntStateOf(0)
		private set

	var info by mutableStateOf<CinemaPlayerInfo?>(null)

	/** Called whenever [visible] changes. */
	var onVisibilityChanged: ((Boolean) -> Unit)? = null

	val controlsVisible: Boolean get() = visible && !introOnly

	fun showControls(target: CinemaPlayerFocusTarget = CinemaPlayerFocusTarget.PlayPause) {
		focusTarget = target
		introOnly = false
		openCount++
		updateVisible(true)
		poke()
	}

	fun showIntro() {
		if (visible) return
		introOnly = true
		updateVisible(true)
		poke()
	}

	fun hide() {
		panel = null
		panelParent = null
		cancelScrub()
		updateVisible(false)
	}

	fun poke() {
		interaction++
	}

	/**
	 * Handles a back press inside the overlay: steps out of an open panel (sub panel -> parent
	 * -> controls), cancels a scrub or hides the controls. Returns `false` when nothing was open
	 * so the caller may close the player.
	 *
	 * Used by both the Compose key handler and the fragment's back callback, because with
	 * predictive back (targetSdk 36) the back key never reaches the Compose hierarchy.
	 */
	fun handleBack(): Boolean {
		when {
			panel != null -> closePanel()
			scrubPositionMs != null -> cancelScrub()
			visible -> hide()
			else -> return false
		}
		return true
	}

	// region panels

	/** The glass side panel (audio, subtitles, more …) that is currently open. */
	var panel by mutableStateOf<CinemaPlayerPanel?>(null)
		private set

	/** Panel to return to when the current one is closed (sub panels of "More"). */
	var panelParent by mutableStateOf<CinemaPlayerPanel?>(null)
		private set

	/** Root panel that was opened from the action row, used to restore the focus. */
	var panelOrigin by mutableStateOf<CinemaPlayerPanel?>(null)
		private set

	fun openPanel(target: CinemaPlayerPanel, parent: CinemaPlayerPanel? = null) {
		if (!controlsVisible) showControls()
		if (parent == null) panelOrigin = target
		panelParent = parent
		panel = target
		poke()
	}

	/** Goes back to the parent panel or closes the panel completely. */
	fun closePanel() {
		val parent = panelParent
		panelParent = null
		panel = parent
		poke()
	}

	fun dismissPanel() {
		panelParent = null
		panel = null
		poke()
	}

	// endregion

	// region scrubbing

	/** Target position while the user scrubs on the timeline, not yet sent to the player. */
	var scrubPositionMs by mutableStateOf<Long?>(null)
		private set

	/** Changes on every scrub step so the delayed commit restarts. */
	var scrubToken by mutableIntStateOf(0)
		private set

	/** Position that was committed but is not yet reported by the player. */
	var pendingSeekMs by mutableStateOf<Long?>(null)
		private set
	internal var pendingSeekAt = 0L
		private set

	fun scrub(deltaMs: Long, currentMs: Long, durationMs: Long) {
		if (durationMs <= 0) return
		val from = scrubPositionMs ?: pendingSeekMs ?: currentMs
		scrubPositionMs = (from + deltaMs).coerceIn(0, (durationMs - END_GUARD_MS).coerceAtLeast(0))
		scrubToken++
		poke()
	}

	/** Ends scrubbing and returns the position that should be sent to the player. */
	fun commitScrub(): Long? {
		val target = scrubPositionMs ?: return null
		scrubPositionMs = null
		pendingSeekMs = target
		pendingSeekAt = System.currentTimeMillis()
		return target
	}

	fun cancelScrub() {
		scrubPositionMs = null
	}

	fun clearPendingSeek() {
		pendingSeekMs = null
	}

	// endregion

	private fun updateVisible(value: Boolean) {
		if (visible == value) return
		visible = value
		onVisibilityChanged?.invoke(value)
	}
}

/** Keeps a scrub target away from the very end so the item does not complete by accident. */
private const val END_GUARD_MS = 1_000L

/** Glass panels of the player. [Options] is the root menu behind the "⋯" button. */
enum class CinemaPlayerPanel {
	Options,
	Audio,
	Subtitles,
	More,
	Speed,
	Quality,
	Zoom,
	Info,
}

/** One row of a [CinemaPlayerPanel]. */
@Immutable
data class CinemaPlayerOption(
	val id: Int,
	val label: String,
	val detail: String? = null,
	/** Shown with a check mark. */
	val selected: Boolean = false,
	@DrawableRes val icon: Int? = null,
	/** Opens another panel instead of performing an action. */
	val opens: CinemaPlayerPanel? = null,
	/** Section caption, not focusable. */
	val header: Boolean = false,
)

@Immutable
data class CinemaPlayerPanelContent(
	val title: String,
	val options: List<CinemaPlayerOption>,
)

/** Polled playback values. */
@Immutable
data class CinemaPlaybackSnapshot(
	val playing: Boolean = false,
	val paused: Boolean = false,
	val positionMs: Long = 0,
	val durationMs: Long = 0,
	val bufferedMs: Long = 0,
	val speed: Float = 1f,
	val zoomFit: Boolean = true,
)

/** Everything the overlay shows about the current item. */
@Immutable
data class CinemaPlayerInfo(
	/** Stable key of the item, used to play the cinematic start once per item. */
	val key: String,
	/** Typographic title, only used when there is no logo. */
	val title: String,
	/** First metadata line below the logo (`2006 · FSK 12` / `Staffel 2 · Folge 6`). */
	val metaPrimary: String?,
	/** Optional second metadata line (episode title). */
	val metaSecondary: String?,
	/** `4K`, `HDR10`, `5.1` … shown in the top end corner. */
	val badges: List<String>,
	val logoUrl: String?,
	/** Width / height of the video stream, used to detect letterboxing. */
	val videoAspect: Float?,
)

/**
 * One trickplay thumbnail: the region ([x], [y], [width], [height] in pixels) inside the tile
 * sheet identified by [tileKey].
 */
@Immutable
data class CinemaTrickplayFrame(
	val tileKey: String,
	val x: Int,
	val y: Int,
	val width: Int,
	val height: Int,
)

/** Bridge from the overlay to the legacy playback controller and fragment. */
interface CinemaPlayerHost {
	fun snapshot(): CinemaPlaybackSnapshot
	fun canSeek(): Boolean
	fun rewindSeconds(): Int
	fun forwardSeconds(): Int

	fun playPause()
	fun seekTo(positionMs: Long)

	fun hasAudioChoice(): Boolean
	fun hasSubtitles(): Boolean

	/** Trickplay thumbnail for [positionMs] or null when the server has none. */
	suspend fun trickplayFrame(positionMs: Long): CinemaTrickplayFrame? = null

	/** Loads the tile sheet of a [CinemaTrickplayFrame]. */
	suspend fun loadTrickplayTile(tileKey: String): ImageBitmap? = null

	/** Builds the rows of [panel] or returns null when it is not available. */
	fun panelContent(panel: CinemaPlayerPanel): CinemaPlayerPanelContent?

	/** Applies [option] of [panel]. Returns a follow-up panel or null to close the panel. */
	fun selectOption(panel: CinemaPlayerPanel, option: CinemaPlayerOption): CinemaPlayerPanel?

	fun close()

	fun isSkipPromptVisible(): Boolean
	fun confirmSkip()
	fun dismissSkip()
}


