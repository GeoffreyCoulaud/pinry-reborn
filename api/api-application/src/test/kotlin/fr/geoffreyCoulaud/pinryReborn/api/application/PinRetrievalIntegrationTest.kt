package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinRecycleBin
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import java.time.Instant
import java.util.UUID
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.emptyIterable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@QuarkusTest
class PinRetrievalIntegrationTest : IntegrationTest() {

    @Inject lateinit var pinCreator: PinCreator

    // ==================== Simple Scenarios ====================

    @Test
    fun `retrieving own pin returns the pin`() {
        val auth = createAuthenticatedUser()

        val pin =
            pinCreator.createPin(
                author = auth.user,
                sourceContextUrl = "https://example.com/page",
                sourceMediaUrl = "https://example.com/media.jpg",
                description = "My pin",
                tags = emptyList(),
            )

        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/pins/${pin.id}")
            .then()
            .statusCode(200)
            .body("id", equalTo(pin.id.toString()))
            .body("authorId", equalTo(auth.user.id.toString()))
            .body("sourceContextUrl", equalTo("https://example.com/page"))
            .body("sourceMediaUrl", equalTo("https://example.com/media.jpg"))
            .body("description", equalTo("My pin"))
            .body("tags", emptyIterable<Any>())
    }

    @Test
    fun `retrieving pin returns correct authorId`() {
        val auth = createAuthenticatedUser()

        val pin =
            pinCreator.createPin(
                author = auth.user,
                sourceContextUrl = "https://author.com",
                sourceMediaUrl = "https://author.com/img.jpg",
                description = "Author test",
                tags = emptyList(),
            )

        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/pins/${pin.id}")
            .then()
            .statusCode(200)
            .body("authorId", equalTo(auth.user.id.toString()))
    }

    // ==================== Complex Scenarios ====================

    @Test
    fun `retrieving non-existent pin returns 404`() {
        val auth = createAuthenticatedUser()

        val nonExistentPinId = UUID.randomUUID()

        given().authenticatedAs(auth).`when`().get("/api/v1/pins/$nonExistentPinId").then().statusCode(404)
    }

    @Test
    fun `retrieving pin without authentication returns 401`() {
        val auth = createAuthenticatedUser()

        val pin =
            pinCreator.createPin(
                author = auth.user,
                sourceContextUrl = "https://unauth.com",
                sourceMediaUrl = "https://unauth.com/img.jpg",
                description = "Unauth test",
                tags = emptyList(),
            )

        given().`when`().get("/api/v1/pins/${pin.id}").then().statusCode(401)
    }

    @Test
    fun `retrieving another user's pin returns 403 forbidden`() {
        // Create two users
        val owner = createAuthenticatedUser()
        val reader = createAuthenticatedUser()

        // Create pin owned by owner
        val pin =
            pinCreator.createPin(
                author = owner.user,
                sourceContextUrl = "https://owned.com",
                sourceMediaUrl = "https://owned.com/img.jpg",
                description = "User1's pin",
                tags = emptyList(),
            )

        // Try to retrieve owner's pin as reader
        given().authenticatedAs(reader).`when`().get("/api/v1/pins/${pin.id}").then().statusCode(403)
    }

    @Test
    fun `user can retrieve only their own pins from multiple pins`() {
        val auth1 = createAuthenticatedUser()
        val auth2 = createAuthenticatedUser()

        // Create pin for user1
        val pin1 =
            pinCreator.createPin(
                author = auth1.user,
                sourceContextUrl = "https://user1.com",
                sourceMediaUrl = "https://user1.com/img.jpg",
                description = "User1's pin",
                tags = emptyList(),
            )

        // Create pin for user2
        val pin2 =
            pinCreator.createPin(
                author = auth2.user,
                sourceContextUrl = "https://user2.com",
                sourceMediaUrl = "https://user2.com/img.jpg",
                description = "User2's pin",
                tags = emptyList(),
            )

        // User1 can access their own pin
        given()
            .authenticatedAs(auth1)
            .`when`()
            .get("/api/v1/pins/${pin1.id}")
            .then()
            .statusCode(200)
            .body("description", equalTo("User1's pin"))

        // User1 cannot access user2's pin
        given().authenticatedAs(auth1).`when`().get("/api/v1/pins/${pin2.id}").then().statusCode(403)

        // User2 can access their own pin
        given()
            .authenticatedAs(auth2)
            .`when`()
            .get("/api/v1/pins/${pin2.id}")
            .then()
            .statusCode(200)
            .body("description", equalTo("User2's pin"))

        // User2 cannot access user1's pin
        given().authenticatedAs(auth2).`when`().get("/api/v1/pins/${pin1.id}").then().statusCode(403)
    }

    @Test
    fun `retrieving pin with wrong password returns 401`() {
        val auth = createAuthenticatedUser()

        val pin =
            pinCreator.createPin(
                author = auth.user,
                sourceContextUrl = "https://wrongpass.com",
                sourceMediaUrl = "https://wrongpass.com/img.jpg",
                description = "Wrong pass test",
                tags = emptyList(),
            )

        // A tampered / invalid bearer token is the Bearer equivalent of a wrong per-request credential
        given()
            .header("Authorization", "Bearer ${auth.token}tampered")
            .`when`()
            .get("/api/v1/pins/${pin.id}")
            .then()
            .statusCode(401)
    }

