package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QMediaFrameModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QMediaModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.MediaConfig
import fr.geoffreyCoulaud.pinryReborn.api.usecases.DeletePinMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.FingerprintMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ReapFingerprints
import fr.geoffreyCoulaud.pinryReborn.api.usecases.SetPinMedia
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** The worker's drain over real media, which the `ffmpeg` on the `PATH` draws (ADR 0051). */
@QuarkusTest
@TestProfile(MediaHostingDataDirTestProfile::class)
class DuplicateFindingIntegrationTest : IntegrationTest() {
    @Inject
    lateinit var pinCreator: PinCreator

    @Inject
    lateinit var setPinMedia: SetPinMedia

    @Inject
    lateinit var fingerprintMedia: FingerprintMedia

    @Inject
    lateinit var deletePinMedia: DeletePinMedia

    @Inject
    lateinit var reapFingerprints: ReapFingerprints

    @Inject
    lateinit var mediaConfig: MediaConfig

    @TempDir
    lateinit var directory: Path

    private fun pinned(user: User, file: Path): Media {
        val pin = pinCreator.createPin(user, "https://example.com", null, "", emptyList())
        return Files.newInputStream(file).use { setPinMedia.set(pin.id, user, it).media }
    }

    private fun drawn(name: String, vararg arguments: String): Path {
        val output = directory.resolve(name)
        val command = listOf("ffmpeg", "-v", "error") + arguments + "$output"
        assertEquals(0, ProcessBuilder(command).inheritIO().start().waitFor())
        return output
    }

    private fun still() = drawn("still.png", "-f", "lavfi", "-i", "mandelbrot=size=640x480", "-frames:v", "1")

    private fun video(seconds: Int) =
        drawn("video.mp4", "-f", "lavfi", "-i", "mandelbrot=size=320x240:rate=25", "-t", "$seconds", *H264)

    // Each pair as its two pin ids, and whether the user rejected it.
    private fun pairs() =
        QPinDuplicateModel().findList().map { setOf(it.firstPinId, it.secondPinId) to (it.rejectedAt != null) }.toSet()

    private fun pending(vararg media: Media) = setOf(media.map { it.pinId }.toSet() to false)

    private fun framesOf(media: Media) = QMediaFrameModel().mediaId.equalTo(media.id).findCount()

    private fun versionOf(media: Media) = QMediaModel().id.equalTo(media.id).findOne()?.fingerprintVersion

    private fun outdate(vararg media: Media) {
        QMediaModel().id.isIn(media.map { it.id }).asUpdate().set("fingerprintVersion", 0).update()
    }

    @Test
    fun `Given a still, its half-size copy and the same still by another user, Then the author's two pins pair`() {
        // Given
        val author = createAuthenticatedUser().user
        val still = still()
        val half = drawn("half.jpg", "-i", "$still", "-vf", "scale=iw/2:-1", "-q:v", "5")

        // When
        val original = pinned(author, still)
        val copy = pinned(author, half)
        pinned(createAuthenticatedUser().user, still)
        awaitFingerprintDrain()

        // Then
        assertEquals(pending(original, copy), pairs())
        assertEquals(1, framesOf(original))
    }

    @Test
    fun `Given a still and a video whose every frame is that still, Then nothing is paired`() {
        // Given
        val author = createAuthenticatedUser().user
        val still = still()
        val looped = drawn("looped.mp4", "-loop", "1", "-i", "$still", "-t", "3", *H264)

        // When
        pinned(author, still)
        pinned(author, looped)
        awaitFingerprintDrain()

        // Then
        assertEquals(emptySet<Any>(), pairs())
    }

    @Test
    fun `Given a video, itself re-encoded and its three-second cut, Then the video pairs with the re-encoding alone`() {
        // Given
        val author = createAuthenticatedUser().user
        val video = video(seconds = 10)
        val reencoded = drawn("reencoded.mp4", "-i", "$video", "-vf", "scale=iw/2:-2", "-crf", "35", *H264)
        val cut = drawn("cut.mp4", "-ss", "2", "-i", "$video", "-t", "3", *H264)

        // When
        val original = pinned(author, video)
        val copy = pinned(author, reencoded)
        pinned(author, cut)
        awaitFingerprintDrain()

        // Then
        assertEquals(pending(original, copy), pairs())
    }

