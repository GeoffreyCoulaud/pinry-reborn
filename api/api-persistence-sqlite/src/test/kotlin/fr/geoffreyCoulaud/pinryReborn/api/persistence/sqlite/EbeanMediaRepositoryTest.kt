package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.EbeanMediaRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.PinRepository
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.UserRepository
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EbeanMediaRepositoryTest : RepositoryTest() {
    private val repository = EbeanMediaRepository(persistor, transactionRunner)
    private val userRepository = UserRepository(persistor)
    private val pinRepository = PinRepository(persistor)

    private fun savedUser() =
        userRepository.saveUser(User(randomUUID(), createRandomString(), createdAt = storableNow()))

    private fun savedPin(user: User = savedUser()): Pin {
        return pinRepository.savePin(
            Pin(
                randomUUID(),
                user,
                "https://ctx",
                null,
                "desc",
                emptyList(),
                emptyList(),
                createdAt = storableNow(),
                updatedAt = storableNow(),
            )
        )
    }

    private fun mediaFor(pinId: UUID, hash: String = "h") =
        Media.StillImage(
            id = randomUUID(),
            pinId = pinId,
            mimeType = "image/png",
            width = 1,
            height = 1,
            byteSize = 1,
            contentHash = hash,
            storageKey = "originals/x/$pinId/i.png",
            createdAt = Instant.parse("2026-07-08T00:00:00Z"),
        )

    private fun animatedFor(pinId: UUID) =
        mediaFor(pinId).run {
            Media.AnimatedImage(id, pinId, "image/gif", width, height, byteSize, contentHash, storageKey, createdAt, 3)
        }

    @Test
    fun `Given a new image, Then save persists it and findByPinId returns it`() {
        val pin = savedPin()
        val saved = repository.save(mediaFor(pin.id))
        assertEquals(saved, repository.findByPinId(pin.id))
    }

    @Test
    fun `Given an animated image, Then findByPinId reads it back animated, with its frames`() {
        val pin = savedPin()
        val saved = repository.save(animatedFor(pin.id))
        assertEquals(saved, repository.findByPinId(pin.id))
    }

    @Test
    fun `Given a video, Then findByPinId reads back its frames, duration, rates and sound`() {
        val pin = savedPin()
        val video =
            mediaFor(pin.id).run {
                Media.Video(
                    id,
                    pinId,
                    "video/mp4",
                    width,
                    height,
                    byteSize,
                    contentHash,
                    storageKey,
                    createdAt,
                    25,
                    Duration.ofMillis(1_023),
                    videoBitRate = 8_000,
                    sound = Media.Sound(2, 1_000),
                )
            }
        val saved = repository.save(video)
        assertEquals(saved, repository.findByPinId(pin.id))
    }

    @Test
    fun `Given a pin already imaged, Then save replaces the row (unique pin_id)`() {
        val pin = savedPin()
        repository.save(mediaFor(pin.id, hash = "old"))
        val replacement = repository.save(mediaFor(pin.id, hash = "new"))
        assertEquals("new", repository.findByPinId(pin.id)?.contentHash)
        assertEquals(replacement, repository.findByPinId(pin.id))
    }

    @Test
    fun `Given no image for a pin, Then findByPinId returns null`() {
        assertNull(repository.findByPinId(randomUUID()))
    }

    @Test
    fun `Given an image, Then deleteByPinId removes it`() {
        val pin = savedPin()
        repository.save(mediaFor(pin.id))
        repository.deleteByPinId(pin.id)
        assertNull(repository.findByPinId(pin.id))
    }

    @Test
    fun `Given no image, Then deleteByPinId is a no-op`() {
        repository.deleteByPinId(randomUUID()) // must not throw
        assertNull(repository.findByPinId(randomUUID()))
    }

    // --- findMissingMediaIds (orphan sweep) ---

    @Test
    fun `Given a mix of present and absent candidate ids, Then findMissingMediaIds returns only the absent`() {
        // Given
        val pin = savedPin()
        val saved = repository.save(mediaFor(pin.id))
        val missingId = randomUUID()

        // When
        val missing = repository.findMissingMediaIds(listOf(saved.id, missingId))

        // Then
        assertEquals(setOf(missingId), missing)
    }

    @Test
    fun `Given an empty candidate set, Then findMissingMediaIds returns empty`() {
        // Given
        val pin = savedPin()
        repository.save(mediaFor(pin.id))

        // When
        val missing = repository.findMissingMediaIds(emptyList())

        // Then
        assertTrue(missing.isEmpty())
    }

    // --- findByPinIds (a page of pins in one query) ---

    @Test
    fun `Given pins with and without an image, Then findByPinIds keys the images it finds by pin id`() {
        // Given
        val imaged = savedPin()
        val bare = savedPin()
        val media = repository.save(mediaFor(imaged.id))

        // When
        val found = repository.findByPinIds(listOf(imaged.id, bare.id))

        // Then
        assertEquals(mapOf(imaged.id to media), found)
    }

    // --- fingerprints (ADR 0051) ---

    @Test
    fun `Given media unhashed, hashed at an older and at the current version, Then the newest outdated is first`() {
        // Given: the newest is current, so it is skipped for the next newest
        val unhashed = repository.save(mediaFor(savedPin().id).copy(createdAt = Instant.parse("2026-01-01T00:00:00Z")))
        val older = repository.save(mediaFor(savedPin().id).copy(createdAt = Instant.parse("2026-01-02T00:00:00Z")))
        val current = repository.save(mediaFor(savedPin().id).copy(createdAt = Instant.parse("2026-01-03T00:00:00Z")))
        repository.markFingerprinted(older.id, VERSION - 1)
        repository.markFingerprinted(current.id, VERSION)

        // When
        val first = repository.findNewestNotFingerprinted(VERSION)
        repository.markFingerprinted(older.id, VERSION)
        val second = repository.findNewestNotFingerprinted(VERSION)
        repository.markFingerprinted(unhashed.id, VERSION)
        val none = repository.findNewestNotFingerprinted(VERSION)

        // Then
        assertEquals(listOf(older, unhashed, null), listOf(first, second, none))
    }

    @Test
    fun `Given candidates of every kind, Then only the author's other pins at its motion level and version compare`() {
        // Given
        val author = savedUser()
        val media = repository.save(mediaFor(savedPin(author).id))
        val comparable = repository.save(mediaFor(savedPin(author).id))
        val recycledPin = savedPin(author)
        val recycled = repository.save(mediaFor(recycledPin.id))
        pinRepository.softDeletePin(recycledPin, storableNow())
        val otherAuthor = repository.save(mediaFor(savedPin().id))
        val animated = repository.save(animatedFor(savedPin(author).id))
        val outdated = repository.save(mediaFor(savedPin(author).id))
        val candidates = listOf(media, comparable, recycled, otherAuthor, animated, outdated)
        candidates.filter { it != outdated }.forEach { repository.markFingerprinted(it.id, VERSION) }

        // When
        val found = repository.findComparable(media, candidates.map { it.id }, VERSION)

        // Then
        assertEquals(setOf(comparable, recycled), found.toSet())
    }

    @Test
    fun `Given no pin ids, Then findByPinIds returns an empty map`() {
        // Given
        val pin = savedPin()
        repository.save(mediaFor(pin.id))

        // When
        val found = repository.findByPinIds(emptyList())

        // Then
        assertTrue(found.isEmpty())
    }

    private companion object {
        const val VERSION = 2
    }
}
