# 0031. The gate builds once and keeps its cache

Status: Accepted
Date: 2026-09-13
Specification: this document, as `docs/adr/0030-the-gate-is-paid-where-it-can-fail.md` did for the
same subject. Tier Spec. One adversarial review closed, its findings recorded here.
Closes: the `P1` backlog item "A pull request pays two cold Gradle builds", which is this lot's
whole subject and the only item adjacent to it.
Related: `docs/adr/0030-the-gate-is-paid-where-it-can-fail.md`, which stopped a pull request paying
for a gate that could not fail. This one changes what the gate costs when it does have to run.

## Context

Continuous integration figures are from run 34778932701 and hold across the four runs around it.
Local figures are from probes on a twelve-core workstation with `--max-workers=4`, the pin the
pipeline carries, each on a fresh engine.

### A pull request waits fourteen minutes and pays for one build twice

`verify` runs 9 m 19 s and `build-image` 4 m 28 s, in series because `build-image` needs `verify`.
Inside `verify` the Gradle gate is 8 m 26 s of the 9 m 19 s: the clients gate finishes in 59 s and
leaves the critical path, prose and the contract guard are noise.

`build-image` rebuilds what `verify` built: `BUILD SUCCESSFUL in 2m 9s`, 42 tasks executed. Two
causes, either sufficient. `gate()` execs `./gradlew gate :api-application:quarkusBuild` while
`quarkusApp()` execs `./gradlew :api-application:quarkusBuild`, and different arguments are
different cache keys; and the second job runs on a second runner whose engine is empty whatever the
keys say.

Dagger's fixed cost is per engine, not per call: 11 s to start and 27 s to load the TypeScript
module in `verify`, 10 s and 26 s again in `build-image`, while `smoke`, third call on an engine
already running, pays 0 s and 0.5 s. One job instead of two therefore saves about 36 s of it.

### Every run starts from nothing

`170 actionable tasks: 170 executed`, on every run. The cache volumes holding the Gradle home and
the pnpm store live inside the engine, and the engine dies with the runner. Locally the same gate
takes 3 m 12 s on a reused engine against 5 m 16 s on a fresh one. **What that gap is spent on is
not established here**: no task is skipped on either side, so it is spread across image pulls,
`apt-get`, `pnpm install` and Gradle's own work against a populated dependency cache.

### What the probes measured

Cold is a fresh engine. Warm is that engine reused with one local variable renamed in
`api-usecases`, which is what a pull request looks like. Every warm figure below follows a cold run
that succeeded, on the same engine.

| Configuration | Cold | Warm |
|---|---|---|
| Current | 5 m 16 s, 170 of 170 executed | 3 m 12 s, 170 of 170 executed |
| `+ org.gradle.caching` | 5 m 15 s, 170 of 170 executed | **2 m 24 s, 73 of 170 from cache** |
| `+ caching + parallel`, current memory bound | **fails at 4 m 22 s** | 2 m 18 s, 73 of 170 from cache |
| `+ caching + parallel`, `-Xmx4g` and metaspace 2 GB | **fails at 6 m 43 s** | 2 m 16 s, 71 of 170 from cache |

`org.gradle.caching` costs nothing cold, there being nothing to hit, and cuts the warm run by a
quarter. `org.gradle.parallel` fails the cold build twice with
`Gradle build daemon disappeared unexpectedly`, at the current bound and at double it; warm, against
`caching` alone and with the same memory, it is 2 m 18 s against 2 m 24 s for the same 73 tasks
served from cache, which one run each cannot separate from noise. **It is refused on the failure,
not on an explanation of it**: under `--no-daemon` that message describes a process that died, and
nothing here establishes which of metaspace exhaustion or an out-of-memory kill did it.

### What the cache would have to fit in

The engine's state directory holds 5.6 GB after one cold gate and 6.8 GB after a warm run stacked on
it, measured with `du -sh` and therefore uncompressed, where a cache entry is stored compressed.

The repository's cache quota is **not empty**: `gh api repos/GeoffreyCoulaud/pinry-reborn-api/actions/cache/usage`
answers 6.28 GB across 465 entries, all `buildkit-blob-1-*` and `index-buildkit-1-*` on
`refs/heads/main`, written by the `cache-to: type=gha,mode=max` that `validate.yml` gives
`docker/build-push-action` on the release path. GitHub gives a repository 10 GB at no cost and
evicts least-recently-used above it, so about 3.7 GB is free and anything beyond evicts the release
path's buildx cache.

### The claim the lot rests on, and does not yet establish

Every warm figure above comes from reusing a live Docker volume on one machine. The mechanism this
lot proposes is a different thing and no probe exercised it: `actions/cache` stores runner paths
rather than named Docker volumes, a BuildKit state archived while the engine runs may not restore,
and an archive unpacked on a *different* runner has never been observed producing a warm build. If
that round trip does not work, block 20 delivers nothing and this lot is block 10 alone. Block 20
therefore opens with a spike, and decisions 2 to 6 are conditional on it.

## Decision

