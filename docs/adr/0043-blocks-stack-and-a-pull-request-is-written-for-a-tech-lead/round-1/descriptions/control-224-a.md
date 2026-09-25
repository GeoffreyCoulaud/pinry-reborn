feat(webapp): the archive uploads

**Stacked on #223** (block 34, `feat/the-import-is-followed`), itself on #222 (block 30, the upload store and its pure decisions). Review and merge those first; the diff below is against #223's branch. Next block up: 40, resuming after a reload.

## What this block does

Block 37 of the lot `the-data-travels`, the last of the three blocks block 30 was split into. From the account screen, a user can now pick an archive and upload it: the file is sent in chunks, survives a network cut and a change of screen, and the page asks before closing while bytes remain.

- **File picker** (`ImportSection.tsx`, `ChooseArchive`): shown when there is no import, or when the latest one is `COMPLETED`, `FAILED`, `CANCELLED` or `ABANDONED`. It stays disabled until the handshake publishes the chunk size, and a file over `maxImportArchiveBytes` is refused before any request is sent.
- **Upload view** (`Uploading`): a progress bar of bytes sent. It shows a resume button once the upload has paused after five failed attempts, the refusal sentence once it has stopped, and the cancel confirmation from #223. The section shows it whenever the store holds an upload, whatever the server row says, because the upload lives above the router.
- **Refusal sentences** (`dataRefusals.ts`, `importRefusal`): one sentence per refusal code of the three import operations, plus the two stop reasons `lib/imports.ts` produces itself (`CHUNK_TOO_LARGE`, `ARCHIVE_LONGER_THAN_FILE`). Any other code gets the general sentence. `hasSentence` is now generic over the table, so the export and the import share it.
- 11 new messages in `en.json` and `fr.json`. The test `handshakeRoute` now publishes the two import bounds.

## Evidence

| Check | Result |
|---|---|
| Gate | `dagger call gate` green at `08412ddd`, exit 0 |
| Continuous integration | run 36149285625 green, `verify` 6 min 41 s |
| Budget against `feat/the-import-is-followed` | 367 lines, 7 files (bounds: 500 lines, 20 files) |
| Headless reading | Firefox over WebDriver BiDi, bundle against a stubbed API that cuts sockets or answers 507 on demand, at 1280 and 380 px |

The headless reading covered: uploading at 52 %, the cancel confirmation during an upload, paused after the five attempts (23 s of real time), stopped by a 507, and a 60 MB file against a 50 MB bound. There was no horizontal overflow, and nothing needed fixing.

The journey **import an archive** covers this block's part of block 30's row:

| Row requirement | Test |
|---|---|
| 4-byte chunk, 10-byte file: `PUT`s at 0, 4, 8 with `application/octet-stream` and the right slices, then one `complete` | "Given a 4-byte chunk" |
| A network error then a retry; a `409` with `currentLength` 6 on the offset-4 `PUT` resumes at 6 | "Given a lost request and a cut chunk" (offsets `[0, 0, 4, 6]`, last body `6789`) |
| `currentLength` 12 stops with its sentence, no `complete` | "Given a server holding more than the file" |
| Five network errors pause behind a resume button, which sends the next `PUT` | "Given five lost requests in a row" |
| A file past `maxImportArchiveBytes` is refused, nothing sent | "Given a file past the deployment's bound" |
| Navigating to `/` and back finds the upload advancing; `beforeunload` held while bytes remain and released after | "Given an upload, Then it outlives the screen" (the chunk at 8 leaves while the account screen is unmounted) |

`dataRefusals.test.ts` checks that `importRefusal("constructor")` falls back to the general sentence rather than reading `Object.prototype`.

## Tier-1 fixes

None.

## Tier-2 questions

- **Splitting block 30 in three** (30, 34, 37) after it measured 892 lines over 17 files. The operator answered "b" on 2026-09-25. This is recorded in #222 and in the specification's section 5.

## Departures from the block table

- **This block is not a row of section 5's table as first written.** It is the rest of block 30's row after #223 took the section's read side and the cancel case. Together, the three blocks differ from the single 892-line block only by #223's two display fixes and by this journey's `openTheAccount` taking its answer as an option.
- `lib/imports.ts` at 100 % belongs to block 30 (#222), not here. This block adds no code under `src/lib/`.

## Pitfalls

- **jsdom's `Blob` does not survive Node's `fetch`.** A slice of a jsdom `File` reaches MSW as the text `"undefined"`, so the journey builds its archive with `node:buffer`'s `File`. Browsers are unaffected.
- **The retry wait is real time.** The retry journeys use fake timers with `shouldAdvanceTime` and advance by `RETRY_MS`. `userEvent.setup` advances fake timers only when they are on.
- **A stopped upload leaves the import `AWAITING_ARCHIVE` on the server.** The section then offers cancel and no picker until block 40 adds resuming with the same file.
- **Not measured:** whether one `PUT` at a time with a 5-second wait is enough against a half-open connection (specification, section 9). The server-side double append stays out of scope, and the handoff carries this as not validated.
- **The upload store is module state and outlives a journey.** `afterEach` calls `dropUpload()`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
