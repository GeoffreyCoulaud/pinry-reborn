package fr.geoffreyCoulaud.pinryReborn.api.application.wiring

import fr.geoffreyCoulaud.pinryReborn.api.domain.imports.ImportArchiveStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.BoardRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TagRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TaskQueueInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportIssueRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.storage.filesystem.FilesystemZipImportArchiveStore
import fr.geoffreyCoulaud.pinryReborn.api.usecases.MediaIngestion
import fr.geoffreyCoulaud.pinryReborn.api.usecases.TagCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.ImportUploadBounds
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.ReapUserDataImports
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.UserDataImportChunkReceiver
import fr.geoffreyCoulaud.pinryReborn.api.usecases.imports.UserDataImportRunner
import fr.geoffreyCoulaud.pinryReborn.api.worker.ImportsConfig
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces

/** The import beans ARC cannot build itself, their scalars coming from `imports.*` in the worker module. */
@ApplicationScoped
class ImportProducers {
    @Produces
    @ApplicationScoped
    fun importArchiveStore(config: ImportsConfig): ImportArchiveStore =
        FilesystemZipImportArchiveStore(config.dataDir(), config.maxLineBytes())

    // The handshake's copy of the two bounds, so the presentation module reads no `imports.*` mapping.
    @Produces
    @ApplicationScoped
    fun importUploadBounds(config: ImportsConfig): ImportUploadBounds =
        ImportUploadBounds(maxChunkBytes = config.maxChunkBytes(), maxArchiveBytes = config.maxArchiveBytes())

    // LongParameterList: four ports, the clock, and the two Durations ARC cannot resolve on its own.
    @Suppress("LongParameterList")
    @Produces
    @ApplicationScoped
    fun reapAbandonedUserDataImports(
        repository: UserDataImportRepositoryInterface,
        archiveStore: ImportArchiveStore,
        taskQueue: TaskQueueInterface,
        clock: Clock,
        transactionRunner: TransactionRunner,
        config: ImportsConfig,
    ): ReapUserDataImports =
        ReapUserDataImports(
            repository,
            archiveStore,
            taskQueue,
            clock,
            transactionRunner,
            uploadGrace = config.uploadGrace(),
            stagedFileMaxAge = config.stagedFileMaxAge(),
            sweepBatchSize = config.sweepBatchSize(),
        )

    // A producer's parameter list is the injection points of what it builds, and this receiver takes
    // four ports plus the config its two bounds come from. Grouping them would only hide them.
    @Suppress("LongParameterList")
    @Produces
    @ApplicationScoped
    fun userDataImportChunkReceiver(
        repository: UserDataImportRepositoryInterface,
        archiveStore: ImportArchiveStore,
        clock: Clock,
        transactionRunner: TransactionRunner,
        config: ImportsConfig,
    ): UserDataImportChunkReceiver =
        UserDataImportChunkReceiver(
            repository,
            archiveStore,
            clock,
            transactionRunner,
            maxArchiveBytes = config.maxArchiveBytes(),
            minimumFreeBytes = config.minimumFreeBytes(),
        )

    /**
     * An archived medium is bounded by what this instance hosts, through [MediaIngestion]'s own bounds.
     * `LongParameterList`: the ports and the config.
     */
    @Suppress("LongParameterList")
    @Produces
    @ApplicationScoped
    fun userDataImportRunner(
        importRepository: UserDataImportRepositoryInterface,
        issueRepository: UserDataImportIssueRepositoryInterface,
        userRepository: UserRepositoryInterface,
        tagRepository: TagRepositoryInterface,
        boardRepository: BoardRepositoryInterface,
        pinRepository: PinRepositoryInterface,
        mediaRepository: MediaRepositoryInterface,
        archiveStore: ImportArchiveStore,
        mediaIngestion: MediaIngestion,
        tagCreator: TagCreator,
        transactionRunner: TransactionRunner,
        clock: Clock,
        config: ImportsConfig,
    ): UserDataImportRunner =
        UserDataImportRunner(
            importRepository,
            issueRepository,
            userRepository,
            tagRepository,
            boardRepository,
            pinRepository,
            mediaRepository,
            archiveStore,
            mediaIngestion,
            tagCreator,
            transactionRunner,
            clock,
            maxMetadataBytes = config.maxMetadataBytes(),
            maxEntries = config.maxEntries(),
            reportDetailLimit = config.reportDetailLimit(),
        )
}
