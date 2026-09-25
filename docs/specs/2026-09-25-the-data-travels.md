# The data travels

Date: 2026-09-25
Status: Draft, for the specification review and then the operator. Frozen when the lot's closing
block merges.
Branches: one stack, each block on the branch below it: 10 `feat/the-data-states-are-declared`,
20 `feat/the-export-is-reachable`, 30 `feat/the-import-uploads`, 40
`feat/the-upload-resumes-after-reload`, 50 `feat/the-import-reports`, 60
`feat/the-task-centre-carries-data-tasks`.
ADRs: none. The contract gains declarations on two existing conventions and no new one: the
handshake already publishes what a client checks before sending (`LimitsDto`), and a state published
as an enum already has a presentation twin (`DownloadStatusDto`). No library, no boundary, no error
contract moves; what the browser stores is a per-browser convenience, as the theme already is.

`docs/specs/2026-09-22-the-account-is-reachable.md` took account management and left import and
export, "a machine with states and a resumable upload". This lot takes both, and it is the first
run as a stack of pull requests (section 8).

## 1. Goal

From the account screen, a signed-in user can export their data and download the archive, and
import an archive, whose upload survives a network cut, a change of screen and a reload of the page.
The task centre shows both while they run and says when they end.

## 2. What exists today

- **Twelve operations under `/api/v1/me/exports` and `/api/v1/me/imports`**, none called by the web
  application (`grep -rn "me/exports\|me/imports" clients/apps/webapp/src` is empty).
- **An export** is requested with `X-Reauthentication` (`MeExportController.requestExport`), then
  moves `PENDING` to `READY` or `FAILED`; a `READY` one later becomes `EXPIRED`, `DELETED` or
  `SUPERSEDED` (`UserDataExportState`). Retention is `exports.retention`, 7 days, and a new request
  inside `exports.minimum_interval`, 1 hour, is refused (`ExportsConfig`). Refusals of the request:
  `EXPORT_ALREADY_IN_PROGRESS` (409), `EXPORT_TOO_SOON` and `TOO_MANY_AUTHENTICATION_ATTEMPTS` (429),
  `REAUTHENTICATION_FAILED` (403), `UNSUPPORTED_REAUTHENTICATION_FACTOR` (400).
- **The archive is served with `Content-Disposition`** and a file name
  (`MeExportController.contentDispositionHeader`). The web application authenticates with the
  `pinry_session` cookie (`docs/adr/0026-one-session-two-transports.md`,
  `CookieAuthenticationMechanism`), so a plain link downloads it: the browser streams it to disk and
  no script holds it.
- **An import** is opened by `POST` (`AWAITING_ARCHIVE`), fed by `PUT .../archive?offset=` chunks,
  closed by `POST .../archive/complete` (`PENDING`), then `RUNNING`, then `COMPLETED` or `FAILED`.
  `DELETE` cancels it (`CANCELLED`) and keeps what it already created; an upload idle for
  `imports.upload_grace`, 24 hours, becomes `ABANDONED` (`ReapUserDataImports`).
- **A chunk is written as it is read** (`FilesystemZipImportArchiveStore.append`), and the row's
  `uploadedBytes` is saved only once the chunk ends (`UserDataImportChunkReceiver.receive`). A chunk
  cut mid-body therefore leaves the file longer than `uploadedBytes` says. The file length is the
  truth, and the `409 IMPORT_CHUNK_OFFSET_MISMATCH` carries it as `currentLength`, which
  `ProblemDetail` already declares.
- **The chunk size and the archive bound are published nowhere.** `imports.max_chunk_bytes`
  (16 MiB) and `imports.max_archive_bytes` (20 GiB) are in `ImportsConfig`, whose comment says the
  first "records the size a client is told to send"; `LimitsDto` carries the image limits alone.
- **The report**: `GET .../issues` pages the anomalies, eight `UserDataImportIssueKind`s. Past
  `imports.report_detail_limit`, 500, only `issueCount` grows and `issueDetailTruncated` says so.
- **`state` and `kind` are published as plain strings** in `UserDataExportOutputDto`,
  `UserDataImportOutputDto` and `UserDataImportIssueOutputDto` (`contract/openapi.json`). `failureCode`
  is a string too, several producers writing it (`EXPORT_INTERRUPTED`, `IMPORT_FAILED`, ...).
- **The task centre** lists image downloads, `PENDING` or `FAILED`, polls while one is `PENDING`
  (`lib/downloads.ts`), and shows nothing for a success, its result being the pin
  (`docs/specs/2026-09-10-web-application.md`, 4.8). `AppNav` renders it on every guarded screen.
- **`/account`** holds the password form and the dangerous part (`routes/Account.tsx`); deleting
  the account already asks the password in a dialog and sends it through `passwordFactor`
  (`lib/reauthentication.ts`).

