package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import io.ebean.test.LoggedSql
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The bulk membership and recycle bin writes, and the bulk read that resolves their pins (spec 2026-09-27, decision H).
 * Split from `PinRepositoryTest` to keep it under detekt's `LargeClass` threshold.
 */
class PinRepositoryBulkWriteTest : PinRepositoryFixtures() {
    private val later: Instant = storableNow().plusSeconds(60)

    // --- findPinsByIds ---

    @Test
    fun `Given pins in either state, Then findPinsByIds answers each with its tags and active boards`() {
        // Given
        val user = createAndSaveUser()
        val tag = createAndSaveTag(name = "tag", user = user)
        val board = createAndSaveBoard(user)
        val recycledBoard = createAndSaveBoard(user)
        val active = repository.savePin(createAndSavePin(user).copy(tags = listOf(tag), boards = listOf(board)))
        repository.savePin(active.copy(boards = listOf(board, recycledBoard)))
        softDeleteBoardModel(recycledBoard)
        val recycled = repository.softDeletePin(createAndSavePin(user), storableNow())

        // When
        val found = repository.findPinsByIds(listOf(active.id, recycled.id, randomUUID()))

        // Then: an unknown id is simply absent, and so is the recycled board
        assertEquals(setOf(active, recycled), found.toSet())
    }

    @Test
    fun `Given no ids, Then findPinsByIds and addPinsToBoard run no statement`() {
        // Given
        val board = createAndSaveBoard(createAndSaveUser())

        // When
        LoggedSql.start()
        val found = repository.findPinsByIds(emptyList())
        repository.addPinsToBoard(emptyList(), board, later)
        val statements = LoggedSql.stop()

        // Then
        assertTrue(found.isEmpty())
        assertEquals(0, statements.size, "Expected no statement, ran ${statements.size}: $statements")
    }

    // --- addPinsToBoard ---

    @Test
    fun `Given a pin in another board and one already filed, Then addPinsToBoard files both once`() {
        // Given
        val user = createAndSaveUser()
        val other = createAndSaveBoard(user)
        val target = createAndSaveBoard(user)
        val elsewhere = repository.savePin(createAndSavePin(user).copy(boards = listOf(other)))
        val filed = repository.savePin(createAndSavePin(user).copy(boards = listOf(target)))

        // When
        repository.addPinsToBoard(listOf(elsewhere.id, filed.id), target, later)

        // Then
        assertEquals(setOf(other.id, target.id), boardIdsOf(elsewhere.id))
        assertEquals(listOf(target.id), repository.findPinById(filed.id)?.boards?.map { it.id })
        assertEquals(listOf(later, later), listOf(elsewhere.id, filed.id).map { repository.findPinById(it)?.updatedAt })
    }

    @Test
    fun `Given one pin or ten, Then addPinsToBoard reads as often and batches its updates and inserts`() {
        val ten =
            assertConstantReadsAndOneUpdateBatch(given = { user, _ -> createAndSavePin(user) }) { ids, board ->
                repository.addPinsToBoard(ids, board, later)
            }

        // Then: the join rows too, one INSERT sent once with ten rows bound
        val inserts = ten.sql().filter { "insert into pin_board_model" in it }
        assertEquals(listOf(JOIN_INSERT, "-- executeBatch() size:10 sql:$JOIN_INSERT"), inserts, "ten ran $ten")
    }

    // --- removePinsFromBoard ---

    @Test
    fun `Given pins in the target board, Then removePinsFromBoard drops that membership alone`() {
        // Given
        val user = createAndSaveUser()
        val other = createAndSaveBoard(user)
        val target = createAndSaveBoard(user)
        val subject = repository.savePin(createAndSavePin(user).copy(boards = listOf(other, target)))
        val bystander = repository.savePin(createAndSavePin(user).copy(boards = listOf(target)))

        // When
        repository.removePinsFromBoard(listOf(subject.id), target, later)

        // Then
        assertEquals(setOf(other.id), boardIdsOf(subject.id))
        assertEquals(later, repository.findPinById(subject.id)?.updatedAt)
        assertEquals(setOf(target.id), boardIdsOf(bystander.id))
    }

    @Test
    fun `Given one pin or ten, Then removePinsFromBoard reads as often and deletes in one statement`() {
        val ten =
            assertConstantReadsAndOneUpdateBatch(given = { user, board -> filedPin(user, board) }) { ids, board ->
                repository.removePinsFromBoard(ids, board, later)
            }

        // Then
        assertEquals(1, ten.sql().count { it.startsWith("delete from") }, "ten ran $ten")
    }

