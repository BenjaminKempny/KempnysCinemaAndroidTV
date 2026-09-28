package org.jellyfin.androidtv.ui.cinema

import androidx.lifecycle.ViewModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class CinemaLibraryTests : FunSpec({
	val store = ViewModelStore()
	afterTest { store.clear() }

	test("home starts independent requests in parallel") {
		val started = Channel<Unit>(Channel.UNLIMITED)
		val release = CompletableDeferred<Unit>()
		val api = LibraryTestApi { _, _ ->
			started.send(Unit)
			release.await()
			libraryResponse(emptyList())
		}
		val vm = CinemaHomeViewModel(api, libraryViews())
		store.put("home", vm)
		try {
			withTimeout(5_000) { repeat(3) { started.receive() } }
		} finally {
			release.complete(Unit)
		}
		withTimeout(5_000) { vm.state.first { !it.loading } }
	}

	test("all movies includes multiple libraries and every page without collapsing collections") {
		val movies = (1..125).map { libraryItem(it, BaseItemKind.MOVIE) }
		val collection = libraryItem(200, BaseItemKind.BOX_SET)
		val starts = ConcurrentLinkedQueue<Int>()
		val api = LibraryTestApi { path, query ->
			if (path == "/UserItems/Resume") {
				query["includeItemTypes"] shouldBe setOf(BaseItemKind.MOVIE)
				libraryResponse(emptyList())
			} else {
				query["parentId"] shouldBe null
				query["includeItemTypes"] shouldBe setOf(BaseItemKind.MOVIE)
				// Reproduce the server's user-configured collection grouping unless disabled.
				val source = if (query["collapseBoxSetItems"] == false) movies else listOf(collection, movies.first())
				val start = query["startIndex"] as? Int ?: 0
				if (query["sortBy"] == setOf(ItemSortBy.SORT_NAME)) starts.add(start)
				libraryResponse(source.drop(start).take(query["limit"] as? Int ?: source.size), source.size)
			}
		}
		val vm = CinemaHomeViewModel(api, libraryViews(
			libraryItem(300, BaseItemKind.COLLECTION_FOLDER).copy(collectionType = CollectionType.MOVIES),
			libraryItem(301, BaseItemKind.COLLECTION_FOLDER).copy(collectionType = CollectionType.MOVIES),
		))
		store.put("home", vm)
		withTimeout(5_000) { vm.state.first { !it.loading } }.catalog shouldBe movies.take(60)
		vm.loadNextCatalogPage()
		withTimeout(5_000) { vm.state.first { it.catalog.size == 120 } }
		vm.loadNextCatalogPage()
		val state = withTimeout(5_000) { vm.state.first { it.catalog.size == 125 } }
		state.catalog shouldBe movies
		state.catalogHasMore shouldBe false
		starts.toList() shouldBe listOf(0, 60, 120)
	}

	test("a mixed library is not lost when there is also a movie-only library") {
		val movie = libraryItem(1, BaseItemKind.MOVIE)
		val api = LibraryTestApi { _, query ->
			query["parentId"] shouldBe null
			libraryResponse(listOf(movie))
		}
		val vm = CinemaHomeViewModel(api, libraryViews(
			libraryItem(300, BaseItemKind.COLLECTION_FOLDER).copy(collectionType = CollectionType.MOVIES),
			libraryItem(301, BaseItemKind.COLLECTION_FOLDER),
		))
		store.put("home", vm)
		withTimeout(5_000) { vm.state.first { !it.loading } }.catalog shouldBe listOf(movie)
	}

	test("collection tabs include only collections with actual members of the selected kind") {
		val movie = libraryItem(1, BaseItemKind.MOVIE)
		val show = libraryItem(2, BaseItemKind.SERIES)
		val movieBox = libraryItem(200, BaseItemKind.BOX_SET)
		val showBox = libraryItem(201, BaseItemKind.BOX_SET)
		val mixedBox = libraryItem(202, BaseItemKind.BOX_SET)
		val members = mapOf(movieBox.id to listOf(movie), showBox.id to listOf(show), mixedBox.id to listOf(movie, show))
		val api = LibraryTestApi { _, query ->
			val types = query["includeItemTypes"] as? Collection<*>
			when {
				types == setOf(BaseItemKind.BOX_SET) -> libraryResponse(listOf(movieBox, showBox, mixedBox))
				query["parentId"] in members -> {
					query["collapseBoxSetItems"] shouldBe false
					query["limit"] shouldBe 1
					val matching = members[query["parentId"]].orEmpty().filter { it.type in types.orEmpty() }
					// A count alone must never admit a collection with no matching members.
					libraryResponse(matching.take(1), total = 999)
				}
				else -> libraryResponse(emptyList())
			}
		}
		val vm = CinemaHomeViewModel(api, libraryViews())
		store.put("home", vm)
		withTimeout(5_000) { vm.state.first { !it.loading } }
		vm.setView(CinemaView.Collections)
		withTimeout(5_000) { vm.state.first { !it.loading } }.collections shouldBe listOf(movieBox, mixedBox)
		vm.setMediaType(CinemaMediaType.Shows)
		withTimeout(5_000) { vm.state.first { !it.loading } }.collections shouldBe listOf(showBox, mixedBox)
	}

	CinemaMediaType.entries.forEach { mediaType ->
		test("$mediaType collection detail retains its filter after refresh and sorting") {
			val box = libraryItem(200, BaseItemKind.BOX_SET)
			val movie = libraryItem(1, BaseItemKind.MOVIE)
			val show = libraryItem(2, BaseItemKind.SERIES)
			val queries = Channel<Map<String, Any?>>(Channel.UNLIMITED)
			val api = LibraryTestApi { path, query ->
				if (path == "/Items/{itemId}") libraryResponse(box)
				else {
					queries.send(query)
					// Also guard the displayed children against unexpected server types.
					libraryResponse(listOf(movie, show, box))
				}
			}
			val vm = CinemaDetailViewModel(api)
			store.put("detail", vm)
			vm.load(box.id, mediaType)
			val expected = if (mediaType == CinemaMediaType.Movies) movie else show
			withTimeout(5_000) { vm.state.first { it.children.isNotEmpty() } }.children shouldBe listOf(expected)
			suspend fun assertFilter() {
				val query = withTimeout(5_000) { queries.receive() }
				query["parentId"] shouldBe box.id
				query["includeItemTypes"] shouldBe setOf(mediaType.itemKind)
				query["collapseBoxSetItems"] shouldBe false
			}
			assertFilter()
			vm.refresh()
			assertFilter()
			vm.setSort(CinemaSort.Year)
			assertFilter()
		}
	}

	test("collections opened without a media context still allow movies and series") {
		val box = libraryItem(200, BaseItemKind.BOX_SET)
		val members = listOf(libraryItem(1, BaseItemKind.MOVIE), libraryItem(2, BaseItemKind.SERIES))
		val api = LibraryTestApi { path, query ->
			if (path == "/Items/{itemId}") libraryResponse(box)
			else {
				query["includeItemTypes"] shouldBe setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
				libraryResponse(members)
			}
		}
		val vm = CinemaDetailViewModel(api)
		store.put("detail", vm)
		vm.load(box.id)
		withTimeout(5_000) { vm.state.first { it.children.isNotEmpty() } }.children shouldBe members
	}
})

