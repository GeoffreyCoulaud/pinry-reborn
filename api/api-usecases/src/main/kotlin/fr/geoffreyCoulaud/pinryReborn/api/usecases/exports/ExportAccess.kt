package fr.geoffreyCoulaud.pinryReborn.api.usecases.exports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataExport
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataExportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.fenced
import fr.geoffreyCoulaud.pinryReborn.api.usecases.fencedOver
import java.util.UUID

/**
 * Reads the export, checks it, and saves [update] of it in one transaction; null when it is absent or refused. The
 * write is a lambda, not `::save`, so the detekt rule sees a call inside the fence.
 */
internal fun UserDataExportRepositoryInterface.saveFenced(
    transactionRunner: TransactionRunner,
    exportId: UUID,
    held: (UserDataExport) -> Boolean,
    update: (UserDataExport) -> UserDataExport,
): UserDataExport? = transactionRunner.fenced({ findById(exportId) }, held, update) { save(it) }

/** [saveFenced], answering the export as it was read rather than as saved: the state before this write. */
internal fun UserDataExportRepositoryInterface.saveFencedOver(
    transactionRunner: TransactionRunner,
    exportId: UUID,
    held: (UserDataExport) -> Boolean,
    update: (UserDataExport) -> UserDataExport,
): UserDataExport? = transactionRunner.fencedOver({ findById(exportId) }, held, update) { save(it) }