1. **One job, one Gradle build.** A `ci` function runs the gate, then builds the image and smokes it
   from the fast jar the gate's own container produced. `validate.yml` keeps one job named `verify`,
   which now also builds and smokes the image; the aggregator `gate` keeps its name, that being the
   only context branch protection requires, and needs `verify` alone. `dagger call gate`, `image` and
   `smoke` stay callable on their own for the workstation and the release path. (Corrected:
   `dagger call quarkus-app` returns the gate's own build rather than a build of its own, so the release
   path's export is a cache hit on `ci` instead of the second build the merge would otherwise have
   introduced, and the bytes it exports stay the bytes `smoke` started.) The root `AGENTS.md`
   describes the two-job shape in its CI and image sections and is corrected with it. (Corrected:
   one job for the *build*, not for the run. Putting the publication in `verify` gave the job that
   runs the build's third-party plugins `packages: write`, `id-token: write` and `actions: write` on
   every pull request, where before the lot those scopes sat on `build-image` alone. `verify` keeps
   `contents: read` and hands its fast jar over as a run artefact; `publish`, on the release path
   alone, and `prune`, on `main` alone, carry the write scopes and build no Gradle target, so the
   one Gradle build this decision buys is untouched.)
2. **Continuous integration starts its own engine** and points the CLI at it with
   `_EXPERIMENTAL_DAGGER_RUNNER_HOST=docker-container://<name>`, so the job owns the engine's
   lifetime and its state directory. Nothing about how the gate is called changes.
3. **The engine is stopped before its state is archived**, the archive being what `actions/cache`
   stores. A state captured from a running engine is not assumed restorable.
4. **A pull request restores and never saves; a push to `main` saves.** A cache a pull request
   creates is scoped to its own merge ref and no other pull request can read it. The key is rolling,
   carrying the engine version and the commit, with `restore-keys` on the version prefix: a fixed key
   would refuse every save after the first, GitHub's entries being immutable, and freeze the cache on
   one merge. **After a successful save the older entries sharing that prefix are deleted**, so the
   lot's footprint stays one entry and the eviction it causes is its own rather than the release
   path's. (Corrected: the deletion lists on the whole `dagger-state-` prefix and spares the full
   key. Listing on the version prefix left the previous engine's entry behind at every version bump,
   waiting seven days for GitHub to evict it, which is exactly the second entry this decision
   refuses.) (Corrected: the deletion precedes the save, and the job `prune` is renamed
   `engine-state` for it. "After a successful save" left the two entries coexisting for the length
   of the save, which the numbers of 2026-09-18 made an overflow rather than a moment: the archive
   had grown to 4.43 GB, so 4.43 × 2 + 1.87 of buildx blobs is 10.73 GB of a ten-gigabyte quota,
   and what GitHub evicted least-recently-used was the release path's buildx cache, down from the
   5.46 GB this decision's own correction above recorded to 1.87 GB. `verify` therefore hands the
   archive to `engine-state` as a run artefact, the way it already hands `publish` the fast jar, and
   `engine-state` downloads it, deletes every `dagger-state-` entry and saves the new one. The peak
   is one entry instead of two and the size of the archive stops bounding anything. Nothing is
   deleted before the artefact has landed: a failed download costs a cold run, where a delete with
   no save to follow it would cost one too and leave nothing behind.)
5. **An `engine.json`, mounted at `/etc/dagger/engine.json` in the engine the job starts, bounds the
   cache with `gc.maxUsedSpace`.** The default policy targets 75 % of the disk and therefore never
   collects on a runner. The bound must sit above what one gate produces and below what the quota
   affords, and **the sweep must not evict the Gradle home and pnpm store cache volumes**, which are
   the thing being kept: a policy that reclaims them destroys what decision 4 saves. The spike fixes
   the value and establishes the policy that spares them. (Corrected: the policy is the generated
   list less its first entry, and the value is 7 GB. Read at `v0.21.9`, the list Dagger generates
   when `gc.policies` is absent opens with a policy filtering `type==exec.cachemount`, which is
   exactly these two volumes, with a `keepDuration` of 48 hours and a 512 MB cap; a restored state
   whose volumes were last used before that loses them.
   `internal/buildkit/cmd/buildkitd/config/gcpolicy.go`, `DefaultGCPolicy`, is where that list is
   written, and `engine/server/gc.go`, `getDagqlGCPolicy`, is where a declared `gc.policies`
   bypasses it entirely. So `.github/engine.json` declares the three remaining policies and drops
   the first. 7 GB sits above the 6.2 GB one cold gate produced and, at the 0.41 compression ratio
   the spike measured, caps the archive near 3 GB against the 4 GB the quota leaves free.)
   (Corrected: 9 GB, not 7. The bound was read against what one cold gate leaves, where the steady
   state is warm: 6.2 GB after a cold gate and 7.4 GB after a warm run stacked on it, so 7 GB sat
   inside the steady state and the sweep would have started biting around the third consecutive run.
   The policy deciding then is the `all: true` one, which is the eviction of the cache mount volumes
   this decision forbids, moved out of the generated list and into the declared one. 9 GB is 1.6 GB
   above the highest state ever measured and, at the 0.42 ratio the two archives measured, raises the
   entry from 2.69 GB toward 3.8 GB. That gigabyte comes out of the release path's buildx cache:
   the quota reads 8.15 GB of 10 on the day of this correction, the buildx entries having grown back
   to 5.46 GB, so what exceeds ten is evicted least-recently-used. It is the arbitration Consequences
   names, taken in favour of the pull request because that is the cost this lot exists to cut, and it
   is the operator's to reverse. A third consecutive run was never measured; what a sweep costs when
   it comes is a slower run and nothing else.)
