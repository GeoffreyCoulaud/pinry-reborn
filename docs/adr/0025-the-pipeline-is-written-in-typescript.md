# 0025. The pipeline is written in TypeScript

Status: Accepted; the pipeline's base images are no longer unwatched: they are pinned by digest in
`.dagger/*.Dockerfile` and `compose.yml`, which Dependabot bumps, and `.dagger/package.json` alone stays outside it
(`docs/specs/2026-09-24-the-gate-is-deterministic.md`, block 60).
Date: 2026-09-08
Specification: `docs/specs/2026-09-08-monorepo.md`, question T and section 4.4
Related: `docs/adr/0024-three-projects-share-one-repository.md`, decision 5, which gives Dagger the
pipeline and deliberately stops short of naming its language (its last consequence says so).

## Context

**Decision 5 of ADR 0024 settles the tool and not the language.** The gate, the image build and the
smoke test become Dagger functions called by one command everywhere. What those functions are
written in is a separate choice, because the module is a source tree with a manifest, a dependency
and a runtime of its own, sitting at the repository root where every reader meets it.

**Kotlin is not on the list.** Dagger's official SDKs are Go, Python and TypeScript, with PHP and
Java beside them; `dagger init --sdk=` takes those five (docs.dagger.io, "Using Dagger SDKs" and the
CI quickstart). There is no Kotlin SDK, so the language the twelve modules are written in is not
available and the question is which language to add, not whether to add one.

**`clients/` brings TypeScript whatever this decision says.** ADR 0024 decision 1 gives the
JavaScript workspace its own root and decision 9 makes the web application a static bundle. So
TypeScript is a language of this repository already, its toolchain has to be understood already,
and choosing it here adds nothing that was not arriving anyway.

**What the pipeline does is ordinary code, and that is why the language is worth a record.** The
gate's three parts run concurrently and their failures have to read as sentences: the contract check
compares two documents and throws with the command that regenerates the stale one; the prose check
reads `git grep`'s exit status and tells "found an offender" apart from "the search itself failed";
the long dashes it refuses are built from code points so the file holding the rule is not its own
offender. None of that is expressible in a job matrix, which is the same argument that refused a
declarative manifest in ADR 0024, one level down.

## Decision

**The Dagger module at `.dagger/` uses the TypeScript SDK.**

`.dagger/package.json` is that module's own manifest, written by `dagger init`. It is not a workspace
root: `clients/` declares its workspace at `clients/`, with the package manager the lot that builds
it picks, and that declaration does not reach `.dagger/`
(`docs/specs/2026-09-08-monorepo.md`, section 4.1).

## Consequences

- **The repository holds three languages, and the third is one it was going to hold anyway.** Java,
  the SDK closest to the JVM the API already needs, would have added a fourth and put the only Java
  in a repository written in Kotlin. Go or Python would each have added a language with no other
  reason to be here.
- **The pipeline is outside every check the repository applies to its code.** Kover does not measure
  it, detekt does not read it, and the TypeScript is executed rather than type checked. What stands
  in for all of that is that a broken module fails every call, on the workstation and on the runner,
  before anything else runs. Nothing tests it in isolation and this lot adds no such test.
- **Two version pins have to move together**: `dagger.json`'s `engineVersion` and the CLI version the
  workflow installs. Neither is derived from the other.
- **Dependabot sees none of it.** `.github/dependabot.yml` declares `gradle` and `docker` at `/api`
  and `github-actions` at `/`; `.dagger/package.json` and the two base images the module names in
  TypeScript are in no ecosystem it scans. The manifest holds one dependency, written by
  `dagger init` and belonging to the SDK's own generation rather than to this project, so it is left
  unwatched deliberately; the base images are `eclipse-temurin:25-jdk`, which the `Dockerfile`
  already tracks under its own entry, and `debian:trixie-slim`.
