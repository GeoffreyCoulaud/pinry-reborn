package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.NameBounds
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person

/**
 * Spec section 4.1's bounds on the description, the addresses and the references, restated because their only
 * invariants live on REST input DTOs. Each answers null when the field passes, else the reported reason.
 */
object ImportFieldBounds {
    /** The report's text for a name its type refuses. */
    const val NAME_FAULT = "name is blank or longer than ${NameBounds.MAX_LENGTH} characters"
    const val MAX_DESCRIPTION_LENGTH = 2000
    const val MAX_REFERENCES = 100

    /** Spec section 4: anchored, so `elsewhere/media/x.png` is not a match, and never a `.` segment. */
    private val ENTRY_PATH = Regex("^media/[A-Za-z0-9._-]+$")
    private val TRAVERSAL_SEGMENTS = setOf(".", "..")

    fun descriptionFault(description: String): String? =
        if (description.length > MAX_DESCRIPTION_LENGTH) {
            "description is longer than $MAX_DESCRIPTION_LENGTH characters"
        } else {
            null
        }

    fun httpAddressFault(field: String, url: String): String? =
        if (HttpUrl.parse(url) == null) "$field is not an absolute http(s) address" else null

    /** The list is resolved inside one transaction, so its length is that transaction's size. */
    fun referenceCountFault(field: String, count: Int): String? =
        if (count > MAX_REFERENCES) "$field holds more than $MAX_REFERENCES entries" else null

    /** `PersonInputDto`'s count; the name and each address go through their own factories. */
    fun personFault(urls: List<String>): String? =
        if (urls.size > Person.MAX_URLS) "a person holds more than ${Person.MAX_URLS} addresses" else null

    /**
     * Traversal cannot reach the disk anyway: an entry name is only ever a ZIP lookup key. The check exists so a
     * malformed archive is reported rather than silently skipped.
     */
    fun entryPathFault(path: String): String? =
        when {
            !ENTRY_PATH.matches(path) -> "path is not an anchored media/<name>"
            path.substringAfterLast('/') in TRAVERSAL_SEGMENTS -> "path ends in a traversal segment"
            else -> null
        }
}
