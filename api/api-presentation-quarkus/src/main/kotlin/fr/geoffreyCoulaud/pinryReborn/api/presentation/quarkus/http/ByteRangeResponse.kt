package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.http

import jakarta.ws.rs.core.StreamingOutput
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder
import java.io.InputStream
import java.io.OutputStream

/** A stored body served whole as `200`, or one [ByteRange] of it as `206`, both under `Accept-Ranges: bytes`. */
object ByteRangeResponse {
    private const val COPY_BUFFER_SIZE = 8192

    fun builder(stream: InputStream, totalSize: Long, range: ByteRange?): ResponseBuilder<StreamingOutput> {
        if (range != null) stream.skipNBytes(range.start)
        val sliceLength = range?.let { it.endInclusive - it.start + 1 } ?: totalSize
        val status = if (range != null) RestResponse.Status.PARTIAL_CONTENT else RestResponse.Status.OK
        val body = StreamingOutput { output -> stream.use { copyBounded(it, output, sliceLength) } }
        val builder = ResponseBuilder.create(status, body)
            .header("Content-Length", sliceLength)
            .header("Accept-Ranges", "bytes")
        if (range != null) builder.header("Content-Range", "bytes ${range.start}-${range.endInclusive}/$totalSize")
        return builder
    }

    /**
     * Copies exactly [byteCount] bytes from [input] to [output]. Deliberately NOT `copyTo`, which
     * streams to end-of-file: that would contradict an announced `Content-Length` on a range slice.
     * Stops early on end-of-stream instead of looping forever, even though that should not happen
     * in practice (the announced size always comes from the same row as the bytes on disk).
     */
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
