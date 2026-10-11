# The database enforces its references

Date: 2026-10-11
Status: Accepted by the operator on 2026-10-11. One specification review ran,
`.reviews/the-database-enforces-its-references-spec.md`, its 1 CRITICAL, 5 MAJOR and 13 MINOR closed in this
document and ADR 0058. Frozen when the lot's last block merges.
Lot: `0.55.0`.
Branches: one stack, each block on the one before it: 10 `chore/the-history-is-one-baseline`, 20
`refactor/a-table-is-named-after-its-model`, 30 `feat/the-database-holds-its-references`, 40
`refactor/the-database-deletes-dependents`, 50 `refactor/a-derived-column-is-computed`, 60
`refactor/an-archive-key-is-derived`, 70 `feat/the-database-checks-its-invariants`, 80
`chore/the-references-lot-closes`. (Corrected: seventeen code blocks ran, #396 to #413: 10
`chore/the-history-is-one-baseline`, 20 `refactor/a-table-is-named-after-its-model`, 25
`refactor/a-timestamp-is-created-at`, 30 `feat/the-database-holds-its-references`, 35
`feat/the-database-enforces-its-references`, 40 `refactor/the-database-deletes-dependents`, 43
`refactor/the-account-rows-go-with-the-user`, 46 `refactor/the-orphan-sweeps-go`, 50
`refactor/a-derived-column-is-computed`, 53 `refactor/an-unread-download-error-goes`, 54
`refactor/a-download-keeps-no-error`, 56 `refactor/an-unread-task-error-goes`, 60 `refactor/an-archive-key-is-derived`,
62 `refactor/an-export-holds-its-archive`, 64 `refactor/an-import-holds-its-archive`, 66
`refactor/an-export-format-is-the-stores`, 70 `feat/the-database-checks-its-invariants`, then the closing block 80
`chore/the-references-lot-closes`.)
ADR: `docs/adr/0058-the-database-enforces-its-references.md` records decisions B to G, and amends ADR 0056,
decision 7.2.

## 1. Goal

SQLite enforces every foreign key, and deletes what depends on a deleted row; the code no longer does it by hand. The
migration history is one baseline generated from the models, whose tables and columns carry the names the models
give them, and which holds the invariants comments held. This is ADR 0056's decision 7.2, and the items of its
`inventory.md` that need a migration: S3, S4, X1 and X2, and unit 20's database renames.

## 2. What exists today

Paths are under `api/`. `dom`, `uc`, `per` and `pres` stand for the main sources of `api-domain`, `api-usecases`,
`api-persistence-sqlite` and `api-presentation-quarkus`; `mig` for `per`'s resources `dbmigration/`. Read at
`cf6b15fc`.

- **The history** is 35 `.sql` files and 36 model files, 1034 lines (`ls mig/*.sql | wc -l`, `ls mig/model | wc -l`,
  `cat mig/*.sql mig/model/* | wc -l`). The only data statements copy rows into rebuilt tables or fill a new column
  (`1.4`, `1.22` to `1.24`, `1.26`), or deduplicate join rows (`1.27`)
  (`grep -n -i -E 'insert into|^update |delete from' mig/*.sql`).
- **Ebean runs every migration in one transaction** on the pool's connection (ebean-migration 14.3.0,
  `MigrationEngine.run`: `setAutoCommit(false)`, then one `commit`), and `pragma foreign_keys` is a no-op inside a
  transaction. With the keys on, the rebuild the history used six times (create a copy, drop the table, rename the
  copy) deletes the dropped table's children through their cascade: reproduced by the specification review on
  sqlite-jdbc 3.53.2.0, a `pin_tags` row gone. On SQLite, Ebean writes any key change as `-- not supported`
  (`SQLiteDdl.alterTableAddForeignKey`), so a rebuild is the only path to one.
- **`foreign_keys` is off** in the three SQLite addresses: production and `api-application`'s tests
  (`application.properties:9` of each) and `api-persistence-sqlite`'s tests (`application-test.properties:16`).
  sqlite-jdbc 3.53.2.0 accepts `foreign_keys` as an address parameter (`SQLiteConfig$Pragma.FOREIGN_KEYS`, read with
  `javap`).
