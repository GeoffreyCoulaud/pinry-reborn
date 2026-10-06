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
    fun `Given the migrated database, Then every constraint and index of the media tables is named after its table`() {
        // Given: a table renamed by hand would keep the inline names of the table it was created as.
        val namesByTable = MEDIA_TABLES.associateWith { constraintAndIndexNamesOf(it) }

        // When
        val misnamed = namesByTable.flatMap { (table, names) ->
            names.filterNot { it.matches(Regex("""[a-z]+_${table}_\w+|pk_$table""")) }
        }

        // Then: the second assertion is the first one's non-empty guard.
        assertEquals(emptyList<String>(), misnamed)
        assertEquals(MEDIA_TABLES, namesByTable.filterValues { it.isNotEmpty() }.keys.toList())
    }

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
        private val MEDIA_TABLES = listOf("media", "media_download")
        private val INLINE_CONSTRAINT = Regex("""constraint\s+(\w+)""", RegexOption.IGNORE_CASE)
    }
}
