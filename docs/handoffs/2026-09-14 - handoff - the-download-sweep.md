# Handoff: a download row stops being immortal, and the list stops being everything

Date: 2026-09-14
Tier: Direct, three code blocks, with the holistic review kept at the operator's request rather than
waived (`agents/workflow.md`, Wrap).
Backlog item closed: "The download list is unbounded, unpaginated and polled every second, and
nothing sweeps a failed row".
Base commit: `ae361d66`. Pull requests: 136 (block 1), 137 (block 2), and block 3's, which carries
this document.

## Current state

The three symptoms the backlog item named are all answered, and the lot closes the item whole rather
than leaving a remainder behind.

**Nothing swept a failed row** (block 1, PR 136). `GarbageCollectionLifecycle` runs five `Reap*`
sweeps instead of four. `ReapStaleImageDownloads` settles a `PENDING` row whose task is terminal or
gone to `FAILED` with `INTERNAL_ERROR`, and deletes a `FAILED` row untouched for
`garbage_collection.failed_download_grace` (`P7D`). The user's own
`DELETE /api/v1/me/image-downloads/{pinId}` still reclaims one at once.

**Polled every second** (block 1). `downloadPollInterval` already stopped as soon as no row was
`PENDING`, so a healthy download polled for the seconds it ran. What polled for ever was a `PENDING`
row nothing would settle; the sweep ends it, and the row keeps the retry and the file recourse the
task centre offers a `FAILED` row.

**Unbounded and unpaginated** (blocks 2 and 3, PRs 137 and this one).
`GET /api/v1/me/image-downloads` takes `cursor` and `pageSize` and answers a `pagination` object,
on the opaque-cursor convention the other five lists carry. The contract is `6.1.0`: both query
parameters and the response field are additive.

## Why the sweep reads the task and not a clock

The first implementation settled a `PENDING` row that had gone untouched for a grace of one hour.
The review refused it, and rightly: the grace was a heuristic standing in for a fact the database
already holds. A row is abandoned exactly when **its task is terminal or absent**, and
`ImageDownloadModel` has carried `taskId` since `1.5`.

Reading the fact rather than approximating it deleted the `pending_download_grace` key, deleted the
comment that stated the trap, and removed the failure mode the trap described: a grace set too low
settles a download that is still running. `TaskQueueInterface.findLiveIds` is what it reads, on the
model of `ImageRepositoryInterface.findMissingImageIds`. The one race it does have is covered:
`markFailed` is a CAS on `PENDING`, so a row the worker settles between the read and the write is
refused, not overwritten, and is not counted.

## Why a stale `PENDING` row exists at all

Narrower than it looks. `DownloadPinImage.failRetryable` marks the row `FAILED` on the last attempt,
and `ClearPinDownload` deletes the row when a cancellation supersedes the download, so neither the
ordinary failure path nor the cancellation path leaves one behind. What does: an exception raised
outside the `try` blocks that route into `failRetryable`, in the two repository reads that open
`download()`. It exhausts its retries, `TaskProcessor` marks the task `DEAD`, and the download row is
still `PENDING` with no task left to advance it. `ReapTerminalTasks` then deletes that `DEAD` row
after seven days, which is the absent-task case.

## The surrogate key, and why it was its own block

The cursor machinery is bound to `BaseModel` (`ModelCursor<M : BaseModel>`, and
`ModelPaginationHelper` comparing `it.id`). `image_download` and `images` were the only two entities
outside it, so no sort strategy could be written for either. Three routes were put to the operator:
the sort strategy carrying the identity, a real `id` column, or paging written by hand for this one
endpoint. The answer was the real column, with `images` corrected alongside, the alpha being deployed
nowhere.

`images` already carried its own `@Id id` and joined `BaseModel` with no schema change at all.
`image_download` gained a surrogate, `pin_id` keeping its uniqueness as `ux_image_download_pin` and
its outcome named in `UniqueConstraintOutcomeTest`. It is a separate block because the two together
would have run the production budget to roughly 185 of 200, and a schema change reads better alone.

## The client shows a page and says so

