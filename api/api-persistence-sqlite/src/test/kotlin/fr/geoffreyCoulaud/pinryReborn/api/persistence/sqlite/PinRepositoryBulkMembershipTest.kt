package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import io.ebean.test.LoggedSql
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

/**
 * The bulk membership writes and the bulk read that resolves their pins (spec 2026-09-27, decision H).
 * Split from `PinRepositoryTest` to keep it under detekt's `LargeClass` threshold.
 */
class PinRepositoryBulkMembershipTest : PinRepositoryFixtures() {
    private val later: Instant = storableNow().plusSeconds(60)

    // --- findPinsByIds ---

    @Test
    fun `Given pins in either state, Then findPinsByIds answers each with its tags and boards`() {
        // Given
        val user = createAndSaveUser()
        val tag = createAndSaveTag(name = "tag", user = user)
        val board = createAndSaveBoard(user)
        val active = repository.savePin(createAndSavePin(user).copy(tags = listOf(tag), boards = listOf(board)))
        val recycled = repository.softDeletePin(createAndSavePin(user), storableNow())

        // When
        val found = repository.findPinsByIds(listOf(active.id, recycled.id, randomUUID()))

        // Then: an unknown id is simply absent
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
    fun `Given one pin or ten, Then addPinsToBoard reads as often and batches its updates`() {
        assertConstantReadsAndOneUpdateBatch(filed = false) { ids, board ->
            repository.addPinsToBoard(ids, board, later)
        }
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
    fun `Given one pin or ten, Then removePinsFromBoard reads as often and batches its updates`() {
        assertConstantReadsAndOneUpdateBatch(filed = true) { ids, board ->
            repository.removePinsFromBoard(ids, board, later)
        }
    }

    private fun boardIdsOf(pinId: UUID): Set<UUID>? = repository.findPinById(pinId)?.boards?.map { it.id }?.toSet()

    private fun assertConstantReadsAndOneUpdateBatch(filed: Boolean, write: (List<UUID>, Board) -> Unit) {
        // When
        val one = statementsWriting(pinCount = 1, filed = filed, write = write)
        val ten = statementsWriting(pinCount = 10, filed = filed, write = write)

        // Then: one UPDATE statement setting when_modified alone, sent once with ten rows bound
        assertTrue(one.reads() > 0, "no read captured in $one")
        assertEquals(one.reads(), ten.reads(), "one pin ran $one, ten ran $ten")
        assertEquals(listOf(PIN_UPDATE, "-- executeBatch() size:10 sql:$PIN_UPDATE"), ten.pinUpdates(), "ten ran $ten")
    }

    /** What a use case runs: the pins resolved in bulk, then [write], in one transaction. */
    private fun statementsWriting(pinCount: Int, filed: Boolean, write: (List<UUID>, Board) -> Unit): List<String> {
        val user: User = createAndSaveUser()
        val board = createAndSaveBoard(user)
        val boards = if (filed) listOf(board) else emptyList()
        val ids = List(pinCount) { repository.savePin(createAndSavePin(user).copy(boards = boards)).id }
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
    }
}
