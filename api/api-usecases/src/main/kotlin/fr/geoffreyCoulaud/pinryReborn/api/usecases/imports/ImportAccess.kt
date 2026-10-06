package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataImport
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportState
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImportDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImportNotAwaitingArchiveError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ImportPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.fenced
import fr.geoffreyCoulaud.pinryReborn.api.usecases.fencedOver
import java.util.UUID

/** The one existence-then-ownership check, so no use case can answer either question differently. */
internal fun UserDataImportRepositoryInterface.findOwned(
    user: User,
    importId: UUID,
): UserDataImport {
    val userDataImport = findById(importId) ?: throw ImportDoesNotExistError()
    if (userDataImport.userId != user.id) throw ImportPermissionError()
    return userDataImport
}

/** Owner before state, so a stranger learns nothing from the refusal about an import that is not his. */
internal fun UserDataImportRepositoryInterface.findAwaitingArchive(
    user: User,
    importId: UUID,
): UserDataImport =
    findOwned(user, importId).also {
        if (it.state != UserDataImportState.AWAITING_ARCHIVE) throw ImportNotAwaitingArchiveError()
    }

/**
 * Reads the import, checks it, and saves [update] of it in one transaction; null when it is absent or refused. The
 * write is a lambda, not `::save`, so the detekt rule sees a call inside the fence.
 */
internal fun UserDataImportRepositoryInterface.saveFenced(
    transactionRunner: TransactionRunner,
    importId: UUID,
    held: (UserDataImport) -> Boolean,
    update: (UserDataImport) -> UserDataImport,
): UserDataImport? = transactionRunner.fenced({ findById(importId) }, held, update) { save(it) }

/** [saveFenced], answering the import as it was read rather than as saved: the phase before this write. */
internal fun UserDataImportRepositoryInterface.saveFencedOver(
    transactionRunner: TransactionRunner,
    importId: UUID,
    held: (UserDataImport) -> Boolean,
    update: (UserDataImport) -> UserDataImport,
): UserDataImport? = transactionRunner.fencedOver({ findById(importId) }, held, update) { save(it) }

/**
 * The fence the upload writes take (spec §6): their windows are wide, a chunk streaming to disk and a digest of up to
 * twenty gigabytes, so a caller that lost the phase is refused as a late one is.
 */
internal fun UserDataImportRepositoryInterface.saveWhileAwaitingArchive(
    transactionRunner: TransactionRunner,
    importId: UUID,
    update: (UserDataImport) -> UserDataImport,
): UserDataImport =
    saveFenced(transactionRunner, importId, { it.awaitsItsArchive() }, update) ?: throw ImportNotAwaitingArchiveError()

/** The upload phase, spelled once: the completer opens its own transaction and reads it there too. */
internal fun UserDataImport.awaitsItsArchive(): Boolean = state == UserDataImportState.AWAITING_ARCHIVE
