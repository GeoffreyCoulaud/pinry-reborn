package fr.geoffreyCoulaud.pinryReborn.api.system

import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.time.temporal.ChronoUnit

@ApplicationScoped
class SystemClock : Clock {
    /**
     * Truncated to the millisecond, within the microsecond PostgreSQL round-trips.
     *
     * Entities carry the instants their use case stamped on them, so a value the clock produces must survive a
     * save-then-read unchanged. `Instant.now()` is nanosecond-resolution on Linux; persisting it and reading it back
     * yields a *different* instant, which silently breaks every value comparison on an entity holding it (an
     * authorization check as blunt as `pin.author != user` starts rejecting the legitimate owner). Keeping the clock's
     * resolution within the store's removes that whole class of bug at the source.
     */
    override fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
}
