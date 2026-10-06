package fr.geoffreyCoulaud.pinryReborn.api.usecases

import java.util.UUID

/**
 * The rows of a sweep selection, one page by `id` at a time until a page comes back empty. A row a page acts on leaves
 * the selection; one it fails on is behind the cursor, so every sweep converges.
 */
object SweepPages {
    /** Pages a sweep may read before it is taken for a selection that does not advance, and stopped loudly. */
    const val MAX_PAGES = 100_000

    fun <T> of(idOf: (T) -> UUID, page: (afterId: UUID?) -> List<T>): Sequence<T> = sequence {
        var afterId: UUID? = null
        var pages = 0
        while (true) {
            val rows = page(afterId)
            if (rows.isEmpty()) break
            pages += 1
            check(pages <= MAX_PAGES) { "a sweep read $MAX_PAGES pages: its selection does not advance" }
            yieldAll(rows)
            afterId = idOf(rows.last())
        }
    }
}
