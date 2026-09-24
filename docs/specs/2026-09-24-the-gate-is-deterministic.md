# The gate is deterministic

Date: 2026-09-24
Status: Approved by the operator on 2026-09-24; one adversarial review closed, its findings
recorded in this document. Frozen when the last block merges.
ADRs: none. `maxWorkers`, `org.gradle.parallel` and `maxHeapSize` are settings whose reason fits in
a one-line comment, as `isolate: false` and `MAX_WORKERS` already do. The guard's base (block 60)
changes what ADR 0030 records the hook reading; the block amends that ADR's status line.

## 1. Goal

The full gate is green five times running on a memory-capped engine (section 3), and the gate gets
faster where a measurement says it can. Closes the backlog item "The web application's journey tests
time out under local load".

## 2. What was measured

Two probes on 2026-09-23 on the operator's workstation (12 CPUs; Docker Desktop, then at 7.4 GiB of
memory and 1 GiB of swap). The first report was lost with `/tmp` at a reboot, and block 10
re-measures what it recorded. The second is summarised here from its report.

- **The journeys time out for lack of memory** (first probe). Vitest starts one worker per CPU less
  one, eleven here. The full gate went red in 3 of 5 runs, each with full swap and about 72% of
  process time stalled on memory; at `maxWorkers: 4`, 3 of 3 green, the clients gate alone no slower.
  (Corrected: block 10 did not reproduce the reds. On native Docker under the same cap, 10 of 10
  full gates were green, with and without the change; the reds came only under Docker Desktop's
  7.4 GiB VM. Vitest alone on the host, each run in its own cgroup, peaked at 3.3-3.6 GB with 11
  workers and 1.6 GB with 4, both in 7.8-8.7 s, three runs each: the PR of block 10.)
- **The pnpm store is never reused.** CI run 35916269302 logs "Content-addressable store is at:
  /src/.pnpm-store/v11" and "reused 0, downloaded 407": the store sits outside the volume mounted at
  `PNPM_STORE` (`.dagger/src/index.ts:96`).
- **CI waits about 60 s on the API's health check.** In run 35916269302 the API probe took 30.5 s and
  printed "healthy after 1s", the compose probe 30.8 s, and nginx, which has no `HEALTHCHECK`, was
  ready in 0.3 s. What waits on `HEALTHCHECK --interval=30s` (`api/Dockerfile:71`) is Dagger's
  service start, before the probe runs.
- **Gradle runs one task at a time** (`org.gradle.parallel` off). `--parallel` took the API gate from
  132 s to 105 s on the host and 146 s to 115 s in the container, after a bytecode change in
  `api-domain`. The gate container's cgroup peak goes from 4.35-4.78 GB serial to 5.3-5.5 GB
  parallel.
- **`:api-application:test` is rarely a cache hit.** Its input `quarkus-app-test-model.dat` orders
  `local-projects` differently in most JVMs, so an unchanged tree pays about 87 s in the container
  instead of about 10 s, on every pull request that leaves the API alone.
- **The test JVM runs on Gradle's default 512 MB heap**, its live heap after full collections rising
  292, 385, 421 MB.
- **Plain `detekt` repeats `detektMain` and `detektTest`**, and is the only task reading
  `testFixtures`, with the one baselined finding at `api/config/detekt/baseline-api-utilities.xml:5`.
- **Dependabot watches `api/Dockerfile` only** (`.github/dependabot.yml:42-52`), not the web
  application's Dockerfile nor `compose.yml`.
- **The contract guard compares against the tip of `origin/main`** (`.dagger/src/index.ts:84`), not
  against where the branch left it: a local ref that is stale, or fresh on a branch not rebased,
  misreads what the branch changed.

## 3. Rules every block applies

