# Names take their shape

Date: 2026-10-13
Status: Accepted by the operator on 2026-10-13. One specification review ran, `.reviews/names-take-their-shape-spec.md`, its 1 CRITICAL, 6 MAJOR and 14
MINOR closed in this document and ADR 0060. Frozen when the lot's last block merges.
Lot: `0.58.0`.
Branches: one stack, each block on the one before it: 10 `feat/a-name-is-bounded-once`, 20 `feat/a-tag-is-its-name`,
30 `feat/a-board-is-its-name`, 40 `feat/a-collection-is-its-name`, 45 `feat/an-account-name-is-typed`, 50
`refactor/an-account-is-its-name`, 60 `chore/the-names-lot-closes`.
ADR: `docs/adr/0060-the-refactorings-come-before-the-importer.md`, which this lot opens.

## 1. Goal

Every name the domain holds is a type of its own, which refuses what its entry paths refuse today and is checked by one
factory on every path. The first of ADR 0060's six types lots: `inventory.md` units 8a, 8b and 8d, T1's
`RemoteCollection.name`, T4's bounds defined twice, T5's trim. No behaviour changes but the two refusals decision C
names.

## 2. What exists today

Read at `f50a3e10`. `dom`, `uc`, `per` and `pres` stand for the main sources of `api-domain`, `api-usecases`,
`api-persistence-ebean` and `api-presentation-quarkus`; `R` is `uc/imports/UserDataImportRunner.kt`.

- **`PersonName`** (`dom/entities/PersonName.kt`) is the model: a `@JvmInline value class` over the text as written,
  its constructor private, `parse(text): PersonName?` refusing a blank text and one past 200 characters. The REST
  check is `@PersonNameText` (`pres/dtos/input/PersonNameText.kt`, target `FIELD`), whose validator calls `parse`; the
  persistence and presentation mappers call `checkNotNull(PersonName.parse(...))`; the import reports
  `PersonLineImporter.NAME_FAULT`.
- **`Tag.name`, `Board.name`, `RemoteCollection.name` and `User.name` are `String`.** The 200-character bound is
  declared four times: `Tag.MAX_NAME_LENGTH`, `Board.MAX_NAME_LENGTH`, `PersonName.MAX_LENGTH`,
  `ImportFieldBounds.MAX_NAME_LENGTH`.
- **REST checks a tag or board name with `@NotBlank @Size(max = 200)`** (`BoardCreationInputDto`, `BoardInputDto`, the
  items of `PinUpdateInputDto.tags`). `@NotBlank` trims only what `String.trim` trims, so a name of U+2003 passes,
  though the contract's `pattern: \S` refuses it.
- **The import checks a tag line, a board line and a collection line** with `ImportFieldBounds.nameFault`
  (`R:323,376,463-464`), which reports `name is blank` or `name is longer than 200 characters`, prefixed `board ` for a
  collection line's board. A collection line without a board names its board after itself (`R:454`).
- **A pin line's tag and board references** are read back by name when the pin is created, and one that resolves to
  nothing is dropped, the pin created, by design (`R:574-587`).
- **`User.name`** follows `User.NAME_PATTERN` (`[A-Za-z0-9._-]`) and 3 to 50 characters on `UserInputDto`.
  `UserCreator.kt:37` trims a name the pattern already refuses with a space. `UserAuthenticator.kt:42` reads the account
  only for a login name inside the alphabet, any length (ADR 0059, decision 11). The export writes the name to
  `user.json` and the manifest; `BearerTokenIdentityProvider.kt:28` reads it.
- **Tests construct `User` 192 times in 82 files**, each file with its own line; 20 files name a user outside the
  alphabet or under 3 characters (`"John Doe"`, `"u"`, `"élodie"`...). No test fixture module depends on `api-domain`.
- **Kover** verifies 100% branch coverage per package on every module but `api-application`
  (`api/build.gradle.kts:106-147`), and counts every source set but `test`.
