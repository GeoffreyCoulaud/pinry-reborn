package fr.geoffreyCoulaud.pinryReborn.api.usecases.exports

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import fr.geoffreyCoulaud.pinryReborn.api.domain.exports.ArchiveEntryDigest
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the exact JSON shape of every `Exported*` type against a Jackson upgrade or an accidental property rename (spec
 * §4: "the field names are a published contract").
 *
 * `api-usecases` carries no Jackson dependency on its main classpath by design (Jackson is adapter-only); this mapper
 * is built **test-only**, configured identically to the real one in `FilesystemZipExportArchiveStore` (`JavaTimeModule`
 * registered, `WRITE_DATES_AS_TIMESTAMPS` disabled, nothing else). That equality is what makes this test meaningful: if
 * the adapter's mapper config ever drifts from this one, this test stops proving anything about the real archive. The
 * end-to-end proof that a real archive on disk matches this shape is the integration tests (later tasks), not this
 * test.
 *
 * `jackson-module-kotlin` reached `api-storage-filesystem` with the import reader, so the sentence this KDoc used to
 * carry (none anywhere in the codebase) is no longer true. It is registered on the reader's mapper only; the writer's
 * registered module ids are asserted in `FilesystemZipExportArchiveStoreTest`, which is where a stray registration
 * would be caught. Nothing registers it here either, so every type below is still serialized through plain JavaBean
 * getter introspection, not constructor/property metadata. The one place that matters is `Boolean`: a property named
 * `isX` compiles to a getter `isX()`, which Jackson reads as a property named `x` (the `is` prefix is stripped).
 * [ExportedMedia.animated] is named `animated`, not `isAnimated`, specifically so its getter is `getAnimated()` and its
 * published field name stays `animated`, matching spec §4 exactly.
 */
