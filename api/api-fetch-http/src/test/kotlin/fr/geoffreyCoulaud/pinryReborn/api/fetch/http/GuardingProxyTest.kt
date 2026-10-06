package fr.geoffreyCoulaud.pinryReborn.api.fetch.http

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GuardingProxyTest {
    private lateinit var origin: HttpServer
    private val originRequests = CopyOnWriteArrayList<String>()
    private val proxies = mutableListOf<GuardingProxy>()
    private val resolutions = AtomicInteger()

    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    private val otherLoopback: InetAddress = InetAddress.getByName("127.0.0.2")

    // Stands for a public address (127.0.0.1, allowed) and a private one (127.0.0.2, refused).
    private val refusingOtherLoopback =
        object : AddressPolicy {
            override fun isAllowed(address: InetAddress): Boolean = address != otherLoopback
        }

    @BeforeEach
    fun start() {
        origin = HttpServer.create(InetSocketAddress(loopback, 0), 0)
        origin.createContext("/") { exchange ->
            val headers = exchange.requestHeaders.entries.joinToString { "${it.key}=${it.value}" }
            originRequests += "${exchange.requestURI} $headers"
            val body = "hello".toByteArray()
            exchange.sendResponseHeaders(OK, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        origin.createContext("/hop") { exchange ->
            originRequests += exchange.requestURI.toString()
            exchange.responseHeaders.add("Location", "http://refused.test:${originPort()}/landed")
            exchange.sendResponseHeaders(FOUND, -1)
            exchange.close()
        }
        origin.start()
    }

    @AfterEach
    fun stop() {
        proxies.forEach { it.close() }
        origin.stop(0)
    }

    private fun originPort(): Int = origin.address.port

    private fun proxy(
        policy: AddressPolicy,
        resolve: (String) -> InetAddress = InetAddress::getByName,
    ): GuardingProxy = GuardingProxy(policy, Duration.ofSeconds(1), resolve).also { proxies += it }

    // A proxy whose resolver answers 127.0.0.1 for any host and counts its calls.
    private fun countingProxy(): GuardingProxy =
        proxy(AddressPolicy.AllowAll) {
            resolutions.incrementAndGet()
            loopback
        }

    private fun connectTo(proxy: GuardingProxy): Socket =
        Socket(proxy.address.address, proxy.address.port).apply { soTimeout = READ_TIMEOUT_MILLIS }

    // Sends raw bytes to the proxy, ends its side, and reads until the proxy closes the connection.
    private fun exchange(
        proxy: GuardingProxy,
        request: String,
    ): String =
        connectTo(proxy).use { socket ->
            socket.getOutputStream().write(request.toByteArray())
            socket.shutdownOutput()
            socket.getInputStream().readAllBytes().toString(Charsets.ISO_8859_1)
        }

    // Reads one response head, up to its blank line or the end, and leaves the rest of the stream unread.
    private fun readHead(input: InputStream): String {
        val head = ByteArrayOutputStream()
        while (!head.toString(Charsets.ISO_8859_1).endsWith("\r\n\r\n")) {
            val byte = input.read()
            if (byte == -1) break
            head.write(byte)
        }
        return head.toString(Charsets.ISO_8859_1)
    }

    // Reads one response with a Content-Length body, leaving the connection open.
    private fun readResponse(input: InputStream): String {
        val head = readHead(input)
        val length = head.lines().first { it.startsWith("Content-Length:", ignoreCase = true) }.substringAfter(':')
        return head + input.readNBytes(length.trim().toInt()).toString(Charsets.ISO_8859_1)
    }

    // A plain request as a client writes it to a proxy: absolute form, its Host naming the same authority.
    private fun get(
        authority: String,
        path: String = "/",
        headers: String = "",
    ): String = "GET http://$authority$path HTTP/1.1\r\nHost: $authority\r\n$headers\r\n"

    private fun connect(authority: String): String = "CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n\r\n"

    private fun originGet(host: String): String = "GET /page HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n"

    private fun statusOf(response: String): String = response.substringBefore("\r\n")

    // What follows the proxy's own head: through a tunnel, the origin's response.
    private fun afterHead(response: String): String = response.substringAfter("\r\n\r\n")

    @Test
    fun `Given a proxy, Then it listens on 127_0_0_1 alone`() {
        // Given
        val proxy = proxy(AddressPolicy.Standard)

        // Then
        assertEquals(loopback, proxy.address.address)
    }

    @Test
    fun `Given AddressPolicy Standard, Then a CONNECT to 127_0_0_1 is refused and recorded`() {
        // Given
        val proxy = proxy(AddressPolicy.Standard)

        // When
        val response = exchange(proxy, connect("127.0.0.1:${originPort()}") + originGet("127.0.0.1"))

        // Then
        assertEquals("HTTP/1.1 403 Forbidden", statusOf(response))
        assertEquals(listOf(loopback), proxy.refusedAddresses)
        assertTrue(originRequests.isEmpty())
    }

    @Test
    fun `Given a host resolving to a private address, Then a CONNECT is refused and recorded`() {
        // Given
        val privateAddress = InetAddress.getByName("10.0.0.1")
        val proxy = proxy(AddressPolicy.Standard) { privateAddress }

        // When
        val response = exchange(proxy, connect("internal.test:443"))

        // Then
        assertEquals("HTTP/1.1 403 Forbidden", statusOf(response))
        assertEquals(listOf(privateAddress), proxy.refusedAddresses)
    }

    @Test
    fun `Given an allowed then a refused answer, Then the tunnel reaches the first after one resolution`() {
        // Given
        val answers = ArrayDeque(listOf(loopback, otherLoopback))
        val proxy =
            proxy(refusingOtherLoopback) {
                resolutions.incrementAndGet()
                answers.removeFirst()
            }

        // When
        val response = exchange(proxy, connect("flip.test:${originPort()}") + originGet("flip.test"))

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(response))
        assertTrue(afterHead(response).startsWith("HTTP/1.1 200 OK"), response)
        assertTrue(response.endsWith("hello"), response)
        assertEquals(1, resolutions.get())
        assertTrue(proxy.refusedAddresses.isEmpty())
    }

    @Test
    fun `Given a redirect to a refused address, Then the second hop is checked again and refused`() {
        // Given
        val stub = mapOf("allowed.test" to loopback, "refused.test" to otherLoopback)
        val proxy = proxy(refusingOtherLoopback) { host -> stub.getValue(host) }
        val client =
            HttpClient.newBuilder()
                .proxy(ProxySelector.of(proxy.address))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        val request =
            HttpRequest.newBuilder(URI("http://allowed.test:${originPort()}/hop"))
                .timeout(Duration.ofMillis(READ_TIMEOUT_MILLIS.toLong()))
                .build()

        // When
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())

        // Then
        assertEquals(FORBIDDEN, response.statusCode())
        assertEquals(listOf("/hop"), originRequests)
        assertEquals(listOf(otherLoopback), proxy.refusedAddresses)
    }

    @Test
    fun `Given a kept-alive connection, Then a second request to a refused host is checked and refused`() {
        // Given
        val stub = mapOf("allowed.test" to loopback, "refused.test" to otherLoopback)
        val proxy = proxy(refusingOtherLoopback) { host -> stub.getValue(host) }

        // When
        val (first, second) =
            connectTo(proxy).use { socket ->
                socket.getOutputStream().write(get("allowed.test:${originPort()}").toByteArray())
                val first = readResponse(socket.getInputStream())
                socket.getOutputStream().write(get("refused.test:${originPort()}").toByteArray())
                first to readHead(socket.getInputStream())
            }

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(first))
        assertTrue(first.endsWith("hello"), first)
        assertEquals("HTTP/1.1 403 Forbidden", statusOf(second))
        assertEquals(1, originRequests.size)
        assertEquals(listOf(otherLoopback), proxy.refusedAddresses)
    }

    @Test
    fun `Given hop-by-hop headers, Then the origin gets the request in origin form without them`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }
        val hopByHop =
            "Connection: keep-alive\r\nKeep-Alive: timeout=5\r\n" +
                "Proxy-Connection: keep-alive\r\nProxy-Authorization: Basic c2VjcmV0\r\n"

        // When
        val response = exchange(proxy, get("allowed.test:${originPort()}", "/page?q=1", hopByHop))

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(response))
        assertTrue(response.endsWith("hello"), response)
        val seen = originRequests.single()
        assertTrue(seen.startsWith("/page?q=1 "), seen)
        assertFalse(seen.contains("Proxy-"), seen)
        assertFalse(seen.contains("Keep-alive"), seen)
        assertFalse(seen.contains("keep-alive"), seen)
    }

    @Test
    fun `Given a forwarded request, Then the origin gets no Via and no Forwarded header`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        exchange(proxy, get("allowed.test:${originPort()}", "/page"))

        // Then
        val seen = originRequests.single()
        assertFalse(seen.contains("Via="), seen)
        assertFalse(seen.contains("Forwarded="), seen)
    }

    @Test
    fun `Given a path with an encoded slash and an empty segment, Then the origin gets the path as sent`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, get("allowed.test:${originPort()}", "/a%2Fb//c"))

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(response))
        assertTrue(originRequests.single().startsWith("/a%2Fb//c "), originRequests.single())
    }

    @Test
    fun `Given an absolute-form request with an empty path, Then the origin is asked for the root`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, get("allowed.test:${originPort()}", path = ""))

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(response))
        assertTrue(originRequests.single().startsWith("/ "), originRequests.single())
    }

    @Test
    fun `Given an origin that closes without answering, Then the client gets 502 and nothing is recorded`() {
        // Given
        val mute = ServerSocket(0, 0, loopback)
        Thread.ofVirtual().start { mute.use { it.accept().use { socket -> socket.getInputStream().read() } } }
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, get("mute.test:${mute.localPort}"))

        // Then
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertTrue(proxy.unreachableHosts.isEmpty())
        assertTrue(proxy.refusedAddresses.isEmpty())
    }

    @Test
    fun `Given a CONNECT with no port, Then the proxy dials port 80`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, connect("allowed.test"))

        // Then nothing listens on port 80 here, so the dial fails
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertEquals(listOf("allowed.test"), proxy.unreachableHosts)
    }

    @Test
    fun `Given a CONNECT to a host that does not answer, Then it is recorded as unreachable`() {
        // Given
        val closedPort = ServerSocket(0, 0, loopback).use { it.localPort }
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, connect("silent.test:$closedPort"))

        // Then
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertEquals(listOf("silent.test"), proxy.unreachableHosts)
        assertTrue(proxy.refusedAddresses.isEmpty())
    }

    @Test
    fun `Given a plain request to a host that does not answer, Then it is recorded as unreachable`() {
        // Given
        val closedPort = ServerSocket(0, 0, loopback).use { it.localPort }
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, get("silent.test:$closedPort"))

        // Then
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertEquals(listOf("silent.test"), proxy.unreachableHosts)
        assertTrue(proxy.refusedAddresses.isEmpty())
    }

    @Test
    fun `Given a CONNECT to a host that does not resolve, Then it is recorded as unreachable`() {
        // Given
        val proxy = proxy(AddressPolicy.Standard) { host -> throw UnknownHostException(host) }

        // When
        val response = exchange(proxy, connect("nowhere.test:443"))

        // Then
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertEquals(listOf("nowhere.test"), proxy.unreachableHosts)
    }

    @Test
    fun `Given a plain request to a host that does not resolve, Then it is recorded as unreachable`() {
        // Given
        val proxy = proxy(AddressPolicy.Standard) { host -> throw UnknownHostException(host) }

        // When
        val response = exchange(proxy, get("nowhere.test"))

        // Then
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertEquals(listOf("nowhere.test"), proxy.unreachableHosts)
    }

    @Test
    fun `Given requests that name no http host, Then each is answered 400 and nothing is dialled`() {
        // Given
        val proxy = countingProxy()
        val requests =
            listOf(
                "NONSENSE\r\n\r\n",
                "GET /relative HTTP/1.1\r\n\r\n",
                "GET http:///no-host HTTP/1.1\r\n\r\n",
                "CONNECT [unclosed HTTP/1.1\r\nHost: [unclosed\r\n\r\n",
                connect("allowed.test:no-port"),
            )

        // When
        val statuses = requests.map { statusOf(exchange(proxy, it)) }

        // Then
        assertEquals(List(requests.size) { "HTTP/1.1 400 Bad Request" }, statuses)
        assertEquals(0, resolutions.get())
        assertTrue(originRequests.isEmpty())
    }

    @Test
    fun `Given an ftp target, Then the proxy answers 502 and dials nothing`() {
        // Given
        val proxy = countingProxy()

        // When
        val response = exchange(proxy, "GET ftp://allowed.test/ HTTP/1.1\r\nHost: allowed.test\r\n\r\n")

        // Then
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertEquals(0, resolutions.get())
    }

    @Test
    fun `Given a head past the bound, Then it is answered 431 and nothing is dialled`() {
        // Given
        val proxy = countingProxy()
        val oversized = "X: ${"a".repeat(OVERSIZED_HEAD)}\r\n"

        // When
        val response = exchange(proxy, get("allowed.test:${originPort()}", headers = oversized))

        // Then
        assertEquals("HTTP/1.1 431 Request Header Fields Too Large", statusOf(response))
        assertEquals(0, resolutions.get())
    }

    @Test
    fun `Given a request head of 20 kB, Then it is forwarded`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }
        val large = "X: ${"a".repeat(LARGE_HEAD)}\r\n"

        // When
        val response = exchange(proxy, get("allowed.test:${originPort()}", headers = large))

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(response))
    }

    @Test
    fun `Given an origin answering a head of 20 kB, Then the client gets it`() {
        // Given
        val large = "a".repeat(LARGE_HEAD)
        origin.createContext("/large") { exchange ->
            exchange.responseHeaders.add("X-Large", large)
            exchange.sendResponseHeaders(OK, -1)
            exchange.close()
        }
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, get("allowed.test:${originPort()}", "/large"))

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(response))
        assertTrue(response.contains(large), "the large header is missing")
    }

    @Test
    fun `Given a head cut short, Then the connection closes and nothing is dialled`() {
        // Given
        val proxy = countingProxy()

        // When
        exchange(proxy, "CONNECT allowed.test:443")

        // Then
        assertEquals(0, resolutions.get())
    }

    @Test
    fun `Given an open tunnel, Then closing the proxy closes it and stops listening`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }
        val tunnel = connectTo(proxy)
        tunnel.getOutputStream().write(connect("idle.test:${originPort()}").toByteArray())
        val established = readHead(tunnel.getInputStream())

        // When
        proxy.close()

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(established))
        assertEquals(-1, tunnel.getInputStream().read())
        tunnel.close()
        assertTrue(proxy.isClosed)
    }

    private companion object {
        const val OK = 200
        const val FOUND = 302
        const val FORBIDDEN = 403
        const val READ_TIMEOUT_MILLIS = 5_000
        const val OVERSIZED_HEAD = 70_000
        const val LARGE_HEAD = 20_000
    }
}
