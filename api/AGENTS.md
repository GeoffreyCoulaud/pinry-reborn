# AGENTS.md

Pinry Reborn's API server: business logic of a self-hosted pin board (users, pins, boards, tags, images, exports),
exposed as an HTTP API. Kotlin + Quarkus, Clean Architecture, SQLite store, filesystem image storage, task worker for
long operations.

This file holds what is true of the Kotlin build alone: its norms, its commands and its gate
(`docs/adr/0024-three-projects-share-one-repository.md`, decision 2). The process, the phases and the writing rules are
the repository's, in the root `AGENTS.md` and the documents it points at. Paths below are relative to the repository
root, so that they read the same from either file.

## Where the code lives

The Gradle modules `api/settings.gradle.kts` declares. Layering enforced by the build graph and
`ArchitectureKonsistTest`.

| Module                     | Role                                                                      |
|----------------------------|---------------------------------------------------------------------------|
| `api-domain`               | Pure: entities, enums, ports. No project dependency.                      |
| `api-usecases`             | Business logic, exceptions, search, exports, task contracts.              |
| `api-persistence-sqlite`   | Ebean/SQLite: models, mappers, repositories, migrations (`dbmigration/`). |
| `api-presentation-quarkus` | Jakarta REST: controllers, DTOs, mappers, security, OpenAPI.              |
| `api-storage-filesystem`   | Image store, rendition cache, export archives.                            |
| `api-imaging-vips`         | libvips adapter (vips-ffm).                                               |
| `api-fetch-http`           | Remote image fetch behind an address policy.                              |
| `api-system`               | Clock, bcrypt, token generation.                                          |
| `api-worker-quarkus`       | Task worker: dispatcher, handlers, export retention.                      |
| `api-utilities`            | Shared helpers, `BaseTest` fixture (testFixtures).                        |
| `api-application`          | Composition root + end-to-end integration tests.                          |
| `detekt-rules`             | Project detekt rules; outside the layering.                               |

## Setup (once per clone)

- Native libvips: `brew install vips` (macOS) or `libvips42t64` (Ubuntu 24.04), otherwise
  `api-imaging-vips` and image-touching integration tests cannot load the library.
- **`JAVA_HOME` on JDK 25**, exported before any `./gradlew` **and before any `git commit`**, the `pre-commit` hook
  running Gradle too: `export JAVA_HOME=~/.sdkman/candidates/java/25-tem`. A shell that falls back to JDK 21 fails
  detekt on `class file version 69`. The toolchain the build compiles against is provisioned automatically; the JVM
  Gradle itself runs on is not.

## Commands

**Every Gradle command runs from `api/`**: the wrapper is `api/gradlew` and the settings file it needs
sits beside it, so `./api/gradlew` from the repository root finds no build at all. Prefix with
`cd api &&` from anywhere else.

- Runner: `./gradlew` (committed wrapper; JDK 25 toolchain auto-provisioned).
- **The API's gate**: `./gradlew gate`. It covers this build alone; the repository's single knob is
  `dagger call gate`, which runs this one in a container and the repository-wide checks beside it (root `AGENTS.md`).
- **Regenerate the contract**: `./gradlew :api-application:quarkusAppPartsBuild --rerun`, which writes
  `contract/openapi.json`. Nothing does it for you, and the gate refuses a stale document. `--rerun` is not
  optional: a document edited by hand leaves the sources untouched, so an ordinary build is up to date and
  writes nothing.
- One test: `./gradlew :api-usecases:test --tests "UserCreatorTest"`
- New migration (after changing an entity model): `./gradlew :api-persistence-sqlite:generateDbMigration`.
- Destructive migration (drop): re-run the generator with the property **in the generator's JVM**, not on the Gradle
  CLI:
  `JAVA_TOOL_OPTIONS="-Dddl.migration.pendingDropsFor=<version>" ./gradlew :api-persistence-sqlite:generateDbMigration`.
  Commit both pairs together. Precedent: `1.13` and `1.14__dropsFor_1.13`.
- No auto-fix task: detekt has no formatting rules, ktlint is IDE-only. Fix findings by hand.

## Gotchas

- **Never edit an applied migration**: the checksum changes and Ebean refuses the history. A correction is a new
  migration.
- **Unique constraint on SQLite**: `@Index(definition = "create unique index ...")`, never
  `unique = true` (Ebean emits an unsupported `ALTER TABLE` that becomes a silent no-op comment).
  `DbMigrationModelCoverageTest` fails on the no-op marker.
- **Partial or expression index**: `definition` alone, no `columnNames`, no `unique = true`.
- **A changed detekt rule is not picked up by a live Gradle daemon** (cached classpath: false green). Run
  `./gradlew --stop` before trusting a local gate after a rule change.
- **detekt baselines are per module** (`api/config/detekt/baseline-<module>.xml`): the `detektBaseline`
  task rewrites rather than merges.
- **The daemon's metaspace is bound in `api/gradle.properties`, and the bound is load-bearing.** The
  gate runs the whole build in one JVM; at the default, a run with nothing cached dies with
  `OutOfMemoryError: Metaspace` on whichever task is unlucky, and Gradle then hangs instead of
  reporting. The symptom is a task marked `FAILED` with no message and no build summary after it.
