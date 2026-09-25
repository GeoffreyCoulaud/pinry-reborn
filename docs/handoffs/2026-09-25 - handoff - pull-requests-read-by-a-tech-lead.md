# Handoff: pull requests read by a tech lead

Date: 2026-09-25
Specification: `docs/adr/0043-blocks-stack-and-a-pull-request-is-written-for-a-tech-lead.md`, a process lot, its
evidence in the folder beside it.
Blocks, one stack of one: 10 `docs/pull-requests-read-by-a-tech-lead` (this block's pull request), which carries the
ADR.
Written in block 10, the lot's only and last code block, from the lot's history as the lead briefed it and the
ADR's own record; to be corrected in the closing block if one runs.
Tier: Spec. One specification review ran, `.reviews/pull-requests-read-by-a-tech-lead-spec.md`, 0 CRITICAL,
5 MAJOR and 11 MINOR, closed in the ADR. A lot of one block: the holistic review is offered to the operator rather
than dispatched.

## Current state

- **A lot's blocks stack** in the one working tree, each block's branch on the previous one's, created with
  `gh stack add`; the next block starts once the previous one's gate is green and its pull request open
  (`agents/workflow.md`, Phases).
- **The stack is built, then reviewed**: the holistic review reads its top, the closing block stacks on top, and the
  operator reviews and merges the whole stack at once with `gh stack merge --rebase`.
- **No draft stage**: a pull request opens ready for review with `gh stack submit --auto --open`, and a red run goes
  back to Verify.
- **A fix-back is made in the layer it concerns**, then cascaded by the lead with `gh stack rebase --upstack` and
  `gh stack push`. Written, never run.
- **The budget is measured against the block's parent branch**, `<parent>...HEAD`.
- **A pull request's body is written for a tech lead** by the rules of V2+, carried verbatim in phase 5, the block
  report last and collapsed under `<details><summary>Block report, for the handoff</summary>`.
- **The handoff counts, per lot**, the fix-backs, the cascaded rebases, the runs they re-triggered and the operator's
  reading of the bodies: the ADR's failure criteria read those counts.

`agents/reviews/holistic.md` reads `origin/<top branch>`; `AGENTS.md`'s merge gotcha names `gh stack merge --rebase`;
the `Status:` lines of ADR 0018, 0019, 0023 and 0028 name 0043. The ADR's `Amends:` gained a `(Corrected: ...)`:
decision 6 also amends ADR 0019's decision 2, which opened a pull request as a draft.

## What was built, per block

| Block | Pull request | Lines, files against `main` | Continuous integration |
|---|---|---|---|
| 10 | this one | 112, 3 | in its pull request |

`dagger call gate` green locally before the push. No web application screen changed, so no headless reading.

## Tier-2 questions and operator decisions

| Where | Question | Answer |
|---|---|---|
| Discuss | Where the block report goes | A3: last in the body, collapsed |
| Discuss | How the bodies are measured | B1: a quiz scored against a reference, plus the operator's blind reading, which decides |
| Discuss | The arms and the second round | C'1: round 1 tested V1 (the operator's criteria as terse rules) and V2 (the same rules each with its reason), no template; then J1 for round 2 (V2 against V2+) |
| Discuss | The quiz reference | D1: written by the lead before any writer ran, and validated by the operator |
| Discuss | The stacking rules | E1: those practised in lot `0.38.0`, with two adjustments: the headless reading inside each block's Verify, and the ready state handled by the lead, since dropped by the no-draft decision |
| Discuss | Where the experiment's material lives | F1: committed beside the ADR |
| Discuss | The decision rule | G3: none fixed in advance; the operator and the lead decide together from the ranking and the quiz |
| Discuss | What round 2 decides | K1: V2+ adopted as it stands, the "why" gap of #224 left to a later round |
| Discuss | Whether what was not verified shows in the visible body | H''2: no such line visible; it stays in the collapsed report |
| Discuss | The model of the writers and readers | I1: Opus 5.5 everywhere |
| Discuss | A worktree per block | M, then N: tried, measured and dropped for N4, the stack being built then reviewed |
| Discuss, after the specification review | The draft stage | Dropped: no draft, the stack is what is ready to review (decision 6) |

## The experiment

