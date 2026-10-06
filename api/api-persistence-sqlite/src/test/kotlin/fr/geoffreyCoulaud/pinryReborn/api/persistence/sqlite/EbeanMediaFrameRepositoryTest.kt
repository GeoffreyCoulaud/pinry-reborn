package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHash
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.EbeanMediaFrameRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.EbeanMediaRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.FrameHashBands
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.PinRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.UserRepository
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The band lookup of ADR 0051, decision 4: found however the bits are spread, refused past 31 bits. */
class EbeanMediaFrameRepositoryTest : RepositoryTest() {
    private val repository = EbeanMediaFrameRepository(persistor)

    // The top bit of the first and last words is set, which an arithmetic shift in SQLite would carry into a band.
    private val stored =
        PdqHash(listOf(-0x123456789abcdefL, 0x0f0f0f0f0f0f0f0fL, 0x7a5a3c1e00ff55aaL, -1L), quality = 100)

    /** [stored] with [bits] of each band flipped, band 0 being the most significant 16 bits of the first word. */
    private fun storedFlipping(bits: Map<Int, Int>): PdqHash =
        PdqHash(
            stored.words.mapIndexed { word, value ->
                (0 until BANDS_PER_WORD).fold(value) { flipped, band ->
                    val count = bits[word * BANDS_PER_WORD + band] ?: 0
                    val shift = (BANDS_PER_WORD - 1 - band) * BAND_BITS
                    flipped xor (((1L shl count) - 1) shl shift)
                }
            },
            quality = 100,
        )

    @Test
    fun `Given a hash 31 bits from a stored one, spread across every band, Then the stored frame is found`() {
        // Given: two bits in each of fifteen bands and one in the last, so no band matches exactly
        val mediaId = randomUUID()
        repository.save(mediaId, listOf(stored))
        val probe = storedFlipping((0 until 15).associateWith { 2 } + (15 to 1))

        // When
        val found = repository.findNear(probe)

        // Then
        assertEquals(listOf(MediaFrame(mediaId, stored.words)), found)
    }

    @Test
    fun `Given a hash 31 bits from a stored one, concentrated in the first word, Then the stored frame is found`() {
        // Given: the first band whole and fifteen bits of the second, every other band exact
        val mediaId = randomUUID()
        repository.save(mediaId, listOf(stored))
        val probe = storedFlipping(mapOf(0 to 16, 1 to 15))

        // When
        val found = repository.findNear(probe)

        // Then
        assertEquals(listOf(MediaFrame(mediaId, stored.words)), found)
    }

    @Test
    fun `Given a hash 32 bits from a stored one in two bands, Then the index returns it and the distance refuses it`() {
        // Given: fourteen bands exact, so the index alone would call it a match
        repository.save(randomUUID(), listOf(stored))
        val probe = storedFlipping(mapOf(0 to 16, 1 to 16))

        // When
        val returnedByIndex = repository.nearQuery(probe).findCount()
        val found = repository.findNear(probe)

        // Then
        assertEquals(1, returnedByIndex)
        assertEquals(emptyList<MediaFrame>(), found)
    }

    @Test
    fun `Given frames of three media, Then those of the asked media are read and those of one are deleted`() {
        // Given
        val (kept, deleted, other) = List(3) { randomUUID() }
        val another = storedFlipping(mapOf(0 to 1))
        repository.save(kept, listOf(stored, another))
        repository.save(deleted, listOf(stored))
        repository.save(other, listOf(stored))

        // When
        repository.deleteByMediaId(deleted)
        val found = repository.findByMediaIds(listOf(kept, deleted))

        // Then
        assertEquals(setOf(MediaFrame(kept, stored.words), MediaFrame(kept, another.words)), found.toSet())
        assertEquals(listOf(MediaFrame(other, stored.words)), repository.findByMediaIds(listOf(other)))
    }

    @Test
    fun `Given frames of a stored media and of a gone one, Then the orphan sweep deletes the gone one's alone`() {
        // Given
        val user =
            UserRepository(persistor).saveUser(User(randomUUID(), createRandomString(), createdAt = storableNow()))
        val pin =
            PinRepository(persistor)
                .savePin(
                    Pin(
                        randomUUID(),
                        user,
                        null,
                        null,
                        "",
                        emptyList(),
                        emptyList(),
                        createdAt = storableNow(),
                        updatedAt = storableNow(),
                    )
                )
        val media =
            EbeanMediaRepository(persistor, transactionRunner)
                .save(Media.StillImage(randomUUID(), pin.id, "image/png", 1, 1, 1, "", "originals/x", storableNow()))
        val gone = randomUUID()
        repository.save(media.id, listOf(stored))
        repository.save(gone, listOf(stored, storedFlipping(mapOf(0 to 1))))

        // When
        val deleted = repository.deleteOrphans()

        // Then
        assertEquals(2, deleted)
        assertEquals(listOf(MediaFrame(media.id, stored.words)), repository.findByMediaIds(listOf(media.id, gone)))
    }

    @Test
    fun `Given the lookup as Ebean builds it, Then its sixteen terms spell the band expressions in order`() {
        // Given: the raw literals cannot read FrameHashBands, the detekt inventory taking plain literals alone
        val query = repository.nearQuery(stored)
        query.findList()

        // When
        val sql = query.query().generatedSql
        val positions = FrameHashBands.EXPRESSIONS.map { sql.indexOf("$it in (") }

        // Then
        assertFalse(positions.contains(-1), sql)
        assertEquals(positions.sorted(), positions, sql)
    }

    @Test
    fun `Given the lookup as Ebean builds it, Then its plan searches the sixteen band indexes and scans nothing`() {
        // Given
        val query = repository.nearQuery(stored)
        query.findList()
        val explain = database.sqlQuery("explain query plan ${query.query().generatedSql}")
        FrameHashBands.valuesNear(stored).flatten().forEachIndexed { index, value ->
            explain.setParameter(index + 1, value)
        }

        // When
        val plan = explain.findList().joinToString("\n") { "${it["detail"]}" }

        // Then: read on a table with no statistics, so this pins the plan the planner picks unaided
        (0 until FrameHashBands.EXPRESSIONS.size).forEach { band ->
            assertTrue(plan.contains("ix_media_frame_band_$band "), plan)
        }
        assertFalse(plan.contains("SCAN"), plan)
    }

    private companion object {
        private const val BANDS_PER_WORD = 4
        private const val BAND_BITS = 16
    }
}
