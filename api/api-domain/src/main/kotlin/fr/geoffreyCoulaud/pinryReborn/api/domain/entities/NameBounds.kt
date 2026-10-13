package fr.geoffreyCoulaud.pinryReborn.api.domain.entities

/** The rule every name of up to [MAX_LENGTH] characters shares, written once for their factories. */
object NameBounds {
    const val MAX_LENGTH = 200

    /** False for a blank text or one longer than [MAX_LENGTH]. */
    fun accepts(text: String): Boolean = !text.isBlank() && text.length <= MAX_LENGTH
}
