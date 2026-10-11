package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.migration

import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.TaskState
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.RepositoryTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** Reads the migrated database's structure from SQLite itself, where a lost index or a dropped CHECK shows. */
class MigratedSchemaTest : RepositoryTest() {
    @Test
    fun `Given the migrated database, Then its indexes are exactly the ones the history leaves in place`() {
        // Given: SQLite names the index it creates for a column constraint itself, and no statement
        // declares it. MigrationDirectory.currentIndexes cannot see a `drop table` that loses one.
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
        val columns = modelColumns()

        // When
        val legacy = (tables + columns).filter { name -> LEGACY_NAME_PARTS.any { it in name } }

        // Then: the second assertion is the first one's non-empty guard.
        assertEquals(emptyList<String>(), legacy)
        assertNotEquals(emptyList<String>(), columns)
    }

    @Test
    fun `Given the migrated database, Then no column stores what nothing reads`() {
        // When: the test above is this one's non-empty guard
        val unread = modelColumns().filter { it in UNREAD_COLUMNS }

        // Then
        assertEquals(emptyList<String>(), unread)
    }

    @Test
    fun `Given the migrated database, Then its foreign keys are exactly the declared ones with their deletion`() {
        // When
        val keys =
            modelTables().flatMap { table ->
                database
                    .sqlQuery("""select "from", "table", on_delete from pragma_foreign_key_list(?)""")
                    .setParameter(table)
                    .findList()
                    .map { "$table.${it.getString("from")} -> ${it.getString("table")} ${it.getString("on_delete")}" }
            }

        // Then
        assertEquals(FOREIGN_KEYS.sorted(), keys.sorted())
    }

    @Test
    fun `Given the migrated database, Then every foreign key's column leads a non-partial index`() {
        // Given: a cascade through a key whose column leads no index scans the child table once per parent row.
        val unindexedKey =
            """
            select fk."from" as key_column from pragma_foreign_key_list(?) fk
            where not exists (
                select 1 from pragma_index_list(?) list join pragma_index_info(list.name) info
                where list.partial = 0 and info.seqno = 0 and info.name = fk."from"
            )
            """

        // When
        val unindexed =
            modelTables().flatMap { table ->
                database.sqlQuery(unindexedKey).setParameter(table).setParameter(table).findList().map {
                    "$table.${it.getString("key_column")}"
                }
            }

        // Then: the foreign keys test above is this one's non-empty guard.
        assertEquals(emptyList<String>(), unindexed)
    }

    @Test
    fun `Given the migrated database, Then its CHECK constraints are exactly the declared ones`() {
        // When: the baseline's are hand-written, which a regenerated baseline drops in silence
        val checks =
            database.sqlQuery("select sql from sqlite_master where type = 'table'").findList().flatMap { row ->
                CHECK_CONSTRAINT.findAll(row.getString("sql")).map { it.groupValues[1] }
            }

        // Then
        assertEquals(CHECK_CONSTRAINTS, checks.sorted())
    }

    @Test
    fun `Given the migrated database, Then the task CHECK's terminal states are the states no attempt is owed`() {
        // Given: the CHECK spells the states by hand, and deleteTerminalBefore's filter rests on it
        val tasks = database.sqlQuery("select sql from sqlite_master where name = 'tasks'").findOne()?.getString("sql")

        // When
        val checked = TERMINAL_STATES.find(tasks.orEmpty())?.groupValues?.get(1).orEmpty()
        val states = Regex("""'(\w+)'""").findAll(checked).map { it.groupValues[1] }.toSet()

        // Then
        assertEquals(TaskState.entries.filterNot { it.isLiveAttempt }.map { it.name }.toSet(), states)
    }

    private fun modelTables(): List<String> =
        database
            .sqlQuery("select name from sqlite_master where type = 'table' order by name")
            .findList()
            .map { it.getString("name") }
            .filterNot { it.startsWith("sqlite_") || it == MIGRATION_TABLE }

    private fun modelColumns(): List<String> =
        modelTables().flatMap { table ->
            database.sqlQuery("select name from pragma_table_info(?)").setParameter(table).findList().map {
                "$table.${it.getString("name")}"
            }
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
        // Ebean's own table, whose names are Ebean's.
        private const val MIGRATION_TABLE = "db_migration"
        private val LEGACY_NAME_PARTS = listOf("_model", "when_")
        // Derived from other values, and errors no reader ever had (ADR 0058, decision 10).
        private val UNREAD_COLUMNS =
            listOf(
                "user_data_exports.storage_key",
                "user_data_exports.media_type",
                "user_data_exports.file_extension",
                "user_data_imports.storage_key",
                "user_data_imports.processed_pins",
                "media.animated",
                "media_downloads.last_error",
                "tasks.last_error",
            )
        private val INLINE_CONSTRAINT = Regex("""constraint\s+(\w+)""", RegexOption.IGNORE_CASE)
        private val CHECK_CONSTRAINT = Regex("""constraint\s+(\w+)\s+check\b""", RegexOption.IGNORE_CASE)
        private val TERMINAL_STATES = Regex("""ck_tasks_terminal_state_at\s+check\s*\(\s*\(state\s+in\s*\(([^)]*)\)""")

        // Ebean generates the enumerated column's; the others hold what comments held (ADR 0058, decision 6).
        private val CHECK_CONSTRAINTS =
            listOf(
                "ck_media_variant",
                "ck_pin_duplicates_order",
                "ck_tasks_terminal_state_at",
                "ck_user_data_exports_ready",
                "ck_user_password_hashes_algorithm",
            )

        // Every key cascades but a pin's publisher, a credit removed with its person (ADR 0058, decisions 1 and 2).
        private val FOREIGN_KEYS =
            listOf(
                "boards.author_id -> users CASCADE",
                "media.pin_id -> pins CASCADE",
                "media_downloads.pin_id -> pins CASCADE",
                "media_frames.media_id -> media CASCADE",
                "persons.author_id -> users CASCADE",
                "pin_boards.board_id -> boards CASCADE",
                "pin_boards.pin_id -> pins CASCADE",
                "pin_creators.person_id -> persons CASCADE",
                "pin_creators.pin_id -> pins CASCADE",
                "pin_duplicates.first_pin_id -> pins CASCADE",
                "pin_duplicates.second_pin_id -> pins CASCADE",
                "pin_tags.pin_id -> pins CASCADE",
                "pin_tags.tag_id -> tags CASCADE",
                "pins.author_id -> users CASCADE",
                "pins.publisher_id -> persons SET NULL",
                "remote_collections.author_id -> users CASCADE",
                "remote_collections.board_id -> boards CASCADE",
                "session_tokens.user_id -> users CASCADE",
                "tags.author_id -> users CASCADE",
                "user_data_exports.user_id -> users CASCADE",
                "user_data_import_issues.import_id -> user_data_imports CASCADE",
                "user_data_imports.user_id -> users CASCADE",
                "user_password_hashes.user_id -> users CASCADE",
            )
    }
}
