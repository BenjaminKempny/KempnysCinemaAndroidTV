package org.jellyfin.androidtv.ui.playback

import android.content.Context
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.getQualityProfiles
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.constant.ZoomMode
import org.jellyfin.androidtv.ui.playback.cinema.CinemaPlaybackSnapshot
import org.jellyfin.androidtv.ui.playback.cinema.CinemaPlayerHost
import org.jellyfin.androidtv.ui.playback.cinema.CinemaPlayerInfo
import org.jellyfin.androidtv.ui.playback.cinema.CinemaPlayerOption
import org.jellyfin.androidtv.ui.playback.cinema.CinemaPlayerOverlay
import org.jellyfin.androidtv.ui.playback.cinema.CinemaPlayerOverlayState
import org.jellyfin.androidtv.ui.playback.cinema.CinemaPlayerPanel
import org.jellyfin.androidtv.ui.playback.cinema.CinemaPlayerPanelContent
import org.jellyfin.androidtv.ui.playback.cinema.CinemaTrickplayFrame
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.TrickplayInfoDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private const val LOGO_REQUEST_WIDTH = 1200

/** Whether the cinema overlay replaces the leanback controls for [items] (not for live TV). */
fun usesCinemaPlayerOverlay(items: List<BaseItemDto>?): Boolean =
	!items.isNullOrEmpty() && items.first().type != BaseItemKind.TV_CHANNEL

/**
 * Replaces the leanback transport controls and the old top panel with the cinema overlay.
 * The leanback fragment stays attached because its glue still owns the popup actions.
 */
fun CustomPlaybackOverlayFragment.setupCinemaOverlay(): CinemaPlayerOverlayState {
	val state = CinemaPlayerOverlayState()
	state.onVisibilityChanged = { visible -> onCinemaOverlayVisibilityChanged(visible) }

	binding.leanbackFragment.visibility = View.GONE
	binding.topPanel.visibility = View.GONE

	val host = CinemaPlayerHostImpl(this)
	binding.cinemaOverlay.apply {
		visibility = View.VISIBLE
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent { CinemaPlayerOverlay(state = state, host = host) }
		requestFocus()
	}

	return state
}

/** Gives the focus back to the overlay, e.g. after the chapter panel closed. */
fun CustomPlaybackOverlayFragment.focusCinemaOverlay() {
	binding?.cinemaOverlay?.requestFocus()
}

fun CustomPlaybackOverlayFragment.updateCinemaOverlay(state: CinemaPlayerOverlayState, item: BaseItemDto) {
	val controller = PlaybackControllerHolder.controller ?: return
	val imageHelper = PlaybackControllerHolder.imageHelper
	state.info = buildCinemaPlayerInfo(
		context = requireContext(),
		item = item,
		mediaSource = runCatching { controller.currentMediaSource }.getOrNull(),
		audioIndex = runCatching { controller.audioStreamIndex }.getOrDefault(-1),
		logoUrl = imageHelper.getLogoImageUrl(item, LOGO_REQUEST_WIDTH),
	)
}

private object PlaybackControllerHolder : KoinComponent {
	private val container by inject<PlaybackControllerContainer>()
	val imageHelper by inject<ImageHelper>()
	val controller: PlaybackController? get() = container.playbackController
}

