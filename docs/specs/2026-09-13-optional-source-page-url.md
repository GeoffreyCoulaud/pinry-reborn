# A pin no longer needs a source page URL

Date: 2026-09-13
Status: Approved by the operator on 2026-09-13; one adversarial review closed, its seven findings
recorded in this document. Frozen when block 10 merges.
Branch of block 10: `feat/optional-source-page-url`
ADRs: none, and the two decisions that could need one are already recorded. The contract break is
the alpha policy `README.md` states ("Breaking changes and data loss are expected between
versions"), which `contract-guard` mechanises. Relaxing the archive's bound on `sourceContextUrl`
is `docs/adr/0015-import-identifies-by-natural-key.md`, decision 4: the archive restates the field
bounds the REST input DTOs carry, so the bound follows the DTO. A pin's identity stays the medium's
SHA-256 (decision 2) and no key moves.

`docs/specs/2026-09-10-web-application.md`, block 10, gives the target shape, on the sibling field.
It does not give the scope: there, the domain, the use case and the column had allowed null since
`1.4.sql`, only the input DTO changed, and the widened request property was a generalisation. Here
the domain, the column, the response and the archive all move, and the response is a break.

## 1. Goal

Close the backlog item "A pin cannot be created without a source page URL". A pin whose image is
uploaded from disk names no page it was found on, and today it must invent one: `sourceContextUrl`
is non-null in the domain, `not null` in its column and `@NotBlank` on the creation input.

## 2. What exists today

```
$ grep -n "sourceContextUrl" api/api-domain/src/main/kotlin/fr/geoffreyCoulaud/pinryReborn/api/domain/entities/Pin.kt
9:    val sourceContextUrl: String,
$ grep -n "source_context_url" api/api-persistence-sqlite/src/main/resources/dbmigration/1.4.sql
24:  source_context_url            text not null,
33:insert into pins_tmp_rebuild (id, author_id, source_context_url, source_media_url, description, when_created, when_modified, soft_deleted_at)
34:  select id, author_id, source_context_url, source_media_url, description, when_created, when_modified, soft_deleted_at
$ python3 -c "import json;print(json.load(open('contract/openapi.json'))['components']['schemas']['PinCreationInputDto']['properties']['sourceContextUrl'])"
{'type': 'string', 'pattern': '\\S'}
$ grep -n "info-version" api/api-application/src/main/resources/application.properties
38:quarkus.smallrye-openapi.info-version=5.0.0
```

`sourceMediaUrl` sits next to it as `{'type': ['string', 'null']}` in both schemas, which is the
target shape. It also sits in `PinCreationInputDto.required`, nullable and required at once:
SmallRye marks every property of a Kotlin data class required. `sourceContextUrl` will do the same,
so the key stays present on the wire and carries `null`.

## 3. The change

| Where | What |
|---|---|
| `Pin`, `PinModel`, `PinCreator.createPin` | `sourceContextUrl: String?` |
| `dbmigration/1.23.sql` and `dbmigration/model/1.23.model.xml` | rebuild `pins` with the column nullable, on the model of `1.4.sql`; the generator writes both |
| `PinCreationInputDto` | `@NotBlank` dropped, field nullable |
| `PinController` | `creationDto.sourceContextUrl?.takeIf { it.isNotBlank() }`, as line 80 already does for the sibling |
| `PinOutputDto`, `PinMapper` | nullable |
| `ExportedPin`, `ImportedPin` | nullable; `blankFault` applies only to a value that is present |
| `UserDataImportRunner:525,544` | the issue's `subject` falls back to `pin.description` when the page URL is null (see below) |
| `contract/openapi.json` | regenerated, `info-version` raised |
| `images.ts`, `CreatePin.tsx` | `PinCreation.sourceContextUrl: string \| null`; the key stays in the request body and carries `null` when the input is empty; `required` dropped from the input |

**The import report keeps a subject.** `UserDataImportRunner` passes `pin.sourceContextUrl` as the
issue's `subject`, and the parameter is already `String?`, so a null would compile and reach the
user as an empty subject with nothing naming the pin. `ImportIssueRecorder.record` carries the
archive line separately, but a line number in a file the user never opens is not a name. The
subject becomes `pin.sourceContextUrl ?: pin.description`, truncated as it already is.

**The contract's version.** The response property widening to `["string", "null"]` is a break, so
the expected version is `6.0.0`. `contract-guard` derives the required bump and names it; the block
takes what the guard asks rather than what this line predicts.

**The form.** The source page input becomes optional in every case, not only when a file is chosen
(question A, answer A1). A direct link to an image on a content delivery network has no page behind
it either, and the asymmetry would make the user paste the image address twice.

**The rebuild is checked by a test, not by an assertion in prose** (question C, answer C1). A
column dropped from the rebuild's `create table` reddens the whole suite, since every pin insertion
names it. A column present there and missing from the `insert into (...)` or the `select` reddens
nothing: the test database is created empty, so the copy always runs over zero rows, and the loss
only happens in production. The block adds a test in the family of `DbMigrationModelCoverageTest`,
over `MigrationDirectory.sqlScripts`: for every `*_tmp_rebuild` table in the directory, the three
column lists are identical. It covers `1.4.sql` at no extra cost.

## 4. Blocks

| Block | Branch | Content | Journeys |
|---|---|---|---|
| 10 | `feat/optional-source-page-url` | Section 3 in full | A creation whose body carries `"sourceContextUrl": null` answers `201` with the field null; the same pin round-trips through export and import unchanged; an archive whose pin has a null page URL and no medium reports its issue with the description as subject; the three column lists of every `*_tmp_rebuild` agree; the form submits with the source page left empty |

One block. The production count is about 40 lines under `api/` and 5 under `clients/`, both far
inside their bounds.

## 5. Adjacent backlog items

Two items touch the files this block edits, and both stay open (question B, answer B1). Each has an
observable that says it did not move:

- **The settled download refetching the whole catalogue** (`P1`), in `images.ts`. Adjacency of file
  is not adjacency of subject: the fix is a query invalidation concern with its own journey.
  `images.ts` still invalidates `["pins"]` whole, in both places.
- **`foreign_keys` is off** (`P2`), which the migration depends on. Turning the pragma on is a
  product decision about the hard delete, which this block does not take. `datasource.db.url` still
  declares `journal_mode`, `synchronous` and `busy_timeout` and not `foreign_keys`.

## 6. Pitfalls

- **The rebuild of `pins` is correct only while `foreign_keys` is off.** `image_download` has
  referenced `pins` since `1.22.sql`. With the pragma on, `drop table pins` would be refused, and
  `ALTER TABLE ... RENAME TO` rewrites the references other tables hold only when it is on (SQLite,
  ALTER TABLE RENAME). The day the `P2` item is treated, `1.4.sql` and `1.23.sql` are reread
  together.
- **The migration is generated, not written**:
  `./gradlew :api-persistence-sqlite:generateDbMigration` from `api/`. SQLite cannot lift a
  `not null` in place, so the generator leaves a "not supported" placeholder and the rebuild is
  written by hand, as `1.4.sql` records. `DbMigrationModelCoverageTest` refuses both a placeholder
  left in place and a `.sql` with no model file.
- **Existing rows are not touched.** Nothing backfills and nothing blanks: the column merely stops
  refusing null. What checks this is the test above, not this sentence.
