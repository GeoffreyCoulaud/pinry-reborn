# Handoff: the model carries what raw SQL stood in for

Date: 2026-09-15
Spec: `docs/specs/2026-09-15-raw-sql-audited.md`
Blocks: 10 `fix/image-holds-its-pin` (PR #141, merged), 20 `fix/import-last-activity-not-null`
(PR #142, merged), 30 `chore/raw-sql-lot-wrap` (the closing block, this pull request)
Tier: Spec. The holistic review runs at the head of Wrap: two code blocks, so no waiver is offered.

## Current state

The backlog item "The `raw(` calls in production have never been audited" is closed. Thirteen `raw(`
calls remain across eight files, over four fragments, and each fragment is now in
`RawSqlOutsideInventory` against the reason the query beans cannot express it. The two the audit
found standing in for a model that did not hold what the query asked for are gone, each by fixing
the model rather than the query.

## What was built

- **`ImageModel` holds its pin** (block 10). The `@ManyToOne` `ImageDownloadModel` already carried,
  so the import's content-hash lookup is a join and not
  `raw("id in (select pin_id from images where content_hash = ?)")`. `ix_images_content_hash` stays
  the selective predicate, and `withPinInAnyState()` is the written record ADR 0008 asks for.
- **`MigratedSchemaIndexesTest`** (block 10). Every index the history leaves in place, asserted
  against `sqlite_master` on the migrated database. `MigrationDirectory.currentIndexes` folds
  `create index` and `drop index` and cannot see a `drop table`, so a rebuild that loses an index was
  invisible to every guard before it.
- **The import's last activity stopped being nullable** (block 20). `lastUploadActivityAt: Instant?`
  is `lastActivityAt: Instant`, on the entity, the model and the column: the value is the request's
  instant before a chunk arrives, so the "upload" in the name was a lie and the nullability was the
  model saying the column should never have been one. The sweep asks
  `.lastActivityAt.lessThan(instant)` where it used to ask
  `raw("coalesce(last_upload_activity_at, requested_at) < ?")`.
- **`1.26.sql`**, a rebuild. The fill runs before the copy, not inside it, so the `select` names
  `last_activity_at` on both sides and carries no expression: `TableRebuildColumnsTest` splits the
  three column lists on commas and would read a `coalesce(a, b)` as two columns. The rebuild
  recreates the table's two indexes, which `drop table` took with it.
- **`RawSqlOutsideInventory`** (block 20), a detekt rule registered in `PinryRuleSetProvider` and
  activated in `config/detekt/detekt.yml`. It holds the inventory, a map from fragment to reason, and
  reports a `raw(` call whose argument is not in it, or is not a string literal at all.

## Pitfalls

- **The generator refuses this migration outright.** A non-null column with no default fails Ebean's
  strict mode with `IllegalArgumentException`, after `model/1.26.model.xml` is written and before any
  `.sql` is. So the model file is generated and `1.26.sql` is written by hand, which is what the
  hand-edited bodies of `1.22`, `1.23`, `1.24` and `1.25` already are.
- **The drop moved out of `pendingDrops` by hand.** The generator records the old column's removal as
  a pending drop, expecting a `<version>__dropsFor_<version>` pair; the rebuild applies it in `1.26`
  instead, so the `<dropColumn>` sits in the `apply` changeSet. Left as a pending drop it blocks the
  next `generateDbMigration` outright ("Pending un-applied drops in versions [1.26]"). The check that
  the model file is honest is a second run: `no changes detected - no migration written`.
- **A rebuild's indexes are the thing to remember.** Removing the two `create index` lines from
  `1.26.sql` fails `MigratedSchemaIndexesTest` with both names, and fails
  `UserDataImportRepositoryTest`'s second-active-import case, the partial unique index being the only
  authority on it.
- **The rule reads names, not resolved members**, like the rest of the rule set: a `raw(` on another
  receiver is reported too. It also cannot report a fragment that has *disappeared*, a rule visiting
  files and never asserting a set was consumed, which costs a stale line in the map and no hole.
- **A changed detekt rule is not picked up by a live Gradle daemon** (`api/AGENTS.md`):
  `./gradlew --stop` before trusting a local run.

## What is not validated

- **A table rebuild's row-carrying path is still exercised by nothing.** `1.26.sql`'s `update` and its
  copy run over an empty database in the suite. The backlog entry stays open, as the spec's section 5
  states; the lot widens the hole and the application is deployed nowhere, so no rebuild has a row to
  lose.
- **`foreign_keys` is still off**, so the key block 10 recorded on `images` enforces nothing. Its own
  backlog entry stays open.
- **The holistic review has not run**; it is the head of Wrap, over
  `git diff <previous lot tag>..origin/main`. (Corrected: it ran on 2026-09-15, and the section below
  carries its findings and their exits.)

## The holistic review

`.reviews/raw-sql-audited-holistic.md`, over `git diff lot/0.22.0-page-size-clamped..origin/main`:
0 CRITICAL, 1 MAJOR, 10 MINOR, and no correctness defect. Block 30 is the only destination, so each
finding is below with the exit it took.

| Finding | Exit |
|---|---|
| **MAJOR.** No value of the inventory map is ever read, so an entry carrying no reason passes the rule, its suite and the gate. The map is a set with decoration. | **Fixed.** `RawSqlOutsideInventoryTest` asserts a length floor on every reason; the mutation `"id > ?" to "forced"` fails it with `expected: <{}> but was: <{id > ?=forced}>`. |
| A `raw(` the rule cannot read is silently accepted, which inverts its posture everywhere else. | **Fixed.** The early return on a missing argument is gone, so a no-argument call falls into the "not a string literal" message with the constant and the interpolation. One branch and one test case fewer. |
| The closed-set claim rests on two guards that do not name each other. | **Fixed.** The rule's KDoc names `ArchitectureKonsistTest`'s confinement of `io.ebean.Database` as the other half, and that assertion's comment names the rule. No code change. |
| `lastActivityAt`'s comment explains the lower bound of the new name and not its upper one. | **Fixed.** The comment says the value stops being written once the upload phase ends, so a running import's value is not a liveness signal. |
| The standing prohibition gained no line in `agents/engineering.md`. | **Refused**, on two counts. The operator decided on 2026-09-15 that `docs/specs/2026-09-15-raw-sql-audited.md` is the prohibition's record, which the review itself reads as a confirmation rather than a defect. And the bullet is not the cost: `agents/writing.md` makes `agents/engineering.md` conform to the mandate-before-argument style the moment something touches it, which is a lot of its own and not a line in a closing block. |
| `ImageQueries.withPinInAnyState()` is the first no-op extension in `queries/`, and its omission is undetectable. | **Accepted limit**, already recorded where the decision lives: `SoftDeletableQueries`' KDoc, and ADR 0008 through the backlog's Known limits band. `SoftDeletableQueries.any()` has the same property by design. Widening the Konsist assertion to navigation is its own lot. |
| `UserDataImportRepositoryTest`'s "never received a chunk" case no longer tests what its name says. | **Fixed.** Renamed to say the row's own activity instant; the claim about the request instant is the creator case's. |
| `MeDeleteCompletionIntegrationTest` writes the two-clock-read shape the lot removed. | **Fixed.** Hoisted to one local, as `UserDataImportCreator` does. |
| The rule's KDoc is a third copy of the spec's argument, in the one module the comment rule cannot reach. | **Fixed.** Cut to what the rule reports, that the reasons in the map are the record, and where the argument lives. |
| `MigratedSchemaIndexesTest` asserts one direction. | **Fixed.** It now asserts both, filtering the `sqlite_autoindex_` names SQLite gives a table constraint's index and no statement declares. The mutation that drops `ix_images_content_hash` from the declared set fails it. |
| PR #141's body reports two counted figures that do not reproduce. | **Corrected here**, the merged body being what it is. `git diff --numstat 0ee99c15..8e6610b2` less `docs/specs` gives 87 counted, and the same restricted to `api/*/src/main/*` gives 51 production, against the 92 and 54 the body claims. Block 20 reproduces: 353 counted and 178 production by the same commands over `8e6610b2..078f9d1c`, less `docs/handoffs`. |

**The merged state is validated, after the fact rather than before it.** PR #142's own run,
`34937426849`, was still in flight when the merge landed, about twelve minutes of exposure. The push
to `main` at `078f9d1c` then ran `validate / verify` in full, run `34937994632`, 06:40:30 to
06:52:09, success, on the rebased tip the lot now is. PR #141 is clean on both its runs.

## Next step

Block 30, `chore/raw-sql-lot-wrap`, is this pull request. After it merges, the lot tag
`lot/0.23.0-raw-sql-audited`, annotated on the closing merge and pushed.
