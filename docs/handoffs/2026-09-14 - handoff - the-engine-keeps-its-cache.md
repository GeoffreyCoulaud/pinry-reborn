# Handoff: the gate builds once and keeps its cache

Date: 2026-09-14
Branch: `ci/one-job-one-gradle-build`, block 10, pull request #124, merged as `910f5f3e`;
`ci/the-engine-keeps-its-cache`, block 20, this pull request
Decision: `docs/adr/0031-the-gate-builds-once-and-keeps-its-cache.md`, which is also this lot's
specification (`agents/workflow.md`, phase 2)
Tier: Spec, implemented in a teammate per block. The specification review ran in a named agent and
its findings were closed in the ADR before the operator read it.

## Current state

A pull request ran two cold Gradle builds on two runners, in series, and waited about fourteen
minutes. It now runs one job, one Gradle build, on an engine that starts from the state the last
push to `main` archived.

| | Before the lot (run 34778932701) | After block 10 (run 34784516313) | After block 20 |
|---|---|---|---|
| `verify` | 9 min 19 s | 10 min 06 | about 7 min, projected |
| `build-image` | 4 min 28 s | the job no longer exists | |
| Gradle | 170 of 170 executed, twice | 170 of 170 executed, once | 97 executed, 73 from cache |

The last column is a projection and not a measurement: no pull request has yet restored an entry
`main` saved, `main` saving for the first time when this block merges. It is built from the spike's
second run, 8 min 02 for the whole job, less the 43 s of archiving and the 15 s of saving that a
pull request does not pay.

## What was built

**Block 10, decision 1.** A `ci` function in `.dagger/src/index.ts` runs the gate, then builds and
smokes the image from the fast jar the gate's own container produced. `validate.yml` keeps one job
named `verify`; the `gate` aggregator needs `verify` alone. `dagger call quarkus-app` returns the
gate's own build rather than a build of its own, so the release path's export is a cache hit and the
bytes it pushes stay the bytes `smoke` started.

**Block 20, decisions 2 to 8.**

- **The job starts its own engine** and reaches it through
  `_EXPERIMENTAL_DAGGER_RUNNER_HOST=docker-container://dagger-engine`, so the job owns the engine's
  lifetime and its state directory.
- **A pull request restores and never saves; a push to `main` stops the engine, archives the state,
  saves it and deletes every older entry sharing the key's prefix.** The key carries the engine
  version and the commit, with `restore-keys` on the version prefix.
- **`.github/engine.json` declares `gc.policies`** rather than a bound alone, and bounds the cache
  at 7 GB.
- **`org.gradle.caching=true`** in `api/gradle.properties`. `org.gradle.parallel` stays refused.
- **`.githooks/pre-push` reads a deletion as a push that sends no object**, which is decision 8 and
  the one thing in this lot that was not in the block table.

## Evidence the journeys left

**The spike, two pushes on a throwaway branch, both green.** Run 34786582019 cold, 10 min 00:
nothing to restore, `Gate, image and smoke` 8 min 36, engine state 6.2 GB, archived in 36 s to
2 691 936 593 bytes and saved in 20 s. Run 34787933347 on the entry the first wrote, 8 min 02:
`Restore` 23 s, `Unpack` 19 s, engine up in 12 s, `Gate, image and smoke` 6 min 01 with
`BUILD SUCCESSFUL in 5m 15s` and `170 actionable tasks: 97 executed, 73 from cache`. The 73 tasks
land on the number the local probe measured on a reused Docker volume, which is what the spike had
to establish: the round trip through `actions/cache` produces the same warm build a live volume
does. Both entries were deleted and the branch removed.

**The documentation-only journey**, on throwaway pull request #125, run 34785412649, after block 10
merged. `validate / verify` green in 1 min 19 s and `validate / gate` green in 4 s; the
`Gate, image and smoke` step skipped, `Prose` green printing `no long dash in a tracked text file`
and the evidence guard's two tests, every image and release step skipped. The previous two-job shape
measured 1 min 07 for `verify` on pull request #120, run 34777724633. The subject was a root-level
markdown file, which `prose` reads rather than skips the way it skips a dated document. The pull
request is closed and its branch deleted.

