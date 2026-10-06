# Handoff: ktfmt formats the Kotlin code

Date: 2026-10-06
Tier: Spec. Specification `docs/specs/2026-10-06-ktfmt-formats-the-kotlin-code.md`; ADR
`docs/adr/0053-ktfmt-formats-the-kotlin-code.md`; specification review `.reviews/0.51.0-spec.md`.
Lot `0.51.0`, one stack of 2 code blocks: 10 `refactor/the-import-integration-test-splits` (#356), 20
`chore/ktfmt-formats-the-kotlin-code` (#357), then the closing block.

## Current state

- **The API's gate refuses Kotlin that ktfmt would change**: ktfmt 0.64, `kotlinlang` style, 120 columns, run by
  Spotless 8.10.3 on every module's sources and every project's `*.gradle.kts`, the root's included. Each module's
  `spotlessCheck` reaches `gate` through `check`; `gate` depends on the root's by name. `./gradlew spotlessApply`
  fixes.
- **The code is formatted once**, in block 20's second commit (`7a1e741c` before the merge), which the lead adds to
  `.git-blame-ignore-revs` after the merge, as merged.
- **`MeImportIntegrationTest` is split** into four classes over an abstract `ImportIntegrationTest`, so detekt's
  `LargeClass` still accepts each after ktfmt (block 10). (Corrected: the base is `MeImportFixtures` since the
  closing block, and `MeImportSweepIntegrationTest` extends it too.)
- **A ktfmt bump arrives alone**: Dependabot's `gradle` group excludes `com.facebook:ktfmt`, and `api/AGENTS.md`'s
  Gotchas say its reformatting goes in a commit of its own, listed in `.git-blame-ignore-revs` (closing block).
- **No editor setting is committed**: `api/.idea/ktlint-plugin.xml` is gone.

## Evidence

- Block 10: `dagger call gate` green at `5e9b2b34`, 173 s; budget 1 691 lines, 6 files against `main`, passed under
  decision D (#356). `:api-application:test --rerun` counts 377 tests before (`c495514c`) and after, the sorted test
  names identical. `git diff --color-moved=plain --color-moved-ws=allow-indentation-change HEAD~1 HEAD` leaves 20
  added and 14 deleted lines uncoloured: class headers, 13 visibility changes, a section comment, an unused import.
  Both checks shown able to fail on a planted defect (#356's report).
- Block 20: `dagger call gate` green at `8bfeecd3`, 4 m 52 s, the log listing the root's `:spotlessCheck` and each
  module's; budget 11 789 lines, 600 files against block 10, passed under decision D; commit 1 alone 47 lines, 7
  files, commit 3 alone 23 lines, 8 files (#357).
- `./gradlew spotlessApply` run on `0e22a656` in a clone reproduces `7a1e741c` (`git diff --quiet`), and run again
  on `7a1e741c` changes nothing.
- Three planted defects, each refused by `./gradlew gate` and removed: `"--as=$maxAddressSpace","--"` in
  `ProcessRunner.kt` (`:api-utilities:spotlessKotlinCheck`); `given().authenticatedAs(auth)` on one line in
  `PinUpdaterIntegrationTest.kt:127` (`:api-application:spotlessKotlinCheck`); `val planted = listOf(1,2)` in
  `api/build.gradle.kts` (the root's `:spotlessKotlinGradleCheck`).
- Specification section 2 against Spotless: `7a1e741c` changes 594 files, +9 879 / -8 079. 588 of them are
  byte-identical to the review's Spotless trial at `4ae64ef1` (`cmp`); the others are block 10's five new files and
  `api/build.gradle.kts`. 13 lines past 120 after the formatting, 11 imports and the 2 strings section 2 names; detekt
  reports the 8 findings section 2 names, `LargeClass` absent, and none after commit 3.

## Departures from the specification

- Block 10: 13 visibility changes to `protected` and a section comment stay uncoloured beside the imports and class headers
  the specification expected, the price of sharing helpers through a base class.
- Block 20: three `@throws` tags became a "Throws [X] when ..." sentence, as `ImageTransformer.kt` writes it: ktfmt
  puts a blank line before a tag, so a summary and a tag cannot hold in four lines.
- Block 20: `.gitignore`'s comment says "the shared files below" rather than a corrected count (`agents/writing.md`).
- Block 20: the gate's description, written past 120 columns in commit 1, is wrapped by commit 2 rather than
  shortened in commit 1, after the refusal below.

## Pitfalls

- **The auto-mode classifier refused `git stash && git stash drop`** (block 20, discarding the formatter's output to
  amend commit 1) as an irreversible local destruction. Not retried. Commit first, then reproduce in a clone.
- **`.claude/hooks/evidence-guard.py` refuses `sed -i`, a Python script on stdin, and a redirection outside
  `$TMPDIR`**: export `TMPDIR` under the scratchpad before redirecting a log.
- **Spotless applies Gradle's `base` plugin to the root project**, which now has `check`, `build`, `assemble` and
  `clean`. The root's `check` is outside `gate`.
- **`MeImportSweepIntegrationTest` keeps private `openImport` and `uploadChunk`** close to the base's, left alone
  because merging them changes code (block 10). The base's KDoc still says "The round trip is the point of it".
  (Corrected: both fixed in the closing block, see the holistic review below.)
- **Gradle warns that `project(":detekt-rules")` as a dependency notation fails in Gradle 10**
  (`api/build.gradle.kts`, the `detektPlugins` line). Predates the lot, untouched.

## Tier-2 questions

None, in either block.

## What is not validated

- Dependabot proposing a ktfmt bump from the catalog's `com.facebook:ktfmt` entry, in a pull request of its own:
  the first ktfmt release after the merge shows it.
- That `gh stack submit` ran the `pre-push` gate on block 10's push: its output shows no gate run (#356's report).

## The holistic review

`.reviews/0.51.0-holistic.md`, over `lot/0.50.0-the-tile-plays-and-the-offset-steps-finer..ca407d10`: no CRITICAL,
no MAJOR, 7 MINOR, every one fixed in the closing block.

- **The base's KDoc described the old suite**, "The round trip is the point of it": it now says what the base holds,
  the wire path and seeding the suites share. Fixed.
- **The base was named `ImportIntegrationTest`**, a `*Test` holding no test: renamed `MeImportFixtures`, as the
  repository names a split base (`PinRepositoryFixtures`). Fixed.
- **`MeImportSweepIntegrationTest` duplicated the base's `openImport` and `uploadChunk`**: it extends the base and
  calls them, and reads the base's `importRepository` and `importsConfig`. Fixed.
- **Commit 3's cut of `AuthenticationAttemptKey.forLogin`'s KDoc dropped the reason for digesting**: the reason is
  back, the ADR pointer shortened to "ADR 0013, decision 4". Fixed.
- **A ktfmt bump would land inside the grouped Gradle update**, its reformatting mixed with the other bumps:
  `.github/dependabot.yml` excludes `com.facebook:ktfmt` from the `gradle` group (`exclude-patterns`, GitHub Docs,
  "Dependabot options reference", `groups`), and `api/AGENTS.md` gains the Gotcha. Fixed, both ways.
- **ADR 0053 named a `ktfmt("0.64")` literal and 590 files**: corrected to the catalog's version and 594 files, the
  specification's decision B corrected for the Dependabot group. Fixed.
- **`settings.gradle.kts` spread its modules over one `include` each**, ktfmt putting a blank line between calls:
  one `include` call, one module per line. Fixed.

## The backlog

"The Kotlin code has no formatter" deleted in block 20. "`.dagger/` is neither linted nor formatted" stays open, the
operator's answer "a" of 2026-10-06 (specification section 5).

## The lot's counts

Read with `gh run list --branch <branch>` on the lot's three branches, before the closing block's push.

- Fix-backs: 1, block 20, for this handoff (`ca407d10`). Its push cancelled the run on `8bfeecd3` (37531422234),
  replaced by 37531708373.
- Cascaded rebases: 0.
- Runs re-triggered by a cascade: 0. Block 10 ran once (37529088530).
- The operator's reading of the bodies: no remark so far.

## Next step

The operator's review of the stack, the closing block included, and `gh stack merge --rebase`. After the
merge, the lead opens the pull request adding the formatting commit, as merged, to `.git-blame-ignore-revs`, and
tags `lot/0.51.0-ktfmt-formats-the-kotlin-code`.
