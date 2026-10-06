package fr.geoffreyCoulaud.pinryReborn.api.application.wiring

import fr.geoffreyCoulaud.pinryReborn.api.domain.exports.ExportArchiveStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.imports.ImportArchiveStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.MediaStore
import fr.geoffreyCoulaud.pinryReborn.api.domain.media.RenditionCache
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaDownloadRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.MediaRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TaskQueueInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataExportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import fr.geoffreyCoulaud.pinryReborn.api.usecases.AccountDeletionCleaner
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ReapOrphanedStorage
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ReapStaleMediaDownloads
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ReapTombstonedAccounts
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.ReapTerminalTasks
import fr.geoffreyCoulaud.pinryReborn.api.worker.GarbageCollectionConfig
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces

/**
 * CDI wiring for the four garbage collection sweeps whose constructor takes a primitive ARC cannot resolve
 * ([ReapOrphanedStorage] takes an `Int` and a `Duration`, [ReapTombstonedAccounts] a `Duration`, [ReapTerminalTasks]
 * and [ReapStaleMediaDownloads] a `Duration`). Mirrors [ExportProducers.reapExpiredUserDataExports]:
 * `GarbageCollectionConfig` lives in `api-worker-quarkus`, so a use case in `api-usecases` cannot take it directly and
 * the primitive is read here. `ReapExpiredSessionTokens` is `@ApplicationScoped` already (its dependencies are all
 * injectable beans), so it has no producer here.
 */
@ApplicationScoped
class GarbageCollectionProducers {
    @Suppress("LongParameterList") // One port and one repository per dataset, plus the batch config.
    @Produces
    @ApplicationScoped
    fun reapOrphanedStorage(
        renditionCache: RenditionCache,
        exportArchiveStore: ExportArchiveStore,
        importArchiveStore: ImportArchiveStore,
        mediaStore: MediaStore,
        mediaRepository: MediaRepositoryInterface,
        userDataExportRepository: UserDataExportRepositoryInterface,
        userDataImportRepository: UserDataImportRepositoryInterface,
        clock: Clock,
        config: GarbageCollectionConfig,
    ): ReapOrphanedStorage =
        ReapOrphanedStorage(
            renditionCache,
            exportArchiveStore,
            importArchiveStore,
            mediaStore,
            mediaRepository,
            userDataExportRepository,
            userDataImportRepository,
            clock,
            batchSize = config.orphanBatchSize(),
            orphanGrace = config.orphanGrace(),
        )

    @Produces
    @ApplicationScoped
    fun reapTombstonedAccounts(
        userRepository: UserRepositoryInterface,
        accountDeletionCleaner: AccountDeletionCleaner,
        clock: Clock,
        config: GarbageCollectionConfig,
    ): ReapTombstonedAccounts =
        ReapTombstonedAccounts(
            userRepository,
            accountDeletionCleaner,
            clock,
            tombstoneGrace = config.tombstoneGrace(),
        )

    @Produces
    @ApplicationScoped
    fun reapStaleMediaDownloads(
        mediaDownloadRepository: MediaDownloadRepositoryInterface,
        taskQueue: TaskQueueInterface,
        clock: Clock,
        config: GarbageCollectionConfig,
    ): ReapStaleMediaDownloads =
        ReapStaleMediaDownloads(
            mediaDownloadRepository,
            taskQueue,
            clock,
            failedGrace = config.failedDownloadGrace(),
        )

    @Produces
    @ApplicationScoped
    fun reapTerminalTasks(
        taskQueue: TaskQueueInterface,
        clock: Clock,
        config: GarbageCollectionConfig,
    ): ReapTerminalTasks =
        ReapTerminalTasks(
            taskQueue,
            clock,
            terminalTaskGrace = config.terminalTaskGrace(),
        )
}