- **The contract** is `27.0.0`; the name schemas are `pattern: \S, maxLength: 200` and, for an account, the alphabet
  with `minLength: 3, maxLength: 50`.

## 3. Decisions

The operator's answers in the Discuss of 2026-10-13, the letter of the question in brackets, unless a decision says it
is the lead's. None needs an ADR: A to D apply `agents/data-modelling.md`'s Types rules and ADR 0059, decisions 8 and
11; E applies ADR 0024, decision 6; F is a build setting with `api-utilities`' precedent.

**A. One type per concept** (C): `TagName`, `BoardName`, `CollectionName`, beside `PersonName`, and `Username`. Each is
a `@JvmInline value class` over the text as written, constructor private, `parse(text): T?` never throwing, `toString`
its text. Equality is the text's: the database alone folds (ADR 0059, decision 8).

**B. The shared rule is written once** (C, "share where it fits"; the shape is the lead's).
- `NameBounds` in `dom/entities`, an `object` holding the rule alone: `MAX_LENGTH = 200` and `accepts(text)`, false for
  a blank text (Kotlin's `isBlank`) or one past `MAX_LENGTH`. The four 200-character factories call it; the four
  declarations of section 2 go.
- `@NameText` replaces `@PersonNameText`, targets `FIELD` and `TYPE`, and its validator calls `NameBounds.accepts`: one
  annotation for the four names. `@AccountName` checks a `Username`, its validator calling `Username.parse`.
- On the import, one fault text, `ImportFieldBounds.NAME_FAULT`: `name is blank or longer than 200 characters`, still
  prefixed `board ` for a collection line's board. `PersonLineImporter.NAME_FAULT` goes.
- Accepted limit: Kotlin's `isBlank` and the contract's ECMAScript `\S` disagree on U+001C to U+001F (blank to Kotlin)
  and U+FEFF (blank to ECMAScript).

**C. Every entry path calls the factory** (the lead's, and F for the pin line).
- REST: the annotation refuses with 400 `VALIDATION_ERROR`; the presentation mapper calls `parse` and reads a null as a
  defect. **A name of U+2003 is now refused**, as the contract already says.
- Import: a tag, board or collection line, and a collection line's board, call the factory; a refusal is
  `FIELD_INVALID` with `NAME_FAULT`, as today. The runner is the archive line's mapper (the precedent of
  `PersonLineImporter`).
- A pin line's tag and board references go through `parse`; a refused one is dropped as an unresolved one is today
  (F).
- Persistence: the mapper calls `checkNotNull(T.parse(stored))`.
- Search terms stay `String`: a substring is not a name.

**D. `Username`** (the lead's, from A).
- `parse` refuses a text outside `[A-Za-z0-9._-]` or outside 3 to 50 characters. `User.NAME_PATTERN` and
  `MAX_NAME_LENGTH` move to it; `UserCreator`'s trim goes.
- The submitted login name stays `String` (`BasicAuthLogin.userName`, `SessionCreationInputDto.name`): the attempt key
  counts any text. `UserAuthenticator` reads the account only when `Username.parse` accepts the name, in place of its
  regex: **a login name of 2 or 51 characters in the alphabet now reads no account**, and answers as an unknown name
  does. The parse sits in a use case because a login name is not a datum the API holds: its refusal is a lookup miss,
  not a defect. (Corrected: `findUserByName` takes the `Username`, so no caller can reach the store with a text outside
  the alphabet; the closing block typed it.)

**E. The contract does not change.** `@Schema` at each use site writes the schema the bean validation annotations
wrote, as `PersonInputDto.kt:12` does. The items of `PinUpdateInputDto.tags`, a type-use position `@Schema` cannot
reach, keep `@NotBlank @Size(max = NameBounds.MAX_LENGTH)` beside `@NameText` for their schema. (Corrected: so a tag
item of `"  "` or of 201 characters answers a `detail` joining two violations, where a board or person name answers
one. Accepted as this decision's consequence, from the holistic review.) SmallRye may reorder a schema's keys, so the
check sorts them: `diff <(git show origin/main:contract/openapi.json | jq -S .) <(jq -S . contract/openapi.json)`
prints nothing. `info.version` stays `27.0.0`.

**F. The test fixtures** (E, G).
- `api-domain` gains `java-test-fixtures`, excluded from its Kover verification. (Corrected: the exclusion sits in
  `api/build.gradle.kts`, for every module's test fixtures, and `agents/engineering.md` lists it; the fixtures depend
  on `api-utilities`' for `TestTime`.) `DomainFixtures` holds one helper per name type (`tagName(text)`, ...) and
  `aUser(name: String = <random in the alphabet>, id = randomUUID(), createdAt = TestTime.now, softDeletedAt = null)`,
  every test building a user through it. (Corrected: `personName` came in the closing block.)
- Blocks whose type change reaches every construction may pass the file bound (E). **Block 50 does the change of
  `User.name` and nothing else, and passes both bounds** (G): its review is mechanical.

## 4. Data shape

| Datum | Domain type | Optional | Identity and uniqueness | Storage | Wire | Archive |
|---|---|---|---|---|---|---|
| A tag's name | `TagName` | no | per author, the collation's | `tags.name`, unchanged | unchanged | unchanged |
| A board's name | `BoardName` | no | per author, recycled rows included, unchanged | `boards.name`, unchanged | unchanged | unchanged |
| A remote collection's name | `CollectionName` | no | None: the address identifies it | `remote_collections.name`, unchanged | unchanged | unchanged |
| A person's name | `PersonName`, on `NameBounds` | no | unchanged | unchanged | unchanged | unchanged |
| An account's name | `Username` | no | the collation's, tombstones included, unchanged | `users.name`, unchanged | unchanged | `user.json` and the manifest's `user`, unchanged |
| A submitted login name | `String`, unchanged | no | None: a key of the attempt limiter | None | unchanged | None |

Rules this table follows, from `agents/data-modelling.md`: never a bare string for a concept; a fallible factory a
mapper calls, decision D's login being the stated exception; the domain never folds more than the database.

## 5. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/a-name-is-bounded-once` | Decision B. |
| 20 | `feat/a-tag-is-its-name` | Decisions A, C and E on tags; F's fixtures. |
| 30 | `feat/a-board-is-its-name` | Decisions A, C and E on boards. |
| 40 | `feat/a-collection-is-its-name` | Decisions A and C on remote collections. |
| 45 | `feat/an-account-name-is-typed` | Decision D, `User.name` unchanged. |
| 50 | `refactor/an-account-is-its-name` | `User.name: Username`, and nothing else. |
| 60 | `chore/the-names-lot-closes` | The holistic review's findings and the documents (Wrap). |

Counted with `git grep -l` at `f50a3e10`, constructions and lookups: tags about 30 files, boards about 35, collections
about 12; block 50 about 90 files and 400 lines, its constructions alone 213 lines. Blocks 20 and 30 may pass the file
bound and block 50 both (decision F).

### Block 10

- This specification, ADR 0060, ADR 0056's `Status:` line naming it, and the backlog of ADR 0060's Consequences.
  (Corrected: the backlog files the types lots after this one, this lot's own item being one it would have filed and
  closed at once, #438.)
- `NameBounds`; `PersonName` on it; `@NameText` in place of `@PersonNameText`; `ImportFieldBounds.nameFault` reading
  `NameBounds.accepts` and answering `NAME_FAULT`, until blocks 20 to 40 call the factories. (Corrected:
  `PersonName.MAX_LENGTH` and `ImportFieldBounds.MAX_NAME_LENGTH` went here too, their readers on
  `NameBounds.MAX_LENGTH`, #438.)
- `NameBounds.accepts` refuses `""`, `" "` and 201 characters, and accepts 200.
- `UserDataImportCollectionsTest` reads `name is blank or longer than 200 characters` for a blank and a 201-character
  collection name, and the same prefixed `board ` for its board.

### Block 20

- `TagName` through `Tag`, `TagRepositoryInterface`, `TagCreator`, `PinTagger`, `PinUpdater`, the mapper, the export
  and the import; `Tag.MAX_NAME_LENGTH` goes, `MigratedSchemaTest` reading `NameBounds.MAX_LENGTH`. Decision F's
  fixtures start here, with `tagName`. (Corrected: `PinCreator.createPin` took a `List<TagName>` too, a caller this
  list missed, #439.)
- `TagName.parse` refuses `" "` and 201 characters.
- `PUT /api/v1/pins/{pinId}` with a tag `" "` answers 400 `VALIDATION_ERROR`.
- A `tags` row whose name is blank, written by SQL, fails the mapper when read.
- An import pin line naming a 201-character tag creates the pin without it and reports nothing.
- Decision E's contract check prints nothing.

### Block 30

- `BoardName` through `Board`, `BoardRepositoryInterface`, `BoardCreator`, `BoardUpdater`, `PinBoardSetter`,
  `RemoteCollectionLinker`, the mapper, the export and the import; `Board.MAX_NAME_LENGTH` goes.
- `POST /api/v1/boards` with `name: " "` answers 400 `VALIDATION_ERROR`.
- A `boards` row whose name is blank, written by SQL, fails the mapper when read.
- Decision E's contract check prints nothing.

### Block 40

- `CollectionName` through `RemoteCollection`, `RemoteCollectionLinker`, the mapper, the export and the import. A
  collection line without a board converts its `CollectionName` to the `BoardName` it names; `ImportFieldBounds.nameFault`
  goes. (Corrected: through `BoardName.namedAfter(CollectionName)`, which cannot fail, where `parse` would leave a
  branch no test reaches, #442.)
- `CollectionName.parse` refuses `" "` and 201 characters.
- A `remote_collections` row whose name is blank, written by SQL, fails the mapper when read.

### Block 45

- `Username` and `@AccountName` on `UserInputDto`; `UserCreator` and `UserAuthenticator` call `Username.parse`, the
  trim gone. `User.name` stays `String`. (Corrected: `UserCreator` takes a `Username`, which
  `UserDtoMapper.toUsername` parses, the mapper being where decision C puts it, #443.)
- `Username.parse` refuses `"ab"`, `"a b"`, 51 characters and `"ａlice"` (U+FF41), and accepts `"Alice.B"`.
- A login as `"ab"`, or as 51 characters in the alphabet, answers 401 and never calls `findUserByName`.
- Decision E's contract check prints nothing.

### Block 50

- `User.name: Username` through the mappers, the export and `BearerTokenIdentityProvider`; every test builds its user
  with `DomainFixtures.aUser`.
- A `users` row named `a b`, written by SQL, fails the mapper when read.
- No test file but `DomainFixtures` constructs `User` (`git grep -nP '(?<![A-Za-z_.])User\(' -- 'api/*/src/test/*'`
  prints nothing).

### Block 60

- The holistic review's findings, the handoff, and the backlog. (Corrected: three pull requests past the file bound,
  60 `chore/the-names-lot-closes` for the code, 62 `chore/the-names-lot-fixtures` for the fixtures, 65
  `chore/the-names-lot-documents` for the documents.)

## 6. Adjacent backlog items

- **ADR 0060's five other types lots, the sealed states lot and the batch writes lot** stay open: each has its own lot.
- **"Import from third-party sites"** stays open: after the refactorings (ADR 0060, decision 1).
- **"People and tags have no management page"** stays open: no route renames a tag.

## 7. Out of scope

- **A board's description** stays `String`: free text, not a concept (`inventory.md`, section 2).
- **Search terms** stay `String` (decision C).
- **Healing stored rows**: a development database holding a name a factory refuses is reset.