- **Ebean declares every key `on delete restrict`**, its default (`PlatformDdl.withDefault`, ebean-ddl-generator
  19.2.0's sources), and renders `CASCADE` as `cascade` and `SET_NULL` as `set null` (`PlatformDdl.translate`). Its
  `ConstraintMode` has no `NO_ACTION` (`javap io.ebean.annotation.ConstraintMode`, ebean-annotation 8.5).
- **Three references carry no key at all**: `media_frame.media_id`, `pin_duplicate.first_pin_id` and
  `second_pin_id`, declared `@DbForeignKey(noConstraint = true)` so that a deletion never waits on them
  (`per/models/MediaFrameModel.kt:93`, `PinDuplicateModel.kt:32,37`).
- **Four keys lead no index a cascade can use**: `pins.author_id`, `pins.publisher_id` and `session_tokens.user_id`
  none, `user_data_exports.user_id` only the partial `uq_user_data_exports_pending` (the review's query of
  `pragma_foreign_key_list` against the first column of `pragma_index_list`, over the migrated history). On SQLite
  Ebean writes keys inline and creates no index for them (`BaseTableDdl.writeForeignKey` runs only when
  `!isInlineForeignKeys()`), so `noIndex` changes nothing there.
- **The code deletes dependents by hand.** `AccountDeletionCleaner.deleteAccountData` (`uc/AccountDeletionCleaner.kt:49-92`)
  deletes each table's rows in key order, then the user. `PinRepository.permanentlyDelete*` and
  `BoardRepository.permanentlyDelete*` delete the join rows and collections before the row (`PinRepository.kt:297-320`,
  `BoardRepository.kt:85-105`). `PersonRepositoryInterface.deleteAllPersonsForUser` is "called after the user's pins
  are deleted" (`dom/repositories/PersonRepositoryInterface.kt:34`).
- **Two sweeps collect orphans**: `MediaFrameRepositoryInterface.deleteOrphans` and
  `PinDuplicateRepositoryInterface.deleteOrphans`, called by `ReapFingerprints` (`uc/ReapFingerprints.kt:17-20`),
  which also enqueues the fingerprint task. The pairs' sweep also deletes a pending pair whose pin lost its media,
  which `DeletePinMedia` leaves when it stops between its two deletions (`uc/DeletePinMedia.kt:29,31`, no transaction).
- **Three writes can name a parent already gone**: `FingerprintMedia` finds duplicates and hashes outside a
  transaction, then saves frames and pairs (`uc/FingerprintMedia.kt:36-43`); `SetPinMedia` saves a media for a pin an
  upload outlived (`:86`); `RequestPinMediaDownload` reads the pin outside its transaction and upserts the download
  inside it (`uc/RequestPinMediaDownload.kt:26,37`). Today they leave orphans; with the pragma on they throw.
  `FingerprintMedia` can also pair a pin whose media `DeletePinMedia` removed meanwhile, after that deletion cleared
  the pin's pending pairs; the pairs' sweep collects that pair today.
- **`ClearPinDownload.clear` cancels the download's task** besides deleting its row (`uc/ClearPinDownload.kt:22-27`);
  `tasks` holds no key to `pins`.
- **Names.** Three join tables are named after their class (`pin_tag_model`, `pin_board_model`,
  `pin_creator_model`); `media_download`, `media_frame` and `pin_duplicate` are singular, the other tables plural.
  `AuthoredBaseModel`, `UserModel`, `SessionTokenModel` and `UserPasswordHashModel` map `createdAt` to `when_created`,
  and `BoardModel` and `PinModel` map `updatedAt` to `when_modified`. 16 of the 19 entities carry `@Table`, the three
  join models lacking it (`git grep -l '@Entity' -- '*/models/*.kt'`, `git grep -L '@Table(' -- '*/models/*.kt'`). Index names repeat their table's (`ix_pin_tag_model_tag`).
- **Columns that repeat another value**: `user_data_imports.processed_pins`, always `created_pins + skipped_pins`
  (`uc/imports/UserDataImportRunner.kt:559-571`) and read as the resume cursor (`:500`); `media.animated`, which the
  domain already computes (`dom/entities/Media.kt:18-19`) and the mapper reads to choose the variant
  (`per/mappers/MediaModelMapper.kt:31-58`); `user_data_exports.storage_key`, `media_type`, `file_extension` and
  `user_data_imports.storage_key`, derived from the row's id and the store's single format
  (`uc/exports/ExportArchiveKey.kt:11`, `uc/imports/ImportArchiveKey.kt:11`). A non-null `storage_key` also means "bytes
  may remain to delete": both reapers select on it (`UserDataExportRepository.kt:130-138`,
  `UserDataImportRepository.kt:88-96`), and the import runner fails a row whose key is null (`UserDataImportRunner.kt:151-160`).
