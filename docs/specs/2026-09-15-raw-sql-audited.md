# The model carries what raw SQL stood in for

Date: 2026-09-15
Status: Approved by the operator on 2026-09-15; one adversarial review closed, its ten findings
recorded in this document. Frozen when block 30 merges. One thing no reading settles, which block 10
runs first: the plan assertion of section A.
Branches: `fix/image-holds-its-pin`, `fix/import-last-activity-not-null`, `chore/raw-sql-lot-wrap`
ADRs: none, and the three decisions are covered unequally.
`docs/specs/2026-09-10-web-application.md` section 4.11 records A's, that raw SQL reaching for a
relation is a symptom of the model and not of Ebean; B is a schema correction the alpha policy
already covers. **C is a new standing prohibition and its record is this document**, by the
operator's decision of 2026-09-15 rather than by omission: it decides that production raw SQL is a
closed set. Its home decides nothing further, `agents/engineering.md` already naming `detekt-rules`
for a prohibition inside a file's statements.
The contract does not change: no property this lot touches appears in `contract/openapi.json`.

## 1. Goal

Close the backlog item "The `raw(` calls in production have never been audited". Its complaint is
not that the calls are wrong, it is that nothing records which are forced and which hide a model
that does not hold what the query asks for. So the audit ends with a record the gate holds, and the
two calls the audit finds standing in for a model are removed by fixing the model.

## 2. What exists today

```
$ cd api && grep -rhoE --include='*.kt' 'raw\("[^"]+"' */src/main | sed -E 's/raw\("//;s/"$//' | sort | uniq -c
      5 id >= ?
      5 id <= ?
      2 name collate nocase = ?
      1 id in (select pin_id from images where content_hash = ?)
      1 id > ?
      1 coalesce(last_upload_activity_at, requested_at) < ?
$ grep -rlE --include='*.kt' 'raw\("' */src/main | wc -l
10
```

Fifteen calls, ten files, six fragments, all in `api-persistence-sqlite`. The backlog's count,
thirteen across nine, was written on 2026-09-11 and is behind.

| Fragment | Sites | Verdict |
|---|---|---|
| `id <= ?`, `id >= ?`, `id > ?` | 11 | **Forced.** The whole hierarchy `PUuid` to `PBaseValueEqual` to `TQPropertyBase` to `TQProperty` carries `eq`, `ne`, `in`, the subqueries, `isNull` and the two orderings, and no ordered comparison: the branch that has one is the one `PInstant` takes through `PBaseDate`. A keyset cursor needs `<=` on its tiebreaker. Ebean's untyped `ExpressionList.le("id", x)` is not the way out: the property is a string there too, and it appends to the query's root clause where eight of these eleven sit inside the `and()` nested in an `or()`, so the predicate would leave the junction and the cursor would be wrong in silence. |
| `name collate nocase = ?` | 2 | **Forced, and kept.** The lookup has to ask what the unique index asks, and that index is `(author_id, name collate nocase)`. `ieq` asks something else: `CaseInsensitiveEqualExpression` emits `lower(col) = ?` and binds `value.toLowerCase()`, so the column is folded by SQLite and the value by Java. SQLite's `lower()` is "limited to English alphabet case mapping" (`ext/icu/README.txt`, sqlite/sqlite), Java's is full Unicode, and the two then disagree in one direction: a row `été` is found by a search for `ÉTÉ`, a row `ÉTÉ` is not found by a search for `été`. `toLowerCase()` also takes no locale, so a Turkish default changes the answer. And `lower(name)` is not the indexed expression, so a find-or-create could miss a row the constraint then refuses. |
| `coalesce(last_upload_activity_at, requested_at) < ?` | 1 | **Symptom.** The `coalesce` is the model saying the column should never have been nullable. |
| `id in (select pin_id from images where content_hash = ?)` | 1 | **Symptom.** `ImageModel` carries `pinId: UUID` and no association, so the ownership traversal has nothing to join through. The shape `docs/specs/2026-09-10-web-application.md` section 4.11 fixed on `ImageDownloadModel`. |

## 3. The change

### A. `ImageModel` holds its pin (block 10)

The association sits beside the identifier, as `ImageDownloadModel` records:

```kotlin
@ManyToOne
@DbForeignKey(noIndex = true)
@JoinColumn(name = "pin_id", insertable = false, updatable = false)
lateinit var pin: PinModel
```

`pinId` stays the mapped column and keeps its `@Column(unique = true)`. `noIndex = true` because
`uq_images_pin_id` already indexes the column.

