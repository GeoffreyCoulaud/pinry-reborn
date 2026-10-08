package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.controllers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDirectionDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common.CursorDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PersonInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinCreationInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinSortStrategyInputEnum
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input.PinUpdateInputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinResponses
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonReference
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicates
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinGetter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinUpdater
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ResolvePinMediaState
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.mockk.every
import io.mockk.mockk
import io.quarkus.security.identity.SecurityIdentity
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PinControllerTest {
    private val pinCreator = mockk<PinCreator>()
    private val pinGetter = mockk<PinGetter>()
    private val pinUpdater = mockk<PinUpdater>()
    private val securityIdentity = mockk<SecurityIdentity>()
    // The real assembler over a stubbed resolver: the responses under assertion are the mapped ones.
    private val resolvePinMediaState =
        mockk<ResolvePinMediaState>().also {
            every { it.statesFor(any()) } returns emptyMap()
        }
    private val pinDuplicates = mockk<PinDuplicates>().also { every { it.pendingAmong(any()) } returns emptySet() }
    private val controller =
        PinController(
            pinCreator = pinCreator,
            pinGetter = pinGetter,
            pinRecycleBin = mockk(),
            pinUpdater = pinUpdater,
            securityIdentity = securityIdentity,
            pinResponses = PinResponses(resolvePinMediaState, pinDuplicates),
        )

    /** Creates a pin through the controller and answers what the created pin actually carries. */
    private fun createPinWith(
        sourceMediaUrl: String? = null,
        sourceContextUrl: String? = "https://example.test/page",
    ): PinOutputDto {
        val user = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            pinCreator.createPin(
                author = user,
                sourceContextUrl = any(),
                sourceMediaUrl = any(),
                description = any(),
                tags = any(),
            )
        } answers
            {
                Pin(
                    id = randomUUID(),
                    author = user,
                    sourceContextUrl = arg(1),
                    sourceMediaUrl = arg(2),
                    description = arg(3),
                    tags = emptyList(),
                    boards = emptyList(),
                    createdAt = TestTime.now,
                    updatedAt = TestTime.now,
                )
            }
        val dto =
            PinCreationInputDto(
                sourceContextUrl = sourceContextUrl,
                sourceMediaUrl = sourceMediaUrl,
                description = createRandomString(),
            )

        return controller.createPin(dto).entity
    }

    @Test
    fun `Given no source media url, Then the created pin carries none`() {
        assertNull(createPinWith(null).sourceMediaUrl)
    }

    @Test
    fun `Given a blank source media url, Then the created pin carries none`() {
        assertNull(createPinWith("   ").sourceMediaUrl)
    }

    @Test
    fun `Given a source media url, Then the created pin carries it`() {
        assertEquals("https://example.test/i.png", createPinWith("https://example.test/i.png").sourceMediaUrl)
    }

    @Test
    fun `Given no source page url, Then the created pin carries none`() {
        assertNull(createPinWith(sourceContextUrl = null).sourceContextUrl)
    }

    @Test
    fun `Given a blank source page url, Then the created pin carries none`() {
        assertNull(createPinWith(sourceContextUrl = "   ").sourceContextUrl)
    }

    @Test
    fun `Given a source page url, Then the created pin carries it`() {
        assertEquals("https://example.test/page", createPinWith().sourceContextUrl)
    }

    /** Writes a pin through the controller, the use case answering only for the people it expects. */
    private fun updatePinWith(publisher: PersonInputDto?, expectedPublisher: PersonReference?): Int {
        val user = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val pin =
            Pin(
                id = randomUUID(),
                author = user,
                sourceContextUrl = null,
                sourceMediaUrl = null,
                description = "",
                tags = emptyList(),
                boards = emptyList(),
                createdAt = TestTime.now,
                updatedAt = TestTime.now,
            )
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            pinUpdater.update(
                pinId = pin.id,
                description = any(),
                sourceContextUrl = any(),
                sourceMediaUrl = any(),
                tagNames = any(),
                boardIds = any(),
                publisher = expectedPublisher,
                creators = listOf(PersonReference(name = "Bob", urls = emptyList())),
                publishedAt = null,
                user = user,
            )
        } returns pin
        val dto =
            PinUpdateInputDto(
                sourceContextUrl = null,
                sourceMediaUrl = null,
                description = "",
                tags = emptyList(),
                boardIds = emptyList(),
                publisher = publisher,
                creators = listOf(PersonInputDto(name = "Bob", urls = emptyList())),
                publishedAt = null,
            )

        return controller.updatePin(pin.id, dto).status
    }

    @Test
    fun `Given a publisher and a creator, Then updatePin hands both to the use case by name and addresses`() {
        val publisher = PersonInputDto(name = "Alice", urls = listOf("https://alice.test"))

        assertEquals(
            200,
            updatePinWith(publisher, PersonReference(name = "Alice", urls = listOf("https://alice.test"))),
        )
    }

    @Test
    fun `Given no publisher, Then updatePin hands the use case none`() {
        assertEquals(200, updatePinWith(publisher = null, expectedPublisher = null))
    }

    @Test
    fun `Given no cursor, no page size and no sort, Then listPins uses defaults`() {
        // Given
        val user = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val page = Page<Pin>(items = emptyList(), previousCursor = null, nextCursor = null)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            pinGetter.listPinsPaginatedForUser(
                reader = user,
                cursor = null,
                pageSize = PinController.DEFAULT_PAGE_SIZE,
                sort = PinSortStrategy.CREATED_AT_ASC,
            )
        } returns page

        // When
        val response = controller.listPins(cursorInput = null, pageSizeInput = null, sortInput = null)

        // Then
        assertEquals(200, response.status)
    }

    @Test
    fun `Given cursor, page size and sort provided, Then listPins uses the provided values`() {
        // Given
        val user = User(id = randomUUID(), name = createRandomString(), createdAt = TestTime.now)
        val pivotId = randomUUID()
        val cursorInput = CursorDto(pivotId = pivotId, direction = CursorDirectionDto.FORWARD)
        val pageSizeInput = 5
        val sortInput = PinSortStrategyInputEnum.CREATED_AT_DESC
        val page = Page<Pin>(items = emptyList(), previousCursor = null, nextCursor = null)
        every { securityIdentity.getAttribute<User>("user") } returns user
        every {
            pinGetter.listPinsPaginatedForUser(
                reader = user,
                cursor = match { it.pivotId == pivotId },
                pageSize = pageSizeInput,
                sort = PinSortStrategy.CREATED_AT_DESC,
            )
        } returns page

        // When
        val response =
            controller.listPins(
                cursorInput = cursorInput,
                pageSizeInput = pageSizeInput,
                sortInput = sortInput,
            )

        // Then
        assertEquals(200, response.status)
    }
}
