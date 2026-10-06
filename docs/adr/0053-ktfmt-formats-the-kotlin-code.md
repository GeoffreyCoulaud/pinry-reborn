# 0053. ktfmt formats the Kotlin code

Status: Accepted
Date: 2026-10-06
Specification: `docs/specs/2026-10-06-ktfmt-formats-the-kotlin-code.md`.
Written by the lead in Spec, carried by block 10.

## Context

The API's gate formats nothing: detekt carries no formatting rule and ktlint is an IDE setting alone. The operator's
review of #340 found a call chain half split across lines and asked for a formatter. Every answer below is the
operator's of 2026-10-06, in Discuss; the figures are the specification's section 2.

## Decision

1. **ktfmt, in its `kotlinlang` style.** ktfmt rebuilds the whole layout from the syntax tree, as Biome does for the
   clients, so a program has one layout whatever its author wrote. ktlint fixes what one of its rules targets and
   keeps any other layout: its `ktlint_official` style fixes the reviewed chain as ktfmt does, but leaves split a
   chain that fits on one line (`PinUpdaterIntegrationTest.kt:73-75`), which ktfmt joins. Both have an IntelliJ
   plugin, so the editor decides nothing.
2. **Spotless runs it**, because it pins ktfmt's version in the build (`ktfmt("0.64")`), where ktfmt-gradle's
   documentation does not say how its ktfmt is chosen. (Corrected: the build reads that version from the version
   catalog, `ktfmt(ktfmtVersion)`, so that Dependabot sees the pin; a ktfmt bump arrives in a pull request of its
   own.) `spotlessCheck` joins `check`, and `spotlessApply` is the fix.
3. **120 columns**, the bound detekt's `MaxLineLength` already holds. At ktfmt's default 100, 851 lines stay past
   that width, 630 of them test names in backticks, which no formatter breaks and only a rename shortens. Either
   detekt drops to 100 and those are renamed, or the two tools hold different widths.

## Consequences

- One commit reformats 590 files. (Corrected: 594, `7a1e741c`, block 10's new files and `api/build.gradle.kts`
  coming on top.) `.git-blame-ignore-revs` lists it, so `git blame` skips it.
- detekt still reads what ktfmt leaves: a string past 120 columns, and a KDoc that ktfmt's reflowing, a blank line
  before its first tag included, pushes past four lines.
- The ktfmt command line is not idempotent on one file, `ProcessRunner.kt`; Spotless writes its stable form.
- No editor setting is committed. `api/.idea/ktlint-plugin.xml` goes, its mode reformatting against ktfmt on save.
