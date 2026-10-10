# Handoff: addresses and people take their shape

Date: 2026-10-10
Tier: Spec. Specification `docs/specs/2026-10-10-addresses-and-people-take-their-shape.md`, reviewed in
`.reviews/addresses-and-people-take-their-shape-spec.md`; ADR 0057 beside ADR 0056, decision 7.1.
Lot `0.54.0`, one stack: 10 `feat/an-address-is-typed` (#382), 20 `feat/a-pin-source-is-an-address` (#383), 30
`feat/a-download-fetches-an-address` (#385), 40 `feat/a-collection-is-an-address` (#386), 50
`feat/a-person-is-its-name-and-addresses` (#387), 60 `feat/the-archive-lists-people` (#388), 63
`feat/an-import-reports-an-unknown-person` (#389), 66 `feat/a-pin-names-its-people-by-id` (#390), 70
`feat/the-archive-names-by-key` (#391), then the closing block 80. Written in block 70 from the block reports of
#382 to #391. (Corrected: the closing blocks are 80 `chore/the-shapes-lot-closes` (#392) and 85
`chore/the-shapes-lot-documents`; completed in block 85 from #392's report.)

## Current state

- **Every address the API holds is an `HttpUrl`**, a value class over `java.net.URI` in `domain.entities`: a pin's
  two sources, a download's source, a remote collection's address and a person's addresses. Its factory encodes what
  a browser encodes, normalises to RFC 3986's equivalences and refuses what is not an absolute http(s) address of at
  most 2000 characters. It never throws; one parse-or-throw mapper per adapter turns its null into a defect
  (`HttpUrlMapper`, `HttpUrlModelMapper`, `ImportedAddress`).
- **Only `null` is no address.** A blank or refused address answers 400 `VALIDATION_ERROR` through `@HttpAddress` on
  every route, the media download route included, and is `FIELD_INVALID` on the import. `MEDIA_SOURCE_URL_INVALID`
  left the code, the contract and the clients. `HttpMediaFetcher` still checks the redirects it follows.
- **A person is a `PersonName` and a `Set<HttpUrl>`.** `PersonName` is a plain class whose equality folds ASCII case
  as `collate nocase` does (the exception `api/AGENTS.md` records). The wire answers a person's addresses sorted, with
  `uniqueItems`.
- **The archive lists every person in `persons.jsonl`** as `{id, name, urls, createdAt}`, and a pin line names its
  publisher and creators by that `id`. An unknown `id` creates the pin without that person and reports
  `PERSON_UNKNOWN`, which the web application shows in English and French.
- **Every other reference of the archive is an object holding its natural key**: `{name}` for a tag, a board and the
  manifest's `user`, `{url}` for a pin's collection. No record but a person line carries a database identifier, and a
  pin's and a board's recycling instant is `softDeletedAt`. `formatVersion` stays 2.
- **A person's name is refused by its own factory**: `@PersonNameText`'s validator calls `PersonName.parse`, as
  `@HttpAddress` calls `HttpUrl.parse`. Every address the contract answers declares `format: uri`, which
  `ContractSchemaDeclarationTest` holds. The import reads back its own user's persons alone (`findUserPersonsByIds`).
- **`ModelsPackageArchTest` refuses a value class in a persistence model**, against a fixture under
  `api-persistence-sqlite/src/test/resources/konsist/`.
- The contract is `25.0.0`. The last migration is still `1.35`.

## Evidence

| Block | Gate | Budget against its parent |
|---|---|---|
| 10 | `dagger call gate` green at `87188505` | 239 lines, 5 files |
| 20 | `./gradlew gate` green; `dagger call gate` read from the pre-push hook letting the push through | 493 lines, 74 files (file bound waived, answer H) |
| 30 | green at `50740014` | 193 lines, 35 files (waived) |
| 40 | green at `6575a797` | 89 lines, 17 files |
| 50 | green at the branch's tip | 341 lines, 34 files (waived) |
| 60 | green at `7d8c2379` | 390 lines, 19 files |
| 63 | green at `f7f7dbc9` | 9 lines, 7 files |
| 66 | green at `d7a4dd1b` | 230 lines, 19 files |
| 70 | green at `fec47b7d` | 199 lines, 15 files |
| 80 | `dagger call gate` green at `c12dcb6c` | 143 lines, 19 files |
| 85 | `dagger call gate` green at the branch's tip | 22 lines, 7 files |

- Block 10's mutation, pasted in `87188505`: an `HttpUrl?` property in `RemoteCollectionModel` fails the Konsist rule.
- `git grep -n MEDIA_SOURCE_URL_INVALID -- api contract clients` prints nothing (#385).
- The round trip (`MeImportRoundTripIntegrationTest`), collections and a person no pin credits included, comes back
  with an empty issue list at the stack's tip (#391), as the reports of #386, #387 and #390 found it at theirs.
- Block 66's headless reading of `PERSON_UNKNOWN`: 8 screenshots, two languages, two themes, two widths, no overflow.
- The 100 001-line `persons.jsonl` test runs in 0.44 s against an in-memory repository (#388).

## Departures from the specification

- **Block 60 split into 60, 63 and 66** at about 28 files (spec commit `c32b4324`), then handed `agents/data-modelling.md`'s
  exception and `ImportArchiveBuilder`'s `persons.jsonl` entry to block 66 at 20 files (`792ea1cc`).
- **Block 80 split into 80 and 85** at 25 files with the code findings alone: 80 the API's findings (#392), 85 the
  web application's flaky journey, `api/AGENTS.md` and the documents. The lead chose; the operator was not consulted.
- **The Konsist rule lives in `ModelsPackageArchTest`** (`api-persistence-sqlite`), beside the models' other rules,
  not in `ArchitectureKonsistTest`.
- **Block 20 measured 74 files** against the spec's upper bound of 59, and folded tests to get under 500 lines from 546.
- **`PersonInputDto.urls` declares `@Schema(type = ARRAY, implementation = URI::class)`**: `@Schema` has no type-use
  target, and meta-annotating `@HttpAddress` registered a bogus component (#387).
- **New code outside the plan**: `PersonLineImporter`, since `UserDataImportRunner` sits at detekt's `LargeClass`
  bound; `PersonRepositoryInterface.findPersonsByIds`, for decision F's read back by identifier. (Corrected: now
  `findUserPersonsByIds(user, ids)`, holistic finding 7.)
- **Block 70's export tests** find records by page address and board name, the identifier being gone.

Tier-1 fix: a malformed `Location` header made `URI.resolve` throw an uncaught `IllegalArgumentException`, retried as
`UNREACHABLE`; it is now `UrlNotAllowedException`, a permanent refusal (#385).

## Pitfalls

- **MockK hands a value class over unboxed**: `arg(n)` on an `HttpUrl?` parameter is a `java.net.URI`.
- **An `assertEquals` between a string and an `HttpUrl` compiles and fails at run time**; compare `.toString()`.
- **`"https://example.com"` reads back as `"https://example.com/"`.**
- **A REST-Assured body built from a domain `Person`** serialises `PersonName` and `HttpUrl` as objects:
  `IntegrationTest.replacePin` writes their text.
- **Kover counts a branch the code cannot reach**: a null check on a resolved redirect's scheme (#385), `PersonName`'s
  fold below `A` (#387), each step of `a?.let { b }?.let { c } ?: return` (#388), and `board?.name ?: name` on a name
  that is never null (#391). Each first gate run failed `koverVerify`.
- **`UserDataImportRunner` sits at detekt's `LargeClass` bound**: new import logic goes outside it.
- **`MeExportCompletionIntegrationTest` builds `UserDataExportBuilder` by hand**: a new constructor parameter goes
  there too.
- **The import report orders no issues a test can rely on**: compare kinds sorted, subjects as a set.
- **The import fixtures' person repository is an in-memory fake**: MockK recording 100 001 calls ran out of heap.
- **`gh stack submit` prints none of the pre-push hook's output**: read the gate from the push succeeding, or run it
  first.
- **A hand-edited message catalogue line can lose the space after its key**; `pnpm exec biome check --write` fixes it.
- **The rule against a value class in a model reads the file's imports**: a fully qualified `HttpUrl?` passes it.
  Accepted, ktfmt and the IDE importing. (Corrected: proposed as accepted, the operator's to decide; see the open
  question below.)
- **A flaky web application journey**: PR #390's first run failed "Given a name the account holds under another case,
  Then the tag it holds is offered", the query recording "L" rather than "Landscape"; the re-run passed. Unrelated to
  the block, and the closing block's to treat. (Corrected: treated in block 85. user-event's default `delay: 0` puts
  a `setTimeout(0)` between keys; on a loaded runner one key outlasts `useDebounced`'s 300 ms pause, the term "L" is
  asked, and the mock answers "landscape" to any term. Reproduced with a keyup listener busy for 400 ms; fixed by
  `typeInOneGo` (`delay: null`) at the five sites that count requests, `a6ca4cf3`.)
- **A constraint that restates a factory's rule drifts from it**: `@NotBlank` trims up to U+0020 where Kotlin's
  `isBlank` also counts U+00A0, so a name of a no-break space passed validation and answered 500. Call the factory.

## Tier-2 questions

- Blocks 20, 30 and 50 cannot hold the file bound. Answer H: their file bound is waived, their line bound strict.
- Block 60 did not fit 20 files. Answer: split into 60, 63 and 66.
- Block 60 reached 20 files. Answer: the modelling exception and the archive builder's entry move to block 66.

## Open question to the operator

- `ModelsPackageArchTest` resolves a property's type through the file's imports, so a fully qualified type escapes it.
  The lead proposes accepting that limit, as `api/AGENTS.md` now records it. (Corrected: the operator accepted it on
  2026-10-10, after the merge.)

## The holistic review

`.reviews/addresses-and-people-take-their-shape-holistic.md`, over
`git diff lot/0.53.0-data-shapes-are-decided-in-the-spec..origin/feat/the-archive-names-by-key`: 0 CRITICAL, 1 MAJOR,
9 MINOR. It found every case the specification asks for in the diff. Each finding and its exit:

1. MAJOR, a name of Unicode spaces passed `@NotBlank` and answered 500: fixed in block 80, `@PersonNameText` calls
   `PersonName.parse`; a name of U+00A0 now answers 400 `VALIDATION_ERROR`.
2. A remote collection's `url` and a person's `urls` declared no `format: uri`: fixed in block 80, with a
   `ContractSchemaDeclarationTest` case. The contract stays `25.0.0`, an output gaining a format breaking no client;
   the specification's Data shape table is aligned in block 85.
3. A host holding an underscore is refused: an accepted limit, observed by `HttpUrlTest` in block 80 and recorded in
   the specification's section 7 in block 85. The operator agreed on 2026-10-10: host names (RFC 952, RFC 1123) hold
   no underscore.
4. `PersonCreator`'s comment still said the import stamps its own instant: fixed in block 80.
5. The 2026-10-08 specification's `Status:` line missed two halves of its decision D, "stamped with the import
   instant" and a person's fault refusing the pin line: fixed in block 85.
6. `api/AGENTS.md` said the Konsist test was still to land: fixed in block 85, naming `ModelsPackageArchTest` and its
   limit.
7. `findPersonsByIds` read persons with no account scope: fixed in block 80 as `findUserPersonsByIds(user, ids)`;
   `PersonRepository` skips the query on an empty set, the runner being at detekt's `LargeClass` bound.
8. The archive's README did not say a pin names its collections by `url`: fixed in block 80, with an
   `ExportReadmeTest` case.
9. The round trip compared no person's `createdAt`, and `PinFacts.deletedAt` kept the retired name: fixed in block 80
   (`PersonFacts.createdAt`, `PinFacts.softDeletedAt`).
10. Two comments pointed at a decision letter rather than saying why: fixed in block 80.

## What is not validated

- The holistic review, still to come at the head of Wrap. (Corrected: it ran; see The holistic review.)
- A person stored before this lot whose name is Unicode spaces alone still cannot be read: rows are reset, not healed.
- The web application by hand against the new 400 on a whitespace-only source address and on a download request;
  its fields are `type="url"`, and its gate is green.
- A real third-party archive: third-party person ids are covered by unit tests only.
- A real archive of 100 000 persons through the import's in-memory map.
- `api/config/detekt/baseline-api-fetch-http-main.xml` names an `openStream: InputStream` that no longer exists.

## The lot's counts

- Fix-backs: 0. Fixes on top: 0. Cascaded rebases: 0. Runs re-triggered by them: 0. One run re-run by hand, PR
  #390's flaky journey.
- `gh stack merge` merged #382 to #391 alone: #392 and #393, opened with `gh pr create`, were not members of the
  stack on GitHub. Both were rebased onto `main` and merged after, two runs re-triggered. Open a split's pull request
  with `gh stack submit`.
- The operator's reading of the bodies: no remark on the bodies. One code comment, on #383's `HttpAddress`: why null
  is valid. Answered without a change: the field's type decides absence, the constraint checks the address alone.

## Next step

The holistic review, then the closing block 80: its findings, the flaky journey, the documents' `Status:` lines and
the backlog (specification, block 80). After the merge and the tag `lot/0.54.0-addresses-and-people-take-their-shape`,
ADR 0056's foreign keys lot, then the third-party importer. (Corrected: the review and the closing blocks 80 and 85
are done, and the operator reviewed the stack, #382 to #393. The open question waits for the operator.)
