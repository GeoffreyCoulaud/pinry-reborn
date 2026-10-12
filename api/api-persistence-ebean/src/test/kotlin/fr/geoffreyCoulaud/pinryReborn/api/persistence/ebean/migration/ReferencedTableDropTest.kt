package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Ebean migrates in one transaction, where `foreign_keys` cannot be turned off, so dropping a table a key references
 * cascades to its children (`docs/adr/0058-the-database-enforces-its-references.md`, decision 9).
 */
class ReferencedTableDropTest {
    private val refusal = "A migration may not drop a table a key references: ADR 0058, decision 9"

    // SQLite quotes a name it rewrites after a rename.
    private val reference = Regex("""\breferences\s+"?(\w+)""", RegexOption.IGNORE_CASE)
    private val tableDrop = Regex("""\bdrop\s+table\s+(?:if\s+exists\s+)?"?(\w+)""", RegexOption.IGNORE_CASE)
    private val tableRename = Regex("""\balter\s+table\s+"?(\w+)"?\s+rename\s+to\s+"?(\w+)""", RegexOption.IGNORE_CASE)

    private val scripts: Map<String, String> =
        MigrationDirectory.sqlScripts.associate { it.name to MigrationDirectory.schemaOnly(it) }

    @Test
    fun `Given the migration scripts, Then none drops a table a key references`() {
        assertEquals(emptyList<String>(), referencedTableDrops(scripts), refusal)
    }

    @Test
    fun `Given a script after the baseline that drops pins, Then it is refused`() {
        // Given
        val withDrop = scripts + ("1.1.sql" to "drop table if exists pins;")

        // Then
        assertEquals(listOf("1.1.sql drops pins"), referencedTableDrops(withDrop))
    }

    @Test
    fun `Given a script after the baseline that drops pins by its quoted name, Then it is refused`() {
        // Given
        val withDrop = scripts + ("1.1.sql" to "drop table \"pins\";")

        // Then
        assertEquals(listOf("1.1.sql drops pins"), referencedTableDrops(withDrop))
    }

    @Test
    fun `Given a script after the baseline that renames pins then drops it, Then it is refused`() {
        // Given: SQLite points the children's keys at the new name, so the drop cascades to them
        val withDrop = scripts + ("1.1.sql" to "alter table pins rename to pins_old; drop table pins_old;")

        // Then
        assertEquals(listOf("1.1.sql drops pins_old"), referencedTableDrops(withDrop))
    }

    private fun referencedTableDrops(scripts: Map<String, String>): List<String> {
        val referenced =
            scripts.values.flatMap { text -> reference.findAll(text).map { it.groupValues[1] } }.toMutableSet()
        scripts.values
            .flatMap { tableRename.findAll(it) }
            .forEach { rename ->
                if (rename.groupValues[1] in referenced) referenced += rename.groupValues[2]
            }
        return scripts.flatMap { (name, text) ->
            tableDrop.findAll(text).map { it.groupValues[1] }.filter { it in referenced }.map { "$name drops $it" }
        }
    }
}
