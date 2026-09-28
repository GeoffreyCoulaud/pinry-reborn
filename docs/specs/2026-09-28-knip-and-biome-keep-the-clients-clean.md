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

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `chore/knip-finds-dead-code` | Decision A. `pnpm run knip` as a new step of `clientsGate` after `boundaries`. Every finding of section 2 fixed: the three exports and types unexported, `dropUpload`, `ATTEMPTS` and `NOTICE_MS` each tested through what uses it or tagged `@internal`, the dependencies moved. The report says how each of the six files of section 2 leaves the production report:
`lib/catalogues.ts` and `lib/journeys.ts` are read by their tests alone, so they fall outside the production
`project` patterns. Planted: an unused export in `lib/`, refused by the default pass; a runtime import of `packages/auth` declared as a `devDependency`, refused by the strict pass. `clients/AGENTS.md` (commands) and the root `AGENTS.md` (the `clients-gate` row) name the step. Carries this specification and ADR 0046. |
| 20 | `chore/biome-replaces-eslint` | Decision B, and C's configuration with the gate running `biome lint` alone, the code not formatted yet. `"preset": "recommended"` only (the `recommended` field is deprecated in 2.5.14), plus the counterparts of decision 2. The report confirms decision 2's list with `biome migrate eslint --include-inspired` run before `eslint.config.js` goes. Every Biome recommended finding fixed or excepted under decision 6. Whether Biome's ignore-file support reads the nested `.gitignore` files of `src/paraglide/` and `packages/api-client/` is measured, `files.includes` being the fallback. `clients/AGENTS.md` loses ESLint and dependency-cruiser: the Node range's reason, the `src/paraglide/` bullet, the `boundaries` command and the `main`-beside-`exports` gotcha, whose reason was dependency-cruiser's resolver, while the import graph norm stays and names Biome as what enforces it; the root `AGENTS.md`'s `clients-gate` row too. Planted, each refused by `pnpm run lint`: a `debugger` statement; a cycle of two files; `openapi-fetch` imported under `apps/`; `../../auth/src` imported under `packages/api-client/`. |
| 30 | `chore/biome-formats-the-clients` | Decision C. One commit holding the output of `biome check --write` and nothing else (formatting and organised imports): the command run on the commit's parent reproduces it, `git diff` empty. A second switching `lint` to `biome ci`. `pnpm add` measured to keep a `package.json` in tabs, else `package.json` files are excluded from the formatter. Planted: a line indented with spaces, refused by `pnpm run lint`. Past the bounds under decision E. |
| 40 | `chore/biome-rules-beyond-recommended` | Decision D, but for `noExcessiveLinesPerFile`. One commit holding `biome lint --write`'s safe fixes alone; the hand fixes after it. `noUnnecessaryConditions`' site in `sign-out.journey.test.tsx:52` is a false positive, excepted with its reason and not rewritten. `useFilenamingConvention` renames `components/DataTasks.tsx` and `routes/Credentials.tsx` to their export's name or its kebab form. `useArraySortCompare`'s one site, `lib/journeys.test.ts:15`, takes a comparator. `noLeakedRender`'s fixes change what renders when a count is `0`, so: read headless at 1280×800, both themes: screenshots of the grid and an opened pin, before and after, in the report. Planted: `{count && <span/>}`, refused. Past the bounds under decision E. |
| 50 | `refactor/pin-grid-splits-by-concern` | `noExcessiveLinesPerFile` on, off for `**/*.test.*` and `src/test/**`. `PinGrid.tsx` split along section 2's four concerns, code moved and not changed: `git diff --color-moved=plain --color-moved-ws=allow-indentation-change` leaves no line uncoloured but imports and exports, and the report gives that count. Read headless: a pin opened, stepped to the next, both themes, screenshots before and after. Planted: a file under `src/` of 301 lines of code by the rule's count, refused. Past the bounds under decision E. |

**The closing block** carries the holistic findings, the handoff and the backlog item of decision F. After the
operator merges the stack, the lead opens a pull request adding block 30's formatting commit, as merged, to
`.git-blame-ignore-revs`: a rebase merge gives it a new identifier. The same pull request adds
`git config blame.ignoreRevsFile .git-blame-ignore-revs` to the root `AGENTS.md`'s setup, git reading the file only
when told to.

## 5. Adjacent backlog items

None: no open item concerns the clients' tooling (`docs/backlog.md` read on 2026-09-28).

## 6. Out of scope

- **Linting and formatting `.dagger/`** (decision F).
- **Biome's nursery rules**, unstable by definition.
- **Formatting on commit or in an editor.** The gate refuses unformatted code; how a contributor formats is theirs.
