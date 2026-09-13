# Handoff: a workflow phase states its mandate before its argument

Date: 2026-09-13
Branch: `docs/workflow-mandate-before-argument`, block 10 of two, one pull request
Decision: `docs/adr/0029-a-workflow-phase-states-its-mandate-before-its-argument.md`
Tier: Spec, implemented in a teammate. The ADR is the specification, phase 2 exempting a lot whose
subject is this process from writing a separate one, and its review ran on it in a named agent. The
holistic review has **not** run when this file is written: it runs at the head of Wrap, on `main`,
over `git diff lot/0.12.0-budget-follows-the-ecosystem..origin/main`, so it reads this handoff after
this block merges and block 20 corrects the file with its findings.

## Current state

`dagger call gate` green at the block's tip, the API's Gradle half in 2 m 58 s. `agents/workflow.md`
is restructured and no rule in it binds differently: the shape changed, the mandates did not. The
form is in force for `agents/*.md` from this block's merge, and the other documents under `agents/`
conform when something next touches them, which is decision 5.

The block writes no code and runs no test: every path it touches is markdown, so strict TDD has no
subject and the production count is zero. The diff is **338 counted lines** against `main`, against a
bound of 600; the ADR and this handoff are dated documents and outside the count, which is the 132
lines the numbers below leave out.

## What was built

- **ADR 0029** as the specification: the mandate-before-argument form, one living owner per closed
  list, the flowchart at the head of Phases, the survival of every mandate, and the form's reach over
  `agents/*.md` with `agents/workflow.md` alone converted here.
- **`agents/workflow.md`, restructured whole.** 184 lines in, 131 out. Every section now leads with
  bullets that bind and closes, where there was an argument to keep, with a `**Detail.**` paragraph
  that binds nothing. The six phases leave the single numbered list for a heading each, `### 1.
  Discuss` to `### 6. Wrap`, so a page and a sentence no longer read as two items of one kind. A
  mermaid flowchart heads `## Phases`, carrying order only, with one line under it for what tier
  Direct skips. Phase 3 states the four stops as a numbered list, which is the one place the
  repository writes them out.
- **`agents/writing.md`**, four lines: the form under Style, with the ADR's pointer and the sentence
  saying which documents are converted and which conform later.
- **`docs/backlog.md`**: the `P1` item this lot exists for is deleted, and the two closed lists it
  wrote out become pointers at `agents/workflow.md`, The backlog. It keeps what is its own, the
  reason a limit is not debt and what this file receives.

## The mandate inventory: old place against new place

Decision 4's test is that no mandate of `git show 9a34219f:agents/workflow.md` is left without a
place. The table maps the old document's sections onto the new one; a row's mandates are unchanged in
wording unless the row says otherwise.

| Old (`9a34219f`) | New | What moved |
|---|---|---|
| `## Scope`, lines 7-20 | `## Scope` | The three tiers keep their letter. Tier 2 loses "the operator prefers being interrupted while the context is live" and "this tier is where the backlog drains" to `**Detail.**`; tier 1 loses "This is the boy-scout rule" to the same place. |
| `## Evidence`, 22-36 | `## Evidence` | Six bullets unchanged. The documentation rule's closing "which is the rule's only observable" becomes the section's one `**Detail.**` sentence. |
| `## Design`, 38-51 | `## Design (how to decide)` | Six bullets unchanged. The pointer at `agents/engineering.md`'s Design invariants and the list of smells move to `**Detail.**`, neither being a rule. |
| `## Phases` preamble, 54-68 | `## Phases`, the flowchart then six bullets | The two roles, the naming of every dispatched agent, the one-brief-one-report discipline and the branch-per-block rule become bullets. "A name buys recoverability" and the two ADR citations move to `**Detail.**`. The old "Discuss and Spec run once for the lot" sentence from line 109 joins them, being the same kind of statement. |
| `### Tiers`, 70-78 | `### Tiers` | The operator's decision and the recommend-the-higher rule split into two bullets. The table is unchanged. |
| `### What a block is`, 80-105 | `### What a block is` | The three conditions keep their letter. "Readable alone" keeps every bound, the strictness, `.dagger/` on the 200, the root with no production line, the both-ecosystems rule and the split-or-justify rule; the exclusions and the prefix partition become their own bullets. Moved to `**Detail.**`: ADR 0028's citation, why markup argued for the 400, why the root has no line, why the partition is by prefix, and "the budget measures what a human rereads". |
| `### The phases` intro, 109-110 | `## Phases`, last bullet | See the preamble row. |
| Phase 1, 112 | `### 1. Discuss` | Split in two bullets, unchanged. |
| Phase 2, 113-122 | `### 2. Spec` | Five bullets: the document, the named adversarial review, the delivery and freeze, the ADR-instead-of-spec exemption, the numbering by tens with the count-is-the-rows rule. Why tens moves to `**Detail.**`. |
| Phase 3, 123-132 | `### 3. Act` | Seven bullets. The four stops become a numbered list under "The teammate speaks only when it stops, and there are four stops", which decision 2 makes the one place they are written out. "Four stops, not three" and the two ADR citations move to `**Detail.**`, with the sentence pointing at phase 5 for the mechanics. |
| Phase 4, 133-137 | `### 4. Verify` | Three bullets: the local branch, the full gate, the handoff on the last code block. Why that block merges like any other and where the holistic review runs move to `**Detail.**`. |
| Phase 5, 138-158 | `### 5. Integrate` | Eleven bullets, the three waiting rules among them. The stop for continuous integration keeps "applies to every run of the block, not to the first alone"; ADR 0019's draft mechanism, the 14.2-minute median and the reason a monitor is never the mechanism move to `**Detail.**`, which also states that phase 3 carries the list of stops and gives no total. |
| Phase 6, 160-181 | `### 6. Wrap` | Seven bullets: the trigger, the review with its one destination and tier Direct's skip, the closing block's (b) to (d), the tag, the report, the split rule, and the delivery-checkpoint rule. Moved to `**Detail.**`: why the tag is not optional, decision 6's citation, the 651-line precedent, and why `release.yml` cannot match a `lot/` tag. |
| `## The backlog`, 183-202 | `## The backlog` | Eight bullets, the four exits and the four bands among them, this being the living document that carries the rules closing both. Moved to `**Detail.**`: ADR 0010's citation, what the backlog receives, the unreadable-entry argument and the argument against a cap. |

