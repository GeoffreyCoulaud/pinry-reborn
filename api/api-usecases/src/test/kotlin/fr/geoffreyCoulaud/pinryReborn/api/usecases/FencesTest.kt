package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.PassthroughTransactionRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FencesTest {
    private val runner = PassthroughTransactionRunner()
    private val written = mutableListOf<Row>()

    private data class Row(val active: Boolean, val version: Int)

    @Test
    fun `Given a held row, Then fenced reads and writes it in one transaction and answers the write`() {
        // Given
        var readIn: Int? = null
        var writtenIn: Int? = null

        // When
        val result =
            runner.fenced(
                read = {
                    readIn = runner.current
                    Row(active = true, version = 0)
                },
                held = { it.active },
                update = { it.copy(version = it.version + 1) },
                write = {
                    writtenIn = runner.current
                    written += it
                    it
                },
            )

        // Then: the read and the write carry the same transaction number, and there is one
        assertEquals(Row(active = true, version = 1), result)
        assertEquals(listOf(Row(active = true, version = 1)), written)
        assertNotNull(readIn)
        assertEquals(readIn, writtenIn)
    }

    @Test
    fun `Given a row the predicate refuses, Then fenced writes nothing and answers null`() {
        val result =
            runner.fenced({ Row(active = false, version = 0) }, { it.active }, { it }) {
                written += it
                it
            }
        assertNull(result)
        assertEquals(emptyList<Row>(), written)
    }

    @Test
    fun `Given no row, Then fenced writes nothing and answers null`() {
        val result =
            runner.fenced<Row>({ null }, { it.active }, { it }) {
                written += it
                it
            }
        assertNull(result)
        assertEquals(emptyList<Row>(), written)
    }

    @Test
    fun `Given a held row, Then fencedOver writes the update and answers the row it replaced`() {
        // Given
        var readIn: Int? = null
        var writtenIn: Int? = null

        // When
        val result =
            runner.fencedOver(
                read = {
                    readIn = runner.current
                    Row(active = true, version = 0)
                },
                held = { it.active },
                update = { it.copy(version = it.version + 1) },
                write = {
                    writtenIn = runner.current
                    written += it
                    it
                },
            )

        // Then
        assertEquals(Row(active = true, version = 0), result)
        assertEquals(listOf(Row(active = true, version = 1)), written)
        assertNotNull(readIn)
        assertEquals(readIn, writtenIn)
    }

    @Test
    fun `Given a row the predicate refuses, Then fencedOver writes nothing and answers null`() {
        val refused = Row(active = false, version = 0)
        val result =
            runner.fencedOver({ refused }, { it.active }, { it }) {
                written += it
                it
            }
        assertNull(result)
        assertEquals(emptyList<Row>(), written)
    }

    @Test
    fun `Given no row, Then fencedOver writes nothing and answers null`() {
        val result =
            runner.fencedOver<Row>({ null }, { it.active }, { it }) {
                written += it
                it
            }
        assertNull(result)
        assertEquals(emptyList<Row>(), written)
    }
}
