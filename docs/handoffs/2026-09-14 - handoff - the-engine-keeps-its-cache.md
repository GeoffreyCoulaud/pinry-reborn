# Handoff: the gate builds once and keeps its cache

Date: 2026-09-14
Branch: `ci/one-job-one-gradle-build`, block 10, pull request #124, merged as `910f5f3e`;
`ci/the-engine-keeps-its-cache`, block 20, merged as `449ab4ef` and `db6097fb`;
`chore/closing-the-warm-cache`, the closing block
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

(Corrected: measured, on throwaway pull request #127, run 34831020334, opened after block 20 merged
and `main` saved. `validate / verify` 7 min 24 and `validate / gate` 4 s. **The lot's headline is
13 min 49 to 7 min 28, 46 %.** The entry restored was
`dagger-state-v0.21.9-db6097fb89341d0d97a049661b815a67fe05f5fc`, 27 s to restore and 20 s to unpack;
`Gate, image and smoke` 6 min 13 with `BUILD SUCCESSFUL in 5m 26s` and
`170 actionable tasks: 97 executed, 73 from cache`; the archive, save and delete steps skipped, as a
pull request must. **The total is solid and its decomposition is not.** Gradle took 5 min 26 here
against 5 min 43 on the cold run of pull request #126, seventeen seconds apart, while cold Gradle
across runs measured 5 min 43, 6 min 51 and 8 min 24 for identical work: runner variance swamps the
attribution, so nothing here splits the gain between Dagger's operation cache and Gradle's build
cache. The pull request is closed and its branch deleted.)

## What was built

**Block 10, decision 1.** A `ci` function in `.dagger/src/index.ts` runs the gate, then builds and
smokes the image from the fast jar the gate's own container produced. `validate.yml` keeps one job
named `verify`; the `gate` aggregator needs `verify` alone. (Corrected: the closing block splits the
publication and the cache maintenance out of `verify`, which keeps `contents: read`, and `gate` needs
`verify`, `prune` and `publish`. One Gradle build, which is what decision 1 buys, is unchanged.)
`dagger call quarkus-app` returns the
gate's own build rather than a build of its own, so the release path's export is a cache hit and the
bytes it pushes stay the bytes `smoke` started.

**Block 20, decisions 2 to 8.**

- **The job starts its own engine** and reaches it through
  `_EXPERIMENTAL_DAGGER_RUNNER_HOST=docker-container://dagger-engine`, so the job owns the engine's
  lifetime and its state directory.
- **A pull request restores and never saves; a push to `main` stops the engine, archives the state,
  saves it and deletes every older entry sharing the key's prefix.** The key carries the engine
  version and the commit, with `restore-keys` on the version prefix. (Corrected: the deletion lists
  on `dagger-state-` and spares the full key, the version prefix having left an entry behind at every
  engine bump.)
- **`.github/engine.json` declares `gc.policies`** rather than a bound alone, and bounds the cache
  at 7 GB. (Corrected: 9 GB.)
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
the first case run the gate again, which is the mutation that fails the check. The holistic review
re-ran it on six, tag deletion and empty stdin included, and it answers as documented.

**The first save on `main`**, run 34829754419, the merge of `db6097fb`: archive 29 s, save 23 s,
purge 1 s, one entry `dagger-state-v0.21.9-db6097fb...` of 2 686 264 632 bytes on `refs/heads/main`.
The repository's cache usage read 6.28 GB across 465 entries before the lot and 5.93 GB across 438
just after, so the release path's buildx entries fell from 6.28 GB to 3.24 GB, about 3 GB and 28
entries gone. **Attribution to our save is likely and not certain**: GitHub's seven-day expiry can
account for part of it. This is the quota arbitration the ADR's Consequences anticipated, measured
rather than feared. By the day the closing block was written the buildx entries had grown back to
5.46 GB and the quota read 8.15 GB of 10.

## What the closing block changed

