package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories

import jakarta.persistence.PersistenceException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

/**
 * Focused unit tests for the collision decision shared by the repositories that answer a unique-index violation with a
 * domain error.
 *
 * They do not extend [fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.RepositoryTest]: the decision under test is
 * a pure function of the exception's cause structure, a `PersistenceException` wrapping the driver's `PSQLException`
 * whose SQLState discriminates `unique_violation` from the other constraint codes (NOT NULL, FOREIGN KEY, ...). The
 * repository tests pin the end-to-end answer each caller asks for, as in
 * [fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.UserPasswordHashRepositoryTest] and
 * [fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.UserDataExportRepositoryTest]. These tests cover the decision
 * itself and the rethrow of unrelated failures, which cannot be produced through a public save against a real store.
 */
class SqliteConstraintViolationsTest {
    private class DomainError(cause: Throwable) : RuntimeException(cause)

    private fun uniqueConstraintFailure() =
        PersistenceException(
            "duplicate key value violates unique constraint",
            PSQLException("duplicate key value violates unique constraint", PSQLState.UNIQUE_VIOLATION),
        )

    private fun notNullConstraintFailure() =
        PersistenceException(
            "null value violates not-null constraint",
            PSQLException("null value violates not-null constraint", PSQLState.NOT_NULL_VIOLATION),
        )

    @Test
    fun `Given a unique-constraint violation, Then translateUniqueConstraint throws the caller's domain error`() {
        // Given: the structure Ebean raises on PostgreSQL, a PersistenceException wrapping a
        // PSQLException whose SQLState is unique_violation
        val error = uniqueConstraintFailure()
        // When / Then: the caller's factory decides the type, and receives the failure as its cause
        val thrown =
            assertThrows(DomainError::class.java) {
                SqliteConstraintViolations.translateUniqueConstraint(error) { DomainError(it) }
            }
        assertSame(error, thrown.cause)
    }

    @Test
    fun `Given a non-unique persistence failure, Then translateUniqueConstraint rethrows it unchanged`() {
        // Given: a NOT NULL violation must NOT be reported as a 409, and only its SQLState parts it from the unique
        // case
        val error = notNullConstraintFailure()
        var factoryCalled = false
        // When / Then: the exact same instance propagates, no translation
        val thrown =
            assertThrows(PersistenceException::class.java) {
                SqliteConstraintViolations.translateUniqueConstraint(error) {
                    factoryCalled = true
                    DomainError(it)
                }
            }
        assertSame(error, thrown)
        assertFalse(factoryCalled)
    }

    @Test
    fun `Given a persistence failure with no SQL cause, Then translateUniqueConstraint rethrows it unchanged`() {
        // Given
        val error = PersistenceException("connection refused")
        var factoryCalled = false
        // When / Then
        val thrown =
            assertThrows(PersistenceException::class.java) {
                SqliteConstraintViolations.translateUniqueConstraint(error) {
                    factoryCalled = true
                    DomainError(it)
                }
            }
        assertSame(error, thrown)
        assertFalse(factoryCalled)
    }
}
