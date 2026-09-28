# Knip and Biome keep the clients clean

Date: 2026-09-28
Status: Draft for the operator. One specification review ran,
`.reviews/knip-and-biome-keep-the-clients-clean-spec.md`, its 5 CRITICAL, 4 MAJOR and 9 MINOR closed in this document
and ADR 0046, four of them by the operator's answers of 2026-09-29. Frozen when the lot's last block merges.
Lot: `0.44.0`. Branches: one stack: 10 `chore/knip-finds-dead-code`, 20 `chore/biome-replaces-eslint` on 10,
30 `chore/biome-formats-the-clients` on 20, 40 `chore/biome-rules-beyond-recommended` on 30,
50 `refactor/pin-grid-splits-by-concern` on 40, then the closing block.
ADR: `docs/adr/0046-knip-and-biome-check-the-clients.md`, written in block 10. The contract does not change.

## 1. Goal

The clients' gate refuses dead code, a misdeclared dependency, unformatted code and the rules the operator chose,
across the whole `clients/` workspace. The existing code is brought into conformance once, in this lot.

## 2. What exists today

Measured on `main` at `bcd2a3a5`.

- **The gate lints with ESLint's and typescript-eslint's recommended sets** (`clients/eslint.config.js`) and formats
  nothing. Its steps are `.dagger/src/index.ts:267-273`.
- **Knip 6.38.0 without configuration** (`pnpm dlx knip`, from `clients/`) finds `@heroui/styles` imported by
  `apps/webapp/src/styles.css:2` and declared nowhere, `THEME_PREFERENCES` (`lib/theme.ts:8`) exported and read in
  its own file alone, and the types `RecycledBoard` (`recycled.ts:6`) and `OpenSession` (`session.ts:7`) exported and
  unused.
- **`knip --production` adds** `dropUpload` (`imports.ts:211`), `ATTEMPTS` (`lib/imports.ts:37`) and `NOTICE_MS`
  (`lib/notices.ts:7`), read by tests alone; `@inlang/paraglide-js` unused in production, it being read by
  `vite.config.ts` and the `messages` script alone; and six files it cannot reach without configuration:
  `lib/catalogues.ts`, `lib/journeys.ts`, `test/app.tsx`, `test/server.ts`, `test/setup.ts`,
  `packages/api-client/generate.mjs`. **`knip --strict` adds** `tailwindcss`, imported by `styles.css:1` and declared
  as a `devDependency`.
- **Biome 2.5.14 with its defaults** would reformat all 138 tracked `ts`, `tsx`, `js`, `mjs`, `json` and `css` files
  of `clients/` (`biome format --reporter=summary`), and cannot parse `styles.css` without
  `--css-parse-tailwind-directives=true`.
- **`PinGrid.tsx` is 613 lines by `wc -l`, 455 by `noExcessiveLinesPerFile`'s own count**, fourteen top-level
  declarations across four concerns: the grid (`PinGrid`, `Tile`, `useColumnWidth`, `LAYOUT`), the image
  (`OriginalImage`, `PinImage`, `SPINNER_DELAY_MS`), the viewer (`PinDialog`, `PinDetails`) and its gestures
  (`PinGestures`, `useSwipe`, `useArrowKeys`, `SWIPE_PX`), plus `NEW_BOARD`, which goes with its reader
  (`grep -nE "^(export )?(function|const|type|interface|class) "`).

## 3. Decisions

The operator's answers of 2026-09-28 in Discuss. ADR 0046 records them with their reasons; they are not repeated
here.

**A. Knip covers the whole workspace**, as `pnpm run knip`, which runs `knip && knip --strict` (ADR 0046, decision 1).
The six files above leave the report through `knip.json`'s `entry` and `project` patterns, production ones suffixed
`!`, never through `ignore`. The dependencies move as strict mode requires: `@heroui/styles` 3.2.6 (the version
`@heroui/react` 3.2.6 declares) and `tailwindcss` to the web application's `dependencies`,
`@inlang/paraglide-js` to its `devDependencies`.

**B. Biome replaces ESLint and dependency-cruiser** (decision 2): `eslint`, `@eslint/js`, `typescript-eslint`,
`globals`, `dependency-cruiser`, `eslint.config.js`, `.dependency-cruiser.json`, the `boundaries` script and its gate
step go. `pnpm run lint` runs Biome.

**C. The formatter takes Biome's defaults** (decision 3), `css.parser.tailwindDirectives` the one formatting option. Generated
files stay out: `src/paraglide/`, `packages/api-client/src/schema.d.ts`, `dist/`, `coverage/`, the lockfile.

**D. The linter is the recommended preset and the rules of ADR 0046, decision 4**, with their scopes and options.

**E. The catch-up blocks may pass the block bounds.** The operator's answer: "C'est ok que pour cette opération de
rattrapage on dépasse les limites de bloc". It covers blocks 30, 40 and 50 alone, each stating its counts in its
report.

**F. `.dagger/` is out of the lot**, a backlog item filed by the closing block. Block 10 still adds its step to
`clientsGate` in `.dagger/src/index.ts`, the gate's one definition.

## 4. Blocks

No block changes a user path, so none adds a journey; every existing journey passes unchanged. A block's check is
shown able to fail: its report names the defect it planted, the output that refused it, and the planted defect's
removal.

| Block | Branch | Subject | Bounds |
|---|---|---|---|
| 10 | `chore/knip-finds-dead-code` | Knip, and its findings fixed | Held |
| 20 | `chore/biome-replaces-eslint` | Biome lints in place of ESLint and dependency-cruiser | Held |
| 30 | `chore/biome-formats-the-clients` | The code formatted once | Passed, decision E |
| 40 | `chore/biome-rules-beyond-recommended` | The rules past the preset, and their fixes | Passed, decision E |
| 50 | `refactor/pin-grid-splits-by-concern` | `PinGrid.tsx` split, the file length rule on | Passed, decision E |

