package fr.geoffreyCoulaud.pinryReborn.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageSizeForwardedUnclampedTest {
    private val rule = PageSizeForwardedUnclamped(Config.empty)

    @Test
    fun `Given a pageSize forwarded unclamped, Then it is reported`() {
        // Given
        val code =
            """
            class Getter(private val repository: Repository) {
                fun list(cursor: Cursor?, pageSize: Int) = repository.findAll(cursor, pageSize)
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(1, findings.size)
        assertEquals(
            "pageSize reaches the repository unclamped, so a request can ask for a page of zero. " +
                "Clamp it with coerceIn.",
            findings.single().message,
        )
    }

    @Test
    fun `Given a pageSize clamped with coerceIn, Then nothing is reported`() {
        // Given
        val code =
            """
            class Getter(private val repository: Repository) {
                fun list(cursor: Cursor?, pageSize: Int) =
                    repository.findAll(cursor, pageSize.coerceIn(1, MAX_PAGE_SIZE))
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `Given one clamped read and one that is not, Then only the unclamped one is reported`() {
        // Given: the half a text search for `coerceIn` walks past, the clamp being present
        val code =
            """
            class Getter(private val repository: Repository) {
                fun list(cursor: Cursor?, pageSize: Int): Page {
                    val page = repository.findAll(cursor, pageSize.coerceIn(1, MAX_PAGE_SIZE))
                    return page.copy(requested = pageSize)
                }
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(listOf(4), findings.map { it.entity.location.source.line })
    }

    @Test
    fun `Given a named argument carrying the parameter's own name, Then that name is not reported`() {
        // Given: `pageSize =` names a parameter of the callee and reads nothing
        val code =
            """
            class Getter(private val repository: Repository) {
                fun list(cursor: Cursor?, pageSize: Int) =
                    repository.findAll(cursor = cursor, pageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE))
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `Given a local named pageSize and no such parameter, Then nothing is reported`() {
        // Given: the controllers' shape, where the default is applied and the use case clamps
        val code =
            """
            class Controller(private val getter: Getter) {
                fun list(pageSizeInput: Int?): Page {
                    val pageSize = pageSizeInput ?: DEFAULT_PAGE_SIZE
                    return getter.list(pageSize)
                }
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `Given a declaration with no body, Then nothing is reported`() {
        // Given: a port declares the parameter and clamps nothing, the implementation answering for it
        val code =
            """
            interface Repository {
                fun findAll(cursor: Cursor?, pageSize: Int): Page
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `Given pageSize passed to coerceIn as an argument, Then it is reported`() {
        // Given: the clamp is on another value, and this one still travels raw
        val code =
            """
            class Getter(private val repository: Repository) {
                fun list(requested: Int, pageSize: Int) =
                    repository.findAll(requested.coerceIn(1, pageSize))
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(1, findings.size)
    }

    @Test
    fun `Given pageSize receiving a call that is not coerceIn, Then it is reported`() {
        // Given
        val code =
            """
            class Getter(private val repository: Repository) {
                fun list(pageSize: Int) = repository.findAll(pageSize.toString())
            }
            """.trimIndent()

        // When
        val findings = rule.lint(code)

        // Then
        assertEquals(1, findings.size)
    }
}
