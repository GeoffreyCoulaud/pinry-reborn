# ktfmt formats the Kotlin code

Date: 2026-10-06
Status: Accepted by the operator on 2026-10-06. One specification review ran, `.reviews/0.51.0-spec.md`, its 1
CRITICAL, 3 MAJOR and 7 MINOR closed in this document and ADR 0053; the split's own block is the operator's answer "a"
to question H. Frozen when the lot's last block merges.
Lot: `0.51.0`. Branches: one stack: 10 `refactor/the-import-integration-test-splits`, 20
`chore/ktfmt-formats-the-kotlin-code` on 10, then the closing block.
ADR: `docs/adr/0053-ktfmt-formats-the-kotlin-code.md`, written by the lead in Spec and carried by block 10. The
contract does not change.

## 1. Goal

The API's gate refuses Kotlin that is not formatted, so a call chain half split across lines or a comma with no space
after it no longer passes. The existing code is formatted once, in this lot.

## 2. What exists today

Measured on `main` at `4ae64ef1`, each tool run on a copy of the tracked `api/**/*.kt` and `api/**/*.kts` in the
session's scratchpad.

- **Nothing formats the Kotlin code.** detekt runs without the ktlint wrapper, and ktlint lives only as an IntelliJ
  plugin setting, `api/.idea/ktlint-plugin.xml` in `DISTRACT_FREE` mode, which `.gitignore:17` keeps tracked.
- **ktfmt 0.64, `--kotlinlang-style`, `max_line_length = 120`** (read from an `.editorconfig` with
  `--enable-editorconfig`) reformats 590 files, +9 869 / -8 072 (`git diff --shortstat`). 13 lines stay past 120
  columns (`awk 'length > 120'`): 11 imports, which detekt excludes, and 2 strings.
- **Spotless 8.10.3 with `setMaxWidth(120)`** gives the same output on every file but `ProcessRunner.kt`, one line,
  where the ktfmt command line is not idempotent and Spotless writes the second pass's form (the specification review,
  `.reviews/0.51.0-spec.md`).
- **ktfmt at its default 100 columns** reformats 626 files, +17 526 / -9 210, and leaves 851 lines past 100: 630 test
  names in backticks, 80 imports, about 132 string lines, 2 comments (the same review's recount).
- **ktlint 1.8.0**, for the record: `ktlint_official` reformats 462 files and leaves 22 violations it cannot fix;
  `intellij_idea` reformats 351. `ktlint_official` fixes the reviewed chain of `PinUpdaterIntegrationTest.kt:273` as
  ktfmt does, and `intellij_idea` leaves it. The chain at `PinUpdaterIntegrationTest.kt:73-75` fits in 120 columns:
  ktfmt joins it on one line, `ktlint_official` leaves it split.
- **detekt on ktfmt's 120-column output** (`detektMain detektTest detektTestFixtures --continue`, in a clone at
  `4ae64ef1`): the baselines hold, and 9 findings are new:
  - `CommentCarriesDocumentation`, a KDoc reflowed to 5 or 6 lines (ktfmt rewraps KDoc, and puts a blank line
    before its first tag): `BoardRepositoryInterface.kt:13`, `UserDataImportRepositoryInterface.kt:15`,
    `AuthenticationAttemptKey.kt:23`, `TagSearcher.kt:11`, `TokenHasher.kt:6`, `MeImportTestProfile.kt:6`;
  - `MaxLineLength`: `MeImportController.kt:154`, `SharedRefusalsFilter.kt:105`;
  - `LargeClass` (`allowedLines: 600`): `MeImportIntegrationTest.kt:54`, 813 lines today and 867 after ktfmt
    (`wc -l`).

## 3. Decisions

The operator's answers of 2026-10-06 in Discuss. ADR 0053 records A to C with their reasons.

**A. ktfmt, `kotlinlang` style**, over ktlint (ADR 0053, decision 1).

**B. Spotless runs it**, over ktfmt-gradle (decision 2): Spotless 8.10.3, and ktfmt 0.64 declared as a library of
`api/gradle/libs.versions.toml` (`com.facebook:ktfmt`), its version read from there, so that Dependabot's Gradle
updater sees the pin. (Corrected: the updater's `gradle` group excludes it, so a ktfmt bump, which can reformat the
code, arrives in a pull request of its own; the holistic review, `.reviews/0.51.0-holistic.md`.)

