# 0059. The database is PostgreSQL

Status: Accepted
Date: 2026-10-12
Supersedes: `docs/adr/0058-the-database-enforces-its-references.md`, decisions 8 and 9 and the Consequences' "Until
PostgreSQL".
Amends: `docs/adr/0009-unique-index-named-outcomes.md` (the violation's shape),
`docs/adr/0013-in-memory-authentication-attempt-limiting.md` (decision 1's "the SQLite store enforces", and the limit
"grouping is wider than the database's"), `docs/adr/0040-search-is-a-parameter-of-the-catalogue.md` (the ASCII fold),
`docs/adr/0056-data-shapes-are-decided-in-the-specification.md` (the equality rule). (Corrected: and
`docs/adr/0008-structural-soft-delete-read-isolation.md`, decision 2, `Persistor` gaining `executeRaw`.)
Specification: `docs/specs/2026-10-12-the-database-is-postgresql.md`.

## Context

With its keys on, SQLite changes a referenced table only by rebuilding it, which Ebean's migration transaction turns
into a cascade (ADR 0058, decision 9). The operator refused a path around it and chose to move the engine, before ADR
0055's importer adds schema to migrate. Discuss on 2026-10-11 and 2026-10-12 settled the questions below, the letter
of each in brackets; the others are the lead's, from the specification and its review
(`.reviews/the-database-is-postgresql-spec.md`).

## Decision

1. **PostgreSQL replaces SQLite** (B). One engine, one baseline, one suite. Nothing is deployed, so no database is
   carried over: the baseline is generated again for PostgreSQL and is append-only from this lot's merge.
2. **Still one connection** (C). Every guarantee that rests on it (ADR 0009, 0016, 0017, the task queue's claim)
   holds while one API process runs. SQLite's file enforced that; a network service does not, so one process is now
   an assumption `compose.yml` keeps and nothing enforces. Opening a pool is its own lot, which reviews each race.
3. **The tests reach PostgreSQL through ebean-test** (D): a container ebean-test starts on a workstation, and in the
   gate one PostgreSQL service per suite, which Dagger binds to the build container and ebean-test reaches with
   `useDocker: false`. The gate needs no Docker of its own.
4. **A name compares without case through its column's collation** (E): a nondeterministic ICU collation,
   `und-u-ks-level2`, on the columns a user names or searches by text. Equality, uniqueness, `LIKE` and order follow
   it, so no query spells the fold. It folds case and width in every script, where `collate nocase` folded ASCII
   alone. PostgreSQL 18.6 is the floor: `LIKE` under such a collation is 18's, 18.4 fixed a planner that assumed
   uniqueness across collations, and 18.6 fixed `LIKE`'s escapes under one.
5. **Columns take PostgreSQL's native types** (F, K): `uuid`, `boolean`, `bigint`, `timestamptz`. A JSON list stays
   canonical text: `jsonb` would need a raw cast in the one lookup that compares it, and a task's payload is not JSON.
6. **PostgreSQL runs as a service of its own** (G), beside the API in `compose.yml`, reached through environment
   variables. The API's image holds no database. The database is initialised with the `builtin` provider's
   `C.UTF-8`, so no index depends on the C library's collation tables. (Corrected: the indexes over the
   `case_insensitive` columns depend on ICU's, which the image ships; see the Consequences.)
7. **The persistence module is named after Ebean** (H): `api-persistence-ebean`, package `persistence.ebean`.
8. **The domain never folds more than the database does** (J, L). `PersonName` compares its text as written, the
   collation being one the domain cannot reproduce; two references to one person meet once a find-or-create has
   resolved them, by identifier. ADR 0056's rule "the domain's equality matches the database's identity" becomes
   this one.
9. **A violation the code translates is recognised by its constraint's name**, which PostgreSQL reports, rather than
   by "the table's only unique index".
10. **A violation never leaves a transaction to recover in.** PostgreSQL aborts a transaction on any error, so the
    task queue's deduplicating insert says `on conflict do nothing` rather than catching the error. (Corrected: and a
    use case that caught a failure and went on, `ClearPinDownload` inside the account deletion's transaction, lets it
    through; holistic review, block 110.)
11. **A login name outside the account alphabet resolves to no account.** Account names are `[A-Za-z0-9._-]`, where
    the collation and the attempt key's `lowercase` agree; a name outside it would reach an account through the
    collation's width fold under a key of its own. It answers as an unknown name does, so the key stays a function
    of the string and existence leaks nowhere.
12. **The collation is created by Ebean's init script** (`create collation if not exists`), which runs before the
    baseline on every database, the test services' included.

## Rejected

- **Both engines** (B, option b): two baselines, two suites, every engine-specific query written twice, and the
  limit that motivates the move kept on one side.
- **A pool in this lot** (C, option b): the engine and the concurrency model changing at once.
- **Testcontainers with the host's Docker socket in the gate** (D): the gate would no longer be isolated from the
  machine. **Embedded PostgreSQL binaries**: a dependency that decides the version.
- **`citext`** (E): an extension whose own documentation now points to nondeterministic collations. **`lower()` in
  each query**: a query that forgets it becomes case-sensitive silently.
- **`jsonb` for a JSON list** (K, option b): a raw cast in the lookup, for a validation the mapper already makes.
- **PostgreSQL in the API's container** (G): two processes to supervise in one image.
- **ICU4J in the domain** (J): 14 MB to copy a rule the database already applies. **The JDK's `Collator`**: older
  tables, disagreeing on rare characters.
- **A savepoint around the colliding insert** (10): machinery to recover from an error the statement can decline to
  raise. **Ebean's `InsertOptions`**: it always names a conflict target, which a partial unique index refuses.
- **Keying the login attempt by the account when the name resolves** (11): a counter shared only when an account
  exists answers 429 where an unknown name answers 401, the oracle ADR 0013, decision 2, denies.
- **Holding an advisory lock to enforce one process** (2): the pool may recycle the connection that holds it.

## Consequences

- A referenced table changes in place inside Ebean's migration transaction; a test alters one and counts its
  children. ADR 0058's `ReferencedTableDropTest` goes.
- A unique index entry is bounded at 2704 bytes; a person's addresses are indexed by their digest.
- `LIKE` under the collation uses no b-tree index. The searches it serves already scanned (ADR 0040).
- Matching widens: `É` finds `é`, and `Élodie` is the name `élodie` already holds. The contract's descriptions say so.
- A workstation runs the suite with Docker. Without it, ebean-test's `useDocker: false` reaches any PostgreSQL the
  environment names.
- The backlog's search tolerant of typos no longer waits on an engine, only on its own lot.

(Corrected: three consequences the lot met, from the holistic review, `.reviews/the-database-is-postgresql-holistic.md`:
- `Persistor` gains `executeRaw` for decision 10's statement, widening ADR 0008's write port, and
  `RawSqlOutsideInventory` guards it as it guards `raw` (block 40).
- A tag name is bounded at 200 characters on every route that writes one, for the index entry's bound:
  `PUT /api/v1/pins/{id}` gained it, a break that made the contract `27.0.0` (block 80, the operator's answer).
- The collated indexes are ordered by the ICU of the image's digest, and PostgreSQL records that version per
  collation: an image whose ICU differs needs those indexes rebuilt and the collation's version refreshed
  (`api/AGENTS.md`, Gotchas).)
