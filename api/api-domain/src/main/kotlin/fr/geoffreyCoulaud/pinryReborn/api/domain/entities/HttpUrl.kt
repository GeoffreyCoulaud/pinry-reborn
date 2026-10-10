package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

import java.net.URI
import java.net.URISyntaxException

/** An absolute http(s) address, normalised to RFC 3986's equivalences (sections 6.2.2 and 6.2.3). */
@JvmInline
value class HttpUrl private constructor(val uri: URI) {
    override fun toString(): String = uri.toString()

    companion object {
        const val MAX_LENGTH = 2000

        private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443)
        private const val ENCODED_BY_BROWSERS = " \"<>\\^`{|}"
        private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        private const val FIRST_NON_ASCII = 0x80
        private const val HEX_RADIX = 16
        private val LONE_PERCENT = Regex("%(?![0-9A-Fa-f]{2})")
        private val ESCAPE = Regex("%[0-9A-Fa-f]{2}")

        /** Null for a text that is not an absolute http(s) address with a host, or too long once normalised. */
        fun parse(text: String): HttpUrl? {
            val normalised = parseHttp(text)?.let(::normalise)
            return if (normalised == null || normalised.length > MAX_LENGTH) null else HttpUrl(URI(normalised))
        }

        private fun parseHttp(text: String): URI? =
            text
                .takeIf { it.isNotBlank() }
                ?.let { parseOrNull(encodeWhatBrowsersEncode(it)) }
                ?.takeIf { it.scheme?.lowercase() in DEFAULT_PORTS && it.host != null }

        private fun parseOrNull(text: String): URI? =
            try {
                URI(text)
            } catch (_: URISyntaxException) {
                null
            }

        private fun encodeWhatBrowsersEncode(text: String): String {
            val bytes = LONE_PERCENT.replace(text, "%25").toByteArray(Charsets.UTF_8)
            return buildString {
                for (byte in bytes) {
                    val code = byte.toInt() and 0xFF
                    if (code >= FIRST_NON_ASCII || code.toChar() in ENCODED_BY_BROWSERS) {
                        append("%" + code.toString(HEX_RADIX).uppercase().padStart(2, '0'))
                    } else {
                        append(code.toChar())
                    }
                }
            }
        }

        private fun normalise(uri: URI): String {
            val scheme = uri.scheme.lowercase()
            val userInfo = uri.rawUserInfo?.let { normaliseEscapes(it) + "@" }.orEmpty()
            val port = if (uri.port == -1 || uri.port == DEFAULT_PORTS.getValue(scheme)) "" else ":${uri.port}"
            val path = removeDotSegments(normaliseEscapes(uri.rawPath).ifEmpty { "/" })
            val query = uri.rawQuery?.let { "?" + normaliseEscapes(it) }.orEmpty()
            val fragment = uri.rawFragment?.let { "#" + normaliseEscapes(it) }.orEmpty()
            return "$scheme://$userInfo${uri.host.lowercase()}$port$path$query$fragment"
        }

        private fun normaliseEscapes(raw: String): String =
            ESCAPE.replace(raw) { escape ->
                val decoded = escape.value.substring(1).toInt(HEX_RADIX).toChar()
                if (decoded in UNRESERVED) decoded.toString() else escape.value.uppercase()
            }

        // RFC 3986, section 5.2.4; the path is absolute, so its rules A and D never apply.
        private fun removeDotSegments(path: String): String {
            val output = StringBuilder()
            var input = path
            while (input.isNotEmpty()) {
                when {
                    input.startsWith("/./") -> input = input.removePrefix("/.")
                    input == "/." -> input = "/"
                    input.startsWith("/../") -> {
                        input = input.removePrefix("/..")
                        removeLastSegment(output)
                    }
                    input == "/.." -> {
                        input = "/"
                        removeLastSegment(output)
                    }
                    else -> {
                        val end = input.indexOf('/', 1).takeIf { it != -1 } ?: input.length
                        output.append(input, 0, end)
                        input = input.substring(end)
                    }
                }
            }
            return output.toString()
        }

        private fun removeLastSegment(output: StringBuilder) {
            output.setLength(maxOf(output.lastIndexOf("/"), 0))
        }
    }
}
