# Handoff: Knip and Biome keep the clients clean

Date: 2026-09-29
Tier: Spec. Specification `docs/specs/2026-09-28-knip-and-biome-keep-the-clients-clean.md`, ADR
`docs/adr/0046-knip-and-biome-check-the-clients.md`. Lot `0.44.0`, one stack: block 10 `chore/knip-finds-dead-code`
(PR #255), 20 `chore/biome-replaces-eslint` (#256), 30 `chore/biome-formats-the-clients` (#258), 40
`chore/biome-rules-beyond-recommended` (#259), 50 `refactor/pin-grid-splits-by-concern` (#260), then the closing
block in three pull requests: `chore/knip-and-biome-closing` (the ternaries), `refactor/sign-in-and-sign-up-split`
and `docs/knip-and-biome-closing-documents`. Written in block 50; the closing block corrects it after the holistic
review.

## Current state

- **The clients' gate runs Knip twice**, `pnpm run knip` being `knip && knip --strict`: unused files, exports and
  dependencies with the tests counted as users, then production code alone. A file only tests read leaves the
  production report through `knip.json`'s `project` patterns, an export only tests read through an `@internal` tag.
- **Biome replaced ESLint and dependency-cruiser.** `pnpm run lint` is `biome ci`: the linter, the formatter and the
  import order, writing nothing. The import graph's three rules are `noImportCycles`, type-only imports counted, and
  two `noRestrictedImports` under `overrides`. The `boundaries` step is gone.
- **The code is in Biome's default format**: tabs, 80 columns, semicolons, double quotes. Generated files stay out
  through the nested `.gitignore` files, which Biome reads.
- **The linter is the recommended preset plus ADR 0046's decision 4**, with three corrections recorded in the ADR
  (`noJsxLiterals`, `useUniqueElementIds`, `noNoninteractiveElementInteractions`) and two code fixes (the observer
  stub's own file, the ternaries), the operator's five answers of block 40.
- **A conditional render is a ternary with `null`**, every one in `clients/` but the pin form's address field,
  whose comment says why. No rule holds the idiom; `noLeakedRender` asks for it only where it can type a leak.
- **Every route file is named after its export**: `routes/SignIn.tsx` and `routes/SignUp.tsx`, their shared form
  in `components/CredentialsForm.tsx`.
- **No file under `clients/` passes 300 lines by the rule's count**, tests and `src/test/**` aside.
  `PinGrid.tsx` is the grid alone; `PinImage.tsx`, `PinDialog.tsx` and `PinGestures.tsx` hold the image, the viewer
  and the selection bar's gestures. `imports.ts` keeps the `Import` type, the file records and the upload's store,
  and `importQueries.ts` the five query hooks, depending on `imports.ts` and never the reverse.
- **Dependencies moved as strict mode requires**: `@heroui/styles` and `tailwindcss` in the web application's
  `dependencies`, `@inlang/paraglide-js` in its `devDependencies`. The Node range is `^22.22.2 || ^24.15 || >=26`,
  jsdom setting it once ESLint left.

## Evidence

- Block 10: `dagger call gate` green at `683218b1`; budget 40 lines, 12 files against `main` (PR #255).
- Block 20: green at `71de425c`; budget 136 lines, 12 files against block 10 (#256).
- Block 30: green at `e4476435`; budget 11843 lines, 139 files against block 20, passed under decision E, all of it
  the formatting commit, which `biome check --write` on its parent reproduces with an empty `git diff` (#258).
- Block 40: green at `5ca9ab36`; budget 860 lines, 64 files against block 30, passed under decision E (#259).
- Block 50: green at `b6374607`; budget 1209 lines, 10 files against block 40, passed under decision E. Each hunk of
  a move counts its deletion and its addition apart, so the two moves are most of it.
- Block 50's moves, `git show --color-moved=plain --color-moved-ws=allow-indentation-change`: `4697ea38` leaves 40
  of 951 changed lines uncoloured, `312fa327` 23 of 265. Every one is an import, an added `export`, or the removed
  `@internal` of `dropUpload`.
- The closing block: `chore/knip-and-biome-closing` green at `c048c97d`, budget 74 lines, 19 files against block
  50; `refactor/sign-in-and-sign-up-split` green at `749f7496`, budget 68 lines, 4 files against it. Together they
  pass 20 files, hence the split. The split's commit leaves 19 lines uncoloured under the same `git show` (with
  `-M`), each an import or the added `export` of `CredentialsForm`.
- The ternaries' conditions, read with the TypeScript checker (a throwaway script over `apps/webapp/tsconfig.json`):
  every left operand a `boolean` but `opened` in `PinGrid.tsx`, an object or `undefined`. No number, so nothing
  rendered changes, and no headless reading was made.
- Continuous integration green on #255, #256, #258, #259 and #260 (`gh pr checks`, 2026-09-29).
- Each block's check was shown able to fail, the planted defect refused and then removed, as each report details:
  an unused export and a misdeclared dependency (10); `debugger`, two cycles and two forbidden imports (20); a line
  indented with spaces (30); `{title.length && <span />}` (40); a 301-line `src/lib/planted.ts`, "This file has too
  many lines (301). Maximum allowed is 300.", exit 1, which passed once renamed `planted.test.ts` (50).
- Read headless in Firefox 156.0.1 against block 40's throwaway API stub, 1280×800, both themes. Block 40: the grid
  and pin 0 opened, before `e4476435` and after `5ca9ab36`, identical byte for byte. Block 50: pin 0 opened, then
  stepped to pin 1 with the right arrow, before `5ca9ab36` and after `b6374607`, the four pairs identical byte for
  byte (`cmp`); within a run, opened differs from stepped and light from dark.

## Pitfalls

- **Formatting lengthens files.** The specification measured the file length rule on `main`, before block 30; 80
  columns and block 40's braces took `imports.ts` from 261 lines to 333. Measure a length bound after the formatter.
- **Knip:** a non-production `project` pattern that overlaps a production one blinds production mode; the patterns
  must include `css`, or `styles.css`'s imports go unread.
- **`pnpm add` does not move a package already in `devDependencies`**; `--save-prod` does. `pnpm run <script>`
  installs first when a manifest changed and rewrites the lockfile.
- **`noImportCycles` misses a cycle through a bare package name** the importer's manifest does not declare;
  `noRestrictedImports` refuses that import anyway.
- **Biome's cognitive complexity counts each function alone**: a handler moved into a nested function lowers nothing,
  only one outside the component does. `noLeakedRender` sees types within a function and not across a hook call.
- **An `@internal` tag outlives its reason in silence** until Knip prints a tag hint, as `dropUpload`'s did once
  production code imported it; the hint does not fail the gate.
- **`.claude/hooks/evidence-guard.py` refuses a redirect into any file, throwaway logs and planted defects
  included**, and `$TMPDIR` is empty here: send a background server's output to `/dev/null`, write a plant with the
  edit tool.
- **`noNegationElse`'s fix inverts `x !== null ? <A/> : null`** into `x === null ? null : <A/>`; with a variable
  in place of `<A/>`, `noLeakedRender` then refuses it, so that one site stays `&&`.
- **The Edit tool trims a replacement's trailing space**: a `replace_all` of `<Item ` gave `<TaskItemkey=`.
- **Firefox over WebDriver BiDi**: `browsingContext.setViewport` needs a tab from `browsingContext.create`, not the
  first context, and a failed script leaves its session open until Firefox restarts.

## Departures from the specification

- Block 10: `RecycledBoard` deleted rather than un-exported; `dropUpload`, `ATTEMPTS` and `NOTICE_MS` tagged
  `@internal` rather than tested through their users.
- Block 20: `noImportCycles` sets `ignoreTypes: false`; `main` removed from both workspace manifests, Biome resolving
  `exports`.
- Block 40: three commits instead of two, the reproducible unsafe fixes apart; `TaskItem.tsx`, `selection.ts` and
  `test/ReachedSentinelObserver.ts` are new files.
- Block 50: `useSwipe`, `useArrowKeys` and `SWIPE_PX` go with `PinDialog`, their one reader, where section 2 put them
  with `PinGestures`, the selection bar's. `imports.ts` split too, which the specification did not foresee.
- The closing block: three pull requests rather than two. The code findings alone touch 23 files, so they split
  in two, the documents staying apart as Wrap asks. Beyond the JSX children, the board screen's `search` prop and
  the pin form's `FilePreview` return became ternaries too; the address field keeps `&&` (Pitfalls).
- Tier-1 fixes: the Node range and its reason (20); the `noShadow` renames, `send`'s `Transfer` object, and a comment
  in each deliberately empty block (40).

## Tier-2 questions

- Blocks 10, 20 and 30: none.
- Block 40, five, answered "reco ok" by the operator on 2026-09-29 for all five: A `noJsxLiterals` allows `·` and `/`;
  B `useUniqueElementIds` excludes `ToggleButton`, `Tab` and `Panel`; C the `IntersectionObserver` stub moves to its
  own file; D the drop box's `biome-ignore` extends to `noNoninteractiveElementInteractions`; E the 23 leaked-render
  sites rewritten as ternaries.
- Block 50, two, answered "reco ok" on 2026-09-29: M, `imports.ts` also passes the rule, so it splits in this block
  (A); N, moving the upload's store would make a cycle, so the query hooks move to `importQueries.ts` instead (A').
- The closing block, two, answered "a" on 2026-09-29: O, `routes/credentials.tsx` splits into `SignIn.tsx` and
  `SignUp.tsx`, the form moving to `components/CredentialsForm.tsx`; P, every remaining `{cond && ...}` in JSX
  becomes a ternary.
- After the closing block, one: Q, the pin form's address field keeps `&&`, answered "On laisse tel quel." on
  2026-09-29.

## What is not validated

- The headless readings cover the grid and the viewer alone: not the task centre, import and export, recycle bin or
  account screens, where block 40 rewrote conditions and block 50 moved the import's hooks. The journeys cover those
  screens, and pass.
- The closing block's ternaries were not read headless, the checker's types being the evidence that nothing
  rendered changes; the sign-in and sign-up screens were not either, the journeys covering both.
- Nothing ran at phone width, and no touch swipe was driven.
- Node 24.15 and 26 were not run on a workstation; the gate's container runs 26.

## The holistic review

Over `git diff lot/0.43.0-the-pin-opens-beside-its-details..origin/refactor/pin-grid-splits-by-concern` at
`f6cb9c2d`, report `.reviews/knip-and-biome-keep-the-clients-clean-holistic.md`: 0 CRITICAL, 0 MAJOR, 6 MINOR, every
one fixed inside the lot.

1. ADR 0027 decision 4 still named dependency-cruiser: fixed, ADR 0027 marked partially superseded by ADR 0046,
   whose `Supersedes:` line names it.
2. This handoff counted five corrections in the ADR where it carries three: fixed under Current state.
3. ADR 0046 decision 2 named four of the five rules "covered otherwise": fixed, a `(Corrected: ...)` in place.
4. `routes/credentials.tsx`, the one lower-case route, named after neither export: fixed, split by the operator's
   answer O.
5. Two conditional-render idioms side by side: fixed, every site a ternary by the operator's answer P, one excepted
   with its reason.
6. `PinGrid.tsx`'s "(decision F)" named no document: fixed, the parenthesis dropped, the sentence giving the reason.

## The backlog

The specification names no adjacent item. The closing block files decision F's item: `.dagger/` is neither linted
nor formatted.

## The lot's counts

Fix-backs 0, cascaded rebases 0, runs re-triggered 0. The operator's reading of the bodies: no remark.

## Next step

The operator's review of the stack. After the operator merges it, the lead's follow-up pull request
adds block 30's formatting commit, as merged, to `.git-blame-ignore-revs`, and `git config blame.ignoreRevsFile
.git-blame-ignore-revs` to the root `AGENTS.md`'s setup.