6. **`org.gradle.caching=true`.** Worth nothing alone, the build cache living in the engine state
   decision 4 keeps, and decision 4 is worth a quarter less without it.
7. **`org.gradle.parallel` is refused**, on the cold failure reproduced at two memory bounds.
8. **A push that deletes a reference runs no gate.**
   `docs/adr/0030-the-gate-is-paid-where-it-can-fail.md`, decision 5, enumerated one push that sends
   no object, a tag on a commit `origin/main` already contains. A deletion is the other: git
   announces it with an all-zero local sha and it sends nothing, which is the principle that
   decision states in its own first sentence. `.githooks/pre-push` matched on the reference name
   alone, so deleting a branch paid a full gate.

## Consequences

**A first run after an eviction or an engine version bump pays the full cold price**, and seven days
without a push to `main` is enough to lose the entry. (Corrected: seven days is GitHub's eviction of
an entry nothing reads, and under the generated policy list it was not what bit first. Forty-eight
hours was, and what it took was the cache volumes inside a restored entry rather than the entry
itself. Decision 5's explicit `gc.policies` removes that policy, which leaves the seven days as the
binding delay the sentence says it is.) The failure mode is a slow run, never a wrong one.
(Corrected: block 20 shipped that as a claim and not as a mechanism. A truncated archive failed the
unpack, and a state the engine could not come up on failed the readiness loop, so one corrupt entry
would have failed every pull request until someone deleted it by hand. The closing block empties the
state directory and carries on cold in both cases, and the archive and the save are best effort, so
a failed upload no longer costs a green gate its image and its attestations.)

**The lot's own cache competes with the release path's.** Decision 4 keeps one entry and deletes its
predecessors, but that entry still occupies space the buildx cache had. If the spike measures a
compressed entry that does not fit the free 3.7 GB, the arbitration between the two is the
operator's and is recorded before block 20 ships.

**A workstation gains a local build cache nothing bounds.** `org.gradle.caching` applies to direct
`./gradlew` runs too, writing under `~/.gradle/caches/build-cache-1`, which `engine.json` does not
reach: it bounds the engine's cache, not a developer's Gradle home.

**The gate's perimeter does not change.** Nothing here removes a check.
`docs/adr/0030-the-gate-is-paid-where-it-can-fail.md` still decides when a pull request pays, and its
documentation-only shortcut is untouched: the job it skipped no longer exists, so that path costs one
runner before and one after. (Corrected: true after block 10 and false after block 20, which restored
and unpacked about 2.7 GB of engine state before running `prose`, roughly doubling a job block 10
measured at 1 min 19. The closing block conditions the restore and the unpack on the same output the
gate reads; starting the engine stays unconditional, `prose` being a `dagger call` too.)

## Block table

| Block | Branch | Content | Journeys |
|---|---|---|---|
| 10 | `ci/one-job-one-gradle-build` | Decision 1: `ci` in `.dagger/src/index.ts`, `validate.yml`, the root `AGENTS.md` | A pull request reports `validate / gate` green with one `BUILD SUCCESSFUL` line in the log instead of two, and the `verify` job's own duration under seven minutes, read from `gh run view --json jobs` rather than from wall clock; `dagger call gate`, `dagger call image` and `dagger call smoke` each still run alone on a workstation; a documentation-only pull request still runs `prose`, builds no image and reports green |
| 20 | `ci/the-engine-keeps-its-cache` | The spike, then decisions 2 to 7 if it holds: the engine the job starts, `engine.json`, the restore, save and prune steps in `validate.yml`, `org.gradle.caching` in `api/gradle.properties` (Corrected: decision 8 joined this block, the defect surfacing while the spike's own branch was deleted) | **The spike first**, on a throwaway branch: two successive pushes, the second on a different runner, the second run's Gradle line reporting tasks from cache after a restore from `actions/cache`, and the `restore` and `save` steps timed and their entry's compressed size read from `gh api .../actions/caches`. Then the block: a throwaway pull request opened after the block merges and `main` saves, perturbing one file of `api-usecases` as the probe did, reports **at least 60 of 170 tasks from cache** against the 73 the probe measured; `gh api .../actions/cache/usage` shows one entry carrying the engine version prefix, not two |

Two blocks. Block 10 is the only one with a production line, `.dagger/src/**` against the 200 that
prefix takes; block 20 touches configuration and one build file alone, so only the 600 bounds it.

**If the spike refutes the round trip, block 20 is abandoned and the lot closes with block 10.** The
handoff records the spike's measurements either way: a refuted mechanism is the expensive thing to
rediscover.
