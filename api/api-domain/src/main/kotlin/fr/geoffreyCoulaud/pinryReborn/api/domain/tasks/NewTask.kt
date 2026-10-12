package fr.geoffreyCoulaud.pinryReborn.api.domain.tasks

import java.time.Instant

data class NewTask(
    val kind: String,
    val payload: String,
    val availableAt: Instant,
    val priority: Int = 0,
    val maxAttempts: Int,
    val dedupKey: String? = null,
) {
    init {
        require(dedupKey == null || dedupKey.length <= MAX_DEDUP_KEY_LENGTH) {
            "a dedup key holds at most $MAX_DEDUP_KEY_LENGTH characters"
        }
    }

    companion object {
        /** A task kind, a colon and an identifier fit; the key's index entry has a bound. */
        const val MAX_DEDUP_KEY_LENGTH = 100
    }
}