- **Columns nothing reads**: `media_download.last_error` and `tasks.last_error` (`git grep -n '\.lastError' -- '*/src/main/*'`
  finds writes alone).
- **Invariants held by a comment**: a duplicate pair's lower id first (`PinDuplicateModel.kt:13`, ordered by
  `EbeanPinDuplicateRepository.kt:80` on the identifiers' text); a video's columns all present and an image's absent
  (`MediaModelMapper.kt:60-68`); a task's `terminal_state_at` written on every terminal state (`TaskModel.kt:14-15`);
  a `READY` export's size, digest and completion instant present (`uc/exports/UserDataExportDownloader.kt:18-21`). The
  one `CHECK` in the schema is the one Ebean generates for an enumerated column (`ck_user_password_hashes_algorithm`).
- **Two migration tests read the history itself**: `JoinTableUniquenessMigrationTest` runs `1.27.sql`, and
  `TableRebuildColumnsTest` reads the rebuilds.
- **No test reads production's SQLite address**: `api-application`'s test `application.properties` overrides
  `datasource.db.url` per property, and `dagger call smoke` waits for `/q/health` alone.
- **The web application reads no export's `mediaType`**: `git grep -n 'mediaType' -- clients/apps clients/packages`
  finds two test fixtures setting it to `null`, and nothing else on exports.
- The contract is `25.0.0`. The last migration is `1.35`.

## 3. Decisions

The operator's answers in the Discuss of 2026-10-10 and 2026-10-11, the letter of the question in brackets, unless a
decision says it is the lead's.

**A. The lot takes every schema item of the inventory** (C): the database renames of unit 20, the derived and unread
columns of S3 and S4, X1's `CHECK` constraints and X2's deletion paths. The renames reaching the domain, the contract
or the archive (`sha256`, `mediaType`, `failureCode`) stay in the types lot.

**B. The history is one baseline** (ADR 0056, decision 7.2).
- Block 10 deletes every file under `mig/` and runs `generateDbMigration`, which writes `1.0__initial.sql` and its
  model file from the models as they stand. Each later block of the lot regenerates it the same way, until block 70
  adds the hand-written `CHECK` constraints; the history is append-only from the lot's merge.
- A development database is reset: Ebean refuses its recorded `1.0` checksum at boot. Nothing is deployed.
- `JoinTableUniquenessMigrationTest` and `TableRebuildColumnsTest` go with what they read; the other migration tests
  read whatever files the directory holds.
- **A migration that drops a table some key references is refused** (the lead's, after the review's critical
  finding): with the keys on, the drop cascades to its children inside Ebean's transaction. A migration test reads
  every script after the baseline and fails on a `drop table` of a referenced table, naming ADR 0058. The operator
  accepts the limit and no path is built: the database moves to PostgreSQL in the next lot, before
  ADR 0055's importer.

