package fr.geoffreyCoulaud.pinryReborn.api.fetch.ytdlp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchUnreachableException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.NoMediaFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UndecodableVideoException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UrlNotAllowedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.VideoCodec
import fr.geoffreyCoulaud.pinryReborn.api.domain.storage.StagedFile
import fr.geoffreyCoulaud.pinryReborn.api.fetch.http.AddressPolicy
import fr.geoffreyCoulaud.pinryReborn.api.fetch.http.GuardingProxy
import fr.geoffreyCoulaud.pinryReborn.api.video.ffmpeg.FfmpegVideoProcessor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** Runs the `yt-dlp` on the `PATH` against a local origin, through a [GuardingProxy]. */
class YtDlpPageMediaExtractorTest {
    @TempDir
    lateinit var staging: Path

    private lateinit var server: HttpServer
    private val routes = ConcurrentHashMap<String, (HttpExchange) -> Unit>()
    private val requestedPaths = CopyOnWriteArrayList<String>()
    private val released = CountDownLatch(1)

    private val fixture = fixture("h264-aac.mkv")
    private val maxDuration = Duration.ofSeconds(120)

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")).use { it.readAllBytes() }

    private fun extractor(
        policy: AddressPolicy = AddressPolicy.AllowAll,
        timeout: Duration = Duration.ofSeconds(60),
        resolve: (String) -> InetAddress = InetAddress::getByName,
    ) = YtDlpPageMediaExtractor(staging, timeout) {
        GuardingProxy(policy, Duration.ofSeconds(2), resolve)
    }

