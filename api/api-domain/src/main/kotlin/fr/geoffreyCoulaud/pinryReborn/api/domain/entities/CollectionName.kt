package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/** A remote collection's name as written; its address, not its name, identifies it. */
@JvmInline
value class CollectionName private constructor(val text: String) {

    override fun toString(): String = text

    companion object {
        /** Null for a text [NameBounds] refuses. */
        fun parse(text: String): CollectionName? = if (NameBounds.accepts(text)) CollectionName(text) else null
    }
}
