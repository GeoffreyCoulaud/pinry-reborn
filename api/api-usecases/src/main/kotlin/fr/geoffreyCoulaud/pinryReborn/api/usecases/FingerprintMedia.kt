package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FrameSampler
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.ImageProbeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHash
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHasher
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoProcessorException
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaFrameRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped
import java.io.IOException
import java.util.UUID

/** Hashes every media not hashed at [FINGERPRINT_VERSION], and pairs its pin with its duplicates' (ADR 0051). */
@ApplicationScoped
@Suppress("LongParameterList") // CDI-injected: every parameter is a collaborator provided by the container.
class FingerprintMedia(
    private val mediaRepository: MediaRepositoryInterface,
    private val frameRepository: MediaFrameRepositoryInterface,
    private val duplicateRepository: PinDuplicateRepositoryInterface,
    private val mediaStore: MediaStore,
    private val frameSampler: FrameSampler,
    private val transactionRunner: TransactionRunner,
) {
    /** Newest first, reading again until none is left, so a media saved meanwhile is drained too. */
    fun drain(renewLease: () -> Unit) {
        generateSequence { mediaRepository.findNewestNotFingerprinted(FINGERPRINT_VERSION) }
            .forEach { media ->
                renewLease()
                val hashes = hashesOf(media, renewLease)
                // Outside the transaction, so the band lookups never hold the write lock; its own pin is not compared.
                val duplicates = duplicatesOf(media, hashes)
                transactionRunner.inTransaction {
                    frameRepository.deleteByMediaId(media.id)
                    duplicateRepository.deletePending(media.pinId)
                    frameRepository.save(media.id, hashes)
                    duplicateRepository.addMissing(media.pinId, duplicates)
                    mediaRepository.markFingerprinted(media.id, FINGERPRINT_VERSION)
                }
            }
    }

    // An undecodable media is stamped with no frames, so it never holds the drain back.
    private fun hashesOf(media: Media, renewLease: () -> Unit): List<PdqHash> =
        try {
            sampled(media, renewLease).filter { it.quality > PdqHasher.DISCARDED_QUALITY }.distinctBy { it.words }
        } catch (refused: ImageProbeException) {
            undecodable(media, refused)
        } catch (refused: VideoProcessorException) {
            undecodable(media, refused)
        } catch (refused: IOException) {
            undecodable(media, refused)
        }

    // Renewed per frame, each frame being a decoder run that may last the whole lease.
    private fun sampled(media: Media, renewLease: () -> Unit): List<PdqHash> {
        val staged = mediaStore.stageStored(media)
        try {
            val hashes = mutableListOf<PdqHash>()
            frameSampler.sample(media, staged) { frame ->
                hashes += PdqHasher.hash(frame)
                renewLease()
            }
            return hashes
        } finally {
            mediaStore.discardQuietly(staged)
        }
    }

    private fun undecodable(media: Media, cause: Exception): List<PdqHash> {
        logger.warn(cause) { "media ${media.id}: undecodable, stamped with no frames" }
        return emptyList()
    }

    private fun duplicatesOf(media: Media, hashes: List<PdqHash>): List<UUID> {
        val near = hashes.flatMap { frameRepository.findNear(it) }.map { it.mediaId }.toSet()
        val comparable = mediaRepository.findComparable(media, near, FINGERPRINT_VERSION)
        val framesOf = frameRepository.findByMediaIds(comparable.map { it.id }).groupBy({ it.mediaId }, { it.words })
        val own = hashes.map { it.words }
        return comparable.filter { isPair(own, framesOf.getValue(it.id)) }.map { it.pinId }
    }

    private fun isPair(own: List<List<Long>>, other: List<List<Long>>) =
        foundEnough(own, among = other) && foundEnough(other, among = own)

    private fun foundEnough(frames: List<List<Long>>, among: List<List<Long>>): Boolean {
        val found = frames.count { frame -> among.any { PdqHash.distance(frame, it) <= PdqHasher.MATCH_DISTANCE } }
        return found * PERCENT >= frames.size * PAIR_PERCENT
    }

    companion object {
        /** Raising it hashes every media again (ADR 0051, decision 6). */
        const val FINGERPRINT_VERSION = 1

        // Of the unique frames of each media, the share that finds a frame of the other (ADR 0051, decision 3).
        private const val PAIR_PERCENT = 80
        private const val PERCENT = 100

        private val logger = KotlinLogging.logger {}
    }
}
