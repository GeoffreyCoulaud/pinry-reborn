# Handoff: the gate is deterministic

Date: 2026-09-25
Specification: `docs/specs/2026-09-24-the-gate-is-deterministic.md`
ADR: none.
Blocks: 10 `fix/vitest-workers-fit-memory` (#208), 20 `fix/the-pnpm-store-is-reused` (#209),
30 `fix/the-health-check-answers-at-start` (#210), 40 `perf/gradle-runs-in-parallel` (#211),
50 `perf/the-application-tests-hit-the-cache` (#213), 60 `fix/the-images-and-the-guard-are-pinned` (#214),
70 `refactor/detekt-runs-once` (this block's pull request). (Corrected: #217. The closing block is
`fix/the-gate-lot-closes`.)
Written in block 70, the lot's last code block, to be corrected in the closing one.
Tier: Spec. One specification review ran, `.reviews/the-gate-is-deterministic-spec.md`, its findings
recorded in the specification. The holistic review runs at the head of Wrap. (Corrected: it ran,
`.reviews/the-gate-is-deterministic-holistic.md`, 1 MAJOR and 7 MINOR; their exits are under
"The holistic review's findings".)

## Current state

- **The web application's tests run on four workers** (#208). The reds did not reproduce on native
  Docker; four workers halve Vitest's memory peak, 3.3-3.6 GB to 1.6 GB, at the same duration.
- **pnpm reuses its store** in the gate and in the web application's image: `downloaded 0` on the
  second install (#209).
- **The API's health check answers at start**: `--start-interval=1s` takes the smoke probe from
  30.5 s to 2.4 s in CI (runs 35916269302 and 36055029613, #210). Docker Engine 25 is the floor.
- **Gradle runs in parallel**, the application tests on a 1 GB heap: the API gate from 144-157 s to
  112-113 s on the capped engine (#211). (Corrected: Gradle runs serially again. The closing block
  withdrew `org.gradle.parallel` after one of three cold gates went red on an out-of-memory kill,
  under "The holistic review's findings"; the 1 GB heap stays, and the 112-113 s is no longer
  the API gate's time.)
- **`:api-application:test` is a cache hit when `api/` is unchanged**: Quarkus 3.39.5 writes its
  test model in a stable order (#213).
- **Every image is pinned by digest where Dependabot bumps it**, the pipeline's own included, and the
  contract guard compares against the merge base (#214).
- **detekt runs once per source set** (block 70): `check` runs `detektMain`, `detektTest` and
  `detektTestFixtures`, and no longer the plain `detekt`. Baselines are
  `baseline-<module>-<sourceSet>.xml`; the plain ones are deleted. (Corrected: #217. The closing
  block names `detektMain` and `detektTest` so a missing one fails the build, and matches
  `detektTestFixtures` alone, which only `api-utilities` has.)
- **Node 26 builds the web application**, in the gate and in its image (closing block): both
  `node:26-slim` lines carry the digest of its multi-platform index, `ec7758ee...`. Dependabot
  proposed Node 25 (#216), closed by the operator: 25 reached end of life in June 2026 and
  `clients/package.json` excludes it.

The backlog item "The web application's journey tests time out under local load" was deleted in #208.

## The gate's wall time

**Local, full `dagger call gate`**, capped engine (`docker inspect` gives `7945689497 9019431321`),
native Docker 29.8.1, 12 processors, warm. A nonce file at the root before each run.

| Case | Before the lot (#208, `main` at the time) | After (block 70's branch) |
|---|---|---|
| `api/` unchanged | 116, 98, 99, 97, 97 s; `:api-application:test` executed | 25, 26, 25 s; `FROM-CACHE` |
| `api-domain` bytecode changed | no full gate measured; the API gate alone took 157, 153, 144 s (#211) | 132, 123, 122 s |

The "after" runs: one cold run first (255 s, 159 tasks executed), then one warm run that fills the
build cache for the tree (105 s), neither counted. (Corrected: every "after" figure was taken with
`org.gradle.parallel` on, which the closing block withdrew; the serial gate after the lot was not
timed.)

**CI, `verify` job.** Before, pull requests of the previous lot, each changing `api/`: 8 m 44 s
(35916269302), 8 m 41 s (35905030198), 7 m 56 s (35902175097), 7 m 21 s (35886175514). After,
pull requests that leave the Gradle inputs alone: 3 m 17 s (36074593407), 3 m 46 s (36073803533),
3 m 28 s (36070761598). The two sides differ in shape as well as in the lot's changes, so the
difference is not the lot's alone. A warm run that changes `api/` is this block's own, in its pull request.
(Corrected: #217's run 36076842313 changed `api/build.gradle.kts` but not the test task's inputs,
so it is not that case either; no warm CI run that changes `api/` was read in this lot.)

**After #214's merge**: `main` run 36074336029, `verify` 5 m 33 s, and pull request run 36074593407,
3 m 17 s. Both restored the state `8800a6da` saved, from before #214, so neither is fully warm: the
first run to restore the state `afeac303` saved is still to be read. Both show
`:api-application:test FROM-CACHE`, 36074593407 on a change that leaves `api/` alone.
(Corrected: #217's run 36076842313 is that run. It restored `dagger-state-v0.21.9-afeac303...`,
ran `verify` in 3 m 52 s and reported `:api-application:test FROM-CACHE`.)

**Engine state archive**: 4.89 GiB (`gh cache list`, `dagger-state-v0.21.9-afeac303...`, 2026-09-25),
up from the 4.4 GB `AGENTS.md` recorded on 2026-09-18, which block 70 corrects. (Corrected: 5.03 GB,
`dagger-state-v0.21.9-a3a11955...`, beside 2.72 GB of buildx entries in the 10 GB quota, read later
on 2026-09-25 by `gh cache list` and `gh api .../actions/cache/usage`; `AGENTS.md` states the
consequence the closing block gives it.)

## Tier-2 questions and operator decisions

| Where | Question | Answer |
|---|---|---|
| Block 10 (#208) | The reds did not reproduce on native Docker | "A: memory evidence", then "measure Vitest alone" |
| Block 20 (#209) | The web application's image also downloaded every package | Fixed in the block |
| Block 60 (#214) | Operator's widening | The pipeline's four images pinned, the environments as Dockerfiles under `.dagger/` |
| Wrap, holistic MAJOR | Two or three cold successes against ADR 0031's refusal reproduced twice | Prove it cold: three cold gates |
| Closing block | Cold gate 3 red on an out-of-memory kill: withdraw parallel, bound the workers, or accept | "A": withdraw `org.gradle.parallel`, keep the 1 GB heap |
| Wrap, holistic MINOR | The archive and the release path's buildx cache no longer fit the quota together | The risk accepted during the alpha, releases being rare |
| Dependabot (#216) | Node 25 proposed | Closed by the operator: 25 reached end of life in June 2026; Node 26 instead |

## The holistic review's findings

`.reviews/the-gate-is-deterministic-holistic.md`, each fixed in the closing block `fix/the-gate-lot-closes`.

| Finding | Exit |
|---|---|
| MAJOR, `org.gradle.parallel` reverses ADR 0031 decision 7 | Withdrawn. Three cold `dagger call gate`, each on a fresh engine v0.21.9 with an empty state volume, native Docker, `--memory 7.4g --memory-swap 8.4g` (`7945689497 9019431321`), `.github/engine.json` mounted, at `c680d628`: 265 s green (`oom_kill 0`, memory peak 7947829248), 249 s green (`oom_kill 0`, peak 7946428416), 254 s red, "Gradle build daemon disappeared unexpectedly" (`oom_kill 2`, peak 8476499968). The kernel log shows the engine's memory cgroup killing two `java` processes of about 2.4 GB resident. ADR 0031's status line records decision 7 held, with this cause |
| MINOR, `MAX_WORKERS`'s comment | Rewritten in one line for the serial build again: the pin is the runner's four cores, whatever the engine host has |
| MINOR, detekt's `dependsOn` matches silently | `dependsOn("detektMain", "detektTest", ...)` by name, `tasks.matching` for `detektTestFixtures` alone |
| MINOR, the cache quota paragraph of `AGENTS.md` | Rewritten in GB: archive 5.03 GB and buildx entries 2.72 GB of the 10 GB quota; a release builds partly cold after an eviction, a pull request is not affected, the pnpm store is in the archive twice; accepted by the operator |
| MINOR, ADR 0025 says the pipeline's images are unwatched | Its status line amended |
| MINOR, `imageIn` general for one caller | Narrowed to `proxyImage(source)`, keeping the error that names the file |
| MINOR, the Dependabot comment on `/api` | "Base image of `api/Dockerfile`, pinned by tag and digest: one grouped weekly PR." |
| MINOR, the handoff predates #217 | Corrected in place: #217, run 36076842313 |

The backlog is reconciled: the lot closed one item, deleted in #208, and files none; the quota risk is
accepted rather than filed.

## Pitfalls

- **The capped engine runs under `DOCKER_CONTEXT=default`**: `docker` on this workstation defaults to
  Docker Desktop, and the engine must sit in the context `_EXPERIMENTAL_DAGGER_RUNNER_HOST` resolves in (#213).
- **`docker rm --force` on an engine discards its cache**: the next gate is cold on a kept volume (#208).
- **A timed Dagger call needs a change it cannot serve from cache**: a second `smoke` is a function
  cache hit, and a change outside `api/` leaves Gradle cached (#210, #211).
- **A failing task is never stored in the cache**, so a red test looks like a cache miss (#213).
- **A pull request restores `main`'s engine state and never saves it**: a change to what the state
  holds (store, environments) shows in CI only after its merge (#209, #214).
- **Type resolution finds what the plain task could not**: `detektTestFixtures` reports
  `AbstractClassCanBeConcreteClass` on `BaseTest`, now baselined (block 70, corrected: #217).
- **pnpm's version is written in `clients/package.json` and both Node Dockerfiles**; Dependabot moves
  none of the Dockerfile lines (#214).
- **A warm gate cannot show a memory failure a cold one does**: parallel passed five warm gates in
  #211 and failed one of three cold ones in the closing block. A memory claim needs cold runs on a
  fresh engine.

## What is not validated

- **The reds of the first probe** came under Docker Desktop's 7.4 GiB VM and were never reproduced
  on native Docker (#208).
- **A fully warm CI run after #214**, and the `api-domain` case before the lot on a full gate.
  (Corrected: the warm run is validated, run 36076842313 above; the `api-domain` case is not, nor a
  warm CI run that changes `api/`.)
- **arm64**: pull requests build one architecture (`AGENTS.md`).

## Next step

Wrap: the holistic review over `git diff lot/0.36.0-the-refusals-are-declared..origin/main`, then the
closing block with its findings and this handoff corrected; then the lot's tag. (Corrected: the
review and the closing block are done; the tag follows its merge.)
