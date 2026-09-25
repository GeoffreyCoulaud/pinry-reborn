# 0043. Blocks stack, and a pull request is written for a tech lead

Status: Accepted
Date: 2026-09-25
Amends: `docs/adr/0018-a-block-is-a-pull-request.md`, decision 2 (in series, no stacking);
`docs/adr/0023-act-in-a-teammate-per-block.md`, decision 1 (a teammate spawned from `main` after the
previous merge, stopped at its own merge), decision 5 (the report is the pull request's body) and
decision 6 (the teammate marks ready); `docs/adr/0019-review-before-the-pull-request.md`, decision 3
(back to draft on a red run or a requested change); `docs/adr/0028-the-budget-follows-the-ecosystem.md`,
decision 6 (the holistic review on `main`, after the last merge). *(Corrected: ADR 0019's decision 2
too, which opens a pull request as a draft; decision 6 below amends it.)*
Evidence: the folder `docs/adr/0043-blocks-stack-and-a-pull-request-is-written-for-a-tech-lead/`.
Review: `.reviews/pull-requests-read-by-a-tech-lead-spec.md`, 0 CRITICAL, 5 MAJOR and 11 MINOR,
closed in this document.

## Context

Lot 0.38.0 (`lot/0.38.0-the-data-travels`, pull requests #219 to #229) ran as an experiment the
operator asked for: every block stacked on the previous one's branch with `gh stack` (github/gh-stack
v0.1.1, a pre-1.0 extension), the next block starting as soon as the previous one's pull request was
open, and the operator reviewing the whole stack after the fact. From the specification's first
commit (`45236961`, 11:47:31Z) to the stack's merge (`gh pr view <n> --json mergedAt`, 18:20:46Z to
18:20:53Z) took 6 h 33 min, against 7 h 51 min for lot 0.36.0 (`883c3581` 13:02:25Z to `05d546a3`
20:52:59Z). Two blocks split past the budget at the cost of one or two extra branches and no wait. No
review comment sent a block back, so the lot ran no fix-back and no cascaded rebase: **the cost side
of stacking was not exercised.**

The operator's reading: fewer interruptions and an easy review, each pull request being small in
scope and size; but the bodies were unreadable. They were the block's report (evidence, fixes,
questions, pitfalls, departures), 45, 44 and 61 lines for #219, #224 and #229
(`gh pr view <n> --json body --jq .body | wc -l`), written for the handoff and not for a reader. The
operator asked for a body a tech lead can review without the specification: the context, the why,
then the how, never the what; under 50 lines; a diagram where it replaces prose. They first named a
body "de 50 lignes ou plus, et/ou avec des tableaux" a bad smell, then corrected it while the
instructions were drafted: "Pas un hard requirement, trop précis. [...] parfois un tableau peut être
utile, comme un diagramme. L'important est de faciliter la relecture". The line bound stays, and
tables are allowed.

The body's rules were chosen by an experiment. **The bench**: three pull requests of that lot, #219
(the API and the contract), #224 (the web application's upload) and #229 (the closing block); for
each, its diff, the specification as it stood at the pull request's head commit, the three
`AGENTS.md`, and a note of the facts the diff does not show (`notes/`). By the lead's account, every
writer was an agent of this session's model, Opus 5.5, reading those files alone, and the quiz
reference was written and validated by the operator before any writer ran; the folder lands in one
commit, so git cannot order them. The arms differ only by their instructions (`instructions/`).
**The measures**: a quiz, where an agent playing a tech lead reads the visible body alone and answers
"context", "why" and "how", scored 0 to 2 each against the reference (`ground-truth.md`); the facts
of the note kept anywhere in the body, the collapsed report included; the visible text lines; and the
operator's blind ranking of anonymised descriptions on a reading page, the measure that decides.

**Round 1**, two descriptions per pull request and arm (`round-1/`):

