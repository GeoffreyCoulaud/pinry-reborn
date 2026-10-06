package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.migration

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * `1.27.sql` on join tables that already repeat a pair, which the empty test database never holds: the tables are
 * created from the history's own statements on a private connection, seeded, then migrated.
 */
class JoinTableUniquenessMigrationTest {
    @Test
    fun `Given repeated pairs, Then the migration keeps one of each and the indexes refuse another`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            // Given
            JOIN_TABLES.forEach { (table, columns) ->
                connection.run(createTableStatement(table))
                connection.run("insert into $table ($columns) values ('p', 'a'), ('p', 'a'), ('p', 'b')")
            }

            // When
            statementsOf(MIGRATION).forEach { connection.run(it) }

            // Then
            JOIN_TABLES.forEach { (table, columns) ->
                assertEquals(listOf("p,a", "p,b"), connection.pairs(table, columns), table)
                assertThrows<SQLException>(table) { connection.run("insert into $table ($columns) values ('p', 'a')") }
            }
        }
    }

    private fun createTableStatement(table: String): String =
        MigrationDirectory.sqlScripts
            .firstNotNullOf { Regex("""create table $table \([^;]*\)""").find(MigrationDirectory.schemaOnly(it)) }
            .value

    private fun statementsOf(script: File): List<String> =
        MigrationDirectory.schemaOnly(script).split(";").map { it.trim() }.filter { it.isNotEmpty() }

    private fun Connection.run(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.pairs(table: String, columns: String): List<String> =
        createStatement().use { statement ->
            val rows = statement.executeQuery("select $columns from $table order by $columns")
            generateSequence { if (rows.next()) "${rows.getString(1)},${rows.getString(2)}" else null }.toList()
        }

    private companion object {
        val MIGRATION = File(MigrationDirectory.root, "1.27.sql")
        val JOIN_TABLES = mapOf("pin_board_model" to "pin_id, board_id", "pin_tag_model" to "pin_id, tag_id")
    }
}
