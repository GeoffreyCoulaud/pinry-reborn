package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/**
 * A person's addresses in the one form that identifies it, sorted and distinct (ADR 0055, decision 8). The private
 * constructor is what makes every write path go through [of].
 */
@ConsistentCopyVisibility
data class PersonUrls private constructor(val values: List<String>) {
    /** The stored form the unique index compares: line-feed joined, empty for none. */
    val joined: String
        get() = values.joinToString(SEPARATOR)

    companion object {
        // An address never holds one, the bounds refusing it at the edge.
        private const val SEPARATOR = "\n"

        fun of(urls: Collection<String>): PersonUrls = PersonUrls(urls.toSortedSet().toList())

        fun parse(joined: String): PersonUrls = of(if (joined.isEmpty()) emptyList() else joined.split(SEPARATOR))
    }
}
