package org.jellyfin.androidtv.ui.cinema

import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/** Poster / cover artwork: primary -> series or parent primary -> thumb -> backdrop. */
val BaseItemDto.cinemaPosterImage: JellyfinImage?
	get() = itemImages[ImageType.PRIMARY]
		?: seriesPrimaryImage
		?: parentImages[ImageType.PRIMARY]
		?: itemImages[ImageType.THUMB]
		?: parentImages[ImageType.THUMB]
		?: seriesThumbImage
		?: itemBackdropImages.firstOrNull()
		?: parentBackdropImages.firstOrNull()

/** Landscape artwork: parent thumb -> item thumb or backdrop -> poster. */
val BaseItemDto.cinemaThumbImage: JellyfinImage?
	get() = parentImages[ImageType.THUMB]
		?: seriesThumbImage
		?: itemImages[ImageType.THUMB]
		?: itemBackdropImages.firstOrNull()
		?: parentBackdropImages.firstOrNull()
		?: cinemaPosterImage

/** Full bleed artwork behind the spotlight and the detail hero. */
val BaseItemDto.cinemaBackdropImage: JellyfinImage?
	get() = itemBackdropImages.firstOrNull()
		?: parentBackdropImages.firstOrNull()
		?: itemImages[ImageType.THUMB]
		?: parentImages[ImageType.THUMB]
		?: cinemaPosterImage

fun BaseItemDto.cinemaPosterUrl(api: ApiClient, width: Int): String? =
	cinemaPosterImage?.getUrl(api, fillWidth = width)

fun BaseItemDto.cinemaThumbUrl(api: ApiClient, width: Int): String? =
	cinemaThumbImage?.getUrl(api, fillWidth = width)

fun BaseItemDto.cinemaBackdropUrl(api: ApiClient, width: Int): String? =
	cinemaBackdropImage?.getUrl(api, maxWidth = width)

