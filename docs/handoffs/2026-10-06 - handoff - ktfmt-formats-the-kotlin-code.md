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
  `LargeClass` still accepts each after ktfmt (block 10).
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
- **Gradle warns that `project(":detekt-rules")` as a dependency notation fails in Gradle 10**
  (`api/build.gradle.kts`, the `detektPlugins` line). Predates the lot, untouched.

## Tier-2 questions

None, in either block.

## What is not validated

- Dependabot proposing a ktfmt bump from the catalog's `com.facebook:ktfmt` entry: the next weekly run shows it.
- That `gh stack submit` ran the `pre-push` gate on block 10's push: its output shows no gate run (#356's report).

## The holistic review

To be filled in Wrap: a lot of two blocks gets it.

## The backlog

"The Kotlin code has no formatter" deleted in block 20. "`.dagger/` is neither linted nor formatted" stays open, the
operator's answer "a" of 2026-10-06 (specification section 5).

## The lot's counts

Read with `gh run list --branch <branch>` before block 20's first fix-back was pushed; to be completed before the
merge.

- Fix-backs: 1, block 20, for this handoff.
- Cascaded rebases: 0.
- Runs re-triggered by a cascade: 0.
- The operator's reading of the bodies: no remark so far.

## Next step

The holistic review and the closing block, then the operator's review and `gh stack merge --rebase`. After the
merge, the lead opens the pull request adding the formatting commit, as merged, to `.git-blame-ignore-revs`, and
tags `lot/0.51.0-ktfmt-formats-the-kotlin-code`.
