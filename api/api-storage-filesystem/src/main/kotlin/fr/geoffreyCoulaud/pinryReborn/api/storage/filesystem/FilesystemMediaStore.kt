package fr.geoffreyCoulaud.pinryReborn.api.storage.filesystem

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaTooLargeException
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StorageLayout
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import kotlin.streams.asSequence

/**
 * [MediaStore] adapter backed by the local filesystem.
 *
 * Bytes are staged under `<dataDir>/tmp/`, measured (size + SHA-256) in a single streaming
 * pass, then promoted (moved) to their final `<dataDir>/<storageKey>` location.
 *
 * [dataDir] is a plain string (not injected) so this class stays framework-light and
 * unit-testable with a temp directory; CDI wiring of the actual data directory is done by
 * a producer elsewhere.
 *
 * Deliberately NOT `@ApplicationScoped`: ARC cannot satisfy a plain `String` constructor
 * parameter on its own, so this class is instantiated exclusively by
 * `MediaAdapterProducers` (`api-presentation-quarkus`), which resolves `dataDir` from
 * `MediaConfig`. Adding `@ApplicationScoped` back here alongside that `@Produces` method
 * would create an ambiguous `MediaStore` bean resolution.
 */
// One override per MediaStore method plus its helpers: splitting would fragment one cohesive adapter.
@Suppress("TooManyFunctions")
class FilesystemMediaStore(private val dataDir: String) : MediaStore {

    private companion object {
        private const val STREAM_BUFFER_SIZE = 8192
        private val HEX = HexFormat.of()
    }

    private val root: Path get() = Path.of(dataDir)
    private val tmpDir: Path get() = root.resolve(StorageLayout.STAGING_DIRECTORY)
    private val paths = DataDirPaths(dataDir)

    // Cleanup-on-failure genuinely has to catch everything: the MediaTooLargeException guard,
    // an IOException from a broken source stream, a write/fsync failure under disk pressure, or
    // any other Throwable mid-stage must all leave no partial temp behind ("no temp file on
    // error" is this store's guarantee). A `finally` + success-flag was rejected: the Kotlin
    // compiler duplicates the finally block, so the normal-completion copy's `if (!success)`
    // only ever sees success == true and stays partly covered, breaking the 100%-branch gate.
    // The catch-and-rethrow below dispatches via the JVM exception table (not a conditional
    // jump), so it adds no uncovered branch. Hence the deliberate broad catch.
    @Suppress("TooGenericExceptionCaught")
    override fun stage(source: InputStream, maxBytes: Long): StagedFile {
        Files.createDirectories(tmpDir)
        val tempPath = Files.createTempFile(tmpDir, "stage-", ".tmp")
        try {
            val (byteSize, digestBytes) = writeAndDigest(source, tempPath, maxBytes)
            return StagedFile(tempPath.toString(), byteSize, HEX.formatHex(digestBytes))
        } catch (error: Throwable) {
            Files.deleteIfExists(tempPath)
            throw error
        }
    }

    // Nothing is resolved, created or opened under the data directory: the store's tmp/ is never
    // touched, which is what separates this from a stage followed by a discard.
    override fun digest(source: InputStream, maxBytes: Long): String =
        OutputStream.nullOutputStream().use { HEX.formatHex(readAndDigest(source, it, maxBytes).second) }

    override fun promote(staged: StagedFile, storageKey: String) {
        val dest = paths.resolveWithinRoot(storageKey)
        Files.createDirectories(dest.parent)
        paths.atomicMove(Path.of(staged.path), dest)
    }

    override fun openStream(storageKey: String): InputStream =
        Files.newInputStream(paths.resolveWithinRoot(storageKey))

    // ponytail: a hard link, so tmp/ and originals/ share one filesystem; a copy fallback if a volume ever splits them.
    override fun stageStored(media: Media): StagedFile {
        Files.createDirectories(tmpDir)
        val link = tmpDir.resolve("stored-${UUID.randomUUID()}.tmp")
        Files.createLink(link, paths.resolveWithinRoot(media.storageKey))
        return StagedFile(link.toString(), media.byteSize, media.contentHash)
    }

    override fun openStaged(staged: StagedFile): InputStream = Files.newInputStream(Path.of(staged.path))

    override fun delete(storageKey: String) {
        Files.deleteIfExists(paths.resolveWithinRoot(storageKey))
    }

    override fun discard(staged: StagedFile) {
        Files.deleteIfExists(Path.of(staged.path))
    }

    override fun discardOrphanedStagedFiles(olderThan: Instant): Int {
        if (!Files.isDirectory(tmpDir)) return 0
        val stale = Files.list(tmpDir).use { stream ->
            stream.filter { it.modifiedBefore(olderThan) && it.newestEntryBefore(olderThan) }.toList()
        }
        return stale.count { it.deleteTree() }
    }

    // A directory under tmp/ is a yt-dlp run's, which a killed API leaves behind: stale once its newest entry is.
    private fun Path.newestEntryBefore(instant: Instant): Boolean =
        Files.walk(this).use { tree -> tree.allMatch { it.modifiedBefore(instant) } }

    // Deepest first, so each directory is empty by its turn; a file walks to itself alone.
    private fun Path.deleteTree(): Boolean {
        val deepestFirst = Files.walk(this).use { tree -> tree.sorted(Comparator.reverseOrder()).toList() }
        return deepestFirst.map { Files.deleteIfExists(it) }.last()
    }

    override fun forEachStorageKeyOnDisk(olderThan: Instant, block: (Sequence<String>) -> Unit) {
        val originals = root.resolve(StorageLayout.ORIGINALS_DIRECTORY)
        // A fresh install has no originals/ yet: the block still runs once, on nothing.
        if (!Files.isDirectory(originals)) {
            block(emptySequence())
            return
        }
        Files.walk(originals).use { stream ->
            block(
                stream.asSequence()
                    .filter { Files.isRegularFile(it) && it.modifiedBefore(olderThan) }
                    .map { root.relativize(it).joinToString("/") },
            )
        }
    }

    // A file promoted or deleted between the listing and this read is no longer this sweep's.
    private fun Path.modifiedBefore(instant: Instant): Boolean =
        try {
            Files.getLastModifiedTime(this).toInstant().isBefore(instant)
        } catch (_: NoSuchFileException) {
            false
        }

    /**
     * Streams [source] into [tempPath] while updating a SHA-256 digest and counting bytes,
     * aborting with [MediaTooLargeException] as soon as the running count exceeds [maxBytes].
     * Fsyncs the temp file before returning so a promote never observes a partially-flushed
     * file.
     */
    private fun writeAndDigest(source: InputStream, tempPath: Path, maxBytes: Long): Pair<Long, ByteArray> =
        FileOutputStream(tempPath.toFile()).use { out ->
            val measured = readAndDigest(source, out, maxBytes)
            out.flush()
            out.channel.force(true)
            measured
        }

    /**
     * Reads [source] into [sink], hashing and counting, aborting with [MediaTooLargeException] as soon
     * as the count passes [maxBytes]: tested per block, so an oversize stream is never read whole.
     */
    private fun readAndDigest(source: InputStream, sink: OutputStream, maxBytes: Long): Pair<Long, ByteArray> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        var byteSize = 0L
        var read = source.read(buffer)
        while (read >= 0) {
            byteSize += read
            if (byteSize > maxBytes) {
                throw MediaTooLargeException("Stream exceeded the $maxBytes byte limit")
            }
            digest.update(buffer, 0, read)
            sink.write(buffer, 0, read)
            read = source.read(buffer)
        }
        return byteSize to digest.digest()
    }
}