- **The capped engine**: a Dagger engine at the version `dagger.json` pins, started on native Docker
  with `--memory 7.4g --memory-swap 8.4g` as `validate.yml:98-102` starts its own, and reached
  through `_EXPERIMENTAL_DAGGER_RUNNER_HOST`. (Corrected: CI's engine carries no `--memory` cap; the
  cap models a small workstation, the operator's Docker Desktop at 7.4 GiB, not CI.) A pull request that measures on it shows the cap with
  `docker inspect -f '{{.HostConfig.Memory}} {{.HostConfig.MemorySwap}}' <engine>`.
- **A timing claim carries its runs**, at least three, with the command, the engine and its state
  (warm or cold).
- **No timeout is widened** to make a test pass.

## 4. Blocks

| Block | Branch | Content |
|---|---|---|
| 10 | `fix/vitest-workers-fit-memory` | Five full gates on the capped engine without the change, the reds recorded; then `maxWorkers: 4` in `clients/apps/webapp/vite.config.ts` with a one-line comment naming the measurement, and five full gates green. The CI clients gate's test step before and after (4 workers on the 4-CPU runner, where Vitest ran 3). The backlog item deleted. This spec rides here. (Corrected: the reds did not reproduce on native Docker, so the evidence is the memory: Vitest's peak with 11 workers and with 4, three runs each, and the engine's memory pressure over five gates each side, all ten green) |
| 20 | `fix/the-pnpm-store-is-reused` | pnpm's store in the mounted `PNPM_STORE`. Evidence: two clients gates with a file under `clients/` changed between them, the second logging "downloaded 0" |
| 30 | `fix/the-health-check-answers-at-start` | First a scratch run with `--interval=1s` to confirm the wait follows the interval; then `--start-interval` on the API's `HEALTHCHECK`, once Dagger's handling of it at the pinned engine is read (Context7). Docker 25 is the floor it sets for `compose.yml`'s `service_healthy` and deployments, which `README.md` states. Evidence: the two probes' durations in CI before and after |
| 40 | `perf/gradle-runs-in-parallel` | `org.gradle.parallel=true` and an explicit `maxHeapSize` on the test JVM, sized from the heap measured; the pull request states the memory arithmetic. The API gate timed before and after; five full gates green on the capped engine. If they go red, the teammate stops with a tier-2 question (bound `org.gradle.workers.max`, or ship the heap without parallel) |
| 50 | `perf/the-application-tests-hit-the-cache` | First, whether a newer Quarkus orders `quarkus-app-test-model.dat` stably (changelog, issues); else a Gradle-side fix (input normalization of that file, with the reason it cannot hide a real change); else a tier-2 question. Evidence: five successive `--no-daemon` runs of `quarkusGenerateTestAppModel --rerun` giving one `sha256sum`, then three unchanged-tree runs in the container each showing `:api-application:test FROM-CACHE`. If the question ends with no fix, the block ships no code: an upstream report is filed and the handoff says so |
| 60 | `fix/the-images-and-the-guard-are-pinned` | The two Dockerfiles' and `compose.yml`'s base images by digest; `docker` entries for `/clients/apps/webapp` and `docker-compose` for `/` in `.github/dependabot.yml`. The contract guard compares against `git merge-base origin/main HEAD`, as `.githooks/pre-push:32` does for paths; ADR 0030's status line amended |
| 70 | `refactor/detekt-runs-once` | `detektTestFixtures` wired into `check` with its own baseline (the finding moved from the plain task's), plain `detekt` taken out of `check`; a seeded violation in each source set still reported |

Each block measures itself against `agents/workflow.md` after its first commit.

## 5. Acceptance

- Blocks 10 and 40: five consecutive full gates green on the capped engine.
- Every block: `dagger call gate` green, CI green, its own evidence as the table states.
- The lot's handoff gives the gate's wall time before and after, locally on the capped engine and in
  CI, each figure with its runs and the engine's state.

## 6. Backlog

Closes "The web application's journey tests time out under local load". No other item is adjacent.

## 7. Out of scope

| Not done | Observable |
|---|---|
| Pinning the images `.dagger/src/index.ts` pulls (`eclipse-temurin:25-jdk`, `debian:trixie-slim`, `NODE`, `PROXY_IMAGE`) | Dependabot reads no TypeScript, so a digest there would never move; they stay tags, and the gate may build on a newer patch than the images ship on |
| Caching the Dagger engine image in CI | `validate.yml` still pulls it from `registry.dagger.io` |
| Splitting `:api-application:test` into forks | Measured at -8 s for three more Quarkus boots |
| The configuration cache | Measured at 0 s |
