package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/** A person's name as written; the database alone compares it without case. */
@JvmInline
value class PersonName private constructor(val text: String) {

    override fun toString(): String = text

    companion object {
        const val MAX_LENGTH = 200

        /** Null for a blank text or one longer than [MAX_LENGTH]. */
        fun parse(text: String): PersonName? =
            if (text.isBlank() || text.length > MAX_LENGTH) null else PersonName(text)
    }
}
