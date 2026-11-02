package org.jellyfin.androidtv.data.sdns

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.URLDecoder

/** In-memory PostgREST fake for the `movies` table. */
private class FakeSupabase(private val profileId: String = PROFILE) : SdnsTransport {
	val rows = mutableListOf<JsonObject>()
	val requests = mutableListOf<Triple<String, String, String?>>()
	var slugConflicts = 0
	private var nextId = 1

	fun seed(title: String, year: Int?, imdbId: String?, seen: Boolean, rating: Double?, profile: String = profileId) {
		rows += buildJsonObject {
			put("id", "row${nextId++}")
			put("profile_id", profile)
			put("title", title)
			put("year", year)
			put("imdb_id", imdbId)
			put("seen", seen)
			put("rating", rating)
			put("imdb_rating", 7.9)
			put("priority", 2)
		}
	}

	override suspend fun execute(config: SdnsConfig, method: String, path: String, body: String?): String {
		requests += Triple(method, path, body)
		val table = path.substringBefore("?")
		val filters = path.substringAfter("?", "").split("&")
			.filter { it.isNotEmpty() && !it.startsWith("select=") && !it.startsWith("order=") }
			.associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }

		fun JsonObject.matches() = filters.all { (key, filter) ->
			val value = (this[key] as? JsonPrimitive)?.content
			when {
				filter.startsWith("eq.") -> value == filter.removePrefix("eq.")
				filter.startsWith("ilike.") -> {
					val pattern = filter.removePrefix("ilike.")
					if (pattern.endsWith("*")) value?.startsWith(pattern.dropLast(1), ignoreCase = true) == true
					else value.equals(pattern, ignoreCase = true)
				}
				else -> error("Unsupported filter $filter")
			}
		}

		require(table == "movies" || table == "profiles") { "Unexpected table $table" }
		if (table == "profiles") {
			val profiles = listOf(
				buildJsonObject { put("id", PROFILE); put("name", "Benjamin Kempny") },
				buildJsonObject { put("id", "22222222-2222-2222-2222-222222222222"); put("name", "Hanna") },
				buildJsonObject { put("id", "33333333-3333-3333-3333-333333333333"); put("name", "Hans") },
			)
			return JsonArray(profiles.filter { it.matches() }).toString()
		}

		return when (method) {
			"GET" -> JsonArray(rows.filter { it.matches() }).toString()
			"PATCH" -> {
				val patch = Json.parseToJsonElement(body!!).jsonObject
				val updated = rows.mapIndexedNotNull { index, row ->
					if (!row.matches()) null
					else JsonObject(row + patch).also { rows[index] = it }
				}
				JsonArray(updated).toString()
			}

			"POST" -> {
				if (slugConflicts > 0) {
					slugConflicts--
					throw SdnsException("Supabase-Fehler 409: {\"code\":\"23505\",\"details\":\"Key (slug)=(x) already exists.\"}")
				}
				val row = JsonObject(Json.parseToJsonElement(body!!).jsonObject + ("id" to JsonPrimitive("row${nextId++}")))
				rows += row
				JsonArray(listOf(row)).toString()
			}

			else -> error("Unsupported method $method")
		}
	}

	companion object {
		const val PROFILE = "11111111-1111-1111-1111-111111111111"
	}
}

