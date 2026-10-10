# Addresses and people take their shape

Date: 2026-10-10
Status: Accepted by the operator on 2026-10-10. One specification review ran, `.reviews/addresses-and-people-take-their-shape-spec.md`, its 1
CRITICAL, 5 MAJOR and 15 MINOR closed in this document and ADR 0057. Frozen when the lot's last block merges.
Lot: `0.54.0`.
Branches: one stack, each block on the one before it: 10 `feat/an-address-is-typed`, 20
`feat/a-pin-source-is-an-address`, 30 `feat/a-download-fetches-an-address`, 40 `feat/a-collection-is-an-address`,
50 `feat/a-person-is-its-name-and-addresses`, 60 `feat/the-archive-lists-people`, 63
`feat/an-import-reports-an-unknown-person`, 66 `feat/a-pin-names-its-people-by-id`, 70 `feat/the-archive-names-by-key`, 80 `chore/the-shapes-lot-closes`,
85 `chore/the-shapes-lot-documents`.
ADR: `docs/adr/0056-data-shapes-are-decided-in-the-specification.md`, decision 7.1, records decisions B and E below;
`docs/adr/0057-the-archive-references-by-key-and-an-address-is-refused-alike.md` records C, F and G, and supersedes
ADR 0056's decision 13 and 7.1's `{name, urls}` reference.

## 1. Goal

Every web address the API holds is one type, which refuses what is not an absolute http(s) address and folds what
RFC 3986 calls equivalent. A person is its name, compared as the database compares it, and a set of such addresses.
The user data archive lists every person, names everything else by its natural key, and calls the recycling instant
as the API does. These are units 0 to 7 of `docs/adr/0056-data-shapes-are-decided-in-the-specification/inventory.md`.
They are not every change the archive will see: the types lot still renames `sha256` and `mediaType` (unit 20) and
settles the medium's shape (A3), and the third-party importer will follow those renames (decision A).

## 2. What exists today

