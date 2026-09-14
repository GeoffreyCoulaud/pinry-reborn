# Handoff: a download row stops being immortal, and the list stops being everything

Date: 2026-09-14
Tier: Direct, three code blocks plus the closing block, with the holistic review kept at the
operator's request rather than waived (`agents/workflow.md`, Wrap).
Backlog item closed: "The download list is unbounded, unpaginated and polled every second, and
nothing sweeps a failed row".
Base commit: `ae361d66`. Pull requests: 136 (block 1), 137 (block 2), 138 (block 3), and the closing
block's, which carries this document's corrections.

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

**Unbounded and unpaginated** (blocks 2 and 3, PRs 137 and 138).
`GET /api/v1/me/image-downloads` takes `cursor` and `pageSize` and answers a `pagination` object,
on the opaque-cursor convention the other lists carry.
(Corrected: "the other five lists" put a number on a living set and got it wrong; the contract holds
six other endpoints taking both parameters, and the count is read where it lives.) The contract is
`6.1.0`: both query parameters and the response field are additive.

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

## The holistic review, and where its findings went

The review ran over `git diff lot/0.20.0-the-grid-keeps-every-page..origin/main`, nothing in flight,
and reported **0 CRITICAL, 3 MAJOR and 9 MINOR**. Every finding is against merged code by
construction, so none gated anything; all of them are the closing block's.
`docs/adr/0010-review-finding-dispositions.md` gives the four exits.

| Finding | Exit |
|---|---|
| **MAJOR.** The sweep read every `PENDING` row and sent every task id in one `IN (...)`. Past SQLite's host-parameter ceiling the statement throws, `safeAll` logs it, and the sweep that bounds the table stops bounding it, silently | **Fixed here.** `pending.chunked(500)`, a constant for `ReapExpiredTasks.REAP_BATCH_SIZE`'s reason, with a test that captures the batches and fails on one unchunked call. The port it copies said so and the block did not read it: `findMissingImageIds`'s KDoc names the caller-side chunking its bound depends on |
| **MAJOR.** Two detekt suppressions carried no reason, against `agents/engineering.md` | **Fixed here.** Block 1's review read the original prose as restating the code, and the answer to that was a reason saying why the count is accepted, not no reason at all. `GarbageCollectionLifecycle` now carries the one thing the code cannot say: a collection of sweeps is the structural answer and was out of scope |
| **MAJOR.** The task centre polled and detected settlement from the first page alone. Before the lot the list was every row, and block 3 made it a page without touching either consumer | **Fixed here.** `downloadPollInterval` takes `hasMore` and keeps polling while a page may follow, and past one page a shrinking page invalidates the catalogue rather than naming pins it cannot see |
| **MINOR.** `?pageSize=0` answered an empty page with no cursor, which no client can advance past | **Fixed here**, `coerceIn(1, PinGetter.MAX_PAGE_SIZE)` as the pin endpoints do. The three older endpoints that share the gap are **a backlog item**: closing them is not this lot's |
| **MINOR.** The cursor's pivot was read unscoped, so a caller could pivot on a row that is not theirs | **Fixed here.** The pivot goes through the same ownership traversal as the page, so a foreign cursor behaves exactly like a deleted one |
| **MINOR.** `ImageDownloadModelSortStrategy`'s KDoc deferred its whole justification to another class | **Fixed here**, one clause, the reason where it stands |
| **MINOR.** The `@Operation` description still described an unpaged read | **Fixed here**, and the contract regenerated with it |
| **MINOR.** The missing-index exemption lived only in this document, which freezes | **Fixed here**: `SweepIndexesMigrationTest`'s KDoc carries it, next to the rule it excepts |
| **MINOR.** Two MockK `verify` lines restated the stubs they configured | **Fixed here**, the positives dropped and the genuine negative kept |
| **MINOR.** The migration's row-carrying path is exercised by nothing, and the same gap sits under `1.22.sql` | **A backlog item.** The review replayed `1.24.sql` against a populated database by hand and found it correct, so what is missing is the guard, not the fix, and the guard is a test harness this lot did not set out to build |
| **MINOR.** Pull request bodies 137 and 138 recorded no continuous-integration run | **Fixed here**, both bodies edited with their run ids. Red before green cannot be reconstructed for either: each block is one commit carrying tests and implementation together, so no commit exists to run |
| **MINOR.** `ImageModel` joined `BaseModel` with no consumer | **An accepted limit, written here.** It joins for symmetry with `image_download` and cost no schema change; there is no paged image endpoint today and no block named to bring one |

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
- **The two detekt counters block 1 tripped are suppressed inline**, each with a reason the closing
  block restored. `EbeanImageDownloadRepository` reaches twelve functions against eleven;
  `GarbageCollectionLifecycle` reaches seven constructor parameters against six, which is **a design
  observation rather than a defect**: the structural answer is one injected collection of sweeps
  instead of one parameter each, a refactor across five classes that was not asked for.
- **Narrowing a read that consumers treat as exhaustive is a change to the consumers too.** The list
  was every row the requester owned, so the task centre could poll on it and diff it to name what
  settled. Block 3 made it a page and left both readings in place, and neither the block's own tests
  nor its pull request caught it: every test answers one page. A port that becomes paginated needs
  its callers reread, not just recompiled.

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
  `previousCursor` half of the endpoint has no consumer yet. **Past one page the centre also cannot
  name what settled**: it invalidates the whole pin catalogue instead, which is correct and costs a
  refetch the single-page path avoids.
- **The migration's row-carrying path is untested**, which is now a backlog item. The review replayed
  `1.24.sql` against a populated database by hand and it is correct today; nothing keeps it so.

## Next step

Nothing this lot leaves open. The backlog gained two items, neither of them this lot's to close: the
untested row-carrying path, which predates it and sits under `1.22.sql` too, and the `pageSize` clamp
missing from three older endpoints.
