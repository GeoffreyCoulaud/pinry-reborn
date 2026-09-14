# AGENTS.md

Pinry Reborn, a self-hosted pin board: users, pins, boards, tags, images and exports. The repository holds three
projects (`docs/adr/0024-three-projects-share-one-repository.md`): the API server that owns the business logic, and the
web application and browser extension that will consume it.

Process, engineering norms and writing conventions live in separate documents; read the one your task needs:

- `agents/workflow.md` : phases, tiers, the two reviews (mandates under `agents/reviews/`), backlog rules.
- `agents/engineering.md` : TDD, coverage, gate perimeter, Kotlin and backend norms.
- `agents/writing.md` : documentation regimes, language and style rules.
- `docs/handoffs/` : the newest file is the entry point (current state, pitfalls, next step).
- `docs/backlog.md` : open items only.

**The process is shared and the technical norms are not.** Each ecosystem root carries its own `AGENTS.md` for its
norms, its commands and its gate; this file carries what holds for the repository.

## Where things live

| Path        | What                                                                                                      |
|-------------|-----------------------------------------------------------------------------------------------------------|
| `api/`      | The Gradle build: the twelve modules, `Dockerfile`, `config/`, `.idea/`. Read `api/AGENTS.md` before touching it. |
| `contract/` | The API's interface artefact, produced by `api/` and consumed by the clients.                              |
| `clients/`  | The pnpm workspace: `apps/webapp`, `packages/` for what the clients share. Read `clients/AGENTS.md` before touching it. The browser extension is not built yet. |
| `.dagger/`  | The pipeline, in TypeScript (`docs/adr/0025-the-pipeline-is-written-in-typescript.md`). Belongs to no ecosystem: it calls both. |
| Root        | `docs/`, `agents/`, `security/`, `.claude/`, `.github/`, `.githooks/`, `dagger.json`.                      |

- **`contract/openapi.json` is generated and committed**, never edited by hand (`agents/writing.md`).
  `contract/.gitattributes` marks it `linguist-generated`, and a block's diff budget excludes it for the
  same reason as `clients/pnpm-lock.yaml`: the budget measures what a human rereads.
- **Its `info.title` and `info.version` are the contract's own**, declared by
  `quarkus.smallrye-openapi.info-title` and `.info-version` in
  `api/api-application/src/main/resources/application.properties`. Left undeclared they fall back to the
  build, the title to the Gradle module's name and the version to `quarkus.application.version`, which a
  client would then negotiate on (`docs/adr/0024-three-projects-share-one-repository.md`, decision 6).
  `ContractVersionDeclarationTest` refuses the version's fallback.
- **The contract's version is always a plain release**, never a prerelease: `oasdiff` reads
  `1.0.0-SNAPSHOT` to `1.0.0` as a *decrease*, where semver precedence makes it an increase, and the guard
  below would refuse a break the version does declare. An accepted limit of the tool, not a defect here.
- **`contract/frozen/` holds one document per contract major still served**, as `<major>.json`: a document
  enters when a major becomes still served and leaves when it stops being served. Empty during the alpha,
  where breaking is the stated policy of the README, and kept in git by a `.gitkeep` alone; the guard reads
  the directory's absence as no major still served.

## Setup (once per clone)

- `git config core.hooksPath .githooks` (enables pre-commit and pre-push hooks).
- **Docker and the Dagger CLI**, which the gate and the `pre-push` hook both go through. `dagger.json` pins the
  engine version; install the CLI at that version.
- `python3` on the PATH (`.claude/hooks/evidence-guard.py` runs on every Bash command; without python3 it enforces
  nothing, silently).
- Each ecosystem root has its own setup steps on top of these; `api/AGENTS.md` carries the API's.

## The gate

**One command, from anywhere in the repository: `dagger call gate`.** It is what `pre-push` runs and what
`dagger call ci` runs on a runner, in the same container, and it holds five things:

| Function                     | What it runs                                                                     |
|------------------------------|-----------------------------------------------------------------------------------|
| `dagger call api-gate`       | The API's Gradle gate (`api/AGENTS.md`), with the JDK, libvips and python3 pinned. |
| `dagger call clients-gate`   | The clients' gate (`clients/AGENTS.md`), with Node and pnpm pinned: install, catalogue compile, typecheck, lint, import boundaries, Vitest with its coverage bound, and the static bundle. |
| `dagger call prose`          | No long dash in a tracked text file, and the evidence guard's own tests.           |
| `dagger call contract`       | Produces `contract/openapi.json`. `gate` refuses a committed document that differs. |
| `dagger call contract-guard` | The contract breaks no still served major, and its `info.version` admits what it changed against `main` (`oasdiff`). |

A check whose scope is the repository goes to `.dagger/`; a check whose scope is one ecosystem goes to that
ecosystem's own gate.

