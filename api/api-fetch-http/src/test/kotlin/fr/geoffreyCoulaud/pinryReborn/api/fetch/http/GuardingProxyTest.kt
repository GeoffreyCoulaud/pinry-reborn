package fr.geoffreyCoulaud.pinryReborn.api.fetch.http

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class GuardingProxyTest {
    private lateinit var origin: HttpServer
    private val originRequests = CopyOnWriteArrayList<String>()
    private val proxies = mutableListOf<GuardingProxy>()

    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")
    private val otherLoopback: InetAddress = InetAddress.getByName("127.0.0.2")

    // Stands for a public address (127.0.0.1, allowed) and a private one (127.0.0.2, refused).
    private val refusingOtherLoopback =
        object : AddressPolicy {
            override fun isAllowed(address: InetAddress): Boolean = address != otherLoopback
        }

    @BeforeEach fun start() {
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

    @AfterEach fun stop() {
        proxies.forEach { it.close() }
        origin.stop(0)
    }

    private fun originPort(): Int = origin.address.port

    private fun proxy(
        policy: AddressPolicy,
        resolve: (String) -> InetAddress = InetAddress::getByName,
    ): GuardingProxy =
        GuardingProxy(policy, Duration.ofSeconds(1), resolve).also { proxies += it }

    // Sends raw bytes to the proxy, ends its side, and reads until the proxy closes the connection.
    private fun exchange(
        proxy: GuardingProxy,
        request: String,
    ): String =
        Socket(proxy.address.address, proxy.address.port).use { socket ->
            socket.soTimeout = READ_TIMEOUT_MILLIS
            socket.getOutputStream().write(request.toByteArray())
            socket.shutdownOutput()
            socket.getInputStream().readAllBytes().toString(Charsets.ISO_8859_1)
        }

    // A proxy closing on unread bytes resets the connection, which the client reads as an exception.
    private fun closesUnanswered(
        proxy: GuardingProxy,
        request: String,
    ): Boolean =
        try {
            exchange(proxy, request).isEmpty()
        } catch (_: SocketException) {
            true
        }

    private fun originGet(host: String): String =
        "GET /page HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n"

    private fun statusOf(response: String): String = response.substringBefore("\r\n")

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
        val response = exchange(proxy, "CONNECT 127.0.0.1:${originPort()} HTTP/1.1\r\n\r\n" + originGet("127.0.0.1"))

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
        val response = exchange(proxy, "CONNECT internal.test:443 HTTP/1.1\r\nHost: internal.test:443\r\n\r\n")

        // Then
        assertEquals("HTTP/1.1 403 Forbidden", statusOf(response))
        assertEquals(listOf(privateAddress), proxy.refusedAddresses)
    }

    @Test
    fun `Given an allowed then a refused answer, Then the tunnel reaches the first after one resolution`() {
        // Given
        val answers = ArrayDeque(listOf(loopback, otherLoopback))
        val resolutions = AtomicInteger()
        val proxy =
            proxy(refusingOtherLoopback) {
                resolutions.incrementAndGet()
                answers.removeFirst()
            }

        // When
        val response =
            exchange(
                proxy,
                "CONNECT flip.test:${originPort()} HTTP/1.1\r\nHost: flip.test\r\n\r\n" + originGet("flip.test"),
            )

        // Then
        assertTrue(response.startsWith("HTTP/1.1 200 Connection Established\r\n\r\nHTTP/1.1 200 OK"), response)
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
    fun `Given an absolute-form request to an allowed host, Then the origin gets an origin-form request with close`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response =
            exchange(
                proxy,
                "GET http://allowed.test:${originPort()}/page?q=1 HTTP/1.1\r\nHost: allowed.test\r\n" +
                    "Connection: keep-alive\r\nKeep-Alive: timeout=5\r\n" +
                    "Proxy-Connection: keep-alive\r\nProxy-Authorization: Basic c2VjcmV0\r\n\r\n",
            )

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(response))
        assertTrue(response.contains("\r\nConnection: close\r\n"), response)
        assertTrue(response.endsWith("hello"), response)
        val seen = originRequests.single()
        assertTrue(seen.startsWith("/page?q=1 "), seen)
        assertTrue(seen.contains("Connection=[close]"), seen)
        assertFalse(seen.contains("Proxy-"), seen)
        assertFalse(seen.contains("Keep-alive"), seen)
    }

    @Test
    fun `Given an absolute-form request with an empty path, Then the origin is asked for the root`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response =
            exchange(proxy, "GET http://allowed.test:${originPort()} HTTP/1.1\r\nHost: allowed.test\r\n\r\n")

        // Then
        assertEquals("HTTP/1.1 200 OK", statusOf(response))
        assertTrue(originRequests.single().startsWith("/ "), originRequests.single())
    }

    @Test
    fun `Given an origin that closes without answering, Then the client's connection closes unanswered`() {
        // Given
        val mute = ServerSocket(0, 0, loopback)
        Thread.ofVirtual().start { mute.use { it.accept().use { socket -> socket.getInputStream().read() } } }
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, "GET http://mute.test:${mute.localPort}/ HTTP/1.1\r\n\r\n")

        // Then
        assertEquals("", response)
    }

    @Test
    fun `Given a CONNECT with no port, Then the proxy dials port 80`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, "CONNECT allowed.test HTTP/1.1\r\n\r\n")

        // Then nothing listens on port 80 here, so the dial fails
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertEquals(listOf("allowed.test"), proxy.unreachableHosts)
    }

    @Test
    fun `Given a host that does not answer, Then it is recorded as unreachable`() {
        // Given
        val closedPort = ServerSocket(0, 0, loopback).use { it.localPort }
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, "CONNECT silent.test:$closedPort HTTP/1.1\r\n\r\n")

        // Then
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertEquals(listOf("silent.test"), proxy.unreachableHosts)
        assertTrue(proxy.refusedAddresses.isEmpty())
    }

    @Test
    fun `Given a host that does not resolve, Then it is recorded as unreachable`() {
        // Given
        val proxy = proxy(AddressPolicy.Standard) { host -> throw UnknownHostException(host) }

        // When
        val response = exchange(proxy, "CONNECT nowhere.test:443 HTTP/1.1\r\n\r\n")

        // Then
        assertEquals("HTTP/1.1 502 Bad Gateway", statusOf(response))
        assertEquals(listOf("nowhere.test"), proxy.unreachableHosts)
    }

    @Test
    fun `Given requests that name no http host, Then each is answered 400`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }
        val requests =
            listOf(
                "NONSENSE\r\n\r\n",
                "GET /relative HTTP/1.1\r\n\r\n",
                "GET ftp://allowed.test/ HTTP/1.1\r\n\r\n",
                "GET http:///no-host HTTP/1.1\r\n\r\n",
                "CONNECT [unclosed HTTP/1.1\r\n\r\n",
                "CONNECT allowed.test:no-port HTTP/1.1\r\n\r\n",
            )

        // When
        val statuses = requests.map { statusOf(exchange(proxy, it)) }

        // Then
        assertEquals(List(requests.size) { "HTTP/1.1 400 Bad Request" }, statuses)
        assertTrue(originRequests.isEmpty())
    }

    @Test
    fun `Given a head past the bound, Then the connection closes unanswered and nothing is dialled`() {
        // Given
        val resolutions = AtomicInteger()
        val proxy =
            proxy(AddressPolicy.AllowAll) {
                resolutions.incrementAndGet()
                loopback
            }
        val oversized = "CONNECT allowed.test:${originPort()} HTTP/1.1\r\nX: ${"a".repeat(OVERSIZED_HEAD)}\r\n\r\n"

        // When
        val unanswered = closesUnanswered(proxy, oversized)

        // Then
        assertTrue(unanswered)
        assertEquals(0, resolutions.get())
    }

    @Test
    fun `Given a head cut short, Then the connection closes unanswered`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }

        // When
        val response = exchange(proxy, "CONNECT allowed.test:443")

        // Then
        assertEquals("", response)
    }

    @Test
    fun `Given an open tunnel, Then closing the proxy closes it and stops listening`() {
        // Given
        val proxy = proxy(AddressPolicy.AllowAll) { loopback }
        val tunnel = Socket(proxy.address.address, proxy.address.port)
        tunnel.soTimeout = READ_TIMEOUT_MILLIS
        tunnel.getOutputStream().write("CONNECT idle.test:${originPort()} HTTP/1.1\r\n\r\n".toByteArray())
        val established = tunnel.getInputStream().readNBytes("HTTP/1.1 200 Connection Established\r\n\r\n".length)

        // When
        proxy.close()

        // Then
        assertEquals("HTTP/1.1 200 Connection Established\r\n\r\n", established.toString(Charsets.ISO_8859_1))
        assertEquals(-1, tunnel.getInputStream().read())
        tunnel.close()
        assertThrows(ConnectException::class.java) { Socket(proxy.address.address, proxy.address.port) }
    }

    private companion object {
        const val OK = 200
        const val FOUND = 302
        const val FORBIDDEN = 403
        const val READ_TIMEOUT_MILLIS = 5_000
        const val OVERSIZED_HEAD = 70_000
    }
}