| Arm | Operator's mean rank (of 6) | Quiz (of 6) | Facts kept in full (partial, lost) | Visible text lines |
|---|---|---|---|---|
| Control, the rule of lot 0.38.0 | 5.5 | 4.50 | 100 % (0, 0) | 39 |
| V1, the operator's criteria as rules | 3.2 | 5.17 | 99 % (1, 0) | 21 |
| V2, the same rules each with its reason | 1.8 | 5.33 | 96 % (4, 0) | 21 |

The operator's notes: #219's descriptions were all verbose for a change that size, and #229's
diagrams too complex to read. **Round 2** (`round-2/`) set V2 against V2+, V2 with those two points
(length fitted to the change, one small diagram in the form that fits):

| Arm | Operator's mean rank (of 4) | Quiz (of 6) | Facts kept in full (partial, lost) | Visible text lines |
|---|---|---|---|---|
| V2 | 3.5 | 5.50 | 96 % (4, 0) | 18 |
| V2+ | 1.5 | 5.33 | 96 % (4, 0) | 17 |

V2+ took the operator's first two places on all three pull requests. The visible mermaid diagrams,
in non-blank lines (`scripts/diagrams.py` over `round-2/descriptions/`): V2 5 and 5 on #219, 12 and
10 on #224, 15 and 13 on #229; V2+ none and 4, 5 and 5, 10 and 10. The gain is most likely the
diagrams, though V2+ also drops V2's sentence arguing for them, and on #229 they stay state diagrams,
the form the operator suspected. The length rule changed nothing measurable, on a bench whose
smallest change is a block of 163 lines. The quiz does not separate the two arms.

## Decision

1. **A lot's blocks stack, in the one working tree.** Each block's branch starts from the previous
   block's, and its pull request targets that branch; the lead creates it with `gh stack add`, and a
   teammate may add the branches of its own split. One teammate works at a time. Only the lead
   rewrites the stack.
2. **The stack is built, then reviewed.** The next block starts once the previous one's gate is
   green locally and its pull request is open as a draft, not once it has merged. The operator's
   review begins when the whole stack is written, the holistic review having read its top; a comment
   that arrives earlier waits for the last block to be pushed. A block's teammate stays idle rather
   than stopped until its pull request merges, so a comment reaches the agent that wrote the code.
3. **A fix-back is made in the layer it concerns, one at a time.** The block's teammate checks its
   branch out (`gh stack checkout <branch>`), commits the fix and runs the gate; the lead cascades
   with `gh stack rebase --upstack` and pushes with `gh stack push`, which re-runs continuous
   integration above. On a conflict, the lead aborts (`gh stack rebase --abort`), and the teammate of
   the branch in conflict rebases it onto its new parent, resolves, runs the gate and stops; the
   cascade then resumes from that branch. Every teammate whose branch moved is told, and reads its
   diff against its new parent again before any further work. This is the path GitHub documents
   ("Reviewing stacked pull requests") and the one its own agent example follows
   ("Turn one giant AI-generated pull request to a reviewable stack", GitHub Blog, 2026).
4. **The budget is measured against the parent branch**, the command of `agents/workflow.md` with
   `<parent>...HEAD` in place of `main...HEAD`.
5. **A block that changes what the web application shows is read headless before its push**, in its
   own Verify.
6. **No draft stage.** A pull request is opened ready for review (`gh stack submit --auto --open`),
   and a red run goes back to Verify without a return to draft. What is ready to review is the
   stack, not one pull request: the lead tells the operator when the whole stack, closing block
   included, has its runs green. `gh stack merge` refuses a draft anyway.
7. **The holistic review reads the top of the stack before the operator's review**, and the closing
   block stacks on top. The operator merges the whole stack at once (`gh stack merge --rebase`), and
   the lead tags the lot.
8. **The pull request's body is written for a tech lead, by the rules of V2+**, which
   `agents/workflow.md` carries as they stand in `instructions/v2plus.md`.
9. **The block's report goes last in the body, collapsed**, under
   `<details><summary>Block report, for the handoff</summary>`: evidence, departures, tier-1 fixes,
   tier-2 questions with their answers, pitfalls, what was not verified. The handoff is written from
   these reports, and counts per lot the fix-backs, the cascaded rebases, the runs they re-triggered,
   and the operator's reading of the bodies.

