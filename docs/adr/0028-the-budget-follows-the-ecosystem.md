# 0028. The block budget follows the ecosystem, and the holistic review follows the last merge

Status: Accepted
Date: 2026-09-13
Specification: this document. A lot whose subject is this process writes its ADR and no separate spec
(`agents/workflow.md`, phase 2). Tier Spec, one block: the specification review ran on this document
in a named agent, and the holistic review runs from `main` after the block merges. This lot is the
first exercise of decisions 4 and 6, on itself. (Corrected: two blocks, 10 and the closing block 20,
which is decision 6's own consequence: the holistic review's findings had no destination until it
existed.)
Amends: `docs/adr/0018-a-block-is-a-pull-request.md`, decision 1 (the production sub-bound becomes
two, one per ecosystem, and gains the partition that makes it countable), decision 3 (a block table
numbers by tens) and decision 8's third bullet (the holistic review leaves the last pull request);
`docs/adr/0019-review-before-the-pull-request.md`, decision 1 (the holistic review no longer runs on
the branch) and, with it, decision 4 and decision 5's second half (Corrected: the commit-range
pinning they restored has nothing left to pin, every block being merged when the review reads them;
the Amends line named decision 1 alone and decision 6 below already retires the range. Corrected
again at block 20: decision 4 falls **whole** and not in its pinning alone. It says two things, and
the first, "the mandates read a branch, not a pull request", is as dead as the second: the one
surviving mandate reads `git diff <previous lot tag>..origin/main`, which is neither);
`docs/adr/0020-two-reviews-and-an-inline-act.md`, decision 1 (a review agent is named,
and the holistic review's artefact moves to `main`) and decision 4 (the count of findings against an
already merged block is retired); `docs/adr/0023-act-in-a-teammate-per-block.md`, decision 2 (a
review agent is named now), decision 7 (one destination for the review's findings), two of its
consequences (the context criterion, retired by decision 7 below; the foreground wait, which moves
into `agents/workflow.md` and is split in two), and with that second one decisions 5 and 6
(Corrected: the wait for continuous integration becomes a fourth stop, so decision 5's three become
four and decision 6's wait is one the lead ends; the Amends line named the consequence alone).
Everything else in all four stands.
Related: `docs/adr/0010-review-finding-dispositions.md` (the four exits a finding takes, unchanged).

## Context

Every measurement below comes from the web application lot, twelve blocks over pull requests 98 to
109, whose handoff is `docs/handoffs/2026-09-11 - handoff - web-application.md`. It is the **second**
lot run under ADR 0023: the first was the monorepo lot
(`docs/handoffs/2026-09-09 - handoff - monorepo-and-pipeline.md`), which ran the regime and did not
take the measurement ADR 0023 asked of it. The criterion decision 7 settles is therefore settled one
lot later than it was owed, which is itself the reason a criterion nobody is bound to run does not
get a replacement.

### The production bound describes Kotlin, not TSX

Insertions per block, excluding the dated documents and the files marked `linguist-generated`, which
is what ADR 0018 decision 1 counts, and split by ecosystem because decision 1 below measures each
against its own bound:

| Pull request | Production, `api/` | Production, `clients/` | Production, `.dagger/` | Total diff |
|---|---|---|---|---|
| 98 | 0 | 75 | 63 | 503 |
| 99 | **232** | 0 | 0 | **778** |
| 100 | 0 | **247** | 0 | 560 |
| 101 | 111 | 0 | 0 | 400 |
| 102 | 51 | 0 | 0 | 221 |
| 103 | 0 | **253** | 0 | 487 |
| 104 | 63 | 0 | 0 | 203 |
| 105 | 179 | 0 | 0 | 541 |
| 106 | 57 | 0 | 0 | 79 |
| 107 | 7 | **393** | 0 | **806** |
| 108 | 50 | 91 | 0 | 551 |
| 109 | 0 | 1 | 0 | 100 |

Counted by `git diff --numstat` over each pull request's rebased range on `main`, partitioned by the
prefixes decision 1 fixes. These figures supersede the ones the lot's specification carries for
blocks 3, 6 and 10 (253 against 262, 247 against 256, 393 against 425): the specification measured at
the first green run on the branch, this table measures the merged range, and block 2 was already
reconciled the same way in the specification itself. (Corrected: the first two pairs were crossed.
Block 3 is `feat/webapp-auth`, pull request 100, **247 against 262**; block 6 is `feat/webapp-grid`,
pull request 103, **253 against 256**. The specification's figures are at
`docs/specs/2026-09-10-web-application.md:591` and `:576`, and the table above already carries the
merged ones in the right rows; only this sentence's pairing was wrong.)

**Six of the lot's blocks are API blocks and five are client code blocks**, the twelfth being the
closing block at one line. On the API's six: median 87, maximum 232. On the clients' five: median
247, maximum 393. **Four blocks passed the 200-line bound and three of them are client blocks.**
Three of the four carried the exemption line ADR 0018 requires at the time they passed it; block 3's
was written at the closing block, which the specification records.

A bound waived on three blocks out of five in an ecosystem is not a bound there. The operator's
reason for a higher one: a view puts on separate lines what a service puts in expressions, and
rereading markup costs less per line than rereading logic. The 600-line total was passed only by the
two blocks that had already passed the production one, so it measures both ecosystems correctly and
does not move.

**No block of this lot is refused by the new bound.** The largest client block is 393. The 400 is a
judgement placed above the observed distribution, not a value read off it, and the honest statement
of what it buys is that it constrains the next lots and refuses nothing in the measured one.

### The lead's context, against the criterion ADR 0023 set

ADR 0023 asked the next tier Spec lot to measure the lead's context growth between consecutive
teammate spawns, and declared the regime failed at 23 k or more per block, or at any compaction
during the lot. Measured from this session's transcript, `255baf9f` under the project's transcript
directory, read under the authorisation of 2026-08-13:

| Measure | P2 debt lot (inline Act) | This lot (teammate per block) |
|---|---|---|
| Context maximum over the lot | 694 k | 372 k |
| Growth between consecutive spawns | 23 k to 125 k | 11 k to 49 k, median 23 k, mean 23.8 k |
| Compactions | 1, at 663 k | 2, neither inside the block series |

**The criterion is failed as written**, on both halves: the mean sits at 23.8 k and there were two
compactions. It also measures the wrong thing, having asked the new regime's mean to beat the old
regime's best block. What the regime did is halve the ceiling and cut the worst block from 125 k to
49 k, and both compactions sit outside the series, one at the end of Spec before the first teammate
and one during Wrap after the last merge; between block 1's spawn and block 12's report there were
none.

Decision 7 is a decision to retire the criterion rather than to pass it, and the narrower perimeter
above is the argument offered for retiring it, not a verdict the original criterion delivered.

The 49 k growth is the holistic review's report landing whole in the lead's context, once per lot.
ADR 0023's estimate of about 10 k per block for a brief, a report and the relays was low by a factor
of two and a half; the rest is the operator's turns, the tier-2 deliberations and the reading of pull
requests, which is the lead's own work and not the relay.

### The channel between the lead and a teammate failed twice, and the rule was known

ADR 0023 states as established, in its Context point 1, that "a teammate's `SendMessage` to `main`
reaches the lead". On block 9 one did not: the teammate had opened its pull request twenty-five
minutes earlier and the lead was still waiting. The lead recovered by looking at the open pull
requests instead of at its mailbox.

On block 11 the teammate started a monitor in the background to await its gate's verdict and ended
its turn, where nothing would re-invoke it; the lead woke it by name. ADR 0023's Consequences had
predicted this and said what to do instead: "a background command's completion does not re-invoke
it, so the gate and the wait for continuous integration run as foreground commands, under the tool's
ten-minute ceiling, or the teammate stops and the lead tells it when the run has settled."

**The rule was right and the agent had it.** The handoff records that every teammate of this lot was
told and one still hit it. So the defect is not that a constraint living in an ADR's consequences
never reaches its agent: it reached this one, in its brief, and did not hold. Writing it into
`agents/workflow.md` phase 5 makes it durable rather than restated per brief, which is necessary and
is not shown to be sufficient. What recovered both incidents is the lead looking at the artefacts,
and that is the half of decision 3 the incidents actually support.

The mechanic ADR 0023 declared unvalidated, how a background agent's permission prompt reaches the
operator, is **still unexercised**: the lot's handoff records no permission prompt, and nothing in
the lot was set up to make one surface, so this is an absence of record rather than an observed
absence.

### Continuous integration does not fit under the tool's ceiling

The same consequence offers the foreground wait as the first branch for continuous integration.
Measured with `gh run list` over the lot's own branches, fourteen runs carried to a conclusion:
**minimum 12.1 minutes, median 14.2, maximum 39.3, and none under ten.** The foreground branch
therefore never applies to continuous integration, and a rule that offers it first misleads the agent
that has to follow it, which is the defect this section is about. It applies to the gate, which a
workstation runs in minutes.

### The holistic review has one recoverable block out of twelve, and had none here

The review ran over `git diff 356fe306..ac5b1412` and reported thirty-one findings, one CRITICAL,
fifteen MAJOR and fifteen MINOR, **all thirty-one against an already merged block**. The operator had
chosen to merge the last code block first, a stated exception, so on this lot zero blocks were
recoverable. It cost one further code block (pull request 108) to close what a teammate still holding
block 10's context would have fixed on its branch, and the CRITICAL reached `main`.

The exception is not the argument. Even run as `agents/workflow.md` phase 4 writes it, the review
reads the whole lot while only the last block is still recoverable: one block out of twelve here, and
the CRITICAL it found was a cross-block defect that no per-block gate catches. The repository
maintained two destinations for the findings of a review whose findings are, by construction, nearly
all against merged code. The count `agents/workflow.md` phase 6 asks for, inherited from ADR 0020
decision 4, was unavailable on this lot and on the one before it: two lots without the instrument.

### A number in the prose goes stale when a block is inserted

The lot inserted two blocks mid-flight, on the operator's decision. Its block table went through four
states, 9 rows then 10, 11 and 12, of which the last added at the end and renumbered nothing. Two
stale numbers survived in the prose around the table, both from the first insertion. Nothing was
reordered and nothing was dropped: the whole defect is that consecutive integers leave no room
between two of them.

### The documentation rule named four libraries, all of them backend

Three claims the lot acted on were false and a teammate refuted each by measurement: that React Aria
offers a start-direction sentinel, that a test `application.properties` shadows production's, and
that declaring a response code is additive. The first two are the specification's, written by the
lead; the third is the holistic review's expectation, which the specification records as corrected.
The rule therefore has to bind every agent, not the lead alone.

The second is a claim about this repository, which the Evidence rule already covers and which no
rewording would have caught. The first is a library claim, and there the rule pointed nowhere: it
read "the source resolves to current upstream docs: Quarkus, Ebean, libvips (vips-ffm), Gradle", an
enumeration written when the repository held one ecosystem. The clients were not excluded by
decision, they were excluded by omission.

## Decision

1. **The production sub-bound follows the ecosystem: under 200 lines under `api/`, under 400 under
   `clients/`.** The 600-line total, and everything else ADR 0018 decision 1 says, is unchanged, and
   both bounds stay strict as that decision writes them. `.dagger/` and the repository root take the
   200: markup was the argument for 400 and there is none there. A block spanning both ecosystems
   measures each against its own bound, so the figure that governs pull request 107 is its 393
   client lines. (Corrected: giving the repository root the 200 bounds nothing. The partition below
   makes production `api/**/src/main/**`, `.dagger/src/**` and `clients/**/src/**`, so no root path
   is ever a production line and only the 600 reaches one. `.dagger/` does have the subject the
   sentence assumed; the root does not, and `agents/workflow.md` says so rather than assigning it a
   bound it cannot use.)

   **Production is counted by prefix**, because the API's tacit `src/main` convention has no
   equivalent on the clients' side: `api/**/src/main/**`, `.dagger/src/**` and `clients/**/src/**`
   are production, less `clients/**/src/journeys/**`, `clients/**/src/test/**` and any `*.test.ts`
   or `*.test.tsx`. Configuration, message catalogues, markdown and build files are outside the
   production count and inside the 600. The partition goes into `agents/workflow.md` so two readers
   counting the same block get the same number.

2. **A block table numbers its blocks by tens.** An insertion takes a number between two existing
   ones, so no reference already written in prose goes stale. The last number no longer gives the
   block count, which the table's own rows give, and a gap means a block was dropped or a number was
   left free, which the table says in the row it keeps or in the line that removes it.

3. **The gate runs in the foreground, the wait for continuous integration stops the teammate, and
   the lead looks rather than waits.** All three go into `agents/workflow.md` phase 5, where the
   teammate reads them:
   - the gate runs as a foreground command, under the tool's ten-minute ceiling;
   - **the wait for continuous integration stops the teammate by default**, no run of the measured
     lot having finished under that ceiling and the median being 14.2 minutes. The teammate reports
     that the run has started and ends its turn; the lead tells it when the run has settled. A
     background command's completion does not re-invoke an idle agent, so a monitor is never the
     mechanism;
   - the lead establishes a teammate's state from the open pull requests, the remote branches and
     `ListAgents`, never from the arrival of a notice, and a teammate whose report draws no answer
     within a few minutes sends it again.

   The first two were already ADR 0023's rule and reached block 11's teammate through its brief
   without holding, so they are moved for durability and are not claimed to be the fix. The third is
   what recovered both incidents, and its second half is the lot's own pitfall promoted from the
   handoff. It buys back a little of the mailbox cost of 2026-08-13, on the one message that had no
   answer, which is the cheapest half of that cost.

4. **Every agent the lead dispatches is named, reviews included, and a review is still not a
   correspondent.** One convention, and the recoverability a name buys: a report that does not
   arrive, or arrives truncated, can be asked for again instead of costing a second full review.
   The mailbox cost recorded in ADR 0023's Context came from the second message, not from the name,
   so the discipline is what carries it: one brief out, one report back, and **the lead never sends a
   review agent a second message except to ask again for a report that did not arrive**.

5. **The documentation rule names no library.** In `agents/workflow.md` under Evidence: for any
   library, command line tool or version-dependent value, whatever the ecosystem, consult the
   current upstream documentation of the thing itself through the documentation source the session
   declares, and name that source when a claim rests on it. The enumeration goes, and the obligation
   to name the source stays, being the rule's only observable.

6. **The holistic review runs at the head of Wrap, from `main`.** The last code block merges like
   any other; then the review runs over `git diff <lot base>..main`, with nothing in flight and no
   commit range to freeze, and **all of its findings go to the closing block**. It leaves phase 4,
   which keeps the rest of Verify unchanged: the teammate runs the full gate on its branch, and on
   the last code block it writes the handoff, which is therefore on `main` for the review to read.
   Tier Direct still skips the review. Phase 6 loses the count of findings against an already merged
   block: under this decision every finding is one, and a constant measures nothing.
   `agents/reviews/holistic.md` is rewritten to match, its artefact, its "nothing is pushed until
   your findings are closed" and its sentence about the count all being false under this decision.

   (Corrected: two things this decision left the review and the closing block without.
   **The artefact is `git diff <previous lot tag>..origin/main`**, not `<lot base>..main`: `main` is a
   local ref the shared working tree moves, and nothing named the base at all, which reached the
   review through its brief alone. The repository already carries it, the previous lot's annotated
   `lot/` tag, so the mandate names the mechanism and the review determines its own range; phase 6's
   tag step is what keeps that true and now says so. **And the closing work splits like any other
   block when it passes a bound**: "the closing block" is singular here and this lot's own
   predecessor needed two pull requests, 108 and 109, 651 counted lines together against a strict
   600. The seam is the code findings on one side and the documents, the backlog and the handoff on
   the other, which is what those two blocks were. One destination is unchanged; it is one
   destination, not one pull request.)

7. **ADR 0023's context criterion is retired, not replaced, and this document is the record.** The
   criterion is failed as written and the operator, holding the regime satisfactory in use, retires
   it on the reading above: the ceiling halved, the worst block cut by three fifths, no compaction
   inside the block series. One criterion was owed because the regime was new; it has been paid, one
   lot late, and asking for a fresh proof of something that hurts nobody is how a measurement gets
   re-run forever.

## Consequences

- **The new bound refuses nothing in the lot that produced it.** Three client blocks move from
  exemption to compliance and the largest, at 393, is inside 400 rather than refused by it. The
  bound constrains the next lots only, and the reader is owed that plainly rather than a claim that
  it caught something.
- **The API's own overrun stays an overrun.** Block 99 is an API block at 232 lines, and decision 1
  gives it no relief.
- **Decisions 3, 4 and 7 leave no observable in the repository.** Whether a command ran in the
  foreground, whether the lead read a notice or a pull request list, whether a review agent was
  messaged twice: none of it reaches git, and `agents/workflow.md` forbids reading the session
  transcripts that hold it. The only signal is the defect recurring in the next lot's handoff, which
  is after the fact. This is stated rather than dressed up, and it is the reason decision 3 puts its
  weight on the half that produces an artefact, the lead's own looking.
- **The teammate regime now has no criterion it can fail.** That is decision 7's deliberate cost.
  What replaces it is the operator's judgement in use, and the next handoff that reports a
  compaction inside a block series is what would reopen it.
- **A review that has to be asked for twice is a finding about the harness**, not a licence to open a
  conversation with it. The exception in decision 4 is narrow on purpose: this document's own
  specification review was asked once for a truncated report and nothing else.
- **The last code block of a lot merges with the review not yet run**, so a defect the review will
  find reaches `main` first. That is the cost of decision 6, and it is the cost the lot already paid
  in full for eleven blocks out of twelve. What it buys is one destination for every finding, and a
  closing block that is the only place they are answered.
- **Wrap grows a step and Verify loses one.** The lot's end reads: last code block merged, holistic
  review from `main`, closing block, tag, report. The report to the operator is still the input to
  Improve.
- **Nothing here parallelises anything.** Series, the operator as the pacer, and one teammate at a
  time are untouched, and two branches of one lot open at once in `gh pr list` is what would show
  otherwise.
- **The measurements are not reproducible in equal measure.** The per-block table and the continuous
  integration durations are recomputable from git and from `gh run list`; the context table comes
  from a transcript outside the repository, read under the 2026-08-13 authorisation, and cannot be
  recounted by a reviewer bound to stay inside it.

## Block table

One block, `docs/budget-follows-the-ecosystem`, numbered 10: this ADR; `agents/workflow.md` (Evidence
for decision 5, "What a block is" for decision 1 including its partition, phase 2 for decision 2,
phase 5 for decision 3, phases 4 and 6 for decision 6); `agents/reviews/spec.md` (a named agent) and
`agents/reviews/holistic.md` (a named agent, and the rewrite decision 6 requires: the artefact
becomes `<lot base>..main`, the review gates no merge, and the sentence about the count goes);
`AGENTS.md`, whose claim that the suite never reads production's `application.properties` this lot
measured false and which is corrected here rather than left standing; the status lines of ADR 0018,
ADR 0019, ADR 0020 and ADR 0023; the handoff. Tier Spec, one pull request. The specification review
runs on this document before the operator reads it, and the holistic review runs from `main` after
this block merges, as decision 6 places it.

**Block 20, the closing block**, `docs/closing-the-holistic-findings`: the holistic review's nine
findings, two MAJOR and seven MINOR, each with its exit; `agents/workflow.md` (phase 3's fourth stop,
phase 5's second run, phase 6's split and its tag, "What a block is" on the root);
`agents/reviews/holistic.md` (the
artefact named from the tag, and point 10 no longer moving the shared tree); `AGENTS.md` (the
deduction bounded to the keys the test file declares and to the image); the status line of ADR 0019;
this document's corrections above; `docs/backlog.md`, one item filed; the handoff, corrected. Tier
Spec, one pull request, and decision 6's own first exercise: it exists because the review's findings
had nowhere else to go. Adding this row is part of it, the document being dated and not yet frozen.

**Adjacent backlog items: none.** (Corrected: the rule requires the statement and this document
omitted it.) The file's open work is the web application's and the API's, and its `P2` band is
operational debt; nothing in it is about the block budget, where a review runs, or how an agent is
dispatched. The one item that would have been adjacent, "Measure what review costs and what it
returns", left the file at `1100a91e` as refused by
`docs/specs/2026-09-05-p2-debt-elimination.md` D2, so there is nothing to reopen here.
