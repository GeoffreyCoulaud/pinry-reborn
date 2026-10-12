package fr.geoffreyCoulaud.pinryReborn.api.application

import java.io.File
import java.util.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * ebean-test starts `postgres:<version>` on a workstation and defaults to 16, under ADR 0059's floor, so each suite
 * declares the version `compose.yml` ships.
 */
class TestDatabaseVersionDeclarationTest {
    @Test
    fun `Given both suites' test properties, Then ebean-test's PostgreSQL version is compose yml's tag`() {
        // Given
        val shipped = POSTGRES_IMAGE.find(File(COMPOSE).readText())?.groupValues?.get(1)
        assertNotNull(shipped, "No postgres image in $COMPOSE")

        // When
        val declared = SUITE_PROPERTIES.associateWith { path ->
            Properties().apply { File(path).reader().use(::load) }.getProperty(VERSION_KEY)
        }

        // Then
        assertEquals(SUITE_PROPERTIES.associateWith { shipped }, declared)
    }

    private companion object {
        const val COMPOSE = "../../compose.yml"
        const val VERSION_KEY = "ebean.test.postgres.version"
        val POSTGRES_IMAGE = Regex("""^\s+postgres:\s*\n\s+image:\s*postgres:([^@\s]+)@""", RegexOption.MULTILINE)
        val SUITE_PROPERTIES =
            listOf(
                "src/test/resources/application.properties",
                "../api-persistence-ebean/src/test/resources/application-test.properties",
            )
    }
}
