# ktfmt formats the Kotlin code

Date: 2026-10-06
Status: Draft, for the specification review. Frozen when the lot's last block merges.
Lot: `0.51.0`. Branches: one stack: 10 `chore/ktfmt-formats-the-kotlin-code`, then the closing block.
ADR: `docs/adr/0053-ktfmt-formats-the-kotlin-code.md`, written in block 10. The contract does not change.

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
  columns (`awk 'length > 120'`), strings and comments ktfmt does not break.
- **ktfmt at its default 100 columns** reformats 626 files, +17 526 / -9 210, and leaves 851 lines past 100.
- **ktlint 1.8.0**, for the record: `ktlint_official` reformats 462 files and leaves 22 violations it cannot fix;
  `intellij_idea` reformats 351 and leaves the half split chain of `PinUpdaterIntegrationTest.kt:273` as it is.
- **detekt on ktfmt's 120-column output** (`detektMain detektTest detektTestFixtures --continue`, in a clone at
  `4ae64ef1`): the baselines hold, and 9 findings are new:
  - `CommentCarriesDocumentation`, a KDoc reflowed to 5 or 6 lines (ktfmt rewraps KDoc, and puts a blank line
    before its first tag): `BoardRepositoryInterface.kt:13`,
    `UserDataImportRepositoryInterface.kt:15`, `AuthenticationAttemptKey.kt:23`, `TagSearcher.kt:11`,
    `TokenHasher.kt:6`, `MeImportTestProfile.kt:6`;
  - `MaxLineLength`: `MeImportController.kt:154`, `SharedRefusalsFilter.kt:105`;
  - `LargeClass`: `MeImportIntegrationTest.kt:54`.

## 3. Decisions

The operator's answers of 2026-10-06 in Discuss. ADR 0053 records them with their reasons.

**A. ktfmt, `kotlinlang` style**, over ktlint (ADR 0053, decision 1).

**B. Spotless runs it**, over ktfmt-gradle (decision 2): Spotless 8.10.3, ktfmt pinned at 0.64 in
`api/gradle/libs.versions.toml`.

**C. 120 columns**, detekt's `MaxLineLength` bound, set in the build with `setMaxWidth(120)` (decision 3).

**D. The formatting block may pass the block bounds**, as the Biome lot's decision E did: a configuration commit,
then a commit holding `spotlessApply`'s output alone, recorded in `.git-blame-ignore-revs`.

**E. The editor is out of the lot.** `api/.idea/ktlint-plugin.xml` goes, its `DISTRACT_FREE` mode reformatting
against ktfmt on every save, and nothing replaces it: "sinon ça signifie qu'on doit tripoter la config d'ide depuis
ici, et ce n'est pas le sujet."

## 4. Blocks

No block changes a user path, so none adds a journey. A block's check is shown able to fail: its report names the
defect it planted, the output that refused it, and the planted defect's removal.

| Block | Branch | Subject | Bounds |
|---|---|---|---|
| 10 | `chore/ktfmt-formats-the-kotlin-code` | Spotless and ktfmt in the gate, the code formatted once | Passed, decision D |

### Block 10: ktfmt formats the Kotlin code

- **Does**, in three commits:
  1. the configuration: Spotless applied in `subprojects` with a `kotlin` target and a `kotlinGradle` target, and on
     the root project for `build.gradle.kts` and `settings.gradle.kts`. `spotlessCheck` joins `check` by default
     (Spotless README, `enforceCheck`), so each module's reaches `gate` through `check`; the root project has no
     `check`, so `gate` depends on its `spotlessCheck` by name. `ktlint-plugin.xml` and its `.gitignore` line go;
  2. the output of `./gradlew spotlessApply` and nothing else. Run on the commit's parent, the command reproduces it:
     `git diff` is empty;
  3. section 2's detekt findings fixed by hand, never baselined: each comment cut back to 4 lines, each long line
     rewritten, and `MeImportIntegrationTest` split along the concerns its tests already group by.
- **Measures** whether Spotless at 120 columns gives section 2's figures; a gap is reported, not chased.
- **Proven by**, each planted defect refused by `./gradlew gate`:
  - `listOf(1,2)`, a comma with no space;
  - a chain with its first two calls on one line and the rest one per line.
- **Documents**: `api/AGENTS.md`'s Commands replace "No auto-fix task" with `./gradlew spotlessApply`, and the root
  `AGENTS.md`'s `api-gate` row names the format check.
- **Carries** this specification and ADR 0053, committed by the lead before the block starts.

### The closing block

- **Carries** the holistic findings, the backlog item deleted, and the handoff.
- **After the operator merges the stack**, the lead opens one pull request that adds block 10's formatting commit, as
  merged, to `.git-blame-ignore-revs`, a rebase merge giving it a new identifier.

## 5. Adjacent backlog items

- **"`.dagger/` is neither linted nor formatted"** stays open: TypeScript, its own `package.json`, and Biome rather
  than ktfmt is the tool it needs. The operator's answer "a" of 2026-10-06.

## 6. Out of scope

- **Formatting in an editor** (decision E).
- **ktlint's lint rules**, which overlap detekt's.