### Block 10: Knip finds dead code

- **Does**: decision A. `pnpm run knip` becomes a step of `clientsGate`, after `boundaries`.
- **Fixes** every finding of section 2:
  - `THEME_PREFERENCES`, `RecycledBoard` and `OpenSession` lose their `export`;
  - `dropUpload`, `ATTEMPTS` and `NOTICE_MS` are each tested through what uses them, or tagged `@internal`;
  - the three dependencies move.
- **Reports** how each of section 2's six files leaves the production report. `lib/catalogues.ts` and
  `lib/journeys.ts` are read by their tests alone, so they fall outside the production `project` patterns.
- **Proven by**, each planted defect refused:
  - an unused export in `lib/`, by the default pass;
  - a runtime import of `packages/auth` declared as a `devDependency`, by the strict pass.
- **Documents**: `clients/AGENTS.md` (commands) and the root `AGENTS.md` (the `clients-gate` row) name the step.
- **Carries** this specification and ADR 0046.

### Block 20: Biome replaces ESLint

- **Does**: decision B, with decision C's configuration in place. The gate runs `biome lint` alone, the code not
  being formatted yet; a recommended fix is applied with `biome lint --write`, never `biome check --write`, which
  would format every file here rather than in block 30.
- **Configures** `"preset": "recommended"` (the `recommended` field is deprecated in 2.5.14), plus decision 2's
  counterparts: `noUnusedExpressions`, `noImportCycles` and the two `noRestrictedImports` edges.
- **Fixes** every recommended finding, or excepts it under decision 6.
- **Measures** whether Biome's ignore-file support reads the nested `.gitignore` files of `src/paraglide/` and
  `packages/api-client/`. `files.includes` is the fallback.
- **Reports** decision 2's list confirmed by `biome migrate eslint --include-inspired`, run before
  `eslint.config.js` goes.
- **Proven by**, each planted defect refused by `pnpm run lint`:
  - a `debugger` statement;
  - a cycle of two files;
  - `openapi-fetch` imported under `apps/`;
  - `../../auth/src` imported under `packages/api-client/`.
- **Documents**:
  - `clients/AGENTS.md` loses ESLint and dependency-cruiser: the Node range's reason, the `src/paraglide/` bullet,
    the `boundaries` command, and the `main`-beside-`exports` gotcha, whose reason was dependency-cruiser's resolver;
  - the import graph norm stays, and names Biome as what enforces it;
  - the root `AGENTS.md`'s `clients-gate` row follows.

### Block 30: Biome formats the clients

- **Does**: decision C, in two commits:
  - the output of `biome check --write` and nothing else, formatting and organised imports. Run on the commit's
    parent, the command reproduces it: `git diff` is empty;
  - `lint` switched to `biome ci`.
- **Measures** whether `pnpm add` keeps a `package.json` in tabs. If not, `package.json` files leave the formatter.
- **Proven by**: a line indented with spaces, refused by `pnpm run lint`.

### Block 40: the rules past the preset

- **Does**: decision D, every rule but `noExcessiveLinesPerFile`, in two commits: `biome check --write`'s safe fixes
  alone, then the hand fixes. `check` and not `lint`, so that the fixes come out formatted and `biome ci` passes.
- **Fixes, at the sites known today** (lines as on `main` at `bcd2a3a5`; block 30's formatting moves them):
  - `noUnnecessaryConditions`: `sign-out.journey.test.tsx:52` is a false positive, excepted with its reason and not
    rewritten;
  - `useFilenamingConvention`: `components/DataTasks.tsx` and `routes/Credentials.tsx` are renamed to their export's
    name or its kebab form;
  - `useArraySortCompare`: `lib/journeys.test.ts:15` takes a comparator.
- **Reads headless** at 1280×800, both themes, `noLeakedRender`'s fixes changing what renders when a count is `0`:
  screenshots of the grid and an opened pin, before and after, in the report.
- **Proven by**: `{count && <span/>}`, refused.

### Block 50: `PinGrid.tsx` splits by concern

- **Does**: `noExcessiveLinesPerFile` on, off for `**/*.test.*` and `src/test/**`. `PinGrid.tsx` splits along
  section 2's four concerns.
- **Moves code without changing it**: `git diff --color-moved=plain --color-moved-ws=allow-indentation-change` leaves
  no line uncoloured but imports and exports, and the report gives that count.
- **Reads headless**, both themes: a pin opened, then stepped to the next, screenshots before and after.
- **Proven by**: a file under `src/` of 301 lines of code by the rule's count, refused.

### The closing block

- **Carries** the holistic findings, the handoff, and the backlog item of decision F.
- **After the operator merges the stack**, the lead opens one pull request that:
  - adds block 30's formatting commit, as merged, to `.git-blame-ignore-revs`, a rebase merge giving it a new
    identifier;
  - adds `git config blame.ignoreRevsFile .git-blame-ignore-revs` to the root `AGENTS.md`'s setup, git reading the
    file only when told to.

## 5. Adjacent backlog items

None: no open item concerns the clients' tooling (`docs/backlog.md` read on 2026-09-28).

## 6. Out of scope

- **Linting and formatting `.dagger/`** (decision F).
- **Biome's nursery rules**, unstable by definition.
- **Formatting on commit or in an editor.** The gate refuses unformatted code; how a contributor formats is theirs.
