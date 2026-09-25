package org.jellyfin.androidtv.ui.cinema

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

class CinemaImageUrlsTests : FunSpec({
	val factory = OkHttpFactory()
	val api = factory.create(
		baseUrl = "https://cinema.example/jellyfin",
		accessToken = null,
		clientInfo = ClientInfo("Cinema tests", "1"),
		deviceInfo = DeviceInfo("test-device", "Test"),
		httpClientOptions = HttpClientOptions(),
		socketConnectionFactory = factory,
	)
	val itemId = UUID.fromString("00000000-0000-0000-0000-000000000001")
	val seriesId = UUID.fromString("00000000-0000-0000-0000-000000000002")

	test("primary selection retains thumb and every backdrop as load fallbacks") {
		val item = BaseItemDto(
			id = itemId,
			type = BaseItemKind.MOVIE,
			imageTags = mapOf(ImageType.PRIMARY to "poster", ImageType.THUMB to "thumb"),
			backdropImageTags = listOf("background-0", "background-1"),
		)
		val urls = item.cinemaPosterUrl(api, 360).urls
		urls.size shouldBe 4
		urls[0] shouldContain "/Images/Primary"
		urls[1] shouldContain "/Images/Thumb"
		urls[2] shouldContain "imageIndex=0"
		urls[3] shouldContain "imageIndex=1"
		urls.forEach { url ->
			url shouldContain "https://cinema.example/jellyfin/Items/"
			url shouldContain "format=Jpg"
			url shouldContain "maxWidth=360"
		}
	}

	test("landscape candidates preserve order without retrying duplicate URLs") {
		val item = BaseItemDto(
			id = itemId,
			type = BaseItemKind.MOVIE,
			imageTags = mapOf(ImageType.PRIMARY to "poster", ImageType.THUMB to "thumb"),
			backdropImageTags = listOf("background"),
		)
		val urls = item.cinemaThumbUrl(api, 520).urls
		urls.size shouldBe 3
		urls[0] shouldContain "/Images/Thumb"
		urls[1] shouldContain "/Images/Backdrop"
		urls[2] shouldContain "/Images/Primary"
		item.cinemaBackdropUrl(api, 1600).urls.first() shouldContain "/Images/Backdrop"
	}

	test("episode detail prefers the series poster but retains its own image") {
		val item = BaseItemDto(
			id = itemId,
			type = BaseItemKind.EPISODE,
			seriesId = seriesId,
			seriesPrimaryImageTag = "series-poster",
			imageTags = mapOf(ImageType.PRIMARY to "episode-still"),
		)
		val urls = item.cinemaPosterUrl(api, 420, preferSeries = true).urls
		urls.size shouldBe 2
		urls.first() shouldContain "tag=series-poster"
		urls.last() shouldContain "tag=episode-still"
	}

	test("missing browse image tags still allow a bounded lookup at the image endpoint") {
		val urls = BaseItemDto(id = itemId, type = BaseItemKind.MOVIE).cinemaPosterUrl(api, 360).urls
		urls.size shouldBe 3
		urls[0] shouldContain "/Images/Primary"
		urls[1] shouldContain "/Images/Thumb"
		urls[2] shouldContain "/Images/Backdrop"
		urls.none { "tag=" in it } shouldBe true
	}

	test("parent artwork is used when an item has no images of its own") {
		val item = BaseItemDto(
			id = itemId,
			type = BaseItemKind.SEASON,
			parentPrimaryImageItemId = seriesId,
			parentPrimaryImageTag = "parent",
		)
		val urls = item.cinemaPosterUrl(api, 360).urls
		urls.size shouldBe 1
		urls.single() shouldContain "tag=parent"
	}
})


