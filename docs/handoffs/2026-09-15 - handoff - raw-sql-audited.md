# Handoff: the model carries what raw SQL stood in for

Date: 2026-09-15
Spec: `docs/specs/2026-09-15-raw-sql-audited.md`
Blocks: 10 `fix/image-holds-its-pin` (PR #141, merged), 20 `fix/import-last-activity-not-null`,
30 `chore/raw-sql-lot-wrap` (the closing block, not yet run)
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
  `git diff <previous lot tag>..origin/main`.

## Next step

Block 30, `chore/raw-sql-lot-wrap`: the holistic review's findings, the backlog reconciled, this
handoff corrected, then the lot tag.