class ExportContentGoldenJsonTest {
    private val mapper: ObjectMapper =
        ObjectMapper().registerModule(JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `Given a fully populated ExportGenerator, Then it serializes to the published JSON shape`() {
        // Given
        val generator = ExportGenerator(name = "pinry-reborn", version = "1.2.3")

        // When
        val json = mapper.writeValueAsString(generator)

        // Then
        assertEquals("""{"name":"pinry-reborn","version":"1.2.3"}""", json)
    }

    @Test
    fun `Given a fully populated ExportedRef, Then it serializes to the published JSON shape`() {
        // Given
        val ref = ExportedRef(name = "alice")

        // When
        val json = mapper.writeValueAsString(ref)

        // Then
        assertEquals("""{"name":"alice"}""", json)
    }

    @Test
    fun `Given a fully populated ExportExclusion, Then it serializes to the published JSON shape`() {
        // Given
        val exclusion = ExportExclusion(what = "password hashes", why = "secrets; useless to you")

        // When
        val json = mapper.writeValueAsString(exclusion)

        // Then
        assertEquals("""{"what":"password hashes","why":"secrets; useless to you"}""", json)
    }

    @Test
    fun `Given a fully populated ExportedUser, Then it serializes to the published JSON shape`() {
        // Given
        val user = ExportedUser(name = "alice", createdAt = Instant.parse("2026-01-01T00:00:00Z"))

        // When
        val json = mapper.writeValueAsString(user)

        // Then
        assertEquals("""{"name":"alice","createdAt":"2026-01-01T00:00:00Z"}""", json)
    }

    @Test
    fun `Given a fully populated ExportedTag, Then it serializes to the published JSON shape`() {
        // Given
        val tag = ExportedTag(name = "travel", createdAt = Instant.parse("2026-01-02T00:00:00Z"))

        // When
        val json = mapper.writeValueAsString(tag)

        // Then
        assertEquals("""{"name":"travel","createdAt":"2026-01-02T00:00:00Z"}""", json)
    }

    @Test
    fun `Given a fully populated ExportedBoard, Then it serializes to the published JSON shape`() {
        // Given
        val board =
            ExportedBoard(
                name = "Summer",
                description = "Summer trip",
                createdAt = Instant.parse("2026-01-03T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-04T00:00:00Z"),
                softDeletedAt = null,
            )

        // When
        val json = mapper.writeValueAsString(board)

        // Then
        assertEquals(
            """{"name":"Summer","description":"Summer trip",""" +
                """"createdAt":"2026-01-03T00:00:00Z","updatedAt":"2026-01-04T00:00:00Z","softDeletedAt":null}""",
            json,
        )
    }

    @Test
    fun `Given a fully populated ExportedCollection, Then it serializes to the published JSON shape`() {
        // Given
        val collection =
            ExportedCollection(url = "https://remote.example/c/1", name = "Sketches", board = ExportedRef("Summer"))

        // When
        val json = mapper.writeValueAsString(collection)

        // Then
        assertEquals("""{"url":"https://remote.example/c/1","name":"Sketches","board":{"name":"Summer"}}""", json)
    }

    @Test
    fun `Given a fully populated ExportedPersonLine, Then it serializes to the published JSON shape`() {
        // Given
        val person =
            ExportedPersonLine(
                id = UUID.fromString("88888888-8888-8888-8888-888888888888"),
                name = "Ada",
                urls = listOf("https://a.example/", "https://b.example/"),
                createdAt = Instant.parse("2026-01-02T00:00:00Z"),
            )

        // When
        val json = mapper.writeValueAsString(person)

        // Then
        assertEquals(
            """{"id":"88888888-8888-8888-8888-888888888888","name":"Ada",""" +
                """"urls":["https://a.example/","https://b.example/"],"createdAt":"2026-01-02T00:00:00Z"}""",
            json,
        )
    }

    @Test
    fun `Given a fully populated ExportedMedia, Then it serializes to the published JSON shape`() {
        // Given
        val media =
            ExportedMedia(
                path = "media/55555555-5555-5555-5555-555555555555.jpg",
                mimeType = "image/jpeg",
                width = 1920,
                height = 1080,
                animated = false,
                byteSize = 482913,
                sha256 = "deadbeef",
                createdAt = Instant.parse("2026-01-05T00:00:00Z"),
            )

        // When
        val json = mapper.writeValueAsString(media)

        // Then
        assertEquals(
            """{"path":"media/55555555-5555-5555-5555-555555555555.jpg","mimeType":"image/jpeg",""" +
                """"width":1920,"height":1080,"animated":false,"byteSize":482913,"sha256":"deadbeef",""" +
                """"createdAt":"2026-01-05T00:00:00Z"}""",
            json,
        )
    }

    @Test
    fun `Given a fully populated ExportedPin, Then it serializes to the published JSON shape`() {
        // Given
        val pin =
            ExportedPin(
                description = "A pin",
                sourceContextUrl = "https://example.org/article",
                sourceMediaUrl = "https://example.org/media.jpg",
                createdAt = Instant.parse("2026-01-06T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-07T00:00:00Z"),
                softDeletedAt = null,
                tags = listOf(ExportedRef(name = "travel")),
                boards = listOf(ExportedRef(name = "Summer")),
                collections = listOf(ExportedCollectionRef(url = "https://remote.example/c/1")),
                media =
                    ExportedMedia(
                        path = "media/55555555-5555-5555-5555-555555555555.jpg",
                        mimeType = "image/jpeg",
                        width = 1920,
                        height = 1080,
                        animated = false,
                        byteSize = 482913,
                        sha256 = "deadbeef",
                        createdAt = Instant.parse("2026-01-05T00:00:00Z"),
                    ),
                publisher = ExportedPersonRef(id = UUID.fromString("77777777-7777-7777-7777-777777777777")),
                creators = listOf(ExportedPersonRef(id = UUID.fromString("88888888-8888-8888-8888-888888888888"))),
                publishedAt = Instant.parse("1999-12-31T23:00:00Z"),
            )

        // When
        val json = mapper.writeValueAsString(pin)

        // Then
        assertEquals(
            """{"description":"A pin",""" +
                """"sourceContextUrl":"https://example.org/article",""" +
                """"sourceMediaUrl":"https://example.org/media.jpg",""" +
                """"createdAt":"2026-01-06T00:00:00Z","updatedAt":"2026-01-07T00:00:00Z","softDeletedAt":null,""" +
                """"tags":[{"name":"travel"}],"boards":[{"name":"Summer"}],""" +
                """"collections":[{"url":"https://remote.example/c/1"}],""" +
                """"media":{"path":"media/55555555-5555-5555-5555-555555555555.jpg","mimeType":"image/jpeg",""" +
                """"width":1920,"height":1080,"animated":false,"byteSize":482913,"sha256":"deadbeef",""" +
                """"createdAt":"2026-01-05T00:00:00Z"},""" +
                """"publisher":{"id":"77777777-7777-7777-7777-777777777777"},""" +
                """"creators":[{"id":"88888888-8888-8888-8888-888888888888"}],""" +
                """"publishedAt":"1999-12-31T23:00:00Z"}""",
            json,
        )
    }

    @Test
    fun `Given a pin with no image and no source page, Then each absent field serializes as null`() {
        // Given
        val pin =
            ExportedPin(
                description = "A pin",
                sourceContextUrl = null,
                sourceMediaUrl = null,
                createdAt = Instant.parse("2026-01-06T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-07T00:00:00Z"),
                softDeletedAt = Instant.parse("2026-01-08T00:00:00Z"),
                tags = emptyList(),
                boards = emptyList(),
                collections = emptyList(),
                media = null,
                publisher = null,
                creators = emptyList(),
                publishedAt = null,
            )

        // When
        val json = mapper.writeValueAsString(pin)

        // Then
        assertEquals(
            """{"description":"A pin",""" +
                """"sourceContextUrl":null,"sourceMediaUrl":null,""" +
                """"createdAt":"2026-01-06T00:00:00Z","updatedAt":"2026-01-07T00:00:00Z",""" +
                """"softDeletedAt":"2026-01-08T00:00:00Z","tags":[],"boards":[],"collections":[],""" +
                """"media":null,"publisher":null,"creators":[],"publishedAt":null}""",
            json,
        )
    }

    @Test
    fun `Given a fully populated ExportManifest, Then it serializes to the published JSON shape`() {
        // Given
        val manifest =
            ExportManifest(
                formatVersion = 1,
                generator = ExportGenerator(name = "pinry-reborn", version = "1.2.3"),
                exportId = UUID.fromString("77777777-7777-7777-7777-777777777777"),
                createdAt = Instant.parse("2026-07-22T10:15:30Z"),
                expiresAt = Instant.parse("2026-07-29T10:15:30Z"),
                user = ExportedRef(name = "alice"),
                counts = ExportCounts(pins = 1234, boards = 12, tags = 90, media = 1180, persons = 34, collections = 5),
                entries = listOf(ArchiveEntryDigest(path = "pins.jsonl", byteSize = 918273, sha256 = "cafef00d")),
                excluded =
                    listOf(
                        ExportExclusion(
                            what = "password hashes",
                            why = "secrets; useless to you, dangerous if this archive leaks",
                        )
                    ),
            )

        // When
        val json = mapper.writeValueAsString(manifest)

        // Then
        assertEquals(
            """{"formatVersion":1,"generator":{"name":"pinry-reborn","version":"1.2.3"},""" +
                """"exportId":"77777777-7777-7777-7777-777777777777",""" +
                """"createdAt":"2026-07-22T10:15:30Z","expiresAt":"2026-07-29T10:15:30Z",""" +
                """"user":{"name":"alice"},""" +
                """"counts":{"pins":1234,"boards":12,"tags":90,"media":1180,"persons":34,"collections":5},""" +
                """"entries":[{"path":"pins.jsonl","byteSize":918273,"sha256":"cafef00d"}],""" +
                """"excluded":[{"what":"password hashes",""" +
                """"why":"secrets; useless to you, dangerous if this archive leaks"}]}""",
            json,
        )
    }
}