## 3. The decisions

Each letter is the question the operator answered on 2026-09-25 in Discuss.

**A. Import and export in one lot, export first.** The export lays down the account screen's data
sections and produces the archive the import consumes.

**E. Two sections on `/account`**, Export and Import, below the password form and above the
dangerous part. No route of their own: the account screen is small.

**I. The contract declares `state` and `kind` as enums**, as presentation twins of the domain enums
(`UserDataExportStateDto`, `UserDataImportStateDto`, `UserDataImportIssueKindDto`), the shape
`DownloadStatusDto` already has. The client then handles every value or does not compile.
`failureCode` stays a string, mapped to a sentence with a general fallback, as `downloadReasons.ts`
maps `reasonCode`.

**I'. The handshake publishes the import's two bounds**, `maxImportChunkBytes` and
`maxImportArchiveBytes` in `LimitsDto`, read from `ImportsConfig` as the image limits are read from
`ImagesConfig`. The client cuts chunks at the first and refuses a file past the second before
opening an import. Hard-coding a chunk size in the bundle would drift from a deployment that lowers
the key. This is the lead's addition to decision I, submitted to the operator with this document.

**J. The account screen shows the latest export and the latest import only**, read as the first row
of each list (`pageSize=1`), which is newest first. No history.

- Export: nothing yet, or `EXPIRED`, `DELETED`, `SUPERSEDED` as the latest: the request button.
  `PENDING`: "being prepared". `READY`: a download link, the size, the expiry date, and a delete
  button. `FAILED`: the `failureCode`'s sentence and the request button.
- The request opens a dialog asking the password, as deleting the account does, and each refusal
  code above has its sentence; a code this bundle does not know gets the general one.
- Import: the file picker when no import is active. While active, its state and a cancel button
  behind a confirmation saying that what is already created stays. `RUNNING` shows
  `processedPins` of `announcedPins`. A terminal import shows its counters (decision H).

**F4. The upload lives above the router**, in a module-level store the section and the task centre
both subscribe to (`useSyncExternalStore`). Changing screen leaves it running; closing or reloading
the tab asks for confirmation through `beforeunload` while bytes remain. A React provider in
`main.tsx` was the alternative: every journey would then have to mount it, and the store needs no
tree.

The upload loop, one chunk at a time:

1. `PUT` the slice `[offset, offset + maxImportChunkBytes)` of the file at `offset`.
2. `200`: `offset` becomes the answered `uploadedBytes`; past the file's size, `POST .../complete`.
3. `409 IMPORT_CHUNK_OFFSET_MISMATCH`: `offset` becomes `currentLength`, and the loop goes on.
   This is how a cut chunk resumes, section 2 saying why the row's own figure can lag.
4. A fetch that never reached the API, or a `5xx` other than `507`: wait, and retry the same
   offset through step 3's reconciliation. Five attempts, 5 seconds apart, then the upload pauses
   and offers a resume button.
