fix: the data travels closes, the holistic review's findings

**Stacked on #228** (block 65, `feat/the-task-centre-keeps-data-notices`). This is the closing block of the lot `the-data-travels` and the top of a stack of ten pull requests, none merged yet. It merges last, after #219 to #228.

The holistic review ran on the top of the stack before any merge, at the operator's request (`.reviews/the-data-travels-holistic.md`, not committed): 1 MAJOR and 11 MINOR. This block fixes all of them except one, kept on purpose (below). It also corrects the handoff, adds one backlog item and fixes three contradictions in the specification.

## What changes in behaviour

- **A lost close is sent again.** Before, a `POST .../archive/complete` whose answer was lost stopped the upload, even though the server could already hold the import as `PENDING` or `RUNNING`. The Cancel button then offered would `DELETE` it. Now the close is retried on the same schedule as the chunks (5 attempts, 5 s apart, then pause).
- **`IMPORT_NOT_AWAITING_ARCHIVE` no longer stops an upload.** Whether it answers a chunk or the close, it means the server has moved on (a lost close, or another tab got there first). The upload gives way to the server's row once that row is read. Its sentence left both catalogues.
- **A finished upload leaves the store only after the latest import is read again**, so the stale "stopped before the end" text never shows in between.
- **A cancel keeps the upload until the `DELETE` settles.** A refused cancel now shows its refusal and the upload goes on.
- **A read begun before an import opened no longer deletes that import's record.** The tab's own upload record is never pruned, and a finished upload deletes its own record.

`lib/imports.ts`: `nextStep` now takes an `Attempt` (a chunk or the close, each with its failure count) and gains a `DONE` step. `imports.ts` sends both requests through one `answerOf`.

## Evidence

| Check | Result |
|---|---|
| `dagger call gate` | Green at `6de14f7b`, exit 0. Clients: 55 files, 280 tests; `src/lib` coverage 100 % of lines and branches |
| Continuous integration | Run 36160705746, green, `verify` 7 min 49 s |
| Budget, against `feat/the-task-centre-keeps-data-notices` | 278 lines, 18 files: under 500 and 20 |
| Red before the fix | The three new behaviour cases (the flash, the lost close, the refused cancel) failed against the unfixed store |
| Headless reading | Firefox, stubbed API, 1280 and 380 px, the section's text sampled every 25 ms (see below) |

What the headless reading saw:
- **A successful upload** goes from 100 % straight to "The archive is waiting to be imported.", with no frame of the "choose that file again" sentence.
- **With every close cut for a second**, the section holds at 100 % and sends the close again 5 s later. The `409` then hands over to the pending import.
- **A refused cancel** shows "That could not be done. Try again in a moment." under the running progress bar.
- **At 380 px**, nothing overflows horizontally.

The refused-opening case (`IMPORT_ALREADY_IN_PROGRESS`) passed on first run. That finding was a missing test, not a bug.

`contract/openapi.json` is unchanged; the gate compares it with the committed document.

## Tier-1 fixes: the holistic review's findings

| Severity | Finding | Exit |
|---|---|---|
| MAJOR | A stopped upload outranked the server's row, with Cancel as its only control; a lost close became a stop while the import could be running, and Cancel would delete it. Same for the losing tab of the two-tabs case | Fixed as described above. Journey case: a close whose answer is lost |
| MINOR | A successful upload flashed "The upload of pinry.zip stopped before the end" for one round trip | Fixed. The first upload journey asserts, with a `MutationObserver`, that the sentence never shows |
| MINOR | A refused cancel from the upload view was silent: the component holding the mutation had unmounted | Fixed. Journey case: a cancel refused during an upload |
| MINOR | A read of the latest import already under way could delete the fresh import's resume record (window refocus when the file dialog closes) | Fixed in `pruneRecords` |
| MINOR | An `ImportsConfig` comment change reached `api/` in a web application block (#222) | **Kept**, recorded in the handoff. See "Partly wrong findings" below |
| MINOR | The journeys' row fixtures accepted any string as a state | Fixed: `exportRow` and `importRow` are typed by the contract |
| MINOR | Empty list routes made redundant by #227's default handlers | Fixed in the three journeys named, and in the four data journeys that carried the same |
| MINOR | No test for a refused opening of an import | Fixed. Journey case: an import already running, nothing sent |
| MINOR | Formatting churn in the message catalogues | Fixed |
| MINOR | Comments cited "decision D1" or "section 2" without naming the document | Fixed: the references are dropped, each sentence already says why |
| MINOR | The specification contradicted its own corrections | Fixed: three `(Corrected: ...)` entries, in the Branches header, section 5 and section 6 |
| MINOR | `maxImportArchiveBytes` had no KDoc | Fixed |

**Partly wrong findings**:
- **`ImportsConfig`**: #222's 264 lines do count the comment (2 lines, 1 file). The review suggested keeping it, and so it stays.
- **KDoc**: `maxFileBytes` and `maxPixels` have no KDoc either. Only `maxImportArchiveBytes` got one, as the finding asked.

## Tier-2 questions

None in this block. Two operator decisions from earlier in the lot are recorded in the handoff here:
- **"Q1"**: two tabs uploading one import at once is an accepted known limit, with no guard. `docs/backlog.md` now points to it from Known limits.
- **"Lance la revue holistique avant que je relise"**: the holistic review ran on the top of the stack, before any merge.

## Departures from the block table

- **This block is not in section 5 of the specification.** It is the closing block, `fix/the-data-travels-closes`, on 65. The specification's Branches header now names it.
- **Wrap ran before the merges.** Wrap normally runs the holistic review over `main` after the last merge. This time it read `origin/feat/the-task-centre-keeps-data-notices` in place of `origin/main`.
- **Some of the handoff is left to fill.** The stack experiment's figures (wall-clock time, review latency, re-triggered runs) and the operator's reading only exist after the operator reviews. They must be filled before this pull request merges and the handoff freezes.

## Pitfalls

- **Firefox resends a POST cut on a reused connection by itself**, up to four times within the same millisecond. A stub that cuts only the first close tests the browser's retry, not the application's. The headless reading cut every close for a second for that reason.
- **The two-tabs case is still unguarded.** The losing tab now shows the server's import rather than a Cancel over it. But two upload loops can still write to one import, and `appendChunk` takes no lock (specification, section 9).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
