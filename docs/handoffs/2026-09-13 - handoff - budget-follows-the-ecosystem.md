# Handoff: the block budget follows the ecosystem

Date: 2026-09-13
Branch: `docs/budget-follows-the-ecosystem`, one block numbered 10, one pull request
Decision: `docs/adr/0028-the-budget-follows-the-ecosystem.md`
Tier: Spec, one block, implemented in a teammate. The ADR is the specification and its review ran on
it in a named agent. The holistic review has **not** run when this file is written: decision 6 moves
it to the head of Wrap, on `main`, so it reads this handoff after this block merges and the closing
block corrects the file with its findings.

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
`dagger call smoke` first.

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

## Not validated

- **Nothing in decisions 3, 4 and 7 leaves an observable in the repository**, as the ADR's
  consequences state: whether a command ran in the foreground, whether the lead read a notice or a
  pull request list, whether a review agent was messaged twice. The only signal is the defect
  recurring in a later handoff.
- **The new bound refuses nothing yet.** It was measured against the web application lot, whose
  largest client block is 393 lines, and it constrains the next lots only.
- **The holistic review has not read this lot.** It runs from `main` after this block merges, which
  is the first exercise of decision 6; whether one destination for its findings actually reads better
  than two is what the closing block will show.
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
