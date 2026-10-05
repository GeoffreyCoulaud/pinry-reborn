package fr.geoffreyCoulaud.pinryReborn.api.domain.enums

/** What the user decided for one version of a group of duplicates (ADR 0052, decision 3). */
enum class DuplicateDecision {
    KEEP,
    MERGE,
    REJECT,
}
