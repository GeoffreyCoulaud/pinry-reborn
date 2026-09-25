# 0023. Act runs in a teammate per block, and the lead keeps the lot's thread

Status: Accepted; amended by `docs/adr/0028-the-budget-follows-the-ecosystem.md`: decision 2's two
reviews are dispatched as named agents rather than plain unnamed subagents; decision 7's holistic
review moves to `main` at the head of Wrap, with one destination for its findings and no commit
range to freeze; and two consequences go, the context criterion retired by that ADR's decision 7 and
the foreground wait, which moves into `agents/workflow.md` phase 5 and splits in two, the gate
staying in the foreground and the wait for continuous integration stopping the teammate. That last
one gives decision 5 a fourth stop, "the run has started", and turns decision 6's wait for
continuous integration into a stop the lead ends. Then by
`docs/adr/0043-blocks-stack-and-a-pull-request-is-written-for-a-tech-lead.md`: decision 1's teammate
starts on the previous block's branch once that block's pull request is open, and stays idle until
the stack merges; decision 5's report goes last in the pull request's body, collapsed, and its stop at
"ready" goes with the draft; decision 6's teammate opens its pull request ready for review. Decisions
3 and 4 stand as written.
Date: 2026-09-07
Specification: this document. A lot whose subject is this process writes its ADR and no separate
spec (`agents/workflow.md`, phase 2). Tier Spec, one block: the specification review ran on this
document, and the holistic review was waived by the operator for this documents-only block
(Discuss, question 2), a stated exception as ADR 0019's lot made for its third angle.
Amends: `docs/adr/0020-two-reviews-and-an-inline-act.md`, decision 2 (in tier Spec, Act is no longer
inline); `docs/adr/0018-a-block-is-a-pull-request.md`, decision 4 and its consequence "Inline Act
puts implementation in the main loop's context"; ADR 0020's consequence that no review table is
restored, narrowed by the Reviews column of the tier table. Everything else in both stands.
Related: `docs/adr/0014-review-budget-upstream.md` (the session transcripts as the measurement
source, and the reviews as plain subagents, which this keeps),
`docs/adr/0010-review-finding-dispositions.md`.

## Context

**One session carried twelve pull requests, and its context carried all of them.** The P2 debt lot
(`docs/handoffs/2026-09-05 - handoff - p2-debt-elimination.md`) ran in one session, from the
operator's first message on 2026-09-05 to the last merge on 2026-09-07. Its transcript, session
`b8f4fb54` under the project's transcript directory, outside the repository and read under an
authorisation the operator gave on 2026-08-13 (Consequences, last item), gives for the main loop
alone:

