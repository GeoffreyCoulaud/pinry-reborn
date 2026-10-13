package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/** A person's name as written; the database alone compares it without case. */
@JvmInline
value class PersonName private constructor(val text: String) {

    override fun toString(): String = text

    companion object {
        /** Null for a text [NameBounds] refuses. */
        fun parse(text: String): PersonName? = if (NameBounds.accepts(text)) PersonName(text) else null
    }
}
