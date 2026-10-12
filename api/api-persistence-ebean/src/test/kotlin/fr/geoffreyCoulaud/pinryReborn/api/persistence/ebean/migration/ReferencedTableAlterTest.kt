package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.migration

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.RepositoryTest
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * PostgreSQL alters a table in place inside Ebean's migration transaction, so a referenced table's column changes type
 * and its children stay (`docs/adr/0059-the-database-is-postgresql.md`).
 */
class ReferencedTableAlterTest : RepositoryTest() {
    @Test
    fun `Given a row in every table, Then altering a column type of each referenced table keeps every child row`() {
        // Given
        insertOneRowPerTable()
        val references = references()
        val referenced = references.map { (_, parent) -> parent }.toSet()
        val children = references.map { (child, _) -> child }.toSet()
        assertEquals(ALTERED_COLUMNS.keys.sorted(), referenced.sorted())
        val rowsBefore = rowCounts(children)

        // When: one transaction, as Ebean runs a migration, closed without a commit
        val (notRewritten, rowsAfter) =
            database.beginTransaction().use {
                referenced.filterNot { table -> alterRewrites(table) } to rowCounts(children)
            }

        // Then: each alter rewrote its table; the last assertion is the third one's non-empty guard
        assertEquals(emptyList<String>(), notRewritten)
        assertEquals(rowsBefore, rowsAfter)
        assertEquals(emptySet<String>(), rowsBefore.filterValues { it == 0 }.keys)
    }

    /** Each foreign key as its child table against the table it references. */
    private fun references(): List<Pair<String, String>> =
        database
            .sqlQuery(
                """
                select child.relname as child_table, parent.relname as parent_table
                from pg_constraint con
                join pg_class child on child.oid = con.conrelid
                join pg_class parent on parent.oid = con.confrelid
                join pg_namespace space on space.oid = con.connamespace
                where space.nspname = current_schema() and con.contype = 'f'
                """
            )
            .findList()
            .map { it.getString("child_table") to it.getString("parent_table") }

    private fun rowCounts(tables: Set<String>): Map<String, Int> = tables.associateWith { table ->
        database.sqlQuery("select count(*) as row_count from $table").findOne()?.getInteger("row_count") ?: 0
    }

    /** Whether [table] was written anew: a precision taken off an instant rewrites every row. */
    private fun alterRewrites(table: String): Boolean {
        val before = fileNodeOf(table)
        database
            .sqlUpdate("alter table $table alter column ${ALTERED_COLUMNS.getValue(table)} type timestamptz(3)")
            .execute()
        return fileNodeOf(table) != before
    }

    private fun fileNodeOf(table: String): Long? =
        database
            .sqlQuery("select pg_relation_filenode(?::regclass) as file_node")
            .setParameter(table)
            .findOne()
            ?.getLong("file_node")

    private fun insertOneRowPerTable() {
        val user = id(1)
        val board = id(2)
        val tag = id(3)
        val person = id(4)
        val pin = id(5)
        val otherPin = id(6)
        val media = id(7)
        val import = id(8)
        listOf(
                "insert into users (id, created_at, name) values ('$user', now(), 'alice')",
                "insert into user_password_hashes (id, user_id, created_at, hash, algorithm) " +
                    "values ('${id(10)}', '$user', now(), 'hash', 'BCRYPT')",
                "insert into session_tokens (id, user_id, expires_at, created_at, token_hash) " +
                    "values ('${id(11)}', '$user', now(), now(), 'token')",
                "insert into boards (id, author_id, created_at, updated_at, name, description) " +
                    "values ('$board', '$user', now(), now(), 'board', '')",
                "insert into tags (id, author_id, created_at, name) values ('$tag', '$user', now(), 'tag')",
                "insert into persons (id, author_id, created_at, name, urls) " +
                    "values ('$person', '$user', now(), 'person', '[]')",
                "insert into pins (id, author_id, created_at, updated_at, publisher_id, description) " +
                    "values ('$pin', '$user', now(), now(), '$person', ''), " +
                    "('$otherPin', '$user', now(), now(), null, '')",
                "insert into pin_boards (pin_id, board_id) values ('$pin', '$board')",
                "insert into pin_tags (pin_id, tag_id) values ('$pin', '$tag')",
                "insert into pin_creators (pin_id, person_id) values ('$pin', '$person')",
                "insert into pin_duplicates (id, first_pin_id, second_pin_id) " +
                    "values ('${id(12)}', '$pin', '$otherPin')",
                "insert into media (id, pin_id, width, height, byte_size, created_at, mime_type, content_hash, " +
                    "storage_key) values ('$media', '$pin', 1, 1, 1, now(), 'image/png', 'hash', 'key')",
                "insert into media_frames (id, media_id, hash_0, hash_1, hash_2, hash_3) " +
                    "values ('${id(13)}', '$media', 0, 0, 0, 0)",
                "insert into media_downloads (id, pin_id, task_id, requested_at, updated_at, source_url, status) " +
                    "values ('${id(14)}', '$pin', '${id(15)}', now(), now(), 'https://a.test/', 'PENDING')",
                "insert into remote_collections (id, author_id, created_at, board_id, url, name) " +
                    "values ('${id(16)}', '$user', now(), '$board', 'https://a.test/', 'collection')",
                "insert into user_data_exports (id, user_id, format_version, requested_at, state) " +
                    "values ('${id(17)}', '$user', 1, now(), 'PENDING')",
                "insert into user_data_imports (id, user_id, requested_at, last_activity_at, uploaded_bytes, " +
                    "created_pins, skipped_pins, created_boards, skipped_boards, created_tags, skipped_tags, " +
                    "issue_count, state) values ('$import', '$user', now(), now(), 0, 0, 0, 0, 0, 0, 0, 0, 'PENDING')",
                "insert into user_data_import_issues (id, import_id, kind) values ('${id(18)}', '$import', 'kind')",
            )
            .forEach { database.sqlUpdate(it).execute() }
    }

    private fun id(number: Long): UUID = UUID(0, number)

    private companion object {
        // An instant column of each referenced table, which the test narrows to the millisecond.
        private val ALTERED_COLUMNS =
            mapOf(
                "boards" to "created_at",
                "media" to "created_at",
                "persons" to "created_at",
                "pins" to "created_at",
                "tags" to "created_at",
                "user_data_imports" to "requested_at",
                "users" to "created_at",
            )
    }
}
