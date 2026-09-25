feat(api): the data states are declared

Block 10 of the lot `the-data-travels`, **the bottom of the stack: base `main`**. Block 20 (`feat/the-export-is-reachable`) is stacked on this branch. This pull request also carries the lot's specification, `docs/specs/2026-09-25-the-data-travels.md`.

## What it changes

- **The two data `state`s are closed on the wire.** `UserDataExportStateDto` and `UserDataImportStateDto` are presentation twins of the domain enums, mapped by an exhaustive `when`, so a domain value added later fails compilation. `kind` and `failureCode` stay open strings (decision I).
- **The handshake publishes the import's two bounds**, `limits.maxImportChunkBytes` and `limits.maxImportArchiveBytes`. `ImportProducers` (`api-application`) builds `ImportUploadBounds` (`api-usecases`) from `ImportsConfig`, the same path `maxArchiveBytes` already takes to the chunk receiver. No module gains a dependency (decision I').
- **The contract goes to `18.0.0`.** Declaring an enum on a response is a break by `oasdiff`'s rules. `contract/openapi.json` is regenerated.
- **`docs/backlog.md` gets one new item**, the operator's request from section 6: weigh the contract's other string fields as open or closed.

Who reads what: block 20 (export section) is the first to read the states, block 30 (upload) the two bounds.

## Evidence

| Check | Result |
|---|---|
| `dagger call gate` | Green at `699d7959`, 2 min 48 s, exit 0. The last commit, `eef0c88f`, only restores a comment to its text on `main` |
| Continuous integration | Run 36143519541 green, `verify` 7 min 40 s |
| `contract-guard` | Green at `18.0.0`. Red at `17.1.0` with `api-major-version-not-bumped` (set 17.1.0, regenerated, restored) |
| Budget against `main` | 163 lines, 19 files. `contract/openapi.json` is excluded |

What the block table requires, and the test that checks it:

- **Every domain value has a twin**: `Given every UserDataExportState` and `Given every UserDataImportState` in the two mapper tests, plus the exhaustive `when`.
- **The contract declares exactly the domain's values, and `kind` stays `type: string`**: two new tests in `ContractSchemaDeclarationTest`. Mutations tried: with the 17.0.0 contract put back, the states test fails (`expected: <[PENDING, READY, FAILED, EXPIRED, DELETED, SUPERSEDED]> but was: <[]>`). With an `enum` added to `kind`, the open-kind test fails.
- **The handshake answers the deployment's values, not constants**: `HandshakeIntegrationTest` overrides `imports.max_chunk_bytes` and `imports.max_archive_bytes` in its test profile (2 345 678 and 98 765 432 109, far from the 16 MiB and 20 GiB defaults) and reads them back from `GET /api/v1/handshake`.

## Tier 1 fixes

- Fixed: the `ImportProducers` KDoc said "The three import beans". It now says "The import beans".
- **Left for block 30**: the comment on `ImportsConfig.maxChunkBytes` names the wrong test (`ImportsConfigIntegrationTest` where it should say `BodyLimitCheck`). Fixing it here would have made 20 files, one over the bound.

## Tier 2 questions

None.

## Departures from the specification

- **One client file changes**, `clients/apps/webapp/src/lib/uploads.test.ts`, although section 4 lists only `api/` paths for this block. Its `LIMITS` fixture is typed by the generated client, the two new `LimitsDto` fields are required, and the clients' typecheck refused the fixture without them.
- **`ImportUploadBounds` is a `data class`, not the value class decision I' names.** A Kotlin value class holds one field, and this holds two.

## Pitfalls

- **A required field added to a DTO breaks the clients' typed fixtures, not the Gradle build.** Only the full gate caught it.
- **`gh stack submit --auto` titles the pull request after the branch and writes no body.** Each block's title and body have to be set by hand.
- **The pre-push gate did not show in `gh stack submit`'s output.** Whether the hook runs through `gh stack` is not established.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
