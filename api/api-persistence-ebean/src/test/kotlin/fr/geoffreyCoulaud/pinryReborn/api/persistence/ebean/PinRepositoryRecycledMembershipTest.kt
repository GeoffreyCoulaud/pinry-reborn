package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.BoardRepository
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `getBoardsForPin` deliberately filters out recycled boards from the API view, while `softDeleteBoard` keeps the join
 * row. The export needs the unfiltered read; this suite pins both halves of that contract, split from
 * `PinRepositoryTest` to keep it under detekt's `LargeClass` threshold (mirrors `PinRepositoryPaginationTest`'s
 * precedent for the same split).
 *
 * Its board is inserted as a raw model by the shared fixtures rather than through `BoardRepository.saveBoard`, which is
 * `merge` and not `save`: the arrangement needs an active row and its id, not the production write.
 */
class PinRepositoryRecycledMembershipTest : PinRepositoryFixtures() {
    private val boardRepository = BoardRepository(persistor)

    private fun createAndSavePinInBoard(
        user: User,
        board: Board,
    ): Pin =
        repository.savePin(
            Pin(
                id = randomUUID(),
                author = user,
                sourceContextUrl = HttpUrl.parse("https://example.com"),
                sourceMediaUrl = HttpUrl.parse("https://example.com/media.jpeg"),
                description = "Something",
                tags = emptyList(),
                boards = listOf(board),
                createdAt = storableNow(),
                updatedAt = storableNow(),
            )
        )

    @Test
    fun `Given a pin in a recycled board, Then the export membership read still sees it`() {
        // Given
        val user = createAndSaveUser()
        val board = createAndSaveBoard(user)
        val pin = createAndSavePinInBoard(user, board)
        boardRepository.softDeleteBoard(board, storableNow())

        // When
        val boards = repository.findBoardsForPinIncludingRecycled(pin.id)

        // Then
        assertEquals(listOf(board.id), boards.map { it.id })
        assertTrue(repository.findPinById(pin.id)!!.boards.isEmpty(), "the API view still filters")
    }
}
