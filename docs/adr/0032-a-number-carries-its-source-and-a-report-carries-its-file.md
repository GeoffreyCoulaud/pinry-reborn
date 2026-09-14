# 0032. A number carries its source, and a report carries its file

Status: Accepted
Date: 2026-09-14
Specification: this document (`agents/workflow.md`, phase 2: a lot whose subject is this process
writes its ADR and no separate spec). Tier Spec. One adversarial review ran on this document in a
named agent and its twenty findings are closed here. One block, so the lead offers the holistic
review's waiver and the operator decides; the handoff records which happened.
Amends: `docs/adr/0028-the-budget-follows-the-ecosystem.md`, decision 3, on its claim that a
background command's completion does not re-invoke an idle agent. Its two waiting rules stand.
Related: `docs/adr/0031-the-gate-builds-once-and-keeps-its-cache.md`, whose Improve pass this is.

## Context

Four frictions, all four observed on lot `0.17.0` and reported at the head of its Improve pass.

### Review reports arrive truncated

Four times on lot `0.17.0`: twice from the specification review of ADR 0031, twice from the holistic
review. It is older than this lot. `docs/handoffs/2026-09-13 - handoff - gate-paid-where-it-can-fail.md`
records lot `0.15.0`'s report naming it, and calls it the one friction of that lot's four that
`0.16.0` left untouched, "a specification review's report arriving truncated twice". At least six
truncations across three lots.

**Why the message cuts is not established here.** Neither the length of a cut report nor the ceiling
it met was measured, and the reports themselves are in the session transcripts, outside the
repository. Volume is the obvious explanation and the evidence does not settle it: two of the four
truncations were the specification review's, whose report is the smaller of the two shapes. The
decision below does not rest on the explanation, only on the fact.

Truncation is also hard to see. The four were caught because the text stopped mid-sentence, which is
luck: a report cut cleanly between two findings reads as a complete report with fewer findings.

Each truncation cost a second message. `agents/workflow.md` permits one to a review, to ask again for
a report that did not arrive, and that permission reads by purpose rather than by count, which is
what made four asks legitimate. The budget meant for a report that never came was spent on a channel
that could not carry one.

### A measurement was transcribed under the wrong label

Lot `0.17.0`'s probe enabled `org.gradle.parallel` and `org.gradle.caching` together. Its warm figure
was written into the first draft of ADR 0031 as the figure for `caching` alone, and one probe was
split across two rows of one table under two different labels. Two decisions rested on it.

The raw evidence was not wrong: the probe printed the configuration it applied and each run carried a
label. The transcription dropped both. The specification review found it by recounting from the
script, which is the safety net working; nothing made the primary harder to get wrong.

### An acceptance criterion was written without checking its number

The block 10 journey of ADR 0031 required the job under seven minutes. In block 10's own run,
34784516313, `verify` took 10 min 06 and the Gradle step alone 8 m 24, so no implementation of that
block could have passed.

The criterion cited no measurement, and worse, **it contradicted one the same document already
carried**: ADR 0031's Context puts the Gradle gate at 8 m 26 s inside a 9 m 19 s `verify`, from run
34778932701. The refuting number was three sections above the criterion when it was written.

### The lead waited for a report instead of looking

The spike's second run, 34787933347, started at 22:49:48 and was green at 22:57:54. It was read the
next morning. The teammate had reported that continuous integration started and stopped, which is
what phase 3 tells it to do, and the lead answered the report and then waited.

`agents/workflow.md`, phase 5, already says the lead looks rather than waits. Repeating it would
change nothing, it having been written before this lot and not followed during it. What failed
mechanically is narrower: the lead arms a background watch when a pull request opens, and the stall
happened on a spike push that had no pull request, so nothing was armed at all.

`docs/adr/0028-the-budget-follows-the-ecosystem.md`, decision 3, states that a background command's
completion does not re-invoke an idle agent. **That is not true of the lead in this harness**: the
Bash tool's own description says a backgrounded command "keeps running across turns and re-invokes
you when it exits", and every background command of lot `0.17.0` did. ADR 0028's observation was
about a **teammate**, which its Context records: on block 11 the teammate started a monitor, ended
its turn, and nothing re-invoked it. Nothing here refutes that half, and nothing here establishes it
either.

## Decision

1. **A review writes its report to `.reviews/<lot>-<mandate>.md` and returns only the path, the
   counts by severity, the three findings it would fix first, and the marker line.** Both mandates
   change: `agents/reviews/spec.md` and `agents/reviews/holistic.md`. `.reviews/` goes in
   `.gitignore`, so the path satisfies "Stay inside the repository" and no closing block's `git add`
   can carry a review onto `main`. The lead reads the file, where nothing truncates.