Paths are under `api/` unless they start with `clients/`. `dom`, `uc`, `per` and `pres` stand for the main sources of
`api-domain`, `api-usecases`, `api-persistence-sqlite` and `api-presentation-quarkus`. Read at `6b996534`; no source
under `api/` changed since the inventory's `1fc81bdc` (`git log --oneline 1fc81bdc..6b996534 -- 'api/*.kt'` lists
nothing).

- **Addresses are `String`** in `Pin.sourceContextUrl` and `sourceMediaUrl` (`dom/entities/Pin.kt:9-10`),
  `Person.urls: List<String>` (`Person.kt:11`), `RemoteCollection.url` (`RemoteCollection.kt:10`),
  `MediaDownload.sourceUrl` (`MediaDownload.kt:10`), `MediaFetcher.openStream`, `PageMediaExtractor.extract`, and the
  ports `PersonRepositoryInterface.findUserPerson`, `RemoteCollectionRepositoryInterface.findUserRemoteCollectionByUrl`
  and `MediaDownloadRepositoryInterface.upsertPending`. Every column holding one is `text`.
- **Four places check an address, each differently.** `RequestPinMediaDownload.kt:44-52` parses with `URI` and refuses
  a scheme other than http(s), throwing `MediaSourceUrlInvalidError` (400 `MEDIA_SOURCE_URL_INVALID`), with no host
  check. `HttpMediaFetcher.httpUri` (`:106-117`) also refuses a missing host, on the request and every redirect.
  `ImportFieldBounds.addressFault` checks blank and 2000 characters, no syntax. `PinController.blankAsNone` turns a
  blank source address into none; `POST` and `PUT /api/v1/pins` otherwise accept any string of any length. The import
  checks a blank `sourceContextUrl` and never `sourceMediaUrl` (`UserDataImportRunner.kt:592`).
- **`PersonInputDto`** bounds a name (`@NotBlank @Size(max = 200)`) and its addresses (at most 20, each `@NotBlank
  @Size(max = 2000)`); `ImportFieldBounds` restates both bounds. A refused bound answers 400 `VALIDATION_ERROR`.
- **A person's identity** is the unique index `(author_id, name collate nocase, urls)`; `urls` is a JSON array, sorted
  and distinct, which `PersonModelMapper.canonicalUrls` writes and `findUserPerson` compares. `collate nocase` folds
  ASCII alone. No Kotlin code folds a name: tags, boards and persons compare names in SQL only
  (`TagRepository.kt:27`, `BoardRepository.kt:53`, `PersonRepository.kt:30`). `DuplicateResolver.kt:70` removes
  repeated creators by `Person`'s data class equality.
- **The web application** keys a person by `JSON.stringify([name, urls])` (`clients/apps/webapp/src/lib/persons.ts:22-24`),
  which relies on the server answering the addresses sorted. It sends `null` for an empty source address
  (`CreatePinDialog.tsx:131`, `PinEditForm.tsx:257`). `importIssues.ts` maps each import issue kind to a sentence, and
  a kind the contract adds fails `tsc` there until it has one.
- **The archive** is `formatVersion` 2 (`UserDataExportRequester.kt:102`). The export writes `README.md`, `user.json`,
  `boards.jsonl`, `collections.jsonl`, `tags.jsonl`, `media/`, `pins.jsonl`, then `manifest.json`. A pin line names its
  tags and boards as `{id, name}` and each person in full as `{name, urls}`; a collection line names its board as a
  bare string; every record but a collection line carries its `id`, and so does the manifest's `user`. A pin line's
  `deletedAt` is what the API calls `softDeletedAt`. The import discards every id
  (`ImportedRef(name)`), walks tags, boards, collections, then pins, and finds or creates a pin line's persons stamped
  with the import instant. A person no pin names is lost. No port lists a user's persons.
- **A reported issue skips its pin**, but one: `MEDIA_DIGEST_MISMATCH` is reported and the pin created
  (`UserDataImportRunner.kt:646-656`). `MEDIA_ENTRY_MISSING` skips it (`:611`).
- **`ArchitectureKonsistTest`** allows `api-domain` to import its own package and `Instant`, `Duration`, `UUID` and
  `InputStream` (`:143-158`). No `@JvmInline value class` exists in `api/`
  (`command grep -rn "@JvmInline\|value class" --include='*.kt' api` prints nothing).
- **The contract** is `24.2.0`. The last migration is `1.35`.

## 3. Decisions

The operator's answers in the Discuss of 2026-10-09 and 2026-10-10, the letter of the question in brackets, unless a
decision says it is the lead's.

**A. The lot is units 0 to 7 of the inventory** (A), `PersonName` included in unit 5 and the archive's `deletedAt`
in unit 7 (G). Unit 20's archive renames and A3's medium stay in the types lot (G).

**B. `HttpUrl` is every address of the domain** (ADR 0056, decision 7.1).
- A `@JvmInline value class` over `java.net.URI`, in `api-domain`, its constructor private. Its factory
  `HttpUrl.parse(text): HttpUrl?` returns null for a text that is blank, unparsable by `URI` once encoded as below,
  relative, of a scheme other than `http` or `https`, without a host, or whose normalised text is longer than
  `HttpUrl.MAX_LENGTH` (2000).
- Before parsing, the factory percent-encodes in UTF-8 what a browser encodes and `URI` refuses: a non-ASCII
  character, a space, `"`, `<`, `>`, `\`, `^`, `` ` ``, `{`, `|`, `}`, and a `%` not followed by two hexadecimal
  digits. A pasted address and its encoded form are then one.
- The factory normalises to RFC 3986's equivalences (sections 6.2.2 and 6.2.3): lower-case scheme and host,
  percent-encoding in upper case, percent-encoded unreserved characters decoded, dot segments removed by section
  5.2.4's algorithm (not `URI.normalize`, which keeps a leading `..`), the default port dropped, an empty path written
  `/`. The trailing slash is kept.
- `ArchitectureKonsistTest` allows `java.net.URI` and `java.net.URISyntaxException` in `api-domain`.

**C. Only `null` is no address** (C).
- A blank or invalid address is refused on every entry path through the one factory: 400 `VALIDATION_ERROR` on the
  API, `FIELD_INVALID` on the import, the line refused. `blankAsNone` goes.
- The lead's default, announced and not refused: the media download route answers `VALIDATION_ERROR` too, and
  `MEDIA_SOURCE_URL_INVALID` leaves the contract. An address without a host is now refused by the request, not by the
  worker.
- The input DTOs keep `String`, each address annotated `@HttpAddress`, a constraint of `api-presentation-quarkus`
  whose validator calls the factory: its refusal is a `ConstraintViolationException`, which
  `ConstraintViolationExceptionMapper` already answers 400 `VALIDATION_ERROR`, naming the property. The presentation
  mapper then calls the factory and reads a null as a defect. On the import, the runner's reading of an archive line
  is that line's mapper. Each address property of the contract declares `format: uri`.
- The fetcher keeps its own check of the redirects it follows (`HttpMediaFetcher.httpUri`): a redirect target is not
  an address the API holds, and the factory's bound would refuse a long signed address the fetcher accepts today.

**D. `PersonName` is compared as the index compares it** (B).
- A plain `class`, not a value class: its equality is the ASCII case fold of its text, as `collate nocase` folds, and
  an inline value class cannot declare `equals`. It keeps the text as written, for display. `api/AGENTS.md`'s value
  type rule gains this exception.
- Its constructor is private. Its factory `PersonName.parse(text): PersonName?` refuses a blank text and one longer
  than `PersonName.MAX_LENGTH` (200). `PersonInputDto`'s `@Size` reads the bound from it, and the import calls the
  factory; `ImportFieldBounds.nameFault` keeps serving tags and boards, whose name types are the types lot's.
- A tag keeps comparing in SQL alone: its `TagName` is the types lot's, on this model.

**E. A person is its name and a set of addresses** (inventory unit 5).
- `Person.name: PersonName`, `Person.urls: Set<HttpUrl>`; `PersonReference` the same. At most `Person.MAX_URLS`
  (20) addresses, as today: `PersonInputDto`'s `@Size` and `ImportFieldBounds.personFault`, which keeps this count
  alone, both read it.
- `PersonModelMapper` only encodes: the JSON array of the addresses' text, sorted, as the index compares it. The
  mapper turns a stored address the factory refuses into an exception.
- On the wire, `PersonOutputDto.urls` declares `uniqueItems` and is answered sorted by text, so the web application's
  `personKey` holds. An input naming one address twice names it once.

**F. The archive lists every person, and a pin line names them by an archive identifier** (D bis, E, F).
- A `persons.jsonl` entry, written after `tags.jsonl` and before the media: one line per person of the account,
  `{id, name, urls, createdAt}`. The export writes the person's database identifier as `id`; the import accepts any
  non-blank text of at most 200 characters unique within the entry, so a third-party tool may write its own.
- The import holds a map from each accepted `id` to the person's row identifier, in memory, rebuilt on every attempt
  as the metadata walks rerun. It reads at most 100 000 person lines: a line past that bound is `FIELD_INVALID`, so
  the map holds at most 100 000 entries of at most 200 characters each.
- A pin line's `publisher` is `{id}` or null, its `creators` a list of `{id}`. The identifier only links the lines of
  one archive: the import finds or creates the person by its name and addresses, then forgets it
  (`docs/adr/0015-import-identifies-by-natural-key.md`).
- The import walks `persons.jsonl` after the collections and before the pins. A line whose name or addresses the
  factories refuse, or whose `id` an earlier line used, is `FIELD_INVALID`. `createdAt` is restored clamped, as a
  tag's is (the lead's: the person now has a line of its own, which ADR 0056's inventory, A4, asked to follow the
  existing rule).
- A pin line naming an identifier no line of `persons.jsonl` carries, or whose line was refused, creates the pin
  without that person and reports `PERSON_UNKNOWN`, as `MEDIA_DIGEST_MISMATCH` reports and creates: one issue per
  unknown identifier, its subject the identifier.
- `ExportCounts` gains `persons` and `collections`; `ExportReadme` describes `persons.jsonl` and the pin line's
  references.
- This reverses ADR 0056's decision 13, recorded in ADR 0057: a person is a composite value, and repeating its
  addresses on every pin line that names it is the duplication an identifier avoids. A tag or a board is named by one
  text, so it repeats nothing. `agents/data-modelling.md` gains the exception in block 66.

**G. Every other reference of the archive is an object holding its natural key** (D).
- A tag and a board are `{name}`, in a pin line's `tags` and `boards` and in a collection line's `board`; a collection
  is `{url}` in a pin line's `collections`. The manifest's `user` is `{name}`.
- No record carries the database's identifier any more: `user.json`, `tags.jsonl`, `boards.jsonl`, `pins.jsonl`, the
  pin's `media`, and the manifest's `user`. The manifest's `exportId` stays: it names the archive, not a record. A
  medium's `path` keeps its file name.
- A pin line's and a board line's `deletedAt` is `softDeletedAt`, the name `PinOutputDto` already gives it (G).
- The export writes a pin line's `collections` empty, as today it writes none: a pin's boards already say where it is.
- `formatVersion` stays 2 (`agents/data-modelling.md`, Contract): nothing is deployed.

## 4. Data shape

| Datum | Domain type | Optional | Identity and uniqueness | Storage | Wire | Archive |
|---|---|---|---|---|---|---|
| A pin's source page and source medium | `HttpUrl?` each | yes, null for none | None: a fact of the pin | `pins.source_context_url`, `source_media_url`, the normalised text | `string`, nullable, `format: uri` | the normalised text, null for none |
| A download's source | `HttpUrl` | no | None: one row per pin | `media_download.source_url`, the normalised text | `sourceUrl`, `string`, `format: uri` | None: downloads are not exported |
| A remote collection's address | `HttpUrl` | no | its address, per author | `remote_collections.url`, the normalised text | `string`, unchanged *(Corrected: `string`, `format: uri`, as decision C asks; holistic finding 2, block 80)* | `{url}` and a collection line's `url` |
| A person's name | `PersonName` | no | with its addresses, per author | `persons.name`, as written | `string`, unchanged | as written |
| A person's addresses | `Set<HttpUrl>` | no, empty for none | with its name, per author | `persons.urls`, a sorted JSON array | `array`, `uniqueItems`, sorted *(Corrected: its items `format: uri`; holistic finding 2, block 80)* | sorted array |
| A person line | `Person` | no | name and addresses; the archive `id` only links lines | None: it is the person row | None: no route lists persons in full | `persons.jsonl`, `{id, name, urls, createdAt}` |
| A person's archive identifier | None: the import holds it as text in a map, then forgets it | no | unique within `persons.jsonl`, at most 200 characters, deciding nothing | None: never stored | None: never on the wire | a person line's `id`, a pin line's `{id}` |
| A pin's or board's recycling instant | `Instant?`, unchanged | yes, null while active | None: a fact of the row | unchanged | `softDeletedAt` | `softDeletedAt`, was `deletedAt` |
| A record's database identifier | unchanged | unchanged | unchanged | unchanged | unchanged | None: removed from every record and from the manifest's `user`, now `{name}` |
| The media routes' refusals | `MediaSourceUrlInvalidError` removed | no | None: a value | None | 400 `VALIDATION_ERROR`; `MEDIA_SOURCE_URL_INVALID` leaves the set | None |
| A pin's person reference | `Person` | publisher nullable, creators a list | the person's | `pins.publisher_id`, `pin_creator_model`, unchanged | `{name, urls}`, unchanged | `{id}` |
| A tag, board or collection reference | `Tag`, `Board`, `RemoteCollection` | no | name, name, address | unchanged | unchanged | `{name}`, `{name}`, `{url}` |
| An import issue kind | `UserDataImportIssueKind.PERSON_UNKNOWN` | no | None: a value | `user_data_import_issues.kind`, unchanged column | `UserDataImportIssueKindDto`, one value more | None: the archive carries no issue |
| Export counts | `ExportCounts` | no | None: a value | None: written once into the manifest | None: no route reads them | `manifest.json`'s `counts`, `persons` and `collections` added |

Rules this table follows, from `agents/data-modelling.md`: the standard type first and a value class for an
invariant `URI` lacks; the normalisation in the factory, which never throws; only a mapper calls a factory, the
import's reading of a line being that line's mapper; the domain's equality matches the index's fold; a set for
elements distinct by nature; the archive names by natural keys and gives a concept one shape, F being the operator's
recorded departure for a composite value, its identifier deciding nothing (ADR 0057).

## 5. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/an-address-is-typed` | Decision B's type, and the Konsist rule. |
| 20 | `feat/a-pin-source-is-an-address` | Decisions B and C on a pin's two addresses. |
| 30 | `feat/a-download-fetches-an-address` | Decisions B and C on downloads and fetchers. |
| 40 | `feat/a-collection-is-an-address` | Decision B on remote collections. |
| 50 | `feat/a-person-is-its-name-and-addresses` | Decisions D and E. |
| 60 | `feat/the-archive-lists-people` | Decision F's `persons.jsonl`. |
| 63 | `feat/an-import-reports-an-unknown-person` | Decision F's `PERSON_UNKNOWN` kind. |
| 66 | `feat/a-pin-names-its-people-by-id` | Decision F's `{id}` references. |
| 70 | `feat/the-archive-names-by-key` | Decision G. |
| 80 | `chore/the-shapes-lot-closes` | The holistic review's findings and the documents (Wrap). |
| 85 | `chore/the-shapes-lot-documents` | The flaky journey, `api/AGENTS.md` and the documents (Wrap). |

Each block is measured after its first commit with the command of `agents/workflow.md`; one that passes a bound
splits at a number between its own and the next. **Blocks 20, 30 and 50 cannot hold the file bound**: a type change
compiles only once every file using the type follows, and `git grep -l` gives 59, 67 and 40 files as upper bounds
(the operator's answer H, 2026-10-10). Their line bound stays strict. Rows already stored are not healed (`agents/data-modelling.md`,
Storage): a development database holding an address the factory refuses is reset.

### Block 10

- `HttpUrl` and its tests; the Konsist allow-list gains `java.net.URI`. Its consumers are blocks 20 to 50.
- Each normalisation of decision B, one case each: `HTTPS://Example.TEST:443/a/./b/../c?q=%7e#F` reads
  `https://example.test/a/c?q=~#F`; `https://x.test/../a` equals `https://x.test/a`; `https://x.test` reads
  `https://x.test/`; `https://x.test/a/` and `https://x.test/a` are two values; `%2f` stays encoded and upper-cased;
  `https://x.test/café` equals `https://x.test/caf%C3%A9`; `https://x.test/a b|c?q={x}` reads
  `https://x.test/a%20b%7Cc?q=%7Bx%7D`; `https://x.test/100%` reads `https://x.test/100%25`.
- An address whose normalised text is 2000 characters parses; one of 2001 is refused.
- Refused, one case each: blank, `/relative`, `ftp://x.test/`, `https:///path`, `https://[x.test/`.
- A Konsist test refuses a property whose type is a value class in a class of `per/models`. It runs its rule over a
  fixture model under `src/test/resources/`, not compiled, which declares such a property, and asserts the rule
  flags it, then over production, and asserts it flags nothing.
- Carries this specification and ADR 0057.
### Block 20

- `Pin.sourceContextUrl` and `sourceMediaUrl` become `HttpUrl?`, through `PinCreator`, `PinUpdater`, `PinModelMapper`,
  the duplicate merge, the export and the import. `blankAsNone` goes.
- `POST` and `PUT /api/v1/pins` with `sourceContextUrl: ""`, `"not an address"` or `"ftp://x.test/"` answer 400
  `VALIDATION_ERROR`, the detail naming the field; with `"HTTPS://X.test/a"` they answer `https://x.test/a`.
- An import pin line with a blank or non-http(s) `sourceMediaUrl` is `FIELD_INVALID` and creates no pin.
- `info.version` is `25.0.0`: an input refused that was accepted. The lot's later breaks stay under it.

### Block 30

- `MediaDownload.sourceUrl`, `MediaFetcher.openStream`, `PageMediaExtractor.extract` and `upsertPending` take
  `HttpUrl`. `RequestPinMediaDownload` receives one and parses nothing; `HttpMediaFetcher.httpUri` checks the
  redirects alone (decision C).
- `PUT /api/v1/pins/{pinId}/media` with `https:///path` answers 400 `VALIDATION_ERROR` and records no download.
- `MediaSourceUrlInvalidError` and `MEDIA_SOURCE_URL_INVALID` are gone from the code, the contract and the generated
  client types (`git grep -n MEDIA_SOURCE_URL_INVALID -- api contract clients` prints nothing).

### Block 40

- `RemoteCollection.url` and `findUserRemoteCollectionByUrl` take `HttpUrl`; the import's collection line and pin
  line `collections` go through the factory. `RemoteCollectionModel`'s comment "the server normalises no address"
  goes.
- An archive whose collection line reads `HTTPS://X.test/c` and whose pin line names `https://x.test/c` joins the
  pin to that collection's board.

### Block 50

- `PersonName`, and `Person` and `PersonReference` of decision E; `PersonModelMapper` encodes only;
  `ImportFieldBounds.personFault` keeps the address count alone; `api/AGENTS.md` carries decision D's exception.
- `PersonName.parse("Alice")` equals `PersonName.parse("ALICE")` with equal hash codes, `PersonName.parse("Élodie")`
  does not equal `PersonName.parse("élodie")`, and `"ALICE"` keeps its case when read back.
- A `PUT` naming a creator `{name: "Alice", urls: ["HTTPS://A.test", "https://a.test/"]}` stores one address and
  answers `["https://a.test/"]`; a second `PUT` naming `{name: "alice", urls: ["https://a.test"]}` creates no
  `persons` row.
- An import pin line naming a person with 21 addresses is `FIELD_INVALID`.
- `PersonOutputDto.urls` declares `uniqueItems`; `PersonInputDto`'s items declare `format: uri`.

Block 60 measured about 28 files with both halves of decision F; it splits into 60, 63 and 66. The `{id}` reference
cannot split further: the export and the import change together, or the round trip breaks.

### Block 60

- `persons.jsonl`, written and read as decision F says, its pin lines still naming `{name, urls}`; a port listing a
  user's persons; the README and the counts.
- The round trip carries a person no pin names, and the issue list is empty.
- A second line reusing an `id`, and a line whose `id` has 201 characters, are `FIELD_INVALID`.
- A `persons.jsonl` of 100 001 lines: the last is `FIELD_INVALID`, and the one before it imports.
- A person line's `createdAt` earlier than the account's creation is clamped as a tag's.
- `ExportContentGoldenJsonTest` shows a person line and the two new counts.

### Block 63

- `PERSON_UNKNOWN` in the domain, the contract, and `importIssues.ts` with its sentence in English and French. Its
  producer arrives in block 66.

### Block 66

- A pin line's `publisher` and `creators` become `{id}` on export and import; the import keeps the identifier of each
  person line and reads the persons back by identifier when it creates the pin. `ImportArchiveBuilder` writes the
  `persons.jsonl` entry; `agents/data-modelling.md`'s Archive rules gain ADR 0057's exception.
- One archive whose two `persons.jsonl` lines carry one name in two ASCII cases with the same addresses creates one
  `persons` row, and two pin lines naming each identifier credit that one row.
- A pin line naming two identifiers absent from `persons.jsonl` creates the pin without them and reports two
  `PERSON_UNKNOWN`, each naming its identifier.
- `ExportContentGoldenJsonTest` shows a pin line's `{id}` references.

### Block 70

- Decision G in the export's content types and the import's; `ImportArchiveBuilder` writes the new shapes.
- `ExportContentGoldenJsonTest` shows no `id` key in any record but a person line, `{name}` for the collection
  line's `board` and the manifest's `user`, and `softDeletedAt` and no `deletedAt` on a pin line and a board line.
- The round trip, collections included, comes back with an empty issue list.
- An archive written with `ImportArchiveBuilder`, read by the real mapper, whose collection line's `board` is a bare
  string is `LINE_MALFORMED`.

### Block 80

- The holistic review's findings, then the documents: this specification, the handoff, ADR 0056's and ADR 0055's
  `Status:` lines naming ADR 0057 and this lot, `docs/specs/2026-10-08-the-pin-credits-its-people.md`'s `Status:`
  line naming the decisions this lot overturns (A's "the server normalises no address", D's persons only inside pin
  lines, E's collection identity now a normalised address), and the backlog. *(Corrected: D's "stamped with the import
  instant" and its person fault refusing the pin line as `FIELD_INVALID` are overturned too; holistic finding 5.)*

Block 80 measured 25 files with the holistic review's code findings alone; it splits into 80, the API's findings, and
85, the web application's flaky journey, `api/AGENTS.md` and the documents. The lead chose the split; the operator was
not consulted.

### Block 85

- The journeys that count a field's requests type a term with no pause between its keys (`typeInOneGo`).
- `api/AGENTS.md`'s value class rule names `ModelsPackageArchTest` and its limit; the documents of block 80's list.

## 6. Adjacent backlog items

- **"Import from third-party sites"** stays open: its importer is the next lot but one, after the foreign keys.
- **"`foreign_keys` is off"** stays open: ADR 0056's next lot.
- **"The domain names concepts with bare strings"** stays open, the types lot: this lot takes its addresses and the
  person's name and leaves the other names.
- **"Import follow-ons"** stays open: an existing pin is still skipped untouched.
- **"People and tags have no management page"** stays open: no route edits a person.

## 7. Out of scope

- **A non-ASCII host.** `URI` reads one as a registry authority with no host, so the factory refuses it; an IDN
  conversion is not made. Observed in block 10's tests: `https://éx.test/` is refused.
- **A host holding an underscore**, which `URI` reads as a registry authority with no host too, so the factory refuses
  it, where a pin stored it before this lot. Observed in block 80's `HttpUrlTest`: `https://a_b.example.test/x` is
  refused (holistic finding 3).
- **Credentials in an address** (`https://user:pass@x.test/`) are kept as written. Observed in block 10: one parses.
- **The other names** (`TagName`, `BoardName`, `Username`): the types lot. Observed as `TagCreator.resolve` taking a
  `String` at the lot's tip.
- **The archive's `sha256`, `mediaType` and medium shape** (decision A): the types lot. Observed as
  `ExportedMedia`'s fields unchanged at the lot's tip.
- **Healing stored rows.** No migration in this lot: the last migration is still `1.35` at its tip.
