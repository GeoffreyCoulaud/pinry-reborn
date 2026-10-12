# Handoff: the database is PostgreSQL

Date: 2026-10-12
Tier: Spec. Specification `docs/specs/2026-10-12-the-database-is-postgresql.md`, reviewed in
`.reviews/the-database-is-postgresql-spec.md` (3 CRITICAL, 11 MAJOR, 13 MINOR, closed in the specification and ADR
0059); ADR 0059 supersedes ADR 0058, decisions 8 and 9, and amends ADR 0008, 0009, 0013, 0040 and 0056.
Lot `0.56.0`, one stack, GitHub stack #421: 10 `refactor/the-persistence-module-is-named-for-ebean` (#419), 20
`test/the-tests-read-the-schema-through-jdbc` (#420), 30 `refactor/a-person-name-compares-as-written` (#422), 40
`refactor/a-write-declines-what-postgresql-refuses` (#423), 50 `feat/the-database-is-postgresql` (#424), 60
`feat/a-name-ignores-case-in-every-script` (#425), 65 `feat/a-login-name-keeps-to-the-account-alphabet` (#426), 70
`refactor/a-violation-names-its-constraint` (#427), 80 `test/the-schema-holds-on-postgresql` (#428), 90
`chore/the-image-holds-no-database` (#429), 100 `docs/the-sources-name-postgresql` (#430), 105
`docs/the-tests-name-postgresql` (#431), then the closing block in four: 110 `fix/the-holistic-findings-for-postgresql`
(#432), 112 `test/the-index-bounds-read-their-writers` (#433), 113 `fix/quarkus-dev-reads-its-configuration` (#434)
and 115 `chore/the-postgresql-lot-closes`. Written in block 115 from the block reports of #419 to #434, the lead's
notes and the holistic review.

## Current state

- **The API stores its data in PostgreSQL 18.6**, a service of its own in `compose.yml`, pinned by tag and digest,
  initialised with the builtin `C.UTF-8` locale, on its own `database` volume; the API waits on its health check,
  which asks over TCP. SQLite, sqlite-jdbc and `--enable-native-access` have left the repository.
- **The persistence module is `api-persistence-ebean`**, package `...persistence.ebean`.
- **The baseline is one PostgreSQL migration**, regenerated on Ebean's PostgreSQL platform and append-only from this
  lot's merge: `uuid`, `boolean`, `bigint`, `timestamptz`, its four hand-written `CHECK` constraints, and an init
  script that creates the `case_insensitive` collation before it.
- **A name compares without case in every script through its column**: `users.name`, `boards.name`, `tags.name`,
  `persons.name`, `pins.description` and `remote_collections.name` carry the nondeterministic ICU collation
  `und-u-ks-level2`. Every lookup by name is `eq`, both former in-memory sorts are query order, searches keep
  `contains` and `startsWith`. `PersonName` compares as written; `agents/data-modelling.md` reads "the domain never
  folds more than the database does".
- **A login name outside the account alphabet resolves to no account** and answers as an unknown name; the alphabet
  is `User.NAME_PATTERN`, read by the account creation and the authenticator.
- **A translated unique violation is recognised by its constraint's name** (`ConstraintViolations`); any other is
  rethrown. The four former `*_nocase` indexes are `ux_users_name`, `ux_boards_author_name`, `ux_tags_author_name`
  and `ux_persons_author_name_urls`.
- **The task queue's deduplicating insert declines the collision** (`insert ... on conflict do nothing`) through
  `Persistor.executeRaw`, which `RawSqlOutsideInventory` guards; `ClearPinDownload` lets a failed cancellation
  through rather than continue in an aborted transaction.
- **Every instant enters at the millisecond**: imports, and a pin's `publishedAt` on import and on `PUT`.
- **An index entry is bounded at 2704 bytes**: a person's index reads `md5(urls)`; a tag name is bounded at 200 on
  `PUT /api/v1/pins/{id}`; `MigratedSchemaTest` requires a bound for every text index, read from the domain's
  constants (`User`, `Board` and `Tag`'s `MAX_NAME_LENGTH`, `NewTask.MAX_DEDUP_KEY_LENGTH`, which `NewTask`
  enforces).
- **A referenced table changes in place**: `ReferencedTableAlterTest` alters a column of each of the seven referenced
  tables inside one transaction and keeps every child row. The backlog's SQLite item is closed.
- **One connection stays, and one API process is assumed and not enforced** (ADR 0059, decision 2); the pool is a
  P2 backlog item.
- **The suites reach PostgreSQL through ebean-test**: a container it starts on a workstation, one service per suite
  in the gate, on the image and initialisation `compose.yml` names. `DB_PASSWORD` unset stops the boot, naming the
  variable; `DB_URL` and `DB_USERNAME` default to the development database the README names.
- **`./gradlew quarkusDev` boots** against `compose.yml`'s PostgreSQL started alone: the task passes `-Dprops.file`,
  avaje-config sitting in dev mode's base class loader (#434). It did not boot before this lot either.
- The contract is `27.0.0`: the descriptions say "case folded" (`26.1.0`), then the tag name's bound broke it. The
  archive is unchanged.
- **A development database is created afresh**: nothing is carried over from a SQLite file.

## Evidence

| Block | Gate | Budget against its parent |
|---|---|---|
| 10 | `dagger call gate` green at `65f0283e` | 553 lines, 170 files (both bounds waived by the operator) |
| 20 | green at `f29e9d26` | 145 lines, 5 files |
| 30 | green at `db3180c2` | 34 lines, 6 files |
| 40 | green at `de41da51` | 289 lines, 10 files |
| 50 | green at `ab1af65e` | 927 lines, 51 files (both bounds waived, the overrun accepted) |
| 60 | green at `a8e740b8` | 165 lines, 17 files |
| 65 | green at `531924d9` | 95 lines, 16 files |
| 70 | green at `ac2dfc88` | 247 lines, 18 files |
| 80 | green at `63a432bc` | 321 lines, 11 files |
| 90 | green at `05009b05` | 128 lines, 7 files |
| 100 | green at `8a4b634e` | 33 lines, 11 files |
| 105 | green at `e06f9e22` | 25 lines, 13 files |
| 110 | green at `e3f0583a` | 85 lines, 12 files |
| 112 | green at `985b3ffb` | 89 lines, 10 files |
| 113 | green at `179474ac` | 6 lines, 1 file |
| 115 | see the closing pull request's report | see the closing pull request's report |

- `verify` green on continuous integration for #419 to #433 (`gh pr checks`, read in block 115); #434 pending when
  read.
- The gate's duration across the switch, `verify` on continuous integration: 12 min 04 s at #423 (run 38233104392),
  13 min 59 s at #424.
- `dagger call smoke` and `dagger call compose-smoke` green at #424 and #429; the smoke fails with a wrong
  `DB_PASSWORD` (#424) and checks that the image refuses to boot without one (#429).
- Measured on `postgres:18.6`: a tag name of 3000 Cyrillic characters exceeds the 2704-byte entry (#428); the
  health check's socket form answered while the TCP port was still closed on a fresh volume (#432); the collation
  records ICU version 153.128 in `collversion` (block 115).
- The mutations of #420, #427, #428, #432 and #433 fail the structural tests on the defects they name; the collation
  tests use values that fail on SQLite's ASCII fold (`élodie` and `ÉLODIE`, `Émile` between `emile` and `emma`).
- Block 115 started the README's two "Running" commands at #434's tip: `docker compose run ... postgres`, then
  `quarkusDev` (on port 8181, 8080 being taken on the workstation): migrations applied, `/q/health` 200.

## Departures from the specification

All recorded in the specification, and ADR 0059 where they reach it, as `(Corrected: ...)` in block 115.

- **Sixteen code blocks where eleven were planned**: 60 split into 60/65, 100 into 100/105, the closing block into
  110/112/115, each approved by the lead; 113 inserted below 115 on the operator's answer.
- **Block 50 came to 927 lines and 51 files** against an estimate of 800 and 38; the operator accepted it.
- **Block 10's move is a commit of its own**, so that three short files keep their history.
- **`Persistor.executeRaw`** carries decision N's statement, widening ADR 0008's write port.
- **Seven keys declare `@DbForeignKey(noIndex = true)`** and **`EbeanDatabaseProducer` gained a disposer** (#424).
- **The password check is two classes**, the reading in the persistence module, the ordering in `api-application`.
- **`ConstraintViolations` reads the constraint's name alone**, not the SQLState; `ix_user_password_hashes_user_created`
  keeps its `ix_` prefix.
- **The contract is `27.0.0`**, a tag name's bound being added on the operator's answer in block 80.
- **Block 80's baseline was edited by hand** on one index line; `generateDbMigration` then reported no change.
- **A remote collection tie orders by the `uuid`'s bytes**, not by Java's signed comparison.
- **`api/AGENTS.md`'s gotcha "Unique constraint on SQLite" is deleted**: PostgreSQL's dialect enforces `unique = true`.
- **The holistic review's code findings changed decisions P and N's reach**: `publishedAt` truncated, and
  `ClearPinDownload` no longer swallows its failure (block 110).
- **The handoff is written in the closing block** rather than the last code block, on the lead's brief.

## Pitfalls

- **avaje-config reads one `application.properties`**, the first on the classpath, where Quarkus reads production's
  file per property: the application suite's Ebean keys come from its own file alone (#432).
- **`quarkusDev` rewrites `contract/openapi.json`'s `servers`** when the port is not 8080: restore it before a commit
  (#434). `--jvm-args` replaces the task's JVM arguments, `-Dprops.file` included.
- **`compose-smoke` cannot see `compose.yml`'s health check or `depends_on`**: it builds the topology from Dagger
  services, which wait on the TCP port.
- **A regenerated baseline drops the hand-written `CHECK` constraints**: diff against a copy taken beforehand (#427).
- **PostgreSQL compresses an index entry before checking its bound**, so a test reaching the bound needs
  incompressible values; `@Size` counts UTF-16 units, at most 3 UTF-8 bytes each in the BMP (#428).
- **Ebean's `sqlQuery` reads `:name` as a parameter**: cast with `cast(... as text)`, not `::text` (#424).
- **A check that must run before the database is reached is the first startup observer**: a query bean builds Ebean's
  default database the first time one is constructed (#429).
- **Konsist bars a recyclable model's query bean outside `queries` and `pagination`** (#425).
- **`git grep` for "SQLite" misses what never names it**: `rowid`, "writer connection", "errorCode 19" (#430).
- **The evidence guard refuses `git apply` and `sed -i`**: a patch handed between teammates is applied by edits (#431).
- **Serena's Kotlin language server put no file under a content root** from block 70 on, and `safe_delete_symbol`
  emptied a class still referenced (#424); the operator answered "b, fais sans serena pour le moment" (#427).
- **`git mv` of a module carries its ignored `build/`**, with query beans generated under the old package (#419).

## Tier-2 questions

Discuss, 2026-10-11 and 2026-10-12, the letter of the question in brackets: the tier, Spec (A); PostgreSQL replaces
SQLite rather than running beside it (B); one connection kept (C); ebean-test reaches the gate's services (D); the
column's collation (E); native types (F); PostgreSQL as a service of its own (G); the module named for Ebean (H);
similarity search left out (I); `PersonName` compares as written (J). From the specification review: `persons.urls`
stays canonical text (K); the data-modelling rule, worded concisely (L). Both waivers of section 5 (blocks 10 and 50),
and the specification, read and validated by the operator on 2026-10-12.

After Discuss:
- Block 50's overrun, 927 lines and 51 files: accepted ("accepter").
- Block 70, Serena's language server: "b, fais sans serena pour le moment."
- Block 80, bound a tag name at 200 on `PUT /api/v1/pins/{id}` in this lot: "a", every route that writes a tag name
  checked.
- Block 115, `./gradlew quarkusDev` boots no API, before this lot too: "a", fixed in block 113 below 115.

The splits of 60, 100 and the closing block were the lead's to approve; the `DB_URL` and `DB_USERNAME` defaults were
the lead's answer to the holistic review (kept, the README names them).

## The holistic review

`.reviews/the-database-is-postgresql-holistic.md`, over
`git diff lot/0.55.0-the-database-enforces-its-references..origin/docs/the-tests-name-postgresql`: 0 CRITICAL,
3 MAJOR, 9 MINOR. Each finding and its exit, all fixed inside the lot:

1. MAJOR, `compose.yml`'s health check could pass on the image's first-boot socket: fixed in block 110, `pg_isready`
   over TCP.
2. MAJOR, the README's `quarkusDev` no longer started the API: fixed in block 115, the README names the database
   to start, `POSTGRES_PASSWORD`, `DB_PASSWORD`, the `DB_URL` and `DB_USERNAME` defaults and the data directories;
   checking it showed dev mode never booted, fixed in block 113.
3. MAJOR, `publishedAt` kept the microsecond: fixed in block 110, truncated on import and in `PinUpdater`.
4. The specification and ADR 0059 carried no correction: fixed in block 115, every departure listed by the review,
   the CI figures and ADR 0059's consequences included.
5. The test properties' comment claimed per-property reading for Ebean: fixed in block 110, the comment names both
   readers and the smoke's KDoc narrows to Ebean's keys.
6. `DB_URL` and `DB_USERNAME` defaults: fixed in block 110 (marked development defaults) and block 115 (the README).
7. `ClearPinDownload` swallowed its failure inside a transaction: fixed in block 110.
8. Comments deferring to ADR 0059, decision 2, and `CASE_INSENSITIVE_COLUMNS`' comment: fixed in block 110.
9. `MigratedSchemaTest`'s literal bounds: fixed in block 112, the domain's constants, which `NewTask` and the DTOs
   enforce.
10. `POSTGRES_INITDB_ARGS` written twice: fixed in block 110, the pipeline reads it from `compose.yml`.
11. ADR 0059 omitted the ICU dependency of the collated indexes: fixed in block 115, a correction in ADR 0059, a
    gotcha in `api/AGENTS.md` and a comment beside the image in `compose.yml`.
12. `AGENTS.md`'s gate row omitted the PostgreSQL services: fixed in block 115.

## What is not validated

- **A real `docker compose up` on a fresh volume, end to end**: `compose-smoke` rebuilds the services and never reads
  `compose.yml`'s health check; its TCP form was confirmed by hand on a fresh `postgres:18.6` container (#432).
- **No database is carried over from SQLite**: nothing is deployed.
- **arm64**: every block built one architecture.
- **That PostgreSQL's `>>` on `bigint` sign-extends**: the band test's comment names no engine and the query masks
  each band (#430, #431).
- **That `ImportFieldBounds.MAX_NAME_LENGTH` stays at the domain's 200**: it restates the bound by design (#433).
- **The web application declares no tag name bound**: a name over 200 characters gets a `400` (#428).
- **Hot reload in dev mode**: after a restart Ebean's default database, static in the base class loader, survives
  the old application (#434), pre-existing; the operator's answer is pending.
- **No check in the gate keeps `quarkusDev` booting**: `@QuarkusTest` has no class loader split (#434).

## The lot's counts

- Fix-backs: 0. Fixes on top: 0. Cascaded rebases: 0; runs re-triggered by them: 0. The lead's split of 110 reset a
  local, unpushed branch, and 115 was rebased onto 113 before its first push: no run.
- The operator's reading of the bodies: no remark.

## Next step

The operator's review of the stack, #419 to the closing pull request, then `gh stack merge --rebase` and the tag
`lot/0.56.0-the-database-is-postgresql`. Then ADR 0055's third-party importer.
