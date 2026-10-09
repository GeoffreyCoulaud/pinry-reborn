# Data-modelling violations: inventory

Read-only audit of `api/` and `contract/openapi.json` against the rules of ADR 0056, by an agent on 2026-10-09 at
`1fc81bdc`: every line number is that commit's. Sizes are estimates from `grep -l`. Paths are abbreviated:
`D/` = `api/api-domain/src/main/kotlin/fr/geoffreyCoulaud/pinryReborn/api/domain/`,
`P/` = `api/api-persistence-sqlite/src/main/kotlin/fr/geoffreyCoulaud/pinryReborn/api/persistence/sqlite/`,
`U/` = `api/api-usecases/src/main/kotlin/fr/geoffreyCoulaud/pinryReborn/api/usecases/`,
`Q/` = `api/api-presentation-quarkus/src/main/kotlin/fr/geoffreyCoulaud/pinryReborn/api/presentation/quarkus/`,
`W/` = `clients/apps/webapp/src/`.
Reach tags: **[contract]** changes `contract/openapi.json`, **[archive]** changes the archive format, **[migration]**
needs a migration, **[client]** forces a web client change.

## 1. Violations per rule

### Types

**T1. Bare String/Int/Long for a concept** (table in section 2). Highlights:
- `D/entities/Pin.kt:9-10`, `D/entities/Person.kt:11`, `D/entities/RemoteCollection.kt:10`, `D/entities/MediaDownload.kt:10`, `D/media/MediaFetcher.kt:5`, `D/media/PageMediaExtractor.kt:8`, repository lookups (`D/repositories/RemoteCollectionRepositoryInterface.kt:12`, `PersonRepositoryInterface.kt:13`, `MediaDownloadRepositoryInterface.kt:12`): addresses as `String`. Fix: `HttpUrl`. Reach: domain, persistence mappers, presentation, usecases, import/export, api-fetch-http, api-fetch-ytdlp; [client] only for the new refusal code (PUT `/pins/{id}` accepts any string today).
- Duplicated http(s) checks to fold into `HttpUrl`: `U/RequestPinMediaDownload.kt:44-52` (also a parse that throws in a use case), `api/api-fetch-http/.../HttpMediaFetcher.kt:106-117` (`httpUri`), `U/imports/ImportFieldBounds.kt:49` (`addressFault`, blank/length only, no scheme), and `Q/controllers/PinController.kt:247` (`blankAsNone`).
- `D/entities/Media.kt:10,14,15`: `mimeType`, `contentHash`, `storageKey` as `String`. Fix: a media type (format enum + codecs), `Sha256`, `StorageKey`. Reach: domain, persistence mapper, usecases (ingestion, export, rendition), storage-filesystem.
- `D/tasks/Task.kt:9,16`, `NewTask.kt:6`, `ClaimedTask.kt:7,11`, `D/repositories/TaskQueueInterface.kt:30-59`: task `kind` (a closed set) and `leaseId` (a `randomUUID().toString()`, `P/repositories/EbeanTaskQueue.kt:126`) as `String`; `retryFloors: Map<String, Duration>`. Fix: `TaskKind` enum, `leaseId: UUID`. Reach: domain, persistence, usecases, worker.
- Names (`Tag.name`, `Board.name`, `User.name`, `Person.name`, `RemoteCollection.name`) as `String`: the bound (non-blank, 200) lives in REST annotations and again in `U/imports/ImportFieldBounds.kt:17`; PUT `/pins/{id}` tags (`Q/dtos/input/PinUpdateInputDto.kt:17`) carry no length bound at all, so a REST tag name escapes the import's 200. Fix: one name type per concept with the fold and the bound. Reach: domain, persistence, usecases, presentation, import.
- `D/entities/UserDataImportIssue.kt:15-16`: `subject`/`detail` "truncated before storage" by `U/imports/ImportIssueRecorder.kt:37` only. Fix: a bounded text type. Reach: domain, usecases.

**Ebean value-class rule:** no Konsist test exists (`api/api-application/src/test/.../ArchitectureKonsistTest.kt` has none; no value class exists yet anywhere). Fix: add the test before the first value class lands. Reach: one test file.

