fix: the data travels, the closing block

**Stacked on #228** (block 65, `feat/the-task-centre-keeps-data-notices`). This is the top of a stack of ten pull requests, and none of them has merged yet. This is the closing block of the lot `the-data-travels`. It fixes what the holistic review found (1 MAJOR, 11 MINOR), corrects the handoff, reconciles the backlog and resolves three contradictions in the specification. At the operator's request, the review ran on the top of the stack before any merge.

## Evidence

| Check | Result |
|---|---|
| `dagger call gate` | Green at `6de14f7b`, exit 0. Clients: 55 files, 280 tests. `src/lib` coverage at 100 % of lines and branches |
| Continuous integration | Run 36160705746, green, `verify` 7 min 49 s |
| Budget against `feat/the-task-centre-keeps-data-notices` | 278 lines, 18 files, under the bound of 500 lines and 20 files |
| Tests red before the fix | The three new behaviour cases (the flash, the lost close, the refused cancel) failed against the unfixed store. The refused-opening case already passed, so that finding was a coverage gap and not a defect |
| Headless reading | Firefox with a stubbed API, at 1280 and 380 px, sampling the section's text every 25 ms. See below |

What the headless reading showed:
- **A successful upload** goes from 100 % straight to "The archive is waiting to be imported." The reload sentence never shows, not even for one frame.
- **Every close cut for one second**: the section stays at 100 % and sends the close again 5 s later. The `409` then hands over to the pending import.
- **A refused cancel** shows "That could not be done. Try again in a moment." under the progress bar, which keeps running.
- No horizontal overflow at 380 px.

## Tier-1 fixes: the holistic review

The review is `.reviews/the-data-travels-holistic.md` (not committed). The handoff records each finding with its exit under "The holistic review".

**MAJOR: a stopped upload blocked the server's row, and Cancel could delete a running import.**
- *The problem.* Once an upload stopped, the store kept it, and it took precedence over the server's row in the section and in the task centre, with Cancel as its only control. If a close request reached the server but its answer was lost, the client treated that as a stop, even though the import could already be `PENDING` or `RUNNING` on the server. Cancel would then `DELETE` it. The tab that lost the two-tabs case ended up in the same state.
- *The fix.* In `clients/apps/webapp/src/lib/imports.ts`, the close is now an `Attempt` like a chunk. A close that gets no answer is retried on the chunks' schedule (5 attempts, then a pause). `IMPORT_NOT_AWAITING_ARCHIVE`, answered to a chunk or to the close, now means the server has moved on: `nextStep` returns `DONE`, and the upload gives way to the server's row once that row is read.
- *The test.* A new journey case, "a close whose answer is lost", plus unit cases for `DONE` and for the close's retry.

| MINOR finding | Exit |
|---|---|
| A successful upload showed "The upload of pinry.zip stopped before the end" for one round trip | Fixed. The upload leaves the store only after the latest import has been read again (`send` awaits `settle` before `publish(null)`). The first upload journey now uses a `MutationObserver` to check that the sentence never shows |
| A refused cancel from the upload view said nothing, because the component holding the mutation had unmounted | Fixed. `useCancelImport` drops the upload only once the `DELETE` succeeds and the row has been read again. New journey case: a cancel refused during an upload |
| A read of the latest import already under way could delete the record of an import that had just opened (for example on window refocus after the file dialog closes) | Fixed. `pruneRecords` keeps this tab's own upload's record, and a finished upload removes its record itself (`forgetRecord`) |
| An API comment change (`ImportsConfig`) arrived in a web application block | **Kept**, as the review itself suggests. `eef0c88f` moved it out of block 10 to stay under the file bound, and `ddabed5a` (block 30, #222) brought it back. The finding was partly wrong: #222's 264 lines and 4 files do count it |
| The journeys' row fixtures accepted any string as a state | Fixed. `exportRow` and `importRow` in `src/test/app.tsx` are now typed by the contract |
| Empty list routes made redundant by #227's default handlers were left in three journeys | Fixed. Removed from those three journeys and from the four data journeys that had the same routes |
| No test covered a refused opening of an import | Fixed. New journey case: an import already running refuses the opening, and nothing is sent |
| Formatting churn in the message catalogues | Fixed (missing space after the colon) |
| Comments cited "decision D1" or "section 2" without naming the document | Fixed. The references are gone, since each sentence already says why |
| The specification contradicted its own corrections | Fixed with three `(Corrected: ...)` entries: the Branches header, section 5 and section 6 |
| `maxImportArchiveBytes` had no KDoc | Fixed. The finding was partly wrong: `maxFileBytes` and `maxPixels` have no KDoc either, and are left as they are |

Because `IMPORT_NOT_AWAITING_ARCHIVE` no longer stops an upload, its sentence (`import_not_awaiting`) has been removed from both catalogues and from `dataRefusals.ts`.

## Tier-2 questions

None in this block. Two earlier operator decisions shape it, and the handoff now records both:
- **"Q1"**: two tabs uploading one import's archive at the same time is accepted as a known limit, with no guard. `docs/backlog.md` gains a pointer to it under Known limits.
- **"Lance la revue holistique avant que je relise"**: the holistic review ran on the top of the stack, before any merge. It diffed against `origin/feat/the-task-centre-keeps-data-notices` rather than `origin/main`.

## Departures

- **Wrap order.** The holistic review and this closing block come before the merges instead of after the last one, at the operator's request. As a result, the handoff's merge figures (wall-clock time, review latency, rebases the merges triggered) and the operator's own reading are **not filled in yet**. They can only exist after the operator reviews and merges the stack, and they will be filled in before this pull request merges and the handoff freezes.
- **Decision F4 of the specification, step 5, still lists `IMPORT_NOT_AWAITING_ARCHIVE` among the refusals that stop an upload**, and step 4 speaks only of chunks. The MAJOR fix changes both: that code now hands over to the server's row, and the close is retried like a chunk. The handoff records the new behaviour, but this block does not correct the specification on that point.
- The closing block is not in the specification's block table. Its branch, `fix/the-data-travels-closes`, is now named in the Branches header's correction.

## Pitfalls

- **Firefox resends a POST that was cut on a reused connection**, on its own, up to four times within the same millisecond. A stub that cuts only the first close is therefore testing the browser's retry, not the application's. The headless reading cut every close for a full second for this reason.
- **A test that only checks the final screen cannot see a flash.** A `findBy` passes once the last state is correct, which is why the success case uses `watchFor`, a `MutationObserver` that records whether the text ever appeared.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