**It is paid where it can fail** (`docs/adr/0030-the-gate-is-paid-where-it-can-fail.md`, as
`docs/adr/0031-the-gate-builds-once-and-keeps-its-cache.md` decision 8 extends it). Three pushes are let off, each
because it can carry no defect the gate would catch: a pull request whose every changed path ends in `.md` runs
`dagger call prose` in its place, restores no engine state and builds no image; a push that only deletes a reference
sends no object and runs nothing at all; and a push whose every reference is a tag on a commit `origin/main` already
contains runs nothing either. One path not ending in `.md`, or one branch in the push, and the full gate is back.

## The image

Three calls outside the gate, because minutes of image build have no place in `pre-push`:

| Function                   | What it does                                                                        |
|----------------------------|---------------------------------------------------------------------------------------|
| `dagger call image`        | Builds `api/Dockerfile` for the engine's own platform and reads the machine back from inside it. `--platforms=linux/amd64,linux/arm64` builds everything the image ships on. |
| `dagger call smoke`        | Starts the image and waits for `/q/health`. The only thing in the repository that runs what ships. |
| `dagger call quarkus-app`  | Returns the fast-jar layout the `Dockerfile` copies, so a caller builds the image with no JDK of its own. Used by the release path alone, and produced by the gate's own build: on a runner it is a cache hit of the `ci` call that precedes it, and therefore the bytes `smoke` started. |

The suite reads production's `application.properties` for every key its own file leaves alone: each
`application.properties` on the classpath is a separate configuration source and overrides **per property**, not per
file (Quarkus configuration reference). So a key the test file leaves alone is exercised by the suite, and a defect
in it does not wait for the image. **What waits for `dagger call smoke` is narrower**: a key the test file does
declare, whose deployment value is then never exercised as shipped, and the image itself, no test starting it at
all. That defect now reaches a workstation rather than only continuous integration.

**A pull request builds one architecture**, the second being emulated and slow. `validate.yml` builds both with
buildx on the release path, so a release still ships both; a defect that shows on arm64 alone therefore surfaces
at the release rather than on the pull request that introduced it.

## CI

CI (`validate.yml`) **calls** the pipeline: a `verify` job runs one `dagger call ci`, which is the gate and then the
image built and smoked from the fast jar the gate's own container produced
(`docs/adr/0031-the-gate-builds-once-and-keeps-its-cache.md`, decision 1). Two jobs on two runners paid for that
jar twice. `gate`, `image` and `smoke` stay callable on their own, which is what a workstation types. A check added
to the pipeline is on the next pull request with nothing to add here.

**`verify` holds `contents: read` and nothing else**, it being the job that runs the build's third-party plugins.
What needs a write scope is two jobs that need it and run nowhere else: **`publish`**, on the release path alone,
which is what CI still holds that the pipeline cannot, a registry and GitHub's identity, so the push to GHCR, the
cosign attestations, both SBOMs and the OpenVEX predicate; and **`prune`**, on `main` alone, which carries the one
scope deleting a cache entry needs. `publish` rebuilds nothing: `verify` calls the pipeline once more,
`dagger call quarkus-app export`, a cache hit on the build `ci` already ran, and hands that fast jar over as a run
artefact, so the image `buildx` pushes carries the bytes `dagger call smoke` started.

**`verify` starts its own engine and keeps its state between runs** (`docs/adr/0031-the-gate-builds-once-and-keeps-its-cache.md`,
decisions 2 to 6). A container named `dagger-engine` is started on a state directory `actions/cache` restored, with
`.github/engine.json` mounted at `/etc/dagger/engine.json`, and the CLI reaches it through
`_EXPERIMENTAL_DAGGER_RUNNER_HOST`. **A pull request restores and never saves; a push to `main` stops the engine,
archives the state, saves it under a key carrying the engine version and the commit, and deletes every other entry
under the `dagger-state-` prefix.** One entry is a condition and not a tidiness: the archive is about three gigabytes
against the four the repository's ten-gigabyte quota leaves free, so a second would evict the release path's buildx
cache. **A restore that does not unpack, and an engine that will not come up on it, both empty the state and carry
on cold**, the cache being an optimisation and never a condition of a green run.
**`.github/engine.json` declares `gc.policies` rather than a bound alone**, because the list Dagger generates
otherwise reclaims the Gradle home and pnpm store volumes, which are the thing being kept.

## Gotchas

- **A local merge to `main` bypasses CI** (`enforce_admins` is false). Always push and open a PR; merge is rebase-only
  (`gh pr merge --rebase`).
- **Nothing regenerates `contract/openapi.json` for you.** The gate refuses a stale document and names the command
  that refreshes it; the `pre-commit` hook rejects em/en-dashes in staged additions and does nothing else.
- **The gate reads git history**, the contract on `origin/main` being what a merge would replace. A shallow clone
  has no such ref and the guard says so: `validate.yml` carries `fetch-depth: 0` for it.
- **A break is allowed and a version that hides one is not.** Raise
  `quarkus.smallrye-openapi.info-version` by a major, regenerate, and the gate accepts the break.
  `contract/frozen/` is the other half: nothing there may break at all, whatever the version says.
