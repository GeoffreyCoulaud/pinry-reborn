package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/** A tag's name as written; the database alone compares it without case. */
@JvmInline
value class TagName private constructor(val text: String) {

    override fun toString(): String = text

    companion object {
        /** Null for a text [NameBounds] refuses. */
        fun parse(text: String): TagName? = if (NameBounds.accepts(text)) TagName(text) else null
    }
}