The holistic review ran in a named agent over `git diff lot/0.16.0-gate-paid-where-it-can-fail..origin/main`.
It confirmed the eight decisions and the three `(Corrected: ...)` clauses are all in the diff, that the
`index.ts` refactoring is a pure extraction, and that the living documents were corrected in the commits
that changed behaviour. Its six major findings and seven minor ones are fixed here, none deferred and
none refused.

- **The gate's job ran under the release path's scopes.** `verify` carried `packages: write`,
  `id-token: write` and `actions: write` and ran `dagger call ci`, so every pull request compiled
  third-party Gradle plugins under a token that could publish to GHCR, mint an OIDC token and delete
  any cache. `verify` now holds `contents: read`, hands its fast jar over as a run artefact, and the
  write scopes live in `publish` (the release path) and `prune` (`main`), neither of which builds
  anything. **`pr.yml` still grants all four**, and that is not a leftover: a caller's grant is a
  ceiling GitHub checks when the run starts, over every job the called workflow declares and not only
  the ones that run. Granting `contents: read` alone failed the run before any job started, with
  "This run likely failed because of a workflow file issue" and nothing else. What the build's token
  carries is the `permissions` block on `verify`, which is `contents: read`.
- **A corrupt entry would have failed every pull request.** Neither the unpack nor the engine's
  readiness loop fell back to an empty state, so one bad archive stuck until someone deleted it by
  hand. Both now empty the state directory and carry on cold.
- **The 7 GB bound sat inside the warm steady state**, which is 7.4 GB after a warm run stacked on a
  cold one, so the third consecutive run's sweep would have been the `all: true` policy deciding the
  fate of the cache mount volumes. The bound is 9 GB.
- **A cache hiccup could cost a green gate its image.** The archive and the save are `continue-on-error`
  and the prune is its own job, so an upload timeout or a transient `gh cache delete` no longer fails
  the run that produced them. The review asked for them to be moved after the publication; the job
  split makes that inexpressible, and making them best effort is the same guarantee.
- **A documentation-only pull request restored and unpacked about 2.7 GB to run `prose`**, roughly
  doubling a job block 10 measured at 1 min 19. The restore and the unpack now carry the same
  condition the gate step does; starting the engine stays unconditional.
- **The purge listed on the version prefix**, so an engine bump left the previous entry behind for
  seven days, and two entries plus the buildx cache exceed the quota. It lists on `dagger-state-`
  and spares the full key.
- **The seven minor findings**: the engine version is read once from `dagger.json` rather than
  duplicated in `validate.yml`; `docker stop` exiting 0 after a `SIGKILL` is caught by reading the
  container's exit code, 137 failing the step rather than archiving a live engine's state;
  `release.yml` has a `concurrency` group that serialises rather than cancels, so two close pushes to
  `main` no longer delete each other's entry; `AGENTS.md` enumerates the three pushes it says are let
  off; the abbreviated ADR references in `.githooks/pre-push` and `validate.yml` are full paths again;
  `api/gradle.properties` loses its trailing blank line; and `ZSTD_NBTHREADS`, which no line of the
  specification asked for and no measurement justified, is gone.

**The backlog needed no change.** The one item this lot closes, a pull request paying two cold Gradle
builds, was deleted in block 20's own pull request, and no finding took the backlog as its exit.

## Pitfalls

- **The compressed entry is about three gigabytes and the repository's quota is ten**, of which the
  release path's buildx cache already holds six. One entry fits and two do not, so the step that
  deletes the older entries is a condition of the mechanism and not tidiness. If it ever fails, the
  next release loses its buildx cache to eviction rather than anything failing loudly.