    @BeforeEach fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newVirtualThreadPerTaskExecutor()
        server.createContext("/") { exchange ->
            requestedPaths += exchange.requestURI.path
            (routes[exchange.requestURI.path] ?: { it.sendResponseHeaders(NOT_FOUND, -1) })(exchange)
            exchange.close()
        }
        server.start()
    }

    @AfterEach fun stop() {
        released.countDown()
        server.stop(0)
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun serve(path: String, body: ByteArray, contentType: String) {
        routes[path] = { exchange ->
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(OK, body.size.toLong())
            exchange.responseBody.write(body)
        }
    }

    private fun page(path: String, body: String) =
        serve(path, "<html><body>$body</body></html>".toByteArray(), "text/html")

    private fun playlist(path: String, vararg lines: String) =
        serve(path, (listOf("#EXTM3U") + lines).joinToString("\n").toByteArray(), "application/vnd.apple.mpegurl")

    private fun segmentPlaylist(path: String, segment: String) =
        playlist(path, "#EXT-X-TARGETDURATION:1", "#EXTINF:1.0,", segment, "#EXT-X-ENDLIST")

    private fun stagingIsEmpty() = Files.list(staging).use { it.toList().isEmpty() }

    // Ingestion stages the extracted stream before probing it; this copy stands for that staged file.
    private fun extractedCopy(pageUrl: String): Path {
        val copy = staging.resolve("extracted")
        extractor().extract(pageUrl) {}.use { Files.copy(it.stream, copy) }
        return copy
    }

    private fun probe(file: Path) =
        FfmpegVideoProcessor(Duration.ofSeconds(30), WEBP_QUALITY)
            .probe(StagedFile(file.toString(), Files.size(file), "unused"), maxDuration)

    @Test
    fun `Given a page whose video element names a file, Then the extracted stream holds that file's bytes`() {
        // Given
        serve("/clip.mkv", fixture, "video/x-matroska")
        page("/page.html", """<video src="/clip.mkv"></video>""")

        // When
        val bytes = extractor().extract(url("/page.html")) {}.use { it.stream.readAllBytes() }

        // Then
        assertArrayEquals(fixture, bytes)
    }

    @Test
    fun `Given an extracted file, Then closing its stream deletes the directory the run wrote into`() {
        // Given
        serve("/clip.mkv", fixture, "video/x-matroska")
        page("/page.html", """<video src="/clip.mkv"></video>""")
        val extracted = extractor().extract(url("/page.html")) {}
        assertFalse(stagingIsEmpty())

        // When
        extracted.close()

        // Then
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun `Given a page with no video, Then extraction fails as no media found and keeps no file`() {
        // Given
        page("/page.html", "<p>No video here.</p>")

        // When / Then
        assertThrows(NoMediaFoundException::class.java) { extractor().extract(url("/page.html")) {} }
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun `Given a page that never answers, Then the run is destroyed past the timeout as unreachable`() {
        // Given
        routes["/page.html"] = { released.await() }

        // When / Then
        assertThrows(FetchUnreachableException::class.java) {
            extractor(timeout = Duration.ofSeconds(3)).extract(url("/page.html")) {}
        }
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun `Given a heartbeat that throws, Then the extraction ends with its exception and keeps no file`() {
        // Given
        serve("/clip.mkv", fixture, "video/x-matroska")
        page("/page.html", """<video src="/clip.mkv"></video>""")
        val leaseLost = IllegalStateException("lease lost")

        // When
        val thrown = assertThrows(IllegalStateException::class.java) {
            extractor().extract(url("/page.html")) { throw leaseLost }
        }

        // Then
        assertEquals(leaseLost, thrown)
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun `Given an HLS master offering H265 then H264, Then the H264 variant is the one downloaded`() {
        // Given
        serve("/h265.mov", fixture("h265-hev1-aac.mov"), "video/quicktime")
        serve("/h264.ts", fixture("mpegts.ts"), "video/mp2t")
        segmentPlaylist("/h265.m3u8", "h265.mov")
        segmentPlaylist("/h264.m3u8", "h264.ts")
        playlist(
            "/master.m3u8",
            """#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=160x120,CODECS="hvc1.1.6.L60.90,mp4a.40.2"""",
            "h265.m3u8",
            """#EXT-X-STREAM-INF:BANDWIDTH=300000,RESOLUTION=160x120,CODECS="avc1.64000c,mp4a.40.2"""",
            "h264.m3u8",
        )
        page("/page.html", """<video src="/master.m3u8"></video>""")

        // When
        extractor().extract(url("/page.html")) {}.close()

        // Then
        assertTrue("/h264.ts" in requestedPaths)
        assertFalse("/h265.mov" in requestedPaths)
    }

    @Test
    fun `Given an HLS page, Then the file it yields is a video the probe accepts`() {
        // Given
        serve("/segment.ts", fixture("mpegts.ts"), "video/mp2t")
        segmentPlaylist("/stream.m3u8", "segment.ts")
        page("/page.html", """<video src="/stream.m3u8"></video>""")

        // When
        val copy = extractedCopy(url("/page.html"))

        // Then
        assertEquals(VideoCodec.H264, probe(copy).videoCodec)
    }

    @Test
    fun `Given an HLS segment holding a concatenation named mp4, Then the file it yields is refused by the probe`() {
        // Given
        serve("/segment.mp4", "ffconcat version 1.0\nfile secret.ts\n".toByteArray(), "video/mp4")
        segmentPlaylist("/stream.m3u8", "segment.mp4")
        page("/page.html", """<video src="/stream.m3u8"></video>""")

        // When
        val copy = extractedCopy(url("/page.html"))

        // Then
        assertThrows(UndecodableVideoException::class.java) { probe(copy) }
    }

    @Test
    fun `Given a proxy refusing every address, Then extraction fails as not allowed and the origin logs no request`() {
        // Given
        page("/page.html", "<p>Unreachable behind the proxy.</p>")

        // When / Then
        assertThrows(UrlNotAllowedException::class.java) {
            extractor(policy = AddressPolicy.Standard).extract(url("/page.html")) {}
        }
        assertEquals(emptyList<String>(), requestedPaths)
    }

    @Test
    fun `Given a page whose host does not resolve, Then extraction fails as unreachable`() {
        // Given
        val resolve: (String) -> InetAddress = { throw UnknownHostException(it) }

        // When / Then
        assertThrows(FetchUnreachableException::class.java) {
            extractor(resolve = resolve).extract("http://nowhere.test/page.html") {}
        }
    }

    private companion object {
        const val OK = 200
        const val NOT_FOUND = 404
        const val WEBP_QUALITY = 75
    }
}
