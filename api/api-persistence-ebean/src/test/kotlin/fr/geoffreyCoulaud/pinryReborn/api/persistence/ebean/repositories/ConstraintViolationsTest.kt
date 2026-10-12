package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories

import jakarta.persistence.PersistenceException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import org.postgresql.util.ServerErrorMessage

/** The failures no public save produces against a real store; the repository tests pin each caller's translation. */
class ConstraintViolationsTest {
    private class DomainError(cause: Throwable) : RuntimeException(cause)

    private val expectedConstraint = "ux_users_name"

    // Ebean wraps the driver's exception; the server reports its fields NUL-separated, `n` naming the constraint.
    private fun serverFailure(state: PSQLState, constraint: String?): PersistenceException {
        val fields = listOfNotNull("SERROR", "C${state.state}", "Mrefused", constraint?.let { "n$it" })
        return PersistenceException("refused", PSQLException(ServerErrorMessage(fields.joinToString("\u0000"))))
    }

    private fun assertRethrownUnchanged(error: PersistenceException) {
        var factoryCalled = false
        val thrown =
            assertThrows(PersistenceException::class.java) {
                ConstraintViolations.translateUniqueConstraint(error, expectedConstraint) {
                    factoryCalled = true
                    DomainError(it)
                }
            }
        assertSame(error, thrown)
        assertFalse(factoryCalled)
    }

    @Test
    fun `Given a violation of the constraint it names, Then translateUniqueConstraint throws the caller's error`() {
        // Given
        val error = serverFailure(PSQLState.UNIQUE_VIOLATION, expectedConstraint)
        // When / Then: the caller's factory decides the type, and receives the failure as its cause
        val thrown =
            assertThrows(DomainError::class.java) {
                ConstraintViolations.translateUniqueConstraint(error, expectedConstraint) { DomainError(it) }
            }
        assertSame(error, thrown.cause)
    }

    @Test
    fun `Given a unique violation of another constraint, Then translateUniqueConstraint rethrows it unchanged`() {
        // Given: the primary key is a unique constraint of the same table, and no taken name
        val error = serverFailure(PSQLState.UNIQUE_VIOLATION, "pk_users")
        // When / Then
        assertRethrownUnchanged(error)
    }

    @Test
    fun `Given a NOT NULL violation, naming no constraint, Then translateUniqueConstraint rethrows it unchanged`() {
        // Given
        val error = serverFailure(PSQLState.NOT_NULL_VIOLATION, constraint = null)
        // When / Then
        assertRethrownUnchanged(error)
    }

    @Test
    fun `Given a persistence failure with no SQL cause, Then translateUniqueConstraint rethrows it unchanged`() {
        // Given
        val error = PersistenceException("connection refused")
        // When / Then
        assertRethrownUnchanged(error)
    }
}
