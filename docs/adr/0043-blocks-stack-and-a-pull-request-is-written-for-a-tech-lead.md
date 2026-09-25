# 0043. Blocks stack, and a pull request is written for a tech lead

Status: Accepted
Date: 2026-09-25
Amends: `docs/adr/0018-a-block-is-a-pull-request.md`, decision 2 (in series, no stacking);
`docs/adr/0023-act-in-a-teammate-per-block.md`, decision 1 (a teammate spawned from `main` after the
previous merge, stopped at its own merge), decision 5 (the report is the pull request's body) and
decision 6 (the teammate marks ready); `docs/adr/0028-the-budget-follows-the-ecosystem.md`,
decision 6 (the holistic review on `main`, after the last merge).
Evidence: the folder `docs/adr/0043-blocks-stack-and-a-pull-request-is-written-for-a-tech-lead/`.

## Context

Lot 0.38.0 (`lot/0.38.0-the-data-travels`, pull requests #219 to #229) ran as an experiment the
operator asked for: every block stacked on the previous one's branch with `gh stack` (github/gh-stack
v0.1.1), the next block starting as soon as the previous one's pull request was open, and the
operator reviewing the whole stack after the fact. The handoff of that lot
(`docs/handoffs/2026-09-25 - handoff - the-data-travels.md`) records the figures: about 6 h 25 min
from the specification's first commit to the merge, against 7 h 51 min for lot 0.36.0; two blocks
split past the budget at the cost of one branch each and no wait; zero cascaded rebases, since no
review comment sent a block back.

The operator's reading: fewer interruptions and an easy review, each pull request being small in
scope and size; but the bodies were unreadable. They were the block's report (evidence, fixes,
questions, pitfalls, departures), 45, 44 and 61 lines for #219, #224 and #229
(`gh pr view <n> --json body | wc -l`), written for the handoff and not for a reader.
The operator asked for a body a tech lead can review without the specification: the context, the
why, then the how, never the what; concise; a diagram where it replaces prose.

The body's rules were chosen by an experiment, not by opinion. **The bench**: three pull requests of
that lot, #219 (the API and the contract), #224 (the web application's upload) and #229 (the closing
block); for each, its diff, the specification as it stood at the pull request's head commit, the
three `AGENTS.md`, and a note of the facts the diff does not show (`notes/`). Every writer was an
Opus 5.5 agent reading those files alone, the arms differing only by their instructions
(`instructions/`). **The measures**: a quiz, where an agent playing a tech lead reads the visible
body alone and answers "context", "why" and "how", scored 0 to 2 each against a reference the lead
wrote before any writer ran and the operator validated (`ground-truth.md`); the facts of the note
kept anywhere in the body, the collapsed report included; the visible text lines; and the operator's
blind ranking of anonymised descriptions on a reading page, the only measure the operator decided on.

**Round 1**, two descriptions per pull request and arm (`round-1/`):

| Arm | Operator's mean rank (of 6) | Quiz (of 6) | Facts kept | Visible text lines |
|---|---|---|---|---|
| Control, the rule of lot 0.38.0 | 5.5 | 4.50 | 100 % | 39 |
| V1, the operator's criteria as rules | 3.2 | 5.17 | 99 % | 21 |
| V2, the same rules each with its reason | 1.8 | 5.33 | 96 %, none lost | 21 |

The operator's notes: #219's descriptions were all verbose for a change that size, and #229's
diagrams too complex to read. **Round 2** (`round-2/`) set V2 against V2+, V2 with those two points
(length fitted to the change, one small diagram in the form that fits):

| Arm | Operator's mean rank (of 4) | Quiz (of 6) | Facts kept | Visible text lines | Diagram size (mermaid lines) |
|---|---|---|---|---|---|
| V2 | 3.5 | 5.50 | 96 % | 18 | 5 to 15 |
| V2+ | 1.5 | 5.33 | 96 % | 17 | 0 to 10 |

V2+ took the operator's first two places on all three pull requests. Its gain is the diagrams, about
half the size; the length rule changed nothing measurable on a bench whose smallest change is still
a block of 163 lines.

## Decision

1. **A lot's blocks stack.** Each block's branch starts from the previous block's, and its pull
   request targets that branch; the lead creates them with `gh stack add`, and a teammate may add
   the branches of its own split. Only the lead rewrites the stack (`gh stack rebase --upstack`,
   `gh stack sync`).
2. **The next block starts once the previous one's gate is green locally and its pull request is
   open as a draft**, not once it has merged. One teammate works at a time in the shared tree; a
   block's teammate stays idle rather than stopped until its pull request merges, so a comment
   reaches the agent that wrote the code. A fix-back pauses the working teammate at a commit, lets
   the lower teammate commit and gate the fix, and resumes after the lead's cascade.
3. **The budget is measured against the parent branch**, the command of `agents/workflow.md` with
   `<parent>...HEAD` in place of `main...HEAD`.
4. **A block that touches the interface is read headless before its push**, in its own Verify, never
   as a separate step that holds the tree.
5. **The lead marks a pull request ready** when its run is green; the teammate has moved on.
6. **The holistic review reads the top of the stack before the operator's review**, and the closing
   block stacks on top. The operator merges the whole stack at once (`gh stack merge --rebase`), and
   the lead tags the lot.
7. **The pull request's body is written for a tech lead, by the rules of V2+**, which
   `agents/workflow.md` carries as they stand in `instructions/v2plus.md`.
8. **The block's report goes last in the body, collapsed**, under
   `<details><summary>Block report, for the handoff</summary>`: evidence, departures, tier-1 fixes,
   tier-2 questions with their answers, pitfalls, what was not verified. The handoff is written from
   these reports.

**Fails if** a stack's review comments start sending blocks back often enough that the cascades cost
more than the waits they replace: the handoff counts fix-backs and cascaded rebases per lot. And if
the operator finds the bodies unreadable again, which reopens the experiment on the same bench.

## Consequences

- `agents/workflow.md` is rewritten in "Phases" (a block's branch), Act, Verify, Integrate and Wrap,
  and `agents/reviews/holistic.md` reads the top of the stack. ADR 0018 decision 2 and ADR 0023
  decisions 1, 5 and 6 are amended as the header states; their status lines say so.
- **A body no longer carries its evidence where the reader sees it.** Continuous integration and the
  stack are shown by GitHub; the gate, the budget and the rest are in the collapsed report.
- **The experiment's limits**: one operator, three pull requests of one lot, two samples per arm
  and pull request, one model, readers of the same model as the writers, and a quiz reference
  written by the lead who also wrote the instructions. The "why" of #224 scored 1 of 2 in every arm
  of both rounds: no description said that without the block importing stays impossible from the
  web application. That weakness is common to all the instructions tested and is left to a later
  round, on this bench.
- **Whether `gh stack submit` runs the `pre-push` hook** was not settled in lot 0.38.0, its output
  showing no gate. It is settled in this lot's block and recorded in its handoff.
- The scripts under `scripts/` ran in the session's scratchpad, whose layout (`out/`, `answers/`,
  `eval/`) differs from this folder's; they document the method rather than run here as they are.
