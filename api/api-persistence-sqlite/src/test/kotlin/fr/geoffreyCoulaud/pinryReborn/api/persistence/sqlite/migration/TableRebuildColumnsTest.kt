package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A `<table>_tmp_rebuild` copy loses a column silently: the test database is empty, so the copy runs
 * over zero rows and only production pays (`docs/specs/2026-09-13-optional-source-page-url.md`, §3).
 */
class TableRebuildColumnsTest {
    private val createRebuild =
        Regex("""create\s+table\s+(\w+)_tmp_rebuild\s*\(([^;]*)\)\s*;""", RegexOption.IGNORE_CASE)
    private val insertRebuild =
        Regex("""insert\s+into\s+(\w+)_tmp_rebuild\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
    private val selectFrom =
        Regex("""select\s+([^;]*?)\s+from\s+(\w+)\s*;""", RegexOption.IGNORE_CASE)

    private val rebuilds: List<Rebuild> = MigrationDirectory.sqlScripts.flatMap { rebuildsIn(it) }

    @Test
    fun `Given a rebuilt table, Then its create, insert and select name the same columns in the same order`() {
        // Given
        val disagreeing =
            rebuilds
                .filterNot { it.created == it.inserted && it.created == it.selected }
                .map { "${it.location} creates ${it.created}, inserts ${it.inserted}, selects ${it.selected}" }

        // Then
        assertEquals(emptyList<String>(), disagreeing)
    }

    @Test
    fun `Given a migration that names a rebuild, Then the extraction reads one out of it`() {
        // Guards the assertion above twice over: an extraction reading nothing at all passes it, and so
        // does one blind to the spelling a single migration happens to use.

        // Given
        val silent =
            MigrationDirectory
                .sqlScripts
                .filter { MigrationDirectory.schemaOnly(it).contains("_tmp_rebuild") }
                .filter { file -> rebuilds.none { it.location.startsWith(file.name) } }
                .map { it.name }

        // Then
        assertNotEquals(emptyList<Rebuild>(), rebuilds)
        assertEquals(emptyList<String>(), silent)
    }

    private fun rebuildsIn(file: File): List<Rebuild> {
        val schema = MigrationDirectory.schemaOnly(file)
        return createRebuild.findAll(schema).map { creation ->
            val table = creation.groupValues[1]
            Rebuild(
                location = "${file.name}: $table",
                created = createdColumnsOf(creation.groupValues[2]),
                inserted = columnList(insertRebuild.findAll(schema).firstOrNull { it.groupValues[1] == table }, 2),
                selected = columnList(selectFrom.findAll(schema).firstOrNull { it.groupValues[2] == table }, 1),
            )
        }.toList()
    }

    /** The columns a `create table` body declares: the leading word of every line that is not a constraint. */
    private fun createdColumnsOf(body: String): List<String> =
        body
            .lineSequence()
            .map { it.trim() }
            .filterNot { it.isEmpty() || it.startsWith("constraint") || it.startsWith("foreign key") }
            .map { it.substringBefore(' ') }
            .toList()

    private fun columnList(match: MatchResult?, group: Int): List<String> =
        match?.groupValues?.get(group).orEmpty().split(',').map { it.trim() }.filterNot { it.isEmpty() }

    private data class Rebuild(
        val location: String,
        val created: List<String>,
        val inserted: List<String>,
        val selected: List<String>,
    )
}
