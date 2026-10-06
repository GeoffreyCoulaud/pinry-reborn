package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import java.util.UUID

/**
 * Reads the pin, checks it, and saves [update] of it in one transaction; null when it is absent or refused. `savePin`
 * writes every column and both link tables from the copy, so the copy is read here.
 */
internal fun PinRepositoryInterface.saveFenced(
    transactionRunner: TransactionRunner,
    pinId: UUID,
    held: (Pin) -> Boolean,
    update: (Pin) -> Pin,
): Pin? = transactionRunner.fenced({ findPinById(pinId) }, held, update) { savePin(it) }