5. Any other refusal stops the upload with its sentence: `IMPORT_ARCHIVE_TOO_LARGE`, a bodyless
   `413` (a reverse proxy's lower limit, section 9), `IMPORT_INSUFFICIENT_STORAGE`,
   `IMPORT_NOT_AWAITING_ARCHIVE`.

The decisions of steps 2 to 5 are a pure function in `src/lib/`, inside the coverage bound; the
store performs what it answers.

**D1 and D'2. After a reload, the same file is chosen again and the upload resumes.** When an
import is opened, the store writes `{ name, size, lastModified }` of the file to `localStorage`
under the import's id. After a reload the section finds the active import in `AWAITING_ARCHIVE`
with no upload in the store, shows how much was sent, and asks for the file again. A file whose
three fields differ is refused with a sentence and nothing is sent; one that matches resumes at the
row's `uploadedBytes`, step 3 correcting it if the file on the server is longer. No hash: it would
read the whole file, `crypto.subtle.digest` does not stream, and the check guards against a mistake,
not an adversary. The record is deleted when the import leaves `AWAITING_ARCHIVE`.

**H. The report**: the counters in the section and in the task centre's notice; a button "See the
N issues" in the section opens a dialog paging `GET .../issues` with a "load more", each issue by
its kind's sentence, its line and its subject. When `issueDetailTruncated`, the dialog says that
only the first issues are listed.

**F4 and G3. The task centre carries the data tasks.** Beside the image downloads it lists:

| Task | Shown while |
|---|---|
| The upload, with bytes sent of the file's size | the store holds one |
| The import on the server, `PENDING` or `RUNNING`, with `processedPins` of `announcedPins` | active and no upload in the store |
| The export being prepared | `PENDING` |
| The end of an import: `COMPLETED` with its counters, `FAILED`, `ABANDONED` | until dismissed, and at most 24 hours after `completedAt` (`requestedAt` for `ABANDONED`, which has no completion) |
| The end of an export: `READY` with the download link, `FAILED` | until dismissed, and at most until `expiresAt` (24 hours after `completedAt` for `FAILED`) |

A cancellation is the user's own gesture and leaves no notice. Dismissals are ids kept in
`localStorage`: another browser shows the notice once more, which is harmless. Which notices show
is a pure function of the rows, the dismissed ids and the current time passed in, in `src/lib/`.

**This departs from 4.8 of `docs/specs/2026-09-10-web-application.md`**, where a success leaves
nothing in the task centre. A download's success is the pin appearing; an export's or an import's
has nowhere else to show once the user has left the account screen.

**Polling.** The latest export and the latest import are two queries shared by the section and the
task centre, polled while `PENDING` or `RUNNING`, on the interval `lib/downloads.ts` uses.

**K. `Import follow-ons` stays open** (section 6).

## 4. The change

Under `api/`, block 10 alone:

- `dtos/output/`: the three enum twins; `UserDataExportOutputDto.state`,
  `UserDataImportOutputDto.state` and `UserDataImportIssueOutputDto.kind` typed by them, their mappers
  mapping exhaustively.
- `HandshakeOutputDto.LimitsDto` gains the two import bounds; `HandshakeController` reads
  `ImportsConfig`.
- `quarkus.smallrye-openapi.info-version` raised as `dagger call contract-guard` requires, a minor
  expected (a response narrowed to an enum and a response gaining required fields), and
  `contract/openapi.json` regenerated.

Under `clients/apps/webapp/src`:

- `exports.ts` and `imports.ts`: the queries and mutations, as `me.ts` is the account's; `imports.ts`
  also holds the upload store.
- `lib/imports.ts`: the upload loop's decisions, the file identity comparison, the retry schedule.
- `lib/notices.ts`: which data notices show (decision G3).
- `dataRefusals.ts`, `dataFailures.ts`, `importIssues.ts`: code to sentence tables, at `src/` because
  they import the catalogue (`clients/AGENTS.md`).
- `routes/Account.tsx` and one component per section under `components/`, plus the issues dialog.
- `components/TaskCentre.tsx`: the data tasks, and the count on its badge including them.
- `lib/journeys.ts`, and `messages/{en,fr}.json`.

## 5. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/the-data-states-are-declared` | A controller test per enum: a row in each domain state serialises to that state's name, and the contract declares `enum` on the three fields with exactly the domain's values, a test comparing `UserDataExportState.entries` and its twins so a domain value added later fails the build. `GET /api/v1/handshake` answers `maxImportChunkBytes` and `maxImportArchiveBytes` equal to a test profile's overridden `imports.*` values, not the defaults, which is what discriminates from constants. `dagger call contract-guard` green with the raised version. This block carries the specification |
| 20 | `feat/the-export-is-reachable` | Journey **export the account's data and download it**: the dialog sends `POST /api/v1/me/exports` with `X-Reauthentication`; the section reads "being prepared" while the latest row is `PENDING`, polls, and on `READY` shows a link whose `href` is `/api/v1/me/exports/{id}/download` and the expiry date; the delete button sends `DELETE` and the request button comes back. `EXPORT_TOO_SOON` and `TOO_MANY_AUTHENTICATION_ATTEMPTS`, both 429, produce two different sentences; an unknown code the general one. A latest row `SUPERSEDED` shows the request button and no link |
| 30 | `feat/the-import-uploads` | Journey **import an archive**, with a handshake publishing a chunk of 4 bytes and a 10-byte file: three `PUT`s at offsets 0, 4 and 8 carrying the right slices, then one `complete`; a `PUT` answered by a network error then retried, and a `409` with `currentLength` 6 answered to the offset-4 `PUT`, make the next `PUT` start at 6 and not at 4 or 8. A file past `maxImportArchiveBytes` is refused with no request sent. Navigating to `/` mid-upload and back finds the upload still advancing. Cancel asks, then sends `DELETE`. `lib/imports.ts` at 100 % |
| 40 | `feat/the-upload-resumes-after-reload` | Journey **resume an import after reloading**: a fresh application (empty store) facing an `AWAITING_ARCHIVE` import with `uploadedBytes` 4 and a matching `localStorage` record asks for the file; the same file resumes with a `PUT` at 4; a file of another size is refused with nothing sent. The record is gone once the import reads `PENDING` |
| 50 | `feat/the-import-reports` | Journey **read an import's report**: a `COMPLETED` import shows its counters; the dialog lists a first page, "load more" sends the answered cursor and appends; each of the eight kinds has its own sentence; `issueDetailTruncated` true shows the truncation sentence and false does not |
| 60 | `feat/the-task-centre-carries-data-tasks` | Journey **an upload and an export surfacing in the task centre**: the upload's progress shows on `/` while it runs; a `READY` export shows a notice with its link, dismissing it hides it and it stays hidden after a remount; `lib/notices.ts` hides a notice past its bound given a time past it and shows it given a time before, at 100 %. A `CANCELLED` import shows nothing |

Each block is green and coherent alone: 20 needs nothing above it; 30 reads the bounds 10 publishes;
40, 50 and 60 read what 30 built. Block 60 deletes the backlog item this lot closes.

**Block 30 is the one most likely to pass 500 lines.** Its seam, declared now: the store and
`lib/imports.ts` with their unit tests on one side, whose consumer is the section on the other side
with the journey, the section arriving in the next block. Each block measures its budget once
committed, against its parent branch (section 8).

## 6. Adjacent backlog items

| Item | Exit |
|---|---|
| **What the API serves and the web application does not reach yet** (Features) | Deleted in block 60's pull request: import and export are what it had left |
| **Import follow-ons** (`P1`) | Left open (decision K): selective import and partial export, merging onto an existing pin, and a pin with no medium travelling each change what the API does and need their own design; this lot reaches what the API does today |
| **Browser-extension CORS origin** (`P1`) | Not adjacent: nothing here touches the origin list |
| **A table rebuild's row-carrying path is exercised by nothing**, **`foreign_keys` is off** (`P2`) | Not adjacent: block 10 touches the presentation layer, no migration |
| **Populate `contract/frozen/`** (Before beta) | Untouched: the contract moves by a minor, and the alpha freezes nothing |
| **The grid keeps every page it scrolls** (Known limits) | Not adjacent: no grid here. The issues dialog pages the same way, bounded by the report's 500 rows |

## 7. Out of scope

| Not done | Observable |
|---|---|
| Export and import history | Both lists are read with `pageSize=1` only |
| A byte-range download of the export | The link is a plain `href`; no client code sends `Range` |
| Parallel chunk uploads | One `PUT` in flight at a time in every journey |
| Hashing the chosen file | No `crypto.subtle` call under `clients/` |
| Filtering the report by kind | `GET .../issues` gains no parameter |
| Anything the import or export does on the server | Block 10 aside, no path under `api/` changes |
| The extension | `clients/apps/extension` absent |

## 8. How this lot runs: stacked pull requests

An experiment the operator asked for on 2026-09-25, departing from Act and Integrate in
`agents/workflow.md` for this lot only. The handoff records what it gained and where it rubbed, for
the operator to decide whether it becomes the rule.

- **Each block's branch starts from the previous block's**, and its pull request targets that
  branch. `gh stack` (github/gh-stack v0.1.1) creates and links them: `gh stack add`, `gh stack submit`.
- **The next block starts once the previous one's local gate is green and its pull request is open
  as a draft**, not once it has merged (answer C1). A red run or a review comment sends that block
  back to Verify; the lead then cascades the fix upward with `gh stack rebase --upstack` and pushes.
- **One teammate works at a time, and a block's teammate stays idle rather than stopped** until its
  pull request merges, so a comment reaches the agent that wrote the code.
- **Only the lead rewrites the stack.** Teammates commit on their own branch and never rebase.
- **The budget is measured against the parent branch**: the command of `agents/workflow.md` with
  `<parent>...HEAD` in place of `main...HEAD`.
- **The operator merges after reviewing**, pull request by pull request or with `gh stack merge`,
  rebase only. The closing block and the lot's tag come after the last merge, as usual.
- **The handoff is written from the pull request bodies, open or merged.**

## 9. Pitfalls

- **The row's `uploadedBytes` can lag the file**, section 2. Resuming from it alone sends a `PUT` at a
  stale offset; the `409` corrects it, and a loop that treats that `409` as fatal never resumes a cut
  chunk.
- **A reverse proxy in front of the API can refuse a chunk under 16 MiB** with a bodyless `413`.
  The bundled `nginx.conf` serves the static files only and proxies nothing; a deployment's own proxy
  is where `client_max_body_size` bites.
- **`File.slice` does not read the file**; the bytes leave only when `fetch` sends the `Blob`. A test
  asserting the bodies must read them from the request MSW receives.
- **`beforeunload` shows the browser's own sentence**, whatever the page sets, and only after a
  user gesture on the page.
- **A `204` leaves `data` undefined** (`openapi-fetch` 0.17.0): the export's and the import's
  `DELETE` are read from `response.ok`.
- **The store outlives a journey**: module state survives between tests in the same file, so the
  test setup resets it.
- **Every message exists in both locales, and none carries a long dash** (`pre-commit`).