Two mechanical checks back it. The bolded spans of both documents, sorted and diffed, leave only
phase names that became headings and two fragments of a bolded code span. Fifty-five phrases taken
from across the old document, one from every mandate whose wording a bullet could quietly have
dropped, all match the new document read as a single flat line, line wrapping being what defeats a
naive `grep` on eight of them.

## Pitfalls

1. **A closed list needs an owner before it needs a pointer.** Decision 2 says the owner is the rule,
   not the mechanics, and the four bands are the case that tests it: `docs/backlog.md` is the file
   the bands structure, which makes it look like the owner, but the rule that closes the list is
   `agents/workflow.md`'s "Banded by nature before priority". The file's own headings are its
   structure, not a second enumeration, so they stay.
2. **Line numbers into `agents/workflow.md` are now stale everywhere they were written.** The ADR
   itself cites `agents/workflow.md:97` and `:130` and `:196`, and the previous lot's documents cite
   others. All are dated documents and none is corrected: they record what was true on their date.
   Cite the section, not the line, in anything living.
3. **`**Detail.**` is load-bearing and easy to overfill.** The failure mode decision 1 names is a
   sentence under it that binds. Two sentences were nearly lost that way and are bullets instead:
   "This step is not optional" for the lot tag, and "A monitor is never the mechanism" in phase 5.

## The specification review's nine findings, and the exit each took

The review ran on the ADR in an agent the lead dispatched by name, before the operator approved it
and before this block started. It reported **one CRITICAL, four MAJOR and four MINOR**. Seven were
fixed outright in the ADR, one was fixed in part and carried two accepted limits into Consequences,
and one was refused with evidence. None became a backlog item.

