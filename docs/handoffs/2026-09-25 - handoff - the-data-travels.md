# Handoff: the data travels

Date: 2026-09-25
Specification: `docs/specs/2026-09-25-the-data-travels.md`
ADR: none.
Blocks, one stack, each on the one below: 10 `feat/the-data-states-are-declared` (#219),
20 `feat/the-export-is-reachable` (#220), 30 `feat/the-import-uploads` (#222),
34 `feat/the-import-is-followed` (#223), 37 `feat/the-archive-uploads` (#224),
40 `feat/the-upload-resumes-after-reload` (#225), 50 `feat/the-import-reports` (#226),
60 `feat/the-task-centre-carries-data-tasks` (#227), 65 `feat/the-task-centre-keeps-data-notices`
(this block's pull request).
Written in block 65, the lot's last code block, from the pull requests' bodies while all were still
open; to be corrected in the closing block.
Tier: Spec. One specification review ran, `.reviews/the-data-travels-spec.md`, 2 CRITICAL, 5 MAJOR
and 12 MINOR closed in the specification. The holistic review runs at the head of Wrap.

## Current state

- **The export and the import are reachable from `/account`**, in two sections between the password
  form and the dangerous part, each reading only the latest row (`pageSize=1`).
- **The export**: requested behind a password dialog (`X-Reauthentication`), polled while `PENDING`,
  downloaded through a plain link the cookie authenticates, deleted from the section (#220).
- **The import**: a file picker, an upload one chunk at a time from a store above the router that
  survives a change of screen and holds the page on `beforeunload` (#222, #224), resumed after a
  reload with the same file (#225), cancelled behind a confirmation (#223), and its report: counters
  and a paged issues dialog (#226).
- **The task centre is named Tasks** and carries the running upload, import and export beside the
  image downloads (#227), then what the latest export and import ended with until dismissed, the
  dismissals kept per browser (block 65).
- **The contract is at `18.0.0`**: both `state`s are closed enums, `kind` and `failureCode` stay open
  strings, and the handshake publishes `maxImportChunkBytes` and `maxImportArchiveBytes` (#219).

The backlog item "What the API serves and the web application does not reach yet" is deleted in
block 65. The lot files one item, in #219: the contract's other string fields weighed as open or
closed. "Import follow-ons" stays open (decision K).

## What was built, per block

| Block | Pull request | Lines, files against its parent | Continuous integration |
|---|---|---|---|
| 10 | #219 | 163, 19 | 36143519541, green, 7 m 40 s |
| 20 | #220 | 455, 16 | 36144544985, green, 7 m 4 s |
| 30 | #222 | 264, 4 | 36147811170, green, 8 m 10 s |
| 34 | #223 | 288, 12 | 36148672568, green, 8 m 12 s |
| 37 | #224 | 367, 7 | 36149285625, green, 6 m 41 s |
| 40 | #225 | 410, 10 | 36150500132, green, 8 m 48 s |
| 50 | #226 | 373, 11 | 36151769922, green, 7 m 55 s |
| 60 | #227 | 263, 13 | 36154177058, read in its pull request |
| 65 | this one | in its pull request | in its pull request |

Each block ran `dagger call gate` green locally before its push, and each web application block read
its screens in headless Firefox against a stubbed API at 1280 and 380 px. That reading caught
defects three times: unformatted counts and a ghost button (#223), a muted sentence in the issues
dialog (#226), and a popover flush against a phone's right edge with a lone ghost "Forget it"
(block 65).

## Tier-2 questions and operator decisions

| Where | Question | Answer |
|---|---|---|
| Discuss and Spec | The lot's decisions | A1, B1, C1, D1 with D'2, E2, F4, G3, H1, I re-asked after the specification review's first CRITICAL and answered I1' with a backlog item, J1, K1, L1 after one rework, M1 (recorded in the specification, section 3) |
| Block 30 | 892 lines over 17 files | "b": three blocks, 30, 34 and 37 (#222) |
| Block 60 | 579 lines over 17 files | "a": two blocks, 60 and 65 (#227) |

## The stacked pull requests experiment

Section 8 of the specification ran this lot as one stack of pull requests, each block's branch on
the previous one, the next block starting once the previous one's gate was green and its pull
request open as a draft. The lead's running log is the source of what follows.

- **Tooling.** `gh stack` (github/gh-stack v0.1.1) linked the stack on GitHub from block 20 on (stack
  #221); a stack of one pull request shows none. `gh stack submit --auto` titles a pull request after
  its branch and writes no body, so each teammate set both with `gh pr edit`. One submit hit a
  transient 504 on #220's check, the branches matching the remote afterwards (#226).
- **The pre-push gate through `gh stack submit`** printed nothing: gh-stack pushes with
  `--force-with-lease --atomic`, and whether the hook ran is not established. Every block rests on
  its local `dagger call gate` instead.
- **Gains.** No block waited on a review or a merge: block 20 started right after #219's run
  started, and nine blocks were opened in one day. Both splits cost only `gh stack add` on top, with
  the same teammate carrying its halves and its context.
- **Frictions.**
  - The shared working tree serialises everything that reads it: the headless reading of one block
    holds the next block's start, so the reading moved into each block's own Verify.
  - Marking a pull request ready belongs to a teammate that has moved on to the next block: the lead
    marked #222 ready.
  - Every teammate sent a duplicate idle notification after each stop, which the lead filtered.
  - One of the lead's CI watches was a shell `&` that died with its shell, re-armed as a background
    task (#220).
- **Cascaded rebases and fix-backs**: none by the time block 65 was written. No pull request had
  merged, and the operator's review had not started, so no fix-back ran and nothing was rewritten.
- **Wall-clock time from block 10's first commit to the last merge**: to be filled in the closing
  block. Block 10's first commit is `741cdce7` at 2026-09-25T13:44:48Z, its run started 13:50:20Z;
  the comparison is with lot `0.36.0-the-refusals-are-declared`.
- **Review latency per pull request and the runs each cascaded rebase re-triggered**: to be filled
  in the closing block, from `gh pr view` and `gh run list` timestamps.
- **The operator's own reading**, which decides whether an ADR adopts stacks: to be filled in the
  closing block.

## Pitfalls

- **`openapi-fetch` serialises a `Blob` as JSON** (`{}`, then a `415`): the chunk `PUT` gives a
  `bodySerializer` and `application/octet-stream` (#222).
- **jsdom's `Blob` does not survive Node's `fetch`**: a jsdom slice reaches MSW as the text
  `undefined`, so the journeys build archives with `node:buffer`'s `File` (#222, #224).
- **`isolate: false`** makes module state and `localStorage` outlive a test file: the journeys reset
  the upload store and clear the storage they write (#222, #225).
- **An unhandled request fails no test**: MSW rejects the fetch and logs to stderr, which Vitest
  shows only for a failing test. `src/test/server.ts` answers the exports and imports lists empty by
  default, since the task centre reads both on every signed-in screen (#227).
- **Paraglide formats no number**: counts go through `Intl.NumberFormat` (#223).
- **HeroUI's `Modal.Body` is muted** (`.modal__body`): primary text inside needs `text-foreground`
  (#226).
- **A popover with a `max-w-sm` bound is as wide as a 380 px viewport** and sits flush against its
  right edge: the task centre bounds it by the viewport as well (block 65).
- **A move by `browsingContext.navigate` reloads the page** and drops the tab's upload: a headless
  reading moves with an in-app link (#227).
- **A required field added to a DTO breaks the clients' typed fixtures, not the Gradle build**: only
  the clients' typecheck in `dagger call gate` catches it (#219).

## What is not validated

- **Two appends on one import file.** `appendChunk` takes no lock, and a retry sent while the server
  still reads a cut request could put two writes on the file. One `PUT` at a time and a 5-second
  wait are what the client does; whether that suffices against a half-open connection is not
  measured (specification, section 9).
- **Two tabs share `localStorage`** (#225). A second tab on `/account` during an upload reads the
  import as `AWAITING_ARCHIVE` with a record and offers "Choose the file again"; choosing there puts
  two upload loops on one import. The lead's reading: both send the same bytes at the same offsets,
  the `409` keeps each offset honest, one tab closes the upload and the other stops on
  `IMPORT_NOT_AWAITING_ARCHIVE`. **Proposed as a known limit with no guard, awaiting the operator.**
- **A reverse proxy's lower body limit** shows as a bodyless `413` and stops the upload with its
  sentence; no deployment with such a proxy was tried (specification, section 9).
- **The screens were read headless** against a stubbed API, never against the running API.
- **The pre-push gate through `gh stack submit`**, above.

## Next step

Wrap: the operator reviews and merges the stack, rebase only; the holistic review over
`git diff lot/0.37.0-the-gate-is-deterministic..origin/main`; then the closing block with its
findings, this handoff corrected and the experiment's figures filled in; then the lot's tag.
