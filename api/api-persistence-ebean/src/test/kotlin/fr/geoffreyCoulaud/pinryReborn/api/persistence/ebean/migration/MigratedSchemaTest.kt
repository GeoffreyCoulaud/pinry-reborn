package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.migration

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Board
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportState
import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.NewTask
import fr.geoffreyCoulaud.pinryReborn.api.domain.tasks.TaskState
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.RepositoryTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** Reads the migrated database's structure from PostgreSQL's catalogue, where a lost index or a dropped CHECK shows. */
class MigratedSchemaTest : RepositoryTest() {
    @Test
    fun `Given the migrated database, Then its indexes are exactly the ones the history leaves in place`() {
        // Given: a constraint's index takes the constraint's name and no statement creates it.
        // MigrationDirectory.currentIndexes cannot see a `drop table` that loses one.
        val present = indexesOutsideConstraints().values.flatten().toSet()
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
        // Given: a table renamed by hand would keep the names of the table it was created as.
        val tables = modelTables()
        val indexes = indexesOutsideConstraints()
        val namesByTable = tables.associateWith { constraintNamesOf(it) + indexes[it].orEmpty() }

        // When
        val misnamed = namesByTable.flatMap { (table, names) ->
            names.filterNot { it.matches(Regex("""(ix|ux|uq|ck|fk)_${table}_\w+|pk_$table""")) }
        }

        // Then: the second assertion is the first one's non-empty guard, every table holding a name.
        assertEquals(emptyList<String>(), misnamed)
        assertEquals(tables, namesByTable.filterValues { it.isNotEmpty() }.keys.toList())
    }

    @Test
    fun `Given the migrated database, Then no table or column keeps a name the models no longer give`() {
        // Given: the join tables were once named after their class, and two instants after a legacy column.
        val tables = modelTables()
        val columns = modelColumns().keys

        // When
        val legacy = (tables + columns).filter { name -> LEGACY_NAME_PARTS.any { it in name } }

        // Then: the second assertion is the first one's non-empty guard.
        assertEquals(emptyList<String>(), legacy)
        assertNotEquals(emptySet<String>(), columns)
    }

    @Test
    fun `Given the migrated database, Then no column stores what nothing reads`() {
        // When: the test above is this one's non-empty guard
        val unread = modelColumns().keys.filter { it in UNREAD_COLUMNS }

        // Then
        assertEquals(emptyList<String>(), unread)
    }

    @Test
    fun `Given the migrated database, Then the columns a reader names, searches or sorts compare without case`() {
        // When
        val collated =
            database
                .sqlQuery(
                    "select table_name, column_name from information_schema.columns " +
                        "where table_schema = current_schema() and collation_name = 'case_insensitive'"
                )
                .findList()
                .map { "${it.getString("table_name")}.${it.getString("column_name")}" }

        // Then
        assertEquals(CASE_INSENSITIVE_COLUMNS.sorted(), collated.sorted())
    }

    @Test
    fun `Given the migrated database, Then every key is a uuid and every instant carries its zone`() {
        // Given: an identifier stored as text, or an instant without its zone, is a model overriding its type
        val types = modelColumns()
        val keyColumns = keyColumns().map { (_, column) -> column }

        // When
        val keysNotUuid = keyColumns.filterNot { types[it] == "uuid" }
        val instantsWithoutZone =
            types.filter { (column, type) -> column.endsWith("_at") && type != "timestamp with time zone" }.keys

        // Then: the third assertion is the first two's non-empty guard
        assertEquals(emptyList<String>(), keysNotUuid)
        assertEquals(emptySet<String>(), instantsWithoutZone)
        assertNotEquals(emptyList<String>(), keyColumns)
    }

    @Test
    fun `Given the migrated database, Then every index over text declares a bound under the entry limit`() {
        // Given: PostgreSQL refuses an index entry over 2704 bytes
        val indexesOverText =
            database
                .sqlQuery(
                    """
                    select distinct ind.relname as index_name
                    from pg_index entry
                    join pg_class ind on ind.oid = entry.indexrelid
                    join pg_class tab on tab.oid = entry.indrelid
                    join pg_namespace space on space.oid = tab.relnamespace
                    join pg_attribute key on key.attrelid = entry.indexrelid
                    where space.nspname = current_schema() and tab.relname <> ?
                      and key.atttypid in ('text'::regtype, 'varchar'::regtype, 'bpchar'::regtype)
                    """
                )
                .setParameter(MIGRATION_TABLE)
                .findList()
                .map { it.getString("index_name") }

        // When
        val overLimit = TEXT_KEY_BYTES.filterValues { it + OTHER_KEY_BYTES > MAX_INDEX_ENTRY_BYTES }.keys

        // Then: an index over text with no declared bound fails the first assertion
        assertEquals(TEXT_KEY_BYTES.keys.sorted(), indexesOverText.sorted())
        assertEquals(emptySet<String>(), overLimit)
    }

