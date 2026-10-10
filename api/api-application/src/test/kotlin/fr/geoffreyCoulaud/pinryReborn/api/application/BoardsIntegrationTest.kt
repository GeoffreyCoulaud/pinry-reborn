package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.RemoteCollectionRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import java.time.Instant
import java.util.UUID
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.hasKey
import org.junit.jupiter.api.Test

@QuarkusTest
class BoardsIntegrationTest : IntegrationTest() {

    @Inject lateinit var boardCreator: BoardCreator

    @Inject lateinit var pinCreator: PinCreator

    @Inject lateinit var mediaRepository: MediaRepositoryInterface

    @Inject lateinit var remoteCollectionRepository: RemoteCollectionRepositoryInterface

    // --- Create ---

    @Test
    fun `Given valid board data, Then creation returns 201 with Location and zero pin count`() {
        // Given
        val auth = createAuthenticatedUser()

        // When / Then
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body("""{"name": "Travel", "description": "Places to visit"}""")
            .`when`()
            .post("/api/v1/boards")
            .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .body("id", notNullValue())
            .body("name", equalTo("Travel"))
            .body("description", equalTo("Places to visit"))
            .body("pinCount", equalTo(0))
    }

    @Test
    fun `Given a name already held up to case, Then creating another board returns 409`() {
        // Given: the unique index folds A to Z, so these two names are one name
        val auth = createAuthenticatedUser()
        boardCreator.create(author = auth.user, name = "voyage", description = "")

        // When / Then
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body("""{"name": "Voyage", "description": ""}""")
            .`when`()
            .post("/api/v1/boards")
            .then()
            .statusCode(409)
            .body("code", equalTo("BOARD_NAME_ALREADY_EXISTS"))
    }

    @Test
    fun `Given two boards, Then renaming one onto the other's name returns 409`() {
        // Given
        val auth = createAuthenticatedUser()
        boardCreator.create(author = auth.user, name = "voyage", description = "")
        val renamed = boardCreator.create(author = auth.user, name = "cuisine", description = "")

        // When / Then
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body("""{"name": "Voyage", "description": ""}""")
            .`when`()
            .put("/api/v1/boards/${renamed.id}")
            .then()
            .statusCode(409)
            .body("code", equalTo("BOARD_NAME_ALREADY_EXISTS"))
    }

    @Test
    fun `Given another user holding the name, Then creating a board with it succeeds`() {
        // Given: the index is scoped to the author, so a name is an identity per account
        val other = createAuthenticatedUser()
        val auth = createAuthenticatedUser()
        boardCreator.create(author = other.user, name = "voyage", description = "")

        // When / Then
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body("""{"name": "voyage", "description": ""}""")
            .`when`()
            .post("/api/v1/boards")
            .then()
            .statusCode(201)
    }

    // --- Get ---

