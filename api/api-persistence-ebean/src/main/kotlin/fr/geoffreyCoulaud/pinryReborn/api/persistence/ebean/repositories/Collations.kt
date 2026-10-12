package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories

/** The column definitions that carry a collation, created by `extra-ddl.xml`'s init script (ADR 0059, decision 12). */
object Collations {
    /** Equality, uniqueness, `like` and order fold case and width in every script (ADR 0059, decision 4). */
    const val CASE_INSENSITIVE_TEXT = "varchar collate case_insensitive"
}
