package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.emptyIterable
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class BoardMembershipIntegrationTest : IntegrationTest() {

    @Inject
    lateinit var pinCreator: PinCreator

    @Inject
    lateinit var boardCreator: BoardCreator

    // --- Set boards on a pin ---

    @Test
    fun `Given two owned boards, Then setting them on a pin returns both in the pin's boards`() {
        // Given
        val auth = createAuthenticatedUser()
        val board1 = boardCreator.create(author = auth.user, name = "Board 1", description = "")
        val board2 = boardCreator.create(author = auth.user, name = "Board 2", description = "")
        val pin = pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "Pin",
            tags = emptyList(),
        )

        // When
        replacePin(auth, pin, boardIds = listOf(board1.id, board2.id))
            .statusCode(200)
            .body("boards", hasSize<Any>(2))
            .body("boards.id", containsInAnyOrder(board1.id.toString(), board2.id.toString()))

        // Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/pins/${pin.id}")
            .then()
            .statusCode(200)
            .body("boards", hasSize<Any>(2))
            .body("boards.id", containsInAnyOrder(board1.id.toString(), board2.id.toString()))
    }

    @Test
    fun `Given two pins added to the same board, Then it lists both pins with pinCount 2`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Shared board", description = "")
        val pin1 = pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = "https://example.com/1",
            sourceMediaUrl = "https://example.com/img1.jpg",
            description = "Pin 1",
            tags = emptyList(),
        )
        val pin2 = pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = "https://example.com/2",
            sourceMediaUrl = "https://example.com/img2.jpg",
            description = "Pin 2",
            tags = emptyList(),
        )
        replacePin(auth, pin1, boardIds = listOf(board.id)).statusCode(200)
        replacePin(auth, pin2, boardIds = listOf(board.id)).statusCode(200)

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards/${board.id}/pins")
            .then()
            .statusCode(200)
            .body("pins", hasSize<Any>(2))
            .body("pins.id", containsInAnyOrder(pin1.id.toString(), pin2.id.toString()))

        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards/${board.id}")
            .then()
            .statusCode(200)
            .body("pinCount", equalTo(2))
    }

    // --- Empty board ---

    @Test
    fun `Given a board with no pins, Then listing its pins returns an empty page`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Empty", description = "")

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards/${board.id}/pins")
            .then()
            .statusCode(200)
            .body("pins", emptyIterable<Any>())
    }

    // --- Invalid membership ---

    @Test
    fun `Given an unknown board id, Then setting it on a pin returns 404`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = pinCreator.createPin(
            author = auth.user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "Pin",
            tags = emptyList(),
        )

        // When / Then
        replacePin(auth, pin, boardIds = listOf(UUID.randomUUID()))
            .statusCode(404)
    }

    @Test
    fun `Given another user's board id, Then setting it on a pin returns 403`() {
        // Given
        val owner = createAuthenticatedUser()
        val attacker = createAuthenticatedUser()
        val otherBoard = boardCreator.create(author = attacker.user, name = "Not yours", description = "")
        val pin = pinCreator.createPin(
            author = owner.user,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = "Pin",
            tags = emptyList(),
        )

        // When / Then
        replacePin(owner, pin, boardIds = listOf(otherBoard.id))
            .statusCode(403)
    }

    // --- Bulk membership ---

    private fun createPin(author: User, description: String = "A pin"): Pin =
        pinCreator.createPin(
            author = author,
            sourceContextUrl = "https://example.com",
            sourceMediaUrl = "https://example.com/img.jpg",
            description = description,
            tags = emptyList(),
        )

    private fun bulkMembership(auth: AuthenticatedUser, method: String, boardId: UUID, pinIds: List<UUID>) =
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body(mapOf("pinIds" to pinIds.map { it.toString() }))
            .`when`()
            .request(method, "/api/v1/boards/$boardId/pins")
            .then()

    private fun boardsOf(auth: AuthenticatedUser, pin: Pin) =
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/pins/${pin.id}")
            .then()
            .statusCode(200)

    @Test
    fun `Given two owned pins, Then adding them to a board files both under it`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "")
        val first = createPin(auth.user, "First")
        val second = createPin(auth.user, "Second")

        // When
        bulkMembership(auth, "POST", board.id, listOf(first.id, second.id)).statusCode(204)

        // Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards/${board.id}/pins")
            .then()
            .statusCode(200)
            .body("pins.id", containsInAnyOrder(first.id.toString(), second.id.toString()))
    }

    @Test
    fun `Given a pin already on the board, Then adding it again leaves one membership`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "")
        val pin = createPin(auth.user)
        bulkMembership(auth, "POST", board.id, listOf(pin.id)).statusCode(204)

        // When
        bulkMembership(auth, "POST", board.id, listOf(pin.id)).statusCode(204)

        // Then
        boardsOf(auth, pin).body("boards", hasSize<Any>(1))
    }

    @Test
    fun `Given another user's pin last, Then adding files nothing`() {
        // Given
        val auth = createAuthenticatedUser()
        val stranger = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "")
        val own = createPin(auth.user)
        val theirs = createPin(stranger.user)

        // When
        bulkMembership(auth, "POST", board.id, listOf(own.id, theirs.id)).statusCode(403)

        // Then
        boardsOf(auth, own).body("boards", emptyIterable<Any>())
    }

    @Test
    fun `Given two pins on a board, Then removing them takes both out and keeps their other boards`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "")
        val kept = boardCreator.create(author = auth.user, name = "Kept", description = "")
        val first = createPin(auth.user, "First")
        val second = createPin(auth.user, "Second")
        bulkMembership(auth, "POST", board.id, listOf(first.id, second.id)).statusCode(204)
        bulkMembership(auth, "POST", kept.id, listOf(first.id)).statusCode(204)

        // When
        bulkMembership(auth, "DELETE", board.id, listOf(first.id, second.id)).statusCode(204)

        // Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards/${board.id}/pins")
            .then()
            .statusCode(200)
            .body("pins", emptyIterable<Any>())
        boardsOf(auth, first).body("boards.id", containsInAnyOrder(kept.id.toString()))
    }

    @Test
    fun `Given an empty body, Then the bulk removal returns 400`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "")

        // When / Then: the body has to reach the resource method for @NotEmpty to ever be read.
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .`when`()
            .delete("/api/v1/boards/${board.id}/pins")
            .then()
            .statusCode(400)
    }

    @Test
    fun `Given a recycled pin, Then adding it to a board returns 409`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "")
        val pin = createPin(auth.user)
        given().authenticatedAs(auth).`when`().delete("/api/v1/pins/${pin.id}").then().statusCode(204)

        // When / Then: the third arm of the batch grammar, beside 404 and 403 (ADR 0039, decision 2).
        bulkMembership(auth, "POST", board.id, listOf(pin.id)).statusCode(409)
    }

    @Test
    fun `Given another user's pin last, Then removing takes nothing out`() {
        // Given
        val auth = createAuthenticatedUser()
        val stranger = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "")
        val own = createPin(auth.user)
        val theirs = createPin(stranger.user)
        bulkMembership(auth, "POST", board.id, listOf(own.id)).statusCode(204)

        // When
        bulkMembership(auth, "DELETE", board.id, listOf(own.id, theirs.id)).statusCode(403)

        // Then
        boardsOf(auth, own).body("boards.id", containsInAnyOrder(board.id.toString()))
    }

    // --- Searching inside a board ---

    @Test
    fun `Given a term, Then the board's page holds its own matching pins and no other`() {
        // Given: one match inside the board, one outside it, one inside matching nothing
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "")
        val inside = createPin(auth.user, "A cat indoors")
        createPin(auth.user, "A cat elsewhere")
        val unrelated = createPin(auth.user, "A dog")
        bulkMembership(auth, "POST", board.id, listOf(inside.id, unrelated.id)).statusCode(204)

        // When / Then
        given()
            .authenticatedAs(auth)
            .queryParam("q", "cat")
            .`when`()
            .get("/api/v1/boards/${board.id}/pins")
            .then()
            .statusCode(200)
            .body("pins", hasSize<Any>(1))
            .body("pins[0].id", equalTo(inside.id.toString()))
    }

    @Test
    fun `Given a blank term, Then the board's page refuses it under SEARCH_EMPTY_QUERY`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "")

        // When / Then: one refusal, one code, whichever catalogue route expresses it
        given()
            .authenticatedAs(auth)
            .queryParam("q", " ")
            .`when`()
            .get("/api/v1/boards/${board.id}/pins")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("SEARCH_EMPTY_QUERY"))
    }
}