**T2. Storage or serialisation format carried by a type**
- `D/tasks/NewTask.kt:7`, `Task.kt:10`, `ClaimedTask.kt:8`, `U/tasks/TaskHandler.kt:12`: `payload: String`, a serialised UUID parsed by each handler (`api/api-worker-quarkus/.../*TaskHandler.kt`, `UUID.fromString(payload)`); `dedupKey` built as `"kind:pinId"` (`U/RequestPinMediaDownload.kt:38`). Fix: typed payload, mapper encodes. Reach: domain, persistence, usecases, worker.
- `U/MediaIngestion.kt:166`: `Media.mimeType` stores the header syntax `video/mp4; codecs="..."`; `P/mappers/MediaModelMapper.kt:32` picks the sealed variant by `mimeType.startsWith("video/")`; `U/exports/ExportMediaExtension.kt:10` re-parses the string into an extension, duplicating `MediaFormat`/`VideoContainer` (and maps `image/avif`, which is never stored). Fix: typed media type; mapper encodes. Reach: domain, persistence, usecases.
- `D/entities/MediaFrame.kt:5-6`: "as stored: the four words": the domain type mirrors the four `hash_n` columns. Fix: carry a PDQ bits type; mapper splits into columns. Reach: domain, persistence, usecases.
- `D/storage/StorageLayout.kt:7`, `U/MediaIngestion.kt:69`, `U/exports/ExportArchiveKey.kt:11`, `U/imports/ImportArchiveKey.kt:11`: the on-disk key layout lives in the domain and use cases. Fix: the store adapter owns the layout behind a typed key. Reach: domain, usecases, storage-filesystem.
- `U/exports/ExportContent.kt`, `U/imports/ImportedContent.kt`: the archive JSON shapes live in `api-usecases`. Judgement call: they are the archive's DTOs, so arguably the adapter's mapper. Reach: usecases, storage-filesystem.

**T3. Independently nullable fields instead of sealed variants**
- `D/entities/UserDataExport.kt:13-22`: `state` + eight optional fields; `U/exports/UserDataExportDownloader.kt:47-73` counts non-nulls and uses six `!!` to recover the READY variant. Fix: sealed per state. Reach: domain, persistence mapper, usecases (14 files reference the constructor).
- `D/entities/UserDataImport.kt:21-40`: `state` + `taskId`, `runToken`, `archiveCompletedAt`, `startedAt`, `completedAt`, `storageKey`, `byteSize`, `formatVersion`, `announcedPins`, `failureCode`. Fix: sealed per state. Reach: domain, persistence mapper, usecases (16 files).
- `D/entities/MediaDownload.kt:11-13`: `status` with `reasonCode?` (FAILED only) and `lastError?` (PENDING only). Fix: sealed Pending/Failed. Reach: domain, persistence, usecases (16 files).
- `U/PinMediaState.kt:15-21`: `status` + `media?`, `reasonCode?`, `replacement?`; `PinMediaReplacement(status, reasonCode?)`. Fix: sealed None/Pending/Ready(media, replacement?)/Failed(reason). Reach: usecases, presentation (22 files).
- `D/tasks/Task.kt:16-17`: `leaseId?` and `leaseExpiresAt?` are meaningful only together. Fix: one nullable `Lease`. Reach: domain, persistence.
- `D/imports/ArchiveLine.kt:9-10`: `value: T?` and `failure: String?`. Fix: sealed Parsed/Malformed. Reach: domain, storage-filesystem, usecases (7 files).
- Wire: `Q/dtos/output/PinMediaStateDto.kt:3-27` (12 optional fields under one `status`), `ReplacementDto`, `MediaDownloadOutputDto.kt:16-17`, `UserDataExportOutputDto.kt:14-19`, `UserDataImportOutputDto.kt:14-30`; `PinOutputDto.media` null duplicates status `NONE`. Fix: `oneOf` by status. Reach: presentation, [contract], [client] (about 15 files read `media.*`, 16 read export/import fields).
- `P/models/MediaModel.kt:32-35`: video-only and audio-only columns independently nullable, guarded at read by `checkNotNull` (`P/mappers/MediaModelMapper.kt:62-68`). Mapper conversion is allowed; see X1 for the missing CHECK.

**T4. Invariant not held by shape, or a parse that throws outside a mapper**
- `D/media/PdqHasher.kt:4` and `D/entities/MediaFrame.kt:6`: `words: List<Long>` must hold exactly four words; `quality` must be 0..100. Fix: fixed-shape type. Reach: domain, persistence, usecases.
- `D/entities/Media.kt` `AnimatedImage.frames` (> 1) and `Video.frames` (> 1, per `UndecodableVideoException`) are bare `Int`; the mapper picks `AnimatedImage` from the `animated` column whatever `frames` says. Fix: shape or factory. Reach: domain, persistence.
- `U/RequestPinMediaDownload.kt:44-52`: parses and throws in a use case. Fix: `HttpUrl` factory in the presentation mapper. Reach: usecases, presentation.
- Name/address bounds defined twice, by REST annotations and by `U/imports/ImportFieldBounds.kt` (fault functions). Fix: factories on the name/`HttpUrl` types, called by both mappers. Reach: presentation, import.
- Domain itself: no parse that throws found.

**T5. Normalisation outside the type**
- `U/UserCreator.kt:37`: `name.trim()` in the use case. Fix: in the `Username` factory. Reach: usecases, domain.
- `P/mappers/PersonModelMapper.kt:11`: sorting and dedup of urls (what defines person equality) happens in the mapper. Fix: `Set<HttpUrl>` in `Person`; the mapper only encodes. Reach: domain, persistence.
- `Q/controllers/PinController.kt:247`: `blankAsNone` normalises in the controller. Fix: `HttpUrl` factory (blank is no address). Reach: presentation.

