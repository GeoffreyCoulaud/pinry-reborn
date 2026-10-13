package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean

import fr.geoffreyCoulaud.pinryReborn.api.domain.DomainFixtures.aUser
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHash
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.EbeanMediaFrameRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.EbeanMediaRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.FrameHashBands
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.PinRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories.UserRepository
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The band lookup of ADR 0051, decision 4: found however the bits are spread, refused past 31 bits. */
class EbeanMediaFrameRepositoryTest : RepositoryTest() {
    private val repository = EbeanMediaFrameRepository(persistor)
    private val mediaRepository = EbeanMediaRepository(persistor, transactionRunner)

    /** A media on a pin of a user, the parents a frame's key requires. */
    private fun savedMedia(): Media {
        val user = UserRepository(persistor).saveUser(aUser())
        val pin =
            PinRepository(persistor)
                .savePin(
                    Pin(randomUUID(), user, null, null, "", emptyList(), emptyList(), storableNow(), storableNow())
                )
        return mediaRepository.save(
            Media.StillImage(randomUUID(), pin.id, "image/png", 1, 1, 1, "", "originals/x", storableNow())
        )
    }

    // The top bit of the first and last words is set, which a sign-extending shift would carry into an unmasked band.
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
        val mediaId = savedMedia().id
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
        val mediaId = savedMedia().id
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
        repository.save(savedMedia().id, listOf(stored))
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
        val (kept, deleted, other) = List(3) { savedMedia().id }
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
    fun `Given frames of a kept media and of a deleted one, Then the deleted one's went with it`() {
        // Given
        val kept = savedMedia()
        val gone = savedMedia()
        repository.save(kept.id, listOf(stored))
        repository.save(gone.id, listOf(stored, storedFlipping(mapOf(0 to 1))))

        // When
        mediaRepository.deleteByPinId(gone.pinId)

        // Then
        assertEquals(listOf(MediaFrame(kept.id, stored.words)), repository.findByMediaIds(listOf(kept.id, gone.id)))
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

        // When
        val plan = planOf(query.query().generatedSql)

        // Then
        (0 until FrameHashBands.EXPRESSIONS.size).forEach { band ->
            assertTrue(plan.contains("ix_media_frames_band_$band "), plan)
        }
        assertFalse(plan.contains("Seq Scan"), plan)
    }

    private companion object {
        private const val BANDS_PER_WORD = 4
        private const val BAND_BITS = 16
    }
}
