package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.response.ValidatableResponse
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.emptyIterable
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class PinUpdaterIntegrationTest : IntegrationTest() {

    @Inject
    lateinit var pinCreator: PinCreator

    @Inject
    lateinit var boardCreator: BoardCreator

    @Test
    fun `Given a new description, Then the write replaces it`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = createPin(auth)

        // When
        update(auth, pin, description = "A corrected description").statusCode(200)

        // Then
        readPin(auth, pin).body("description", equalTo("A corrected description"))
    }

    @Test
    fun `Given new source addresses, Then the write replaces both, a blank one reading as none`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = createPin(auth)

        // When
        update(auth, pin, sourceContextUrl = "https://example.com/other", sourceMediaUrl = "  ")
            .statusCode(200)

        // Then
        readPin(auth, pin)
            .body("sourceContextUrl", equalTo("https://example.com/other"))
            .body("sourceMediaUrl", nullValue())
    }

    @Test
    fun `Given tag names, Then the write replaces the pin's tags`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = createPin(auth, tags = listOf("old"))

        // When
        update(auth, pin, tags = listOf("nature", "landscape")).statusCode(200)

        // Then
        readPin(auth, pin)
            .body("tags", hasSize<Any>(2))
            .body("tags.name", containsInAnyOrder("nature", "landscape"))
    }

    @Test
    fun `Given board ids, Then the write files the pin under them`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = createPin(auth)
        val board = boardCreator.create(author = auth.user, name = "Board", description = "")

        // When
        update(auth, pin, boardIds = listOf(board.id)).statusCode(200)

        // Then
        readPin(auth, pin).body("boards.id", containsInAnyOrder(board.id.toString()))
    }

    @Test
    fun `Given empty tags and board ids, Then the write clears both`() {
        // Given: a pin carrying one of each
        val auth = createAuthenticatedUser()
        val pin = createPin(auth, tags = listOf("old"))
        val board = boardCreator.create(author = auth.user, name = "Board", description = "")
        update(auth, pin, tags = listOf("old"), boardIds = listOf(board.id)).statusCode(200)

        // When
        update(auth, pin).statusCode(200)

        // Then
        readPin(auth, pin)
            .body("tags", emptyIterable<Any>())
            .body("boards", emptyIterable<Any>())
    }

    @Test
    fun `Given an unknown board id, Then the write returns 404 and changes nothing`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = createPin(auth)

        // When
        update(auth, pin, description = "Never stored", boardIds = listOf(UUID.randomUUID()))
            .statusCode(404)

        // Then
        readPin(auth, pin).body("description", equalTo("A pin"))
    }

    @Test
    fun `Given a refused board id, Then a tag the same write names is not created either`() {
        // Given: a name the author holds no tag for
        val auth = createAuthenticatedUser()
        val pin = createPin(auth)
        val newTag = "atagnobodyholds"

        // When: the write invents that tag and then meets a board that refuses the whole call
        update(auth, pin, tags = listOf(newTag), boardIds = listOf(UUID.randomUUID())).statusCode(404)

        // Then: the tag was rolled back with the rest. Resolved outside the transaction it would
        // stand here for good, nothing in the API sweeping an orphan tag.
        given()
            .authenticatedAs(auth)
            .queryParam("q", newTag)
            .`when`()
            .get("/api/v1/tags/search")
            .then()
            .statusCode(200)
            .body("results", emptyIterable<Any>())
    }

    @Test
    fun `Given another user's board id, Then the write returns 403 and changes nothing`() {
        // Given
        val auth = createAuthenticatedUser()
        val other = createAuthenticatedUser()
        val pin = createPin(auth)
        val othersBoard = boardCreator.create(author = other.user, name = "Not yours", description = "")

        // When
        update(auth, pin, description = "Never stored", boardIds = listOf(othersBoard.id))
            .statusCode(403)

        // Then
        readPin(auth, pin).body("description", equalTo("A pin"))
    }

    private fun createPin(auth: AuthenticatedUser, tags: List<String> = emptyList()): Pin =
        pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = "https://example.com/page",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "A pin",
            tags = tags,
        )

    @Test
    fun `Given an empty body, Then the write returns 400`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = createPin(auth)

        // When / Then: the body has to reach the resource method for its validation to ever run.
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .`when`()
            .put("/api/v1/pins/${pin.id}")
            .then()
            .statusCode(400)
    }

    @Suppress("LongParameterList") // The whole pin, which is what the route under test writes.
    private fun update(
        auth: AuthenticatedUser,
        pin: Pin,
        description: String = "A pin",
        sourceContextUrl: String? = "https://example.com/page",
        sourceMediaUrl: String? = "https://example.com/img.jpg",
        tags: List<String> = emptyList(),
        boardIds: List<UUID> = emptyList(),
    ): ValidatableResponse =
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body(
                mapOf(
                    "description" to description,
                    "sourceContextUrl" to sourceContextUrl,
                    "sourceMediaUrl" to sourceMediaUrl,
                    "tags" to tags,
                    "boardIds" to boardIds.map { it.toString() },
                ),
            )
            .`when`()
            .put("/api/v1/pins/${pin.id}")
            .then()

    private fun readPin(auth: AuthenticatedUser, pin: Pin): ValidatableResponse =
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/pins/${pin.id}")
            .then()
            .statusCode(200)
}