    @Test
    fun `Given two black videos, Then their frames are all dropped, both are stamped and nothing is paired`() {
        // Given
        val author = createAuthenticatedUser().user
        val black = drawn("black.mp4", "-f", "lavfi", "-i", "color=black:size=320x240:duration=3", *H264)

        // When
        val both = List(2) { pinned(author, black) }
        awaitFingerprintDrain()

        // Then
        assertEquals(List(2) { 0 to FingerprintMedia.FINGERPRINT_VERSION }, both.map { framesOf(it) to versionOf(it) })
        assertEquals(emptySet<Any>(), pairs())
    }

    @Test
    fun `Given a corrupt media between two good ones, Then the good ones are hashed and paired, the corrupt not`() {
        // Given: three copies drained once, the middle one's original then overwritten
        val author = createAuthenticatedUser().user
        val still = still()
        val (first, corrupt, last) = List(3) { pinned(author, still) }
        awaitFingerprintDrain()
        Files.writeString(Path.of(mediaConfig.dataDir()).resolve(corrupt.storageKey), "not an image")
        QPinDuplicateModel().delete()
        outdate(first, corrupt, last)

        // When
        fingerprintMedia.drain {}

        // Then
        val all = listOf(first, corrupt, last)
        assertEquals(listOf(1, 0, 1), all.map { framesOf(it) })
        assertEquals(List(3) { FingerprintMedia.FINGERPRINT_VERSION }, all.map { versionOf(it) })
        assertEquals(pending(first, last), pairs())
    }

    @Test
    fun `Given a raised version, Then hashing one media again drops its pending pair and keeps its rejected one`() {
        // Given: three copies paired with each other, the first and the last pair rejected
        val author = createAuthenticatedUser().user
        val still = still()
        val (first, middle, last) = List(3) { pinned(author, still) }
        awaitFingerprintDrain()
        QPinDuplicateModel().firstPinId.isIn(first.pinId, last.pinId).secondPinId.isIn(first.pinId, last.pinId)
            .asUpdate().set("rejectedAt", Instant.EPOCH).update()
        outdate(first, middle, last)
        val atEachRenewal = mutableListOf<Set<Pair<Set<UUID>, Boolean>>>()

        // When: the newest is hashed first, and compared with none of the outdated two
        fingerprintMedia.drain { atEachRenewal.add(pairs()) }

        // Then: only the newest hashed leaves the first two paired alone
        val rejected = setOf(first.pinId, last.pinId) to true
        assertTrue(pending(first, middle) + rejected in atEachRenewal, "$atEachRenewal")
        assertEquals(pending(first, middle) + pending(middle, last) + rejected, pairs())
    }

    @Test
    fun `Given a media replaced on its pin, Then the new one is hashed and the sweep deletes the old one's frames`() {
        // Given
        val author = createAuthenticatedUser().user
        val old = pinned(author, still())
        awaitFingerprintDrain()
        val other = drawn("other.png", "-f", "lavfi", "-i", "testsrc2=size=640x480", "-frames:v", "1")

        // When
        val replacement = Files.newInputStream(other).use { setPinMedia.set(old.pinId, author, it).media }
        awaitFingerprintDrain()
        val beforeTheSweep = framesOf(old)
        reapFingerprints.reap()

        // Then
        assertEquals(listOf(1, 0, 1), listOf(beforeTheSweep, framesOf(old), framesOf(replacement)))
        assertEquals(FingerprintMedia.FINGERPRINT_VERSION, versionOf(replacement))
    }

    @Test
    fun `Given two pinned copies, Then deleting one's media deletes their pending pair`() {
        // Given
        val author = createAuthenticatedUser().user
        val still = still()
        val (kept, emptied) = List(2) { pinned(author, still) }
        awaitFingerprintDrain()
        val beforeTheDelete = pairs()

        // When
        deletePinMedia.delete(emptied.pinId, author)

        // Then
        assertEquals(listOf(pending(kept, emptied), emptySet()), listOf(beforeTheDelete, pairs()))
    }

    @Test
    fun `Given two pinned copies and one's media row deleted alone, Then the sweep deletes their pending pair`() {
        // Given: a crash between `DeletePinMedia`'s media delete and its pairs' delete
        val author = createAuthenticatedUser().user
        val still = still()
        val (kept, emptied) = List(2) { pinned(author, still) }
        awaitFingerprintDrain()
        val beforeTheSweep = pairs()
        QMediaModel().id.equalTo(emptied.id).delete()

        // When
        reapFingerprints.reap()

        // Then
        assertEquals(listOf(pending(kept, emptied), emptySet()), listOf(beforeTheSweep, pairs()))
    }

    private companion object {
        val H264 = arrayOf("-c:v", "libx264", "-pix_fmt", "yuv420p")
    }
}
