# Handoff: the gate is deterministic

Date: 2026-09-25
Specification: `docs/specs/2026-09-24-the-gate-is-deterministic.md`
ADR: none.
Blocks: 10 `fix/vitest-workers-fit-memory` (#208), 20 `fix/the-pnpm-store-is-reused` (#209),
30 `fix/the-health-check-answers-at-start` (#210), 40 `perf/gradle-runs-in-parallel` (#211),
50 `perf/the-application-tests-hit-the-cache` (#213), 60 `fix/the-images-and-the-guard-are-pinned` (#214),
70 `refactor/detekt-runs-once` (this block's pull request).
Written in block 70, the lot's last code block, to be corrected in the closing one.
Tier: Spec. One specification review ran, `.reviews/the-gate-is-deterministic-spec.md`, its findings
recorded in the specification. The holistic review runs at the head of Wrap.

## Current state

- **The web application's tests run on four workers** (#208). The reds did not reproduce on native
  Docker; four workers halve Vitest's memory peak, 3.3-3.6 GB to 1.6 GB, at the same duration.
- **pnpm reuses its store** in the gate and in the web application's image: `downloaded 0` on the
  second install (#209).
- **The API's health check answers at start**: `--start-interval=1s` takes the smoke probe from
  30.5 s to 2.4 s in CI (runs 35916269302 and 36055029613, #210). Docker Engine 25 is the floor.
- **Gradle runs in parallel**, the application tests on a 1 GB heap: the API gate from 144-157 s to
  112-113 s on the capped engine (#211).
- **`:api-application:test` is a cache hit when `api/` is unchanged**: Quarkus 3.39.5 writes its
  test model in a stable order (#213).
- **Every image is pinned by digest where Dependabot bumps it**, the pipeline's own included, and the
  contract guard compares against the merge base (#214).
- **detekt runs once per source set** (block 70): `check` runs `detektMain`, `detektTest` and
  `detektTestFixtures`, and no longer the plain `detekt`. Baselines are
  `baseline-<module>-<sourceSet>.xml`; the plain ones are deleted.

The backlog item "The web application's journey tests time out under local load" was deleted in #208.

## The gate's wall time

**Local, full `dagger call gate`**, capped engine (`docker inspect` gives `7945689497 9019431321`),
native Docker 29.8.1, 12 processors, warm. A nonce file at the root before each run.

| Case | Before the lot (#208, `main` at the time) | After (block 70's branch) |
|---|---|---|
| `api/` unchanged | 116, 98, 99, 97, 97 s; `:api-application:test` executed | 25, 26, 25 s; `FROM-CACHE` |
| `api-domain` bytecode changed | no full gate measured; the API gate alone took 157, 153, 144 s (#211) | 132, 123, 122 s |

The "after" runs: one cold run first (255 s, 159 tasks executed), then one warm run that fills the
build cache for the tree (105 s), neither counted.

**CI, `verify` job.** Before, pull requests of the previous lot, each changing `api/`: 8 m 44 s
(35916269302), 8 m 41 s (35905030198), 7 m 56 s (35902175097), 7 m 21 s (35886175514). After,
pull requests that leave the Gradle inputs alone: 3 m 17 s (36074593407), 3 m 46 s (36073803533),
3 m 28 s (36070761598). The two sides differ in shape as well as in the lot's changes, so the
difference is not the lot's alone. A warm run that changes `api/` is this block's own, in its pull request.

**After #214's merge**: `main` run 36074336029, `verify` 5 m 33 s, and pull request run 36074593407,
3 m 17 s. Both restored the state `8800a6da` saved, from before #214, so neither is fully warm: the
first run to restore the state `afeac303` saved is still to be read. Both show
`:api-application:test FROM-CACHE`, 36074593407 on a change that leaves `api/` alone.

**Engine state archive**: 4.89 GiB (`gh cache list`, `dagger-state-v0.21.9-afeac303...`, 2026-09-25),
up from the 4.4 GB `AGENTS.md` recorded on 2026-09-18, which block 70 corrects.

## Tier-2 questions and operator decisions

| Where | Question | Answer |
|---|---|---|
| Block 10 (#208) | The reds did not reproduce on native Docker | "A: memory evidence", then "measure Vitest alone" |
| Block 20 (#209) | The web application's image also downloaded every package | Fixed in the block |
| Block 60 (#214) | Operator's widening | The pipeline's four images pinned, the environments as Dockerfiles under `.dagger/` |

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
  `AbstractClassCanBeConcreteClass` on `BaseTest`, now baselined (block 70).
- **pnpm's version is written in `clients/package.json` and both Node Dockerfiles**; Dependabot moves
  none of the Dockerfile lines (#214).

## What is not validated

- **The reds of the first probe** came under Docker Desktop's 7.4 GiB VM and were never reproduced
  on native Docker (#208).
- **A fully warm CI run after #214**, and the `api-domain` case before the lot on a full gate.
- **arm64**: pull requests build one architecture (`AGENTS.md`).

## Next step

Wrap: the holistic review over `git diff lot/0.36.0-the-refusals-are-declared..origin/main`, then the
closing block with its findings and this handoff corrected; then the lot's tag.
