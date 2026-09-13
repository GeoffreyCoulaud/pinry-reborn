# Handoff: the gate is paid where it can fail

Date: 2026-09-13
Branch: `ci/gate-paid-where-it-can-fail`, block 10, pull request #122, merged as `c08281cf`
Decision: `docs/adr/0030-the-gate-is-paid-where-it-can-fail.md`, which is also this lot's
specification (`agents/workflow.md`, phase 2)
Tier: Spec, implemented in a teammate. The specification review ran in a named agent and its nine
findings were closed in the ADR before the operator read it.

## Current state

Run 34778932701 green on all three checks: `verify` 9 min 19 s, `build-image` 4 min 28 s,
`validate / gate` 2 s. The diff is 146 counted lines against a strict 600, and no production line:
nothing changed sits under `api/**/src/main/**`, `.dagger/src/**` or `clients/**/src/**`.

This lot is the Improve pass of lot `0.15.0`, whose report named four frictions. Three are fixed
here. The fourth, a specification review's report arriving truncated twice, is untouched.

## What was built

- **A documentation-only pull request runs `dagger call prose` and builds no image.** `verify`
  always runs and publishes a `docs-only` job output from a strict `.md` whitelist diffed against
  `github.base_ref`; `build-image` carries a job-level `if:`; the aggregator accepts `skipped`
  beside `success`, which is what makes the job-level skip possible without losing a failure.
- **`.githooks/pre-push` exits before the clean-tree refusal** when every pushed reference is a tag
  on a commit `origin/main` already contains.
- **The holistic review is offered rather than dispatched on a one-block lot**, in
  `agents/workflow.md` under Wrap and in the tier table's Reviews cell, with a back-link on ADR
  0028's status line.
- **The root `AGENTS.md` states both exemptions**, which the living-document rule requires and the
  block table did not name.

## Evidence the journeys left

Both pull-request journeys were established on throwaway pull requests based on the block's branch,
a local reusable workflow being read from the merge commit. Both are closed and both branches
deleted.

| | Documentation only (#120, run 34777724633) | Mixed (#121, run 34777736344) |
|---|---|---|
| scope step | `Documentation only: true` | `Documentation only: false` |
| `verify` | 1 min 7 s | 9 min 42 s |
| `Gate` step | skipped | success |
| `Prose` step | success | skipped |
| `build-image` | skipped | success, image built and smoke-tested |
| `validate / gate` | success | success |

The comment pruning the operator asked for at review landed after those two runs. What lets them
stand is that `yaml.safe_load` of `validate.yml` before and after the pruning compares equal, so
nothing the workflow does changed. The file went from 237 lines to 187 and from 89 comment lines
to 39.

## Pitfalls

- **`grep -q`'s exit status is inverted in an agent's shell here**, a ugrep wrapper from the shell
  snapshot. It reported a diff of one `.md` and one `.kt` as documentation-only. `grep -v`,
  `grep -cv` and `command grep -qv` all answer correctly, and a runner's real `bash` is unaffected,
  so the bug was in the verification and never in the artefact.
- **A path filter cannot be used on `pr.yml`.** A workflow a path filter stops leaves the required
  check *Pending* forever, so the pull request is blocked rather than slow. That is why the
  condition lives in the steps and in one job-level `if:`, not on the trigger.
- **One tier-1 fix changed a claim rather than a comment's length.** The smoke step asserted that
  the test `application.properties` shadows production's; the root `AGENTS.md` and ADR 0028's
  context both refute it, and the refuted half was removed rather than filed.

## What is not validated

- **The mixed journey's `.kt` side is one added comment line**, the cheapest possible mixed pull
  request rather than a representative one. It is faithful to decision 3, which reads paths and not
  content, but nothing here observed a real code change taking the full gate.
- **No holistic review ran over this lot**, one block and the operator deciding, which is decision 6
  applied to itself.

## Next step

The `P1` band. The item closest to this lot is still open: a pull request pays two cold Gradle
builds, one in `verify` and one in `build-image`, and they run in series. This lot made a
documentation-only pull request stop paying either; it did nothing for the ones that pay both.