    @Test
    fun `Given the migrated database, Then its booleans are the columns the models hold as one`() {
        // When
        val booleans = modelColumns().filterValues { it == "boolean" }.keys

        // Then
        assertEquals(BOOLEAN_COLUMNS.sorted(), booleans.sorted())
    }

    @Test
    fun `Given the migrated database, Then its foreign keys are exactly the declared ones with their deletion`() {
        // When
        val keys =
            database
                .sqlQuery(
                    """
                    select child.table_name, child.column_name, parent.table_name as parent_table, ref.delete_rule
                    from information_schema.referential_constraints ref
                    join information_schema.key_column_usage child
                      on child.constraint_schema = ref.constraint_schema and child.constraint_name = ref.constraint_name
                    join information_schema.constraint_column_usage parent
                      on parent.constraint_schema = ref.constraint_schema
                      and parent.constraint_name = ref.constraint_name
                    where ref.constraint_schema = current_schema()
                    """
                )
                .findList()
                .map {
                    "${it.getString("table_name")}.${it.getString("column_name")} -> " +
                        "${it.getString("parent_table")} ${it.getString("delete_rule")}"
                }

        // Then
        assertEquals(FOREIGN_KEYS.sorted(), keys.sorted())
    }

    @Test
    fun `Given the migrated database, Then every foreign key's column leads a non-partial index`() {
        // Given: a cascade through a key whose column leads no index scans the child table once per parent row.
        val leadingColumns =
            database
                .sqlQuery(
                    """
                    select tab.relname as table_name, col.attname as column_name
                    from pg_index ind
                    join pg_class tab on tab.oid = ind.indrelid
                    join pg_namespace space on space.oid = tab.relnamespace
                    join pg_attribute col on col.attrelid = ind.indrelid and col.attnum = ind.indkey[0]
                    where space.nspname = current_schema() and ind.indpred is null
                    """
                )
                .findList()
                .map { "${it.getString("table_name")}.${it.getString("column_name")}" }
                .toSet()

        // When
        val unindexed =
            keyColumns()
                .filter { (kind, _) -> kind == FOREIGN }
                .map { (_, column) -> column }
                .filterNot { it in leadingColumns }

        // Then: the foreign keys test above is this one's non-empty guard.
        assertEquals(emptyList<String>(), unindexed)
    }

    @Test
    fun `Given the migrated database, Then its CHECK constraints are exactly the declared ones`() {
        // When: the baseline's are hand-written, which a regenerated baseline drops in silence
        val checks =
            database
                .sqlQuery(
                    "select con.conname from pg_constraint con " +
                        "join pg_namespace space on space.oid = con.connamespace " +
                        "where space.nspname = current_schema() and con.contype = 'c'"
                )
                .findList()
                .map { it.getString("conname") }

        // Then
        assertEquals(CHECK_CONSTRAINTS, checks.sorted())
    }

    @Test
    fun `Given the migrated database, Then the task CHECK's terminal states are the states no attempt is owed`() {
        // Given: the CHECK spells the states by hand, and deleteTerminalBefore's filter rests on it
        val definition =
            database
                .sqlQuery(
                    "select pg_get_constraintdef(con.oid) as definition from pg_constraint con " +
                        "where con.conname = 'ck_tasks_terminal_state_at'"
                )
                .findOne()
                ?.getString("definition")

        // When: the constraint's only literals are the states it names
        val states = Regex("""'(\w+)'""").findAll(definition.orEmpty()).map { it.groupValues[1] }.toSet()

        // Then
        assertEquals(TaskState.entries.filterNot { it.isLiveAttempt }.map { it.name }.toSet(), states)
    }

    private fun modelTables(): List<String> =
        database
            .sqlQuery(
                "select table_name from information_schema.tables " +
                    "where table_schema = current_schema() and table_type = 'BASE TABLE' and table_name <> ? " +
                    "order by table_name"
            )
            .setParameter(MIGRATION_TABLE)
            .findList()
            .map { it.getString("table_name") }

    /** Each column of the model tables as `table.column`, against its type. */
    private fun modelColumns(): Map<String, String> =
        database
            .sqlQuery(
                "select table_name, column_name, data_type from information_schema.columns " +
                    "where table_schema = current_schema() and table_name <> ?"
            )
            .setParameter(MIGRATION_TABLE)
            .findList()
            .associate { "${it.getString("table_name")}.${it.getString("column_name")}" to it.getString("data_type") }

