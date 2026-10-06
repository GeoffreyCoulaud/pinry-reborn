# 0053. ktfmt formats the Kotlin code

Status: Accepted
Date: 2026-10-06
Specification: `docs/specs/2026-10-06-ktfmt-formats-the-kotlin-code.md`.
Written in block 10.

## Context

The API's gate formats nothing: detekt carries no formatting rule and ktlint is an IDE setting alone. The operator's
review of #340 found a call chain half split across lines and asked for a formatter. Every answer below is the
operator's of 2026-10-06, in Discuss; the figures are the specification's section 2.

## Decision

1. **ktfmt, in its `kotlinlang` style.** ktfmt rebuilds the whole layout from the syntax tree, as Biome does for the
   clients, so one program has one layout and no hybrid form survives. ktlint fixes what one of its rules targets and
   leaves any layout no rule covers; its `intellij_idea` style left the reviewed chain untouched. Both have an
   IntelliJ plugin, so the editor decides nothing.
2. **Spotless runs it**, because it pins ktfmt's version in the build (`ktfmt("0.64")`), where ktfmt-gradle's
   documentation does not say how its ktfmt is chosen. `spotlessCheck` joins `check`, and `spotlessApply` is the
   fix.
3. **120 columns**, the bound detekt's `MaxLineLength` already holds. At ktfmt's default 100, 851 lines stay past the
   bound, strings and comments it does not break, so either detekt drops to 100 and those are rewrapped by hand or
   the two tools hold different widths.

## Consequences

- One commit reformats 590 files. `.git-blame-ignore-revs` lists it, so `git blame` skips it.
- detekt still reads what ktfmt leaves: a string or a comment past 120 columns, and a KDoc that ktfmt's reflowing, a blank
  line before its first tag included, pushes past four lines.
- No editor setting is committed. `api/.idea/ktlint-plugin.xml` goes, its mode reformatting against ktfmt on save.