private class CinemaPlayerHostImpl(
	private val fragment: CustomPlaybackOverlayFragment,
) : CinemaPlayerHost, KoinComponent {
	private val container by inject<PlaybackControllerContainer>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userPreferences by inject<UserPreferences>()
	private val itemMutationRepository by inject<ItemMutationRepository>()
	private val api by inject<ApiClient>()
	private val imageLoader by inject<ImageLoader>()

	/** Favorite changes made in the player, the item DTO itself is immutable. */
	private val favoriteOverrides = mutableMapOf<UUID, Boolean>()

	private val controller: PlaybackController? get() = container.playbackController
	private val glue: CustomPlaybackTransportControlGlue? get() = fragment.leanbackOverlayFragment?.playerGlue
	private val anchor: View? get() = fragment.binding?.cinemaPopupAnchor

	override fun snapshot(): CinemaPlaybackSnapshot {
		val controller = controller ?: return CinemaPlaybackSnapshot()
		return runCatching {
			CinemaPlaybackSnapshot(
				playing = controller.isPlaying,
				paused = controller.isPaused,
				positionMs = controller.currentPosition,
				durationMs = controller.duration,
				bufferedMs = controller.bufferedPosition,
				speed = controller.playbackSpeed,
				zoomFit = controller.zoomMode == ZoomMode.FIT,
			)
		}.getOrElse { CinemaPlaybackSnapshot() }
	}

	override fun canSeek(): Boolean = controller?.canSeek() == true
	override fun rewindSeconds(): Int = userSettingPreferences[UserSettingPreferences.skipBackLength] / 1000
	override fun forwardSeconds(): Int = userSettingPreferences[UserSettingPreferences.skipForwardLength] / 1000

	override fun playPause() {
		controller?.playPause()
	}

	override fun seekTo(positionMs: Long) {
		controller?.seek(positionMs)
	}

	override fun hasAudioChoice(): Boolean = runCatching { glue?.cinemaHasMultiAudio() == true }.getOrDefault(false)
	override fun hasSubtitles(): Boolean = runCatching { glue?.cinemaHasSubtitles() == true }.getOrDefault(false)

	// region trickplay

	private val trickplayTiles = object : LinkedHashMap<String, ImageBitmap>(TRICKPLAY_CACHE_SIZE, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > TRICKPLAY_CACHE_SIZE
	}

	override suspend fun trickplayFrame(positionMs: Long): CinemaTrickplayFrame? {
		val controller = controller ?: return null
		val item = runCatching { controller.currentlyPlayingItem }.getOrNull() ?: return null
		val mediaSource = runCatching { controller.currentMediaSource }.getOrNull()
		val manifest = trickplayFor(item) ?: return null
		// Smallest resolution is plenty for a preview and the fastest to load.
		val (sourceKey, info) = selectTrickplayInfo(manifest, mediaSource?.id, item.id) ?: return null
		if (info.interval <= 0 || info.tileWidth <= 0 || info.tileHeight <= 0 || info.width <= 0 || info.height <= 0) return null

		return runCatching {
			val thumbnail = (positionMs.coerceAtLeast(0) / info.interval).toInt()
				.coerceAtMost((info.thumbnailCount - 1).coerceAtLeast(0))
			val perTile = info.tileWidth * info.tileHeight
			val offset = thumbnail % perTile
			val url = api.trickplayApi.getTrickplayTileImageUrl(
				itemId = item.id,
				width = info.width,
				index = thumbnail / perTile,
				mediaSourceId = sourceKey.toUUIDOrNull(),
			)
			CinemaTrickplayFrame(
				tileKey = url,
				x = (offset % info.tileWidth) * info.width,
				y = (offset / info.tileWidth) * info.height,
				width = info.width,
				height = info.height,
			)
		}.onFailure { Timber.w(it, "Trickplay: unable to build the tile url") }.getOrNull()
	}

	/**
	 * Picks the manifest of the playing media source. The keys are media source ids which the
	 * server may format with or without dashes; falls back to the item id or the only entry.
	 */
	private fun selectTrickplayInfo(
		manifest: Map<String, Map<String, TrickplayInfoDto>>,
		mediaSourceId: String?,
		itemId: UUID,
	): Pair<String, TrickplayInfoDto>? {
		fun normalize(id: String) = id.replace("-", "").lowercase(Locale.ROOT)
		val wanted = listOfNotNull(mediaSourceId, itemId.toString()).map(::normalize)
		val entry = wanted.firstNotNullOfOrNull { id -> manifest.entries.firstOrNull { normalize(it.key) == id } }
			?: manifest.entries.singleOrNull()
			?: manifest.entries.firstOrNull()
			?: return null
		val info = entry.value.values.filter { it.width > 0 }.minByOrNull { it.width } ?: return null
		return entry.key to info
	}

	/**
	 * Trickplay manifest of [item]. Items opened from the light weight home rows come without
	 * the `Trickplay` field, so the full item is fetched once per item.
	 */
	private suspend fun trickplayFor(item: BaseItemDto): Map<String, Map<String, TrickplayInfoDto>>? {
		item.trickplay?.takeIf { it.isNotEmpty() }?.let { return it }
		if (trickplayLookups.containsKey(item.id)) return trickplayLookups[item.id]
		return trickplayMutex.withLock {
			if (trickplayLookups.containsKey(item.id)) return@withLock trickplayLookups[item.id]
			val result = runCatching {
				withContext(Dispatchers.IO) { api.userLibraryApi.getItem(itemId = item.id).content.trickplay }
			}
				.onSuccess { trickplay ->
					if (trickplay.isNullOrEmpty()) Timber.i("Trickplay: no thumbnails on the server for \"%s\" (%s)", item.name, item.id)
					else Timber.i("Trickplay: thumbnails available for \"%s\": %s", item.name, trickplay.mapValues { it.value.keys })
				}
				.onFailure { Timber.w(it, "Trickplay: unable to load the trickplay info of %s", item.id) }
			// Only cache definitive answers so a network hiccup does not disable trickplay for good.
			if (result.isSuccess) trickplayLookups[item.id] = result.getOrNull()
			result.getOrNull()
		}
	}

	private val trickplayMutex = Mutex()
	private val trickplayLookups = mutableMapOf<UUID, Map<String, Map<String, TrickplayInfoDto>>?>()

	override suspend fun loadTrickplayTile(tileKey: String): ImageBitmap? {
		trickplayTiles[tileKey]?.let { return it }
		val context = fragment.context ?: return null
		val request = ImageRequest.Builder(context)
			.data(tileKey)
			.size(Size.ORIGINAL)
			.maxBitmapSize(Size(Dimension.Undefined, Dimension.Undefined))
			.allowHardware(false)
			.httpHeaders(
				NetworkHeaders.Builder()
					.set(
						"Authorization",
						AuthorizationHeaderBuilder.buildHeader(
							api.clientInfo.name,
							api.clientInfo.version,
							api.deviceInfo.id,
							api.deviceInfo.name,
							api.accessToken,
						),
					)
					.build()
			)
			.build()
		val result = imageLoader.execute(request)
		if (result !is SuccessResult) {
			Timber.w((result as? ErrorResult)?.throwable, "Trickplay: unable to load tile %s", tileKey)
			return null
		}
		val bitmap = runCatching { result.image.toBitmap().asImageBitmap() }.getOrNull() ?: return null
		trickplayTiles[tileKey] = bitmap
		return bitmap
	}

	// endregion


	private fun perform(action: Int) {
		val glue = glue ?: return
		val anchor = anchor ?: return
		runCatching { glue.performCinemaAction(action, anchor) }
			.onFailure { Timber.w(it, "Unable to perform cinema player action %d", action) }
	}

	override fun panelContent(panel: CinemaPlayerPanel): CinemaPlayerPanelContent? {
		val controller = controller ?: return null
		val context = fragment.context ?: return null
		return runCatching {
			when (panel) {
				CinemaPlayerPanel.Options -> optionsPanel(context, controller)
				CinemaPlayerPanel.Audio -> audioPanel(context, controller)
				CinemaPlayerPanel.Subtitles -> subtitlePanel(context, controller)
				CinemaPlayerPanel.More -> morePanel(context, controller)
				CinemaPlayerPanel.Speed -> speedPanel(context, controller)
				CinemaPlayerPanel.Quality -> qualityPanel(context)
				CinemaPlayerPanel.Zoom -> zoomPanel(context, controller)
				CinemaPlayerPanel.Info -> CinemaPlayerPanelContent(
					title = context.getString(R.string.playback_info),
					options = playbackInfoRows(context, controller),
				)
			}
		}.onFailure { Timber.w(it, "Unable to build cinema player panel %s", panel) }.getOrNull()
	}

	override fun selectOption(panel: CinemaPlayerPanel, option: CinemaPlayerOption): CinemaPlayerPanel? {
		val controller = controller ?: return null
		runCatching {
			when (panel) {
				CinemaPlayerPanel.Audio -> controller.switchAudioStream(option.id)
				CinemaPlayerPanel.Subtitles -> controller.setSubtitleIndex(option.id)
				CinemaPlayerPanel.Speed -> VideoSpeedController(controller).currentSpeed = VideoSpeedController.SpeedSteps.entries[option.id]
				CinemaPlayerPanel.Quality -> {
					val key = fragment.context?.let { getQualityProfiles(it).keys.elementAtOrNull(option.id) }
					if (key != null && key != userPreferences[UserPreferences.maxBitrate]) {
						userPreferences[UserPreferences.maxBitrate] = key
						controller.refreshStream()
					}
				}

				CinemaPlayerPanel.Zoom -> controller.setZoom(ZoomMode.entries[option.id])
				CinemaPlayerPanel.More -> when (option.id) {
					MORE_CHAPTERS -> perform(CustomPlaybackTransportControlGlue.CINEMA_ACTION_CHAPTERS)
					MORE_PREVIOUS -> controller.prev()
					MORE_NEXT -> controller.next()
					MORE_FAVORITE -> controller.currentlyPlayingItem?.let { item ->
						val favorite = favoriteOverrides[item.id] ?: (item.userData?.isFavorite == true)
						toggleFavorite(item.id, !favorite)
					}
				}

				// Read only.
				CinemaPlayerPanel.Info -> return panel
				// Only rows that open sub panels.
				CinemaPlayerPanel.Options -> return panel
			}
		}.onFailure { Timber.w(it, "Unable to apply cinema player option %s of %s", option.id, panel) }
		return null
	}

	private fun audioPanel(context: Context, controller: PlaybackController): CinemaPlayerPanelContent? {
		val tracks = controller.currentStreamInfo?.selectableAudioStreams ?: return null
		val current = controller.audioStreamIndex
		return CinemaPlayerPanelContent(
			title = context.getString(R.string.cinema_player_audio),
			options = tracks.map { track ->
				CinemaPlayerOption(
					id = track.index,
					label = track.displayTitle ?: track.language ?: track.index.toString(),
					selected = track.index == current,
				)
			},
		)
	}

	private fun subtitlePanel(context: Context, controller: PlaybackController): CinemaPlayerPanelContent {
		val current = controller.subtitleStreamIndex
		val subtitles = controller.currentMediaSource?.mediaStreams.orEmpty()
			.filter { it.type == MediaStreamType.SUBTITLE }
		return CinemaPlayerPanelContent(
			title = context.getString(R.string.cinema_player_subtitles),
			options = buildList {
				add(CinemaPlayerOption(id = -1, label = context.getString(R.string.lbl_none), selected = current == -1))
				subtitles.forEach { sub ->
					add(
						CinemaPlayerOption(
							id = sub.index,
							label = sub.displayTitle ?: sub.language ?: sub.index.toString(),
							detail = if (sub.isExternal) sub.title?.takeIf { it != sub.displayTitle } else null,
							selected = sub.index == current,
						)
					)
				}
			},
		)
	}

	/** Root menu behind the "⋯" button: audio, subtitles, quality, speed and further settings. */
	private fun optionsPanel(context: Context, controller: PlaybackController): CinemaPlayerPanelContent {
		val audioTracks = runCatching { controller.currentStreamInfo?.selectableAudioStreams }.getOrNull().orEmpty()
		val currentAudio = audioTracks.firstOrNull { it.index == controller.audioStreamIndex }
		val subtitleIndex = controller.subtitleStreamIndex
		val currentSubtitle = controller.currentMediaSource?.mediaStreams.orEmpty()
			.firstOrNull { it.type == MediaStreamType.SUBTITLE && it.index == subtitleIndex }
		val speed = controller.playbackSpeed
		val speedLabel = if (abs(speed - 1f) < 0.01f) context.getString(R.string.cinema_player_speed_normal, formatSpeed(speed, decimals = true))
		else formatSpeed(speed)

		return CinemaPlayerPanelContent(
			title = context.getString(R.string.cinema_player_options),
			options = buildList {
				if (audioTracks.isNotEmpty()) {
					add(
						CinemaPlayerOption(
							OPTIONS_AUDIO,
							context.getString(R.string.cinema_player_audio),
							detail = currentAudio?.let { it.displayTitle ?: it.language },
							icon = R.drawable.ic_select_audio,
							opens = CinemaPlayerPanel.Audio,
						)
					)
				}
				add(
					CinemaPlayerOption(
						OPTIONS_SUBTITLES,
						context.getString(R.string.cinema_player_subtitles),
						detail = currentSubtitle?.let { it.displayTitle ?: it.language } ?: context.getString(R.string.cinema_player_off),
						icon = R.drawable.ic_subtitles,
						opens = CinemaPlayerPanel.Subtitles,
					)
				)
				add(
					CinemaPlayerOption(
						OPTIONS_QUALITY,
						context.getString(R.string.cinema_player_quality),
						detail = getQualityProfiles(context)[userPreferences[UserPreferences.maxBitrate]],
						icon = R.drawable.ic_select_quality,
						opens = CinemaPlayerPanel.Quality,
					)
				)
				add(
					CinemaPlayerOption(
						OPTIONS_SPEED,
						context.getString(R.string.lbl_playback_speed),
						detail = speedLabel,
						icon = R.drawable.ic_playback_speed,
						opens = CinemaPlayerPanel.Speed,
					)
				)
				add(
					CinemaPlayerOption(
						OPTIONS_MORE,
						context.getString(R.string.cinema_player_settings),
						detail = context.getString(R.string.cinema_player_settings_detail),
						icon = R.drawable.ic_settings,
						opens = CinemaPlayerPanel.More,
					)
				)
			},
		)
	}

	private fun morePanel(context: Context, controller: PlaybackController): CinemaPlayerPanelContent {
		val item = controller.currentlyPlayingItem
		val hasChapters = runCatching { glue?.cinemaHasChapters() == true }.getOrDefault(false)
		val favorite = item?.let { favoriteOverrides[it.id] ?: (it.userData?.isFavorite == true) } ?: false

		return CinemaPlayerPanelContent(
			title = context.getString(R.string.cinema_player_settings),
			options = buildList {
				if (hasChapters) add(CinemaPlayerOption(MORE_CHAPTERS, context.getString(R.string.lbl_chapters), icon = R.drawable.ic_select_chapter))
				if (controller.hasPreviousItem()) add(CinemaPlayerOption(MORE_PREVIOUS, context.getString(R.string.lbl_prev_item), icon = R.drawable.ic_previous))
				if (controller.hasNextItem()) add(CinemaPlayerOption(MORE_NEXT, context.getString(R.string.lbl_next_item), icon = R.drawable.ic_next))

				add(
					CinemaPlayerOption(
						MORE_ZOOM,
						context.getString(R.string.lbl_zoom),
						detail = context.getString(controller.zoomMode.nameRes),
						icon = R.drawable.ic_aspect_ratio,
						opens = CinemaPlayerPanel.Zoom,
					)
				)
				if (item != null) {
					add(
						CinemaPlayerOption(
							MORE_FAVORITE,
							context.getString(if (favorite) R.string.lbl_remove_favorite else R.string.lbl_add_favorite),
							icon = if (favorite) R.drawable.ic_heart_red else R.drawable.ic_heart,
						)
					)
				}
				add(CinemaPlayerOption(MORE_INFO, context.getString(R.string.playback_info), icon = R.drawable.ic_info, opens = CinemaPlayerPanel.Info))
			},
		)
	}

	private fun speedPanel(context: Context, controller: PlaybackController): CinemaPlayerPanelContent {
		val current = controller.playbackSpeed
		return CinemaPlayerPanelContent(
			title = context.getString(R.string.lbl_playback_speed),
			options = VideoSpeedController.SpeedSteps.entries.mapIndexed { index, step ->
				CinemaPlayerOption(
					id = index,
					label = formatSpeed(step.speed),
					selected = abs(step.speed - current) < 0.01f,
				)
			},
		)
	}

	private fun qualityPanel(context: Context): CinemaPlayerPanelContent {
		val current = userPreferences[UserPreferences.maxBitrate]
		return CinemaPlayerPanelContent(
			title = context.getString(R.string.lbl_quality_profile),
			options = getQualityProfiles(context).entries.mapIndexed { index, (key, label) ->
				CinemaPlayerOption(id = index, label = label, selected = key == current)
			},
		)
	}

	private fun zoomPanel(context: Context, controller: PlaybackController): CinemaPlayerPanelContent {
		val current = controller.zoomMode
		return CinemaPlayerPanelContent(
			title = context.getString(R.string.lbl_zoom),
			options = ZoomMode.entries.map { mode ->
				CinemaPlayerOption(id = mode.ordinal, label = context.getString(mode.nameRes), selected = mode == current)
			},
		)
	}

	private fun toggleFavorite(itemId: UUID, favorite: Boolean) {
		fragment.viewLifecycleOwner.lifecycleScope.launch {
			runCatching { itemMutationRepository.setFavorite(itemId, favorite) }
				.onSuccess { favoriteOverrides[itemId] = it.isFavorite }
				.onFailure { Timber.w(it, "Unable to update favorite state") }
		}
	}

	override fun close() {
		fragment.closePlayer()
	}

	override fun isSkipPromptVisible(): Boolean = fragment.binding?.skipOverlay?.visible == true

	override fun confirmSkip() {
		val target = fragment.binding?.skipOverlay?.targetPositionMs ?: return
		controller?.seek(target, true)
		fragment.clearSkipOverlay()
	}

	override fun dismissSkip() {
		fragment.clearSkipOverlay()
	}

	private companion object {
		const val MORE_CHAPTERS = 1
		const val MORE_PREVIOUS = 2
		const val MORE_NEXT = 3
		const val MORE_ZOOM = 6
		const val MORE_FAVORITE = 7
		const val MORE_INFO = 8

		const val OPTIONS_AUDIO = 1
		const val OPTIONS_SUBTITLES = 2
		const val OPTIONS_QUALITY = 3
		const val OPTIONS_SPEED = 4
		const val OPTIONS_MORE = 5

		/** Decoded tile sheets kept in memory while scrubbing. */
		const val TRICKPLAY_CACHE_SIZE = 4
	}
}

