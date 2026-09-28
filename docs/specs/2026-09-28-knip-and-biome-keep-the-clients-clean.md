# Knip and Biome keep the clients clean

Date: 2026-09-28
Status: Draft for the operator. Frozen when the lot's last block merges.
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
  of `clients/` but the lockfile (`biome format --reporter=summary`), and cannot parse `styles.css` without
  `--css-parse-tailwind-directives=true`.
- **`PinGrid.tsx` is 613 lines** (`wc -l`), eleven top-level declarations across four concerns: the grid (`PinGrid`,
  `Tile`, `useColumnWidth`), the image (`OriginalImage`, `PinImage`), the viewer (`PinDialog`, `PinDetails`) and its
  gestures (`PinGestures`, `useSwipe`, `useArrowKeys`), plus `LAYOUT`.

## 3. Decisions

The operator's answers of 2026-09-28 in Discuss. ADR 0046 records them with their reasons; they are not repeated
here.

**A. Knip covers the whole workspace**, as `pnpm run knip`, which runs `knip && knip --strict` (ADR 0046, decision 1).
The six files above leave the report through `knip.json`'s `entry` and `project` patterns, production ones suffixed
`!`, never through `ignore`. The dependencies move as strict mode requires: `@heroui/styles` 3.2.6 (the version
`@heroui/react` 3.2.6 declares) and `tailwindcss` to the web application's `dependencies`,
`@inlang/paraglide-js` to its `devDependencies`.

**B. Biome replaces ESLint** (decision 2): `eslint`, `@eslint/js`, `typescript-eslint`, `globals` and
`eslint.config.js` go. `pnpm run lint` runs Biome.

**C. The formatter takes Biome's defaults** (decision 3), `css.parser.tailwindDirectives` the one setting. Generated
files stay out: `src/paraglide/`, `packages/api-client/src/schema.d.ts`, `dist/`, `coverage/`, the lockfile.

**D. The linter is the recommended preset and the rules of ADR 0046, decision 4**, with their scopes and options.

**E. The catch-up blocks may pass the block bounds.** The operator's answer: "C'est ok que pour cette opération de
rattrapage on dépasse les limites de bloc". It covers blocks 30, 40 and 50 alone, each stating its counts in its
report.

**F. `.dagger/` is out of the lot**, a backlog item filed by the closing block.

## 4. Blocks

No block changes a user path, so none adds a journey; every existing journey passes unchanged. A block's check is
shown able to fail: its report names the defect it planted, the output that refused it, and the planted defect's
removal.

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `chore/knip-finds-dead-code` | Decision A. `pnpm run knip` as a new step of `clientsGate` after `boundaries`. Every finding of section 2 fixed: the three exports and types unexported, `dropUpload`, `ATTEMPTS` and `NOTICE_MS` each tested through what uses it or tagged `@internal`, the dependencies moved. Planted: an unused export in `lib/`, refused by the default pass; a runtime import of `packages/auth` declared as a `devDependency`, refused by the strict pass. `clients/AGENTS.md` (commands) and the root `AGENTS.md` (the `clients-gate` row) name the step. Carries this specification and ADR 0046. |
| 20 | `chore/biome-replaces-eslint` | Decision B, and C's configuration with the gate running `biome lint` alone, the code not formatted yet. Recommended preset only, plus the counterparts of decision 2. The report lists every rule of ESLint's two recommended sets with its Biome counterpart, or states it has none (Biome's "Rules sources" page). Every Biome recommended finding fixed or excepted under decision 6. Whether Biome's ignore-file support reads the nested `.gitignore` files of `src/paraglide/` and `packages/api-client/` is measured, `files.includes` being the fallback. `clients/AGENTS.md` loses ESLint: the Node range's reason and the `src/paraglide/` bullet. Planted: a `debugger` statement, refused by `pnpm run lint`. |
| 30 | `chore/biome-formats-the-clients` | Decision C. One commit holding the output of `biome check --write` and nothing else (formatting and organised imports); a second switching `lint` to `biome ci`. `pnpm add` measured to keep a `package.json` in tabs, else `package.json` files are excluded from the formatter. Planted: a line indented with spaces, refused by `pnpm run lint`. Past the bounds under decision E. |
| 40 | `chore/biome-rules-beyond-recommended` | Decision D, but for `noExcessiveLinesPerFile`. One commit holding `biome lint --write`'s safe fixes alone; the hand fixes after it. `useImageSize`: the grid's tiles and the viewer's original take the `width` and `height` the API gives; a local preview is excepted. `noUnnecessaryConditions`' site in `sign-out.journey.test.tsx:52` read and fixed so that the journey still fails when sign-out breaks. Read headless at 1280×800, both themes: the grid and an opened pin look as on `main`. Planted: `{count && <span/>}`, refused. Past the bounds under decision E. |
| 50 | `refactor/pin-grid-splits-by-concern` | `noExcessiveLinesPerFile` on outside tests. `PinGrid.tsx` split along section 2's four concerns, code moved and not changed, `git diff -M` showing the moves. Read headless: a pin opened, stepped to the next, both themes. Planted: a 301-line file under `src/`, refused. Past the bounds under decision E. |

**The closing block** carries the holistic findings, the handoff and the backlog item of decision F. After the
operator merges the stack, the lead opens a pull request adding block 30's formatting commit, as merged, to
`.git-blame-ignore-revs`: a rebase merge gives it a new identifier.

## 5. Adjacent backlog items

None: no open item concerns the clients' tooling (`docs/backlog.md` read on 2026-09-28).

## 6. Out of scope

- **Linting and formatting `.dagger/`** (decision F).
- **Biome's nursery rules**, unstable by definition.
- **Formatting on commit or in an editor.** The gate refuses unformatted code; how a contributor formats is theirs.