**Fails if** a lot's cascaded rebases re-trigger more continuous integration runs than the lot has
blocks, as its handoff counts them: a stack that costs more runs than a series would. Or if the
operator's reading of the bodies, in a handoff, calls them unreadable again, which reopens the
experiment on the same bench.

## Consequences

- `agents/workflow.md` is rewritten in "Phases" (a block's branch), Act, Verify, Integrate and Wrap
  (d), `agents/reviews/holistic.md` reads the top of the stack, and `AGENTS.md`'s merge gotcha names
  `gh stack merge --rebase`. The status lines of ADR 0018, 0019, 0023 and 0028 name this ADR.
- **A body no longer carries its evidence where the reader sees it.** Continuous integration and the
  stack are shown by GitHub; the gate, the budget and the rest are in the collapsed report.
- **The fix-back path is unmeasured**: decision 3 is a protocol no lot has run yet.
- **No worktrees, and so no concurrent gates.** A worktree per block was considered and dropped on
  2026-09-25: `gh stack` 0.1.1 refuses to rebase a branch checked out in another worktree ("est déjà
  utilisé par l'arbre-de-travail") and does not see its stack from inside one ("is not part of a
  stack"), measured on two throwaway local branches; the sources that pair agents with worktrees use
  one worktree per independent task, never one per layer of a stack. Building before reviewing
  removes the case worktrees were for, a fix-back while a block above is still being written.
- **`gh stack init` checks the top branch out**, so a commit meant for a lower branch lands on the
  top one unless the lead switches back first (the same measurement).
- **The experiment's limits**: one operator, three pull requests of one lot, two samples per arm and
  pull request, one model, readers of the same model as the writers, and a quiz reference written by
  the lead who also wrote the instructions. The reading page showed each description's blind id, and
  `b598` named `v2-219-b` in both rounds; the operator ranked it 1, then 4, so any anchoring ran
  against the result. The "why" of #224 scored 1 of 2 in 11 of 12 descriptions: they said the import
  was impossible without the block, not that a 20 GiB archive must survive cuts and screen changes,
  which only `b420` (V2, round 2) said. That gap is left to a later round, on this bench.
- **Whether `gh stack submit` runs the `pre-push` hook** was not settled in lot 0.38.0, its output
  showing no gate. This lot's block settles it with `GIT_TRACE=1 gh stack push 2>&1 | grep -i hook`
  and records the answer in its handoff. *(Corrected: that command prints nothing either way, gh-stack keeping
  git's standard error; the handoff settles it from the process tree during the push: the hook runs.)*
- The scripts under `scripts/`, as they stood for round 2, ran in the session's scratchpad, whose
  layout (`out/`, `answers/`, `eval/`) differs from this folder's; round 1's `stats.json` came from
  an earlier version of `prepare.py`. They document the method rather than run here as they are.

## Block table

| Block | Branch | What its checks have to fail on |
|---|---|---|
| 10 | `docs/pull-requests-read-by-a-tech-lead` | Phase 5 of `agents/workflow.md` carries the bullets of `instructions/v2plus.md` verbatim (a `diff` of the extracted section against the file is empty). `grep -n 'in series\|off \`main\`\|from \`main\` once' agents/workflow.md` prints nothing. The budget command reads `<parent>...HEAD`. Decisions 1 to 7 and 9 each have their bullet in the phase they govern. `agents/reviews/holistic.md` no longer says "every code block merged". `grep -n -i 'draft\|mark.*ready' agents/workflow.md` finds no draft stage. The `Status:` lines of ADR 0018, 0019, 0023 and 0028 name 0043, and `AGENTS.md` names `gh stack merge --rebase`. Whether `gh stack submit` runs the `pre-push` hook is answered in the handoff with the command that settled it. `dagger call gate` green. This block carries this ADR |

**Adjacent backlog items**: none (`grep -n -i 'stack\|pull request\|pre-push' docs/backlog.md` finds no
item). A lot of one block: the holistic review is offered to the operator rather than dispatched.
