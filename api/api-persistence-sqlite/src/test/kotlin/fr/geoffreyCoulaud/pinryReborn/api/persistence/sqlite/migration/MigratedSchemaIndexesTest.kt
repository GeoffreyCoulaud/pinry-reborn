package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.migration

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.RepositoryTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * [MigrationDirectory.currentIndexes] cannot see a `drop table`, so a rebuild that loses one of its table's indexes
 * leaves it green. The migrated database is the only place that loss is observable.
 */
class MigratedSchemaIndexesTest : RepositoryTest() {
    @Test
    fun `Given the migrated database, Then its indexes are exactly the ones the history leaves in place`() {
        // Given: SQLite names the index it creates for a column constraint itself, and no statement
        // declares it.
        val present =
            database
                .sqlQuery("select name from sqlite_master where type = 'index'")
                .findList()
                .map { it.getString("name") }
                .filterNot { it.startsWith(AUTO_INDEX_PREFIX) }
                .toSet()
        val declared = MigrationDirectory.currentIndexes.keys

        // When
        val missing = declared.filterNot { it in present }.sorted()
        val undeclared = present.filterNot { it in declared }.sorted()

        // Then: the third assertion is the first one's non-empty guard, a history read as carrying no
        // index at all being trivially held in full.
        assertEquals(emptyList<String>(), missing)
        assertEquals(emptyList<String>(), undeclared)
        assertNotEquals(emptySet<String>(), declared)
    }

    @Test
    fun `Given the migrated database, Then every constraint and index is named after its table`() {
        // Given: a table renamed by hand would keep the inline names of the table it was created as.
        val tables = modelTables()
        val namesByTable = tables.associateWith { constraintAndIndexNamesOf(it) }

        // When
        val misnamed = namesByTable.flatMap { (table, names) ->
            names.filterNot { it.matches(Regex("""(ix|ux|uq|ck)_${table}_\w+|pk_$table""")) }
        }

        // Then: the second assertion is the first one's non-empty guard, every table holding a name.
        assertEquals(emptyList<String>(), misnamed)
        assertEquals(tables, namesByTable.filterValues { it.isNotEmpty() }.keys.toList())
    }

    @Test
    fun `Given the migrated database, Then no table or column keeps a name the models no longer give`() {
        // Given: the join tables were once named after their class, and two instants after a legacy column.
        val tables = modelTables()
        val columns = tables.flatMap { table ->
            database.sqlQuery("select name from pragma_table_info(?)").setParameter(table).findList().map {
                "$table.${it.getString("name")}"
            }
        }

        // When
        val legacy = (tables + columns).filter { name -> LEGACY_NAME_PARTS.any { it in name } }

        // Then: the second assertion is the first one's non-empty guard.
        assertEquals(emptyList<String>(), legacy)
        assertNotEquals(emptyList<String>(), columns)
    }

    private fun modelTables(): List<String> =
        database
            .sqlQuery("select name from sqlite_master where type = 'table' order by name")
            .findList()
            .map { it.getString("name") }
            .filterNot { it.startsWith("sqlite_") || it == MIGRATION_TABLE }

    private fun constraintAndIndexNamesOf(table: String): List<String> {
        val rows =
            database
                .sqlQuery("select type, name, sql from sqlite_master where tbl_name = ?")
                .setParameter(table)
                .findList()
        val tableSql = rows.single { it.getString("type") == "table" }.getString("sql")
        val constraints = INLINE_CONSTRAINT.findAll(tableSql).map { it.groupValues[1] }.toList()
        val indexes =
            rows
                .filter { it.getString("type") == "index" }
                .map { it.getString("name") }
                .filterNot { it.startsWith(AUTO_INDEX_PREFIX) }
        return constraints + indexes
    }

    private companion object {
        private const val AUTO_INDEX_PREFIX = "sqlite_autoindex_"
        // Ebean's own table, whose names are Ebean's.
        private const val MIGRATION_TABLE = "db_migration"
        private val LEGACY_NAME_PARTS = listOf("_model", "when_")
        private val INLINE_CONSTRAINT = Regex("""constraint\s+(\w+)""", RegexOption.IGNORE_CASE)
    }
}