- **The state grows.** 6.2 GB after one cold gate, 7.4 GB after one warm run stacked on it, and the
  compressed archive with it, 2.69 GB then 3.13 GB. That growth is what `gc.maxUsedSpace` bounds and
  why the bound is not optional. (Corrected: the bound is 9 GB and not 7, 7 GB having sat inside that
  steady state. The extra gigabyte of archive comes out of the release path's buildx cache by
  least-recently-used eviction, which is the operator's to reverse.)
- **The default garbage collection policy would have eaten the thing being kept.** Dagger's
  generated list opens with a policy filtering `type==exec.cachemount`, which is exactly the Gradle
  home and pnpm store volumes, with a `keepDuration` of 48 hours and a 512 MB cap. Two days without
  a push to `main` and a restored state would have come back without its build cache. Declaring
  `gc.policies` is what removes it; setting `gc.maxUsedSpace` alone would not have.
- **`actions: write` is now on `verify` and therefore on both callers**, `pr.yml` included, where
  nothing is ever saved or deleted. The reusable workflow's permissions have to be granted by the
  caller or the run does not start, which is the same reason `pr.yml` already grants
  `packages: write`. (Corrected: the sentence's second half is right and its first half was the lot's
  worst finding. The write scopes now sit on `publish` and `prune`, jobs a pull request never starts,
  so `verify` runs the build under `contents: read`. `pr.yml` still grants all four, and has to: the
  caller's grant is a ceiling checked against every job the called workflow declares, whatever runs.)
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
  not a run. (Corrected: measured at 7 min 28 on pull request #127. What stays unestablished is the
  split of that gain between Dagger's operation cache and Gradle's build cache, runner variance on
  cold Gradle being larger than the difference being attributed.)
- **The third consecutive run, and every run after it.** Every warm figure in this lot, the block 20
  journey's 73 tasks from cache included, is the run that *immediately follows* a save. Nothing
  measured what a third run leaves behind, whether the state plateaus, or whether the 9 GB bound
  starts sweeping. The observable if it ever does is the Gradle line reporting well under 73 tasks
  from cache on a warm pull request.
- **The release path under the job split.** `publish` has never run. The fast jar reaching it as a
  run artefact, `buildx` building from a downloaded directory, and cosign's keyless identity under a
  renamed job are all first exercised by the next push to `main`; the identity is the reusable
  workflow's path, which did not change, so `grype-scan.yml` should keep verifying, but that is
  reasoning and not a run.
- **Whether the pitfall above has a way out.** The caller has to grant what every declared job asks
  for, so `pr.yml` cannot be narrowed while `publish` and `prune` live in the workflow it calls. A
  caller that granted less would have to call a workflow that declares less, which means splitting
  `validate.yml` in two; nothing here measured whether that is worth its duplication.
- **That `prose` fails on a long dash in a root-level markdown file.** The documentation-only journey
  showed the step green and the operator declined the negative test, so what is established is that
  `prose` runs on that path, not that it would refuse.
- **The holistic review**, which Wrap decides. This lot has two blocks, so the waiver
  `docs/adr/0030-the-gate-is-paid-where-it-can-fail.md` decision 6 offers does not apply to it.
  (Corrected: it ran in a named agent, and the section above carries its thirteen findings and their
  exit, which is "fixed in the lot" for every one of them.)

## Next step

Wrap: the holistic review over `git diff <previous lot tag>..origin/main`, then the closing block
with the backlog reconciled and this handoff corrected. The `P1` item this lot closes, a pull
request paying two cold Gradle builds, is deleted in this pull request.

The first push to `main` after this merges pays the cold price and writes the first entry; the
pull request that follows it is the one that measures decision 4. The block table asks it for at
least 60 of 170 tasks from cache and for one entry carrying the engine version prefix, not two.

(Corrected: all of that happened. The entry was written by run 34829754419, the journey ran on pull
request #127 and reported 73 of 170 tasks from cache and one entry. What is left is step (e), the
annotated `lot/0.17.0-*` tag on the closing merge. After that, the two things to watch are the first
push to `main`, which is the first run of the `publish` job and of the whole release path under the
job split, and the warm pull request after the one that follows it, which is the first third
consecutive run and the first chance to see whether the 9 GB bound sweeps.)
