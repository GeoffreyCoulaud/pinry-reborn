package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class DatasourcePasswordCheckTest {
    @Test
    fun `Given a password left as its placeholder, Then the boot is refused naming the variable`() {
        // Given: avaje-config keeps `${DB_PASSWORD}` as written when the variable is unset
        val unresolved = "\${DB_PASSWORD}"

        // When
        val error =
            assertThrows(IllegalStateException::class.java) { DatasourcePasswordCheck.verifyResolved(unresolved) }

        // Then: the operator reads the variable to set
        assertEquals("datasource.db.password is unresolved: set the environment variable DB_PASSWORD", error.message)
    }

    @Test
    fun `Given a password the environment resolved, Then the boot goes on`() {
        // Given
        val resolved = "a password"

        // When / Then
        assertDoesNotThrow { DatasourcePasswordCheck.verifyResolved(resolved) }
    }

    @Test
    fun `Given no password declared, Then the boot goes on`() {
        // Given: the application suite's properties declare none, ebean-test supplying its own

        // When / Then
        assertDoesNotThrow { DatasourcePasswordCheck.verifyResolved(null) }
    }

    @Test
    fun `Given this suite's properties, Then the configured password passes without a connection`() {
        // Given: application-test.properties declares no password, ebean-test supplying its own

        // When / Then
        assertDoesNotThrow { DatasourcePasswordCheck.verifyConfigured() }
    }
}
