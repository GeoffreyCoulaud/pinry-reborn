package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories

import jakarta.persistence.PersistenceException
import java.sql.SQLException

/**
 * Tells a unique-index violation apart from every other persistence failure, for the repositories that answer one with
 * a domain error.
 *
 * Ebean wraps the driver's `SQLException` in a `PersistenceException`, pinned by the duplicate-insert repository tests.
 * The SQLState is the discriminator: a NOT NULL violation or a dropped connection must stay a genuine 500, not become
 * the caller's 409.
 */
internal object SqliteConstraintViolations {
    /**
     * Always throws: [toDomainError] for a unique-constraint violation, [error] itself otherwise. The rethrow lives
     * here because no real store produces a non-unique failure through a public save.
     */
    fun translateUniqueConstraint(
        error: PersistenceException,
        toDomainError: (PersistenceException) -> Throwable,
    ): Nothing {
        if (isUniqueConstraint(error)) throw toDomainError(error)
        throw error
    }

    /** True when [error] is PostgreSQL refusing a row that already exists under a unique index. */
    private fun isUniqueConstraint(error: PersistenceException): Boolean =
        (error.cause as? SQLException)?.sqlState == UNIQUE_VIOLATION

    /** PostgreSQL's `unique_violation` (postgresql.org/docs/18/errcodes-appendix.html). */
    private const val UNIQUE_VIOLATION = "23505"
}
