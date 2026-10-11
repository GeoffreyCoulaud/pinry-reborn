# Handoff: the database enforces its references

Date: 2026-10-11
Tier: Spec. Specification `docs/specs/2026-10-11-the-database-enforces-its-references.md`, reviewed in
`.reviews/the-database-enforces-its-references-spec.md` (1 CRITICAL, 5 MAJOR, 13 MINOR, closed in the specification
and ADR 0058); ADR 0058 amends ADR 0056, decision 7.2.
Lot `0.55.0`, one stack, GitHub stack #398: 10 `chore/the-history-is-one-baseline` (#396), 20
`refactor/a-table-is-named-after-its-model` (#397), 25 `refactor/a-timestamp-is-created-at` (#399), 30
`feat/the-database-holds-its-references` (#400), 35 `feat/the-database-enforces-its-references` (#401), 40
`refactor/the-database-deletes-dependents` (#402), 43 `refactor/the-account-rows-go-with-the-user` (#403), 46
`refactor/the-orphan-sweeps-go` (#404), 50 `refactor/a-derived-column-is-computed` (#405), 53
`refactor/an-unread-download-error-goes` (#406), 54 `refactor/a-download-keeps-no-error` (#407), 56
`refactor/an-unread-task-error-goes` (#408), 60 `refactor/an-archive-key-is-derived` (#409), 62
`refactor/an-export-holds-its-archive` (#410), 64 `refactor/an-import-holds-its-archive` (#411), 66
`refactor/an-export-format-is-the-stores` (#412), 70 `feat/the-database-checks-its-invariants` (#413), then the
closing block 80 `chore/the-references-lot-closes`. Written in block 80 from the block reports of #396 to #413 and
the holistic review.

## Current state

- **The migration history is one baseline**, `1.0__initial.sql` and its model file, generated from the models and
  append-only from this lot's merge. Its four `CHECK` constraints are written by hand into the `create table`
  statements; `MigratedSchemaTest` lists them, so a regenerated baseline that drops them fails.
- **SQLite enforces every foreign key**: `foreign_keys=true` is in the three SQLite addresses, and
  `ProductionDatasourceDeclarationTest` reads production's from its file. Every key declares its deletion with
  `@DbForeignKey(onDelete = ...)`: `CASCADE` everywhere but `pins.publisher_id`, `SET NULL`. A Konsist rule refuses a
  relation that declares no action or no constraint. Every key's column leads a non-partial index.
- **The code deletes the row it targets, the database the rest.** Deleting an account deletes the user row; deleting
  a pin or a board deletes its row. The hand-written dependent deletions, both orphan sweeps and the repository
  methods that served them are gone; `SessionTokenRepositoryInterface.deleteAllForUser` stays for sign-out.
  `DeletePinMedia` runs its two deletions in one transaction. `RequestOutdatedFingerprintRefresh`, formerly `ReapFingerprints`, only enqueues the fingerprint task.
- **A write whose parent may be gone checks it inside its transaction**: `SetPinMedia` re-reads the pin,
  `RequestPinMediaDownload` reads it there once, `FingerprintMedia` re-reads its media and the duplicates' media.
- **A table is named after its model**, plural and snake case, `media` the one exception; `created_at` and
  `updated_at` replace `when_created` and `when_modified`; every index or constraint name starts with `ix_`, `ux_`,
  `uq_` or `ck_` and its table's name, or is `pk_<table>`.
- **No column stores what another derives or what nothing reads**: `processed_pins`, `media.animated`, both
  `last_error`, and the archives' `storage_key`, `media_type` and `file_extension` are gone. An archive's key derives
  from the row's id and the store's format; `archive_held` says whether bytes may remain to delete.
- **A migration may not drop a table a key references** (ADR 0058, decision 9): `ReferencedTableDropTest` refuses
  one, a quoted name and a rename before the drop included. The move to PostgreSQL lifts the limit.
- The contract is `26.0.0`: `UserDataExportOutput` lost `mediaType`, which no client read. The archive is unchanged.
- **A development database must be reset**: Ebean refuses the old history's recorded `1.0` checksum at boot.

## Evidence

| Block | Gate | Budget against its parent |
|---|---|---|
| 10 | `dagger call gate` green at `ceadd0e0` | 1649 lines, 85 files (both bounds waived by the operator) |
| 20 | green at `7492bed6` | 258 lines, 18 files |
| 25 | green at `18ef4e05` | 58 lines, 14 files |
| 30 | green at `4d616dfc` | 159 lines, 19 files |
| 35 | green at `34ec0b1c` | 305 lines, 18 files |
| 40 | green at `767e9999` | 459 lines, 17 files |
| 43 | green at `f3e7666c` | 164 lines, 17 files |
| 46 | green at `6000c653` | 176 lines, 16 files |
| 50 | green at `cb2fb6be` | 143 lines, 17 files |
| 53 | green at `f680ef68` | 42 lines, 6 files |
| 54 | green at `44e7a113` | 30 lines, 18 files |
| 56 | green at `ffa02d6c` | 64 lines, 17 files |
| 60 | green at `8b935e7d` | 149 lines, 11 files |
| 62 | green at `11dfa790` | 123 lines, 19 files |
| 64 | green at `5c89827e` | 77 lines, 20 files (the lead's approval, the specification's line) |
| 66 | green at `0f08f8be` | 76 lines, 17 files |
| 70 | green at `f9f46cdb` | 166 lines, 10 files |
| 80 | see #414's report | 100 lines, 15 files |

- Every pull request of #396 to #413 green on continuous integration (`gh pr checks`, read in block 80).
- Block 10's schema comparison: `main`'s history and the baseline each applied by `sqlite3` 3.53.4, `sqlite_master`
  diffed; a semantic comparison (`pragma_table_info`, `pragma_foreign_key_list`, every index's statement) found no
  difference (#396).
- Each block's mutations are in its report and pull request; the structural tests each failed on the mutation that
  breaks them (a key's action, an index made partial, the pragma removed from an address, a CHECK clause removed, a
  column added back to the baseline).

## Departures from the specification

All recorded in the specification as `(Corrected: ...)` in block 80.

- **Seventeen code blocks where eight were planned**: 20 split into 20/25, 30 into 30/35, 40 into 40/43/46, 50 into
  50/53/56, 53 again into 53/54, 60 into 60/62/64/66. Each split was approved by the lead; block 40's by the operator
  through the lead.
- **Block 64 kept at 20 files**, every one a compile break of the one field's rename, with a line in the
  specification saying why.
- **`ReferencedTableDropTest` landed in block 10**, not 30.
- **`RequestPinMediaDownload` reads the pin once, inside its transaction**; `FingerprintMedia` re-reads through the
  existing `findMissingMediaIds`, no new port method (blocks 30 and 35).
- **Behaviour beyond the decisions**, accepted by the lead and endorsed by the holistic review: a retryable download
  failure no longer bumps `media_downloads.updated_at`, so the wire's `updatedAt` on a `PENDING` download stays at the
  request instant (53); `TaskProcessor`'s `Retryable` has no message and the exhausted-retries log line no longer
  repeats the cause (56); the export reaper deletes the derived key alone (60); the downloader requires three fields
  (60, 66); `PinRecycleBin` lost its media deletions (46).
- **`EbeanTaskQueueTest`'s case was cut, not deleted**: its terminal rows stay as the time filter's only test (70).
- **The handoff is written in the closing block** rather than the last code block, on the lead's brief.

## Pitfalls

- **`generateDbMigration` needs `dbmigration/model/` to exist**: `git rm` of the baseline pair deletes the emptied
  directories, and the generator fails with "path to resources does not exist". Delete with `rm`, or recreate them.
- **`generateDbMigration` compiles the module's tests first**, so the persistence tests must compile before the
  baseline can be regenerated (#411).
- **A regenerated baseline drops the hand-written `CHECK` constraints**; carry them back by hand (`api/AGENTS.md`).
- **A teammate does not receive its background task's end notice**: brief it to poll.
- **`gh stack submit` pushes every branch above**, so a split's unpushed upper branch needs `gh stack link` for the
  lower one alone (#397). It also prints none of the `pre-push` hook's output: run the gate first.
- **A split's file list misses tests that build a type positionally**: four tests building `MediaDownload` (#406),
  and `RequestPinMediaDownloadTest`'s trailing `null` that compiled silently into `terminalStateAt` (#408). Compile
  every module's tests (`./gradlew compileTestKotlin`) after the first cut.
- **`BaseTest` clears every mock before each test**, so a stub declared in a test class's `init` is gone by the time
  the test runs (met in block 80).
- **`RawSqlOutsideInventory` refuses a `raw(` fragment it does not list**: use the query beans, or add the fragment
  with its reason (#405, block 80).
- **The evidence guard refuses `sed -i` and a redirection outside `$TMPDIR`**: export `TMPDIR` to the scratchpad.
- **Removing a repository test can drop a branch's only coverage**: `koverVerify` failed on
  `findCurrentPasswordHash`'s null branch once `deleteForUser`'s test went (#403).

## Tier-2 questions

Discuss, 2026-10-10 and 2026-10-11, the letter of the question in brackets:

- The reference of a pin to its publisher (F1): `SET NULL`, deleting a person removing their credits.
- How a cascade is declared (B1): in the model, each relation's `@DbForeignKey(onDelete = ...)`.
- How a table's name is held (D1): a Konsist rule on `@Table` against the class name.
- A model whose table keeps another name (E2): an exception list in the test, each entry with its reason.
- Dropping a referenced table (G1): the limit accepted, no path built; PostgreSQL made the next lot.
- The specification, read and validated by the operator on 2026-10-11.
- Block 10's bounds: waived by the operator, for the history's deletion alone.

After Discuss: block 40's three-way split, approved by the operator through the lead. The other splits and block 64's
20 files were the lead's to approve.

In the closing block, the holistic review's finding 8 (H): rename `ReapFingerprints`, rather than keep the name or
delete the class, since `GarbageCollectionLifecycle` calls use cases alone. Named `RequestOutdatedFingerprintRefresh`
after `RequestPinMediaDownload` (a request asks for work a task does later), "drain" having been found to be code
jargon that also names the worker's shutdown.

## The holistic review

`.reviews/the-database-enforces-its-references-holistic.md`, over
`git diff lot/0.54.0-addresses-and-people-take-their-shape..origin/feat/the-database-checks-its-invariants`: 0
CRITICAL, 2 MAJOR, 8 MINOR. Each finding and its exit:

1. MAJOR, `FingerprintMediaTest` stubbed the re-read whatever it was asked: fixed in block 80. The stub answers on the
   ids asked; `findMissingMediaIds(listOf(media.id))` now fails the duplicate case.
2. MAJOR, the specification carried no correction: fixed in block 80, decision C's re-read, the branches and blocks,
   the block-64 line, the behaviour beyond the decisions, the drop test's block, the name rule's prefix and the
   `EbeanTaskQueueTest` case.
3. `ck_tasks_terminal_state_at`'s states were bound to nothing: fixed in block 80, a `MigratedSchemaTest` case reads
   them against `TaskState`'s states owed no attempt; dropping `CANCELLED` from the baseline fails it.
4. `findComparable` folded case where the mapper and `ck_media_variant` do not: fixed in block 80, `glob 'video/*'`
   through a new `RawSqlOutsideInventory` entry, and a case on a still image typed `VIDEO/PNG`, which fails on the
   query beans' `startsWith`.
5. ADR 0058's referenced tables omitted `media`: fixed in block 80 (`Corrected`).
6. `ReferencedTableDropTest` missed a quoted name and a rename before the drop: fixed in block 80, a case each, each
   failing on the former regular expressions.
7. `MigratedSchemaIndexesTest`'s name: fixed in block 80, now `MigratedSchemaTest` with a one-line KDoc;
   `api/AGENTS.md` follows.
8. `ReapFingerprints` named a sweep it no longer runs: fixed in block 80, renamed
   `RequestOutdatedFingerprintRefresh` with `request()` (answer H below), the lifecycle's log line "outdated
   fingerprint refresh request failed".
9. The export format constant carried no warning: fixed in block 80, a one-line comment.
10. The re-read's place inside the transaction was not discriminated: fixed in block 80, `SetPinMediaTest` and
    `FingerprintMediaTest` see the parent gone only from inside it; moving each re-read out fails them.

Handed over by the blocks, all closed in block 80: `AccountDeletionCleaner`'s comment on the `storageKey` column;
`findReclaimableTerminal`'s KDoc; `PinDuplicateModel`, `TaskModel` and `MediaModelMapper` now name their constraint.
`DeletePinMedia`'s `@Suppress("LongParameterList")` stays with its reason, as `SetPinMedia`, `FingerprintMedia` and
`PinRecycleBin` carry it: seven container-injected collaborators, and a parameter object would group them for the
threshold alone.

## What is not validated

- **No production database exists to migrate**: the suite migrates the baseline through Ebean on every run, but the
  alpha has nothing deployed.
- Block 10's schema comparison migrated both sides with the `sqlite3` CLI, not Ebean's runner.
- `deleteTerminalBefore`'s state filter has no test that fails without it: the constraint holds its premise, and the
  constraint's states are now bound to `TaskState`.

## Open question to the operator

None. The one this handoff asked, whether to rename `ReapFingerprints`, is answered (H, under Tier-2 questions).

## The lot's counts

- Fix-backs: 0. Fixes on top: 0. Cascaded rebases: 0. One local rebase, block 35 onto block 30 amended before either
  was pushed; runs re-triggered by it: 0.
- The operator's reading of the bodies: no remark.

## Next step

The operator's review of the stack, #396 to #414, then `gh stack merge --rebase` and the tag
`lot/0.55.0-the-database-enforces-its-references`. Then the move to PostgreSQL (operator, 2026-10-11), before ADR
0055's third-party importer.