private fun libraryItem(id: Long, type: BaseItemKind) = BaseItemDto(id = UUID(0, id), type = type, name = "Item $id")
private fun libraryItem(id: Int, type: BaseItemKind) = libraryItem(id.toLong(), type)

private fun libraryViews(vararg libraries: BaseItemDto): UserViewsRepository = mockk {
	every { views } returns flowOf(libraries.toList())
}

private fun libraryResponse(items: List<BaseItemDto>, total: Int = items.size): RawResponse = RawResponse(
	Json.encodeToString(BaseItemDtoQueryResult(items = items, totalRecordCount = total, startIndex = 0)).encodeToByteArray(),
	200,
	emptyMap(),
)

private fun libraryResponse(item: BaseItemDto): RawResponse =
	RawResponse(Json.encodeToString(item).encodeToByteArray(), 200, emptyMap())

/** Exercise SDK parameter serialization without contacting or modifying a real server. */
private class LibraryTestApi(
	private val respond: suspend (String, Map<String, Any?>) -> RawResponse,
) : ApiClient() {
	override val baseUrl = "https://cinema.example"
	override val accessToken: String? = null
	override val clientInfo = ClientInfo("Cinema tests", "1")
	override val deviceInfo = DeviceInfo("tests", "Tests")
	override val httpClientOptions = HttpClientOptions()
	override val webSocket: SocketApi get() = error("No sockets in library tests")
	override fun update(baseUrl: String?, accessToken: String?, clientInfo: ClientInfo, deviceInfo: DeviceInfo) = Unit
	override suspend fun request(
		method: HttpMethod,
		pathTemplate: String,
		pathParameters: Map<String, Any?>,
		queryParameters: Map<String, Any?>,
		requestBody: Any?,
	): RawResponse = respond(pathTemplate, queryParameters)
}

