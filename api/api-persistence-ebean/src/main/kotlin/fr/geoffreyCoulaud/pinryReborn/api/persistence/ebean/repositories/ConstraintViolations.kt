package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories

import jakarta.persistence.PersistenceException
import org.postgresql.util.PSQLException

/** Tells the violation of one named constraint apart from every other persistence failure, which stays a 500. */
internal object ConstraintViolations {
    /** Always throws: [toDomainError] for a violation of [constraint], [error] itself otherwise. */
    fun translateUniqueConstraint(
        error: PersistenceException,
        constraint: String,
        toDomainError: (PersistenceException) -> Throwable,
    ): Nothing {
        if (violatedConstraint(error) == constraint) throw toDomainError(error)
        throw error
    }

    // PostgreSQL names the constraint a row violates, its primary key included; Ebean wraps the driver's exception.
    private fun violatedConstraint(error: PersistenceException): String? =
        (error.cause as? PSQLException)?.serverErrorMessage?.constraint
}