/** Technical details live in "Mehr" › "Wiedergabeinformationen", not below the logo. */
private fun playbackInfoRows(context: Context, controller: PlaybackController): List<CinemaPlayerOption> {
	val streamInfo = controller.currentStreamInfo
	val mediaSource = controller.currentMediaSource
	val streams = mediaSource?.mediaStreams.orEmpty()
	val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
	val audio = selectedAudioStream(streams, controller.audioStreamIndex)

	var id = 0
	return buildList {
		fun row(text: String) = add(CinemaPlayerOption(id = id++, label = text))
		fun header(text: String) = add(CinemaPlayerOption(id = id++, label = text, header = true))

		val playMethod = when (streamInfo?.playMethod) {
			PlayMethod.DIRECT_PLAY -> context.getString(R.string.playback_info_direct_play)
			PlayMethod.DIRECT_STREAM -> context.getString(R.string.playback_info_direct_stream)
			PlayMethod.TRANSCODE -> context.getString(R.string.playback_info_transcoding)
			else -> null
		}
		playMethod?.let { row(context.getString(R.string.playback_info_play_method, it)) }
		(streamInfo?.container ?: mediaSource?.container)?.let { row(context.getString(R.string.playback_info_container, it)) }

		if (video != null) {
			header(context.getString(R.string.playback_info_video_title))
			video.codec?.let { row(context.getString(R.string.playback_info_codec, it.uppercase(Locale.ROOT))) }
			if (video.width != null && video.height != null) row(context.getString(R.string.playback_info_resolution, video.width, video.height))
			video.bitRate?.let { row(context.getString(R.string.playback_info_bitrate, formatBitrate(it))) }
			hdrLabel(video)?.let { row(it) }
		}

		if (audio != null) {
			header(context.getString(R.string.playback_info_audio_title))
			audio.displayTitle?.let { row(it) }
			audio.codec?.let { row(context.getString(R.string.playback_info_codec, it.uppercase(Locale.ROOT))) }
			audio.bitRate?.let { row(context.getString(R.string.playback_info_bitrate, formatBitrate(it))) }
		}
	}
}

