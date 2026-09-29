# 0046. Knip and Biome check the clients

Status: Accepted
Date: 2026-09-28
Specification: `docs/specs/2026-09-28-knip-and-biome-keep-the-clients-clean.md`.
Written in block 10.
Supersedes: the linter of `docs/specs/2026-09-10-web-application.md`, its tooling table, ESLint with its
recommended sets.

## Context

The clients' gate linted with ESLint's and typescript-eslint's recommended sets and nothing else: no rule reading
types, no React plugin, no formatter, and nothing that finds code, exports or dependencies no one uses. Every answer
below is the operator's of 2026-09-28, in Discuss.

## Decision

1. **Knip runs twice in the clients' gate, as one script**: `pnpm run knip` is `knip && knip --strict`, over the
   whole workspace. The default mode counts tests as users and finds unused files, dependencies and tooling; strict
   mode implies production mode, so it reads production code alone, and also refuses a runtime import declared as a
   `devDependency`. No single run gives both readings (knip.dev, "Using Knip in CI").
   - **Strict keeps the manifests truthful**: what reaches the bundle is a `dependency`, the rest a `devDependency`.
     It protects no install: a workspace package is a symbolic link to its source directory, its `devDependencies`
     installed beside it (pnpm, `dependenciesMeta.injected`), and every image installs in full. Today it costs
     `tailwindcss` moving to `dependencies`, the one finding strict adds to production mode's.
   - **An export read by tests alone is either tested through what uses it or tagged `@internal`**, which production
     mode skips.
   - **A file leaves the report through `knip.json`'s `entry` and `project` patterns, never through `ignore`**, so
     what Knip does not read is stated as what it is.

2. **Biome replaces ESLint**, linter and formatter, at 2.5.14. A rule of ESLint's two recommended sets whose Biome
   counterpart sits outside Biome's recommended preset is turned on: `noUnusedExpressions` for
   `no-unused-expressions`. Of the eight rules `biome migrate eslint --include-inspired` reports as not implemented,
   five are covered otherwise: `no-delete-var` and `no-octal` are syntax errors in a module, `tsc` refuses
   `no-new-symbol`'s case, the formatter exposes `no-unexpected-multiline`'s. Three are lost:
   - `no-invalid-regexp`, which checks only a literal pattern given to `new RegExp`, and every one here is a variable;
   - `@typescript-eslint/triple-slash-reference`, with no triple-slash directive in the code;
   - `no-useless-assignment`, a value overwritten before it is read, which neither `tsc` nor Biome sees. The
     operator accepted this loss rather than keep a second linter for it.

   **Biome also replaces dependency-cruiser.** `noImportCycles` takes `no-circular`; `noRestrictedImports`, scoped by
   `overrides`, takes the two forbidden edges, each rule's reason becoming its message. Measured on a scratch copy of
   the tracked files: a planted cycle, `openapi-fetch` imported under `apps/`, and `@pinry-reborn/auth`,
   `../../auth/src` and `../../auth/src/index` imported under `packages/api-client/` are each refused. Biome matches
   the import as written where dependency-cruiser matched the resolved path, so a path alias, which the code has
   none of, could slip past a pattern. The operator took that trade for one dependency fewer.

3. **The formatter takes Biome's defaults**: tabs, 80 columns, semicolons, double quotes, trailing commas, imports
   organised. The one formatting option set is `css.parser.tailwindDirectives`, without which Biome cannot parse
   `styles.css`.
   The operator chose defaults over the style in place (no semicolons, two spaces, about 120 columns), the catch-up
   rewriting every file either way. Semicolons protect nothing a formatter does not already protect: Biome parses
   as the engine does, so an automatic-semicolon hazard shows as a joined line.