**T6. Distinct elements as a List**
- `D/entities/Pin.kt:12-13,19` (`tags`, `boards`, `creators`), `D/entities/Person.kt:11` (`urls`); symptoms: `U/DuplicateResolver.kt:67-70` `.distinct()`, `P/repositories/PinRepository.kt:84,104,128` `distinctBy { it.id }`. Fix: `Set`. Reach: domain, persistence, usecases (37 files build a `Pin`).
- Port and use case parameters: `D/repositories/PinRepositoryInterface.kt:23,26,29,64,67` (`List<UUID>` ids), `D/repositories/PersonRepositoryInterface.kt:13`, `U/PinTagger.kt:10`, `U/PinBoardSetter.kt:48`, `U/PersonCreator.kt:13` (`PersonReference.urls`), `D/repositories/MediaFrameRepositoryInterface.kt:9` (callers `distinctBy { it.words }`). Fix: `Set`. Reach: domain, usecases, persistence.
- Wire (no `uniqueItems` anywhere in the contract): `Q/dtos/input/PinUpdateInputDto.kt:17,18,20`, `PersonInputDto.kt:9`, `PinIdsInputDto.kt:8`, `BoardIdsInputDto.kt:9`, `BoardCreationInputDto.kt:12`; outputs `PinOutputDto.kt:12-15`, `PersonOutputDto.kt:3`, `HandshakeOutputDto.kt:19` (`mediaTypes`). Fix: `Set` + `uniqueItems`. Reach: presentation, [contract]; client TS types unchanged.
- Archive: `U/exports/ExportContent.kt:73,88-92`, `U/imports/ImportedContent.kt:40,51-59`. Fix: `Set`. Reach: usecases, [archive] (JSON array either way).

**T7. A concept named differently across layers**
- Content digest: `Media.contentHash`, `StagedFile.contentHash` vs `sha256` in `UserDataExport` (`D/entities/UserDataExport.kt:19`), `ArchiveEntryDigest` (`D/exports/ExportArchiveStore.kt:11`), `ExportedMedia` (`U/exports/ExportContent.kt:68`), wire `UserDataExportOutputDto.sha256`. Reach: domain, persistence ([migration] for the column), [contract], [archive].
- MIME type: `Media.mimeType` vs `UserDataExport.mediaType` / `ArchiveFormat.mediaType` / wire `mediaType`. Reach: domain, [contract], [migration].
- Recycling instant: `softDeletedAt` (domain, wire `PinOutputDto.softDeletedAt`) vs `deletedAt` (archive, `U/exports/ExportContent.kt:49,87`). Reach: [archive] or [contract].
- Failure cause: domain `failureCode` (exports, imports) vs `reasonCode` (downloads, and every wire DTO). Reach: domain, persistence ([migration]).
- Size: `MeasuredMedia.bytes` (`D/media/MediaLimits.kt`) vs `byteSize` everywhere else. Reach: domain, imaging, video.
- `MediaFrame` names a frame hash (`D/entities/MediaFrame.kt:5`). Reach: domain, persistence.
- Columns: `when_created`/`when_modified` vs `createdAt`/`updatedAt` (`P/models/bases/AuthoredBaseModel.kt:18`, `BoardModel.kt:27`, `PinModel`, `UserModel`, `SessionTokenModel`, `UserPasswordHashModel`); tables `pin_tag_model`, `pin_board_model`, `pin_creator_model` carry the class name. Reach: persistence, [migration].
- `ImportedPin.collections` (`U/imports/ImportedContent.kt:59`) holds collection addresses. Reach: [archive].

### Identity

**I1.** Clean outside the archive (see A1).

**I2.** Clean.

**I3. Read-before-write, or a unique violation left untranslated**
- `U/exports/UserDataExportRequester.kt:58`: `findPendingForUser` answers uniqueness before the insert (the partial index already does, and `savePending` translates it); `D/repositories/UserDataExportRepositoryInterface.kt:23` exists for it. Fix: drop the read, order the refusals after the insert. Reach: usecases, domain port.
- `P/repositories/EbeanPinDuplicateRepository.kt:22-25`: reads held pairs, then inserts the rest. Fix: insert and let `ux_pin_duplicate_pins` refuse (insert-or-ignore). Reach: persistence.
- `P/repositories/EbeanTaskQueue.kt:51-52`: reads the live dedup task before an insert that already converges on the unique violation. Fix: drop the read. Reach: persistence.
- Saves that never translate their unique index: `P/repositories/TagRepository.kt:18`, `PersonRepository.kt:18`, `RemoteCollectionRepository.kt:18`. Safe today only because each caller reads first in a transaction. Fix: translate, or make the find-or-create converge on the violation. Reach: persistence, domain exceptions.
- Judgement call: the import identifies a pin by its media digest (`U/imports/UserDataImportRunner.kt` `digested`/`matched`, `MEDIA_AMBIGUOUS`) with no unique index behind it, by product decision (`P/models/MediaModel.kt:17-18`).

**I4.** Clean (`boards` and `users` indexes cover recycled and tombstoned rows; nothing else is recyclable).

