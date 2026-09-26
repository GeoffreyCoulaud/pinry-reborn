# Handoff: the codes declare their sets

Date: 2026-09-26
Specification: `docs/specs/2026-09-26-the-codes-declare-their-sets.md`
ADR: `docs/adr/0044-a-response-code-declares-its-set.md`
Blocks, one stack, each on the one below: 10 `refactor/the-failures-are-declared` (#233),
15 `refactor/the-import-failures-are-declared` (#234), 20 `feat/the-open-codes-are-published` (#236),
25 `feat/the-client-reads-the-open-codes` (#237), 30 `feat/the-export-says-gone` and
35 `feat/the-import-says-why` (this block's pull request and the one below it). (Corrected: 30 is #238
and 35 is #239; the closing block is `fix/the-codes-declare-their-sets-closes`, on 35.)
Written in block 35, the lot's last code block, from the pull requests' block reports while all were
still open; to be corrected in the closing block, `fix/the-codes-declare-their-sets-closes`, on 35.
Tier: Spec. One specification review ran, `.reviews/the-codes-declare-their-sets-spec.md`, its 2 MAJOR
and 5 MINOR closed in the specification. The holistic review runs at the head of Wrap. (Corrected: it
ran, `.reviews/the-codes-declare-their-sets-holistic.md`, 0 CRITICAL, 0 MAJOR, 4 MINOR, each fixed in
the closing block; see "The holistic review".)

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
| 30 | #238 | The export's `GONE` and `UserDataExportReasonDto`; `dataFailures.ts` one table per side, the export's keyed by `Known` | 163, 17 |
| 35 | #239 | The import's `reasonCode` and `UserDataImportReasonDto`, its table keyed by `Known`; the backlog item deleted | 66, 14 |
| Closing | this block | The holistic review's four findings: the export's table keyed by its failures alone, every response enum classified, ADR 0044 and this handoff corrected | 55, 4 |

Budgets are the `agents/workflow.md` command against each block's parent. Each block ran
`dagger call gate` green locally before its push: block 30 at `fc89d36d`, block 35 at `fec168f8`, each
`BUILD SUCCESSFUL` and the contract guard green at `19.0.0` against `main`. (Corrected: the cascade
after the two fix-backs rewrote both, block 30 now ending at `ee983427` and block 35 at `aafca2eb`; the
fix-backs are `6d1e280e` on block 20 and `ecc0ef92` on block 25.)

Continuous integration ran once per branch, `verify` passing on each (`gh run list --branch`): #233 run
36230198577, #234 run 36230230598, #236 run 36231416913, #237 run 36231452177, #238 run 36232322417,
#239 run 36232351287.

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

(Corrected: both items are done in the closing block, the ADR as its `(Corrected: ...)` notes and the
counts under "The lot's counts".)

## The holistic review

`.reviews/the-codes-declare-their-sets-holistic.md`, over
`git diff lot/0.39.0-pull-requests-read-by-a-tech-lead..origin/feat/the-import-says-why`: 0 CRITICAL,
0 MAJOR, 4 MINOR. Every finding was fixed in the closing block.

| Finding | Exit |
|---|---|
| A `null` entry of the export's sentence table produced the general failure sentence, and the test pinned the gone causes to it | Fixed: the table is keyed by `Exclude<Known<"UserDataExportReasonDto">, "EXPIRED" \| "DELETED" \| "SUPERSEDED">`, with no `null` entry |
| Nothing tied the next open code to `ExtensibleEnumsFilter` | Fixed: `ContractSchemaDeclarationTest` refuses an enum under `dtos/output` listed neither in `EXTENSIBLE` nor in its `closedCodes`, mutation in the commit |
| ADR 0044's Consequences described the generation step as a documented option guarded by a type assertion | Fixed: `(Corrected: ...)` on decision 4 and on the bullet |
| This handoff did not name #238 and #239 or their runs | Fixed: header, table and runs above |

## The lot's counts

- **Fix-backs: 2**, the operator's two readability comments: "Test illisible" on #236's open-code
  tests, answered by `6d1e280e`, and "Illisible." on #237's `generate.mjs`, answered by `ecc0ef92`.
- **Cascaded rebases: 1**, over blocks 25, 30, 35 and the closing block, with conflicts on blocks 30
  and 35 resolved in `ContractSchemaDeclarationTest.kt`. The closing block rebased cleanly, then moved
  its classification test into the file's new shape.
- **Runs re-triggered: 5**, one per branch that moved, #236 to #240, all green: runs 36244137521,
  36244138306, 36244137774, 36244137848 and 36244138374. GitHub also cancelled 4 duplicates on the base
  change (36244137538, 36244137660, 36244137393, 36244137555), which are noise. Under ADR 0043's failure
  criterion: fewer runs re-triggered than the lot has blocks.
- **The operator's reading of the bodies**, their words of 2026-09-26: "Les descriptions de PR me
  conviennent, je n'ai pas de retour à faire dessus, c'est assez clair." (The bodies suit them, no
  feedback, clear enough.)

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
- **The holistic review has not run.** (Corrected: it ran; see "The holistic review".)
- **The closing block was not read headless**: only a `FAILED` export row reaches `exportFailure`, so
  dropping the gone causes from its table changes nothing on screen.

## Next step

Wrap: the holistic review over
`git diff lot/0.39.0-pull-requests-read-by-a-tech-lead..origin/feat/the-import-says-why`, then the
closing block on 35 with its findings, ADR 0044 decision 4 corrected and this handoff corrected; then
the operator's review, the merge of the whole stack, and the lot's tag. (Corrected: the review and the
closing block are done; the operator's review, the merge and the tag remain.)