/** `1×`, `1,25×` … or with [decimals] always one decimal (`1,0×`). */
private fun formatSpeed(speed: Float, decimals: Boolean = false): String {
	val text = if (decimals) String.format(Locale.getDefault(), "%.1f", speed)
	else String.format(Locale.getDefault(), "%.2f", speed).trimEnd('0').trimEnd('.', ',')
	return "$text×"
}

private fun formatBitrate(bitsPerSecond: Int): String =
	if (bitsPerSecond >= 1_000_000) String.format(Locale.getDefault(), "%.1f Mbit/s", bitsPerSecond / 1_000_000f)
	else String.format(Locale.getDefault(), "%d kbit/s", bitsPerSecond / 1_000)

// region info

internal fun buildCinemaPlayerInfo(
	context: Context,
	item: BaseItemDto,
	mediaSource: MediaSourceInfo?,
	audioIndex: Int,
	logoUrl: String?,
): CinemaPlayerInfo {
	val isEpisode = item.type == BaseItemKind.EPISODE
	val name = item.name.orEmpty()

	val episodeLine = when {
		!isEpisode -> null
		item.parentIndexNumber != null && item.indexNumber != null ->
			context.getString(R.string.cinema_player_season_episode, item.parentIndexNumber, item.indexNumber)

		item.indexNumber != null -> context.getString(R.string.cinema_player_episode, item.indexNumber)
		else -> null
	}

	val title = if (isEpisode) item.seriesName ?: name else name
	val metaPrimary: String?
	val metaSecondary: String?
	if (isEpisode) {
		metaPrimary = episodeLine ?: name.takeIf { it.isNotBlank() }
		metaSecondary = if (episodeLine != null) name.takeIf { it.isNotBlank() } else null
	} else {
		metaPrimary = listOfNotNull(
			item.productionYear?.toString(),
			item.officialRating?.takeIf { it.isNotBlank() }?.let(::formatRating),
		).joinToString(" · ").takeIf { it.isNotBlank() }
		metaSecondary = null
	}

	val streams = mediaSource?.mediaStreams.orEmpty()
	val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
	val audio = selectedAudioStream(streams, audioIndex)
	val badges = listOfNotNull(
		video?.let(::resolutionLabel),
		video?.let(::hdrLabel),
		audio?.let(::audioLabel),
	)

	val videoAspect = video?.let {
		val width = it.width
		val height = it.height
		if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else null
	}

	return CinemaPlayerInfo(
		key = item.id.toString(),
		title = title,
		metaPrimary = metaPrimary,
		metaSecondary = metaSecondary,
		badges = badges,
		logoUrl = logoUrl,
		videoAspect = videoAspect,
	)
}