| # | Severity | Finding | Exit |
|---|----------|---------|------|
| 1 | CRITICAL | Decision 2 said a closed list is written where it is operated, then placed the four stops in phase 5 alone. Phase 3 operates two of them, the tier-2 question and the blocker. The decision contradicted itself in its own sentence. | Fixed in the ADR: the owner is the rule and not the mechanics, so phase 3 owns the enumeration and phase 5 details the two it operates without giving a total. |
| 2 | MAJOR | The published grep did not return what the document said it returned. `grep -rn "four exits\|four stops" agents/ docs/backlog.md` gives two lines, both "four exits"; "Four stops" capitalises and sits at line 130, not 129. | Fixed in the ADR: the command is now `grep -rin "four exits\|four stops\|three stops" agents docs/backlog.md`, three lines in two files, and the line number is corrected. |
| 3 | MAJOR | Decision 2 was unsatisfiable on dated documents. `docs/adr/0023:135` writes "Three stops", which its own status line corrects to four, and an append-only document can never become a pointer. | Fixed in the ADR: a third clause exempts `docs/adr`, `docs/specs` and `docs/handoffs`. The rule binds the living documents alone. |
| 4 | MAJOR | The Related line said ADR 0023 and ADR 0010 own the two closed lists; decision 2 gave ownership to `agents/workflow.md`. Under "exactly one owner" both cannot hold. | Fixed in the ADR: deciding a list and writing its members out are separated, in the Related line and in decision 2's second clause. |
| 5 | MAJOR | No acceptance criterion, for any of the five decisions. | Fixed in part: decisions 1, 2 and 4 each gained a `Fails if`. Two accepted limits go with it, both in Consequences: decision 2's check stays manual, a grep being unable to tell an enumeration from a pointer, so it does not go into `dagger call prose`; and decisions 3 and 5 are placements with no failure mode. |
| 6 | MINOR | The per-phase line table was said to count lines outside the phases, making the factor 14 rather than 22. | Refused, with evidence: the paragraph at `agents/workflow.md:118` is indented three spaces under item 2, which is markdown's own form for a second paragraph of a list item, so it belongs to the phase. The table now says the counts include each item's indented paragraphs, which removes the ambiguity that produced the finding. |
| 7 | MINOR | The "near 400" estimate was right and its justification was not: it cited the production partition, which is not what excludes the ADR and the handoff. | Fixed in the ADR's block table: it cites `agents/workflow.md:97`, which puts the dated documents outside the 600. |
| 8 | MINOR | Decision 5 read as though the review mandates had received nothing, while the same review returned MINOR 4 and MINOR 7 against `agents/reviews/holistic.md`. | Fixed in the ADR: decision 5 names both and says they are wrong instructions rather than buried ones, so its conclusion stands. |
| 9 | MINOR | The `P1` backlog item also asks for "its own spec", and the Specification line reconciled only the handoff's wording. | Fixed in the ADR's Specification line, which now names both. |

Two corrections belong beside the table because they are against the review itself, not against the
ADR.

- **The review's own replacement grep was miscounted.** It proposed
  `grep -rin "four exits\|four stops\|three stops" agents docs` as "5 occurrences, 3 files";
  recounted, that returns 19 lines across 11 files. The ADR scopes the command to
  `agents docs/backlog.md` instead, and says the number that matters is how many hits write the
  members out, not the hit count.
- **The ADR was rewritten in plainer English after the findings were closed**, on the operator's
  reading: 183 lines to 132, shorter sentences, the arguing subordinate clauses cut,
  `Falsifiable by:` become `Fails if`, decision 2's three clauses become bullets. No decision, no
  finding closure and no measurement changed in that rewrite.

## Not validated

- **Nothing here is checkable by the gate.** Decision 2's own consequences say so: a grep cannot tell
  an enumeration from a pointer, and a pattern catching "Four stops, not three" also catches every
  `Related:` line. The mandate inventory above is the artefact a reviewer reads instead, and it was
  built by hand.
- **The form is unproven against a reader.** The signal decision 1 wants is the next lot's holistic
  review returning no finding of the shape MAJOR 1 and MINOR 5 had, and that arrives a lot too late
  to count here.
- **`agents/engineering.md`, `agents/writing.md` and `agents/reviews/*` are not converted**, by
  decision 5. Until something next touches them, `agents/` holds one document in the new form and
  four in the old, which is a mixed corpus a reader meets without warning.
- **The flowchart can go stale** against the text, an accepted limit the ADR records. It carries no
  rule and no count, so a drift in it misleads about order alone.

## Tier-2 questions asked

None. One adjacent change was taken as tier 1 and is named in the pull request's body: the four bands
of the backlog were a second closed list written out in two living documents, exactly what decision 2
forbids, and `docs/backlog.md` now points at `agents/workflow.md` for them as it does for the exits.
One message was sent to the lead that is neither a tier-2 question nor a blocker, the brief having
authorised it: a request for the specification review's nine findings, which no file in the working
tree records and which this handoff has to carry.

## Next step

Wrap. The holistic review at the head of it, in a named agent, over
`git diff lot/0.12.0-budget-follows-the-ecosystem..origin/main` once this block has merged. Then
block 20, `docs/closing-the-workflow-restructure`: the review's findings with their exits, the
backlog reconciled, this file corrected. Then the annotated `lot/0.13.0-*` tag on the closing merge,
pushed, which is what the next lot's review will read.

After the lot: the first ordinary product lot to run under both this form and ADR 0028's budget. The
backlog's `P1` band is where its subject comes from, and the two cheapest candidates in it are the
pull request paying two cold Gradle builds and the catalogue refetching every settled download.
