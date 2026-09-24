# The gate is deterministic

Date: 2026-09-24
Status: Draft, awaiting the specification review. Frozen when the last block merges.
ADRs: none. Each change below is a setting or a fix whose reason fits in its block.

## 1. Goal

A green gate stays green on a rerun, locally and in CI, whatever the machine runs beside it; and the
gate gets faster where a measurement says it can. Closes the backlog item "The web application's
journey tests time out under local load".

## 2. What was measured

Two probes on 2026-09-23 on the operator's workstation (12 CPUs; Docker Desktop, then at 7.4 GiB of
memory and 1 GiB of swap). The first report was lost with `/tmp` at a reboot; its figures survive in
the lead's summary to the operator and are re-measured by the block that relies on them.

- **The journeys time out for lack of memory, not CPU.** Vitest starts 11 workers (CPUs less one),
  3.2 to 4.1 GiB, beside Gradle's JVMs at 2.4 to 2.6 GiB. The full gate went red in 3 of 5 runs, each
  with full swap and about 72% of process time stalled on memory. At `maxWorkers: 4`, 3 of 3 green,
  and the clients gate alone no slower (8.3 s against 7.8 to 8.4 s).
- **The pnpm store is never reused.** pnpm writes it to `/src/.pnpm-store`, outside the volume
  mounted at `PNPM_STORE` (`.dagger/src/index.ts:96`), so every clients gate downloads its 407
  packages, locally and in CI.
- **CI waits about 60 s for nothing.** The API smoke and the compose smoke each wait about 30 s while
  Quarkus is up in 1.3 s; the likely cause is `HEALTHCHECK --interval=30s` (`api/Dockerfile:71`),
  whose first probe comes after one interval.
- **Gradle runs one task at a time** (`org.gradle.parallel` off). `--parallel` took the API gate from
  132 s to 105 s on the host and 146 s to 115 s in the container, after a bytecode change in
  `api-domain`, for 0.6 to 1 GB more memory at peak.
- **`:api-application:test` is never a cache hit.** Its input `quarkus-app-test-model.dat` orders
  `local-projects` differently in each JVM, so an unchanged tree pays about 87 s in the container
  instead of about 10 s: every pull request that leaves the API alone pays it.
- **The test JVM runs on Gradle's default 512 MB heap**, its live heap after full collections rising
  292, 385, 421 MB.
- **Plain `detekt` repeats `detektMain` and `detektTest`**, and is the only task reading
  `testFixtures`.
- **Base images are tags**, and Dependabot watches no Dockerfile (`.github/dependabot.yml`).
- **The contract guard reads the local `origin/main`** (`.dagger/src/index.ts:84`), stale when the
  clone has not fetched.

## 3. Rules every block applies

- **A block that changes memory use is measured on a memory-capped engine**: a Dagger engine started
  on native Docker with `--memory 7.4g`, reached through `_EXPERIMENTAL_DAGGER_RUNNER_HOST`, so the
  measurement does not depend on the operator's Docker Desktop setting (16 GiB since 2026-09-24).
  The pull request gives the command, the runs and their outcome.
- **A timing claim carries its runs**, at least three, with the command and the engine's state (warm
  or cold).
- **No timeout is widened** to make a test pass.

## 4. Blocks

| Block | Branch | Content |
|---|---|---|
| 10 | `fix/vitest-workers-fit-memory` | `maxWorkers: 4` in `clients/apps/webapp/vite.config.ts`, a one-line comment naming the measurement; five full gates green on the capped engine. The backlog item deleted. This spec rides here |
| 20 | `fix/the-pnpm-store-is-reused` | pnpm writes its store to the mounted `PNPM_STORE`; a second clients gate downloads nothing (the pull request shows pnpm's own count) |
| 30 | `fix/the-health-check-answers-at-start` | `--start-interval` on the API's `HEALTHCHECK` (Docker's documentation, read through Context7, for the engine version CI runs); the two smoke waits measured before and after |
| 40 | `perf/gradle-runs-in-parallel` | `org.gradle.parallel=true` and an explicit `maxHeapSize` on the test JVM, sized from the heap measured under the capped engine; the API gate timed before and after, and five full gates green on the capped engine |
| 50 | `perf/the-application-tests-hit-the-cache` | First, whether a newer Quarkus orders `quarkus-app-test-model.dat` stably (its changelog and issues); else a Gradle-side fix (input normalization of that file, with the reason it cannot hide a real change); else a tier-2 question to the operator, with an upstream report drafted. Evidence: two clean runs on an unchanged tree, the second a cache hit |
| 60 | `fix/the-images-and-main-are-pinned` | The two Dockerfiles' base images by digest, and a `docker` entry per Dockerfile in `.github/dependabot.yml` so the digests move; `.githooks/pre-push` fetches `origin/main` before the gate |
| 70 | `refactor/detekt-runs-once` | `detektTestFixtures` wired into `check`, plain `detekt` taken out of it; the same findings on a seeded violation in each source set |

Each block measures itself against `agents/workflow.md` after its first commit.

## 5. Acceptance

- Blocks 10 and 40: five consecutive full gates green on the capped engine.
- Every block: `dagger call gate` green; CI green; its own evidence as the table states.
- The lot's handoff gives the gate's wall time, locally and in CI, before and after, each figure with
  its runs.

## 6. Backlog

Closes "The web application's journey tests time out under local load". No other item is adjacent.

## 7. Out of scope

| Not done | Observable |
|---|---|
| Pinning the images `.dagger/src/index.ts` pulls (`eclipse-temurin:25-jdk`, `debian:trixie-slim`, `NODE`) | Dependabot reads no TypeScript, so a digest there would never move; they stay tags |
| Caching the Dagger engine image in CI | `validate.yml` still pulls it from `registry.dagger.io` |
| Splitting `:api-application:test` into forks | Measured at -8 s for three more Quarkus boots; not worth it |
| The configuration cache | Measured at 0 s |
