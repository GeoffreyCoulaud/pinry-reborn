package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/** A person's name as written, equal to another folded on ASCII case alone, as `collate nocase` compares it. */
class PersonName private constructor(val text: String) {
    private val folded = text.map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")

    override fun equals(other: Any?): Boolean = other is PersonName && other.folded == folded

    override fun hashCode(): Int = folded.hashCode()

    override fun toString(): String = text

    companion object {
        const val MAX_LENGTH = 200

        /** Null for a blank text or one longer than [MAX_LENGTH]. */
        fun parse(text: String): PersonName? =
            if (text.isBlank() || text.length > MAX_LENGTH) null else PersonName(text)
    }
}
