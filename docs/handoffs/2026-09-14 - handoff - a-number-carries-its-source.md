# Handoff: a number carries its source, and a report carries its file

Date: 2026-09-14
Branch: `docs/a-number-carries-its-source`, block 10, the lot's only code block
Decision: `docs/adr/0032-a-number-carries-its-source-and-a-report-carries-its-file.md`, which is also
this lot's specification (`agents/workflow.md`, phase 2)
Tier: Spec, implemented in a teammate. The specification review ran in a named agent and returned
twenty findings, 1 CRITICAL, 8 MAJOR and 11 MINOR (`.reviews/0.18.0-spec.md`), all closed in the ADR
before the operator read it.

## Current state

A review's report no longer travels through the message channel, which truncated it at least six
times across three lots. It goes to `.reviews/<lot>-<mandate>.md`, an ignored directory, and the
message carries four things and a marker line. A measured figure in a dated document now carries the
identifier of what produced it, a journey's numeric threshold names the measurement that sets it, and
the lead arms a watch on a run the moment a start is reported instead of waiting for a report.

## What was built

**Decisions 1 and 2, both review mandates.** `agents/reviews/spec.md` and `agents/reviews/holistic.md`
each write their report to `.reviews/<lot>-<mandate>.md` and return the path, the counts by severity,
the three findings to fix first, and the line `END OF MESSAGE`. That marker is on every message a
review returns, report or not: the failure the file leaves is the short return message being cut, and
that message is not a report. `.gitignore` ignores `.reviews/`, so the path stays inside the
repository and no closing block's `git add` can carry a review onto `main`.

**Decision 3, `agents/writing.md`.** Under Rules: every measured figure in a dated document carries
the run id, the probe's label, the pull request or the file it came from. It binds documents written
from lot `0.18.0` on, the dated ones already written being append-only.

**Decision 4, `agents/workflow.md` phase 2.** A journey's numeric threshold names the measurement that
sets it, or it is comparative, or it carries no number. It sits beside the rule that a block table
numbers its blocks by tens, which is where the block table's own rules live.

**Decision 5, `agents/workflow.md` phase 5.** The lead arms the watch before answering the report:
`gh pr checks <number> --watch` where a pull request exists, `gh run watch <id> --exit-status`
otherwise, with the command that reads the id. A concluded run is read rather than watched.

**Decision 6, ADR 0028 and phase 5's Detail.** ADR 0028 is frozen, so it takes the amendment on its
`Status:` line alone and its body is untouched, as `32bb7af3` and `ceecb882` did before. The living
document is where the refuted sentence lived: phase 5's Detail no longer says a background command's
completion does not re-invoke an idle agent, and the bullet reads "a monitor is never the
**teammate's** mechanism", which is the distinction it lacked.

**The style conversion, not in the block table.** `agents/writing.md`, `agents/reviews/spec.md` and
`agents/reviews/holistic.md` now state their mandate before their argument:
`docs/adr/0029-a-workflow-phase-states-its-mandate-before-its-argument.md`, decision 5, has a document
under `agents/` conform when something next touches it, and this block touches all three. Every
mandate keeps its letter; the justification and the illustrations moved into the `**Detail.**`
paragraph of their section. `agents/engineering.md` is the one document under `agents/` still
unconverted, nothing here touching it.

**The backlog gains one item**, the `pre-push` hook running the full gate on a throwaway branch, in
the `P1` band beside the other process debt. No open item was adjacent to this lot's subject.

## Pitfalls

- **The marker line's text is `END OF MESSAGE`**, chosen here and written in both mandates. Nothing
  reads it mechanically: the lead notices its absence, or it does nothing at all.
- **A report lives outside every check the repository owns.** `dagger call prose` reads tracked files
  only, so nothing in the gate sees `.reviews/`, and a finding nobody closes leaves no trace. That is
  the property the findings already had arriving in a message.
- **Decision 3 binds forward and cannot be checked backward.** Every dated document already written
  breaks it, the lot `0.17.0` handoff included, and none of them can be brought into conformance.
- **`gh run watch` refuses a run that has already concluded.** Phase 5 says to read such a run rather
  than watch it; the failure otherwise is the watch exiting immediately with an error the lead may
  read as a red run.

## What is not validated

- **A review writing a file, or a marker line arriving in anger.** Nothing in this block exercises
  either. The first exercise of the new mandates is the next lot's specification review.
- **The only trial this lot gets** is the review of ADR 0032 itself, which ran under decision 1's
  shape by its brief rather than by the mandates, and returned its twenty findings complete with its
  marker line. That is one report through one channel, not the mandates being read and followed.
- **That the channel truncates for the reason anyone thinks.** Neither the length of a cut report nor
  the ceiling it met was measured, and the reports are in session transcripts, outside the repository.
  Decision 1 removes the channel whatever the mechanism; the explanation stays unestablished.
- **Decision 5 preventing the stall it is written for.** No run has been watched under it. What would
  show it working is a spike push with no pull request whose run the lead reads within minutes of it
  settling.
- **Whether ADR 0028's observation holds for a teammate.** The amendment covers the lead alone. The
  teammate half, block 11's monitor that never re-invoked it, is neither refuted nor re-established
  here, which is why the stop for continuous integration is still the teammate's rule.
- **The holistic review.** This lot is one block, so `docs/adr/0030-the-gate-is-paid-where-it-can-fail.md`,
  decision 6, has the lead offer the waiver and the operator decide. The closing block corrects this
  sentence with what happened.

## Next step

Wrap: the waiver offered or the holistic review dispatched, then the closing block with the backlog
reconciled and this handoff corrected, then the annotated `lot/0.18.0-*` tag on the closing merge.

The thing to watch is the next lot's specification review: whether its report reaches
`.reviews/<lot>-spec.md` and whether the marker line is at the end of the message that names it. A
message that arrives without the marker is the truncation this lot could never see.
