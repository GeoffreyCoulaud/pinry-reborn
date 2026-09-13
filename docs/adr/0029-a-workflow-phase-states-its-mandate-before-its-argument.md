# 0029. A workflow phase states its mandate before its argument

Status: Accepted
Date: 2026-09-13
Specification: this document (`agents/workflow.md`, phase 2: a lot whose subject is this process
writes its ADR and no separate spec). Tier Spec. The handoff of 2026-09-13 and the backlog item it
filed both ask for "its own spec"; phase 2 is what they meant.
Amends: nothing. The shape of `agents/workflow.md` changes. Its mandates do not.
Related: `docs/adr/0028-the-budget-follows-the-ecosystem.md`, whose review found the defect.
`docs/adr/0023-act-in-a-teammate-per-block.md` and `docs/adr/0010-review-finding-dispositions.md`
decide the two closed lists decision 2 names. Deciding a list is not the same job as writing its
members out.

## Context

### One defect, found twice

The holistic review of lot `0.12.0` returned nine findings. Two are the same defect:

- **MAJOR 1**: phase 3 listed three stops for a teammate. ADR 0023's status line says four.
- **MINOR 5**: the return-to-Verify loop named no stop for the second continuous integration run.

Both rules are stated correctly elsewhere in the document. Both were missed because the paragraph
carrying the rule also carries the argument for it. The operator named the cause: the document states
each step and its justification in one breath.

### The phases differ in length by a factor of twenty-two

`agents/workflow.md` at `9a34219f`:

```
awk 'NR>=112 && NR<=181 {if (/^[0-9]+\. /) {if (n) print n, c; n=$1; c=0} c++} END {print n, c}' agents/workflow.md
```

| Phase | 1 Discuss | 2 Spec | 3 Act | 4 Verify | 5 Integrate | 6 Wrap |
|-------|-----------|--------|-------|----------|-------------|--------|
| Lines | 1         | 10     | 10    | 5        | 22          | 22     |

Counts include each item's indented paragraphs, which markdown makes part of it.

The six sit in one numbered list, so they read as six items of one kind. They are not. Phase 1 is a
sentence, phase 5 is a page. A reader scans the list, takes the shape of the process from phase 1,
and stops somewhere inside phase 5. MINOR 5 lived there.

### A closed list is written out where it is not decided

`grep -rin "four exits\|four stops\|three stops" agents docs/backlog.md` returns three lines in two
files at `9a34219f`. Case-insensitive, because one hit capitalises. Scoped to the living documents,
because a rule cannot bind an append-only one.

- **The four exits of a review finding**: written out at `agents/workflow.md:196` and at
  `docs/backlog.md:23`. `docs/adr/0010` decision 1 decided them.
- **The four stops of a teammate**: written out at `agents/workflow.md:130`, in phase 3. Phase 5
  operates two of them and holds the mechanics of all four.

A mention is not an enumeration. `Related: ... (the four exits a finding takes)` names the list and
stops there. The grep finds the candidates; a reader decides which of them write the members out.

A list written out twice drifts in one copy. MAJOR 1 is that drift.

## Decision

1. **A phase states its mandate first, its argument after.** Short imperative bullets, one rule each,
   no justification. Then a `**Detail.**` paragraph. A reader who stops at the last bullet has read
   every rule that binds them.

   **Fails if** a bullet argues, or a sentence under `**Detail.**` binds. (Corrected: a bullet whose
   mandate carries its justification inside its own letter is not a failure. Decision 4 keeps that
   letter, so the two tests collide on it, and decision 4 wins: "Tier 2 is a question, so the work
   waits for the answer" argues and stays under Scope. What this test catches is a justification the
   bullet could drop without losing a rule.)

2. **A closed list is written out in one living document: the one carrying the rule that closes it.**
   Everywhere else names the list and states no member and no total.

   - **The owner is the rule, not the mechanics.** The four stops go to phase 3, which says the
     teammate speaks only when it stops. Phase 5 details the two it operates and gives no total.
   - **Deciding is not enumerating.** The ADR that decided a list keeps its members.
   - **Dated documents are exempt.** `docs/adr`, `docs/specs` and `docs/handoffs` are append-only, so
     the stale "Three stops" at `docs/adr/0023:135` cannot become a pointer. It records what was
     believed on its date.

   **Fails if** `grep -rin "<the list's words>" agents docs/backlog.md` returns two hits that write
   the members out.

3. **One mermaid flowchart heads `## Phases`.** Order only: Discuss, Spec, a subgraph for the
   per-block series (Act, Verify, Integrate, with the return edge), then Wrap. One diagram and not
   two: the per-block loop drawn twice is decision 2's defect in another medium.

4. **Every mandate survives the rewrite. The prose arguing for one may go.** A rule that binds an
   agent keeps its letter. A sentence explaining why it exists goes when it changes no decision.

   **Fails if** a mandate of `git show 9a34219f:agents/workflow.md` has no place in the new document.
   The pull request body lists the two side by side.

5. **The form binds `agents/*.md`. Only `agents/workflow.md` is converted here.** (Corrected: the
   reach is a document under `agents/`, the review mandates under `agents/reviews/` included. That is
   what `agents/writing.md` records and what this glob excludes.) `agents/writing.md`
   records the rule; the other documents conform when something next touches them. Both findings
   above are in `agents/workflow.md`. The same review hit `agents/reviews/holistic.md` twice, but
   MINOR 4 and MINOR 7 are wrong instructions, not buried ones.

## Consequences

- **The diff is a rewrite, not a set of hunks.** About 200 lines out and about as many in. Decision
  4's inventory is what a reviewer reads instead.
- **No rule changes.** A rule that binds differently after the block is a defect in the block.
- **Decision 2 costs a lookup.** Phase 5 no longer says how many stops there are. It points back to
  phase 3.
- **The flowchart can go stale** against the text. It carries order only, no rule and no count.
  Accepted limit.
- **Tier Direct is not drawn.** One line under the diagram says what it skips.
- **Decision 2's check stays manual.** A grep cannot tell an enumeration from a pointer: a pattern
  catching "Four stops, not three" also catches every `Related:` line. In `dagger call prose` it would
  pass on everything or fail on every pointer. Accepted limit.
- **Decisions 3 and 5 have no failure mode.** They are placements. Only 1, 2 and 4 can be proved
  wrong.
- **None of this proves the document foolproof.** The signal is the next lot's review returning no
  finding of this shape, which arrives too late to count as evidence here.

## Block table

**Block 10**, `docs/workflow-mandate-before-argument`: this ADR; `agents/workflow.md`, restructured
under decisions 1 to 4; `agents/writing.md`, which records decision 5; `docs/backlog.md`, whose `P1`
item closes here and whose four exits become a pointer; the handoff, written here as phase 4
requires. Tier Spec, one pull request.

No production line: the partition of ADR 0028 decision 1 leaves every path here as markdown.
`agents/workflow.md:97` puts the dated documents outside the 600, so this ADR and the handoff do not
count. The estimate is near 400, from `agents/workflow.md`, `agents/writing.md` and
`docs/backlog.md`.

**Block 20, the closing block**, `docs/closing-the-workflow-restructure`: the holistic review's
findings, each with its exit; `docs/backlog.md` reconciled; the handoff corrected. Tier Spec, one
pull request.

**Adjacent backlog items**: one, the `P1` entry this lot exists for, closed in block 10. Nothing else
in the file is adjacent to the form of a process document.
