package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/** A board's name as written; the database alone compares it without case. */
@JvmInline
value class BoardName private constructor(val text: String) {

    override fun toString(): String = text

    companion object {
        /** Null for a text [NameBounds] refuses. */
        fun parse(text: String): BoardName? = if (NameBounds.accepts(text)) BoardName(text) else null
    }
}