**I5.** Clean (`pin_tag_model`, `pin_board_model`, `pin_creator_model`, `pin_duplicate`: unique pair plus an index on the other side).

**I6.** Clean (`P/repositories/EbeanPinDuplicateRepository.kt:80`); see X1 for the missing CHECK.

**I7. Find-or-create outside the write's transaction**
- `U/PinCreator.kt:24-26`: each tag is found-or-created in its own transaction, then the pin is saved outside any. Fix: one transaction. Reach: usecases. (The only caller passes `tags = emptyList()`, `Q/controllers/PinController.kt:95`, so the parameter is dead.)
- `U/imports/UserDataImportRunner.kt:385`: `findBoardForUserByName` then `saveBoard` with no transaction; a race surfaces as `LINE_REJECTED` instead of a skip. Fix: transaction (or converge on `BoardNameAlreadyTakenException`). Reach: usecases.

**I8. Domain equality differs from the DB identity**
- `D/entities/Person.kt:8-12` (known): equality is id + exact name + ordered urls; the index is `(author_id, name collate nocase, urls)` with sorted urls.
- Same pattern: `D/entities/Tag.kt:9` and `D/entities/Board.kt:9` (index `name collate nocase`), `D/entities/User.kt:8` (`ix_users_name_nocase`). Fix: name types whose equality folds ASCII case; entity equality by natural key. Reach: domain, usecases (`DuplicateResolver`, `pin.author != user` comparisons), tests.
- `P/repositories/UserRepository.kt:27`: `ieq` (Unicode fold) disagrees with `ix_users_name_nocase` (ASCII), and the contract says "whatever its case" (`contract/openapi.json:4429`). Fix: `collate nocase`, as the other repositories do. Reach: persistence, [contract] wording.

### Storage

**S1.** Clean. Borderline: `tasks.dedup_key` holds `"kind:id"` (`U/RequestPinMediaDownload.kt:38`), a delimited pair rather than a list.

**S2.** Clean.

