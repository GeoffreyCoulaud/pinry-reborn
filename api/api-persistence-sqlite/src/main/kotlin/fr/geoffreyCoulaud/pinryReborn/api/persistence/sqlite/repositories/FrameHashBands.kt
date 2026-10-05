package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.media.PdqHash

/**
 * The sixteen 16-bit bands of a frame hash (ADR 0051, decision 4), as `MediaFrameModel`'s indexes spell them.
 * The lookup's `raw(` literals spell them again, the detekt inventory taking plain literals alone.
 */
object FrameHashBands {
    const val BAND_0 = "((hash_0 >> 48) & 65535)"
    const val BAND_1 = "((hash_0 >> 32) & 65535)"
    const val BAND_2 = "((hash_0 >> 16) & 65535)"
    const val BAND_3 = "((hash_0 >> 0) & 65535)"
    const val BAND_4 = "((hash_1 >> 48) & 65535)"
    const val BAND_5 = "((hash_1 >> 32) & 65535)"
    const val BAND_6 = "((hash_1 >> 16) & 65535)"
    const val BAND_7 = "((hash_1 >> 0) & 65535)"
    const val BAND_8 = "((hash_2 >> 48) & 65535)"
    const val BAND_9 = "((hash_2 >> 32) & 65535)"
    const val BAND_10 = "((hash_2 >> 16) & 65535)"
    const val BAND_11 = "((hash_2 >> 0) & 65535)"
    const val BAND_12 = "((hash_3 >> 48) & 65535)"
    const val BAND_13 = "((hash_3 >> 32) & 65535)"
    const val BAND_14 = "((hash_3 >> 16) & 65535)"
    const val BAND_15 = "((hash_3 >> 0) & 65535)"

    val EXPRESSIONS =
        listOf(
            BAND_0, BAND_1, BAND_2, BAND_3, BAND_4, BAND_5, BAND_6, BAND_7,
            BAND_8, BAND_9, BAND_10, BAND_11, BAND_12, BAND_13, BAND_14, BAND_15,
        )

    private const val BITS = 16
    private const val MASK = 0xFFFFL
    private val SHIFTS = listOf(48, 32, 16, 0)

    /** Per band, in order: the band's value in [hash], then its sixteen values one bit away. */
    fun valuesNear(hash: PdqHash): List<List<Long>> =
        hash.words.flatMap { word ->
            SHIFTS.map { shift ->
                val band = (word ushr shift) and MASK
                listOf(band) + (0 until BITS).map { bit -> band xor (1L shl bit) }
            }
        }
}
