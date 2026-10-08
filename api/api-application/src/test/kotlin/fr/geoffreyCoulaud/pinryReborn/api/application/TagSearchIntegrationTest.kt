package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.TagCreator
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test

@QuarkusTest
class TagSearchIntegrationTest : IntegrationTest() {

    @Inject lateinit var tagCreator: TagCreator

    @Inject lateinit var personCreator: PersonCreator

    // The search reads the user's tags, not a pin's, so nothing here needs a pin to hang them on.
    private fun createTagsFor(auth: AuthenticatedUser, vararg tagNames: String) {
        tagNames.forEach { tagCreator.findOrCreate(name = it, user = auth.user) }
    }

    @Test
    fun `Given tags exist, Then search returns the prefix matches first`() {
        // Given
        val auth = createAuthenticatedUser()
        createTagsFor(auth, "landscape", "scapegoat", "mountain")

        // When, Then
        given()
            .authenticatedAs(auth)
            .queryParam("q", "scape")
            .`when`()
            .get("/api/v1/tags/search")
            .then()
            .statusCode(200)
            .body("results", hasSize<Any>(2))
            .body("results[0].tag.name", equalTo("scapegoat"))
            .body("results[1].tag.name", equalTo("landscape"))
    }

    @Test
    fun `Given a typo in the query, Then search returns nothing`() {
        // Given: similarity left the product, so a term is a substring or it is not a match
        val auth = createAuthenticatedUser()
        createTagsFor(auth, "landscape", "nature", "mountain")

        // When, Then
        given()
            .authenticatedAs(auth)
            .queryParam("q", "landscpe")
            .`when`()
            .get("/api/v1/tags/search")
            .then()
            .statusCode(200)
            .body("results", hasSize<Any>(0))
    }

    @Test
    fun `Given empty query, Then returns 400`() {
        // Given
        val auth = createAuthenticatedUser()

        // When, Then
        given()
            .authenticatedAs(auth)
            .queryParam("q", "")
            .`when`()
            .get("/api/v1/tags/search")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("SEARCH_EMPTY_QUERY"))
    }

    @Test
    fun `Given no query parameter, Then returns 400`() {
        // Given: the refusal is the use case's on both routes, so one emptiness carries one code
        val auth = createAuthenticatedUser()

        // When, Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/tags/search")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("SEARCH_EMPTY_QUERY"))
    }

    @Test
    fun `Given limit parameter, Then returns at most limit results`() {
        // Given
        val auth = createAuthenticatedUser()
        createTagsFor(auth, "test1", "test2", "test3", "test4", "test5")

        // When, Then
        given()
            .authenticatedAs(auth)
            .queryParam("q", "test")
            .queryParam("limit", 2)
            .`when`()
            .get("/api/v1/tags/search")
            .then()
            .statusCode(200)
            .body("results", hasSize<Any>(2))
    }

    @Test
    fun `Given limit exceeds max, Then returns at most max results`() {
        // Given
        val auth = createAuthenticatedUser()
        val tagNames = (1..25).map { "tag$it" }.toTypedArray()
        createTagsFor(auth, *tagNames)

        // When, Then - requesting 100 should be capped to max (20)
        given()
            .authenticatedAs(auth)
            .queryParam("q", "tag")
            .queryParam("limit", 100)
            .`when`()
            .get("/api/v1/tags/search")
            .then()
            .statusCode(200)
            .body("results", hasSize<Any>(20))
    }

    @Test
    fun `Given unauthenticated request, Then returns 401`() {
        // When, Then
        given().queryParam("q", "test").`when`().get("/api/v1/tags/search").then().statusCode(401)
    }

    @Test
    fun `Given search for another user's tags, Then returns only own tags`() {
        // Given
        val auth1 = createAuthenticatedUser()
        val auth2 = createAuthenticatedUser()
        createTagsFor(auth1, "user1tag")
        createTagsFor(auth2, "user2tag")

        // When, Then - user1 should only see their own tags
        given()
            .authenticatedAs(auth1)
            .queryParam("q", "user")
            .`when`()
            .get("/api/v1/tags/search")
            .then()
            .statusCode(200)
            .body("results", hasSize<Any>(1))
            .body("results[0].tag.name", equalTo("user1tag"))
    }

    // --- Persons, the same search over another of the user's names ---

    private fun searchPersons(auth: AuthenticatedUser, query: String) =
        given().authenticatedAs(auth).queryParam("q", query).`when`().get("/api/v1/persons/search").then()

    @Test
    fun `Given two homonyms with different addresses, Then the person search returns both with their addresses`() {
        // Given
        val auth = createAuthenticatedUser()
        personCreator.findOrCreate(name = "Alice", urls = listOf(FIRST_URL), user = auth.user)
        personCreator.findOrCreate(name = "Alice", urls = listOf(SECOND_URL), user = auth.user)

        // When, Then
        searchPersons(auth, "alice")
            .statusCode(200)
            .body("results", hasSize<Any>(2))
            .body("results.person.name", contains("Alice", "Alice"))
            .body("results.person.urls", containsInAnyOrder(listOf(FIRST_URL), listOf(SECOND_URL)))
    }

    @Test
    fun `Given a name beginning with the term and one merely holding it, Then the person search serves it first`() {
        // Given
        val auth = createAuthenticatedUser()
        personCreator.findOrCreate(name = "Malice", urls = emptyList(), user = auth.user)
        personCreator.findOrCreate(name = "Alice", urls = emptyList(), user = auth.user)

        // When, Then
        searchPersons(auth, "alice").statusCode(200).body("results.person.name", contains("Alice", "Malice"))
    }

    @Test
    fun `Given a blank query, Then the person search returns 400 as the tag search does`() {
        // Given
        val auth = createAuthenticatedUser()

        // When, Then
        searchPersons(auth, "  ")
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("SEARCH_EMPTY_QUERY"))
    }

    private companion object {
        const val FIRST_URL = "https://a.test/alice"
        const val SECOND_URL = "https://b.test/alice"
    }
}
