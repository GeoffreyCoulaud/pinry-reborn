package fr.geoffreyCoulaud.pinryReborn.api.fetch.http

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchUnreachableException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UrlNotAllowedException
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** One download's loopback HTTP proxy, which dials only addresses [addressPolicy] allows (ADR 0048, decision 2). */
class GuardingProxy(
    private val addressPolicy: AddressPolicy,
    private val connectTimeout: Duration,
    private val resolve: (String) -> InetAddress = InetAddress::getByName,
) : AutoCloseable {
    private val server = ServerSocket(0, 0, InetAddress.getByName(LOOPBACK))
    private val sockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
    private val refused = CopyOnWriteArrayList<InetAddress>()
    private val unreachable = CopyOnWriteArrayList<String>()

    val address = InetSocketAddress(server.inetAddress, server.localPort)

    /** The addresses the policy refused, which make a failed download `URL_NOT_ALLOWED`. */
    val refusedAddresses: List<InetAddress> get() = refused.toList()

    /** The hosts that did not resolve or did not accept the connection, which make it `UNREACHABLE`. */
    val unreachableHosts: List<String> get() = unreachable.toList()

    /** The reason the record gives a failed download, if any: a refusal reaches neither client as such. */
    fun refusal(cause: Throwable? = null): FetchException? =
        when {
            refused.isNotEmpty() -> UrlNotAllowedException("address not allowed", cause)
            unreachable.isNotEmpty() -> FetchUnreachableException("could not reach the origin", cause)
            else -> null
        }

    init {
        Thread.ofVirtual().start(::acceptAll)
    }

    override fun close() {
        server.close()
        sockets.forEach(Socket::close)
    }

    private fun acceptAll() {
        while (true) {
            val client =
                try {
                    server.accept()
                } catch (_: SocketException) {
                    return
                }
            Thread.ofVirtual().start { serve(client) }
        }
    }

    private fun serve(client: Socket) =
        tracked(client) {
            val input = BufferedInputStream(client.getInputStream())
            val head = (readHead(input) ?: return@tracked).split(CRLF)
            val requestLine = head.first().split(' ')
            try {
                val target = targetOf(requestLine) ?: throw Refusal(BAD_REQUEST)
                tracked(dial(target)) { upstream ->
                    if (requestLine.first() == CONNECT) {
                        tunnel(client, input, upstream)
                    } else {
                        forward(requestLine, head.drop(1), target, client, input, upstream)
                    }
                }
            } catch (refusal: Refusal) {
                client.getOutputStream().write(headOf(listOf("HTTP/1.1 ${refusal.status}", "Content-Length: 0")))
            }
        }

    private fun dial(target: URI): Socket {
        val address = resolveOnce(target.host)
        val upstream = Socket()
        try {
            upstream.connect(InetSocketAddress(address, portOf(target)), connectTimeout.toMillis().toInt())
        } catch (e: IOException) {
            upstream.close()
            unreachable += target.host
            throw Refusal(BAD_GATEWAY, e)
        }
        return upstream
    }

    // The one resolution of this connection: the address checked is the address dialled.
    private fun resolveOnce(host: String): InetAddress {
        val address =
            try {
                resolve(host)
            } catch (e: UnknownHostException) {
                unreachable += host
                throw Refusal(BAD_GATEWAY, e)
            }
        if (!addressPolicy.isAllowed(address)) {
            refused += address
            throw Refusal(FORBIDDEN)
        }
        return address
    }

    private fun tunnel(
        client: Socket,
        clientInput: InputStream,
        upstream: Socket,
    ) {
        client.getOutputStream().write(headOf(listOf("HTTP/1.1 200 Connection Established")))
        pumpToUpstream(clientInput, upstream)
        upstream.getInputStream().transferTo(client.getOutputStream())
    }

    // Both heads say close, so a client that keeps its connection alive cannot send another host down it.
    @Suppress("LongParameterList")
    private fun forward(
        requestLine: List<String>,
        headers: List<String>,
        target: URI,
        client: Socket,
        clientInput: InputStream,
        upstream: Socket,
    ) {
        val (method, _, version) = requestLine
        val path = target.rawPath.ifEmpty { "/" } + target.rawQuery?.let { "?$it" }.orEmpty()
        upstream.getOutputStream().write(headOf(listOf("$method $path $version") + closing(headers)))
        pumpToUpstream(clientInput, upstream)
        val upstreamInput = BufferedInputStream(upstream.getInputStream())
        val response = (readHead(upstreamInput) ?: return).split(CRLF)
        client.getOutputStream().write(headOf(listOf(response.first()) + closing(response.drop(1))))
        upstreamInput.transferTo(client.getOutputStream())
    }

    // The client's half-close reaches the origin; the origin's end returns, which closes both sockets.
    private fun pumpToUpstream(
        clientInput: InputStream,
        upstream: Socket,
    ) {
        Thread.ofVirtual().start {
            quietly {
                clientInput.transferTo(upstream.getOutputStream())
                upstream.shutdownOutput()
            }
        }
    }

    private inline fun tracked(
        socket: Socket,
        block: (Socket) -> Unit,
    ) {
        // ponytail: a socket accepted during close() escapes it, and ends when its download's client closes.
        sockets += socket
        try {
            quietly { socket.use(block) }
        } finally {
            sockets -= socket
        }
    }

    // A socket closed under a transfer, by the peer or by close(), ends that connection and nothing else.
    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (_: IOException) {
            return
        }
    }

    private class Refusal(
        val status: String,
        cause: Throwable? = null,
    ) : Exception(status, cause)

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val CONNECT = "CONNECT"
        const val CRLF = "\r\n"
        const val CRLF_CRLF = 0x0D0A0D0A
        const val REQUEST_LINE_PARTS = 3
        const val MAX_HEAD_BYTES = 65_536
        const val HTTP_PORT = 80
        const val BAD_REQUEST = "400 Bad Request"
        const val FORBIDDEN = "403 Forbidden"
        const val BAD_GATEWAY = "502 Bad Gateway"

        // A CONNECT names an authority, any other method an absolute http URI; both reduce to a host and a port.
        fun targetOf(requestLine: List<String>): URI? {
            if (requestLine.size != REQUEST_LINE_PARTS) return null
            val (method, target) = requestLine
            val uri = runCatching { URI(if (method == CONNECT) "http://$target" else target) }.getOrNull()
            return uri?.takeIf { it.scheme == "http" && it.host != null }
        }

        fun portOf(target: URI): Int = if (target.port == -1) HTTP_PORT else target.port

        fun closing(headers: List<String>): List<String> = headers.filterNot(::isHopByHop) + "Connection: close"

        fun isHopByHop(header: String): Boolean {
            val name = header.substringBefore(':').trim().lowercase()
            return name == "connection" || name == "keep-alive" || name.startsWith("proxy-")
        }

        fun readHead(input: InputStream): String? {
            val head = ByteArrayOutputStream()
            var lastFour = 0
            while (lastFour != CRLF_CRLF) {
                val byte = input.read()
                if (byte == -1 || head.size() == MAX_HEAD_BYTES) return null
                head.write(byte)
                lastFour = (lastFour shl Byte.SIZE_BITS) or byte
            }
            return head.toString(Charsets.ISO_8859_1).removeSuffix(CRLF + CRLF)
        }

        fun headOf(lines: List<String>): ByteArray =
            lines.joinToString(separator = CRLF, postfix = CRLF + CRLF).toByteArray(Charsets.ISO_8859_1)
    }
}