**A migration is expected here, and it turns out to have nothing to apply**, by the operator's
decision of 2026-09-15 on block 10's question. The generator diffs against the model files and not
against the database: `1.4.model.xml:6` records `images.pin_id` with no `references`, so the
association produced `1.25.model.xml`'s `alterColumn` and the same "not supported" placeholder
`1.22` met. But `images` is not `image_download`. `1.4.sql:13-14` created the table with
`foreign key (pin_id) references pins (id) on delete restrict on update restrict`, where `1.5.sql`
created `image_download` without one, and only the two model files are alike. A fresh install
therefore already ends with the constraint the model now records, which replaying the history into
SQLite shows: `pragma foreign_key_list(images)` gives `0|0|pins|pin_id|id|RESTRICT|RESTRICT|NONE`.

So `1.25.sql` replaces the placeholder with the comment saying that, and applies nothing. The
rebuild this section first asked for would have produced the same table. Block 20 is the one that
rebuilds, and section 6's first pitfall is what block 10 owes it.

The query becomes the one the beans offer, rooted on the image so the content hash stays the
selective predicate:

```kotlin
internal fun pinIdsByContentHashQuery(user: User, contentHash: String) =
    QImageModel()
        .contentHash.equalTo(contentHash)
        .pin.author.id.equalTo(user.id)
        .select("pinId")
```

**The state decision keeps its written record**, which `PinQueries.any()` carries today and which
neither structural rule would miss: the detekt rule keys on a `softDeletedAt` receiver and the
Konsist assertion bars importing a recyclable model's bean, and the new query does neither. A test
is not the record ADR 0008 exists to keep, so `queries/` gains the line `ImageDownloadQueries`
already models:

```kotlin
/** Images whose pin is in any state. The caller states that it means it. */
fun QImageModel.withPinInAnyState(): QImageModel = this
```

It adds no predicate, exactly as `SoftDeletableQueries.any()` adds none and for the same stated
reason. The lookup must find a pin in the recycle bin, so the import does not create a second copy
of it, and the recycled-pin test is what holds that.

### B. The import's last activity stops being nullable (block 20)

| Where | What |
|---|---|
| `UserDataImport` (domain) | `lastUploadActivityAt: Instant?` becomes `lastActivityAt: Instant` |
| `UserDataImportModel` | the same, `var lastActivityAt: Instant` |
| `UserDataImportModelMapper:22,53` | renamed |
| `UserDataImportCreator:29` | `clock.now()` captured once into a local and given to both `requestedAt` and `lastActivityAt`, a second call being a second instant |
| `UserDataImportChunkReceiver:45` | renamed |
| `UserDataImportRepository:83` | `.lastActivityAt.lessThan(instant)`, the `raw(` gone |
| `dbmigration/1.2x.sql` and its model file | the rename and the `not null`, neither of which SQLite does in place: a rebuild on `1.24.sql`'s model |

The column loses its "upload" because the value is the request's instant before any chunk arrives,
and the name would then be a lie. It is written in one place today, read by this one sweep, and
carried by no DTO, so the rename reaches nothing else.