**`.github/engine.json` is read, and 7 GB is the bound in force.** An engine started locally with the
file mounted answers `7516192768` to
`dagger core engine local-cache max-used-space`, which is 7 GiB exactly and a number that can only
come from that file; the same engine started without it answers `1.2e+10`. That is what makes the
rest of decision 5 sound: `engine/server/gc.go`, `getDagqlGCPolicy` at `v0.21.9`, uses the declared
list in place of the generated one, and the declared list has no policy filtering
`type==exec.cachemount`.

**The `pre-push` loop, on four inputs.** A deletion alone runs no gate; a branch push, a deletion
beside a branch push, and a tag the remote cannot reach all run it. Removing the deletion arm makes
the first case run the gate again, which is the mutation that fails the check.

## Pitfalls

- **The compressed entry is about three gigabytes and the repository's quota is ten**, of which the
  release path's buildx cache already holds six. One entry fits and two do not, so the step that
  deletes the older entries is a condition of the mechanism and not tidiness. If it ever fails, the
  next release loses its buildx cache to eviction rather than anything failing loudly.
- **The state grows.** 6.2 GB after one cold gate, 7.4 GB after one warm run stacked on it, and the
  compressed archive with it, 2.69 GB then 3.13 GB. That growth is what `gc.maxUsedSpace` bounds and
  why the bound is not optional.
- **The default garbage collection policy would have eaten the thing being kept.** Dagger's
  generated list opens with a policy filtering `type==exec.cachemount`, which is exactly the Gradle
  home and pnpm store volumes, with a `keepDuration` of 48 hours and a 512 MB cap. Two days without
  a push to `main` and a restored state would have come back without its build cache. Declaring
  `gc.policies` is what removes it; setting `gc.maxUsedSpace` alone would not have.
- **`actions: write` is now on `verify` and therefore on both callers**, `pr.yml` included, where
  nothing is ever saved or deleted. The reusable workflow's permissions have to be granted by the
  caller or the run does not start, which is the same reason `pr.yml` already grants
  `packages: write`.
- **The engine's readiness is polled, not assumed.** `dagger core version` is the probe; a container
  that never answers prints its logs and fails the step rather than letting the gate time out at
  forty-five minutes.

## What is not validated

- **The runner actually changing between the spike's two runs.** The observable chosen for it,
  `/etc/machine-id` and `hostname`, answers `58b34b8c91a94400a52c175421986a53` and `runnervmlun5p` in
  both runs: those values come from the runner image, not from the machine. What supports the claim
  is indirect, 2.69 GB restored in 23 s, a network rate and not a local disk. The observable that
  would have settled it is the runner's own network identity, for instance the public address the
  job sees.
- **The scoping between a pull request and `main`.** The spike saved and restored on one branch, so
  it exercised the archive, the transfer and the change of runner, and never the rule that a cache a
  pull request writes is invisible to another. Decision 4 rests on that rule and takes it from
  GitHub's documentation.
- **What a pull request will actually cost.** The 7 minutes above is arithmetic on the spike's run,
  not a run.
- **That `prose` fails on a long dash in a root-level markdown file.** The documentation-only journey
  showed the step green and the operator declined the negative test, so what is established is that
  `prose` runs on that path, not that it would refuse.
- **The holistic review**, which Wrap decides. This lot has two blocks, so the waiver
  `docs/adr/0030-the-gate-is-paid-where-it-can-fail.md` decision 6 offers does not apply to it.

## Next step

Wrap: the holistic review over `git diff <previous lot tag>..origin/main`, then the closing block
with the backlog reconciled and this handoff corrected. The `P1` item this lot closes, a pull
request paying two cold Gradle builds, is deleted in this pull request.

The first push to `main` after this merges pays the cold price and writes the first entry; the
pull request that follows it is the one that measures decision 4. The block table asks it for at
least 60 of 170 tasks from cache and for one entry carrying the engine version prefix, not two.