The task centre reads one page. Its count would otherwise under-report silently, so `useImageDownloads`
returns `{ downloads, hasMore }` and the badge reads `Downloads (1+)` when the server holds more. The
journey that asserts it was checked against the mutation that makes it fail (`hasMore: false`
hard-coded, the assertion then failing on the missing `Downloads (1+)`).

## Pitfalls, in the order they cost time

- **`CommentCarriesDocumentation` leaves a KDoc two content lines**, `/**` and `*/` counting toward
  the four. Worse, a `//` block under a KDoc merges into it: only a blank line ends a run, so a
  four-line KDoc followed by a two-line comment is reported as one comment of six. Every long
  comment already in the tree is in a per-module baseline, which is why the rule looks unused. It
  cost six red gates across the lot.
- **`generateDbMigration` writes the model snapshot before it fails on the SQL.** A run that dies on
  `DB Migration of non-null column with no default value` still leaves `<version>.model.xml` behind,
  and every later run then answers "no changes detected". Delete the snapshot before re-running, and
  delete the hand-written `.sql` too or the generator takes the next version number instead.
  `-Dddl.migration.strictMode=false` is not read; only `migration.setStrictMode(false)` in
  `GenerateDbMigration.kt` is, which is what the exception message itself names. It was set to
  produce the snapshot and removed again.
- **`@Index(definition = ...)` needs `name` as well**, or the model records a generated
  `ix_image_download_` and `DbMigrationModelCoverageTest` reports the index as created by a migration
  and recorded in none. `TaskModel` is the precedent and gives both.
- **A rebuild cannot compute its new column in the `select`.** `TableRebuildColumnsTest` compares the
  create, insert and select column lists literally, so `select pin_id, pin_id, ...` and any uuid
  expression both fail it. An `alter table ... add column` plus `update` before the rebuild is what
  satisfies it honestly.
- **`claimNext` claims the earliest available task, not the one the helper just enqueued.** A test
  that builds one task per state has to enqueue the still-PENDING one last, or a later `claimFresh`
  claims it out from under the assertion. It cost a green run that was green by luck.
- **A first page always carries a `previousCursor`.** `ModelPaginationHelper` sets it to the page's
  own first element on a FORWARD read, so an assertion that a first page has neither cursor is wrong
  against every list in the tree, not just this one.
- **100% branch coverage reaches the null branches of a paged read.** A cursor whose pivot row is
  gone, and a page carrying one cursor rather than the other, are each a branch: both need their
  own case or `koverVerify` fails on the package.
- **The two detekt counters block 1 tripped are suppressed inline and without a comment**, the review
  having read the justifying prose as restating the code. `EbeanImageDownloadRepository` reaches
  twelve functions against eleven; `GarbageCollectionLifecycle` reaches seven constructor parameters
  against six, which is **a design observation rather than a defect**: the structural answer is one
  injected collection of sweeps instead of one parameter each, a refactor across five classes that
  was not asked for.

## What is not validated

- **No index supports the sweep's reads**, deliberately, and no measurement backs the choice: the
  argument is that this sweep is what bounds the table, so `findPending` scans the downloads in
  flight rather than a column that accumulates, which is what `SweepIndexesMigrationTest` pins an
  index for on `session_tokens` and `tasks`. The same holds for the paged read, which orders on
  `(requested_at, id)` with no index behind it. If `image_download` is ever seen to grow, both are
  the first thing to revisit.
- **Nothing exercises the sweep end to end.** The repository tests cover its two reads against a real
  SQLite schema and the use case covers the abandoned row, the live one and the lost CAS, but no
  integration test starts the worker and watches a row settle on the tick.
- **No client pages the download list.** The task centre reads the first page and says when more
  exist; nothing sends a `cursor` back. That is enough for a list the sweep bounds, and it means the
  `previousCursor` half of the endpoint has no consumer yet.
- **The migration's row-carrying path is untested.** Migrations run against an empty database in the
  suite, so the `update image_download set id = pin_id` that preserves existing rows is exercised by
  no test at all.
- **The holistic review has not run yet.** It is Wrap's first step and this document predates it.

## Next step

Wrap: the holistic review over
`git diff lot/0.20.0-the-grid-keeps-every-page..origin/main` with nothing in flight, then the closing
block for its findings, this document's corrections and the lot tag.
