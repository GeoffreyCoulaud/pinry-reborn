package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * ADR 0015 decision 3: the archive's instants are restored, clamped at both ends. Clamping only the future, as the
 * first draft did, let a valid `Instant` of year -999999999 reach a pagination sort key.
 */
class ImportInstantClamp(
    private val accountCreatedAt: Instant,
    private val importInstant: Instant,
) {
    /** Truncated to the millisecond, as `SystemClock` stamps, so the store gives back what was restored. */
    fun clamp(instant: Instant): Instant =
        instant.truncatedTo(ChronoUnit.MILLIS).coerceAtLeast(accountCreatedAt).coerceAtMost(importInstant)

    /** Floored at the row's own clamped creation: nothing was modified before it existed. */
    fun clampUpdate(updatedAt: Instant, createdAt: Instant): Instant = clamp(updatedAt).coerceAtLeast(createdAt)
}
