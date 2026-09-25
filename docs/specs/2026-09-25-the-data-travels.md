# The data travels

Date: 2026-09-25
Status: Draft for the operator. One specification review ran, `.reviews/the-data-travels-spec.md`,
its 2 CRITICAL, 5 MAJOR and 12 MINOR closed in this document; decision I was asked again on its
first CRITICAL. Frozen when the lot's closing block merges.
Branches: one stack, each block on the branch below it: 10 `feat/the-data-states-are-declared`,
20 `feat/the-export-is-reachable`, 30 `feat/the-import-uploads`, 40
`feat/the-upload-resumes-after-reload`, 50 `feat/the-import-reports`, 60
`feat/the-task-centre-carries-data-tasks`.
ADRs: none. The contract gains declarations on existing conventions: the handshake already
publishes what a client checks before sending (`LimitsDto`), and a state published as an enum
already has a presentation twin (`DownloadStatusDto`). The import bounds reach the presentation
layer the way `maxArchiveBytes` already reaches `UserDataImportChunkReceiver`, produced in
`api-application` (decision I'), so no module gains a dependency. What the browser stores is a
per-browser convenience, as the theme already is.

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
- **The report**: `GET .../issues` pages the anomalies, of the twelve `UserDataImportIssueKind`s
  (`sed -n '/^enum class/,/^}/p' .../enums/UserDataImportIssueKind.kt | command grep -cE '^    [A-Z_]+,'`),
  `LINE_REJECTED` being a catch-all. Past
  `imports.report_detail_limit`, 500, only `issueCount` grows and `issueDetailTruncated` says so.
- **`state` and `kind` are published as plain strings** in `UserDataExportOutputDto`,
  `UserDataImportOutputDto` and `UserDataImportIssueOutputDto` (`contract/openapi.json`). `failureCode`
  is a string too, nine producers writing it: `USER_GONE`, `DISK_FULL`, `BUILD_FAILED`,
  `EXPORT_INTERRUPTED`, `ARCHIVE_UNREADABLE`, `MANIFEST_MISSING`, `UNSUPPORTED_FORMAT_VERSION`,
  `IMPORT_INTERRUPTED`, `IMPORT_FAILED`. Only `COMPLETED` and `READY` stamp `completedAt`; no failure
  path does (`UserDataImportRunner`, `UserDataExportBuilder` and both reapers).
- **`ImportsConfig` lives in `api-worker-quarkus`**, which `api-presentation-quarkus` may not depend
  on (`ArchitectureKonsistTest`); `ImagesConfig` lives in the presentation module itself.
  `ImportProducers`, in `api-application`, already hands `maxArchiveBytes` to the use cases.
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

**I (answered I1'). The two `state`s are closed, `kind` and `failureCode` stay open.** A closed set
(`enum`) promises every value, so a value added later breaks a client that relied on it and is a
contract major; an open set (`string`) tells the client to expect values it does not know, so an
added one breaks nothing. What decides is what the client does with the value:

- **`state` drives behaviour**: whether to poll, which buttons to show, whether a link exists. An
  unknown state has no correct default, so it is closed, as presentation twins of the domain enums
  (`UserDataExportStateDto`, `UserDataImportStateDto`), the shape `DownloadStatusDto` already has.
  The client maps each through a `Record` keyed by the enum's union, so a state it does not handle
  fails `pnpm run typecheck`; a `switch` would not, the lint configuration carrying no
  exhaustiveness rule (`clients/eslint.config.js`).
- **`kind` and `failureCode` drive a sentence**, and an unknown one has a correct fallback: a
  general sentence, beside the `detail` and `subject` each issue already carries. They stay strings,
  mapped with a fallback as `downloadReasons.ts` maps `reasonCode`, for the reason 4.10 of
  `docs/specs/2026-09-10-web-application.md` kept `reasonCode` open: a closed `kind` would make every
  new anomaly a contract major, and after beta each served major is a frozen document to keep serving.

`oasdiff` v1.31.0 reads a response enum as closed: declaring the states is
`response-property-enum-value-added` on every response carrying them, so the contract goes to
`18.0.0` (measured by the specification review, `.reviews/the-data-travels-spec.md`, on all three
fields; each declared enum raises that error, so the two states alone suffice). The same question for the contract's other
string fields is filed rather than taken here (section 6).

**I'. The handshake publishes the import's two bounds**, `maxImportChunkBytes` and
`maxImportArchiveBytes` in `LimitsDto`. The client cuts chunks at the first and refuses a file past
the second before opening an import. Hard-coding a chunk size in the bundle would drift from a
deployment that lowers the key. They reach `HandshakeController` as a value class in
`api-usecases`, `ImportUploadBounds(maxChunkBytes, maxArchiveBytes)`, which `ImportProducers`
produces from `ImportsConfig`: the path `maxArchiveBytes` already takes to the chunk receiver, and
not a second `@ConfigMapping` on `imports.*` in the presentation module, which would declare each
default twice. This is the lead's addition to decision I, submitted to the operator with this
document.

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
2. `200`: `offset` becomes the answered `uploadedBytes`. Equal to the file's size: `POST .../complete`.
   Greater: the server holds bytes this file does not have, and the upload stops with a sentence.
3. `409 IMPORT_CHUNK_OFFSET_MISMATCH`: `offset` becomes `currentLength`, and the loop goes on.
   This is how a cut chunk resumes, section 2 saying why the row's own figure can lag. The same
   greater-than-the-file check as step 2 applies.
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
read the whole file, `crypto.subtle.digest` takes the whole buffer and does not stream
([MDN, `SubtleCrypto.digest()`](https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/digest)),
and the check guards against a mistake, not an adversary.

With no record (another browser, a private window, cleared storage), the check cannot run: the
section says the upload was started elsewhere and offers the cancel button only. Whenever the
latest import is read, every record whose id is not that import in `AWAITING_ARCHIVE` is deleted,
so a record outlives neither its import nor the state it serves.

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
| An import `COMPLETED`, with its counters | until dismissed, and at most 24 hours after `completedAt` |
| An export `READY`, with the download link | until dismissed, and at most until `expiresAt` |
| An import `FAILED` or `ABANDONED`, an export `FAILED` | until dismissed |

A failure has no time bound because the API publishes no instant to count from: no failure path
stamps `completedAt` (section 2), and an import is `ABANDONED` only once `imports.upload_grace` has
passed since its last activity, which `UserDataImportOutputDto` does not carry, so any bound on
`requestedAt` has always expired by then. Only the latest of each is read, so at most two such
notices wait. A cancellation is the user's own gesture and leaves no notice. Dismissals are ids kept in
`localStorage`, deleted with their row's notice once its bound passes: another browser shows the
notice once more, which is harmless. Which notices show
is a pure function of the rows, the dismissed ids and the current time passed in, in `src/lib/`.

**This departs from 4.8 of `docs/specs/2026-09-10-web-application.md`**, where a success leaves
nothing in the task centre. A download's success is the pin appearing; an export's or an import's
has nowhere else to show once the user has left the account screen.

**Polling.** The latest export and the latest import are two queries shared by the section and the
task centre, polled while `PENDING` or `RUNNING`, on the interval `lib/downloads.ts` uses.

**K. `Import follow-ons` stays open** (section 6).

## 4. The change

Under `api/`, block 10 alone:

- `dtos/output/`: the two enum twins; `UserDataExportOutputDto.state` and
  `UserDataImportOutputDto.state` typed by them, their mappers mapping exhaustively. `kind` untouched.
- `HandshakeOutputDto.LimitsDto` gains the two import bounds; `HandshakeController` reads
  `ImportUploadBounds` (`api-usecases`), which `ImportProducers` (`api-application`) produces.
- `quarkus.smallrye-openapi.info-version` raised to `18.0.0` (decision I), and
  `contract/openapi.json` regenerated.
- `docs/backlog.md`: the item section 6 files.

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
| 10 | `feat/the-data-states-are-declared` | A mapper test per twin, as `PinImageStateMapperTest` is written, covering every domain entry, so a domain value added later fails the build; the contract declares `enum` on the two `state` fields with exactly the domain's values, and `kind` is still `type: string`. `GET /api/v1/handshake` answers `maxImportChunkBytes` and `maxImportArchiveBytes` equal to a test profile's overridden `imports.*` values, not the defaults, which is what discriminates from constants. `dagger call contract-guard` green at `18.0.0` and red at `17.1.0`. This block carries the specification and files the backlog item of section 6 |
| 20 | `feat/the-export-is-reachable` | Journey **export the account's data and download it**: the dialog sends `POST /api/v1/me/exports` with `X-Reauthentication`; the section reads "being prepared" while the latest row is `PENDING`, polls, and on `READY` shows a link whose `href` is `/api/v1/me/exports/{id}/download` and the expiry date; the delete button sends `DELETE` and the request button comes back. `EXPORT_TOO_SOON` and `TOO_MANY_AUTHENTICATION_ATTEMPTS`, both 429, produce two different sentences; an unknown code the general one. A latest row `EXPIRED` shows the request button and no link. A `FAILED` row with `failureCode` `DISK_FULL` shows that code's sentence, and one with an unknown code the general failure sentence and not `undefined` |
| 30 | `feat/the-import-uploads` | Journey **import an archive**, with a handshake publishing a chunk of 4 bytes and a 10-byte file: three `PUT`s at offsets 0, 4 and 8, each with `Content-Type: application/octet-stream` and the right slice as its body, then one `complete`; a `PUT` answered by a network error then retried, and a `409` with `currentLength` 6 answered to the offset-4 `PUT`, make the next `PUT` start at 6 and not at 4 or 8; a `currentLength` of 12 stops the upload with its sentence and sends no `complete`. Five network errors in a row pause the upload behind a resume button, which sends the next `PUT`. A file past `maxImportArchiveBytes` is refused with no request sent. Navigating to `/` mid-upload and back finds the upload still advancing. A `beforeunload` listener is registered while the store holds bytes to send and removed once it holds none. Cancel asks, then sends `DELETE`. `lib/imports.ts` at 100 % |
| 40 | `feat/the-upload-resumes-after-reload` | Journey **resume an import after reloading**: a fresh application (empty store) facing an `AWAITING_ARCHIVE` import with `uploadedBytes` 4 and a matching `localStorage` record asks for the file; the same file resumes with a `PUT` at 4; a file of another size is refused with nothing sent. With no record, the section offers the cancel button and no file picker. A record whose id is not the latest import is deleted on the first read, and the resumed import's own record once it reads `PENDING` |
| 50 | `feat/the-import-reports` | Journey **read an import's report**: a `COMPLETED` import shows its counters; the dialog lists a first page, "load more" sends the answered cursor and appends; each of the twelve kinds has its own sentence and an unknown kind the general one, never `undefined`; `issueDetailTruncated` true shows the truncation sentence and false does not. A `FAILED` import with `failureCode` `ARCHIVE_UNREADABLE` shows that code's sentence, an unknown code the general one |
| 60 | `feat/the-task-centre-carries-data-tasks` | Journey **an upload and an export surfacing in the task centre**: the upload's progress shows on `/` while it runs; a `READY` export shows a notice with its link, dismissing it hides it and it stays hidden after a remount. `lib/notices.ts`, at 100 %, fed rows shaped as the API emits them (`completedAt` null on every failure): a `COMPLETED` import shows before `completedAt` + 24 hours and not after; a `READY` export shows before `expiresAt` and not after; a `FAILED` export shows at any time until dismissed. A `CANCELLED` import shows nothing |

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
| **Populate `contract/frozen/`** (Before beta) | Untouched: the contract takes a major, which the alpha allows freely, and nothing is frozen yet |
| **The grid keeps every page it scrolls** (Known limits) | Not adjacent: no grid here. The issues dialog pages the same way, bounded by the report's 500 rows |

This lot files one item, in block 10's pull request, at the operator's request of 2026-09-25:
**the contract's other string fields weighed as open or closed**, by decision I's test (does the
client's behaviour depend on the value, or only a sentence), `reasonCode` and `DownloadStatusDto`
included. `P1`, client ergonomics.

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
`agents/workflow.md` for this lot only. It suspends, for this lot, decision 6 of
`docs/adr/0018-a-block-is-a-pull-request.md` as `agents/workflow.md` states it (a block's pull
request merged before the next starts) and, of `docs/adr/0023-act-in-a-teammate-per-block.md`, the
teammate spawned from `main` and stopped when its pull request merges. An ADR adopting stacks would
supersede those.

- **Each block's branch starts from the previous block's**, and its pull request targets that
  branch. `gh stack` (github/gh-stack v0.1.1) creates and links them: `gh stack add`, `gh stack submit`.
- **The next block starts once the previous one's local gate is green and its pull request is open
  as a draft**, not once it has merged (answer C1).
- **One teammate works at a time, and a block's teammate stays idle rather than stopped** until its
  pull request merges, so a comment reaches the agent that wrote the code.
- **A fix-back runs in this order**, one working tree being shared: the lead asks the working
  teammate to reach a commit and stop; the lower block's teammate checks out its branch, commits the
  fix and runs the gate; the lead cascades it with `gh stack rebase --upstack` and pushes; the working
  teammate resumes on its rewritten branch.
- **Only the lead rewrites the stack.** Teammates commit on their own branch and never rebase.
- **After each merge the lead runs `gh stack sync`**: a rebase merge gives the merged commits new
  identities, and the branch above still carries the old ones until it is rebased. Each such push
  re-runs continuous integration on every pull request above.
- **The budget is measured against the parent branch**: the command of `agents/workflow.md` with
  `<parent>...HEAD` in place of `main...HEAD`.
- **The operator merges after reviewing**, pull request by pull request or with `gh stack merge`,
  rebase only. The closing block and the lot's tag come after the last merge, as usual.
- **The handoff is written from the pull request bodies, open or merged**, and reports, from
  `gh pr view` and `gh run list` timestamps: the wall-clock time from block 10's first commit to the
  last merge, beside lot `0.36.0-the-refusals-are-declared`'s; each cascaded rebase and the runs it
  re-triggered; each fix-back and how long the working teammate waited on it; and the operator's
  review latency per pull request. Then the operator's own reading, which decides.
- **`gh stack submit` and `gh stack view` are settled on block 10**: if GitHub's stacks do not
  link for this repository, plain pull requests with a base branch each, rebased by hand, replace
  them, and the handoff says so.

## 9. Pitfalls

- **The row's `uploadedBytes` can lag the file**, section 2. Resuming from it alone sends a `PUT` at a
  stale offset; the `409` corrects it, and a loop that treats that `409` as fatal never resumes a cut
  chunk.
- **A reverse proxy in front of the API can refuse a chunk under 16 MiB** with a bodyless `413`.
  The bundled `nginx.conf` serves the static files only and proxies nothing; a deployment's own proxy
  is where `client_max_body_size` bites.
- **`openapi-fetch` serialises any body that is not `FormData` as JSON**, with
  `Content-Type: application/json` (0.17.0, `dist/index.mjs`, `defaultBodySerializer`). A `Blob`
  passed as is sends `{}` and earns a `415`: the chunk `PUT` gives a `bodySerializer` returning the
  `Blob` and an explicit `application/octet-stream`, as `images.ts` does for its `FormData`.
- **`Blob.slice` does not read the file**; it returns a new `Blob`
  ([MDN, `Blob.slice()`](https://developer.mozilla.org/en-US/docs/Web/API/Blob/slice)), and the bytes
  leave only when `fetch` sends it. A test asserting the bodies reads them from the request MSW
  receives.
- **`beforeunload` shows the browser's own sentence**, whatever the page sets, and only once the
  user has interacted with the page
  ([MDN, `beforeunload` event](https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeunload_event)).
- **The file length is the truth with one writer only.** `appendChunk` takes no lock, so a retry
  sent while the server still reads the cut request could put two appends on one file. The client
  sends one `PUT` at a time and waits 5 seconds before a retry; whether that suffices against a
  half-open connection is not measured, nor what the import makes of an archive corrupted that way.
  Unverified, and the server is out of scope: the handoff carries it under what is not validated.
- **A `204` leaves `data` undefined** (`openapi-fetch` 0.17.0): the export's and the import's
  `DELETE` are read from `response.ok`.
- **The store outlives a journey**: module state survives between tests in the same file, so the
  test setup resets it.
- **Every message exists in both locales, and none carries a long dash** (`pre-commit`).