| Measure | Value |
|---|---|
| Model calls (distinct message ids; a streamed response's blocks repeat its usage) | 578 |
| Context per call (input, cache read and cache write summed), median / maximum | 451 k / 694 k tokens |
| Context at block 1's branch, Discuss done and the spec unwritten | 284 k |
| Context at the spec's approval | 356 k |
| Context at block 1's pull request | 394 k |
| Growth per block, from one `gh pr create` to the next | +23 k to +125 k |
| The block that followed the compaction, restarting at 48 k | +238 k, re-reading what the compaction dropped |
| Compactions | one, `/compact` typed by the operator after the comments on block 5's pull request, at 663 k |
| Input tokens summed over the lot, raw / normalised | 248 Mtok / 32.2 Mtok |
| The two reviews, calls and raw / normalised | spec 12 calls, 1.4 / 0.4 Mtok; holistic 13 calls, 2.1 / 0.6 Mtok |

Normalised means input 1x, cache write 1.25x, cache read 0.1x, output 5x, the convention of
`docs/specs/2026-08-13-review-regime-rework.md`. A transcript records one line per content block of
a response, each repeating the response's usage; ADR 0014's figures came from a script of the same
kind as this lot's first one, and whether they counted lines or responses is unverified here.

At block 5 the context held blocks 1 to 5 in full, their reads, their gate outputs, their diffs, at
663 k, and the operator compacted it by hand; at block 10 it held blocks 6 to 9 the same way, at
689 k. Neither set was needed to write the block in hand, and every call re-read it. The operator's
words: by the end of block 10, block 2 serves nothing and takes context for nothing.

**Cost is not the argument.** The cache absorbs the re-reading: 32.2 Mtok normalised for the whole
main loop, against 1.0 for the two reviews together. What grows is the context each call attends
to, and what it costs is one compaction every five blocks and a model working at 600 k tokens of
which most is dead.

**What the operator said to the implementer from the spec's approval to the end: twenty turns, the
approval included.** Fourteen merge notices for twelve merges (the first was announced three times),
two "comments left on the pull request", one tier-2 answer (a single digit, choosing among three
options offered), one `/compact`, and one request to delete local branches. The conversation the inline Act was kept for is real, and it is thin: the implementer needs
to be able to stop and ask, not to be the agent the operator talks to all day.

**The premise of ADR 0018 decision 4 and ADR 0020 decision 2 is false for a named agent.** Both say
a subagent cannot interrupt, its final message being its return value. That describes the shape the
project used for reviews, one brief in and one report out, with no name. It does not describe the
tool. Two probes run during this lot, the named background agents `spike-resume` and `spike-idle`
whose transcripts sit under this session's `subagents/` directory, established this:

1. A teammate's `SendMessage` to `main` reaches the lead. Sent while the lead was inside a turn, it
   was absorbed into that turn and rendered in full in the operator's terminal; sent while the lead
   was idle, waiting for the operator, it started a lead turn by itself and was rendered folded, a
   message from the teammate that the operator expands.
2. A teammate ending its turn is announced to the lead and the operator as "Teammate @name
   finished", with the first line of its final message, truncated. The first probe's three notices
   reached the lead together, one to seventy minutes after they were produced: the notice is not
   the channel a question travels by, the message to `main` is, and its first line has to carry the
   question.
3. A `SendMessage` addressed to a teammate's name resumed it with its context intact: it answered
   with the code word it had just been given and the first word of the brief it had received before
   stopping. It did so again after `TaskStop`, which the tool's own answer said in so many words.
4. A teammate receives the project instructions: asked for the first cell of the module table and
   the first heading of the instructions in its context, it answered `api-domain` and `AGENTS.md`.
5. A background command's completion did not re-invoke an idle teammate: a `sleep 120` run in the
   background ended, and the probe stayed idle until messaged.

So a named agent stops, asks, waits and resumes, which is what tier 2 requires. What the probes did
not establish is named under Consequences.

**The mailbox cost of 2026-08-13 was paid for reviews, and it stands.** On the attempt-limiting lot,
naming review agents turned one-shot reports into correspondents: idle notices, reports that had to
be asked for, a scope extension landing after the report it should have changed. The rule that came
out of it, plain subagents for reviews, was written in `agents/workflow.md` and left with commit
`b3a8671a`; this paragraph is now its record. This ADR changes nothing in what the reviews read;
each mandate gains the words "the lead dispatches". The conversation an implementer needs is
exactly what a review does not.

**ADR 0018's isolation was never bought.** Its consequence says "the isolation that subagent
dispatch bought is now bought by the session boundary instead, which is stronger: a session ends at
a merge and the next block starts from nothing but `main` and the specification". No session ended
at a merge. Twelve pull requests, one session: the boundary that was to isolate the blocks never
happened, because ending the session also ends the lot's thread, the Discuss, the approved spec and
the operator's answers.

## Decision

1. **In tier Spec, each block is implemented by a teammate: a named background agent, one per
   block, spawned by the lead from `main` after the previous block's pull request merges, and
   stopped by name when the operator reports this block's merge.** One teammate lives at a time. A
   stopped teammate's name is never messaged again, since a message resumes it. The lead is the main
   loop, the agent the operator talks to.

2. **The lead keeps the lot's thread and nothing else.** Discuss, Spec, the dispatch of both reviews
   as plain unnamed subagents (specification and holistic), the spawning and stopping of teammates,
   the relay of the operator's answers. It writes no block of a tier Spec lot. A tier Direct lot is
   one block with no spec, and the lead writes it inline: a brief for it would have to restate the
   request, which is the plan-for-subagents ADR 0018 removed.

3. **The brief is a pointer, not a plan.** It names the block's row in the specification's block
   table, the specification's path, `AGENTS.md`, the branch name and the report shape of decision 5.
   It restates nothing the specification says. ADR 0018's finding stands: a plan written for a
   subagent drifts from the spec it derives from, so the teammate reads the spec.

4. **Tier 2 stops the teammate, and the lead never answers it.** On discovery, the teammate sends
   the question to `main` with `SendMessage`, its first line carrying the question, stating the
   defect, the size of the fix and the block it would join, and ends its turn. The operator reads it
   directly. Their answer goes to the lead, who sends it to the teammate by name, verbatim, and the
   teammate resumes. A blocker takes the same path: a gate that will not go green, a permission
   denied. A denied permission is reported, never routed through the lead, whose permissions are
   the same and whose doing it would launder the denial.

5. **The teammate speaks only when it stops, and its report has a fixed shape.** Three stops: a
   tier-2 question, a blocker, the pull request ready, and ready again after each change the human
   asks for. Between two stops the lead answers "in progress" from `ListAgents` and nothing else.
   The report at "ready" is both the teammate's final message and the pull request's body, in five
   parts: evidence (gate, continuous integration, the diff measured against the budget), tier-1
   fixes made, tier-2 questions with the answers received, pitfalls, departures from the block
   table.

6. **The teammate runs Act, Verify and Integrate through to the merge.** TDD, the gate, the push,
   the draft pull request, the wait for continuous integration, the mark ready, the link sent to
   `main`; then the fixes the human asks for, back through Verify. The lead's part of Integrate is
   one act: on "merged", `TaskStop` by name, the shared working tree brought back with
   `git switch main && git pull --ff-only && git branch -d <branch>`, then the next teammate from
   `main`.

7. **The handoff is written from the lot's pull requests, not from the lead's memory.** The last
   code block's teammate reads the bodies of the lot's merged pull requests (`gh pr view`) and writes
   the handoff from them and from its own block. The lead dispatches the holistic review when that
   teammate reports the gate green and the handoff written, over the frozen commit range; findings
   against the current block go back to its teammate by name, the rest into the closing block's
   brief.

## Consequences

- **What the lead's context holds is now bounded by the lot's thread**: Discuss, the spec, the
  reviews' reports, one brief and one report per block, the operator's turns. The P2 lot's 356 k at
  the spec's approval is the part this ADR does not touch; the 23 k to 125 k per block is what it
  removes. **The next tier Spec lot measures both from its transcript, and the regime fails if the
  lead's context grows by 23 k or more per block**, the P2 minimum, measured between consecutive
  teammate spawns; a brief, a report and the relays should cost about 10 k. A compaction during the
  lot fails it too.
- **Each block pays a fresh start**: reading `AGENTS.md`'s pointers, the spec, the block's files.
  Unmeasured; the next lot measures it. A teammate receives `AGENTS.md` through `.claude/CLAUDE.md`
  without being told to read it (Context, point 4).
- **The operator no longer sees the work scroll.** What they see is the teammate's three stops and
  the lead's relays. The operator chose that over a progress feed at fixed milestones (Discuss,
  question 4), the mailbox noise of 2026-08-13 being the reason.
