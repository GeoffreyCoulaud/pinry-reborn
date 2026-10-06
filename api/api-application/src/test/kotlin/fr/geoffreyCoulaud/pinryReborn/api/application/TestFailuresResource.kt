package fr.geoffreyCoulaud.pinryReborn.api.application

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.ServiceUnavailableException
import jakarta.ws.rs.core.MediaType
import java.io.IOException

/**
 * Drives the rows of `docs/specs/2026-09-05-p2-debt-elimination.md` 4.5 that the API reaches on its own nowhere: the
 * two `500`s, the umbrella `WebApplicationException`, the `406` (no route declares `@Produces`). Runs nothing.
 */
@Path("/test/failures")
class TestFailuresResource {
    @GET @Path("/illegal-state") fun illegalState(): String = error(MARKER)

    @GET @Path("/io") fun io(): String = throw IOException(MARKER)

    @GET @Path("/service-unavailable") fun serviceUnavailable(): String = throw ServiceUnavailableException()

    @GET
    @Path("/json-only")
    @Produces(MediaType.APPLICATION_JSON)
    fun jsonOnly(): Map<String, String> = mapOf("served" to "json")

    companion object {
        /** A refusal's body must carry this nowhere. */
        const val MARKER = "marker-7c1e-never-published"
    }
}
