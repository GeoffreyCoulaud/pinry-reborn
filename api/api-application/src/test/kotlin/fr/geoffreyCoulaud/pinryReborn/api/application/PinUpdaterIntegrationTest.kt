package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinIdsInputDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.BoardCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinRecycleBin
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

    @Test
    fun `Given a blank tag, Then the write returns 400`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = createPin(auth)

        // When / Then
        update(auth, pin, tags = listOf(" ")).statusCode(400)
    }

    @Test
    fun `Given a null among the board ids, Then the write returns 400`() {
        // Given
        val auth = createAuthenticatedUser()
        val pin = createPin(auth)

        // When / Then
        update(auth, pin, boardIds = listOf(null)).statusCode(400).body("code", equalTo("MALFORMED_BODY"))
    }

    @Suppress("LongParameterList") // The whole pin, which is what the route under test writes.
    private fun update(
        auth: AuthenticatedUser,
        pin: Pin,
        description: String = "A pin",
        sourceContextUrl: String? = "https://example.com/page",
        sourceMediaUrl: String? = "https://example.com/img.jpg",
        tags: List<String> = emptyList(),
        boardIds: List<UUID?> = emptyList(),
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
                    "boardIds" to boardIds.map { it?.toString() },
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

    // ==================== Merge ====================

    @Inject
    lateinit var duplicateRepository: PinDuplicateRepositoryInterface

    @Inject
    lateinit var pinRecycleBin: PinRecycleBin

    private fun merge(auth: AuthenticatedUser, kept: UUID, absorbed: List<UUID>): ValidatableResponse =
        mergeBody(auth, mapOf("keptPinId" to "$kept", "absorbedPinIds" to absorbed.map { "$it" }))

    private fun mergeBody(auth: AuthenticatedUser, body: Map<String, Any>): ValidatableResponse =
        given().authenticatedAs(auth).contentType(ContentType.JSON).body(body).post("/api/v1/pins/merges").then()

    // Each pin as its owner reads it, so a refused merge is shown to have written nothing.
    private fun bodiesOf(vararg pins: Pair<AuthenticatedUser, Pin>): List<String> =
        pins.map { (auth, pin) -> readPin(auth, pin).extract().asString() }

    private fun pairCountOf(pin: Pin): Int =
        QPinDuplicateModel().or().firstPinId.equalTo(pin.id).secondPinId.equalTo(pin.id).endOr().findCount()

    @Test
    fun `Given three pins merged, Then the kept one gains their boards, tags and description, and they are recycled`() {
        // Given: a blank kept pin paired with the first absorbed, itself paired with an outsider
        val auth = createAuthenticatedUser()
        val (keptBoard, sharedBoard, absorbedBoard) = listOf("Kept", "Shared", "Absorbed")
            .map { boardCreator.create(author = auth.user, name = it, description = "") }
        val kept = createPin(auth, tags = listOf("kept", "shared"))
        replacePin(auth, kept.copy(description = ""), boardIds = listOf(keptBoard.id, sharedBoard.id))
        val blank = createPin(auth, tags = listOf("shared"))
        replacePin(auth, blank.copy(description = " "), boardIds = listOf(sharedBoard.id))
        val filled = createPin(auth, tags = listOf("absorbed"))
        val filledCopy = filled.copy(description = "Filled", sourceMediaUrl = "https://example.com/filled.jpg")
        replacePin(auth, filledCopy, boardIds = listOf(absorbedBoard.id))
        val outsider = createPin(auth)
        duplicateRepository.addMissing(kept.id, listOf(blank.id))
        duplicateRepository.addMissing(blank.id, listOf(outsider.id))

        // When
        val merged = merge(auth, kept.id, listOf(blank.id, filled.id)).statusCode(200).extract().jsonPath()
        val recycledAt = listOf(blank, filled).map { readPin(auth, it).extract().path<String?>("softDeletedAt") }
        val listed = given().authenticatedAs(auth).get("/api/v1/pins/${kept.id}/duplicates")
            .then().statusCode(200).extract().path<List<Any>>("duplicates")

        // Then: the kept pin's one pair is hidden, and the outsider's is not carried over to it
        assertEquals(listOf("${kept.id}", "Filled", kept.sourceMediaUrl, false),
            listOf("id", "description", "sourceMediaUrl", "hasPendingDuplicates").map { merged.get<Any?>(it) })
        assertEquals(setOf("kept", "shared", "absorbed"), merged.getList<String>("tags.name").toSet())
        assertEquals(listOf(keptBoard, sharedBoard, absorbedBoard).map { "${it.id}" }.toSet(),
            merged.getList<String>("boards.id").toSet())
        assertNotNull(recycledAt.first())
        assertEquals(recycledAt.first(), recycledAt.last())
        assertEquals(emptyList<Any>(), listed)
        assertEquals(1, pairCountOf(kept))
    }

    @Test
    fun `Given an empty list, a repeated pin, the kept pin absorbed or no body field, Then the merge returns 400`() {
        // Given
        val auth = createAuthenticatedUser()
        val (kept, absorbed) = List(2) { createPin(auth) }
        val before = bodiesOf(auth to kept, auth to absorbed)

        // When, Then
        merge(auth, kept.id, emptyList()).statusCode(400).body("code", equalTo("VALIDATION_ERROR"))
        merge(auth, kept.id, listOf(absorbed.id, absorbed.id)).statusCode(400).body("code", equalTo("VALIDATION_ERROR"))
        merge(auth, kept.id, listOf(absorbed.id, kept.id)).statusCode(400).body("code", equalTo("VALIDATION_ERROR"))
        mergeBody(auth, mapOf("absorbedPinIds" to listOf("${absorbed.id}"))).statusCode(400)
        mergeBody(auth, mapOf("keptPinId" to "${kept.id}")).statusCode(400)
        assertEquals(before, bodiesOf(auth to kept, auth to absorbed))
    }

    @Test
    fun `Given another user's pin last in the list, Then the merge returns 403 and changes no pin`() {
        // Given
        val auth = createAuthenticatedUser()
        val other = createAuthenticatedUser()
        val (kept, absorbed) = List(2) { createPin(auth) }
        val foreign = createPin(other)
        val before = bodiesOf(auth to kept, auth to absorbed, other to foreign)

        // When
        merge(auth, kept.id, listOf(absorbed.id, foreign.id))
            .statusCode(403).body("code", equalTo("PIN_INSUFFICIENT_PERMISSIONS"))

        // Then
        assertEquals(before, bodiesOf(auth to kept, auth to absorbed, other to foreign))
    }

    @Test
    fun `Given an unknown pin last in the list, Then the merge returns 404 and changes no pin`() {
        // Given
        val auth = createAuthenticatedUser()
        val (kept, absorbed) = List(2) { createPin(auth) }
        val before = bodiesOf(auth to kept, auth to absorbed)

        // When
        merge(auth, kept.id, listOf(absorbed.id, UUID.randomUUID()))
            .statusCode(404).body("code", equalTo("PIN_DOES_NOT_EXIST"))

        // Then
        assertEquals(before, bodiesOf(auth to kept, auth to absorbed))
    }

    @Test
    fun `Given a recycled pin last in the list, Then the merge returns 409 and changes no pin`() {
        // Given
        val auth = createAuthenticatedUser()
        val (kept, absorbed, recycled) = List(3) { createPin(auth) }
        pinRecycleBin.softDelete(recycled.id, auth.user)
        val before = bodiesOf(auth to kept, auth to absorbed, auth to recycled)

        // When
        merge(auth, kept.id, listOf(absorbed.id, recycled.id))
            .statusCode(409).body("code", equalTo("PIN_ALREADY_SOFT_DELETED"))

        // Then
        assertEquals(before, bodiesOf(auth to kept, auth to absorbed, auth to recycled))
    }

    // ==================== Resolution ====================

    private fun postResolution(
        auth: AuthenticatedUser,
        openPinId: UUID,
        decisions: Map<String, String?>,
    ): ValidatableResponse =
        given().authenticatedAs(auth).contentType(ContentType.JSON).body(mapOf("decisions" to decisions))
            .post("/api/v1/pins/$openPinId/duplicates/resolutions").then()

    // The open pin first, paired with each of the others.
    private fun createPinsPairedWithTheFirst(auth: AuthenticatedUser, size: Int): List<Pin> {
        val pins = List(size) { createPin(auth) }
        duplicateRepository.addMissing(pins.first().id, pins.drop(1).map { it.id })
        return pins
    }

    // Each pin as its owner reads it, and every pair with its rejection.
    private fun readPinsAndEveryPair(vararg pins: Pair<AuthenticatedUser, Pin>): List<Any> =
        bodiesOf(*pins) + QPinDuplicateModel().findList().map { setOf(it.firstPinId, it.secondPinId) to it.rejectedAt }

    // A set: pins created within one clock tick tie on the list's order.
    private fun listDuplicatesWithRejection(auth: AuthenticatedUser, pin: Pin): Set<Pair<String, Boolean>> {
        val body = given().authenticatedAs(auth).get("/api/v1/pins/${pin.id}/duplicates")
            .then().statusCode(200).extract().jsonPath()
        return body.getList<String>("duplicates.pin.id").zip(body.getList<Boolean>("duplicates.rejected")).toSet()
    }

    @Test
    fun `Given an unreadable or an invalid decision, Then the resolution returns 400 and changes nothing`() {
        // Given
        val auth = createAuthenticatedUser()
        val pins = createPinsPairedWithTheFirst(auth, 3).map { auth to it }.toTypedArray()
        val (openId, other, third) = pins.map { "${it.second.id}" }
        val before = readPinsAndEveryPair(*pins)
        val malformed = listOf(
            mapOf("not-a-pin" to "KEEP", other to "MERGE"),
            mapOf(openId to "KEEP", other to "DROP"),
            mapOf(openId to "KEEP", other to null),
        )
        val invalid = listOf(
            mapOf(openId to "MERGE", other to "MERGE"),
            mapOf(openId to "KEEP", other to "KEEP"),
            mapOf(other to "KEEP", third to "MERGE"),
            mapOf(openId to "REJECT", other to "KEEP"),
            mapOf(openId to "KEEP"),
            List(PinIdsInputDto.MAX_IDENTIFIERS) { "${UUID.randomUUID()}" to "MERGE" }.toMap() + (openId to "KEEP"),
        )

        // When, Then
        val pinId = UUID.fromString(openId)
        malformed.forEach { postResolution(auth, pinId, it).statusCode(400).body("code", equalTo("MALFORMED_BODY")) }
        invalid.forEach { postResolution(auth, pinId, it).statusCode(400).body("code", equalTo("VALIDATION_ERROR")) }
        assertEquals(before, readPinsAndEveryPair(*pins))
    }

    @Test
    fun `Given a foreign, an unknown or a recycled open pin, Then the resolution refuses each and changes nothing`() {
        // Given: each open pin paired with a candidate of its owner's
        val auth = createAuthenticatedUser()
        val foreignAuth = createAuthenticatedUser()
        val (foreign, foreignCandidate) = createPinsPairedWithTheFirst(foreignAuth, 2)
        val (recycled, candidate) = createPinsPairedWithTheFirst(auth, 2)
        pinRecycleBin.softDelete(recycled.id, auth.user)
        val pins = arrayOf(foreignAuth to foreign, foreignAuth to foreignCandidate, auth to recycled, auth to candidate)
        val before = readPinsAndEveryPair(*pins)
        val refuse = { id: UUID, other: Pin ->
            postResolution(auth, id, mapOf("$id" to "KEEP", "${other.id}" to "MERGE"))
        }

        // When, Then
        refuse(foreign.id, foreignCandidate).statusCode(403).body("code", equalTo("PIN_INSUFFICIENT_PERMISSIONS"))
        refuse(UUID.randomUUID(), candidate).statusCode(404).body("code", equalTo("PIN_DOES_NOT_EXIST"))
        refuse(recycled.id, candidate).statusCode(409).body("code", equalTo("PIN_ALREADY_SOFT_DELETED"))
        assertEquals(before, readPinsAndEveryPair(*pins))
    }

    @Test
    fun `Given a named pin unpaired, unknown, foreign or recycled, Then the resolution refuses it, changing nothing`() {
        // Given: the recycled candidate is paired with the open pin, which hides the pair
        val auth = createAuthenticatedUser()
        val foreignAuth = createAuthenticatedUser()
        val (openPin, candidate, recycled) = createPinsPairedWithTheFirst(auth, 3)
        pinRecycleBin.softDelete(recycled.id, auth.user)
        val unpaired = createPin(auth)
        val foreign = createPin(foreignAuth)
        val ownPins = listOf(openPin, candidate, recycled, unpaired).map { auth to it }
        val pins = (ownPins + (foreignAuth to foreign)).toTypedArray()
        val before = readPinsAndEveryPair(*pins)

        // When, Then
        listOf(unpaired.id, UUID.randomUUID(), foreign.id, recycled.id).forEach {
            val decisions = mapOf("${openPin.id}" to "KEEP", "${candidate.id}" to "MERGE", "$it" to "REJECT")
            postResolution(auth, openPin.id, decisions)
                .statusCode(404).body("code", equalTo("DUPLICATE_DOES_NOT_EXIST"))
        }
        assertEquals(before, readPinsAndEveryPair(*pins))
    }

    @Test
    fun `Given the open pin merged into a candidate with a third, Then a fourth stays rejected against the kept pin`() {
        // Given: the open pin paired with the three others, and the rejected one with the kept one too
        val auth = createAuthenticatedUser()
        val (openBoard, mergedBoard) = listOf("Open", "Merged")
            .map { boardCreator.create(author = auth.user, name = it, description = "") }
        val pins = createPinsPairedWithTheFirst(auth, 4)
        val (openPin, kept, merged) = pins
        val rejected = pins.last()
        replacePin(auth, openPin, tags = listOf("open"), boardIds = listOf(openBoard.id))
        replacePin(auth, kept, tags = listOf("kept"))
        replacePin(auth, merged, tags = listOf("merged"), boardIds = listOf(mergedBoard.id))
        duplicateRepository.addMissing(rejected.id, listOf(kept.id))
        val decisions = mapOf(openPin to "MERGE", kept to "KEEP", merged to "MERGE", rejected to "REJECT")

        // When
        val answered = postResolution(auth, openPin.id, decisions.mapKeys { "${it.key.id}" })
            .statusCode(200).extract().jsonPath()
        val recycledAt = listOf(openPin, merged).map { readPin(auth, it).extract().path<String?>("softDeletedAt") }
        val keptDuplicates = listDuplicatesWithRejection(auth, kept)
        pinRecycleBin.restore(openPin.id, auth.user)

        // Then
        assertEquals("${kept.id}", answered.getString("id"))
        assertEquals(setOf("open", "kept", "merged"), answered.getList<String>("tags.name").toSet())
        assertEquals(setOf("${openBoard.id}", "${mergedBoard.id}"), answered.getList<String>("boards.id").toSet())
        assertNotNull(recycledAt.first())
        assertEquals(recycledAt.first(), recycledAt.last())
        assertEquals(setOf("${rejected.id}" to true), keptDuplicates)
        assertEquals(setOf("${kept.id}" to false, "${rejected.id}" to true), listDuplicatesWithRejection(auth, openPin))
    }

    @Test
    fun `Given one of two candidates rejected alone, Then its pair is rejected, the other's pending, none recycled`() {
        // Given
        val auth = createAuthenticatedUser()
        val (openPin, rejected, unnamed) = createPinsPairedWithTheFirst(auth, 3)

        // When
        val answered = postResolution(auth, openPin.id, mapOf("${openPin.id}" to "KEEP", "${rejected.id}" to "REJECT"))
            .statusCode(200).extract().path<String>("id")

        // Then
        assertEquals("${openPin.id}", answered)
        val listed = listDuplicatesWithRejection(auth, openPin)
        assertEquals(setOf("${rejected.id}" to true, "${unnamed.id}" to false), listed)
        listOf(openPin, rejected, unnamed).forEach { readPin(auth, it).body("softDeletedAt", nullValue()) }
    }
}
