package org.jellyfin.androidtv.ui.cinema

import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.apiclient.seriesPrimaryImage
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageFormat
import org.jellyfin.sdk.model.api.ImageType

/** Ordered candidates are retained until an image actually loads, not just until a tag exists. */
data class CinemaArtwork(val urls: List<String>)

internal val BaseItemDto.cinemaPosterImages: List<JellyfinImage>
	get() = listOfNotNull(
		itemImages[ImageType.PRIMARY],
		seriesPrimaryImage,
		parentImages[ImageType.PRIMARY],
		itemImages[ImageType.THUMB],
		parentImages[ImageType.THUMB],
		seriesThumbImage,
	) + itemBackdropImages + parentBackdropImages

internal val BaseItemDto.cinemaThumbImages: List<JellyfinImage>
	get() = listOfNotNull(parentImages[ImageType.THUMB], seriesThumbImage, itemImages[ImageType.THUMB]) +
		itemBackdropImages + parentBackdropImages + cinemaPosterImages

internal val BaseItemDto.cinemaBackdropImages: List<JellyfinImage>
	get() = itemBackdropImages + parentBackdropImages +
		listOfNotNull(itemImages[ImageType.THUMB], parentImages[ImageType.THUMB]) + cinemaPosterImages

fun BaseItemDto.cinemaPosterUrl(api: ApiClient, width: Int, preferSeries: Boolean = false): CinemaArtwork =
	cinemaArtwork(api, width, if (preferSeries) listOfNotNull(seriesPrimaryImage) + cinemaPosterImages else cinemaPosterImages)

fun BaseItemDto.cinemaThumbUrl(api: ApiClient, width: Int): CinemaArtwork =
	cinemaArtwork(api, width, cinemaThumbImages)

fun BaseItemDto.cinemaBackdropUrl(api: ApiClient, width: Int): CinemaArtwork =
	cinemaArtwork(api, width, cinemaBackdropImages)

private fun BaseItemDto.cinemaArtwork(api: ApiClient, width: Int, images: List<JellyfinImage>): CinemaArtwork {
	val urls = images.map { image ->
		api.imageApi.getItemImageUrl(
			itemId = image.item,
			imageType = image.type,
			imageIndex = image.index,
			tag = image.tag.takeIf { it.isNotBlank() },
			maxWidth = width,
			// Older Android TV decoders cannot read every original format (e.g. AVIF).
			format = ImageFormat.JPG,
		)
	}
	// Some browse responses omit image tags. The image endpoint does not require a tag.
	val probes = if (images.isEmpty()) {
		listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP).map { type ->
			api.imageApi.getItemImageUrl(itemId = id, imageType = type, maxWidth = width, format = ImageFormat.JPG)
		}
	} else emptyList()
	return CinemaArtwork((urls + probes).distinct())
}