- **One mechanic is not validated, and the first lot tests it**: how a background agent's permission
  prompt reaches the operator. Both probes used tools that ask for none. If a prompt never surfaces,
  the teammate sits until the operator notices its silence; decision 4's blocker path is the
  fallback where the denial is visible to the teammate, and `ListAgents` showing it busy for too
  long is the only sign where it is not.
- **A teammate waits in the foreground.** A background command's completion does not re-invoke it
  (Context, point 5), so the gate and the wait for continuous integration run as foreground
  commands, under the tool's ten-minute ceiling, or the teammate stops and the lead tells it when
  the run has settled.
- **A merged block's teammate stays reachable after `TaskStop`.** Tested on the probe: a message to
  its name after the stop was answered by the tool with "was not running; resumed it as an
  in-process teammate with 22 prior messages", on a branch that no longer matters. Decision 1
  forbids the message; nothing enforces it. The precedent is the 2026-08-05 incident, recorded
  nowhere else in the repository: a remediation agent messaged mid-flight ran `git commit --amend`
  while a second agent worked in the same clone, and the amend landed on the second agent's commit.
- **The tier-2 answer crosses two hops**, operator to lead to teammate, where the question crossed
  none. A relay that paraphrases changes the answer; decision 4 says verbatim.
- **ADR 0018's consequence about the session boundary is corrected here**, as the dated regime
  allows: it described an isolation that one session for twelve pull requests never delivered. The
  teammate boundary delivers it per block and keeps the lead's thread, which the session boundary
  would have destroyed.
