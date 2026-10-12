package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.EbeanPersistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.EbeanTransactionControl
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.EbeanTransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.utilities.TestTime
import io.ebean.DB
import io.ebean.Database
import jakarta.persistence.PersistenceException
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach

@Suppress("AbstractClassCanBeConcreteClass") // Abstract by intent: a shared test base for concrete subclasses.
abstract class RepositoryTest {
    protected val database: Database
        get() = DB.getDefault()

    // The CRUD repositories inject Persistor in production; tests wire the same adapter here so a
    // repo under test sees the write capability through the port, not the Database type it is being
    // weaned off. Each access wraps the shared database; EbeanPersistor is stateless.
    protected val persistor: Persistor
        get() = EbeanPersistor(database)

    // The transaction runner injects TransactionControl in production; tests wire the same adapter here.
    // Each access wraps the shared database; EbeanTransactionControl is stateless.
    protected val transactionControl: TransactionControl
        get() = EbeanTransactionControl(database)

    // The adapters that need a transaction boundary inject TransactionRunner in production; tests wire
    // the same adapter here. Stateless like the two above.
    protected val transactionRunner: TransactionRunner
        get() = EbeanTransactionRunner(transactionControl)

    /**
     * An instant an entity can carry across a save-then-read unchanged.
     *
     * TestTime.now is millisecond-coarse, so it round-trips through the store (SystemClock truncates for the same
     * reason; a nanosecond-precision instant would come back different and break equality on a re-read entity).
     */
    protected fun storableNow(): Instant = TestTime.now

    /** Asserts the database refuses [write], its error naming the CHECK constraint [constraint]. */
    protected fun assertRefusedByCheck(constraint: String, write: () -> Unit) {
        val thrown = assertThrows(PersistenceException::class.java) { write() }
        val error = thrown.cause?.message.orEmpty()
        val namesTheConstraint = Regex("\\b${Regex.escape(constraint)}\\b").containsMatchIn(error)
        assertTrue(namesTheConstraint, "The error does not name $constraint: $error")
    }

    /**
     * PostgreSQL's generic plan for [sql], sequential scans priced out so the plan shows the indexes it can use. Each
     * `?` becomes a numbered placeholder, executed with nulls a generic plan never reads.
     */
    protected fun planOf(sql: String): String {
        val numbered = sql.split('?').reduceIndexed { position, statement, rest -> "$statement\$$position$rest" }
        val arguments = List(sql.count { character -> character == '?' }) { "null" }
        // `set local` ends with the transaction, closed here without a commit; a prepared statement outlives it.
        database.beginTransaction().use {
            database.sqlUpdate("set local enable_seqscan = off").execute()
            database.sqlUpdate("set local plan_cache_mode = force_generic_plan").execute()
            database.sqlUpdate("prepare plan_probe as $numbered").execute()
            try {
                return database
                    .sqlQuery("explain execute plan_probe(${arguments.joinToString()})")
                    .findList()
                    .joinToString("\n") { row -> "${row.values.single()}" }
            } finally {
                database.sqlUpdate("deallocate plan_probe").execute()
            }
        }
    }

    /** Empties every table but `db_migration`, which Ebean needs, in one statement. */
    @BeforeEach
    fun truncateAllTables() {
        database.sqlUpdate("truncate table ${tables().joinToString()} cascade").execute()
    }

    /** Every table of the connection's schema but `db_migration`. */
    private fun tables(): List<String> =
        database
            .sqlQuery(
                "select table_name from information_schema.tables " +
                    "where table_schema = current_schema() and table_type = 'BASE TABLE' and table_name <> ?"
            )
            .setParameter(MIGRATION_TABLE)
            .findList()
            .map { it.getString("table_name") }

    private companion object {
        const val MIGRATION_TABLE = "db_migration"
    }
}