/** `DE-12` / `FSK-12` → `FSK 12`, everything else as delivered by the server. */
private fun formatRating(rating: String): String {
	val match = Regex("^(?:DE|FSK)[-\\s]?(\\d+)$", RegexOption.IGNORE_CASE).find(rating.trim())
	return if (match != null) "FSK ${match.groupValues[1]}" else rating
}

private fun selectedAudioStream(streams: List<MediaStream>, audioIndex: Int): MediaStream? {
	val audioStreams = streams.filter { it.type == MediaStreamType.AUDIO }
	return audioStreams.firstOrNull { it.index == audioIndex }
		?: audioStreams.firstOrNull { it.isDefault }
		?: audioStreams.firstOrNull()
}

private fun resolutionLabel(video: MediaStream): String? {
	val width = video.width ?: 0
	val height = video.height ?: 0
	return when {
		width >= 3200 || height >= 2000 -> "4K"
		width >= 1900 || height >= 1000 -> "1080p"
		width >= 1200 || height >= 700 -> "720p"
		else -> null
	}
}

private fun hdrLabel(video: MediaStream): String? {
	val rangeType = video.videoRangeType.name.replace("_", "").lowercase(Locale.ROOT)
	return when {
		rangeType.startsWith("dovi") -> "Dolby Vision"
		rangeType.startsWith("hdr10plus") -> "HDR10+"
		rangeType.startsWith("hdr10") -> "HDR10"
		rangeType.startsWith("hlg") -> "HLG"
		video.videoRange.name.equals("HDR", ignoreCase = true) -> "HDR"
		else -> null
	}
}

private fun audioLabel(audio: MediaStream): String? {
	val codec = audio.codec?.lowercase(Locale.ROOT).orEmpty()
	val profile = audio.profile.orEmpty() + " " + audio.displayTitle.orEmpty()
	val format = when {
		profile.contains("atmos", ignoreCase = true) -> "Dolby Atmos"
		codec == "truehd" -> "Dolby TrueHD"
		codec == "eac3" -> "Dolby Digital+"
		codec == "ac3" -> "Dolby Digital"
		codec == "dts" && profile.contains("MA", ignoreCase = false) -> "DTS-HD MA"
		codec.startsWith("dts") -> "DTS"
		else -> null
	}
	val channels = when (audio.channels) {
		null -> null
		1 -> "Mono"
		2 -> "Stereo"
		6 -> "5.1"
		8 -> "7.1"
		else -> null
	}
	return listOfNotNull(format, channels).joinToString(" ").takeIf { it.isNotBlank() }
}

// endregion


