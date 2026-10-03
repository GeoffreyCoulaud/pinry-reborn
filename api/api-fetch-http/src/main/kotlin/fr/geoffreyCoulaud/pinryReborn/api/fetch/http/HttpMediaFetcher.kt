package fr.geoffreyCoulaud.pinryReborn.api.fetch.http

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchAccessDeniedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchFailedException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchNotFoundException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchUnreachableException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.FetchedMedia
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaFetcher
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.TooManyRedirectsException
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.UrlNotAllowedException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.ProxySelector
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HttpMediaFetcher(
    private val connectTimeout: Duration,
    private val requestTimeout: Duration,
    private val maxRedirects: Int,
    private val openProxy: () -> GuardingProxy,
) : MediaFetcher {
    // Each download gets its own proxy, the client's only route out (ADR 0048, decision 2).
    override fun openStream(sourceUrl: String): FetchedMedia {
        val url = httpUri(sourceUrl)
        val proxy = openProxy()
        val client =
            HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.of(proxy.address))
                .build()
        val end = {
            client.shutdownNow()
            proxy.close()
        }
        val response = runCatching { finalResponse(client, proxy, url) }.onFailure { end() }.getOrThrow()
        val contentType = response.headers().firstValue("content-type").orElse(null)
        return FetchedMedia(EndingStream(response.body(), end), contentType)
    }

    // Each throw maps a distinct HTTP outcome to its typed FetchException; that mapping is the
    // adapter's purpose, so the count is intentional.
    @Suppress("ThrowsCount")
    private fun finalResponse(
        client: HttpClient,
        proxy: GuardingProxy,
        first: URI,
    ): HttpResponse<InputStream> {
        var url = first
        var redirects = 0
        while (true) {
            val response = send(client, proxy, url)
            val status = response.statusCode()
            if (status < REDIRECT_MIN) return response
            response.body().close()
            refusalOf(proxy)?.let { throw it }
            // Ascending boundaries: the JDK consumes 1xx itself, and a non-standard 6xx is retried as a 5xx.
            when {
                status < CLIENT_ERROR_MIN -> {
                    if (redirects >= maxRedirects) throw TooManyRedirectsException("too many redirects")
                    val location =
                        response.headers().firstValue("location").orElse(null)
                            ?: throw FetchFailedException("redirect without a location header")
                    url = httpUri(url.resolve(location).toString())
                    redirects += 1
                }
                status == UNAUTHORIZED || status == FORBIDDEN ->
                    throw FetchAccessDeniedException("origin refused access ($status)")
                status == NOT_FOUND || status == GONE ->
                    throw FetchNotFoundException("no image at this url ($status)")
                status == TOO_MANY_REQUESTS ->
                    throw FetchUnreachableException("origin error ($status)")
                status < SERVER_ERROR_MIN ->
                    throw FetchFailedException("unexpected response status $status")
                else -> throw FetchUnreachableException("origin error ($status)")
            }
        }
    }

    private fun send(
        client: HttpClient,
        proxy: GuardingProxy,
        url: URI,
    ): HttpResponse<InputStream> {
        // Note (spec section 17 risk): HttpRequest.timeout() bounds the time to obtain the
        // response headers, not the streaming read of the body. For v1 this is acceptable: the
        // connect timeout plus this response timeout bound the worst case before the body arrives.
        // If a slow-body origin becomes a problem, wrap the returned stream with a read deadline.
        val request = HttpRequest.newBuilder(url).timeout(requestTimeout).GET().build()
        return try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: IOException) {
            throw refusalOf(proxy, e) ?: FetchUnreachableException("could not reach the origin", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw FetchUnreachableException("fetch interrupted", e)
        }
    }

    // The proxy's record names the reason: a tunnel refusal reaches the client as a bare IOException.
    private fun refusalOf(
        proxy: GuardingProxy,
        cause: Throwable? = null,
    ): FetchException? =
        when {
            proxy.refusedAddresses.isNotEmpty() -> UrlNotAllowedException("address not allowed", cause)
            proxy.unreachableHosts.isNotEmpty() -> FetchUnreachableException("could not reach the origin", cause)
            else -> null
        }

    // Each throw rejects a distinct unsafe-URL condition (malformed, bad scheme, no host); the
    // address itself is the proxy's to check.
    @Suppress("ThrowsCount")
    private fun httpUri(raw: String): URI {
        val uri =
            try {
                URI(raw)
            } catch (e: URISyntaxException) {
                throw UrlNotAllowedException("malformed url", e)
            }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") throw UrlNotAllowedException("scheme not allowed")
        if (uri.host == null) throw UrlNotAllowedException("missing host")
        return uri
    }

    // Closing the body ends the download: the client, then its proxy.
    private class EndingStream(
        body: InputStream,
        private val end: () -> Unit,
    ) : FilterInputStream(body) {
        override fun close() =
            try {
                super.close()
            } finally {
                end()
            }
    }

    private companion object {
        const val REDIRECT_MIN = 300
        const val CLIENT_ERROR_MIN = 400
        const val SERVER_ERROR_MIN = 500
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val GONE = 410
        const val TOO_MANY_REQUESTS = 429
    }
}
