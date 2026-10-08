# The pin credits its people

Date: 2026-10-08
Status: Accepted by the operator on 2026-10-08. One specification review ran,
`.reviews/the-pin-credits-its-people-spec.md`, its 4 MAJOR and 15 MINOR closed in this document. Frozen when the lot's
last block merges.
Lot: `0.52.0`.
Branches: one stack, each block on the one before it: 10 `feat/a-person-is-stored`, 20 `feat/the-pin-holds-its-people`,
30 `feat/the-api-credits-people`, 40 `feat/the-api-searches-people`, 50 `feat/the-catalogue-finds-people`,
60 `feat/the-archive-carries-people`, 70 `feat/the-import-links-collections`, 80 `feat/the-export-carries-collections`,
90 `feat/the-board-shows-its-collections`, 100 `feat/the-form-credits-people`, 110 `feat/the-form-dates-a-pin`.
ADR: `docs/adr/0055-third-party-imports-write-the-user-data-archive.md` (pull request #363), decisions 7, 8 and 11,
is this lot's record; this document settles what it leaves to a specification. The routes follow
`docs/adr/0040-search-is-a-parameter-of-the-catalogue.md` and `docs/adr/0044-a-response-code-declares-its-set.md`;
the archive follows `docs/adr/0015-import-identifies-by-natural-key.md`; the merge extends
`docs/adr/0052-duplicates-are-resolved-in-one-call.md`, decision 3.

## 1. Goal

A pin names who published it, who made it and when it was published, and a board knows which remote collections
feed it. Every entry path can carry these facts: the web application's form, the API, and the user data archive,
which the third-party importer of the next lot will write. Advances the backlog item "Import from third-party sites";
closes none.

## 2. What exists today

Paths below are under `api/` unless they start with `clients/`. `dom`, `uc`, `per` and `pres` stand for the main
sources of `api-domain`, `api-usecases`, `api-persistence-sqlite` and `api-presentation-quarkus`.

- **A tag** is `{id, author, name, createdAt}` (`dom/entities/Tag.kt`), unique per author on
  `name collate nocase` (`ix_tags_author_name_nocase`, `dbmigration/1.20.sql`), which folds ASCII only. It is found
  with `raw("name collate nocase = ?")`, a literal of `RawSqlOutsideInventory.INVENTORY`. `TagCreator.resolve` reads
  then writes in one transaction; `UniqueConstraintOutcomeTest` lists the index as "no translation, deliberately".
  Pins reference tags through `pin_tag_model(pin_id, tag_id)`, unordered, unique on the pair, written by
  `PinRepository.savePinTags`, which reads the existing rows and inserts the missing ones. A tag no pin references
  stays.
- **`PUT /api/v1/pins/{pinId}`** takes `PinUpdateInputDto`, all five fields required, an empty list clearing
  (`PinUpdater.update`, which resolves tag names through `PinTagger`). Its `tags` carry no count bound.
  `POST /api/v1/pins` creates a pin with no tags and no boards. `PinOutputDto` is built by `PinMapper` through
  `PinResponses`; the catalogue's pages load each pin's tags and boards with one query each per pin
  (`PinRepository.findPinsForUser`, `findActivePinsForBoard`).
- **The catalogue's `q`** matches a description or a tag name containing it, through query beans
  (`per/queries/PinQueries.kt`). `GET /api/v1/tags/search` returns the names starting with `q`, then those containing
  it, `limit` 10 by default and at most 20 (`TagSearchController`, `TagRepository.findTagsForUserMatching`).
- **A pin row is deleted** in three places, each deleting its `pin_tag_model` and `pin_board_model` rows first
  (`PinRepository.permanentlyDeletePin`, `permanentlyDeleteAllSoftDeletedPinsForUser`,
  `permanentlyDeleteAllPinsForUser`). **A board row** in three, each deleting its `pin_board_model` rows first
  (`BoardRepository.kt:84-101`). A recycled board keeps its links. `AccountDeletionCleaner` deletes a user's rows in
  one transaction, pins before boards before tags.
- **The duplicate merge** (`DuplicateResolver.afterAbsorbing`) gives the kept pin the union of the absorbed pins'
  tags and boards, and fills a blank description and a null `sourceContextUrl` from the oldest absorbed pin holding
  one.
- **The archive** (`UserDataExportBuilder.writeArchive`) holds `README.md`, `user.json`, `boards.jsonl` (active and
  recycled boards), `tags.jsonl`, `media/`, `pins.jsonl` and `manifest.json`, at `formatVersion` 2. The import
  (`UserDataImportRunner`) walks tags, boards, then pins; it reads an absent entry as empty, ignores an unknown field,
  and reports a missing non-nullable field as `LINE_MALFORMED`. A recycled board is recreated recycled; a pin line's
  `boards` joins a board by name whatever its state (`createPin`). A pin is identified by its medium's SHA-256, and
  an existing one is skipped untouched, before anything else of its line is resolved. Bounds are restated in
  `ImportFieldBounds` (name 200, description 2000, 100 references per list, "resolved inside one transaction"); there
  is no bound on an address. Its `rejecting` catch-all turns an unexpected exception into `LINE_REJECTED`.
  `MeImportRoundTripIntegrationTest` exports an account, a recycled board included, and imports it into another,
  asserting an empty issue list.
- **The contract** is `23.0.0`. The last migration is `1.32`; Ebean generates the next from the models
  (`./gradlew :api-persistence-sqlite:generateDbMigration`, `api/AGENTS.md`).
- **In the web application**, `PinEditForm`'s `TagField` is a text field with debounced suggestions shown as buttons
  and the chosen tags as a `TagGroup`; Enter adds what was typed, trimmed. `PinDialog` shows the description, the
  source page, tags and boards, and no date. The board page shows the board's name and description, the board coming
  from `useBoards()`. The UI library is HeroUI 3; its `Input` renders a native `input` and already serves
  `type="url"` in the form. No date or time field is used yet. Only `src/lib/**` is held to full coverage, and only
  pure functions live there (`clients/AGENTS.md`).
- **Node applies a change of `process.env.TZ` at once**: `new Date(2026, 0, 15)` gives `2026-01-14T23:00:00.000Z`
  after `process.env.TZ = "Europe/Paris"` and `2026-01-15T00:00:00.000Z` after `"UTC"`, in one process
  (`node -e`, Node 22.21.1, 2026-10-08).

## 3. Decisions

Each is the operator's answer in the Discuss of 2026-10-07 and 2026-10-08, the letter of the question in brackets,
unless it says it is the lead's.

**A. A person is an entity per owner, identified by its name and its addresses together** (H, O, U, W).
- `persons(id, author_id, name, urls text not null, created_at)`. `urls` is the addresses sorted and distinct, joined
  by a line feed, empty for none (ADR 0055, decision 8). One function of the domain produces this form, and every
  write path goes through it. *(Corrected: on the operator's review of pull request #364, `urls` is a JSON array held
  as text, sorted and distinct, `[]` for none. `Person` holds a plain list; the canonical form and its serialisation
  are the persistence adapter's, `PersonModelMapper.canonicalUrls`, which every write and the lookup go through.)*
- Unique index `(author_id, name collate nocase, urls)`. A person is found with the existing literal
  `"name collate nocase = ?"` and an equality on `urls`. Two people may share a name when their addresses differ.
- No normalisation beyond the ASCII fold of the name and the canonical order of the addresses: the server trims
  nothing, and `https://x.test/a` and `https://x.test/a/` are two addresses.
- Found or created in one transaction, as a tag is: the index joins `UniqueConstraintOutcomeTest` as "no translation,
  deliberately", for the tag's reason.
- A person no pin references stays, as a tag does (the lead's default, announced and not refused).
- Bounds, the lead's: a name non-blank and at most 200 characters, as a board's; at most 20 addresses, each non-blank,
  at most 2000 characters, holding no line feed. *(Corrected: no rule on line feeds, the JSON form escaping them;
  pull request #364.)*

**B. A pin holds a publisher, creators and a publication instant** (L, D, Y).
- `pins.publisher_id`, nullable; `pin_creator_model(pin_id, person_id)`, unordered, unique on the pair; and
  `pins.published_at`, a nullable instant. `Pin` gains `publisher`, `creators` and `publishedAt`, defaulting to none.
- `pin_creator_model` is written as `pin_tag_model` is, reading the existing rows and inserting the missing ones:
  its index joins `UniqueConstraintOutcomeTest` as "no translation, deliberately", as `ux_pin_tag_model_pin_tag` does.
- Each of the three paths that delete a pin row deletes its `pin_creator_model` rows first. Account deletion deletes
  the user's persons after their pins.
- `POST /api/v1/pins` is unchanged: a pin is created without them, as without tags.

**C. The API names a person by `{name, urls}`**, never by id, as it names a tag by its name.
- `PersonOutputDto {name, urls}`. `PinOutputDto` gains `publisher` (nullable), `creators` and `publishedAt`
  (nullable).
- `PinUpdateInputDto` gains the same three, required as its other fields are: `publisher: PersonInputDto?`,
  `creators: [PersonInputDto]` with at most 100 entries, `publishedAt: instant?`. The server finds or creates each
  person by decision A's identity. A refused bound answers 400 `VALIDATION_ERROR`.
- `GET /api/v1/persons/search?q=&limit=` mirrors `GET /api/v1/tags/search`: the names starting with `q`, then those
  containing it, each with its addresses, so homonyms come back as two results; the same defaults and refusals.
- The catalogue's `q` also matches a pin whose publisher's or creator's name contains it (I).
- Block 30 makes the contract `24.0.0`: the new required input fields break it. Block 40 makes it `24.1.0`.

**D. The archive carries the people** (AA, and ADR 0055, decision 3).
- A pin line gains `publisher`, `creators` and `publishedAt`, written by the export. The import reads an absent one
  as none, finds or creates each person when it creates the pin, stamped with the import instant, and checks decision
  A's bounds and at most 100 creators, a fault refusing the line as `FIELD_INVALID`.
- `publishedAt` is restored as written and not clamped: ADR 0015's clamp keeps the account's own chronology, and a
  work is often older than the account.
- A person travels only inside the pin lines that name it: the archive gains no entry for persons.
- `formatVersion` stays 2: nothing is deployed, so no archive of the earlier shape needs refusing.

**E. A remote collection links to a board** (P, Z, and ADR 0055, decision 11).
- `remote_collections(id, author_id, url, name, board_id, created_at)`, unique on `(author_id, url)`. A row is a
  link: a collection is stored only linked. Several rows may name one board.
- Recycling a board keeps its rows. Each of the three paths deleting a board row deletes its collection rows first.
  Account deletion deletes them before the boards.
- `BoardOutputDto` gains `remoteCollections: [{name, url}]`, sorted by name. Block 90 makes the contract `24.2.0`.
- The board page shows them under its description, read only: each name links to its address, in a new tab.

**F. The archive carries collections** (P).
- A `collections.jsonl` entry, one line per collection: `{url, name, board}`, `board` a board's name or absent. The
  README lists the entry.
- The import walks it after the boards, each line's read and insert in one transaction: the index joins
  `UniqueConstraintOutcomeTest` as "no translation, deliberately". A collection whose `url` the owner already holds is
  skipped, and its link kept. Otherwise it links to the board named `board`, or named as the collection when `board`
  is absent, **whatever the board's state**, as a pin line's `boards` joins a board; none of that name creates it,
  with an empty description, stamped with the import instant. A recycled board is linked rather than refused, so a
  round trip keeps the link a recycled board holds (decision E).
- A pin line gains `collections`, a list of addresses, absent as empty, at most 100. Each address resolves to the
  board its collection links to, which joins the pin's boards, whatever its state; an unknown address is ignored, as
  an unknown board name is. **Its producer is the importer of the next lot**; this lot's tests write it through
  `ImportArchiveBuilder`.
- The export writes every collection with its board's name, and no pin line's `collections`: a pin's boards already
  say where it is.
- Bounds: `url` as an address of decision A, `name` and `board` as a board's name. A fault refuses the line as
  `FIELD_INVALID`.
- Linking to an existing board of the collection's name is ADR 0055's accepted risk, the backlog's plan step being its
  fix.

**G. The form credits people** (X).
- `PinEditForm` gains a publisher field (one person) and a creators field (several), each built as `TagField` is: a
  text field, suggestions from `GET /api/v1/persons/search` after the same pause, the chosen people as removable
  chips. A suggestion shows the name and the host of each address, so homonyms read apart.
- Enter creates a person with the name typed, trimmed, and no address. The form never edits an existing person's
  addresses.
- `PinDialog` shows the publisher and the creators: the name, then each address as a link showing its host, in a new
  tab, as the source page is shown.

**H. The form dates a pin** (Y, and the lead's choice of controls).
- A date field and a time field, HeroUI's `Input` with `type="date"` and `type="time"`: native controls, no new
  dependency. Choosing a day fills the time with 00:00 when it is empty; the time field is disabled while no day is
  chosen. The browser's time zone (`Intl.DateTimeFormat().resolvedOptions().timeZone`) is shown beside them. A clear
  button empties both, and the pin's `publishedAt` becomes null.
- The instant is the day and time read in the browser's zone, through the platform's `Date`, and back. The two
  conversions are pure functions under `src/lib/`; their tests set `process.env.TZ`.
- `PinDialog` shows the instant with `Intl.DateTimeFormat`, `dateStyle: "medium"` and `timeStyle: "short"`, in the
  browser's zone.

**I. The duplicate merge carries them** (the lead's, extending ADR 0052, decision 3). The kept pin gains the union of
the absorbed pins' creators; a null publisher and a null `publishedAt` are filled from the oldest absorbed pin
holding one.

## 4. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/a-person-is-stored` | Decision A, but the API. |
| 20 | `feat/the-pin-holds-its-people` | Decision B. |
| 30 | `feat/the-api-credits-people` | Decision C's pin fields and `PUT`. |
| 40 | `feat/the-api-searches-people` | Decision C's person search. |
| 50 | `feat/the-catalogue-finds-people` | Decision C's `q`, and decision I. |
| 60 | `feat/the-archive-carries-people` | Decision D. |
| 70 | `feat/the-import-links-collections` | Decision E's storage and deletions, and F's import. |
| 80 | `feat/the-export-carries-collections` | Decision F's export. |
| 90 | `feat/the-board-shows-its-collections` | Decision E's API and board page. |
| 100 | `feat/the-form-credits-people` | Decision G. |
| 110 | `feat/the-form-dates-a-pin` | Decision H. |

Each block is measured after its first commit with the command of `agents/workflow.md`; one that passes a bound splits
at a number between its own and the next.

### Block 10

- Migration `1.33`: `persons` with its unique index; `urls` reads `not null` in `1.33.sql`.
- `Person` and its repository port in `api-domain`, the canonical `urls` function, the Ebean adapter, and
  `PersonCreator.findOrCreate`. Their consumers are blocks 20 and 30. *(Corrected: the canonical function lives in
  the Ebean adapter, not in `api-domain`; pull request #364.)*
- Found or created: a person with the same name in another ASCII case and the same addresses in another order is the
  same row; one with an address more is a second row; another author's person of the same name is not found.
- The canonical form: sorted, distinct, line-feed joined, empty for none. *(Corrected: a sorted and distinct JSON
  array, `[]` for none; pull request #364.)*
- `UniqueConstraintOutcomeTest` names the index's outcome.
- Carries this specification.

### Block 20

- Migration `1.34`: `pins.publisher_id`, `pins.published_at`, `pin_creator_model` with its unique index and an index
  on `person_id`.
- `Pin`'s three fields, saved and read by `PinRepository`. Their consumer through the API is block 30.
- A pin saved with a publisher, two creators and a date reads back equal through `findPinById` and `findPinsByIds`;
  saved again with one creator fewer, it leaves no `pin_creator_model` row for that creator.
- Each of the three pin deletion paths leaves no `pin_creator_model` row of the deleted pins; account deletion leaves
  no `persons` row of the user; a person whose only pin is deleted stays.
- `UniqueConstraintOutcomeTest` names the pair index's outcome.

### Block 30

- The DTOs of decision C and `PinUpdater` resolving the people.
- An integration test: a `PUT` naming a new publisher and an existing creator answers both with their addresses and
  creates one `persons` row; a `PUT` with `publisher: null`, `creators: []` and `publishedAt: null` clears them; each
  bound of decision A, and 101 creators, refused with 400 `VALIDATION_ERROR`.
- `oasdiff changelog` against `main` lists the new fields; `info.version` is `24.0.0`.
- The web application's fixture `pin()` answers `publisher: null`, `creators: []`, `publishedAt: null`; the form
  sends the pin's three values unchanged, and the journey "edit a pin's description, tags and boards" asserts them in
  its body.

### Block 40

- `GET /api/v1/persons/search`, its use case and repository query.
- Two homonyms with different addresses come back as two results; a name starting with `q` comes before one only
  containing it; a blank `q` is refused as the tag search refuses it.
- `info.version` is `24.1.0`. Its consumer is block 100.

### Block 50

- The catalogue's `q` and `afterAbsorbing`, of decisions C and I.
- `q` finds a pin by a creator's name and by its publisher's, and not by one of their addresses.
- A merge of three pins: creators are the union; a null publisher and date are filled from the oldest absorbed pin
  holding one, and kept when the kept pin holds them.

### Block 60

- Export and import of decision D. `ImportArchiveBuilder.pinLine` takes the three fields.
- The round trip: `PinFacts` gains the publisher, the creators and the publication instant, and they come back
  equal, the issue list empty.
- A `publishedAt` earlier than the importing account's creation is restored unchanged.
- One archive whose two pin lines, with two different media, name one person in two ASCII cases with its addresses in
  two orders creates one `persons` row; a third line naming it with one address more creates a second.
- A pin line with no `publisher`, `creators` or `publishedAt` imports with none; one with a person whose name is blank,
  or with 101 creators, is `FIELD_INVALID` and creates no pin.
- `ExportContentGoldenJsonTest` shows the three fields, and the manifest's `entries` hold no entry beyond those of
  today.

### Block 70

- Migration `1.35`: `remote_collections` with its unique index and an index on `board_id`.
- Its domain type, port and adapter; the import's collection walk and the pin line's `collections` of decision F;
  the deletions of decision E. `ImportArchiveBuilder` gains `collections(...)` and `pinLine` a `collections` list.
- A collection with no `board`, named as no board of the account, creates the board of its name and links it, and a
  pin line naming its address joins it.
- A collection whose `board` names a board absent from the account creates that board, not one of the collection's
  name.
- Imported again after the board is renamed, a new pin line naming the collection joins the renamed board, and no
  board of the old name is created.
- A collection naming a recycled board links it with no issue; a new pin line naming it joins the recycled board.
- Two collections naming one board both link to it.
- A pin line with 101 addresses in `collections` is `FIELD_INVALID`.
- Each of the three board deletion paths and account deletion leave no `remote_collections` row of the deleted
  boards; recycling and restoring a board keeps its rows.
- `UniqueConstraintOutcomeTest` names the index's outcome.
- (Corrected: measured at 748 lines over 20 files after its first commit, block 70 splits. Block 70 keeps the
  migration, the domain type, port and adapter, the deletions and the index's outcome; block 75,
  `feat/the-import-walks-collections`, takes the import's walk, the pin line's `collections`, `ImportArchiveBuilder`
  and the bullets above about importing, and is the port's consumer.)

### Block 80

- The export of decision F, and the README's line.
- The round trip seeds one collection on an active board and one on the recycled board; both come back linked to
  their boards, the issue list empty.
- `ExportContentGoldenJsonTest` shows a `collections.jsonl` line.

### Block 90

- `BoardOutputDto.remoteCollections`; `info.version` is `24.2.0`.
- The board page's list of decision E; the fixture `board()` answers `remoteCollections: []`.
- Journey "see a board's linked collections": two collections answered in reverse order of name are shown sorted,
  each name a link to its address.
- Read headless before the push, two themes by a phone's and a desktop's width.

### Block 100

- The fields and the dialog of decision G, their messages in English and French.
- Journey "credit a pin's people": a creator chosen from a suggestion keeps its addresses in the `PUT` body, a
  publisher typed and entered is sent with no address, and one person search runs per pause.
- The dialog shows a creator's two addresses as two links showing their hosts.
- Read headless before the push.

### Block 110

- The fields and the dialog of decision H, the conversions under `src/lib/` at full coverage.
- The conversions, `process.env.TZ` set to `Europe/Paris`: a January day at 00:00 gives the previous day at 23:00
  UTC; a July day, 22:00; an instant read back gives the same day and time.
- Journey "date a pin": choosing a day shows 00:00 and the zone, and the `PUT` body carries the instant; the time
  field is disabled before a day is chosen; clearing sends `publishedAt: null`.
- Read headless before the push.

## 5. Adjacent backlog items

- **"Import from third-party sites"** stays open: its importer is the next lot.
- **"People and tags have no management page"** stays open: decision G edits no person, and ADR 0055 puts the page
  in neither lot.
- **"The import flow has no plan step and a poor screen"** stays open: decision F applies ADR 0055's default with no
  choice offered.
- **"Import follow-ons"** stays open: an existing pin is still skipped untouched, so an import does not add a
  publisher to a pin that has none.
- **"A field cannot be applied to every pin of one post"** stays open: nothing here groups pins by post.

## 6. Out of scope

- **Editing a person's addresses, and merging two people.** Observed as no operation on persons in
  `oasdiff changelog` but the search.
- **Sorting the catalogue by publication.** Observed as `PinSortStrategyInputEnum` unchanged in `oasdiff changelog`.
- **Counting collections and persons in an import's summary.** Observed as `UserDataImportOutputDto` unchanged in
  `oasdiff changelog`.
- **A person no pin names, in the archive** (decision D). Observed in block 60's golden test: no new manifest entry.
- **The source's title, text, upstream address and classification** (ADR 0055, decision 10). Observed as no such
  field in `oasdiff changelog`.
