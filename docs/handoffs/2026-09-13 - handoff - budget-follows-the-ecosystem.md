# Handoff: the block budget follows the ecosystem

Date: 2026-09-13
Branch: `docs/budget-follows-the-ecosystem`, one block numbered 10, one pull request
(Corrected: two blocks, 10 and the closing block 20 on `docs/closing-the-holistic-findings`, two pull
requests. Block 20 is decision 6's own first exercise, the review's findings having no other
destination.)
Decision: `docs/adr/0028-the-budget-follows-the-ecosystem.md`
Tier: Spec, one block, implemented in a teammate. The ADR is the specification and its review ran on
it in a named agent. The holistic review has **not** run when this file is written: decision 6 moves
it to the head of Wrap, on `main`, so it reads this handoff after this block merges and the closing
block corrects the file with its findings. (Corrected: it has now run, over
`git diff 7892368e..main` in a named agent, and reported no CRITICAL, two MAJOR and seven MINOR. Its
nine findings and their exits are the section below.)

## Current state

`dagger call gate` green at the block's tip, in 4 m 38 s for the API's half. The seven decisions are
written into the living documents; the regime they describe is in force from this block's merge, and
the first lot to run under it is the next one. This lot is the first exercise of decisions 4 and 6,
on itself: the specification review ran in a named agent, and the holistic review runs from `main`.

## What was built

- **ADR 0028** as the specification: the production sub-bound per ecosystem with the prefix
  partition, the block table numbered by tens, the three waiting rules, named review agents, the
  documentation rule stripped of its enumeration, the holistic review at the head of Wrap, and the
  retirement of ADR 0023's context criterion.
- **`agents/workflow.md`**, five places. Evidence: the documentation rule names no library and keeps
  the obligation to name the source. "What a block is": two production bounds, 200 under `api/` and
  400 under `clients/`, with `.dagger/` and the root on the 200, plus the prefix partition that says
  what production is. Phases: the preamble gains the naming rule and the one-brief-one-report
  discipline, phase 2 the numbering by tens, phase 4 loses the holistic review, phase 5 gains the
  three waiting rules and the stop for continuous integration, phase 6 gains the review and loses
  the count of findings against an already merged block. The tier table's Reviews column follows.
- **`agents/reviews/spec.md`**: dispatched by name, and the one second message the name buys.
- **`agents/reviews/holistic.md`**: the artefact becomes `git diff <lot base>..main` with every
  block merged, the "nothing is pushed until your findings are closed" sentence goes with the branch
  it described, the closing block is the findings' one destination, and the sentence about counting
  findings against merged blocks goes.
- **`AGENTS.md`**: the claim that the suite never reads production's `application.properties` is
  false and is replaced by what the measurement shows (below).
- **The status lines of ADR 0018, 0019, 0020 and 0023**, each naming what 0028 amends in it.

## The application.properties correction, and how it was measured

The false claim: "The suite never reads production's `application.properties`, its own sharing that
name and winning by classpath order." Two sources settle it:

- Quarkus's configuration reference, through the session's documentation source: "Each
  `application.properties` found is treated as a separate `ConfigSource` and follow the same rules as
  every other source (override per property)."
- `./gradlew :api-application:test --tests "HandshakeIntegrationTest"`, green. That test's third case
  reads `quarkus.smallrye-openapi.info-version` out of `src/main/resources/application.properties`
  with `ProductionProperties`, and compares it to what the route answers from the injected value. The
  test file declares that key nowhere, so a `@QuarkusTest` that read only the test file would answer
  a default and the case would fail. It passes.

So the test file overrides **per property**, not per file, and a key it leaves alone is read from the
production file. What still holds is the sentence that followed: the keys the test file does declare
are never exercised as shipped and no test starts the image, so a deployment defect still reaches
`dagger call smoke` first. (Corrected, MINOR 1: that last clause is too wide, and `AGENTS.md`
carried it unbounded. A defect in a key the test file does **not** declare, such as
`quarkus.http.limits.max-body-size`, `quarkus.rest.exception-mapping.disable-mapper-for` or
`api.host`, is exercised by the suite and waits for nothing. What waits for `smoke` is a key the
test file does declare, whose deployment value is then never exercised, and the image itself.)

## Pitfalls

1. **An Amends line is an enumeration and enumerations go stale against their own Decision section.**
   Two amendments this ADR makes were absent from its Amends line although its decision 6 states
   them: ADR 0019's decision 4 and decision 5's second half (the commit-range pinning, which has
   nothing left to pin), and ADR 0023's decisions 5 and 6 (the wait for continuous integration
   becomes a fourth stop). Writing the amended documents' status lines is what found them, because a
   status line has to say what still stands and the Amends line only has to say what changes. Write
   the status lines before believing the Amends line.
2. **The prefix partition has to be checked against the tree, not assumed.**
   `clients/apps/webapp/src/paraglide` is under `clients/**/src/**` and would be production by
   prefix; it carries its own `.gitignore` and never reaches a diff, so the partition needs no
   exception for it. `clients/**/src/journeys/**` and `clients/**/src/test/**` both exist, and
   `.dagger/src/` exists beside the generated `.dagger/sdk/`.

## The holistic review's nine findings, and the exit each took

The review ran at the head of Wrap over `git diff 7892368e..main`, in a named agent, with every code
block merged. It reported **no CRITICAL, two MAJOR and seven MINOR**, and recomputed the ADR's
per-block table and its continuous integration figures as reproducing exactly. **All nine were fixed
inside the lot**, in block 20: no backlog item, no accepted limit, nothing refused. Every one of them
is a document finding, which is what a lot whose subject is its own process produces.

