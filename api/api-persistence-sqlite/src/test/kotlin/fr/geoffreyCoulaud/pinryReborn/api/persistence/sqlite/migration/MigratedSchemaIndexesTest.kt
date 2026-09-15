package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.migration

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.RepositoryTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * [MigrationDirectory.currentIndexes] cannot see a `drop table`, so a rebuild that loses one of its
 * table's indexes leaves it green. The migrated database is the only place that loss is observable.
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

    private companion object {
        private const val AUTO_INDEX_PREFIX = "sqlite_autoindex_"
    }
}