    /** Each primary and foreign key's columns as `table.column`, against its kind, `p` or `f`. */
    private fun keyColumns(): List<Pair<String, String>> =
        database
            .sqlQuery(
                """
                select con.contype as kind, tab.relname as table_name, col.attname as column_name
                from pg_constraint con
                join pg_class tab on tab.oid = con.conrelid
                join pg_namespace space on space.oid = tab.relnamespace
                join pg_attribute col on col.attrelid = con.conrelid and col.attnum = any(con.conkey)
                where space.nspname = current_schema() and con.contype in ('p', 'f') and tab.relname <> ?
                """
            )
            .setParameter(MIGRATION_TABLE)
            .findList()
            .map { it.getString("kind") to "${it.getString("table_name")}.${it.getString("column_name")}" }

    /** The NOT NULL constraints PostgreSQL names itself are left out: no statement declares them. */
    private fun constraintNamesOf(table: String): List<String> =
        database
            .sqlQuery(
                "select con.conname from pg_constraint con join pg_class tab on tab.oid = con.conrelid " +
                    "join pg_namespace space on space.oid = tab.relnamespace " +
                    "where space.nspname = current_schema() and tab.relname = ? and con.contype <> 'n'"
            )
            .setParameter(table)
            .findList()
            .map { it.getString("conname") }

    /** The indexes a statement creates, by table: a constraint's own index takes the constraint's name. */
    private fun indexesOutsideConstraints(): Map<String, List<String>> =
        database
            .sqlQuery(
                """
                select tab.relname as table_name, ind.relname as index_name
                from pg_index entry
                join pg_class ind on ind.oid = entry.indexrelid
                join pg_class tab on tab.oid = entry.indrelid
                join pg_namespace space on space.oid = tab.relnamespace
                where space.nspname = current_schema()
                  and not exists (select 1 from pg_constraint con where con.conindid = entry.indexrelid)
                """
            )
            .findList()
            .groupBy({ it.getString("table_name") }, { it.getString("index_name") })

    private companion object {
        // Ebean's own table, whose names are Ebean's.
        private const val MIGRATION_TABLE = "db_migration"
        private const val FOREIGN = "f"
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

        // The columns a reader names, searches or sorts by (ADR 0059, decision 4).
        private val CASE_INSENSITIVE_COLUMNS =
            listOf(
                "boards.name",
                "persons.name",
                "pins.description",
                "remote_collections.name",
                "tags.name",
                "users.name",
            )

        private const val MAX_INDEX_ENTRY_BYTES = 2704

        // A uuid, an instant and an integer at most beside the text, with the entry's headers.
        private const val OTHER_KEY_BYTES = 64

        // The API's length bounds count UTF-16 units, each at most three bytes in UTF-8.
        private const val UTF8_BYTES_PER_UNIT = 3
        private const val SHA256_HEX_LENGTH = 64
        private const val MD5_HEX_LENGTH = 32

        // The longest text each index's keys can hold, in bytes, from the bound its writers read.
        private val TEXT_KEY_BYTES =
            mapOf(
                // The account alphabet is ASCII.
                "ux_users_name" to User.MAX_NAME_LENGTH,
                // ImportFieldBounds restates the bound for the import.
                "ux_boards_author_name" to Board.MAX_NAME_LENGTH * UTF8_BYTES_PER_UNIT,
                "ux_tags_author_name" to Tag.MAX_NAME_LENGTH * UTF8_BYTES_PER_UNIT,
                // The addresses by their digest, whatever their length.
                "ux_persons_author_name_urls" to PersonName.MAX_LENGTH * UTF8_BYTES_PER_UNIT + MD5_HEX_LENGTH,
                // HttpUrl percent-encodes every byte outside ASCII.
                "ux_remote_collections_author_url" to HttpUrl.MAX_LENGTH,
                // Written by the use cases alone, in ASCII.
                "ux_tasks_dedup" to NewTask.MAX_DEDUP_KEY_LENGTH,
                "ix_media_content_hash" to SHA256_HEX_LENGTH,
                "uq_session_tokens_token_hash" to SHA256_HEX_LENGTH,
                "ix_tasks_claim" to TaskState.entries.maxOf { it.name.length },
                "ix_tasks_state_terminal_state_at" to TaskState.entries.maxOf { it.name.length },
                "ix_user_data_imports_user_state" to UserDataImportState.entries.maxOf { it.name.length },
            )

        private val BOOLEAN_COLUMNS =
            listOf(
                "session_tokens.persistent",
                "tasks.cancel_requested",
                "user_data_exports.archive_held",
                "user_data_imports.archive_held",
                "user_data_imports.issue_detail_truncated",
            )

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