4. **The linter is Biome's recommended preset and the rules below.** Each rule is judged on the readability and
   maintainability it buys, not on how many sites it flags today.

   | Rule | Why | Scope or option |
   |---|---|---|
   | `noLeakedRender` | `{count && <X/>}` renders `0` | |
   | `useUniqueElementIds` | A literal `id` repeats when the component does | (Corrected: `excludedComponents: ["ToggleButton", "Tab", "Panel"]`, react-aria's `id` there being a collection key and not a DOM id; the operator's answer of 2026-09-29 in block 40.) |
   | `noShadow` | A name hiding another reads as the same value | |
   | `useArraySortCompare` | `.sort()` compares numbers as strings | |
   | `noReturnAssign` | An assignment hidden in a return | |
   | `useComponentExportOnlyModules` | Vite's fast refresh needs components alone in their module | |
   | `noExcessiveCognitiveComplexity` | Measures what a long function only suggests | |
   | `useBlockStatements` | An `if` without braces | |
   | `noUnusedExpressions` | A forgotten `return` or call; ESLint's recommended set carried it (decision 2) | |
   | `noUnnecessaryConditions` | A condition always true or always false | Its one site today is a false positive, a variable a later statement sets before the handler reading it runs (`sign-out.journey.test.tsx:52`), excepted under decision 6. The operator keeps the rule and excepts its false positives |
   | `useConsistentMethodSignatures` | A method signature is checked bivariantly, a property signature strictly | |
   | `noParameterProperties` | Syntax a type stripper cannot erase | |
   | `noJsxLiterals` | Text written in JSX escapes the catalogues | (Corrected: `allowedStrings: ["·", "/"]`, punctuation being no text to translate; the operator's answer of 2026-09-29 in block 40.) |
   | `noNoninteractiveElementInteractions` | Accessibility | (Corrected: `ImageDropBox.tsx` is excepted, with the reason its existing `noStaticElementInteractions` exception gives; the operator's answer of 2026-09-29 in block 40.) |
   | `noNestedTernary` | Nested ternaries read badly | |
   | `noNegationElse` | A positive condition reads first | |
   | `useMaxParams` | Past four, a named object reads better | |
   | `useConsistentTypeDefinitions` | One way to declare an object type | |
   | `noDefaultExport` | Named exports only, as the code already is | Off for the configuration files that tools require |
   | `useFilenamingConvention` | One naming of files | Off for `src/journeys/`, named by `journeyTestFile` |
   | `useNamingConvention` | Locks the naming the code already has | Object keys may be `CONSTANT_CASE`, since they mirror the API's values; `strictCase: false` |
   | `noEmptyBlockStatements` | An empty block says why in a comment, a swallowed error first | Off for tests, where `() => {}` is the idiom |
   | `useGlobalThis` | Code moves between the application and the shared packages unchanged | |
   | `noExcessiveClassesPerFile` | Guards against classes piling up, though the code has almost none | |
   | `noExcessiveLinesPerFile` | A long file mixes concerns, as `PinGrid.tsx` does | Default 300, the rule's own count; off for `**/*.test.*` and `src/test/**` |

5. **These rules are refused, and why**:
   - Another framework's: `useQwikValidLexicalScope`, `noSolidDestructuredProps`, `useSolidForComponent`,
     `noReactSpecificProps`, `noImgElement`.
   - At odds with the build: `useImportExtensions`, since Vite resolves without them; `noNodejsModules`, which flags
     the configuration files that run on Node; `noUnresolvedImports`; `useJsonImportAttributes`.
   - `noJsxPropsBind`: a `useCallback` per handler, which React's documentation says pays only for a memoised child.
   - `noExcessiveLinesPerFunction`: a component in JSX is long and still one concern; cognitive complexity measures
     the thing itself.
   - `noImplicitBoolean`: a bare boolean prop is JSX's shorthand, as a bare attribute is HTML's.
   - `useImageSize`: a tile already reserves its box with `aspectRatio`, and the API's `width` and `height` may be
     null, so one of its sites could not comply.
   - `noMisplacedAssertion`: an `expect` in a helper a test calls does assert, and such a helper reads well.
   - `useExportsLast`: order only, and the code reads public first.
   - `useTopLevelRegex`: every site is a test query, and hoisting parts a pattern from its one use.
   - `noTernary`: the conditional render is React's idiom.
   - `noVoid`: `void promise` says a promise is left on purpose.
   - `noMagicNumbers`: `413` reads better than a constant's name.
   - `noEqualsToNull`: `!= null` is the idiom for both absent values. The closest call of the list.
   - `noAwaitInLoops`: the loops it flags are sequential on purpose; `Promise.all` would change them.
   - `noIncrementDecrement`, `useDestructuring`, `useNumberNamespace`, `useNumericSeparators`: no reading gained.

6. **A rule's exception at a site is `// biome-ignore <rule>: <reason>`**, and a reason is required. An exception
   covering a whole path is an `overrides` entry in `biome.json`, listed in decision 4.

## Consequences

- **Every file of `clients/` is rewritten once**, in a commit that does nothing else. Its identifier goes in
  `.git-blame-ignore-revs` after the stack merges, a rebase merge giving it a new one.
- **TypeScript is no longer held below 6.1 by `typescript-eslint`'s peer range.** `openapi-typescript` 7.13.0 still
  declares `typescript: ^5.x`, which 6.0.3 already does not meet.
- **`.dagger/src/index.ts` stays unchecked**: the pipeline belongs to neither ecosystem, and bringing it in is its own
  lot (`docs/backlog.md`).
- **Fails if** a rule adopted here is silenced more often than it is obeyed: its `biome-ignore` lines outnumbering the
  sites that follow it says the rule was wrong, and it moves to decision 5. Counted from the sites met after this lot:
  `noUnnecessaryConditions` starts at one exception and no site, by the operator's choice.
