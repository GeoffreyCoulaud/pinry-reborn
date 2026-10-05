package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FrameSampler
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.LumaFrame
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHash
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHasher
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableImageException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaFrameRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.utilities.BaseTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import kotlin.random.Random

class FingerprintMediaTest : BaseTest() {
    private val mediaRepository = mockk<MediaRepositoryInterface>(relaxed = true)
    private val frames = InMemoryFrames()
    private val duplicates = InMemoryDuplicates()
    private val store = mockk<MediaStore>(relaxed = true)
    private val sampled = mutableMapOf<UUID, List<LumaFrame>>()
    private var refusal: Exception? = null
    private val sampler =
        object : FrameSampler {
            override fun sample(media: Media, staged: StagedFile, onFrame: (LumaFrame) -> Unit) {
                sampled.getValue(media.id).forEach(onFrame)
                refusal?.let { throw it }
            }
        }
    private val useCase = FingerprintMedia(mediaRepository, frames, duplicates, store, sampler, PassthroughRunner)

    private val staged = StagedFile("/tmp/stored", 1, "hash")

    // Noise is detailed enough to keep, and two draws are about 128 bits apart.
    private fun noise(seed: Int): LumaFrame {
        val random = Random(seed)
        return LumaFrame(SIDE, SIDE, FloatArray(SIDE * SIDE) { random.nextFloat() * 255 })
    }

    private fun uniform() = LumaFrame(SIDE, SIDE, FloatArray(SIDE * SIDE) { 128f })

    private fun media(sampledFrames: List<LumaFrame>): Media {
        val media = Media(randomUUID(), randomUUID(), "image/png", 1, 1, false, 1, "", "", Instant.EPOCH)
        sampled[media.id] = sampledFrames
        return media
    }

    /** Another media already hashed, whose frames are [seeds]' noise, comparable to whatever is drained. */
    private fun hashed(vararg seeds: Int): Media {
        val other = media(emptyList())
        frames.save(other.id, seeds.map { PdqHasher.hash(noise(it)) })
        every { mediaRepository.findComparable(any(), match { other.id in it }, VERSION) } returns listOf(other)
        return other
    }

    private fun drain(vararg queue: Media, renewLease: () -> Unit = {}) {
        every { mediaRepository.findNewestNotFingerprinted(VERSION) } returnsMany queue.toList() + null
        every { store.stageStored(any()) } returns staged
        useCase.drain(renewLease)
    }

    @Test
    fun `Given two outdated media, Then each is hashed and stamped and the lease renewed after each`() {
        // Given
        val (first, second) = List(2) { media(listOf(noise(it))) }
        var renewals = 0

        // When
        drain(first, second) { renewals++ }

        // Then
        assertEquals(1 to 1, frames.of(first.id).size to frames.of(second.id).size)
        verify { mediaRepository.markFingerprinted(first.id, VERSION) }
        verify { mediaRepository.markFingerprinted(second.id, VERSION) }
        verify(exactly = 2) { store.discard(staged) }
        assertEquals(2, renewals)
    }

    @Test
    fun `Given a uniform frame and the same frame twice, Then one frame is stored`() {
        // Given
        val media = media(listOf(noise(1), uniform(), noise(1)))

        // When
        drain(media)

        // Then
        assertEquals(listOf(PdqHasher.hash(noise(1)).words), frames.of(media.id))
    }

    @Test
    fun `Given a media four fifths of whose frames are found in another's and back, Then their pins are paired`() {
        // Given: one frame of five unmatched each way, the 80 percent bound
        val other = hashed(1, 2, 3, 4, 9)
        val media = media(listOf(noise(1), noise(2), noise(3), noise(4), noise(5)))

        // When
        drain(media)

        // Then
        assertEquals(setOf(setOf(media.pinId, other.pinId)), duplicates.pending)
    }

