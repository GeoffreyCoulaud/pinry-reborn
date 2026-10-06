package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http

import jakarta.ws.rs.core.StreamingOutput
import java.io.InputStream
import java.io.OutputStream
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder

/** A stored body served whole as `200`, or one [ByteRange] of it as `206`, both under `Accept-Ranges: bytes`. */
object ByteRangeResponse {
    private const val COPY_BUFFER_SIZE = 8192

    /** [open] runs only when the body is written, so a response never written holds no file open. */
    fun builder(open: () -> InputStream, totalSize: Long, range: ByteRange?): ResponseBuilder<StreamingOutput> {
        val sliceLength = range?.let { it.endInclusive - it.start + 1 } ?: totalSize
        val status = if (range != null) RestResponse.Status.PARTIAL_CONTENT else RestResponse.Status.OK
        val body = StreamingOutput { output ->
            open().use { stream ->
                stream.skipNBytes(range?.start ?: 0)
                copyBounded(stream, output, sliceLength)
            }
        }
        val builder =
            ResponseBuilder.create(status, body).header("Content-Length", sliceLength).header("Accept-Ranges", "bytes")
        if (range != null) builder.header("Content-Range", "bytes ${range.start}-${range.endInclusive}/$totalSize")
        return builder
    }

    // Not `copyTo`, which reads to the end and would overrun a slice's announced `Content-Length`.
    private fun copyBounded(input: InputStream, output: OutputStream, byteCount: Long) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var remaining = byteCount
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
}
