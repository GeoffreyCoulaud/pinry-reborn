package fr.geoffreyCoulaud.pinryReborn.api.fetch.http

import com.sun.net.httpserver.HttpServer
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchAccessDeniedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchFailedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchNotFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchUnreachableException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.TooManyRedirectsException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UrlNotAllowedException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HttpMediaFetcherTest {
    private lateinit var server: HttpServer
    private val proxies = CopyOnWriteArrayList<GuardingProxy>()
    private val resolvedHosts = CopyOnWriteArrayList<String>()

    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    private val otherLoopback: InetAddress = InetAddress.getByName("127.0.0.2")

    // The `.test` names stand for remote hosts only the proxy's resolver knows; any other name does not resolve.
    private val stubHosts =
        mapOf(
            "127.0.0.1" to loopback,
            "origin.test" to loopback,
            "cdn.test" to loopback,
            "private.test" to otherLoopback,
        )

    // Stands for a public address (127.0.0.1, allowed) and a private one (127.0.0.2, refused).
    private val refusingOtherLoopback =
        object : AddressPolicy {
            override fun isAllowed(address: InetAddress): Boolean = address != otherLoopback
        }

    private val fetcher = fetcherThrough(AddressPolicy.AllowAll)

    private fun fetcherThrough(policy: AddressPolicy, bodyTimeout: Duration = Duration.ofSeconds(30)) =
        HttpMediaFetcher(
            connectTimeout = Duration.ofSeconds(2),
            requestTimeout = Duration.ofSeconds(2),
            maxRedirects = 3,
            bodyTimeout = bodyTimeout,
            openProxy = { GuardingProxy(policy, Duration.ofSeconds(2), ::stubResolve).also { proxies += it } },
        )

    private fun stubResolve(host: String): InetAddress {
        resolvedHosts += host
        return stubHosts[host] ?: throw UnknownHostException(host)
    }

    private fun theProxy(): GuardingProxy = proxies.single()

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
    }

    // Holds a stalling handler until the test ends, so the server can stop.
    private val stalled = CountDownLatch(1)

    @AfterEach
    fun stop() {
        stalled.countDown()
        server.stop(0)
    }

    private fun base() = "http://127.0.0.1:${server.address.port}"

    // No TLS listens there: a refused CONNECT never reaches a handshake.
    private fun httpsBase() = "https://127.0.0.1:${server.address.port}"

    private fun handle(
        path: String,
        status: Int,
        body: ByteArray = ByteArray(0),
        headers: Map<String, String> = emptyMap(),
    ) {
        server.createContext(path) { exchange ->
            headers.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
    }

    /** A local port that is not being listened on, giving a deterministic connection refusal. */
    private fun closedPort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `Given a proxy whose policy allows every address, Then a loopback origin's body is fetched`() {
        // Given
        val bytes = byteArrayOf(1, 2, 3)
        handle("/i.png", 200, bytes)

        // When / Then
        fetcher.openStream("${base()}/i.png").use { assertArrayEquals(bytes, it.stream.readAllBytes()) }
    }

    @Test
    fun `Given an origin that redirects to a second host, Then the proxy resolves both hosts the fetch reached`() {
        // Given
        val bytes = byteArrayOf(4, 5)
        handle("/final.png", 200, bytes)
        handle("/hop", 302, headers = mapOf("Location" to "http://cdn.test:${server.address.port}/final.png"))

        // When
        fetcher.openStream("http://origin.test:${server.address.port}/hop").use {
            assertArrayEquals(bytes, it.stream.readAllBytes())
        }

        // Then
        assertEquals(listOf("origin.test", "cdn.test"), resolvedHosts)
    }

    // One byte every 200 ms for ten seconds: each read would renew a lease that then never expires.
    private fun trickle(path: String) {
        server.createContext(path) { exchange ->
            exchange.sendResponseHeaders(200, 0)
            runCatching {
                repeat(TRICKLE_BYTES) {
                    exchange.responseBody.write(1)
                    exchange.responseBody.flush()
                    Thread.sleep(TRICKLE_INTERVAL_MILLIS)
                }
            }
            exchange.close()
        }
    }

    @Test
    fun `Given a body trickling past the body timeout, Then reading it in blocks throws FetchUnreachable`() {
        // Given
        trickle("/trickle")
        val slowFetcher = fetcherThrough(AddressPolicy.AllowAll, bodyTimeout = Duration.ofSeconds(1))

        // When / Then
        slowFetcher.openStream("${base()}/trickle").use {
            assertThrows(FetchUnreachableException::class.java) { it.stream.readAllBytes() }
        }
    }

    @Test
    fun `Given a body trickling past the body timeout, Then reading it byte by byte throws FetchUnreachable`() {
        // Given
        trickle("/trickle")
        val slowFetcher = fetcherThrough(AddressPolicy.AllowAll, bodyTimeout = Duration.ofSeconds(1))

        // When / Then
        slowFetcher.openStream("${base()}/trickle").use {
            assertThrows(FetchUnreachableException::class.java) { while (it.stream.read() != -1) continue }
        }
    }

    @Test
    fun `Given a chunked body stalling after a byte, Then a read throws FetchUnreachable at the body timeout`() {
        // Given headers and one byte, then nothing: the proxy forwards a head with the body's first bytes
        server.createContext("/stall") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write(FIRST_BYTE)
            exchange.responseBody.flush()
            stalled.await()
            exchange.close()
        }
        val slowFetcher = fetcherThrough(AddressPolicy.AllowAll, bodyTimeout = Duration.ofSeconds(1))

        // When / Then
        assertTimeoutPreemptively(Duration.ofSeconds(STALL_TEST_DEADLINE_SECONDS)) {
            slowFetcher.openStream("${base()}/stall").use {
                assertEquals(FIRST_BYTE, it.stream.read())
                assertThrows(FetchUnreachableException::class.java) { it.stream.read() }
            }
        }
    }

    @Test
    fun `Given a close-delimited body stalling after a byte, Then a read throws FetchUnreachable at its timeout`() {
        // Given headers and one byte of a body whose end would be the close the timeout itself causes
        val stalling = ServerSocket(0, 0, loopback)
        Thread.ofVirtual().start {
            runCatching {
                stalling.accept().use { socket ->
                    // Jetty's client drops a response that arrives before it has sent the request.
                    val request = socket.getInputStream().bufferedReader()
                    while (!request.readLine().isNullOrEmpty()) continue
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".toByteArray())
                    socket.getOutputStream().write(FIRST_BYTE)
                    stalled.await()
                }
            }
        }
        val slowFetcher = fetcherThrough(AddressPolicy.AllowAll, bodyTimeout = Duration.ofSeconds(1))

        // When / Then
        stalling.use {
            assertTimeoutPreemptively(Duration.ofSeconds(STALL_TEST_DEADLINE_SECONDS)) {
                slowFetcher.openStream("http://127.0.0.1:${it.localPort}/stall").use { fetched ->
                    assertEquals(FIRST_BYTE, fetched.stream.read())
                    assertThrows(FetchUnreachableException::class.java) { fetched.stream.read() }
                }
            }
        }
    }

    @Test
    fun `Given an origin that sends its headers and then nothing, Then openStream throws FetchUnreachable`() {
        // Given headers, then no byte of the body: the proxy holds the head until a body byte arrives
        server.createContext("/mute-body") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.flush()
            stalled.await()
            exchange.close()
        }

        // When / Then
        assertTimeoutPreemptively(Duration.ofSeconds(STALL_TEST_DEADLINE_SECONDS)) {
            assertThrows(FetchUnreachableException::class.java) { fetcher.openStream("${base()}/mute-body") }
        }
    }

    @Test
    fun `Given a 200 response with a Content-Type, Then openStream returns it with the body`() {
        // Given
        handle("/v", 200, byteArrayOf(1), headers = mapOf("Content-Type" to "video/mp4"))

        // When
        val contentType = fetcher.openStream("${base()}/v").use { it.contentType }

        // Then
        assertEquals("video/mp4", contentType)
    }

    @Test
    fun `Given a 401 response, Then it throws FetchAccessDenied`() {
        // Given
        handle("/i.png", 401)

        // When / Then
        assertThrows(FetchAccessDeniedException::class.java) { fetcher.openStream("${base()}/i.png") }
    }

    @Test
    fun `Given a 403 response, Then it throws FetchAccessDenied`() {
        // Given
        handle("/i.png", 403)

        // When / Then
        assertThrows(FetchAccessDeniedException::class.java) { fetcher.openStream("${base()}/i.png") }
    }

    @Test
    fun `Given a 404 response, Then it throws FetchNotFound`() {
        // Given
        handle("/i.png", 404)

        // When / Then
        assertThrows(FetchNotFoundException::class.java) { fetcher.openStream("${base()}/i.png") }
    }

    @Test
    fun `Given a 410 response, Then it throws FetchNotFound`() {
        // Given
        handle("/i.png", 410)

        // When / Then
        assertThrows(FetchNotFoundException::class.java) { fetcher.openStream("${base()}/i.png") }
    }

    @Test
    fun `Given a 429 response, Then it throws FetchUnreachable`() {
        // Given
        handle("/i.png", 429)

        // When / Then
        assertThrows(FetchUnreachableException::class.java) { fetcher.openStream("${base()}/i.png") }
    }

    @Test
    fun `Given a 500 response, Then it throws FetchUnreachable`() {
        // Given
        handle("/i.png", 500)

        // When / Then
        assertThrows(FetchUnreachableException::class.java) { fetcher.openStream("${base()}/i.png") }
    }

    @Test
    fun `Given an unmapped 418 response, Then it throws FetchFailed`() {
        // Given
        handle("/i.png", 418)

        // When / Then
        assertThrows(FetchFailedException::class.java) { fetcher.openStream("${base()}/i.png") }
    }

    @Test
    fun `Given a single redirect to a 200, Then openStream returns the final body`() {
        // Given
        val bytes = byteArrayOf(9, 8, 7)
        handle("/final.png", 200, bytes)
        handle("/redirect", 302, headers = mapOf("Location" to "/final.png"))

        // When / Then
        fetcher.openStream("${base()}/redirect").use { assertArrayEquals(bytes, it.stream.readAllBytes()) }
    }

    @Test
    fun `Given a redirect chain over the cap, Then it throws TooManyRedirects`() {
        // Given
        handle("/loop", 302, headers = mapOf("Location" to "/loop"))

        // When / Then
        assertThrows(TooManyRedirectsException::class.java) { fetcher.openStream("${base()}/loop") }
    }

    @Test
    fun `Given a redirect without a Location header, Then it throws FetchFailed`() {
        // Given
        handle("/redirect", 302)

        // When / Then
        assertThrows(FetchFailedException::class.java) { fetcher.openStream("${base()}/redirect") }
    }

    @Test
    fun `Given a file scheme, Then it throws UrlNotAllowed`() {
        // When / Then
        assertThrows(UrlNotAllowedException::class.java) { fetcher.openStream("file:///etc/passwd") }
    }

    @Test
    fun `Given a schemeless url, Then it throws UrlNotAllowed`() {
        // When / Then
        assertThrows(UrlNotAllowedException::class.java) { fetcher.openStream("//example.com/i.png") }
    }

    @Test
    fun `Given a malformed url, Then it throws UrlNotAllowed`() {
        // When / Then
        assertThrows(UrlNotAllowedException::class.java) { fetcher.openStream("http://exa mple/i.png") }
    }

    @Test
    fun `Given a url without a host, Then it throws UrlNotAllowed`() {
        // When / Then
        assertThrows(UrlNotAllowedException::class.java) { fetcher.openStream("http:///i.png") }
    }

    @Test
    fun `Given an http host no resolver knows, Then it throws FetchUnreachable and the proxy records the host`() {
        // When / Then
        assertThrows(FetchUnreachableException::class.java) { fetcher.openStream("http://nowhere.test/i.png") }
        assertEquals(listOf("nowhere.test"), theProxy().unreachableHosts)
    }

    @Test
    fun `Given an https host no resolver knows, Then it throws FetchUnreachable and the proxy records the host`() {
        // When / Then
        assertThrows(FetchUnreachableException::class.java) { fetcher.openStream("https://nowhere.test/i.png") }
        assertEquals(listOf("nowhere.test"), theProxy().unreachableHosts)
    }

    @Test
    fun `Given an http origin that cannot be reached, Then it throws FetchUnreachable`() {
        // Given
        val port = closedPort()

        // When / Then
        assertThrows(FetchUnreachableException::class.java) {
            fetcher.openStream("http://127.0.0.1:$port/i.png")
        }
    }

    @Test
    fun `Given an origin that closes without answering, Then it throws FetchUnreachable`() {
        // Given
        val silent = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        Thread.ofVirtual().start { runCatching { while (true) silent.accept().close() } }

        // When / Then
        silent.use {
            assertThrows(FetchUnreachableException::class.java) {
                fetcher.openStream("http://127.0.0.1:${it.localPort}/i.png")
            }
        }
    }

    @Test
    fun `Given an https origin that cannot be reached, Then it throws FetchUnreachable`() {
        // Given
        val port = closedPort()

        // When / Then
        assertThrows(FetchUnreachableException::class.java) {
            fetcher.openStream("https://127.0.0.1:$port/i.png")
        }
    }

    @Test
    fun `Given a proxy refusing loopback, Then an http loopback origin throws UrlNotAllowed, recorded by the proxy`() {
        // Given
        handle("/i.png", 200, byteArrayOf(1))

        // When / Then
        assertThrows(UrlNotAllowedException::class.java) {
            fetcherThrough(AddressPolicy.Standard).openStream("${base()}/i.png")
        }
        assertEquals(listOf(loopback), theProxy().refusedAddresses)
    }

    @Test
    fun `Given a proxy refusing loopback, Then an https loopback origin throws UrlNotAllowed, recorded by the proxy`() {
        // When / Then
        assertThrows(UrlNotAllowedException::class.java) {
            fetcherThrough(AddressPolicy.Standard).openStream("${httpsBase()}/i.png")
        }
        assertEquals(listOf(loopback), theProxy().refusedAddresses)
    }

    @Test
    fun `Given an http redirect to a private address, Then the proxy refuses and records it as UrlNotAllowed`() {
        // Given
        handle("/redirect", 302, headers = mapOf("Location" to "http://private.test:${server.address.port}/x"))

        // When / Then
        assertThrows(UrlNotAllowedException::class.java) {
            fetcherThrough(refusingOtherLoopback).openStream("${base()}/redirect")
        }
        assertEquals(listOf(otherLoopback), theProxy().refusedAddresses)
    }

    @Test
    fun `Given an https redirect to a private address, Then the proxy refuses and records it as UrlNotAllowed`() {
        // Given
        handle("/redirect", 302, headers = mapOf("Location" to "https://private.test:${server.address.port}/x"))

        // When / Then
        assertThrows(UrlNotAllowedException::class.java) {
            fetcherThrough(refusingOtherLoopback).openStream("${base()}/redirect")
        }
        assertEquals(listOf(otherLoopback), theProxy().refusedAddresses)
    }

    private companion object {
        const val TRICKLE_BYTES = 50
        const val TRICKLE_INTERVAL_MILLIS = 200L
        const val STALL_TEST_DEADLINE_SECONDS = 5L
        const val FIRST_BYTE = 'x'.code
    }
}