    @Test
    fun `user can retrieve multiple pins they created`() {
        val auth = createAuthenticatedUser()

        val pin1 =
            pinCreator.createPin(
                author = auth.user,
                sourceContextUrl = "https://multi1.com",
                sourceMediaUrl = "https://multi1.com/img.jpg",
                description = "First pin",
                tags = emptyList(),
            )

        val pin2 =
            pinCreator.createPin(
                author = auth.user,
                sourceContextUrl = "https://multi2.com",
                sourceMediaUrl = "https://multi2.com/img.jpg",
                description = "Second pin",
                tags = emptyList(),
            )

        // Retrieve first pin
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/pins/${pin1.id}")
            .then()
            .statusCode(200)
            .body("description", equalTo("First pin"))

        // Retrieve second pin
        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/pins/${pin2.id}")
            .then()
            .statusCode(200)
            .body("description", equalTo("Second pin"))
    }

    @Test
    fun `retrieving pin with invalid UUID format returns error`() {
        val auth = createAuthenticatedUser()

        given()
            .authenticatedAs(auth)
            .`when`()
            .get("/api/v1/pins/not-a-valid-uuid")
            .then()
            .statusCode(404) // Invalid UUID format results in 404
    }

    // ==================== Duplicates ====================

    @Inject lateinit var duplicateRepository: PinDuplicateRepositoryInterface

    @Inject lateinit var pinRecycleBin: PinRecycleBin

    private fun pinsOf(auth: AuthenticatedUser, count: Int): List<Pin> =
        List(count) {
            pinCreator.createPin(
                author = auth.user,
                sourceContextUrl = null,
                sourceMediaUrl = null,
                description = "",
                tags = emptyList(),
            )
        }

    private fun flagOf(auth: AuthenticatedUser, pin: Pin): Boolean =
        given()
            .authenticatedAs(auth)
            .get("/api/v1/pins/${pin.id}")
            .then()
            .statusCode(200)
            .extract()
            .path("hasPendingDuplicates")

    @Test
    fun `Given a pending pair, Then both pins flag it until it is rejected or either is recycled`() {
        // Given
        val auth = createAuthenticatedUser()
        val (pin, other, unpaired) = pinsOf(auth, 3)
        val (kept, recycled) = pinsOf(auth, 2)
        duplicateRepository.addMissing(pin.id, listOf(other.id))
        duplicateRepository.addMissing(kept.id, listOf(recycled.id))

        // When
        val page = given().authenticatedAs(auth).get("/api/v1/pins").then().statusCode(200).extract().jsonPath()
        val pendingFlags = page.getList<String>("pins.id").zip(page.getList<Boolean>("pins.hasPendingDuplicates"))
        QPinDuplicateModel().firstPinId.isIn(pin.id, other.id).asUpdate().set("rejectedAt", Instant.EPOCH).update()
        pinRecycleBin.softDelete(recycled.id, auth.user)
        val laterFlags = listOf(pin, other, kept, recycled).map { flagOf(auth, it) }

        // Then
        val expectedFlags = mapOf(pin to true, other to true, unpaired to false, kept to true, recycled to true)
        assertEquals(expectedFlags.mapKeys { "${it.key.id}" }, pendingFlags.toMap())
        assertEquals(listOf(false, false, false, false), laterFlags)
    }

    // Each listed duplicate as its pin's id and whether it is rejected.
    private fun duplicatesOf(auth: AuthenticatedUser, pin: Pin): List<Pair<String, Boolean>> {
        val body =
            given()
                .authenticatedAs(auth)
                .get("/api/v1/pins/${pin.id}/duplicates")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
        return body.getList<String>("duplicates.pin.id").zip(body.getList<Boolean>("duplicates.rejected"))
    }

    @Test
    fun `Given a pair, Then either side lists it, and a rejection from one side shows on the other`() {
        // Given
        val auth = createAuthenticatedUser()
        val (pin, other) = pinsOf(auth, 2)
        duplicateRepository.addMissing(pin.id, listOf(other.id))

        // When
        val listedPending = listOf(duplicatesOf(auth, pin), duplicatesOf(auth, other))
        given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body(mapOf("decisions" to mapOf("${pin.id}" to "KEEP", "${other.id}" to "REJECT")))
            .post("/api/v1/pins/${pin.id}/duplicates/resolutions")
            .then()
            .statusCode(200)

        // Then
        assertEquals(listOf(listOf("${other.id}" to false), listOf("${pin.id}" to false)), listedPending)
        assertEquals(listOf("${pin.id}" to true), duplicatesOf(auth, other))
    }

    @Test
    fun `Given a pair one of whose pins is recycled, Then neither lists it until the pin is restored`() {
        // Given
        val auth = createAuthenticatedUser()
        val (pin, recycled) = pinsOf(auth, 2)
        duplicateRepository.addMissing(pin.id, listOf(recycled.id))
        pinRecycleBin.softDelete(recycled.id, auth.user)

        // When
        val listed = listOf(duplicatesOf(auth, pin), duplicatesOf(auth, recycled))
        pinRecycleBin.restore(recycled.id, auth.user)

        // Then
        assertEquals(listOf(emptyList<Pair<String, Boolean>>(), emptyList()), listed)
        assertEquals(listOf("${recycled.id}" to false), duplicatesOf(auth, pin))
    }

    @Test
    fun `Given an unknown or a foreign pin, Then its duplicates are refused`() {
        // Given
        val auth = createAuthenticatedUser()
        val (foreign) = pinsOf(createAuthenticatedUser(), 1)

        // When, Then
        given()
            .authenticatedAs(auth)
            .get("/api/v1/pins/${UUID.randomUUID()}/duplicates")
            .then()
            .statusCode(404)
            .body("code", equalTo("PIN_DOES_NOT_EXIST"))
        given()
            .authenticatedAs(auth)
            .get("/api/v1/pins/${foreign.id}/duplicates")
            .then()
            .statusCode(403)
            .body("code", equalTo("PIN_INSUFFICIENT_PERMISSIONS"))
    }
}