2. **Every message a review returns ends with a marker line**, not only one carrying a report. The
   failure decision 1 leaves is the short return message being cut, and that message is not a report,
   so a marker conditioned on reports would never be present when it is needed.
3. **Every measured figure in a dated document carries the identifier of what produced it**: the run
   id, the probe's label, the pull request, the file. In `agents/writing.md`, Rules. **It binds
   documents written from this lot on**; the dated ones already written are append-only and cannot be
   brought into conformance.
4. **A journey's numeric threshold names the measurement that sets it**, or it is comparative, or it
   carries no number. In `agents/workflow.md`, phase 2, beside the rule that the block table numbers
   its blocks by tens, which is where the block table's own rules live.
5. **The lead arms a watch on the run at the moment a start is reported, whether or not a pull
   request exists, and arms it before answering the report.** Phase 5 carries both halves, neither
   being written anywhere today: `gh pr checks <number> --watch` where a pull request exists, and
   `gh run watch <id> --exit-status` otherwise, the id coming from
   `gh run list --branch <branch> --limit 1 --json databaseId -q '.[0].databaseId'` because stop 3
   carries no id. A run that has already concluded is read rather than watched, `gh run watch`
   refusing it.
6. **ADR 0028's decision 3 is amended on its claim about background commands, and
   `agents/workflow.md` is corrected with it.** The frozen document takes the amendment on its
   `Status:` line alone, which is how this repository has twice amended a frozen ADR (`32bb7af3` on
   0028, `ceecb882` on 0023); the living document, phase 5's Detail paragraph, carries the refuted
   sentence verbatim and is where an agent reads it. The correction covers the lead and not the
   teammate. `agents/workflow.md`'s "A monitor is never the mechanism" keeps its force and gains the
   distinction it lacked: it binds the teammate, whose turn ends, and not the lead, which decision 5
   has arming one.

## Consequences

**Decision 1 is robust to why the channel cut.** It removes the channel from the path whatever the
mechanism, and decision 2 makes a cut visible if one still happens. What rests on the unverified
explanation is only decision 2's necessity, and it is cheap enough to keep either way.

**A review's findings stop being quotable from the conversation.** They live in an ignored file the
lead reads and closes into the document under review. A report nobody acts on leaves no trace, which
is the property they already had arriving in a message, and `dagger call prose` reads tracked files
only, so nothing in the gate sees them.

**Decisions 3 and 4 are one rule read twice.** Neither adds a check that runs: both make a wrong
number visible to the next reader, which is what caught the mislabelled row on lot `0.17.0` and what
would have caught the seven-minute threshold.

**What does not change.** The gate's perimeter: nothing here runs. And the four stops of phase 3:
decision 5 adds work to the lead, not a fifth stop to the teammate.

## Block table

| Block | Branch | Content | Journeys |
|---|---|---|---|
| 10 | `docs/a-number-carries-its-source` | Decisions 1 to 6: `agents/reviews/spec.md`, `agents/reviews/holistic.md`, `.gitignore`, `agents/writing.md`, `agents/workflow.md` phases 2 and 5, ADR 0028's `Status:` line, and the backlog item under Adjacent | Both mandates name `.reviews/<lot>-<mandate>.md`, the four parts of the return message and the marker line, and neither still asks for the findings in that message; `.gitignore` ignores `.reviews/` and `git status --porcelain` is empty with a report present; `agents/writing.md` carries the source rule under Rules with its start clause; `agents/workflow.md` carries the threshold rule in phase 2 and both watch commands in phase 5, and its phase 5 Detail no longer carries the refuted sentence while "A monitor is never the mechanism" reads as binding the teammate; ADR 0028's `Status:` line names this document and its body is untouched; `docs/backlog.md` carries the item below |

One block. No production line: `agents/` and `docs/` are markdown and `.gitignore` is configuration,
so the whole diff sits inside the 600 and the production bounds have no subject.

**What no journey establishes.** Nothing here observes a review writing a file or a marker line
arriving in anger; the first exercise of the new mandates is the next lot's specification review. The
only trial this lot gets is the review of this document, which ran under the same shape by its brief
rather than by the mandates, and returned twenty findings complete with its marker line.

## Adjacent

No open backlog item is adjacent to this lot: `docs/backlog.md`'s `P1` band holds client and import
work, and its `P2` band persistence and operational debt. This lot files one.

**The `pre-push` hook runs the full gate on a throwaway branch.** Lot `0.17.0` paid it on four such
pushes, two for the spike and one each for pull requests #125 and #127, at the local gate's 5 m 16 s
cold or 3 m 12 s warm (ADR 0031's probe table). Decision 8 of ADR 0031 exempted deletions and not
this. Left open rather than fixed here: the exemption would have to read the branch's name or its
fate, and a hook that trusts a naming convention is a hole in the gate rather than a shortcut through
it. Block 10 files it.