| # | Finding | Exit, and where |
|---|---|---|
| MAJOR 1 | Phase 3's closed list of a teammate's stops counts three where the ADR's Amends line and ADR 0023's status line both say four. | Fixed: `agents/workflow.md` phase 3 names the fourth, "continuous integration started", and points at phase 5 where it is spelled out. |
| MAJOR 2 | Every finding goes to "the closing block", singular, and the previous lot needed two pull requests for its own: 108 and 109, 651 counted lines together against a strict 600. | Fixed: `agents/workflow.md` phase 6 says the closing work splits like any other block, the seam being code findings against document findings, and the ADR's decision 6 carries the correction. The 651 was recounted here. |
| MINOR 1 | `AGENTS.md`'s premise was corrected and its conclusion left unconditional: a key the test file does not declare is exercised by the suite and waits for no image. | Fixed: `AGENTS.md` bounds the deduction to the keys the test file declares and to the image, and the section above records it. |
| MINOR 2 | The ADR's two replaced figures were paired with the wrong blocks. | Fixed: block 3 is 247 against 262 and block 6 is 253 against 256, as a `(Corrected: ...)` on the sentence. |
| MINOR 3 | ADR 0019's status line retired decision 4's pinning and left its other half standing, and ended without a full stop. | Fixed: decision 4 is retired whole, the surviving mandate reading neither a branch nor a pull request, in that status line and in the ADR's own Amends line. |
| MINOR 4 | Nothing named the lot's base: the review's artefact said `main`, a mobile local ref, and the base reached the review through its brief alone. **The operator decided this one: adopt.** | Fixed: `agents/reviews/holistic.md` names the mechanism, `git diff <previous lot tag>..origin/main`, so a review determines its own range; phase 6's tag step now states that the tag is what the next lot's review reads. |
| MINOR 5 | The return-to-Verify loop had no stop for the second continuous integration run, so a teammate following it marks ready before the run settles or installs the monitor the same rule forbids. | Fixed: `agents/workflow.md` phase 5 has the teammate report the new run and wait for the lead, and says the stop applies to every run of the block. |
| MINOR 6 | "`.dagger/` and the repository root take the 200" gives the root a bound the partition leaves no line to measure. | Fixed: `agents/workflow.md` says the root has no production line and only the 600 reaches it; the ADR's decision 1 carries the correction. |
| MINOR 7 | The holistic mandate's point 10 asks the review to check out a commit in the shared working tree, which under decision 6 leaves a detached HEAD for the closing block to find. | Fixed: point 10 reads with `git show <commit>:<path>`, or in a `git worktree` it removes, and never moves the shared tree. |

**One item filed, and it is not one of the nine.** Reading the review, the operator named the root
cause behind MAJOR 1 and MINOR 5: `agents/workflow.md` is not foolproof, because it states each step
and its justification in one breath. The wanted shape is the steps short first with the essential
clarification after, plus a mermaid flowchart of the per-block loop and then the lot's steps. That is
a `P1` backlog item, not a change this block makes: it needs its own Discuss and its own
specification. It is the next lot's subject.

## Not validated

- **Nothing in decisions 3, 4 and 7 leaves an observable in the repository**, as the ADR's
  consequences state: whether a command ran in the foreground, whether the lead read a notice or a
  pull request list, whether a review agent was messaged twice. The only signal is the defect
  recurring in a later handoff.
- **The new bound refuses nothing yet.** It was measured against the web application lot, whose
  largest client block is 393 lines, and it constrains the next lots only.
- **The holistic review has not read this lot.** It runs from `main` after this block merges, which
  is the first exercise of decision 6; whether one destination for its findings actually reads better
  than two is what the closing block will show. (Corrected: it has read it, and one destination held:
  nine findings, one closing block, no arbitration about where any of them went. What the exercise
  also showed is that the destination does not state its own size, which is MAJOR 2, and that a
  document whose one destination is a block nobody had written yet says "the closing block" as if it
  were one pull request.)
- **How a background agent's permission prompt reaches the operator** is still unexercised, as it was
  at the end of the web application lot.

## Tier-2 questions asked

None. Two adjacent defects were fixed as tier 1 and are named in the pull request's body: the stale
Amends clauses above, and the comment in `api/api-application/src/test/resources/application.properties`
that carried the same per-file reading as the `AGENTS.md` sentence.

## Next step

Wrap: the holistic review from `main` over `git diff <lot base>..main`, in a named agent, then the
closing block that fixes its findings and corrects this file. After that, the first ordinary lot run
under the new regime, whose block table numbers by tens and whose client blocks measure against 400.

(Corrected: the review has run and block 20 is the closing block that answers it, this pull request.
What is left of Wrap after it merges is phase 6's own two steps, the annotated lot tag on the closing
merge and the report to the operator. That tag is the base the next lot's holistic review reads, so
MINOR 4's fix depends on it being pushed.)

**The next lot's subject: make `agents/workflow.md` foolproof.** The `P1` item above is what the
operator asked for after reading this review, and it is the first thing to run under the new regime
rather than after it: two of this lot's nine findings are the same design smell, and a process
document that contradicts itself is what the next ordinary lot would trip on. Tier Spec, its own
Discuss, its own specification, and no inline restructuring before them. The ordinary product lot,
whose client blocks measure against 400, comes after it.