**C. 120 columns**, detekt's `MaxLineLength` bound, set in the build with `setMaxWidth(120)` (decision 3).

**D. Blocks 10 and 20 may pass the block bounds**, as the Biome lot's decision E did. Block 20 is a configuration
commit, then a commit holding `spotlessApply`'s output alone, recorded in `.git-blame-ignore-revs`. Block 10 moves
code without changing it, which an observable shows.

**E. The editor is out of the lot.** `api/.idea/ktlint-plugin.xml` goes, its `DISTRACT_FREE` mode reformatting
against ktfmt on every save, and nothing replaces it: "sinon ça signifie qu'on doit tripoter la config d'ide depuis
ici, et ce n'est pas le sujet."

## 4. Blocks

No block changes a user path, so none adds a journey. A block's check is shown able to fail: its report names the
defect it planted, the output that refused it, and the planted defect's removal.

| Block | Branch | Subject | Bounds |
|---|---|---|---|
| 10 | `refactor/the-import-integration-test-splits` | `MeImportIntegrationTest` split, before ktfmt grows it | Passed, decision D |
| 20 | `chore/ktfmt-formats-the-kotlin-code` | Spotless and ktfmt in the gate, the code formatted once | Passed, decision D |

### Block 10: the import integration test splits

- **Does**: `MeImportIntegrationTest` splits into classes along the concerns its tests already group by, each far
  enough under `LargeClass`'s 600 lines that ktfmt's growth keeps it there.
- **Moves code without changing it**: `git diff --color-moved=plain --color-moved-ws=allow-indentation-change` leaves
  no line uncoloured but imports and class headers, and the report gives that count.
- **Keeps every test**: the JUnit XML reports of `:api-application:test` count the same tests before and after.
- **Carries** this specification and ADR 0053.

### Block 20: ktfmt formats the Kotlin code

- **Does**, in three commits:
  1. the configuration: Spotless applied in `subprojects` with a `kotlin` target and a `kotlinGradle` target, and on
     the root project for its `*.gradle.kts`. Each module's `spotlessCheck` reaches `gate` through `check`, which
     it joins by default (Spotless README, `enforceCheck`). The root project's `check`, which Spotless creates
     through the `base` plugin, is outside `gate`, so `gate` depends on the root's `spotlessCheck` by name.
     `ktlint-plugin.xml` goes, with its `.gitignore` line and the count in the comment above it;
  2. the output of `./gradlew spotlessApply` and nothing else. Run on the commit's parent, the command reproduces it:
     `git diff` is empty;
  3. section 2's detekt findings but `LargeClass`, which block 10 removes, fixed by hand and never baselined: each
     KDoc cut back to 4 lines, each long string rewritten.
- **Measures** whether Spotless gives section 2's figures; a gap is reported, not chased.
- **Proven by**, each planted defect refused by `./gradlew gate`:
  - `listOf(1,2)`, a comma with no space, in a module's source;
  - a chain with two calls on its first line, such as `given().authenticatedAs(auth)`, and the rest one per line;
  - `listOf(1,2)` in a `val` of `api/build.gradle.kts`, the output naming the root project's
    `spotlessKotlinGradleCheck`.
- **Deletes** the backlog item "The Kotlin code has no formatter".
- **Documents**:
  - `api/AGENTS.md`'s Commands replace "No auto-fix task" with `./gradlew spotlessApply`;
  - the root `AGENTS.md`'s `api-gate` row names the format check;
  - `api/build.gradle.kts`: the comment saying the root project has no `check`, and `gate`'s description, which
    names its checks.

### The closing block

- **Carries** the holistic findings, and the handoff.
- **After the operator merges the stack**, the lead opens one pull request that adds block 20's formatting commit, as
  merged, to `.git-blame-ignore-revs`, a rebase merge giving it a new identifier.

## 5. Adjacent backlog items

- **"`.dagger/` is neither linted nor formatted"** stays open: TypeScript, its own `package.json`, and Biome rather
  than ktfmt is the tool it needs. The operator's answer "a" of 2026-10-06.

## 6. Out of scope

- **Formatting in an editor** (decision E).
- **ktlint's lint rules**, which overlap detekt's.
