package fr.geoffreyCoulaud.pinryReborn.api.imaging.vips

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHash
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHasher
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Meta's images under `pdq/`, with its BSD notice, and their hashes from `pdq/cpp/output-regtest/out`. */
class VipsPdqHashTest {
    private val sampler = VipsImageTransformer(quality = 80, Duration.ofSeconds(60), DECODER_MEMORY)

    private fun hashOf(name: String): PdqHash {
        val media = Media.StillImage(UUID.randomUUID(), UUID.randomUUID(), "image/jpeg", 1, 1, 0, "", "", Instant.EPOCH)
        val hashes = mutableListOf<PdqHash>()
        sampler.sample(media, StagedFile("src/test/resources/pdq/$name", 0, "")) { hashes += PdqHasher.hash(it) }
        return hashes.single()
    }

    @ParameterizedTest
    @CsvSource(
        "bridge-1-original.jpg, d8f8f0cee0f4a84f0637022a078f67f0b36e2ed596621e1d33e6339c4e9c9b22",
        "bridge-2-rotate-90.jpg, 30a10efdf1c83f429013d48d0ffffc52e34e0e35ada952a9d29605215aa9e5af",
        "bridge-3-rotate-180.jpg, 2dad5a64b1a142e7d362a09857da895ae63b8c7fc23794b766b319361fc93188",
        "bridge-4-rotate-270.jpg, a5f0a457248995e8c9065c275aaa5498b61ba4bdf8fcf80387c32f8b5bfc4f05",
        "bridge-5-flipx.jpg, d8f80f33e0f417b20e37f5cd028f980fb36ed02a9662c1e233e64c634e9c64dd",
        "bridge-6-flipy.jpg, 2da9259bb1a1bd1a5362576552da32a5e63b7380c2774b4866b346c91b89ce77",
        "bridge-7-flip-plus-1.jpg, f0a1e10271ccc0bd90530b720fff038de34ef1e8ada9a956d6967ade5ea91a50",
        "bridge-8-flip-minus-1.jpg, 2df05aa8a4896a17c14682da5aaaab07b61b5b42f8fc07fc87c3d0741bfcb0fa",
    )
    fun `Given one of Meta's images, Then its hash through vips is within ten bits of the reference's`(
        name: String,
        hexadecimal: String,
    ) {
        // Given
        val reference = PdqHash(hexadecimal.chunked(16).map { java.lang.Long.parseUnsignedLong(it, 16) }, quality = 100)
        // When
        val hash = hashOf(name)
        // Then
        assertTrue(hash.quality >= MIN_QUALITY) { "quality ${hash.quality}" }
        assertTrue(hash.distanceTo(reference) <= MAX_DISTANCE) { "${hash.distanceTo(reference)} bits apart" }
    }

    @Test
    fun `Given an image and its rotation by half a turn, Then their hashes are no match`() {
        val distance = hashOf("bridge-1-original.jpg").distanceTo(hashOf("bridge-3-rotate-180.jpg"))
        assertTrue(distance > PdqHasher.MATCH_DISTANCE) { "$distance bits apart" }
    }

    private companion object {
        const val DECODER_MEMORY = 2L * 1024 * 1024 * 1024

        // Meta's second acceptance condition (`pdq/README.md`, "Writing Your Own Hashing").
        const val MIN_QUALITY = 80
        const val MAX_DISTANCE = 10
    }
}
