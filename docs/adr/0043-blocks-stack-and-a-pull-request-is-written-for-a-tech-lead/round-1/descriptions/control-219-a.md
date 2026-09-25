feat(api): the data travels, the data states are declared and the handshake publishes the import bounds

Block 10 of the lot `the-data-travels` (`docs/specs/2026-09-25-the-data-travels.md`, which this pull request also carries).

## Stack

- **Base: `main`.** This is the bottom of the stack; it depends on no other pull request.
- **Next up: block 20**, `feat/the-export-is-reachable`, targets this branch.
- Consumers: block 20 (export section) is the first to read the states, and block 30 (upload) the first to read the two bounds.

## What changes

- **The two `state` fields are closed** (decision I). `UserDataExportOutputDto.state` and `UserDataImportOutputDto.state` are now typed by their presentation twins `UserDataExportStateDto` and `UserDataImportStateDto`. Each mapper maps with an exhaustive `when`, so adding a value to the domain breaks the build.
- **`kind` and `failureCode` stay open strings.** A contract test fails if `kind` gains an `enum`.
- **The handshake publishes the import's two bounds** (decision I'). `LimitsDto` gains `maxImportChunkBytes` and `maxImportArchiveBytes`. `ImportProducers` (`api-application`) builds `ImportUploadBounds` (`api-usecases`) from `ImportsConfig`, and `HandshakeController` reads it. No module gains a dependency, and each default is declared only once.
- **The contract goes to `18.0.0`**, and `contract/openapi.json` is regenerated. Declaring an enum on a response is `response-property-enum-value-added` for `oasdiff`, so this is a major.
- **`docs/backlog.md`** files the item from section 6: the contract's other string fields, `reasonCode` and `DownloadStatusDto` included, have not yet been weighed as open or closed.

## Evidence

| Check | Result |
|---|---|
| `dagger call gate` | Green at `699d7959`, 2 min 48 s, exit 0. The last commit, `eef0c88f`, only restores a comment to its wording on `main` |
| Continuous integration | Run 36143519541 green, `verify` 7 min 40 s |
| Budget against `main` (the parent) | 163 lines, 19 files: under 500 and under 20 |
| `contract-guard` | Green at `18.0.0`. Red at `17.1.0` with `api-major-version-not-bumped` (set to 17.1.0, regenerated, then restored) |

What the block table requires its tests to fail on:

- **A mapper test per twin** walks every domain entry, as `PinImageStateMapperTest` does.
- **The contract declares exactly the domain's values** on both `state` fields. With the 17.0.0 contract restored, the test fails: `expected: <[PENDING, READY, FAILED, EXPIRED, DELETED, SUPERSEDED]> but was: <[]>`.
- **`kind` is still `type: string`.** With an `enum` added to `kind`, the open-kind test fails.
- **`GET /api/v1/handshake` answers a test profile's overridden `imports.*` values** (2 345 678 and 98 765 432 109), not the defaults, so a hard-coded constant would fail.

## Tier-1 fixes

- The `ImportProducers` KDoc said "The three import beans". It now says "The import beans", as the class now produces a fourth.

## Tier-2 questions

None.

## Departures from the block table

- **One client file changed**, `clients/apps/webapp/src/lib/uploads.test.ts`, although section 7 says only `api/` moves in block 10. Its `LIMITS` fixture is typed by the generated client, and the two new `LimitsDto` fields are required, so the clients' typecheck refused it.
- **`ImportUploadBounds` is a data class, not the value class decision I' names**: it holds two fields, and a value class holds one.
- **One tier-1 fix is left to block 30.** The comment on `ImportsConfig.maxChunkBytes` names the wrong test (`ImportsConfigIntegrationTest` instead of `BodyLimitCheck`). Fixing it here made 20 files, over the file bound.

## Pitfalls

- **A required field added to a DTO breaks the clients' typed fixtures, not the Gradle build.** Only the full gate caught it.
- **`gh stack submit --auto` titles the pull request after the branch and writes no body.** Both are written by hand.
- **The pre-push gate did not show in `gh stack submit`'s output.** Whether it runs through `gh stack` is not established.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