    @Test
    fun `Given a created board, Then GET by id returns its data`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Recipes", description = "Cooking ideas")

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards/${board.id}")
            .then()
            .statusCode(200)
            .body("id", equalTo(board.id.toString()))
            .body("name", equalTo("Recipes"))
            .body("description", equalTo("Cooking ideas"))
            .body("pinCount", equalTo(0))
            .body("remoteCollections", empty<Any>())
    }

    @Test
    fun `Given a board with two linked collections, Then GET by id returns them sorted by name`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Harbours", description = "")
        linkCollection(board, name = "Zeeland ports", url = "https://remote.example/zeeland")
        linkCollection(board, name = "breton ports", url = "https://remote.example/breton")

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards/${board.id}")
            .then()
            .statusCode(200)
            .body("remoteCollections.name", contains("breton ports", "Zeeland ports"))
            .body("remoteCollections.url", contains("https://remote.example/breton", "https://remote.example/zeeland"))
    }

    private fun linkCollection(board: Board, name: String, url: String) {
        remoteCollectionRepository.saveRemoteCollection(
            RemoteCollection(
                id = UUID.randomUUID(),
                author = board.author,
                url = url,
                name = name,
                board = board,
                createdAt = board.createdAt,
            )
        )
    }

    // --- List ---

    @Test
    fun `Given boards with mixed-case names, Then listing sorts them case-insensitively`() {
        // Given
        val auth = createAuthenticatedUser()
        boardCreator.create(author = auth.user, name = "Banana", description = "")
        boardCreator.create(author = auth.user, name = "apple", description = "")
        boardCreator.create(author = auth.user, name = "cherry", description = "")

        // When / Then
        // Naive case-sensitive ASCII order would be "Banana", "apple", "cherry" (B=66 < a=97 < c=99).
        // The correct case-insensitive order is "apple", "Banana", "cherry", so this data discriminates
        // a correct implementation from a regression to case-sensitive sorting.
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards")
            .then()
            .statusCode(200)
            .body("boards.name", contains("apple", "Banana", "cherry"))
    }

    // --- Update ---

    @Test
    fun `Given an existing board, Then updating its name and description is reflected on get`() {
        // Given
        val auth = createAuthenticatedUser()
        val board = boardCreator.create(author = auth.user, name = "Old name", description = "Old description")

        // When
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body("""{"name": "New name", "description": "New description"}""")
            .`when`()
            .put("/api/v1/boards/${board.id}")
            .then()
            .statusCode(200)
            .body("name", equalTo("New name"))
            .body("description", equalTo("New description"))

        // Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards/${board.id}")
            .then()
            .statusCode(200)
            .body("name", equalTo("New name"))
            .body("description", equalTo("New description"))
    }

    // --- Owner scoping ---

    @Test
    fun `Given another user's board, Then getting it returns 403`() {
        // Given
        val owner = createAuthenticatedUser()
        val attacker = createAuthenticatedUser()
        val board = boardCreator.create(author = owner.user, name = "Private", description = "")

        // When / Then
        given().authenticatedAs(attacker).`when`().get("/api/v1/boards/${board.id}").then().statusCode(403)
    }

    @Test
    fun `Given an unknown board id, Then getting it returns 404`() {
        // Given
        val auth = createAuthenticatedUser()

        // When / Then
        given().authenticatedAs(auth).`when`().get("/api/v1/boards/${UUID.randomUUID()}").then().statusCode(404)
    }

    @Test
    fun `Given another user's board, Then updating it returns 403`() {
        // Given
        val owner = createAuthenticatedUser()
        val attacker = createAuthenticatedUser()
        val board = boardCreator.create(author = owner.user, name = "Private", description = "")

        // When / Then
        given()
            .authenticatedAs(attacker)
            .contentType(ContentType.JSON)
            .body("""{"name": "Hacked", "description": ""}""")
            .`when`()
            .put("/api/v1/boards/${board.id}")
            .then()
            .statusCode(403)
    }

    @Test
    fun `Given an unknown board id, Then updating it returns 404`() {
        // Given
        val auth = createAuthenticatedUser()

        // When / Then
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body("""{"name": "Ghost", "description": ""}""")
            .`when`()
            .put("/api/v1/boards/${UUID.randomUUID()}")
            .then()
            .statusCode(404)
    }

    @Test
    fun `Given another user's board, Then deleting it returns 403`() {
        // Given
        val owner = createAuthenticatedUser()
        val attacker = createAuthenticatedUser()
        val board = boardCreator.create(author = owner.user, name = "Private", description = "")

        // When / Then
        given().authenticatedAs(attacker).`when`().delete("/api/v1/boards/${board.id}").then().statusCode(403)
    }

    @Test
    fun `Given an unknown board id, Then deleting it returns 404`() {
        // Given
        val auth = createAuthenticatedUser()

        // When / Then
        given().authenticatedAs(auth).`when`().delete("/api/v1/boards/${UUID.randomUUID()}").then().statusCode(404)
    }

    // --- Cover ---

    /** A pin holding a media, all a cover asks of it. */
    private fun imagedPin(auth: AuthenticatedUser): Pin {
        val pin = pinCreator.createPin(auth.user, HttpUrl.parse("https://example.com"), null, "Pin", emptyList())
        mediaRepository.save(
            Media.StillImage(
                id = UUID.randomUUID(),
                pinId = pin.id,
                mimeType = "image/png",
                width = 1,
                height = 1,
                byteSize = 1,
                contentHash = "hash-${pin.id}",
                storageKey = "originals/x/${pin.id}/i.png",
                createdAt = Instant.EPOCH,
            )
        )
        return pin
    }

    private fun mediaUrlOf(pin: Pin) = "/api/v1/pins/${pin.id}/media"

    @Test
    fun `Given an imaged pin in the body, Then creating a board answers its media as coverUrl`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = imagedPin(auth)

        // When / Then
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body(mapOf("name" to "Trip", "description" to "", "pinIds" to listOf(pin.id.toString())))
            .`when`()
            .post("/api/v1/boards")
            .then()
            .statusCode(201)
            .body("coverUrl", equalTo(mediaUrlOf(pin)))
    }

    @Test
    fun `Given a board holding an imaged pin, Then GET by id answers its media as coverUrl`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = imagedPin(auth)
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "", pinIds = listOf(pin.id))

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards/${board.id}")
            .then()
            .statusCode(200)
            .body("coverUrl", equalTo(mediaUrlOf(pin)))
    }

    @Test
    fun `Given a board holding an imaged pin, Then updating it answers its media as coverUrl`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = imagedPin(auth)
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "", pinIds = listOf(pin.id))

        // When / Then
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body("""{"name": "Journey", "description": ""}""")
            .`when`()
            .put("/api/v1/boards/${board.id}")
            .then()
            .statusCode(200)
            .body("coverUrl", equalTo(mediaUrlOf(pin)))
    }

    @Test
    fun `Given a covered board and an empty one, Then listing answers the cover and a present null coverUrl`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = imagedPin(auth)
        boardCreator.create(author = auth.user, name = "Covered", description = "", pinIds = listOf(pin.id))
        boardCreator.create(author = auth.user, name = "Empty", description = "")

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/boards")
            .then()
            .statusCode(200)
            .body("boards.find { it.name == 'Covered' }.coverUrl", equalTo(mediaUrlOf(pin)))
            .body("boards.find { it.name == 'Empty' }", hasKey("coverUrl"))
            .body("boards.find { it.name == 'Empty' }.coverUrl", nullValue())
    }

    @Test
    fun `Given a recycled board holding an imaged pin, Then restoring it answers its media as coverUrl`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = imagedPin(auth)
        val board = boardCreator.create(author = auth.user, name = "Trip", description = "", pinIds = listOf(pin.id))
        given().authenticatedAs(auth).delete("/api/v1/boards/${board.id}").then().statusCode(204)

        // When / Then
        given()
            .authenticatedAs(auth)
            .`when`()
            .post("/api/v1/boards/recycled/${board.id}/restore")
            .then()
            .statusCode(200)
            .body("coverUrl", equalTo(mediaUrlOf(pin)))
    }
}
