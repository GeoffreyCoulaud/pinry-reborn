package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonReference
import java.time.Instant

/**
 * What the importer reads out of a `formatVersion` 2 archive (spec section 4): only the fields it acts on, so an
 * archive identifier never reaches a row. Plain Kotlin, no Jackson: the adapter owns the mapper.
 */

/** `manifest.json`'s `counts`, read for progress display only and never for a decision. */
internal data class ImportedCounts(val pins: Int?)

internal data class ImportedManifest(val formatVersion: Int, val counts: ImportedCounts?)

/** One `tags.jsonl` line. */
internal data class ImportedTag(val name: String, val createdAt: Instant)

/** One `boards.jsonl` line. `softDeletedAt` carries the recycled state a created board is given. */
internal data class ImportedBoard(
    val name: String,
    val description: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val softDeletedAt: Instant?,
)

/** One `collections.jsonl` line. A null [board] links the collection to the board of its own name. */
internal data class ImportedCollection(val url: String, val name: String, val board: ImportedRef? = null)

/** A `{name}` reference: a pin's tag or board, a collection's board. */
internal data class ImportedRef(val name: String)

/** A pin's `{url}` reference to a collection. */
internal data class ImportedCollectionRef(val url: String)

/**
 * A pin's `media` object. `mimeType` and the dimensions are deliberately absent: the manifest is never trusted for
 * anything with a consequence, and `sha256` is read only to be compared and reported.
 */
internal data class ImportedMedia(val path: String, val sha256: String)

/** A pin's publisher or creator, named by the [id] of its `persons.jsonl` line. */
internal data class ImportedPersonRef(val id: String)

/** One `persons.jsonl` line. Its [id] only links the lines of one archive, so no row ever carries it. */
internal data class ImportedPersonLine(
    val id: String,
    val name: String,
    val urls: List<String>,
    val createdAt: Instant,
) {
    /** Read once the line's check passed, so a refused name here is a defect. */
    fun toReference(): PersonReference =
        PersonReference(
            name = checkNotNull(PersonName.parse(name)) { "the line's check let a refused name through: $name" },
            urls = urls.map(ImportedAddress::read).toSet(),
        )
}

/**
 * One `pins.jsonl` line. A null [media] is a pin with no medium, which has no identity to import. The people, the
 * publication instant and the [collections]' addresses default to none, so a line that predates them still reads.
 */
internal data class ImportedPin(
    val description: String,
    val sourceContextUrl: String?,
    val sourceMediaUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val softDeletedAt: Instant?,
    val tags: List<ImportedRef>,
    val boards: List<ImportedRef>,
    val media: ImportedMedia?,
    val publisher: ImportedPersonRef? = null,
    val creators: List<ImportedPersonRef> = emptyList(),
    val publishedAt: Instant? = null,
    val collections: List<ImportedCollectionRef> = emptyList(),
)