**C. Each key declares what a deletion does** (A, B, F, and the operator's rule that the model says it readably).
- `CASCADE` on every key but one: a user's rows, a pin's media, download, join rows and pairs, a media's frames, a
  board's join rows and collections, a tag's and a person's join rows, an import's issues.
- `SET_NULL` on `pins.publisher_id` (F): deleting a person removes their credits. `RESTRICT` there made deleting a
  user fail or pass depending on the order the cascade reached `persons` and `pins` (ADR 0058, Rejected).
- Each `@ManyToOne` declares `@DbForeignKey(onDelete = ...)`; a Konsist test refuses one that does not, and one that
  declares `noConstraint`, over a fixture model under `src/test/resources/konsist/` and over production.
  `noConstraint` goes from the three references that carry it.
- Every key's column leads a non-partial index, or a cascade scans its table. Ebean creates none on SQLite, so each is
  an `@Index`: new ones on `pins.author_id`, `pins.publisher_id`, `session_tokens.user_id` and
  `user_data_exports.user_id`, the others existing.
- The pragma is on in the three addresses, `foreign_keys=true`, every connection carrying it. A test reads
  production's `api-application/src/main/resources/application.properties` as a file, the test configuration
  overriding it, and requires `foreign_keys=true` in its `datasource.db.url`.
- A write whose parent may have gone re-reads it inside its own transaction and writes nothing if it is gone (the
  lead's). `FingerprintMedia` re-reads its media, and each duplicate pin with its media; `SetPinMedia` and
  `RequestPinMediaDownload` re-read the pin and answer as for a pin not found. (Corrected: `RequestPinMediaDownload`
  reads the pin once, inside its transaction, leaving no window to re-read across; `FingerprintMedia` re-reads the
  ids of its media and of the duplicates' media through `findMissingMediaIds`, a deleted pin taking its media by
  cascade. Blocks 30 and 35.)

**D. The database deletes dependents** (B).
- The code deletes the row it targets. The repository methods that delete dependents alone go:
  `deleteAllPersonsForUser`, `deleteAllTagsForUser`, `permanentlyDeleteAllPinsForUser`,
  `permanentlyDeleteAllBoardsForUser`, `UserPasswordHashRepositoryInterface.deleteForUser`, and the `deleteAllForUser`
  of exports, imports and import issues. The join rows' and collections' deletions inside `PinRepository` and
  `BoardRepository` go.
- `AccountDeletionCleaner` deletes the user row, and still reads the media keys and archive ids it reclaims on disk.
- `ClearPinDownload.clear` stays on the pin deletion paths for the task it cancels.
- Both `deleteOrphans` sweeps go. `DeletePinMedia` runs in one transaction, so no crash leaves a pending pair behind a
  media, and decision C's re-read keeps `FingerprintMedia` from pairing a pin that lost its media;
  `ReapFingerprints` keeps the enqueue (ADR 0051, decision 7).

**E. A table is named after its model** (D, E).
- Every entity carries `@Table`, its name the class name without `Model`, in snake case, plural: `pin_tags`,
  `pin_boards`, `pin_creators`, `media_downloads`, `media_frames`, `pin_duplicates`. `media` stays, the word being
  its own plural, and is the test's first exception.
- A Konsist test computes the expected name from the class (`s`, or `es` after `s`, `sh`, `ch` or `x`) and compares
  it with `@Table`'s; an exception list in the test names a model whose table keeps another name, each entry with its
  reason. `api/AGENTS.md` says when to add one: a model renamed without paying the migration.
- `when_created` and `when_modified` become `created_at` and `updated_at`.
- An index or constraint name is `ix_`, `ux_`, `uq_` or `ck_` followed by its table's name and `_`, or
  `pk_<table>`; SQLite's keys carry no name. A Konsist test reads every `@Index` name against its model's `@Table`;
  `MigratedSchemaIndexesTest`'s rule over the media tables extends to every table, for the names Ebean generates.
  (Corrected: its prefix narrows from `[a-z]+_` to these four, block 20; the test is `MigratedSchemaTest` since
  block 80.)

**F. A derived column is computed, an unread one deleted** (C).
- `processed_pins` goes; `UserDataImport.processedPins` becomes `createdPins + skippedPins`, computed, and stays on
  the wire.
- `media.animated` goes; the mapper reads a video from its MIME type as today and an animated image from
  `frames > 1`, and `findComparable` filters on the same expression (the lead's: no `kind` column, which would store
  what the two columns already say). `Media.AnimatedImage` refuses `frames <= 1` in its `init`, the read now resting
  on it.
- `media_download.last_error`, `tasks.last_error`, `recordLastError` and `lastError` parameters go (the lead's: no
  log replaces the two writes no log records, a task killed at claim and a lease reclaimed, its `DEAD` state,
  attempts and `terminal_state_at` saying what happened).
- `storage_key` goes from exports and imports; every key is derived. `archive_held`, a boolean, says bytes may remain
  to delete: set where the key is stamped today, cleared where the reapers null it, kept on `SUPERSEDED` as the key is
  kept. The import runner fails a row whose archive is not held, as it fails a null key today.
- `media_type` and `file_extension` go from exports and from `UserDataExport` (the lead's). The downloader fills
  `OpenedExport`'s media type and extension from `ExportArchiveStore.format`, and `MeExportController` keeps writing
  them into the download's headers. `UserDataExportOutputDto.mediaType` leaves the wire, no client reading it
  (`agents/data-modelling.md`, Contract: the wire carries what the client acts on): the contract's major rises to
  `26.0.0`.

**G. The database checks what comments held** (C, the lead's extent).
- Four `CHECK` constraints, named `ck_<table>_<subject>`: a pair's `first_pin_id < second_pin_id`; a media is a video
  exactly when its MIME type starts with `video/` and both video columns are present, its two audio columns are present
  together, and only a video has sound; a task's `terminal_state_at` is present exactly when its state is terminal; a
  `READY` export has its size, digest and completion instant.
- Written by hand into the baseline's `create table` statements, Ebean generating a `CHECK` for an enumerated column
  alone. A test reads each one from the migrated schema (`sqlite_master`), and a repository test per constraint sees
  the database refuse a row that breaks it.
- The pair's order compares text: the test establishes that `typeof(first_pin_id)` is `text` before relying on it.
- `EbeanTaskQueueTest`'s case that back-dates `terminal_state_at` on a `PENDING` and a `RUNNING` row to prove the
  sweep's state filter goes: the constraint refuses those rows, and holds the filter's premise. (Corrected: the case
  is cut rather than deleted, its stale and fresh terminal rows staying as the time filter's only test, block 70; the
  premise holds while the constraint's states are `TaskState`'s terminal ones, which `MigratedSchemaTest` binds since
  block 80.)
- The task and export constraints are not in the inventory: found by this lot's reading of the models, and taken
  because each is one line here and a migration later.

(Corrected: behaviour the blocks changed beyond these decisions, accepted by the lead and endorsed by the holistic
review, `.reviews/the-database-enforces-its-references-holistic.md`:
- A retryable download failure no longer bumps `media_downloads.updated_at`, which only `recordLastError` wrote, so
  the wire's `MediaDownloadOutput.updatedAt` on a `PENDING` row stays at the request instant (block 53).
- `TaskProcessor`'s `Retryable` carries no message, and the "exhausted retries" log line no longer repeats the cause,
  which `runHandler` logs with its stack just before (block 56).
- The export reaper deletes the derived key alone, no column remaining to diverge from it (block 60).
- The export downloader requires a `READY` row's size, digest and completion instant, three fields where it required
  six (blocks 60 and 66).
- `PinRecycleBin` no longer deletes a pin's media by hand, a dependent deletion under decision D though not in its
  list (block 46).)

## 4. Data shape

The archive changes nowhere: `ExportContentGoldenJsonTest`'s expected text is unchanged at every block. The wire
changes once, in block 60: `UserDataExportOutputDto.mediaType` leaves it.

| Datum | Domain type | Optional | Identity and uniqueness | Storage | Wire | Archive |
|---|---|---|---|---|---|---|
| A reference between two rows | unchanged | unchanged | None: a key decides no identity | a foreign key with its declared action, enforced | None: the contract describes no storage mechanism | None: the archive names by natural keys |
| A pin's publisher | `Person?`, unchanged | yes | the person's | `pins.publisher_id`, `on delete set null`, indexed | unchanged | unchanged |
| Table and column names | None: storage only | no | None: names, not data | E's names | None: storage only | None: storage only |
| An import's processed pins | `Int`, computed | no | None: a count | None: `created_pins + skipped_pins` | `processedPins`, unchanged | None: imports are not exported |
| A media's animation | `Boolean`, computed, unchanged | no | None: a fact of the media | None: `mime_type` and `frames` | None: not on the wire today | `animated`, unchanged |
| A download's and a task's last error | None: removed | None: removed | None: removed | removed | None: never on the wire | None: never exported |
| An archive's storage key | derived from the id and the format | no | None: derived | removed | None: storage only | None: storage only |
| An archive held | `archiveHeld: Boolean` | no | None: a fact of the row | `archive_held`, not null | None: no client acts on it | None: exports and imports are not exported |
| An export's media type and extension | `ArchiveFormat`, read from `ExportArchiveStore.format` into `OpenedExport` | no | None: one format per store | removed | removed from `UserDataExportOutputDto`; the download's `Content-Type` and file name, unchanged | None: an archive is not inside itself |
| Comment-held invariants | unchanged | unchanged | None: constraints | G's four `CHECK` constraints | None: storage only | None: storage only |

Rules this table follows, from `agents/data-modelling.md`: derived data is not stored, a column nothing reads is
deleted, an invariant is held by structure, a dependent row is deleted with what it depends on, alpha data is
disposable.

## 5. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `chore/the-history-is-one-baseline` | Decision B. |
| 20 | `refactor/a-table-is-named-after-its-model` | Decision E. |
| 30 | `feat/the-database-holds-its-references` | Decision C. |
| 40 | `refactor/the-database-deletes-dependents` | Decision D. |
| 50 | `refactor/a-derived-column-is-computed` | Decision F, but the archives. |
| 60 | `refactor/an-archive-key-is-derived` | Decision F's archives. |
| 70 | `feat/the-database-checks-its-invariants` | Decision G. |
| 80 | `chore/the-references-lot-closes` | The holistic review's findings and the documents (Wrap). |

(Corrected: seventeen code blocks ran, their branches listed at the head of this document. The splits, each approved
by the lead and recorded in the report of the block it split: 20 into 20 (tables, indexes, the Konsist rules) and 25
(`created_at` and `updated_at`); 30 into 30 (the keys declared, their indexes, the onDelete rule) and 35 (the pragma
on, the re-reads, the fixtures); 40 into 40 (the user row, the pin and board repositories), 43 (the account's
repository methods) and 46 (the sweeps, `DeletePinMedia`'s transaction); 50 into 50 (`processed_pins`,
`media.animated`), 53 (`recordLastError`), 54 (`media_downloads.last_error`) and 56 (`tasks.last_error`); 60 into 60
(every key derived), 62 (an export's `archive_held`), 64 (an import's) and 66 (an export's format, the contract
`26.0.0`). Block 80 holds its bounds and does not split.)

Each block is measured after its first commit with the command of `agents/workflow.md`; one that passes a bound
splits at a number between its own and the next. **Block 10 cannot hold either bound**: deleting the history is 71
files and 1034 lines before the baseline is written, and it cannot split, the gate refusing a half-deleted history.
The operator waived both bounds for it on 2026-10-11, for the deletion of the history alone. Every later block regenerates the baseline, whose diff counts.

### Block 10

- The history replaced by the generated baseline: `mig/` holds `1.0__initial.sql` and `model/1.0__initial.model.xml`
  alone. The two tests of decision B gone.
- The pull request shows the migrated schema of `main` and of the block side by side: `select type, name, tbl_name, sql
  from sqlite_master order by name` on a database each migrated, diffed. Every difference is named in the body.
- `agents/engineering.md`'s flattening rule reads that the history is append-only from the baseline. The backlog's
  item on the table rebuild's row-carrying path is deleted: no rebuild remains.
- Carries this specification and ADR 0058.

### Block 20

- Decision E's names in the models, the raw queries and the outcome table of `UniqueConstraintOutcomeTest`.
- The `@Table` rule flags a fixture model named `pin_tag_model` and nothing in production; the `@Index` rule flags a
  fixture index `ix_pin_tag_model_tag` on a model whose table is `pin_tags`.
- The migrated schema holds no table or column whose name contains `_model` or `when_`
  (`select name from sqlite_master` and `pragma_table_info` of each table).

### Block 30

- Decision C. The onDelete rule flags a fixture `@ManyToOne` without `@DbForeignKey(onDelete)`, and one declaring
  `noConstraint`.
- A test reads `pragma_foreign_key_list` for every table of the migrated database and asserts the exact set of
  `(table, column, referenced table, action)`, the three references of `media_frames` and `pin_duplicates` included:
  `CASCADE` everywhere but `pins.publisher_id`, `SET NULL`. Each key's column leads an index whose
  `pragma_index_list(...).partial` is `0`.
- The production address test of decision C fails on `main`'s `application.properties`.
- The migration test of decision B fails on a fixture script that drops `pins` after the baseline. (Corrected: it
  landed in block 10, `ReferencedTableDropTest`.)
- Deleting a person a pin names as publisher leaves the pin, its `publisher_id` null.
- `FingerprintMedia` on a media deleted while it hashed saves no frame and no pair, and its task succeeds; on a
  duplicate pin whose media was deleted while it hashed, it saves no pair naming that pin.
- `SetPinMedia` and `RequestPinMediaDownload` on a pin permanently deleted after their first read answer as for a pin
  not found, and write nothing.
- Fixtures that insert a row naming a parent that does not exist are fixed in this block.

### Block 40

- Decision D. Deleting a user leaves no row of theirs in any table but `tasks`, which holds no key to them and keeps
  the cancelled download task, one row seeded per table; permanently deleting a pin leaves no join row, media, frame,
  download or pair of it; permanently deleting a board leaves no join row or collection of it.
- `DeletePinMedia` whose pair deletion throws leaves the media row in place.
- In the pull request, as evidence and not as a test:
  `git grep -n -E 'fun (deleteOrphans|deleteAllPersonsForUser|deleteAllTagsForUser|permanentlyDeleteAllPinsForUser|permanentlyDeleteAllBoardsForUser|deleteForUser|deleteAllForUser)' -- api/api-domain`
  prints `SessionTokenRepositoryInterface.deleteAllForUser` alone.
- The backlog's batch writes item reads "I3, I7 and S5, and units 14 and 21" where it read "I3, I7, S5 and X2, and
  units 14, 15 and 21".

### Block 50

- `processed_pins`, `animated` and both `last_error` columns absent from the migrated schema: the criterion that
  tells this block from `main`.
- Regression guards, which pass on `main` too: an import resumed after three lines resumes at the fourth, its cursor
  computed; `findComparable` from an animated image returns animated images and videos, not still images.
- `Media.AnimatedImage` with `frames = 1` throws.

### Block 60

- `storage_key`, `media_type` and `file_extension` absent from the migrated schema; `archive_held` present.
- A `SUPERSEDED` export keeps `archive_held` until its reaper pass deletes the bytes, then clears it.
- An import whose archive is not held fails `ARCHIVE_UNREADABLE`, as a null key fails today.
- Block 64 touches 20 files: each is a compile break of the one field's rename, and a split would leave
  `storage_key` and `archive_held` side by side. (Corrected: a line of its own, not a continuation of the criterion
  above.)
- `contract/openapi.json`'s `UserDataExportOutput` has no `mediaType`, and `info.version` is `26.0.0`; the download
  still answers `Content-Type: application/zip` and a `.zip` file name.

### Block 70

- Decision G. One repository test per constraint: inserting the breaking row raises the constraint violation.
- The schema test lists `ck_pin_duplicates_order`, `ck_media_variant`, `ck_tasks_terminal_state_at` and
  `ck_user_data_exports_ready` in the migrated schema.
- `api/AGENTS.md` says the baseline's `CHECK` constraints are hand-written, that a migration may not drop a table a
  key references, and which changes that leaves out (ADR 0058, Consequences). The backlog's `foreign_keys` item is deleted, and the types item gains unit 24's factories
  (`RenditionSpec`, the import's counters, `ExportMediaExtension`), which this lot leaves. The backlog gains, first
  in P2, the move to PostgreSQL, pointing at ADR 0058.

### Block 80

- The holistic review's findings, then the documents: this specification, the handoff, ADR 0056's `Status:` line
  naming ADR 0058, and the backlog.

## 6. Adjacent backlog items

- **"`foreign_keys` is off"**: closed by this lot, in block 70.
- **"A table rebuild's row-carrying path is exercised by nothing"**: closed in block 10, no rebuild remaining.
- **"A write over many rows saves one row at a time, and some deletions ... miss a dependent"**: X2 closed in block
  40; the batch writes and transactions stay its lot's.
- **"Domain and wire states are independent nullable fields"**: stays open, the sealed states lot; its text names no
  column this lot removes.
- **"The domain names concepts with bare strings"**: stays open, the types lot; this lot takes its database renames,
  and hands it X1's factories (block 70). S3's `media.storage_key` stays with its unit 19.

## 7. Out of scope

- **The domain, wire and archive renames** (`sha256`, `mediaType`, `failureCode`): the types lot. Observed as
  `ExportedMedia.sha256` unchanged at the lot's tip.
- **A `CHECK` on a state column's values** (`check (state in (...))`): the types lot, whose enums Ebean turns into one.
  Observed as no `check (state in` in the baseline at the lot's tip.
- **Deleting a person from a route**: no route does. Observed as no `DELETE` under a persons path in the contract.
