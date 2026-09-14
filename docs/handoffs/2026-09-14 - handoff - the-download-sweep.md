# Handoff: a download row stops being immortal

Date: 2026-09-14
Tier: Direct, with the holistic review kept at the operator's request rather than waived
(`agents/workflow.md`, Wrap).
Backlog item closed: "The download list is unbounded, unpaginated and polled every second, and
nothing sweeps a failed row".
Base commit: `ae361d66`.

## Current state

**Block 1 (`fix/image-download-sweep`) is open.** `image_download` is swept like the four datasets
before it: `GarbageCollectionLifecycle` runs five `Reap*` sweeps instead of four, and the fifth,
`ReapStaleImageDownloads`, does two things on the same daily tick.

- A `PENDING` row **whose task is terminal or gone** becomes `FAILED` with `INTERNAL_ERROR`.
- A `FAILED` row untouched for `garbage_collection.failed_download_grace` (`P7D`) is deleted.

The contract is unchanged, no migration was written, and the web application was not touched.

**Block 2 is the pagination of `GET /api/v1/me/image-downloads`**, adopted from the review of
block 1 rather than filed: the endpoint returns every row the requester owns where every other list
pages on an opaque cursor. It changes the response shape, so it carries a contract major.

## What the three symptoms became

The backlog item named three, and one sweep answers two of them.

**Nothing swept a failed row**: fixed. `deleteFailedBefore` reclaims it seven days after the
failure, and the user's own `DELETE /api/v1/me/image-downloads/{pinId}` still reclaims it at once.

**Polled every second**: fixed where it was a defect, and left where it is not.
`downloadPollInterval` already stops as soon as no row is `PENDING`, so a healthy download polls for
the seconds it runs. What polled for ever was a `PENDING` row nothing would settle; the sweep ends
it, and the row keeps the retry and the file recourse the task centre offers a `FAILED` row.

**Unbounded and unpaginated**: block 2's, above.

## Why the sweep reads the task and not a clock

The first implementation settled a `PENDING` row that had gone untouched for a grace of one hour.
The review refused it, and rightly: the grace was a heuristic standing in for a fact the database
already holds. A row is abandoned exactly when **its task is terminal or absent**, and
`ImageDownloadModel` has carried `taskId` since `1.5`.

Reading the fact rather than approximating it deleted the `pending_download_grace` key, deleted the
comment that stated the trap, and removed the failure mode the trap described: a grace set too low
settles a download that is still running. `TaskQueueInterface.findLiveIds` is what it reads, on the
model of `ImageRepositoryInterface.findMissingImageIds`, and a terminal task and an absent one are
the same answer to the caller.

The sweep is safe against the race it does have: `markFailed` is a CAS on `PENDING`, so a row the
worker settles between the read and the write is refused, not overwritten, and is not counted.

## Why a stale `PENDING` row exists at all

Narrower than it looks. `DownloadPinImage.failRetryable` marks the row `FAILED` on the last attempt,
and `ClearPinDownload` deletes the row when a cancellation supersedes the download, so neither the
ordinary failure path nor the cancellation path leaves one behind. What does: an exception raised
outside the `try` blocks that route into `failRetryable`, in the two repository reads that open
`download()`. It exhausts its retries, `TaskProcessor` marks the task `DEAD`, and the download row is
still `PENDING` with no task left to advance it. `ReapTerminalTasks` then deletes that `DEAD` row
after seven days, which is the absent-task case.

## Pitfalls, in the order they cost time

- **`CommentCarriesDocumentation` leaves a KDoc two content lines**, `/**` and `*/` counting toward
  the four. Worse, a `//` block under a KDoc merges into it: only a blank line ends a run, so a
  four-line KDoc followed by a two-line comment is reported as one comment of six. Every long
  comment already in the tree is in a per-module baseline, which is why the rule looks unused. It
  cost four red gates in this block alone.
- **The fifth sweep pushes two detekt counters over**, both suppressed inline and both without a
  comment, the review having refused the justifying prose. `EbeanImageDownloadRepository` reaches
  twelve functions against eleven, eleven of them the port's and the twelfth the private helper
  three of them share. `GarbageCollectionLifecycle` reaches seven constructor parameters against
  six: **a design observation rather than a defect**, the structural answer being one injected
  collection of sweeps instead of one parameter each, which is a refactor across five classes and
  was not asked for.

## What is not validated

- **No index supports the sweep's reads**, deliberately, and no measurement backs the choice: the
  argument is that this sweep is what bounds the table, so `findPending` scans the downloads in
  flight rather than a column that accumulates, which is what `SweepIndexesMigrationTest` pins an
  index for on `session_tokens` and `tasks`. If `image_download` is ever seen to grow, that is the
  first thing to revisit.
- **Nothing exercises the sweep end to end.** The repository tests cover both reads against a real
  SQLite schema and the use case covers the abandoned row, the live one and the lost CAS, but no
  integration test starts the worker and watches a row settle on the tick.
- **The holistic review has not run yet.** It is Wrap's first step and this document predates it.

## Next step

Block 2, the pagination. Then Wrap: the holistic review over
`git diff lot/0.20.0-the-grid-keeps-every-page..origin/main` with nothing in flight, then the closing
block for its findings, this document's corrections and the lot tag.
