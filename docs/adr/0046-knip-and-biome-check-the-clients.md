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
   - **Strict matters for the shared packages**: pnpm installs a workspace package's `dependencies` alone into its
     consumer, so `packages/auth` declaring a runtime import as a `devDependency` would break the web application,
     and later the extension. The rule that follows for the web application: what reaches the bundle is a
     `dependency`.
   - **An export read by tests alone is either tested through what uses it or tagged `@internal`**, which production
     mode skips.

2. **Biome replaces ESLint**, linter and formatter, at 2.5.14. A rule of ESLint's two recommended sets whose Biome
   counterpart sits outside Biome's recommended preset is turned on, so the switch loses no check. dependency-cruiser
   stays: Biome does not read the import graph.

3. **The formatter takes Biome's defaults**: tabs, 80 columns, semicolons, double quotes, trailing commas, imports
   organised. The one setting is `css.parser.tailwindDirectives`, without which Biome cannot parse `styles.css`.
   The operator chose defaults over the style in place (no semicolons, two spaces, about 120 columns), the catch-up
   rewriting every file either way. Semicolons protect nothing a formatter does not already protect: Biome parses
   as the engine does, so an automatic-semicolon hazard shows as a joined line.

4. **The linter is Biome's recommended preset and the rules below.** Each rule is judged on the readability and
   maintainability it buys, not on how many sites it flags today.

   | Rule | Why | Scope or option |
   |---|---|---|
   | `noLeakedRender` | `{count && <X/>}` renders `0` | |
   | `useUniqueElementIds` | A literal `id` repeats when the component does | |
   | `noShadow` | A name hiding another reads as the same value | |
   | `useArraySortCompare` | `.sort()` compares numbers as strings | |
   | `noReturnAssign` | An assignment hidden in a return | |
   | `noMisplacedAssertion` | An `expect` outside a test asserts nothing | |
   | `useComponentExportOnlyModules` | Vite's fast refresh needs components alone in their module | |
   | `noExcessiveCognitiveComplexity` | Measures what a long function only suggests | |
   | `useBlockStatements` | An `if` without braces | |
   | `noUnnecessaryConditions` | A condition always true or always false | |
   | `useConsistentMethodSignatures` | A method signature is checked bivariantly, a property signature strictly | |
   | `noParameterProperties` | Syntax a type stripper cannot erase | |
   | `noJsxLiterals` | Text written in JSX escapes the catalogues | |
   | `useImageSize` | The API gives every image's size, and the grid then does not jump | |
   | `noNoninteractiveElementInteractions` | Accessibility | |
   | `noNestedTernary` | Nested ternaries read badly | |
   | `noNegationElse` | A positive condition reads first | |
   | `useMaxParams` | Past four, a named object reads better | |
   | `useConsistentTypeDefinitions` | One way to declare an object type | |
   | `useLiteralKeys` | `a.name` over `a["name"]` | |
   | `noDefaultExport` | Named exports only, as the code already is | Off for the configuration files that tools require |
   | `useFilenamingConvention` | One naming of files | Off for `src/journeys/`, named by `journeyTestFile` |
   | `useNamingConvention` | Locks the naming the code already has | Object keys may be `CONSTANT_CASE`, since they mirror the API's values; `strictCase: false` |
   | `noEmptyBlockStatements` | An empty block says why in a comment, a swallowed error first | Off for tests, where `() => {}` is the idiom |
   | `useGlobalThis` | Code moves between the application and the shared packages unchanged | |
   | `noExcessiveClassesPerFile` | Guards against classes piling up, though the code has almost none | |
   | `noExcessiveLinesPerFile` | A long file mixes concerns, as `PinGrid.tsx` does | Default 300; off for tests |

5. **These rules are refused, and why**:
   - Another framework's: `useQwikValidLexicalScope`, `noSolidDestructuredProps`, `useSolidForComponent`,
     `noReactSpecificProps`, `noImgElement`.
   - At odds with the build: `useImportExtensions`, since Vite resolves without them; `noNodejsModules`, which flags
     the configuration files that run on Node; `noUnresolvedImports`; `useJsonImportAttributes`.
   - `noJsxPropsBind`: a `useCallback` per handler, which React's documentation says pays only for a memoised child.
   - `noExcessiveLinesPerFunction`: a component in JSX is long and still one concern; cognitive complexity measures
     the thing itself.
   - `noImplicitBoolean`: `isRequired` is HTML's own boolean attribute form.
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
- **TypeScript is no longer held by `typescript-eslint`'s peer range**, which pinned it at 6.0.3.
- **`.dagger/src/index.ts` stays unchecked**: the pipeline belongs to neither ecosystem, and bringing it in is its own
  lot (`docs/backlog.md`).
- **Fails if** a rule adopted here is silenced more often than it is obeyed: its `biome-ignore` lines outnumbering the
  sites that follow it says the rule was wrong, and it moves to decision 5.