    @Test
    fun `Given a media found in another whose own frames are mostly not found back, Then nothing is paired`() {
        // Given
        hashed(1, 2, 3, 4)
        val media = media(listOf(noise(1)))

        // When
        drain(media)

        // Then
        assertEquals(emptySet<Set<UUID>>(), duplicates.pending)
    }

    @Test
    fun `Given a media whose frames are mostly not found in another, Then nothing is paired`() {
        // Given
        hashed(1)
        val media = media(listOf(noise(1), noise(2), noise(3), noise(4)))

        // When
        drain(media)

        // Then
        assertEquals(emptySet<Set<UUID>>(), duplicates.pending)
    }

    @Test
    fun `Given a media hashed again, Then its old frames and pending pairs go and a rejected pair stays`() {
        // Given
        val media = media(listOf(noise(1)))
        frames.save(media.id, listOf(PdqHasher.hash(noise(2))))
        val (pending, rejected) = List(2) { randomUUID() }
        duplicates.addMissing(media.pinId, listOf(pending, rejected))
        duplicates.rejected += setOf(media.pinId, rejected)

        // When
        drain(media)

        // Then
        assertEquals(listOf(PdqHasher.hash(noise(1)).words), frames.of(media.id))
        assertEquals(setOf(setOf(media.pinId, rejected)), duplicates.pairs)
    }

    @ParameterizedTest
    @ValueSource(strings = ["image", "video", "frame"])
    fun `Given a decoder that refuses the media after a frame, Then it is stamped with no frames`(decoder: String) {
        // Given
        refusal = when (decoder) {
            "image" -> UndecodableImageException("vips refused it")
            "video" -> UndecodableVideoException("ffmpeg refused it")
            else -> IOException("Truncated raster")
        }
        val media = media(listOf(noise(1)))

        // When
        drain(media)

        // Then
        assertEquals(emptyList<List<Long>>(), frames.of(media.id))
        verify { mediaRepository.markFingerprinted(media.id, VERSION) }
        verify { store.discard(staged) }
    }

    private class InMemoryFrames : MediaFrameRepositoryInterface {
        private val stored = mutableListOf<MediaFrame>()

        fun of(mediaId: UUID) = stored.filter { it.mediaId == mediaId }.map { it.words }

        override fun save(mediaId: UUID, hashes: Collection<PdqHash>) {
            stored += hashes.map { MediaFrame(mediaId, it.words) }
        }

        override fun findNear(hash: PdqHash) =
            stored.filter { hash.distanceTo(PdqHash(it.words, 0)) <= PdqHasher.MATCH_DISTANCE }

        override fun findByMediaIds(mediaIds: Collection<UUID>) = stored.filter { it.mediaId in mediaIds }

        override fun deleteByMediaId(mediaId: UUID) {
            stored.removeAll { it.mediaId == mediaId }
        }

        override fun deleteOrphans(): Int = error("not used")
    }

    private class InMemoryDuplicates : PinDuplicateRepositoryInterface {
        val pairs = mutableSetOf<Set<UUID>>()
        val rejected = mutableSetOf<Set<UUID>>()
        val pending get() = pairs - rejected

        override fun deletePending(pinId: UUID) {
            pairs.removeAll { pinId in it && it !in rejected }
        }

        override fun addMissing(pinId: UUID, otherPinIds: Collection<UUID>) {
            pairs += otherPinIds.map { setOf(pinId, it) }
        }

        override fun deleteOrphans(): Int = error("not used")

        override fun findShownFor(pinId: UUID): Map<UUID, Boolean> = error("not used")

        override fun findPinIdsWithPending(pinIds: Collection<UUID>): Set<UUID> = error("not used")

        override fun setRejected(pinId: UUID, otherPinId: UUID, rejectedAt: Instant?): Boolean = error("not used")
    }

    private object PassthroughRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private companion object {
        const val SIDE = 64
        const val VERSION = FingerprintMedia.FINGERPRINT_VERSION
    }
}