**The fill runs before the copy, not inside it**, which is `1.24.sql`'s shape: `add column
last_activity_at`, one `update` setting it to `coalesce(last_upload_activity_at, requested_at)`,
then the rebuild, whose `select` names `last_activity_at` on both sides and does not carry the old
column. Not a data concern, nothing being deployed, but a shape one: `TableRebuildColumnsTest`
splits the three lists on commas, so a `coalesce(a, b)` left in the `select` reads as two columns
and breaks the equality it asserts.

**The rebuild recreates the table's two indexes**, `uq_user_data_imports_active` and
`ix_user_data_imports_user_state`, both created by `1.21.sql:46,47` and both dropped with the table.
Section 6 says what makes that visible.

### C. The record the gate holds (block 20)

`RawSqlOutsideInventory` in `detekt-rules/`, registered in `PinryRuleSetProvider` and activated in
`config/detekt/detekt.yml`, as `PageSizeForwardedUnclamped` was. It holds the inventory itself, a
map from SQL fragment to the reason the query beans cannot express it, and reports a `raw(` call
whose argument is not in it.

Two shapes are reported, and they are the whole rule:

- a fragment absent from the map, so a new raw call cannot be written without its reason being
  written beside it, in a file the reviewer sees in the diff;
- an argument that is not a string literal, because `raw(SOME_CONSTANT)` would otherwise be a
  production raw call the inventory never sees.

Four fragments remain after A and B, thirteen calls, and the first three share one reason.

It freezes fragments and not call sites, deliberately: a sixth sort strategy reusing `id <= ?` is
the same forced call and passes, while a genuinely new shape is a new fragment by construction.

**A rule rather than a test**, which is where `agents/engineering.md` puts a prohibition inside a
file's statements, and it is the only home that sees the call rather than a line of text. What it
cannot do is report a fragment that has *disappeared*, a rule visiting files and never asserting a
set was consumed. That costs a stale line in the map and no hole in the guard, the record's job
being to stop a raw call arriving without a reason.

It rides in block 20 because the inventory can only be written once both symptoms are gone, and
block 20 is the last block that removes one.

## 4. Blocks

| # | Branch | Delivers | Green alone |
|---|---|---|---|
| 10 | `fix/image-holds-its-pin` | Section A | The four behaviours of `PinRepositoryContentHashTest` unchanged, the recycled pin still found; its plan assertion still naming `ix_images_content_hash` with no `SCAN`; every index the history leaves in place present in `sqlite_master` on the migrated database; `raw(` gone from `PinRepository` |
| 20 | `fix/import-last-activity-not-null` | Sections B and C, the backlog entry deleted, and the handoff, this being the lot's last code block | A row whose upload never started swept on its request instant, one whose upload stalled swept on its last chunk; a created import whose two instants are equal, not merely close; `uq_user_data_imports_active` and `ix_user_data_imports_user_state` present in `sqlite_master` after it runs, and a second active import still refused; the rule red on a `raw(` whose fragment is not in the map, and red on one whose argument is not a literal, both shown by adding each to a repository |
| 30 | `chore/raw-sql-lot-wrap` | The holistic review's findings, the backlog reconciled, the handoff | The gate, and each finding named with its exit |

Two code blocks, 54 production lines measured for block 10 and about 90 expected for block 20, both
far inside the 200, a rebuild's worth of SQL being the bulk of block 20 alone.

## 5. Adjacent backlog items

Both stay open, by the operator's decision on 2026-09-15, and each has an observable that says it
did not move.

- **"A table rebuild's row-carrying path is exercised by nothing"** (`P2`). Both blocks write a
  rebuild and neither copy will be tested: `TableRebuildColumnsTest` still compares the three column
  lists against an empty database. The lot widens the hole and leaves it open, the application being
  deployed nowhere, so no rebuild has a row to lose and the cost of the hole is still zero.
- **"`foreign_keys` is off"** (`P2`). Block 10 records a foreign key that nothing enforces, one more
  than yesterday. Turning the pragma on changes deletion across the schema and is its own lot.
  `datasource.db.url` still declares `journal_mode`, `synchronous` and `busy_timeout` and not
  `foreign_keys`.

## 6. Pitfalls

- **A rebuild drops the table's indexes, and no guard sees it.** `MigrationDirectory.currentIndexes`
  folds `create index` and `drop index` statements only, so a `drop table` is invisible to it and
  `PartialUniqueIndexStatesTest` stays green over a schema that lost the index. Neither `pins` nor
  `image_download` carried one when they were rebuilt, so the history offers no precedent to copy.
  Block 10 therefore writes `MigratedSchemaIndexesTest`, which asserts every index the history
  leaves in place against `sqlite_master` on the migrated database, that being the only place the
  loss is observable. Block 20's rebuild is the first it covers, and recreates the table's indexes.
- **The plan test's binds flip.** `PinRepositoryContentHashTest` sets parameter 1 to the author and
  2 to the content hash. Rooted on the image, the content hash binds first. The test reads
  `generatedSql`, so the order is the query's and not a choice.
- **A rebuild of `pins` and this lot's foreign key.** `docs/specs/2026-09-13-optional-source-page-url.md`
  already records that `1.4.sql` and `1.23.sql` are reread together the day `foreign_keys` goes on.
  Nothing here changes that; `images` referencing `pins` is not new.
- **The migration is generated, not written**:
  `./gradlew :api-persistence-sqlite:generateDbMigration` from `api/`, with
  `JAVA_HOME=~/.sdkman/candidates/java/25-tem`. `DbMigrationModelCoverageTest` refuses a placeholder
  left in place and a `.sql` with no model file.
- **One tier-1 fix waits in block 20's own files.** `PartialUniqueIndexStates.kt:28` cites
  `uq_user_data_imports_active` as `1.21.sql:2`; it is at line 46. Its two sibling pointers are
  right.
- **The rule set reads names, not resolved members**, as every rule in `detekt-rules` does.
  `RawSqlOutsideInventory` keys on a call named `raw`, so a `raw(` on another receiver is reported
  too. A nuisance rather than a miss, and the same trade `PageSizeForwardedUnclamped` took.
- **Registration is not activation.** `PinryRuleSetProviderTest` compares the provider's rules to
  what `detekt.yml` names and sets `active: true`; a custom rule set is excluded from detekt's
  configuration validation, so a forgotten key costs the rule in silence.
- **A changed rule is not picked up by a live Gradle daemon** (`api/AGENTS.md`): `./gradlew --stop`
  before trusting a local run.
