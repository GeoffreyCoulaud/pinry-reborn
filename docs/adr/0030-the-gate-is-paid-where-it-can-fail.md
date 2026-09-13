# 0030. The gate is paid where it can fail

Status: Accepted
Date: 2026-09-13
Specification: this document (`agents/workflow.md`, phase 2: a lot whose subject is this process
writes its ADR and no separate spec). Tier Spec. One adversarial review closed, its nine findings
recorded here.
Amends: the holistic review's reach, stated in three places and changed in all three:
`agents/workflow.md`, Wrap, step (a); `agents/workflow.md`, the tier table's Reviews column on the
Spec row; and `docs/adr/0028-the-budget-follows-the-ecosystem.md`, decision 6, which is the source.
Related: `docs/adr/0028-the-budget-follows-the-ecosystem.md`, which sized the review regime to the
lot. This document does the same for the gate.

## Context

Three frictions, all three reported at the head of the Improve pass that closed lot
`0.15.0-optional-source-page-url`, all three measured on that lot.

### A pull request of one markdown file pays thirteen and a half minutes

Pull request #119 changed one file, a handoff. `pr.yml` declares `on: pull_request:` with no path
filter, so it ran `dagger call gate` and the image build. The sibling pull request #118 measures
what that costs: run 34772253333 ran `17:39:31Z` to `17:53:05Z`, thirteen minutes and thirty-four
seconds, `verify` alone 9 m 7 s. The median of ADR 0028, decision 3, is 14.2 minutes.

The operator cancelled #119's run. `validate / gate` is the only context branch protection
requires, it concluded `failure` at `18:00:24Z`, and the pull request merged at `18:00:31Z`:
cancelling meant going around the protection by hand.

**The convention this repository already has is `paths-ignore`**, which `release.yml:11-13` carries
for exactly this reason. It cannot be borrowed here. A workflow a path filter stops leaves the
required check *Pending* forever, so the pull request is blocked rather than slow (GitHub,
troubleshooting required status checks).

**The residual value of a doc-only run is small and not nothing.** `dagger call prose` refuses a
long dash in a tracked text file, but `.dagger/src/index.ts:41` excludes the dated documents:
`git ls-files '*.md'` counts 121 tracked markdown files and 109 of them sit under `docs/specs`,
`docs/plans`, `docs/adr` or `docs/handoffs`, frozen from the rule. On #119's handoff, `prose` would
have read nothing of the diff. What it still covers is the other twelve, `agents/*.md`, the
`AGENTS.md` files and the two `README.md`, plus the evidence guard's own tests, which run in the
same call.

### The `pre-push` hook runs the gate on a tag push

Tagging lot `0.15.0` took minutes: `git push origin lot/0.15.0-...` fired `.githooks/pre-push`,
which runs `dagger call gate` unconditionally at line 16.

A tag push is not free of commits in general: it sends every object reachable from the tag that the
remote lacks, and no workflow would catch them, `release.yml` filtering `v*` alone. What was true
of this push is narrower: `lot/0.15.0-optional-source-page-url` points at `6760391b`, already
contained in `origin/main`, which step (e) of Wrap guarantees by tagging a merge.

### The holistic review is disproportionate to a one-block lot

`agents/workflow.md` makes the holistic review part of tier Spec's Wrap. The operator stopped one
mid-flight on lot `0.15.0` and said so plainly. That lot was one block, mechanical, with a
specification review already closed on it: the holistic review reads a lot as a whole, and a lot
whose whole is one block leaves it nothing the specification review and the gate did not see.

## Decision

1. **A pull request whose every changed path ends in `.md` runs `dagger call prose` in place of
   `dagger call gate`, and does not build the image.** `verify` always runs: it computes the
   condition and publishes it as a job output. `build-image` is skipped at the job level, which
   costs it no runner, no checkout and no Dagger install.
2. **The aggregator accepts a skipped job.** `validate.yml`'s `gate` job refuses anything but
   `success`, which is what made a job-level `if:` look impossible; it now accepts `skipped`
   alongside it. Branch protection reads a job skipped by condition as Success, so the required
   check is satisfied and no protection is bypassed.
3. **The condition is strict and is a whitelist.** One path not ending in `.md` puts the whole pull
   request back on the full gate. A configuration file that does not end in `.md` is therefore
   never doc-only; one that does is (`clients/apps/webapp/project.inlang/README.md` is the tracked
   example), and that is accepted under the consequence below.
4. **It applies to the pull request path alone.** The condition diffs against `github.base_ref`,
   which is empty outside a pull request, so the release path cannot take the shortcut whatever it
   holds. `release.yml` carries its own `paths-ignore` and never reaches this code doc-only anyway.
5. **`pre-push` exits without running the gate when every pushed reference is a tag whose commit is
   already contained in `origin/main`.** A mixed push of a branch and a tag runs it, and so does a
   tag on a commit the remote lacks. This test comes **before** the clean-tree refusal at lines 10
   to 14, which is otherwise unchanged: neither the working tree nor the gate bears on a push that
   sends no object.
6. **The lead offers the holistic review's waiver on a lot of one block rather than dispatching by
   reflex, and the operator decides.** A lot of two blocks or more still gets it. The handoff
   records which happened.

## Consequences

**A defect reachable only through a markdown file now reaches `main` unvalidated by anything but
`prose`, and on a dated document by nothing at all.** That is the accepted cost, and it is small
because no tracked markdown path in this repository is read by anything executable: the grep over
`api/`, `.dagger/`, `.githooks/`, `.github/` and `clients/` returns comments, and the export's
`README.md` is rendered from Kotlin rather than read from disk. The day a markdown file feeds a
build step, decision 3 is what has to change.

**Nothing here changes what `dagger call gate` holds.** A check added to the pipeline is still on
the next pull request. What changes is when a pull request is made to pay for it.

## Block table

| Block | Branch | Content | Journeys |
|---|---|---|---|
| 10 | `ci/gate-paid-where-it-can-fail` | Decisions 1 to 6: `validate.yml`, `.githooks/pre-push`, `agents/workflow.md` and ADR 0028's decision 6 | A pull request of one `.md` file reports `validate / gate` green with no Gradle line in `verify`'s log and `build-image` skipped; a pull request touching one `.md` and one `.kt` runs the full gate and builds the image; a tag push on a commit contained in `origin/main` prints no gate; a push carrying a branch and a tag does print it; `agents/workflow.md` sends a one-block lot's handoff to record either the waiver under what is not validated or the review that ran |

One block. No production line: `.github/` and `.githooks/` are configuration and `agents/` and
`docs/` are markdown, so the whole diff sits inside the 600 and the production bounds have no
subject.
