package fr.geoffreyCoulaud.pinryReborn.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RawSqlOutsideInventoryTest {
    private val rule = RawSqlOutsideInventory(Config.empty)

    @Test
    fun `Given a raw call whose fragment the inventory holds, Then nothing is reported`() {
        // Given: the keyset cursor's tiebreaker, which no query bean property expresses
        val code =
            """
            class Strategy {
                fun page(query: Query, pivot: UUID) = query.raw("id <= ?", pivot)
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `Given a raw call whose fragment the inventory does not hold, Then it is reported`() {
        // Given
        val code =
            """
            class Repository {
                fun find(query: Query, hash: String) =
                    query.raw("id in (select pin_id from images where content_hash = ?)", hash)
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(1, findings.size)
        assertTrue(
            findings.single().message.contains("id in (select pin_id from images where content_hash = ?)"),
            "the finding should quote the fragment: ${findings.single().message}",
        )
    }

    @Test
    fun `Given a raw call taking a constant, Then it is reported`() {
        // Given: an argument the inventory can never see, whatever the constant holds
        val code =
            """
            class Repository {
                fun find(query: Query, pivot: UUID) = query.raw(KEYSET_TIEBREAKER, pivot)
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(1, findings.size)
    }

    @Test
    fun `Given a raw call taking an interpolated string, Then it is reported`() {
        // Given: the template reads as a literal to a text search and is one to nothing else
        val code =
            """
            class Repository {
                fun find(query: Query, column: String, pivot: UUID) = query.raw("${'$'}column <= ?", pivot)
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(1, findings.size)
    }

    @Test
    fun `Given a raw call with no argument at all, Then it is reported`() {
        // Given: not valid Ebean, and an argument the inventory cannot see is an argument it cannot see
        val code =
            """
            class Repository {
                fun find(query: Query) = query.raw()
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(1, findings.size)
    }

    @Test
    fun `Given the inventory, Then every fragment carries a reason and not a label`() {
        // Given: nothing else reads these values, so a one-word "forced" would pass the rule, this
        // suite and the gate, and the map would be a set with decoration.
        val withoutReason = RawSqlOutsideInventory.INVENTORY.filterValues { it.length < SHORTEST_REASON }

        // Then
        assertEquals(emptyMap<String, String>(), withoutReason)
    }

    @Test
    fun `Given a call that is not named raw, Then its string argument is not reported`() {
        // Given
        val code =
            """
            class Repository {
                fun find(query: Query) = query.orderBy("id asc")
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(0, findings.size)
    }

    private companion object {
        /** Shorter than this is a label, not the reason the query beans cannot express the fragment. */
        private const val SHORTEST_REASON = 120
    }
}
