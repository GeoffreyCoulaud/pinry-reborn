package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner

/**
 * Reads a row, checks it with [held], and writes [update] of it, all in one transaction. Answers the written row, or
 * null when no row was read or [held] refused it (`docs/adr/0016`).
 */
internal fun <T : Any> TransactionRunner.fenced(
    read: () -> T?,
    held: (T) -> Boolean,
    update: (T) -> T,
    write: (T) -> T,
): T? = inTransaction { read()?.takeIf(held)?.let { write(update(it)) } }

/**
 * [fenced], answering the row as it was read rather than as it was written. A caller that decides its next step from
 * the state before this write takes that state from here, not from an earlier read.
 */
internal fun <T : Any> TransactionRunner.fencedOver(
    read: () -> T?,
    held: (T) -> Boolean,
    update: (T) -> T,
    write: (T) -> T,
): T? = inTransaction { read()?.takeIf(held)?.also { write(update(it)) } }
