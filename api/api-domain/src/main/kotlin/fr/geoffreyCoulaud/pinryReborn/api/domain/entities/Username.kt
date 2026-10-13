package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/** An account's name as written; the database alone compares it without case. */
@JvmInline
value class Username private constructor(val text: String) {

    override fun toString(): String = text

    companion object {
        /** The account alphabet, where the store's case fold and a lower-casing agree (ADR 0059, decision 11). */
        const val PATTERN = "^[A-Za-z0-9._-]+$"

        const val MIN_LENGTH = 3

        const val MAX_LENGTH = 50

        private val ALPHABET = Regex(PATTERN)

        /** Null for a text outside the alphabet or outside [MIN_LENGTH] to [MAX_LENGTH] characters. */
        fun parse(text: String): Username? =
            if (text.length in MIN_LENGTH..MAX_LENGTH && ALPHABET.matches(text)) Username(text) else null
    }
}