class SdnsRepositoryTests : FunSpec({
	fun repository(fake: FakeSupabase, profileId: String = FakeSupabase.PROFILE) = SdnsRepository(
		config = { SdnsConfig("https://example.supabase.co", "anon", profileId) },
		transport = fake,
	)

	test("rating a watchlist movie updates only rating and seen") {
		val fake = FakeSupabase().apply { seed("Dune", 2021, "tt1160419", seen = false, rating = null) }

		val result = repository(fake).submit(SdnsMovieQuery("Dune", 2021, "tt1160419"), 8.5)

		result.seen shouldBe true
		result.rating shouldBe 8.5
		fake.rows shouldHaveSize 1
		val row = fake.rows.single()
		(row["imdb_rating"] as JsonPrimitive).content shouldBe "7.9"
		(row["priority"] as JsonPrimitive).content shouldBe "2"
		val patchBody = fake.requests.single { it.first == "PATCH" }.third!!
		patchBody shouldNotContain "title"
		patchBody shouldNotContain "imdb_rating"
		patchBody shouldNotContain "priority"
	}

	test("saving twice never creates duplicates") {
		val fake = FakeSupabase()
		val repo = repository(fake)
		val query = SdnsMovieQuery("Arrival", 2016, "tt2543164")

		repo.submit(query, 7.0)
		repo.submit(query, 8.0)
		repo.submit(query, 8.0)

		fake.rows shouldHaveSize 1
		repo.find(query)?.rating shouldBe 8.0
		fake.requests.count { it.first == "POST" } shouldBe 1
	}

	test("unknown movie is inserted as seen and rated") {
		val fake = FakeSupabase()

		val result = repository(fake).submit(SdnsMovieQuery("Arrival", 2016, "tt2543164"), 9.0)

		result.seen shouldBe true
		result.rating shouldBe 9.0
		val body = fake.requests.single { it.first == "POST" }.third!!
		body shouldContain "\"profile_id\":\"${FakeSupabase.PROFILE}\""
		body shouldContain "\"imdb_id\":\"tt2543164\""
		body shouldContain "\"slug\":\"arrival-"
	}

	test("slug collision is retried with a new slug") {
		val fake = FakeSupabase().apply { slugConflicts = 1 }

		repository(fake).submit(SdnsMovieQuery("Arrival", 2016, "tt2543164"), 9.0)

		fake.rows shouldHaveSize 1
		fake.requests.count { it.first == "POST" } shouldBe 2
	}

	test("slug retries are limited") {
		val fake = FakeSupabase().apply { slugConflicts = 5 }

		shouldThrow<SdnsException> { repository(fake).submit(SdnsMovieQuery("Arrival", 2016, null), 9.0) }
		fake.requests.count { it.first == "POST" } shouldBe 3
	}

	test("title fallback matches movies stored without imdb id") {
		val fake = FakeSupabase().apply { seed("Amélie", 2001, null, seen = false, rating = null) }

		repository(fake).submit(SdnsMovieQuery("Amelie", 2002, "tt0211915"), 9.5)

		fake.rows shouldHaveSize 1
		(fake.rows.single()["rating"] as JsonPrimitive).content shouldBe "9.5"
	}

	test("only marking as seen keeps an existing rating") {
		val fake = FakeSupabase().apply { seed("Dune", 2021, "tt1160419", seen = true, rating = 6.0) }

		val result = repository(fake).submit(SdnsMovieQuery("Dune", 2021, "tt1160419"), null)

		result.seen shouldBe true
		result.rating shouldBe 6.0
	}

	test("movies of other profiles are never touched") {
		val fake = FakeSupabase().apply { seed("Dune", 2021, "tt1160419", seen = false, rating = null, profile = "other") }

		repository(fake).submit(SdnsMovieQuery("Dune", 2021, "tt1160419"), 8.0)

		fake.rows shouldHaveSize 2
		(fake.rows.first()["seen"] as JsonPrimitive).content shouldBe "false"
	}

	test("invalid rating is rejected before any write") {
		val fake = FakeSupabase()
		shouldThrow<IllegalArgumentException> {
			repository(fake).submit(SdnsMovieQuery("Dune", 2021, "tt1160419"), 11.0)
		}
		fake.requests.none { it.first != "GET" } shouldBe true
	}

	test("missing profile is reported") {
		shouldThrow<SdnsException> {
			repository(FakeSupabase(), profileId = "").submit(SdnsMovieQuery("Dune", 2021, null), 5.0)
		}
	}

	test("profile name is resolved to its uuid") {
		val fake = FakeSupabase().apply { seed("Dune", 2021, "tt1160419", seen = false, rating = null) }

		repository(fake, profileId = "benjamin").submit(SdnsMovieQuery("Dune", 2021, "tt1160419"), 7.5)

		fake.rows shouldHaveSize 1
		(fake.rows.single()["rating"] as JsonPrimitive).content shouldBe "7.5"
		fake.requests.none { it.second.contains("profile_id=eq.benjamin") } shouldBe true
	}

	test("unknown profile name is reported") {
		shouldThrow<SdnsException> {
			repository(FakeSupabase(), profileId = "Niemand").find(SdnsMovieQuery("Dune", 2021, null))
		}
	}

	test("exact profile name wins over prefix") {
		val fake = FakeSupabase()
		repository(fake, profileId = "Hanna").find(SdnsMovieQuery("Dune", 2021, null))
		fake.requests.any { it.second.contains("profile_id=eq.22222222-2222-2222-2222-222222222222") } shouldBe true
	}

	test("ambiguous profile prefix is reported") {
		shouldThrow<SdnsException> {
			repository(FakeSupabase(), profileId = "Han").find(SdnsMovieQuery("Dune", 2021, null))
		}
	}
})

