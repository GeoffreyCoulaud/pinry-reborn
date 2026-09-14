# Review mandate: specification

**Artefact: a specification**, block table included. Run once, in an agent the lead dispatches by
name, before the operator reads it.

**This document states its mandate before its argument**
(`docs/adr/0029-a-workflow-phase-states-its-mandate-before-its-argument.md`, decision 1). The bullets
bind. The `**Detail.**` paragraph that closes a section explains and binds nothing.

## What you deliver

- **Judge two things and settle a third**: what the document **claims to be true**, whether its
  **acceptance criteria can be failed**, and whether the **decisions it settles have a record**.
- **Write your report to `.reviews/<lot>-<mandate>.md`**, the lot the brief names and `spec` as the
  mandate.
- **Report findings there as `SEVERITY | file:line | issue | suggested fix`**, most severe first,
  SEVERITY one of `CRITICAL`, `MAJOR`, `MINOR`. Say plainly if a part finds nothing.
- **Return four things in your message and nothing else**: the report's path, the counts by severity,
  the three findings you would fix first, and the marker line.
- **End every message you return with the line `END OF MESSAGE`**, whether or not it carries a
  report.
- **Do not edit anything.** Stay inside the repository.

**Detail.** The findings are closed in the document before the operator reads it. The name is there
so a report that does not arrive can be asked for again: that is the only second message you will
ever get. The report goes to a file because the message channel truncates and a report cut cleanly
between two findings reads as a complete report with fewer findings
(`docs/adr/0032-a-number-carries-its-source-and-a-report-carries-its-file.md`, decisions 1 and 2);
`.reviews/` is in `.gitignore`, so writing there stays inside the repository and no closing block's
`git add` can carry a review onto `main`. The marker line is on every message and not only on one
carrying a report, because the failure the file leaves is the short return message being cut, and
that message is not a report.

## Evidence

- **Severity follows what rests on the claim**: false and a block depends on it, MAJOR; false and a
  decision was taken on it, CRITICAL.
- **Enumerate the factual claims**: every statement about how something behaves, a library, the
  build, the database, a class in this repository. Goals and intentions are not your subject.
- **Find the evidence or produce it.** A claim carries the command that established it or the source
  it cites. Where it carries neither, measure it yourself: read the source, run the query, grep the
  call sites.
- **A claim you could not settle is a finding**: report it as unverified with the command that would
  settle it.
- **Counted evidence is recounted** from the code, never read from the document. Report the command
  and the number it gave.
- **A dated measurement is not a current one.** Re-run it; when the numbers differ, the finding is
  against the document under review, and the stale source is named so it can be corrected too.
- **A spike proves nothing until it isolates its variable.** Ask what else could have produced the
  observed result.
- **Read the document against itself**: two sentences that cannot both be true, a count stated twice
  with two values, a claim contradicting the ADR or the backlog item it derives from.
- **Name the claim that decides**: the one claim the central decision rests on, and what happens to
  the work if it is false, whatever your verdict on it.

**Detail.** Counts are where documents lie most often. A claim quoting an earlier document is
evidence it was true once, which is why a dated measurement is re-run. A spike that leaves the old
path in place has not tested the new one. A design derived from a false premise is wrong in the most
expensive way, planned around before anyone measures.

## Falsifiability

- **Name the output that would prove each criterion wrong**: the command, the status code, the row,
  the file. Where you cannot, the criterion is the finding.
- **Ask whether it is already satisfied.** Run it against the tree as it is.
- **Ask whether the observable discriminates**: what other state produces the same observation.
- **Ask whether it names the observable or the instrument.**
- **Ask whether the property checked matches the property claimed.**
- **Ask how absence is observed**, and over what window: no leak, no log, nothing on disk.
- **Ask for out of scope's observable too.** It is a set of claims about what will not change; name
  how a reader would notice if one did.

**Detail.** A criterion nobody can fail is declared done against a green gate with nothing
established. The six questions after the first are the shapes that failure takes, each from a
criterion this repository accepted once. A criterion the tree already meets tests nothing: "the
generator reports no change" was offered as proof, and it reports no change on an untouched tree too.
An assertion meant to require that a migration creates an index was satisfied by one that drops it,
both mentioning the name. "The repository test passes" names an instrument, which moves whenever the
test is edited. A bound on entries is not a bound on memory; a unique index is not uniqueness if the
column is nullable; a grep is not a structural test.

## Decision record

- **Say whether the decisions this specification settles were recorded as an ADR.**
- **Treat as architectural** a specification that picks a library or a library setting, a storage
  format, a protocol between two components, a boundary, a public surface or an error contract.
- **Test the one-line justification given for an absent ADR** against that list, and say for each
  such decision where the document sends it: an ADR it names, an existing ADR, or nothing.