Every figure below is the ADR's, whose Context carries its sources. Three pull requests of lot `0.38.0` were the
bench (#219, #224, #229); the writers were Opus 5.5 agents reading the diff, the specification at the pull request's
head, the three `AGENTS.md` and a note of what the diff does not show; the arms differed by their instructions alone.
Each description was scored by a quiz (a reader agent answering "context", "why" and "how" from the visible body,
0 to 2 each against `ground-truth.md`), by the note's facts kept, by its visible lines, and by the operator's blind
ranking on a reading page.

| Round | Arm | Operator's mean rank | Quiz (of 6) | Facts kept in full (partial, lost) | Visible text lines |
|---|---|---|---|---|---|
| 1 | Control, the rule of lot `0.38.0` | 5.5 of 6 | 4.50 | 100 % (0, 0) | 39 |
| 1 | V1, the operator's criteria as rules | 3.2 of 6 | 5.17 | 99 % (1, 0) | 21 |
| 1 | V2, the same rules each with its reason | 1.8 of 6 | 5.33 | 96 % (4, 0) | 21 |
| 2 | V2 | 3.5 of 4 | 5.50 | 96 % (4, 0) | 18 |
| 2 | V2+, length fitted and one small diagram | 1.5 of 4 | 5.33 | 96 % (4, 0) | 17 |

Round 1 wrote two descriptions per pull request and arm; the operator's notes (`round-1/operator-ranking.json`) found
#219's descriptions verbose for its size and #229's diagrams too complex to read, which is what V2+ answers. V2+ took
the operator's first two places on all three pull requests. The quiz does not separate V2 from V2+.

## The stack's counts

- **Fix-backs**: none at the writing of this handoff.
- **Cascaded rebases, and the runs they re-triggered**: none; a stack of one has nothing above to cascade.
- **The operator's reading of the bodies**: to be filled once the operator has read this pull request.

## The pre-push hook through `gh stack`

**`gh stack push` runs the `pre-push` hook**, and the gate with it. Settled on 2026-09-25 while
`GIT_TRACE=1 gh stack push 2>&1` pushed `29931334`, by `ps -ef` during the push: `gh-stack push` (pid 811713) ran
`/usr/bin/git push origin --force-with-lease=refs/heads/docs/pull-requests-read-by-a-tech-lead: ...`, with no
`--no-verify`, and that `git push` (pid 811777) was the parent of `dagger call gate` (pid 811783), which the hook
`exec`s. The push took the gate's time and then printed `✓ Pushed 1 branches`.

**The ADR's command cannot settle it**: `GIT_TRACE=1 gh stack push 2>&1 | grep -i hook` prints nothing whether or not
the hook runs, gh-stack keeping git's standard error to itself (the full output above was three lines, no trace). The
same silence is why lot `0.38.0` saw no gate through `gh stack submit`.

## Pitfalls

- **The evidence guard refuses shell redirection into files** (`.claude/hooks/evidence-guard.py`): a file is written
  with the edit tool, or by a command whose declared product it is.
- **Seven agent launches failed on "Lock file is already being held"**, and were retried.
- **`gh stack init` checks the top branch out**, so a commit meant for a lower branch lands on the top one unless the
  lead switches back first.
- **`gh stack` 0.1.1 and worktrees do not mix**: it refuses to rebase a branch checked out in another worktree and does
  not see its stack from inside one (ADR 0043, Consequences).
- **`gh stack submit --auto` creates a draft unless given `--open`** (`gh stack submit --help`), and writes no body.

## What is not validated

- **The fix-back path** (decision 3): no lot has run a fix-back or a cascaded rebase yet.
- **The experiment's limits**, which the ADR's Consequences list: one operator, three pull requests of one lot, two
  samples per arm and pull request, one model, readers of the same model as the writers.
- **The "why" of #224** scored 1 of 2 in 11 of 12 descriptions, a gap left to a later round on the same bench.
- **The holistic review**: waived by the operator for this one-block lot ("O1", 2026-09-25), so nothing read the lot
  as a whole beyond the specification review and the gate.

## Next step

The operator reviews and merges this stack of one with `gh stack merge --rebase`, and the lead tags
`lot/0.39.0-pull-requests-read-by-a-tech-lead`. The next lot is the first to run the rewritten phases: its handoff
counts its fix-backs, cascaded rebases and re-triggered runs against its block count, and records the operator's
reading of the bodies.
