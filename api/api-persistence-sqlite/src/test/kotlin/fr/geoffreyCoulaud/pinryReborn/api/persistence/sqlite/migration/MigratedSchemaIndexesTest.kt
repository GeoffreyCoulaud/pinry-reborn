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
    fun `Given the migrated database, Then it holds every index the history leaves in place`() {
        // Given
        val present =
            database
                .sqlQuery("select name from sqlite_master where type = 'index'")
                .findList()
                .map { it.getString("name") }
                .toSet()

        // When
        val missing = MigrationDirectory.currentIndexes.keys.filterNot { it in present }.sorted()

        // Then: the second assertion is the first one's non-empty guard, a history read as carrying no
        // index at all being trivially held in full.
        assertEquals(emptyList<String>(), missing)
        assertNotEquals(emptySet<String>(), MigrationDirectory.currentIndexes.keys)
    }
}
