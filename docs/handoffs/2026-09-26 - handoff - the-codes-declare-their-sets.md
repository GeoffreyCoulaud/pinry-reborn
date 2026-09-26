# Handoff: the codes declare their sets

Date: 2026-09-26
Specification: `docs/specs/2026-09-26-the-codes-declare-their-sets.md`
ADR: `docs/adr/0044-a-response-code-declares-its-set.md`
Blocks, one stack, each on the one below: 10 `refactor/the-failures-are-declared` (#233),
15 `refactor/the-import-failures-are-declared` (#234), 20 `feat/the-open-codes-are-published` (#236),
25 `feat/the-client-reads-the-open-codes` (#237), 30 `feat/the-export-says-gone` and
35 `feat/the-import-says-why` (this block's pull request and the one below it).
Written in block 35, the lot's last code block, from the pull requests' block reports while all were
still open; to be corrected in the closing block, `fix/the-codes-declare-their-sets-closes`, on 35.
Tier: Spec. One specification review ran, `.reviews/the-codes-declare-their-sets-spec.md`, its 2 MAJOR
and 5 MINOR closed in the specification. The holistic review runs at the head of Wrap.

## Current state

- **Every code a response carries declares its set.** A state is a closed `enum`; a reason or a kind
  is an `x-extensible-enum` beside it, which `oasdiff` v1.31.0 reports neither on an added nor on a
  removed value. `ExtensibleEnumsFilter` lists the extensible ones: `DownloadReasonDto`,
  `UserDataImportIssueKindDto`, `UserDataExportReasonDto`, `UserDataImportReasonDto`.
- **Each code is a presentation twin** mapped from the domain by an exhaustive `when`, so a renamed
  domain constant fails to compile rather than changing the wire.
- **The export's state is `PENDING`, `READY`, `FAILED`, `GONE`.** The domain keeps its six states;
  `EXPIRED`, `DELETED` and `SUPERSEDED` travel as `GONE` with the cause in `reasonCode`.
- **`reasonCode` replaces `failureCode`** on both data rows, the name the image rows already use.
- **The contract is `19.0.0`**, raised in block 20: SmallRye writes a nullable reference as
  `anyOf: [$ref, null]`, which `oasdiff` reads as a widening of the three download `reasonCode`s.
- **The web application's sentence tables are keyed by `Known<"Component">`**, read from the
  `extensibleEnums` interface `generate.mjs` appends to `schema.d.ts`. A value the server adds fails
  `pnpm run typecheck` until the table names it; a value a newer server sends reaches the fallback.

## What was built, per block

| Block | Pull request | What | Budget (lines, files) |
|---|---|---|---|
| 10 | #233 | `UserDataExportFailure` replaces the export's string constants; the column keeps the same names, read with `valueOf` | 100, 12 |
| 15 | #234 | The same for the import, `UserDataImportFailure` | 98, 11 |
| 20 | #236 | The twins of the two existing open codes, `ExtensibleEnumsFilter`, the contract at `19.0.0` | 193, 16 |
| 25 | #237 | `generate.mjs` and `Known<K>`; `downloadReasons.ts` and `importIssues.ts` keyed by it; the rule in `agents/engineering.md` and `clients/AGENTS.md` | 44, 8 |
| 30 | this block's parent | The export's `GONE` and `UserDataExportReasonDto`; `dataFailures.ts` one table per side, the export's keyed by `Known` | 163, 17 |
| 35 | this block | The import's `reasonCode` and `UserDataImportReasonDto`, its table keyed by `Known`; the backlog item deleted | 66, 14 |

Budgets are the `agents/workflow.md` command against each block's parent. Each block ran
`dagger call gate` green locally before its push: block 30 at `fc89d36d`, block 35 at `fec168f8`, each
`BUILD SUCCESSFUL` and the contract guard green at `19.0.0` against `main`.

Blocks 10 and 20 split at the file bound as their rows note; block 30 came to 23 files and split too,
by side rather than by ecosystem (see the pitfalls).

## Tier-2 questions and operator decisions

| Where | Question | Answer |
|---|---|---|
| Block 25 (#237) | Decision D's `"A" \| (string & {})` does not survive openapi-fetch 0.17's `Readable`, which maps anything extending `object`: (a) the known values in a separate generated map, (b) a closed literal union in TypeScript only, (c) reopen D | (a) |

## For the closing block

- **ADR 0044 decision 4 no longer matches what block 25 built.** It says the client generates "with
  a `transform`" and that an `x-extensible-enum` "becomes its literals or any other string". The field
  stays `string`; `generate.mjs` appends an `extensibleEnums` interface of known values, and
  `Known<"Component">` reads it (specification, decision D as corrected). The Consequences' "a committed
  type assertion fails `tsc`" is stale for the same reason: a lost step fails `index.ts` with TS2305.
- **The counts of Wrap (d)**: no fix-back, cascaded rebase or re-triggered run by the time block 35
  was written; the operator's reading of the bodies has not happened yet.

## Pitfalls

- **A field removed from a response cannot split from its client**: the clients' typed fixtures stop
  compiling, so block 30 split by side (export, then import), each carrying its API and client halves.
- **A transform probed outside the web application misses openapi-fetch's `Readable`**: probe a
  client type change through `pnpm run typecheck` of the whole workspace (#237).
- **`$TMPDIR` is unset in these sessions, and the evidence guard refuses a redirection elsewhere**:
  `export TMPDIR=<scratchpad>` in the same command satisfies it (#236 used the literal path).
- **A headless reading over WebDriver BiDi**: `script.evaluate` takes `target: { context }`, not
  `context`.

## What is not validated

- **Rows written by a deployed instance** hold only the enums' names on the strength of the
  specification's grep of the producers, rerun in #233 (#233, #234).
- **The screens were read headless** against a stubbed API, never against the running API: the built
  bundle served with an export `GONE`/`EXPIRED` and an import `FAILED`/`ARCHIVE_UNREADABLE`, then an
  export `FAILED`/`USER_GONE` and an import `FAILED`/`IMPORT_FAILED`, read in headless Firefox 156.0.1
  at 1280 and 380 px (block 35). The `GONE` export showed the request button alone, no link and no
  sentence, and the task centre's badge counted one task, the failed import's.
- **No sentence names a gone cause**, by the specification's section 6: `reasonCode` makes one
  possible and nothing asks for it.
- **The holistic review has not run.**

## Next step

Wrap: the holistic review over
`git diff lot/0.39.0-pull-requests-read-by-a-tech-lead..origin/feat/the-import-says-why`, then the
closing block on 35 with its findings, ADR 0044 decision 4 corrected and this handoff corrected; then
the operator's review, the merge of the whole stack, and the lot's tag.
