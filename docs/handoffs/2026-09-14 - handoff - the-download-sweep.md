# Handoff: a download row stops being immortal

Date: 2026-09-14
Tier: Direct, one code block written inline by the lead, with the holistic review kept at the
operator's request rather than waived (`agents/workflow.md`, Wrap).
Backlog item closed: "The download list is unbounded, unpaginated and polled every second, and
nothing sweeps a failed row".
Base commit: `ae361d66`. Branch: `fix/image-download-sweep`.

## Current state

`image_download` is swept like the four datasets before it. `GarbageCollectionLifecycle` runs five
`Reap*` sweeps instead of four, and the fifth, `ReapStaleImageDownloads`, does two things on the
same daily tick: a `PENDING` row untouched for `garbage_collection.pending_download_grace` (`PT1H`)
becomes `FAILED` with `INTERNAL_ERROR`, and a `FAILED` row untouched for
`garbage_collection.failed_download_grace` (`P7D`) is deleted.

The contract is unchanged, no migration was written, and the web application was not touched.

## What the three symptoms became

The backlog item named three, and one sweep answers two of them.

**Nothing swept a failed row**: fixed. `deleteFailedBefore` reclaims it seven days after the
failure, and the user's own `DELETE /api/v1/me/image-downloads/{pinId}` still reclaims it at once.

**Polled every second**: fixed where it was a defect, and left where it is not. `downloadPollInterval`
already stops as soon as no row is `PENDING`, so a healthy download polls for the seconds it runs.
What polled for ever was a `PENDING` row nothing would settle; `failPendingBefore` ends it, and the
row keeps the retry and the file recourse the task centre offers a `FAILED` row.

**Unbounded and unpaginated**: bounded in time, not in count. The list is now capped by one grace of
failures rather than by the user's willingness to clear rows, and the endpoint still returns every
row it finds where every other list pages on a cursor. That remainder is a fresh `P2` backlog item,
the operator having scoped this lot to the sweep alone.

## Why a stale `PENDING` row exists at all

Narrower than it looks, which is why the grace is generous rather than tight.
`DownloadPinImage.failRetryable` marks the row `FAILED` on the last attempt, and `ClearPinDownload`
deletes the row when a cancellation supersedes the download, so neither the ordinary failure path
nor the cancellation path leaves one behind. What does: an exception raised outside the `try` blocks
that route into `failRetryable`, in the two repository reads that open `download()`. It exhausts its
retries, `TaskProcessor` marks the task `DEAD`, and the download row is still `PENDING` with no task
left to advance it.

## Pitfalls, in the order they cost time

- **`CommentCarriesDocumentation` leaves a KDoc two content lines**, `/**` and `*/` counting toward
  the four. Worse, a `//` block under a KDoc merges into it: only a blank line ends a run, so a
  four-line KDoc followed by a two-line comment is reported as one comment of six. Every long
  comment already in the tree is in a per-module baseline, which is why the rule looks unused.
- **The fifth sweep pushes two detekt counters over**, and both are suppressed inline with a reason:
  `EbeanImageDownloadRepository` reaches twelve functions against eleven, eleven of them the port's
  and the twelfth the private helper three of them share; `GarbageCollectionLifecycle` reaches seven
  constructor parameters against six. The second is a design observation and not a defect: the
  structural answer is one injected collection of sweeps rather than one parameter each, which is a
  refactor across five classes and was not asked for.
- **`pending_download_grace` is bounded from below by the task queue**, not chosen freely. A live
  download stamps `updatedAt` on every transient failure, so the longest it goes unstamped is
  `tasks.backoff_cap` (`PT5M`) plus `tasks.lease_duration` (`PT1M`); an hour is that with an order of
  magnitude to spare. Lower it under six minutes and the sweep settles downloads that are still running.

## What is not validated

- **No index supports either cutoff**, deliberately, and no measurement backs the choice: the
  argument is that this sweep is what bounds the table, so its scan is over one grace of failures
  rather than over a column that accumulates, which is what `SweepIndexesMigrationTest` pins an index
  for on `session_tokens` and `tasks`. If `image_download` is ever seen to grow, that is the first
  thing to revisit.
- **Nothing exercises the sweep end to end.** The repository tests cover both cutoffs against a real
  SQLite schema and the use case covers the two graces, but no integration test starts the worker and
  watches a row settle on the tick.
- **The holistic review has not run yet.** It is Wrap's first step and this document predates it.

## Next step

Wrap: the holistic review over `git diff lot/0.20.0-the-grid-keeps-every-page..origin/main` once this
merges, then the closing
block for its findings, this document's corrections and the lot tag.