- **Series is unchanged, and so is its cost.** The operator is still the pacer; a block still waits
  for the previous merge. Nothing here parallelises anything, and the idle time between merges is
  the same as before.
- **The measurements are not reproducible from the repository**, as ADR 0014's were not: transcript
  files and throwaway scripts. The table names the session and the fields summed. Recounting means
  reading outside the repository, which `agents/workflow.md` forbids; the operator lifted that for
  measuring the harness on 2026-08-13, in session, and this sentence is the first place the
  repository records it.

## Block table

One block, `docs/act-in-a-teammate-per-block`: this ADR; `agents/workflow.md` (Phases: the two
roles, the tier table restored, phases 2 to 6); `agents/reviews/spec.md` and
`agents/reviews/holistic.md` (dispatched by the lead); the status lines of ADR 0018 and 0020; the
handoff. Tier Spec, one pull request; the specification review ran on this document and its
findings are closed below, and the holistic review was waived by the operator for this
documents-only block, which the tier table does not provide for: a stated exception.

**Adjacent defect, tier 2, taken.** `AGENTS.md` sends the reader to the tiers in
`agents/workflow.md`; commit `b3a8671a` cut the tier table and left one occurrence, "Tier Direct
skips it", with no definition. Asked at discovery (Discuss, question 5), the operator chose to
restore a two-row table in this block.

**Adjacent backlog items: none.** No item in `docs/backlog.md` concerns the process. The item
"measure what review costs and what it returns" left the backlog with the P2 lot, refused; the
measurements above are of the main loop, not of the reviews, and reopen nothing.

## Review of this document

The specification review (`agents/reviews/spec.md`) ran on this document before the operator read
it, in a plain subagent authorised to recount in the transcripts. Six MAJOR and nine MINOR findings,
no CRITICAL, every one closed in this document:

- MAJOR, the counts were per transcript line, not per response: recounted per message id, the unit
  stated in the table, ADR 0014's figures flagged as unverified on the same point.
- MAJOR, 284 k labelled "Discuss and Spec done" while the spec was unwritten at that branch: three
  rows now, branch, approval and first pull request, and the consequence uses 356 k.
- MAJOR, "blocks 2 to 9 in full" contradicted the compaction row: rewritten as blocks 1 to 5 at
  663 k, then blocks 6 to 9 at 689 k.
- MAJOR, the probe had never messaged an idle lead: a second probe did, and the lead turn it started
  is the evidence (Context, point 1); the stop notices' latency and the background-command limit
  were found on the way (points 2 and 5).
- MAJOR, tier Direct declared while the restored table sends a design decision to Spec: tier Spec,
  the holistic review waived as a stated exception.
- MAJOR, nothing could fail "the next lot measures both": 23 k of growth per block, or a compaction,
  fails it.
- MINOR, nine: the compaction at 663 k, not 648 k; the coefficients' source and the transcript
  authorisation named; the mailbox cost and the amend incident given their record here; the
  project-instructions self-report replaced by an answer the brief could not contain; "does not
  touch the mandates" corrected and ADR 0020's consequence narrowed; the working tree's return to
  `main` given to the lead; the resume after `TaskStop` tested instead of asserted; the count on the
  backlog removed; twenty turns and fourteen notices restated exactly.
