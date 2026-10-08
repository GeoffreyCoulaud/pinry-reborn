# Handoff: the pin credits its people

Date: 2026-10-08
Tier: Spec. Specification `docs/specs/2026-10-08-the-pin-credits-its-people.md`; ADR
`docs/adr/0055-third-party-imports-write-the-user-data-archive.md` (#363), decisions 7, 8 and 11. Specification review
`.reviews/the-pin-credits-its-people-spec.md`.
Lot `0.52.0`, one stack of 13 code blocks, where the specification planned 11: 10 `feat/a-person-is-stored` (#364),
20 `feat/the-pin-holds-its-people` (#365), 30 `feat/the-api-credits-people` (#367), 40
`feat/the-api-searches-people` (#368), 50 `feat/the-catalogue-finds-people` (#369), 60
`feat/the-archive-carries-people` (#370), 70 `feat/the-import-links-collections` (#371), 75
`feat/the-import-walks-collections` (#372, block 70's split), 80 `feat/the-export-carries-collections` (#373), 90
`feat/the-board-shows-its-collections` (#374), 100 `feat/the-form-credits-people` (#375), 110
`feat/the-form-dates-a-pin` (#376), 120 `fix/the-stall-test-holds` (the pull request this file arrives in, added by
the operator mid-lot). Written in block 120 from the block reports collapsed in those pull requests and the lead's
notes; to be corrected by the closing block.

## Current state

- **A person is stored per owner** (10): migration `1.33`, `persons`, unique on `(author_id, name collate nocase,
  urls)`. `PersonUrls` is the canonical form of the addresses (sorted, distinct, line-feed joined, empty for none);
  `PersonCreator.findOrCreate` finds or creates in one transaction.
- **A pin holds a publisher, creators and a publication instant** (20): migration `1.34`, `pins.publisher_id`,
  `pins.published_at` and `pin_creator_model`. Each pin deletion path deletes its creator rows; account deletion
  deletes the user's persons after their pins. A person no pin names stays.
- **The API credits them** (30, 40), contract `24.0.0` then `24.1.0`: `PinOutputDto` and `PinUpdateInputDto` carry
  `publisher`, `creators` and `publishedAt`, a person named `{name, urls}`; `GET /api/v1/persons/search` mirrors the
  tag search, homonyms coming back as two results.
- **The catalogue's `q` finds a pin by its people's names, and the duplicate merge carries them** (50): the union of
  creators, a null publisher and date filled from the oldest absorbed pin.
- **The archive carries them** (60): each pin line names its people inside it, `publishedAt` restored unclamped,
  `formatVersion` still 2. `PersonCreator` gained an overload taking the import instant.
- **A remote collection links to a board** (70, 75, 80, 90): migration `1.35`, `remote_collections`, unique on
  `(author_id, url)`. The import walks `collections.jsonl` after the boards and a pin line's `collections` joins
  their boards; the export writes every collection with its board's name; `BoardOutputDto.remoteCollections`,
  contract `24.2.0`, shown read only under the board's description.
- **The form credits people and dates a pin** (100, 110): a publisher and a creators field with suggestions from the
  person search, in `components/CreditFields.tsx`; a native date and time field with the browser's zone and a clear
  button. `PinDialog` shows the people and the instant.
- **`HttpMediaFetcherTest`'s close-delimited stall case holds** (120), below.

## Evidence

- Block 10: `dagger call gate` green at `19d6025b`; budget 473 lines, 13 files against `main` (#364).
- Block 20: gate green at `62160dc0`, then after the fix-back `b5a2316d`; budget 314 lines, 15 files (#365).
- Block 30: gate green at `cf4ee86b`; budget 349 lines, 19 files (#367). `oasdiff changelog`
  (`tufin/oasdiff:v1.31.0`) from `main`: 3 errors, the three new required request properties, 27 infos;
  `info.version` `24.0.0`.
- Block 40: gate green at `11e4af6a`; budget 388 lines, 14 files (#368). `oasdiff changelog` from its parent: 5
  infos, `endpoint-added` and four `request-parameter-became-optional`; `info.version` `24.1.0`.
- Block 50: gate green at `ac6d7a62`; budget 142 lines, 8 files (#369).
- Block 60: gate green at `682823fd`; budget 377 lines, 14 files (#370).
- Block 70: gate green at `1778630f`, its first run red on the stall case block 120 fixes; budget 312 lines, 11
  files, after a split measured at 748 lines over 20 files (#371).
- Block 75: gate green at `0ff8804b`; budget 436 lines, 9 files (#372).
- Block 80: gate green at `861a3bce`; budget 153 lines, 13 files (#373).
- Block 90: gate green at `ada7fcc9`; budget 218 lines, 18 files (#374). Read headless in Firefox over WebDriver
  BiDi against a Node stub API, light and dark, 390x844 and 1280x800 (scratchpad `read90/`).
- Block 100: gate green at `4236e5a2`; budget 385 lines, 12 files (#375). Read headless the same way, in English and
  French (scratchpad `read100/`).
- Block 110: gate green at `cbac741b`; budget 256 lines, 9 files (#376). Read headless the same way, 24 screenshots
  (scratchpad `read110/`); the conversion tests pass with the host at `TZ=America/New_York`.
- Block 120: gate green at the branch's tip; budget 3 lines, 1 file against `feat/the-form-dates-a-pin`. The stall case
  repeated 100 times in one JVM (`@RepeatedTest`, not committed) under twelve busy loops on twelve cores: 2 failures
  before the fix, each `HttpTimeoutException` in `send` (`repro-before.log`). After it, three runs of 300, 300 and
  600: 1199 of 1200 passed, the slowest 1.022 s (`repro-after2.log`, `repro-after3.log`). The one failure, in the
  first run (`repro-after.log`), is of another kind, below. Logs in the session's scratchpad.
- Continuous integration green on #364 to #376 (`gh pr view <n> --json statusCheckRollup`, 2026-10-08).

## Pitfalls

- **Jetty's client drops a response that arrives before its request is sent**, parsing it as unsolicited
  (`HttpReceiverOverHTTP`, 12.1.13). A test origin written on a bare socket must read the request first; a
  close-delimited body then never ends and the fetch times out in `send` (120).
- **`foreign_keys` is off** (backlog item), so a declared key refuses nothing: a row left behind by a deletion fails
  no test on its own, and the tests count rows (20, 70).
- **A Kotlin default argument reading an injected field breaks through a CDI client proxy**: the static `$default`
  reads the proxy's null field, which only a `@QuarkusTest` shows (60).
- **A JAX-RS query parameter in Kotlin without `= null` is published `required: true`** by SmallRye, whatever its
  nullability (40).
- **`Pin` holds three more fields**: a read path building a `Pin` without its creators would let the next save erase
  them; every read in `PinRepository` goes through `loaded()` or `findPinsByIds` (20). Every test writing a pin over
  `PUT` sends the three fields (30).
- **`1.34.sql` was edited after generation**: the generator's unsupported foreign key line became an inline
  `references` on `add column` (20).
- **detekt refuses a test helper of more than 5 parameters, a KDoc over 4 lines, a third `?: return`, and a test name
  over 120 columns** (50, 75, 80, 10, 90). `./gradlew spotlessApply detektMain detektTest` costs a minute rather than
  a gate.
- **`BaseTest`'s `checkUnnecessaryStub` fails a default `@BeforeEach` stub** a case never uses (80).
- **Biome's `noExcessiveLinesPerFile` (300) caps `PinEditForm.tsx`**: the credit fields live in their own file (100).
- **Native date and time controls follow the browser's locale, not the interface's**; clearing the time alone sets
  it back to 00:00 (110). `firefox --headless --screenshot` captures before a query answers (90).

## Departures from the specification

- Block 10: the creation column is `when_created`, as every authored model's; the canonical form is a type,
  `PersonUrls`; decision A's bounds are enforced at the edges, blocks 30 and 60.
- Block 20: `pins.publisher_id` has no index.
- Block 30: `PersonReference` is the use case's input; the line-feed refusal is an anchored `@Pattern`.
- Block 40: the prefix-then-contains query is copied from `TagRepository`, and the person search's integration cases
  live in `TagSearchIntegrationTest`.
- Block 60: `PersonCreator` takes the import instant through an overload, a default argument failing (pitfalls).
- Block 70 split at its first commit, at 748 lines over 20 files, into 70 (storage and deletions) and 75 (the import);
  one teammate wrote both, the code having been written before the measurement.
- Block 75: `ImportFieldBounds.addressFault` is shared by persons and collections.
- Block 100: the fields live in `components/CreditFields.tsx`; `TAG_SUGGESTIONS` became `SUGGESTIONS`.
- Block 120: not in the specification, added by the operator mid-lot.

Tier-1 fixes: block 40, the `= null` defaults on `TagSearchController`'s `q` and `limit` and `MediaController`'s
`size` and `animated`, which the contract read as required; block 50, the repository's `@param query` and the merge
operation's description; block 80, the README's JSON Lines paragraph no longer naming the files; block 100,
`PinDialog`'s source link reading its host through `hostOf`.

## Tier-2 questions

- Discuss settled the specification's decisions with the operator.
- Block 20's report found the comments in `PinRepositorySoftDeleteTest` claiming a foreign key violation would fail
  the test, false with `foreign_keys` off. Answer: « oui on corrige les commentaires qui sont faux au passage, comme
  proposé ». Fixed as a fix-back, `b5a2316d`.
- Block 70's gate failed once on `HttpMediaFetcherTest`'s stall case. Answer: « Oui, je suis pour qu'on fasse un bloc
  supplémentaire qui sert à fiabiliser le test flaky. » Block 120.

## For the holistic review

The lead's notes, for the review to judge:

- Block 40 copied the tag search's "prefix then contains" query instead of sharing it, and put the person search's
  integration cases in `TagSearchIntegrationTest`.
- Blocks 70 and 75 were written by one teammate, against the fresh-teammate-per-block rule; the lead's brief allowed
  it.

## What is not validated

- The web application against the running API: blocks 90, 100 and 110 were read against a stub.
- Chrome and Safari's native date and time controls (110).
- The interface's new labels, in English and French, which the operator has not read (100, 110).
- A third-party archive, which only the next lot produces (60, 75).
- The catalogue search's query plan with persons in volume (50).
- A body omitting the nullable `publisher` or `publishedAt`, as for `sourceContextUrl` before (30).
- One repetition of the stall case after the fix passed its 5 s deadline under load, the read having already thrown
  and the thread closing the stream (120). Not reproduced in the 900 repetitions after it, 600 of them timed phase by phase; its cause is
  unknown, and the deadline was left as it is.

## The lot's counts

- Fix-backs: 1, block 20's comments.
- Cascaded rebases: 1, blocks 20 and 30.
- Runs that cascade re-triggered: 2.
- The operator's reading of the bodies: to be filled before the stack merges.

## Next step

Wrap: the holistic review over `git diff lot/0.51.0-ktfmt-formats-the-kotlin-code..origin/fix/the-stall-test-holds`,
then the closing block, then the operator's review and `gh stack merge --rebase`, and the tag
`lot/0.52.0-the-pin-credits-its-people`.
