feat(webapp): the archive uploads

Block 37 of the lot `the-data-travels` (`docs/specs/2026-09-25-the-data-travels.md`), branch `feat/the-archive-uploads`.

**Stacked on #223** (block 34, `feat/the-import-is-followed`), itself on #222 (block 30, the upload store and its pure decisions). Review and merge those first; the next block up is 40, resuming after a reload.

## What it does

The import section gets its file picker and its upload view, on top of the store #222 built:

- **Choosing an archive**: a picker, disabled until the handshake has answered, refuses a file past `maxImportArchiveBytes` before any request, then opens the import and hands the file to the store with `maxImportChunkBytes` as the chunk size.
- **While an upload runs**: a progress bar of bytes sent over the file's size and the cancel button. Paused after five lost attempts, it offers "Resume the upload"; stopped by a refusal, it shows that refusal's sentence.
- **The picker comes back** after a `COMPLETED`, `FAILED`, `CANCELLED` or `ABANDONED` import, and when there is none.
- **`importRefusal`** in `dataRefusals.ts` maps the refusals of the three upload operations, plus the two stops `lib/imports.ts` names itself (`CHUNK_TOO_LARGE`, a proxy's bodyless `413`; `ARCHIVE_LONGER_THAN_FILE`), to a sentence, with the general one for any other code. `hasSentence` now takes its table, shared with `exportRefusal`.
- **Eleven messages** in both locales.

The journey `import an archive` covers the rest of the block table's row 30: three `PUT`s at 0, 4 and 8 with `application/octet-stream` and the right slices, then one `complete`; a lost request retried and a `409` with `currentLength` 6 resuming at 6; a `currentLength` of 12 stopping with no `complete`; five lost requests pausing behind the resume button; a file past the bound refused with nothing sent; the upload advancing while the account screen is unmounted, and `beforeunload` held only while bytes remain.

## Evidence

| Check | Result |
|---|---|
| Gate | `dagger call gate` green at `08412ddd`, exit 0 |
| Continuous integration | run 36149285625 green, `verify` 6 min 41 s |
| Budget, against `feat/the-import-is-followed` | 367 lines over 7 files (under 500 and 20) |
| Headless reading | Firefox over WebDriver BiDi, the bundle against a stubbed API that cuts sockets or answers `507` on demand, at 1280 and 380 px |

The headless reading covered uploading at 52 %, the cancel confirmation during an upload, the pause after the five attempts (23 s real), the stop on a `507`, and a 60 MB file against a 50 MB bound. No horizontal overflow; nothing needed fixing.

## Tier-1 fixes

None.

## Tier-2 questions

One, carried by #222: block 30 measured 892 lines over 17 files, and the operator answered "b" on 2026-09-25, splitting it into 30, 34 and 37. Together the three differ from the single block only by #223's two display fixes and this journey's `openTheAccount` taking its answer as an option.

## Pitfalls

- **jsdom's `Blob` does not survive Node's `fetch`**: a slice of a jsdom `File` reaches MSW as the text `"undefined"`, so the journey builds its archive with `node:buffer`'s `File`. The browser is unaffected.
- **The retry wait is real time**: the journeys that retry use fake timers with `shouldAdvanceTime` and advance by `RETRY_MS`; `user-event` is told to advance them only when they are faked.
- **A stopped upload leaves the import `AWAITING_ARCHIVE` on the server**: the section then offers cancel and no picker. Block 40 adds resuming with the same file.
- **Not measured**: whether one `PUT` at a time with a 5-second wait suffices against a half-open connection (specification, section 9). The handoff carries it under what is not validated.

## Departures from the block table

This block is the rest of block 30's row once #223 took the cancel case: the file picker, the upload view and the journey's upload cases. The table's row 30 records the split.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
