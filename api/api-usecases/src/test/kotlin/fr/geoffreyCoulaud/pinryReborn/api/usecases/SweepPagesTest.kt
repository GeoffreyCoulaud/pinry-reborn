package fr.geoffreyCoulaud.pinryReborn.api.usecases

import java.util.UUID
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SweepPagesTest {
    private data class Row(val id: UUID)

    @Test
    fun `Given pages that advance, Then every row is read once and the cursor is the last id of the page before`() {
        // Given: two pages then an empty one, each read with the id the previous page ended on
        val first = listOf(Row(randomUUID()), Row(randomUUID()))
        val second = listOf(Row(randomUUID()))
        val cursors = mutableListOf<UUID?>()
        val pages = mapOf<UUID?, List<Row>>(null to first, first.last().id to second, second.last().id to emptyList())

        // When
        val rows =
            SweepPages.of(Row::id) { afterId ->
                    cursors += afterId
                    pages.getValue(afterId)
                }
                .toList()

        // Then
        assertEquals(first + second, rows)
        assertEquals(listOf(null, first.last().id, second.last().id), cursors)
    }

    @Test
    fun `Given a selection that never advances, Then the sweep stops loudly at the page cap`() {
        // Given: a page that answers the same row whatever the cursor, the shape of a selection that forgot its key
        val stuck = listOf(Row(randomUUID()))
        var reads = 0

        // When / Then
        assertThrows(IllegalStateException::class.java) {
            SweepPages.of(Row::id) {
                    reads += 1
                    stuck
                }
                .count()
        }
        assertEquals(SweepPages.MAX_PAGES + 1, reads)
    }
}
