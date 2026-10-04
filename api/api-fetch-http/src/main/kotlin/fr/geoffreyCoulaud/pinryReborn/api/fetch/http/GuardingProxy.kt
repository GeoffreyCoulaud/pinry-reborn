package fr.geoffreyCoulaud.pinryReborn.api.fetch.http

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchUnreachableException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UrlNotAllowedException
import org.eclipse.jetty.client.Destination
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.transport.HttpClientTransportDynamic
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.http.HttpHeaderValue
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.http.UriCompliance
import org.eclipse.jetty.io.ClientConnector
import org.eclipse.jetty.proxy.ProxyHandler
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.ConnectHandler
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.SocketAddressResolver
import org.eclipse.jetty.util.thread.VirtualThreadPool
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.UnknownHostException
import java.nio.channels.SelectableChannel
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import org.eclipse.jetty.client.Request as UpstreamRequest
import org.eclipse.jetty.client.Response as UpstreamResponse

/** One download's loopback HTTP proxy, which dials only addresses [addressPolicy] allows (ADR 0048, decision 2). */
class GuardingProxy(
    private val addressPolicy: AddressPolicy,
    private val connectTimeout: Duration,
    private val resolve: (String) -> InetAddress = InetAddress::getByName,
) : AutoCloseable {
    private val refused = CopyOnWriteArrayList<InetAddress>()
    private val unreachable = CopyOnWriteArrayList<String>()
    private val server = Server(VirtualThreadPool())
    private val connector = ServerConnector(server, HttpConnectionFactory(boundedHeads())).apply { host = LOOPBACK }

    init {
        server.addConnector(connector)
        server.handler = Tunnels().apply { handler = Forwards() }
        server.start()
    }

    val address = InetSocketAddress(LOOPBACK, connector.localPort)

    /** The addresses the policy refused, which make a failed download `URL_NOT_ALLOWED`. */
    val refusedAddresses: List<InetAddress> get() = refused.toList()

    /** The hosts that did not resolve or did not accept the connection, which make it `UNREACHABLE`. */
    val unreachableHosts: List<String> get() = unreachable.toList()

    /** Whether the proxy has stopped listening. */
    internal val isClosed: Boolean get() = server.isStopped

    /** The reason the record gives a failed download, if any: a refusal reaches neither client as such. */
    fun refusal(cause: Throwable? = null): FetchException? =
        when {
            refused.isNotEmpty() -> UrlNotAllowedException("address not allowed", cause)
            unreachable.isNotEmpty() -> FetchUnreachableException("could not reach the origin", cause)
            else -> null
        }

    override fun close() = server.stop()

    // The one resolution of a connection, shared by both paths: the address checked is the address dialled.
    private fun checkedAddress(host: String): InetAddress {
        val address =
            try {
                resolve(host)
            } catch (e: UnknownHostException) {
                unreachable += host
                throw Refusal(HttpStatus.BAD_GATEWAY_502, e)
            }
        if (!addressPolicy.isAllowed(address)) {
            refused += address
            throw Refusal(HttpStatus.FORBIDDEN_403)
        }
        return address
    }

    // HTTPS: a CONNECT tunnel to the checked address.
    private inner class Tunnels : ConnectHandler() {
        init {
            connectTimeout = this@GuardingProxy.connectTimeout.toMillis()
        }

        override fun newConnectAddress(host: String, port: Int) = InetSocketAddress(checkedAddress(host), port)

        // Reached by a refusal, recorded already, and by a dial that fails; Jetty's parser answers a bad authority.
        override fun onConnectFailure(request: Request, response: Response, callback: Callback, failure: Throwable) {
            val status =
                if (failure is Refusal) {
                    failure.status
                } else {
                    unreachable += request.httpURI.host
                    HttpStatus.BAD_GATEWAY_502
                }
            response.headers.put(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE)
            Response.writeError(request, response, callback, status)
        }
    }

    // Plain HTTP: each request forwarded on its own, through a client that resolves through the guard.
    private inner class Forwards : ProxyHandler.Forward() {
        init {
            isUseServerThreadPool = true
        }

        override fun newHttpClient() =
            HttpClient(HttpClientTransportDynamic(DialRecorder())).apply {
                socketAddressResolver = SocketAddressResolver { host, port, _, promise ->
                    runCatching { InetSocketAddress(checkedAddress(host), port) }
                        .fold({ promise.succeeded(listOf(it)) }, promise::failed)
                }
                maxRequestHeadersSize = MAX_HEAD_BYTES
                maxResponseHeadersSize = MAX_HEAD_BYTES
            }

        // The origin sees what a direct fetch would send: no Via, no Forwarded.
        override fun addProxyHeaders(clientToProxyRequest: Request, proxyToServerRequest: UpstreamRequest) = Unit

        override fun onServerToProxyResponseFailure(
            clientToProxyRequest: Request,
            proxyToServerRequest: UpstreamRequest,
            serverToProxyResponse: UpstreamResponse,
            proxyToClientResponse: Response,
            proxyToClientCallback: Callback,
            failure: Throwable,
        ) {
            if (failure is Refusal) {
                Response.writeError(clientToProxyRequest, proxyToClientResponse, proxyToClientCallback, failure.status)
            } else {
                super.onServerToProxyResponseFailure(
                    clientToProxyRequest,
                    proxyToServerRequest,
                    serverToProxyResponse,
                    proxyToClientResponse,
                    proxyToClientCallback,
                    failure,
                )
            }
        }
    }

    // Jetty's client reports a failed dial here alone, with the destination it was for.
    private inner class DialRecorder : ClientConnector() {
        init {
            connectTimeout = this@GuardingProxy.connectTimeout
        }

        override fun connectFailed(
            channel: SelectableChannel?,
            address: SocketAddress?,
            failure: Throwable,
            context: Map<String, Any>,
        ) {
            unreachable += (context.getValue(Destination.CONTEXT_KEY) as Destination).origin.address.host
            super.connectFailed(channel, address, failure, context)
        }
    }

    private class Refusal(
        val status: Int,
        cause: Throwable? = null,
    ) : Exception(HttpStatus.getMessage(status), cause)

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val MAX_HEAD_BYTES = 65_536

        // The proxy never reads a path, so it forwards any path an origin may accept, `%2F` and `//` included.
        fun boundedHeads() =
            HttpConfiguration().apply {
                requestHeaderSize = MAX_HEAD_BYTES
                responseHeaderSize = MAX_HEAD_BYTES
                uriCompliance = UriCompliance.UNSAFE
            }
    }
}