    // --- softDeletePins and restorePins ---

    @Test
    fun `Given active pins, Then softDeletePins recycles each and restorePins brings each back`() {
        // Given
        val user = createAndSaveUser()
        val ids = List(2) { createAndSavePin(user).id }

        // When
        repository.softDeletePins(ids, later)
        val recycled = ids.map { repository.findPinById(it) }
        repository.restorePins(ids, later.plusSeconds(1))
        val restored = ids.map { repository.findPinById(it) }

        // Then
        assertEquals(listOf(later, later), recycled.map { it?.softDeletedAt })
        assertEquals(listOf(later, later), recycled.map { it?.updatedAt })
        assertEquals(listOf(null, null), restored.map { it?.softDeletedAt })
        assertEquals(List(2) { later.plusSeconds(1) }, restored.map { it?.updatedAt })
    }

    @Test
    fun `Given one pin or ten, Then softDeletePins reads as often and batches its updates`() {
        assertConstantReadsAndOneUpdateBatch(RECYCLE_UPDATE, given = { user, _ -> createAndSavePin(user) }) { ids, _ ->
            repository.softDeletePins(ids, later)
        }
    }

    @Test
    fun `Given one pin or ten, Then restorePins reads as often and batches its updates`() {
        val recycledPin = { user: User, _: Board -> repository.softDeletePin(createAndSavePin(user), storableNow()) }
        assertConstantReadsAndOneUpdateBatch(RECYCLE_UPDATE, given = recycledPin) { ids, _ ->
            repository.restorePins(ids, later)
        }
    }

    private fun boardIdsOf(pinId: UUID): Set<UUID>? = repository.findPinById(pinId)?.boards?.map { it.id }?.toSet()

    private fun filedPin(user: User, board: Board): Pin =
        repository.savePin(createAndSavePin(user).copy(boards = listOf(board)))

    /**
     * [given] saves one pin in the state [write] expects; [update] is the one UPDATE statement expected. Answers the
     * statements of ten pins, for the caller's own assertions.
     */
    private fun assertConstantReadsAndOneUpdateBatch(
        update: String = PIN_UPDATE,
        given: (User, Board) -> Pin,
        write: (List<UUID>, Board) -> Unit,
    ): List<String> {
        // When
        val one = statementsWriting(pinCount = 1, given = given, write = write)
        val ten = statementsWriting(pinCount = 10, given = given, write = write)

        // Then: one UPDATE statement, sent once with ten rows bound
        assertTrue(one.reads() > 0, "no read captured in $one")
        assertEquals(one.reads(), ten.reads(), "one pin ran $one, ten ran $ten")
        assertEquals(listOf(update, "-- executeBatch() size:10 sql:$update"), ten.pinUpdates(), "ten ran $ten")
        return ten
    }

    /** What a use case runs: the pins resolved in bulk, then [write], in one transaction. */
    private fun statementsWriting(
        pinCount: Int,
        given: (User, Board) -> Pin,
        write: (List<UUID>, Board) -> Unit,
    ): List<String> {
        val user: User = createAndSaveUser()
        val board = createAndSaveBoard(user)
        val ids = List(pinCount) { given(user, board).id }
        LoggedSql.start()
        transactionRunner.inTransaction {
            repository.findPinsByIds(ids)
            write(ids, board)
        }
        return LoggedSql.stop()
    }

    /** Each logged statement without the `txn[…]` prefix Ebean writes before it. */
    private fun List<String>.sql(): List<String> = map { it.replace(TRANSACTION_PREFIX, "") }

    private fun List<String>.reads(): Int = sql().count { it.startsWith("select") }

    private fun List<String>.pinUpdates(): List<String> = sql().filter { "update pins" in it }

    private companion object {
        val TRANSACTION_PREFIX = Regex("""^txn\[[^]]*]\s*""")

        /** `updatedAt` maps to the legacy `when_modified` column. */
        const val PIN_UPDATE = "update pins set when_modified=? where id=?"

        const val RECYCLE_UPDATE = "update pins set when_modified=?, soft_deleted_at=? where id=?"

        const val JOIN_INSERT = "insert into pin_board_model (pin_id, board_id) values (?,?)"
    }
}