**S3. Derived data stored**
- `user_data_imports.processed_pins` (`P/models/UserDataImportModel.kt:40`): always `created_pins + skipped_pins` (`U/imports/UserDataImportRunner.kt` `applied`). Fix: derive. Reach: domain, persistence [migration], presentation (wire field kept, computed).
- `user_data_exports.storage_key`, `media_type`, `file_extension` (`P/models/UserDataExportModel.kt:33,36,37`): derived from the id and the store's format (`U/exports/ExportArchiveKey.kt:11`); the key's nullness doubles as the "bytes still held" flag. Same for `user_data_imports.storage_key` (`P/models/UserDataImportModel.kt:36`, `U/imports/ImportArchiveKey.kt:11`). Fix: derive the key; store the flag explicitly. Reach: domain, persistence [migration], usecases (reapers, downloader, completer).
- `media.animated` (`P/models/MediaModel.kt:27`): derivable from the variant (video, or an image's frames > 1); it is the read-side discriminator and a `findComparable` filter. Fix: a `kind` column or derive. Reach: persistence [migration].
- Judgement call: `media.storage_key` is derivable from owner, pin, media id and format (`U/MediaIngestion.kt:69`).

**S4. Columns nothing reads**
- `media_download.last_error` (`P/models/MediaDownloadModel.kt:28`): written by `recordLastError`, never read (the DTO excludes it on purpose). Fix: drop, or name its reader. Reach: domain, persistence [migration], usecases.
- `tasks.last_error` (`P/models/TaskModel.kt:48`): written on retry, dead, reap; never read. Same fix and reach.

**S5. A save per element in a loop**
- `P/repositories/PinRepository.kt:86,107,131`: `persistor.save` per join row. Fix: `saveAll`. Reach: persistence.
- `P/repositories/EbeanMediaFrameRepository.kt:18-21`: one save per frame. Fix: `saveAll`.
- `P/repositories/EbeanPinDuplicateRepository.kt:23-26`: one save per pair. Fix: one batch.
- `U/AccountDeletionCleaner.kt:58-62`: per pin, a media read, a download clear and a media delete. Fix: bulk by pin ids. Reach: usecases, domain ports.
- `U/PinRecycleBin.kt:81-87` (`emptyRecycleBin`): same per-pin loop.
- `U/BoardRecycleBin.kt:46-48` (`restoreAll`): one read and one save per board.
- `U/DuplicateResolver.kt:44`: one `setRejected` statement per rejected pin.
- Find-or-create per name: `U/PinTagger.kt:10`, `U/PinUpdater.kt:45`, `U/PinCreator.kt:24`, `U/imports/UserDataImportRunner.kt:561-567` (tags, boards, collections and persons looked up one by one). Fix: one read for the names, one batch for the missing. Reach: usecases, domain ports, persistence.

### Nullability

**N1. Annotation restating nullability**
- `@Valid @NotNull` on non-null Kotlin body parameters, 13 sites (`Q/controllers/BoardController.kt:94,176,255,271`, `BoardRecycleBinController.kt:86`, `MeController.kt:108`, `PinRecycleBinController.kt:91`, `SessionController.kt:79`, `UserController.kt:48`, `PinController.kt:87,158,228`, `PinDuplicateController.kt:123`, `MediaController.kt:172,370`). `ArchitectureKonsistTest.kt:79` mandates them, because RESTEasy does not read Kotlin nullability. Fix: a Kotlin-aware refusal of a missing body, then drop the annotations and invert the test. Reach: presentation, one test. Flagged for decision.

**N2. Null as a third state, or an entity built with something it did not load**
- `D/repositories/PinRepositoryInterface.kt:45,100`, `U/PinGetter.kt:31`, `U/BoardPinLister.kt:26`, `P/queries/PinQueries.kt:20`: `query: String? = null` means "no filter". Fix: a search-term shape. Reach: domain, usecases, persistence, presentation.
- `D/entities/Pin.kt:17`: `media: Media? = null` is never set by `P/mappers/PinModelMapper.kt` and never read. Fix: delete it. Reach: domain.
- `D/entities/Pin.kt:16-19`: defaults (`publisher = null`, `creators = emptyList()`, `media = null`) let a `Pin` be built without loading them. Fix: no defaults. Reach: domain and the 37 files that build a `Pin`.
- `U/imports/UserDataImportRunner.kt:697-698`: a `Pin` built with placeholder `tags`/`boards = emptyList()`, filled later by `copy`. Fix: build once resolved. Reach: usecases.
- `Q/dtos/output/PinOutputDto.kt:20`: `media = null` and `status = NONE` both mean "no media" (`U/ResolvePinMediaState.kt:32` filters NONE out). Fix: one shape (see T3). Reach: presentation, [contract], [client].

**N3. Optionality depending on the entry path**
- REST maps a blank `sourceContextUrl`/`sourceMediaUrl` to none (`Q/controllers/PinController.kt:92-93,234-235`); the import refuses a blank `sourceContextUrl` as `FIELD_INVALID` and does not check `sourceMediaUrl` at all (`U/imports/UserDataImportRunner.kt:592`). Fix: one `HttpUrl` factory for both paths. Reach: presentation, import.

### Contract

**C1.** Near clean. Minor storage wording: "creates rows" (`/api/v1/me/imports` POST and `/archive/complete`), "`imports.report_detail_limit` rows" (issues GET), "the worker owns its row" (media-downloads DELETE 409). Fix: wording. Reach: presentation annotations, [contract].

**C2.** Clean (tags by name, persons by `{name, urls}`; boards are entities addressed by id).

**C3. The client rebuilds an address from an id**
- `W/exports.ts:26-27`: `downloadHref` builds `/api/v1/me/exports/${row.id}/download`. Fix: `downloadUrl` on the READY export. Reach: presentation, [contract], [client] (1 file plus tests).

**C4. Wire fields the web client never reads** (candidates; the extension is not built yet):
- `Q/dtos/output/PinOutputDto.kt:8` `authorId`; `UserDataExportOutputDto.kt:17-20` `mediaType`, `sha256`, `formatVersion`; `UserDataImportOutputDto.kt:16,19` `archiveCompletedAt`, `formatVersion`; `MediaDownloadOutputDto.kt:15` `updatedAt`. Fix: drop or name the consumer. Reach: presentation, [contract], [client] test fixtures only.

**C5. Nullable output property not required**
- `PinMediaStateDto`: `url`, `mimeType`, `width`, `height`, `byteSize`, `durationMillis`, `videoBitRate`, `audioChannels`, `audioBitRate`, `reasonCode`, `message`, `replacement` (Kotlin defaults `= null`, `Q/dtos/output/PinMediaStateDto.kt:5-18`).
- `ReplacementDto`: `reasonCode`, `message` (`PinMediaStateDto.kt:22-23`).
- `PinOutputDto`: `softDeletedAt`, `media` (`Q/dtos/output/PinOutputDto.kt:18,20`).
- `ProblemDetail.currentLength` (`Q/dtos/output/ProblemDetail.kt:17`): omitted on purpose through `JsonInclude.NON_NULL`. Fix: a dedicated problem schema for that refusal with `currentLength` required.
- Fix for the rest: drop the defaults so SmallRye marks them required. Reach: presentation, [contract], [client] (generated types turn optional into `T | null`; mostly compatible).
- PUT inputs: every property required. Clean. (Related, outside C5: `ProblemDetail.type` is non-nullable and not `required`.)

### Archive

**A1. Ids in the archive**
- The importer discards every id (`U/imports/ImportedContent.kt`). But the export still writes references as `{id, name}` (`U/exports/ExportContent.kt:27`, `U/exports/UserDataExportBuilder.kt:305,308`) and ids on every record (`ExportedTag`, `ExportedBoard`, `ExportedPin`, `ExportedMedia`, `ExportedUser`, manifest `user`). Fix: references by natural key (`name`); drop ids nothing reads. Reach: usecases, [archive], golden test.

**A2. Entity without its own section**
- Known: persons travel only inside pin lines (`U/exports/ExportContent.kt:73`, `U/exports/UserDataExportBuilder.kt:316`); a person no pin names is lost. Fix: `persons.jsonl` (`{name, urls, createdAt}`), pin lines keep `{name, urls}` as the reference, the importer walks persons before pins, `ExportCounts` and `ExportReadme` gain persons. Reach: usecases (builder, runner, content types, readme), a domain port (`findAllPersonsForUser`), persistence, [archive].

**A3. Two shapes for one concept**
- A board reference is `{id, name}` in a pin line (`UserDataExportBuilder.kt:308`) and a bare name in a collection line (`ExportedCollection.board`, `U/exports/ExportContent.kt:53`). Fix: one shape (name). Reach: [archive].
- A tag is `{name}` on the wire (`TagOutputDto`) and `{id, name}` in archive references. Fix: as A1. Reach: [archive].
- A pin's board membership arrives two ways on import: `boards` names and `collections` addresses (`U/imports/ImportedContent.kt:59`), the second never written by our export. Judgement call (ADR 0055 third-party importer). Reach: [archive].
- Media: the wire state (`PinMediaStateDto`, with video fields) and `ExportedMedia` (`animated`, `sha256`, no video fields) diverge in names and fields. Reach: [archive] or [contract].

**A4.** Clean: `publishedAt` restored as written, server stamps clamped per ADR 0015, persons and collections stamped at import. `persons.jsonl` must follow the same rule for `createdAt`.

### Cross-cutting

**X1. Invariant held only by a comment**
- `P/models/PinDuplicateModel.kt:13`: "[firstPinId] the lower id": no CHECK. Fix: `check (first_pin_id < second_pin_id)` or a pair type. Reach: persistence [migration].
- `D/media/RenditionSpec.kt:9-10`: callers "must" intersect `animated` with the source's. Fix: a factory from the media. Reach: domain, usecases.
- `D/repositories/PersonRepositoryInterface.kt:25`: "Called after the user's pins are deleted". Ordering by comment (foreign keys off).
- `D/entities/UserDataImportIssue.kt:7-8`: truncation (see T1).
- `D/entities/UserDataImport.kt:9`: "counters which increment and are never assigned": the data class allows any value.
- `U/exports/ExportMediaExtension.kt:6`: "the MIME type comes from a server-side enum" while the type is `String`.
- `P/mappers/MediaModelMapper.kt:60`: "a video row missing one is a defect": no CHECK on the video and audio columns.
- `P/models/RemoteCollectionModel.kt:13`: "the server normalises no address" turns false once `HttpUrl` lands.

**X2. Deletion paths missing a dependent table** (foreign keys off; only the periodic sweeps `deleteOrphans` collect these):
- `pin_duplicate` (both sides) and `media_frame` not deleted by `U/PinRecycleBin.kt:69` (`permanentlyDelete`), `:81` (`emptyRecycleBin`), or `U/AccountDeletionCleaner.kt:49-92`.
- `media_frame` not deleted by `U/DeletePinMedia.kt:29`, nor for the superseded media when `P/repositories/EbeanMediaRepository.kt:24` replaces a pin's media (`U/SetPinMedia.kt:86`, `U/DownloadPinMedia.kt:129`).
- Fix: delete frames with their media, and pairs with their pin, on each path; keep the sweeps. Reach: usecases, domain ports, persistence.

## 2. T1 table: bare String/Int/Long in `api-domain` naming a concept

Free text (descriptions, error messages), counters, and stdlib-typed fields (`UUID`, `Instant`, `Duration`) are left out.

| Property / parameter | Where | Proposed type (invariant) | Files referencing |
|---|---|---|---|
| `Pin.sourceContextUrl`, `sourceMediaUrl` | `entities/Pin.kt:9-10` | `HttpUrl?` | ~15 main, ~44 test |
| `Person.urls` | `entities/Person.kt:11` | `Set<HttpUrl>` | ~15 main, ~19 test |
| `RemoteCollection.url`, `findUserRemoteCollectionByUrl(url)` | `entities/RemoteCollection.kt:10` | `HttpUrl` | ~12 |
| `MediaDownload.sourceUrl`, `upsertPending(sourceUrl)`, `MediaFetcher.openStream(sourceUrl)` | `entities/MediaDownload.kt:10` | `HttpUrl` | ~13 main, ~10 test |
| `PageMediaExtractor.extract(pageUrl)` | `media/PageMediaExtractor.kt:8` | `HttpUrl` | ~3 |
| `Tag.name`, `findUserTagByName(name)` | `entities/Tag.kt:9` | `TagName` (non-blank, max 200, ASCII-fold equality, spelling kept) | ~14 + lookups |
| `Board.name`, `findBoardForUserByName(name)` | `entities/Board.kt:9` | `BoardName` (same) | ~21 |
| `User.name`, `findUserByName`, `BasicAuthLogin.userName` | `entities/User.kt:8`, `entities/Login.kt:12` | `Username` (pattern `[A-Za-z0-9._-]`, 3 to 50, trimmed, ASCII-fold equality) | ~85 (mostly tests) |
| `Person.name` | `entities/Person.kt:10` | `PersonName` (as `TagName`) | ~15 |
| `RemoteCollection.name` | `entities/RemoteCollection.kt:11` | name type (non-blank, max 200) | ~12 |
| `Media.mimeType`, `ArchiveFormat.mediaType`, `UserDataExport.mediaType`, `FetchedMedia.contentType` | `entities/Media.kt:10`, `exports/ExportArchiveStore.kt:8` | media type: `MediaFormat`, or `VideoContainer` + codecs | ~16 + ~25 main |
| `VideoProbeResult.codecs` | `media/VideoProcessor.kt:33` | RFC 6381 codecs value class | ~6 |
| `Media.contentHash`, `StagedFile.contentHash`, `ArchiveEntryDigest.sha256`, `UserDataExport.sha256`, `MediaStore.digest(): String`, `findPinIdsByContentHashForUser(contentHash)` | `entities/Media.kt:14`, `storage/StagedFile.kt:9` | `Sha256` (64 lowercase hex) | ~11 + ~18 main |
| `Media.storageKey`, `UserDataExport.storageKey`, `UserDataImport.storageKey`, every store's `storageKey` parameter | `entities/Media.kt:15` | `StorageKey` (relative, no traversal) | ~36 main, ~47 test |
| `StagedFile.path` | `storage/StagedFile.kt:9` | `java.nio.file.Path` | ~10 |
| `ArchiveEntryDigest.path`, `ArchiveSink` entry `name`s | `exports/ExportArchiveStore.kt:11` | archive entry path value class | ~5 |
| `UserDataExport.fileExtension`, `ArchiveFormat.fileExtension` | `entities/UserDataExport.kt:21` | derived (S3); otherwise part of the media type | ~13 |
| `Media.byteSize`, `StagedFile.byteSize`, `UserDataExport/Import.byteSize`, `uploadedBytes`, `MediaLimits.max*Bytes`, `decoderMemory`, `MeasuredMedia.bytes` | various | byte count value class (>= 0) | ~29 main, ~37 test |
| `Media.width`, `height`, `RenditionSpec.*`, `MediaLimits.maxPixelsPerFrame/Render` | `entities/Media.kt` | pixel dimensions value class (> 0) | ~20 |
| `Media.frames` | `entities/Media.kt` | per variant (see T4) | ~10 |
| `Video.videoBitRate`, `Sound.bitRate`, `Sound.channels` | `entities/Media.kt:53,57` | bit-rate value class; channels > 0 | ~7 |
| `MediaFrame.words`, `PdqHash.words`, `PdqHash.quality` | `entities/MediaFrame.kt:6`, `media/PdqHasher.kt:4` | 256-bit PDQ value (4 words); quality 0..100 | ~5 |
| `Task.kind`, `NewTask.kind`, `ClaimedTask.kind`, `TaskHandler.kind`, `retryFloors` keys | `tasks/Task.kt:9` | `TaskKind` enum | ~29 |
| `Task.payload`, `NewTask.payload`, `ClaimedTask.payload` | `tasks/Task.kt:10` | typed payload (T2) | ~10 |
| `Task.leaseId`, `ClaimedTask.leaseId`, queue `leaseId` parameters | `tasks/Task.kt:16` | `UUID` | ~7 main, ~6 test |
| `Task.dedupKey`, `NewTask.dedupKey` | `tasks/NewTask.kt:11` | typed key (kind + subject id) | ~10 |
| `IssuedSession.token`, `TokenGenerator.generateToken()` | `entities/IssuedSession.kt:6` | secret token value class (redacted `toString`) | ~8 |
| `tokenHash` parameters | `repositories/SessionTokenRepositoryInterface.kt:9,12` | `Sha256` or token-hash value class | ~5 |
| `HashedPassword.hash` | `entities/HashedPassword.kt:7` | password-hash value class (opaque) | ~6 |
| `BasicAuthLogin.password`, `PasswordHasher.hash(raw)` | `entities/Login.kt:13` | password value class (redacted `toString`) | ~8 |
| `UserDataExport.formatVersion`, `UserDataImport.formatVersion` | `entities/UserDataExport.kt:12` | archive format version value class (positive) | ~15 main, ~21 test |
| `UserDataImportIssue.line` | `entities/UserDataImportIssue.kt` | line number (> 0) | ~6 |
| `UserDataImportIssue.subject`, `detail` | `entities/UserDataImportIssue.kt:15-16` | bounded text (200) | ~6 |
| `RenditionCache` `key` | `media/RenditionCache.kt:11-25` | rendition key value class | ~5 |
| `MediaRepositoryInterface` fingerprint `version: Int` | `repositories/MediaRepositoryInterface.kt` | fingerprint version value class | ~4 |

## 3. Clean rules

I1 (outside the archive), I2, I4, I5, I6, S1 (dedup key borderline), S2, C2, A4. Domain-side T4 (no parse that throws inside `api-domain`).

## 4. Proposed units (rough sizes; tests included)

Counts are estimates from `grep -l`; test files dominate. Several units cannot be split by layer without breaking compilation, so a unit over 20 files is cut by concept (one entity or field at a time).

| Unit | Rules | Size | Reach |
|---|---|---|---|
| 0. Konsist guard: no value class in a persistence model | Ebean | ~40 lines, 1 file | test only; must land first |
| 1. `HttpUrl` type + tests | T1, T4, T5 | ~200 lines, 2 files | domain |
| 2. `HttpUrl` on `Pin` sources (REST, import, export, mapper, `blankAsNone` gone) | T1, N3, T5 | ~400 lines, ~25 files: split main+fixtures / remaining tests | [client] refusal code |
| 3. `HttpUrl` on downloads and fetchers (`MediaDownload`, `MediaFetcher`, `PageMediaExtractor`, `RequestPinMediaDownload`, `HttpMediaFetcher.httpUri`) | T1, T4 | ~300 lines, ~18 files | fetch-http, fetch-ytdlp |
| 4. `HttpUrl` on `RemoteCollection` | T1, X1 | ~150 lines, ~12 files | |
| 5. Person identity: `Set<HttpUrl>`, `PersonName` fold equality, mapper only encodes | I8, T5, T6 | ~300 lines, ~15 files | [contract] uniqueItems |
| 6. `persons.jsonl` | A2, A4 | ~350 lines, ~10 files | [archive] |
| 7. Archive references by natural key, ids dropped, one board-reference shape | A1, A3, T7 (deletedAt) | ~200 lines, ~6 files | [archive] |
| 8a-d. Name types `TagName`, `BoardName`, `PersonName` (if not in 5), `Username` | T1, T4, T5, I8 | 8a-c ~250 lines, ~15-20 files each; 8d ~85 files, needs a split strategy | `Username` fixes `ieq` |
| 9. `Set` for pin tags, boards and creators, plus batch ids (domain, ports) | T6 | ~400 lines, ~37 files: split Pin fields / batch ids | |
| 10. `uniqueItems` on the wire | T6 | ~60 lines, ~8 files | [contract] |
| 11. Nullability on the wire (defaults dropped, `currentLength` schema) | C5 | ~60 lines, ~5 files | [contract], [client] types |
| 12a-d. Sealed domain states: `UserDataExport`, `UserDataImport`, `MediaDownload` (with S4 `last_error`), `PinMediaState` | T3 | ~300-500 lines, 14-22 files each | 12a with S3 export columns [migration] |
| 13a-c. Sealed wire states: `PinMediaStateDto`, `MediaDownloadOutputDto`, export/import DTOs (+ `downloadUrl`) | T3, N2, C3 | ~300-500 lines each incl. client | [contract], [client]; SmallRye `oneOf` support is a risk |
| 14. Batch writes | S5 | ~300 lines, ~12 files | |
| 15. Dependents on every deletion path | X2 | ~150 lines, ~8 files | pairs well with 14 |
| 16. Derived and unread columns (`processed_pins`, archive keys and flags, `media.animated`, `tasks.last_error`) | S3, S4 | ~300 lines, ~15 files, 2 migration pairs (drop is two-step) | [migration] |
| 17. Task typing (`TaskKind`, `leaseId: UUID`, `Lease`, typed payload and dedup key) | T1, T2, T3 | ~350 lines, ~25 files: split kind/lease vs payload | worker |
| 18. Media typing (`MediaType`, `Sha256`, PDQ value, frame-count shape, `ExportMediaExtension` gone) | T1, T2, T4 | ~400 lines, ~25 files: split by type | |
| 19. `StorageKey` and layout moved to the store adapters | T1, T2 | ~500 lines, ~40 files: split media / exports / imports | storage-filesystem |
| 20. Renames across layers (`sha256`, `mediaType`, `failureCode`, `bytes`, `MediaFrame`, `when_*` columns, `*_model` tables) | T7 | ~300 lines, ~20 files: split wire / archive / DB | [contract], [archive], [migration] |
| 21. Uniqueness and transactions (export pre-read, duplicate insert-or-ignore, dedup pre-read, untranslated saves, `PinCreator`, import board) | I3, I7 | ~150 lines, ~8 files | |
| 22. Small nullability items (`query` shape, `Pin.media` and defaults removed, import placeholders) | N2 | ~200 lines, ~40 files (Pin defaults) | |
| 23. Missing-body handling without `@NotNull` | N1 | ~80 lines, ~15 files | needs a decision |
| 24. CHECK constraints and factories for comment-held invariants (pair order, video columns, `RenditionSpec`) | X1 | ~120 lines, ~6 files | [migration] |
| 25. Contract wording and fields no client reads | C1, C4 | ~60 lines, ~6 files | [contract], client fixtures |

Suggested order: 0, 1, then 2-4 (HttpUrl), 5, 6-7 (archive), 9-11, 12-13, then the rest independently.
