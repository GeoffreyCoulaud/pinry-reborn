# Handoff: names take their shape

Date: 2026-10-13
Tier: Spec. Specification `docs/specs/2026-10-13-names-take-their-shape.md`, reviewed in
`.reviews/names-take-their-shape-spec.md` (1 CRITICAL, 6 MAJOR, 14 MINOR, closed in the specification and ADR 0060);
ADR 0060 amends ADR 0056, decision 7.
Lot `0.58.0`, the first of ADR 0060's six types lots, one stack, GitHub stack #440: 10 `feat/a-name-is-bounded-once`
(#438), 20 `feat/a-tag-is-its-name` (#439), 30 `feat/a-board-is-its-name` (#441), 40 `feat/a-collection-is-its-name`
(#442), 45 `feat/an-account-name-is-typed` (#443), 50 `refactor/an-account-is-its-name`, then the closing block 60
`chore/the-names-lot-closes`. Written in block 50 from the block reports of #438 to #443 and its own; the closing
block corrects it. (Corrected: block 50 is #444; the closing work splits into 60 `chore/the-names-lot-closes`, 62
`chore/the-names-lot-fixtures` and 65 `chore/the-names-lot-documents`, all three the closing block.)

## Current state

Every name the domain holds is a type of its own, built only through its factory, on every entry path.

- **`NameBounds`** holds the 200-character rule once: `accepts(text)` is false for a blank text (Kotlin's `isBlank`)
  or one past 200 characters. `PersonName`, `TagName`, `BoardName` and `CollectionName` call it.
- **`Username`** holds the account alphabet `[A-Za-z0-9._-]` and its 3 to 50 characters. `User.name` is a `Username`
  from the store to the export and the bearer principal (#443, block 50). (Corrected: #444.)
- **REST** checks a name with `@NameText` (the four 200-character names) or `@AccountName`, 400 `VALIDATION_ERROR`;
  the presentation mapper then reads a refused name as a defect. **A name of U+2003 is now refused**, as the
  contract already said.
- **The import** reports one fault text, `ImportFieldBounds.NAME_FAULT` (`name is blank or longer than 200
  characters`, prefixed `board ` for a collection line's board). A pin line's refused tag or board reference is
  dropped silently, as an unresolved one already was (decision F, the operator's answer to the specification review's
  CRITICAL).
- **The store**: each mapper calls `checkNotNull(T.parse(stored))`, so a row no write of the API makes fails when read.
- **Login** keeps the submitted name as a `String`; `UserAuthenticator` reads an account only when `Username.parse`
  accepts it, so **a login of 2 or 51 characters in the alphabet now reads no account** and answers 401 (#443).
  `findUserByName` takes the `Username` since block 60, so no caller reaches the store with a text outside the
  alphabet.
- **The contract does not change**: `info.version` stays `27.0.0`, and the key-sorted diff against `main` printed
  nothing on #439 to #443.
- **Tests build their values through `DomainFixtures`** (`api-domain`'s test fixtures, outside its coverage bound):
  `tagName`, `boardName`, `collectionName`, `username`, and `aUser`, through which every test builds its user.
  (Corrected: `personName` too, and every module's test fixtures are outside the bound, set in `api/build.gradle.kts`,
  block 62.)
  No test but `DomainFixtures` constructs `User` (`git grep -nP '(?<![A-Za-z_.])User\(' -- 'api/*/src/test/*'`
  prints nothing at block 50).

## Evidence

| Block | Gate | Budget against its parent |
|---|---|---|
| 10 (#438) | `dagger call gate` green at `10cc6e28` | 83 lines, 13 files |
| 20 (#439) | green at `d197a4e0`, after a coverage miss and a `MaxLineLength` | 261 lines, 45 files |
| 30 (#441) | green at `bae756b9`, after a `RowMergedOutsideTransaction` | 410 lines, 46 files |
| 40 (#442) | green at `9025bed3`; `pre-push` ran it again | 145 lines, 19 files |
| 45 (#443) | green at `672d2483`, after a `MaxLineLength` | 193 lines, 18 files |
| 50 (#444) | green at `dd817a24`, after a value class compared to a `String` | 468 lines, 95 files |
| 60 | green at `a983bbb3` | 32 lines, 14 files |
| 62 | green at `47383074` | 67 lines, 19 files |
| 65 | `dagger call prose`, its paths all ending in `.md` | 0 lines, 0 files: dated documents alone |

Blocks 20, 30 and 50 pass the file bound, as decision F allows: the type change reaches every test that builds the
value. Block 50 stays under the line bound, which the specification allowed it to pass.

## Departures from the specification

- **`PersonName.MAX_LENGTH` and `ImportFieldBounds.MAX_NAME_LENGTH` went in block 10**, their readers moved to
  `NameBounds.MAX_LENGTH` (#438).
- **The backlog files five types lots, not six** as ADR 0060's Consequences say: this lot's own item would have been
  filed and deleted in the same lot (#438).
- **`PinCreator.createPin` takes `List<TagName>`**, a caller the specification did not list (#439).
- **The board line's import was reshaped** to satisfy `RowMergedOutsideTransaction`: the lookup moved into
  `importBoard`, and the new board is built inside the `saveBoard` call (#441).
- **A collection line naming no board converts its name with `BoardName.namedAfter(CollectionName)`**, a factory
  that cannot fail, where `BoardName.parse` would leave a branch no test can reach (#442).
- **`UserCreator` takes a `Username`, and `UserDtoMapper.toUsername` calls `parse`**, where the block section said
  `UserCreator` calls it: the mapper is where decision C puts it (#443).
- **`UserRepositoryTest`'s two case folds outside ASCII now fold ASCII** (`Alice`, `ALICE`): an account name can no
  longer hold `élodie`, and the collation's fold outside ASCII stays pinned on tags, boards and persons (block 50).
- **`api-domain`'s test fixtures depend on `api-utilities`' test fixtures**, for `TestTime` (block 50).
- **The closing block is three pull requests, not two**: the code findings pass the file bound, so the fixtures'
  findings (the coverage exclusion, `personName`, the `createUser` wrappers) are block 62, between the code of
  block 60 and the documents of block 65.
- **The coverage exclusion covers `api-utilities`' test fixtures too**, which hold no branch: the root's Kover block
  sets it for every module (block 62).

## Pitfalls

- **MockK 1.14 hands an inline value class argument to `answers` unboxed**: `firstArg<TagName>()` throws
  `ClassCastException`, so the import fixtures read `secondArg<String>()`. Stubbing with a value matches fine.
- **`assertEquals("x", board.name)` compiles against a value class and fails at run time**; so does Hamcrest's
  `equalTo(user.name)` against a JSON string. Read `.text`, and search for them: a failing test reveals one at a time.
- **The edit tools turn a literal non-ASCII character into another one**: write such test inputs as `\uXXXX`
  escapes. ktfmt rewrites an escape of a space character into the literal character.
- **`UserDataImportRunner` sits at detekt's `LargeClass` bound of 600 lines**; #442 had to shorten it to fit.
- **Test names that read as people (`"John Doe"`, `"u"`) fall outside `Username`**; `aUser()` draws a name inside it.
- **`gh stack submit` showed no trace of the `pre-push` gate** on two pushes; the gate was run by hand before each.

## Tier-2 questions

None asked during Act. The operator's answers in Discuss are decisions A to G of the specification. (Corrected: one
in the closing block. Deleting `PinCreator.createPin`'s `tags` parameter, the holistic review's first fix, touched 21
files, two more blocks; keep it and leave it to the batch writes and transactions lot? The operator answered "B":
keep it.)

## The holistic review

Runs at the head of Wrap over the top of the stack; its findings and their exits go here in the closing block.
(Corrected: it ran, `.reviews/names-take-their-shape-holistic.md`, over
`git diff lot/0.57.0-the-projects-reference-each-other..origin/refactor/an-account-is-its-name`: 0 CRITICAL, 2 MAJOR,
12 MINOR. It found every case the specification asks for, and the contract's key-sorted diff over the whole lot
empty. Each finding and its exit:)

1. MAJOR, `findUserByName` took a `String`, so ADR 0059 decision 11's guard lived in its one caller: fixed in block
   60, the port takes a `Username`.
2. MAJOR, the test fixtures' coverage exclusion sat in `api-domain`'s build, and `agents/engineering.md`'s Outside
   list did not name it: fixed in block 62, set in `api/build.gradle.kts` for every module and listed.
3. `BoardNameTest`'s em space test fed U+0020, and `NameBoundsTest` had no blank Kotlin and `@NotBlank` disagree on:
   fixed in block 60, both feed U+2003.
4. `MeExportController` and `MeDeleteIntegrationTest` read the account name through `toString`, and nothing pinned
   the filename: fixed in block 60, both read `.text` and `MeExportControllerTest` asserts the name.
5. `PersonSearcherTest` and `TagSearcherTest` wrapped `aUser()`: fixed in block 62, the wrappers inlined.
6. `ContentDispositionFileName`'s comment said account names are only trimmed: fixed in block 60, it says the
   encoding stays so the header trusts no caller.
7. `ImportFieldBounds`' comment and a `UserDataImportRunnerTest` comment said the names' bounds live on DTOs alone:
   fixed in block 60.
8. `PersonName` had no fixture: fixed in block 62, `DomainFixtures.personName`, every hand-written parse moved onto
   it.
9. Nothing accepted a 3-character account name: fixed in block 60, `UsernameTest` accepts `abc`.
10. A tag item's `detail` joins two violations where a board name answers one: accepted as decision E's
    consequence, recorded there in block 65.
11. `api/AGENTS.md` said `api-domain` has no project dependency, and the test conventions did not name
    `DomainFixtures`: fixed in block 62.
12. ADR 0060's Consequences still said one backlog item per lot: fixed in block 65, `(Corrected: ...)`.
13. This handoff named #444 nowhere, gave #443 for block 50, and overstated the 201-character gap: fixed in block 65.
14. `PinCreator.createPin`'s `tags` parameter is filled by tests alone, its one production caller passing
    `tags = emptyList()`: left to the batch writes and transactions lot, which reworks `PinCreator` for its
    transaction and whose backlog item points to `inventory.md`'s I7, where the dead parameter is already named. The
    operator's answer to the tier-2 question below; no new backlog item.

Left for the closing block by the blocks themselves (Corrected: findings 3, 4 and 5 above):

- **`BoardNameTest`'s "one em space" test parses U+0020** (#442), as block 30's REST tests did before #442 fixed
  them.
- **`PersonSearcherTest` and `TagSearcherTest` keep `createUser() = aUser()`**, a wrapper block 50 left in place to
  stay mechanical.
- **`MeExportController` and `MeDeleteIntegrationTest` read `user.name` through a string template**, which is the
  text through `toString`, where the rest reads `.text`.

## What is not validated

- **The 401 for a login of 2 or 51 characters** is held at the use case, not by a REST test (#443).
- **arm64**: every block built one architecture.
- **The two 201-character pin line tests pass on the old code too** (#439, #441): the old lookup also found nothing,
  so they pin the outcome rather than fail on the old code. (Corrected: the old code cannot compile against
  `CreatedPin.tagNames: List<TagName>`; among what the types allow, the tests tell dropping from throwing, which
  creates no pin, and from reporting, which fills `savedIssues`.)

## The lot's counts

- Fix-backs: 0. Fixes on top: 0. Cascaded rebases: 0. Runs re-triggered by them: 0.
- The operator's reading of the bodies: to be filled before the merge.

## Next step

The holistic review and the closing block 60, then the operator's review of the stack and the tag
`lot/0.58.0-names-take-their-shape`. Then the tasks lot of ADR 0060, decision 2. (Corrected: the review and the
closing blocks 60, 62 and 65 are on the stack; what is left is the operator's review, the merge and the tag. The
backlog holds no item this lot closes, its own never filed, and the items the specification's section 6 names stay
open.)
