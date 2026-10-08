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
notes; to be corrected by the closing block. (Corrected: the stack ends with three more blocks. 130
`fix/the-people-lot-closes` (#378) fixes the holistic review's code findings; 135 `feat/the-fields-hold-their-chips`
gives the creators and the tags their chip field, after the operator's review of #375; 140
`docs/the-people-lot-is-recorded` corrects this file, the specification, ADR 0055 and the backlog.)

## Current state

- **A person is stored per owner** (10): migration `1.33`, `persons`, unique on `(author_id, name collate nocase,
  urls)`. `PersonUrls` is the canonical form of the addresses (sorted, distinct, line-feed joined, empty for none);
  `PersonCreator.findOrCreate` finds or creates in one transaction. (Corrected: after the operator's review of #364,
  `Person.urls` is a plain list and `PersonUrls` is gone; the column holds a sorted, distinct JSON array as text, `[]`
  for none, which `PersonModelMapper.canonicalUrls` produces for every write and the lookup.)
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
  button. `PinDialog` shows the people and the instant. (Corrected: after the operator's reviews of #375, the
  publisher is a single-value combo box, `PublisherField`, cleared with an X button (100); the creators and the tags
  share `ChipField`, the chosen values as chips inside the field, which says that Enter adds one (135); the date's
  clear button is an X shown only while a date is set (110).)
- **`HttpMediaFetcherTest`'s close-delimited stall case holds** (120), below.
- **The holistic review's findings are fixed** (130, 140), below: an import now counts the boards its collection
  walk creates in `createdBoards`.

## Evidence

- Block 10: `dagger call gate` green at `19d6025b`; budget 473 lines, 13 files against `main` (#364).
  (Corrected: then the fix-back `4e6a725f` on the operator's review, below.)
- Block 20: gate green at `62160dc0`, then after the fix-back `b5a2316d`; budget 314 lines, 15 files (#365).
- Block 30: gate green at `cf4ee86b` (Corrected: rebased unchanged as `9eab50f1`, `git range-diff` showing `=`);
  budget 349 lines, 19 files (#367). `oasdiff changelog`
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
  French (scratchpad `read100/`). (Corrected: reworked after the operator's reviews, below, as the single
  commit `9634256a`: 423 lines, 12 files against `feat/the-board-shows-its-collections`.)
- Block 110: gate green at `cbac741b`; budget 256 lines, 9 files (#376). Read headless the same way, 24 screenshots
  (scratchpad `read110/`); the conversion tests pass with the host at `TZ=America/New_York`. (Corrected: rebased
  onto the reworked block 100 as `f0ae5851`, with the clear button an X shown only while a date is set and grouped
  with the time field, so a phone wraps them together: 265 lines, 9 files.)
- Block 120: gate green at the branch's tip; budget 3 lines, 1 file against `feat/the-form-dates-a-pin`. The stall case
  repeated 100 times in one JVM (`@RepeatedTest`, not committed) under twelve busy loops on twelve cores: 2 failures
  before the fix, each `HttpTimeoutException` in `send` (`repro-before.log`). After it, three runs of 300, 300 and
  600: 1199 of 1200 passed, the slowest 1.022 s (`repro-after2.log`, `repro-after3.log`). The one failure, in the
  first run (`repro-after.log`), is of another kind, below. Logs in the session's scratchpad. (Corrected: rebased
  unchanged with every cascade, still 3 lines, 1 file.)
- Continuous integration green on #364 to #376 (`gh pr view <n> --json statusCheckRollup`, 2026-10-08).
  (Corrected: and on #377, read the same way in block 130.)
- Block 130: gate green at `68c7fe9e` (`gate130d.log`), the code findings alone after every rebase; budget 90 lines,
  15 files against `fix/the-stall-test-holds` (#378). It leaves `contract/openapi.json` unchanged. The board page read
  headless as in block 90, the stub answering four collections in the server's order, an accented name last
  (scratchpad `read130/`).
- Block 135: gate green at `7e6bc893`; budget 284 lines, 9 files against `fix/the-people-lot-closes`. Read headless
  in English and French, light and dark, 390x844 and 1280x800, 24 screenshots (scratchpad `read135/`).
- Block 140: `dagger call prose` green at the branch's tip; budget 2 lines, 1 file against
  `feat/the-fields-hold-their-chips`, the backlog alone, the dated documents being outside the count.

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
  (Corrected: `PublisherField.tsx` (100) and `ChipField.tsx` (135); `CreditFields.tsx` is gone.)
- **HeroUI's `Tag` accepts `variant` and ignores it**: only `TagGroup`'s is read, and passed to each chip (135).
- **Native date and time controls follow the browser's locale, not the interface's**; clearing the time alone sets
  it back to 00:00 (110). `firefox --headless --screenshot` captures before a query answers (90).
- **SmallRye publishes one `pattern` per string**: of `@NotBlank` and `@Pattern` on one list element, only the
  `@Pattern` reached the contract, which hid the blank refusal until block 10's fix-back dropped it (30, 130).

## Departures from the specification

- Block 10: the creation column is `when_created`, as every authored model's; the canonical form is a type,
  `PersonUrls`; decision A's bounds are enforced at the edges, blocks 30 and 60. (Corrected: `PersonUrls` left
  with the fix-back; the canonical form is the persistence adapter's.)
- Block 20: `pins.publisher_id` has no index.
- Block 30: `PersonReference` is the use case's input; the line-feed refusal is an anchored `@Pattern`. (Corrected:
  the rule left with block 10's fix-back, the JSON form escaping a line feed; an address carries `@NotBlank` alone.)
- Block 40: the prefix-then-contains query is copied from `TagRepository`, and the person search's integration cases
  live in `TagSearchIntegrationTest`. (Corrected: renamed `TagAndPersonSearchIntegrationTest` in block 130.)
- Block 60: `PersonCreator` takes the import instant through an overload, a default argument failing (pitfalls).
- Block 70 split at its first commit, at 748 lines over 20 files, into 70 (storage and deletions) and 75 (the import);
  one teammate wrote both, the code having been written before the measurement.
- Block 75: `ImportFieldBounds.addressFault` is shared by persons and collections.
- Block 100: the fields live in `components/CreditFields.tsx`; `TAG_SUGGESTIONS` became `SUGGESTIONS`. (Corrected:
  after the rework, the publisher is a combo box in `PublisherField.tsx`, and the creators are sent as read.)
- Block 120: not in the specification, added by the operator mid-lot.
- (Corrected: blocks 130, 135 and 140 split the closing work after the operator's reviews of #375, below. Block 135
  started from block 100's teammate's patch, written by hand onto the form, the patch tool being refused by the
  repository's hook; `personNamed` moved to `lib/persons.ts`, shared by both people's fields. Block 130 renamed
  `TagSearchIntegrationTest` rather than open a new `@QuarkusTest` class, as `agents/engineering.md` asks.)

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

## The holistic review

`.reviews/the-pin-credits-its-people-holistic.md`, over
`git diff lot/0.51.0-ktfmt-formats-the-kotlin-code..origin/fix/the-stall-test-holds`: 0 CRITICAL, 1 MAJOR, 11 MINOR.
It found every decision of the specification in the diff, and judged the lead's two notes: the copied query
acceptable, the test class's name a finding; blocks 70 and 75 split cleanly. Every finding was fixed, the code in
block 130 and the documents in block 140:

- MAJOR, a board the collection walk creates was counted nowhere: `RemoteCollectionLinker.link` says whether it
  created the board, and the walk adds it to `createdBoards`. `UserDataImportCollectionsTest` asserts the count.
- Block 120's acceptance claimed no residual failure: a `(Corrected: ...)` in the specification records 1199 of 1200;
  the cause stays under what is not validated.
- `PinModel`'s reason for no index on `publisher_id` was false since block 50: it now names the scan the catalogue
  filters inside.
- Four comments sent the reader to another class: each states its reason in one line.
- "the spec's decision" named no document beside the import's own specification: the API's comments say
  "specification 2026-10-08", the two import test classes included.
- The contract did not show that a blank address is refused: closed by block 10's fix-back, which dropped the
  line-feed `@Pattern`, so `@NotBlank` alone publishes `\S`. Block 130's own pattern was dropped in the rebase.
- The board page sorted the collections again with another collation: it shows the server's order, and the journey
  asserts it as answered; a `(Corrected: ...)` on block 90's journey bullet.
- A person invented by a refused `PUT` was not shown rolled back: the existing tag case sends a new publisher too and
  counts no `persons` row.
- The person search's cases were not findable by the route's name: the class is `TagAndPersonSearchIntegrationTest`.
- ADR 0055 named no specification: a `(Corrected: ...)` names this one.
- Block 30's evidence named a commit no branch holds: corrected above.
- `instantOf` threw on a five-digit year: it returns null, and the date field ignores the change.

## The operator's review

- #364, on `PersonUrls`: « Pourquoi le domaine fait du parsing pour la DB ? Le domaine ne doit pas se soucier des
  couches externes, une personne doit avoir une `List` d'URLs dans ce cas, pas un objet custom. »
- #364, on `PersonModel.urls`: « Pourquoi pas utiliser un array, ou du json ? » SQLite has no array type, so the
  addresses are a JSON array held as text, which the unique index still compares as one value and which escapes a
  line feed, so the rule against one went too.
- Both went to block 10 as the fix-back `4e6a725f`: `Person.urls` a plain list, `PersonUrls` deleted, the canonical
  form and its serialisation in `PersonModelMapper`, the line-feed rule dropped from `PersonInputDto` and the import's
  bounds. The specification and ADR 0055 carry it as `(Corrected: ...)`. The lead cascaded it: every branch from 20
  to 130 was rebased, 130 resolving `PersonInputDto.kt` and `contract/openapi.json` in favour of its new parent.
- #375, on the creators field: « Dans la UI, le champ "creators" est confus. Le texte est au pluriel, mais rien
  n'indique comment créer plusieurs créateurs, il faudrait amener un système de champ multi-string, possiblement
  comment on fait pour les tags. » Asked for one multi-value component shared by tags and people, chips inside the
  field with the hint « Entrée pour ajouter », the operator answered « a ok ». Asked, once block 100 passed the line
  bound, whether block 100 keeps it for the people and the closing block moves the tags, « a ok ». The fix-back
  `e7f20e5b`, no longer on the stack, put the publisher and the creators on `ChipField`, at 454 lines, and the lead
  cascaded it.
- #375, on that fix-back: « Tu as utilisé le champ multi valeur pour Publisher, qui est mono-valeur, c'est une
  erreur. » The error was the lead's: its brief put the publisher, one person, on the multi-value field. The
  operator chose the lead's recommended plan (« reco ok »): block 100 keeps only the publisher's single-value combo
  box and the dialog, the chip field for the creators and the tags becomes block 135, and the closing work splits
  into 130 (the code findings) and 140 (the documents), on top of 135.
- Block 100 was reworked as the single commit `9634256a`, 423 lines; 110 was rebased onto it as `f0ae5851`, its
  clear button now an X shown only while a date is set and grouped with the time field for a phone; 120 was rebased
  unchanged; 130 was cut back to its code commit, `68c7fe9e`, keeping 110's field and `instantOf`'s null; 135,
  `7e6bc893`, carries the chip field; 140 carries the documents, cherry-picked from 130.

## What is not validated

- The web application against the running API: blocks 90, 100 and 110 were read against a stub. (Corrected: and
  130 and 135.)
- Chrome and Safari's native date and time controls (110).
- The interface's new labels, in English and French, which the operator has not read (100, 110).
- A third-party archive, which only the next lot produces (60, 75).
- The catalogue search's query plan with persons in volume (50).
- A body omitting the nullable `publisher` or `publishedAt`, as for `sourceContextUrl` before (30).
- One repetition of the stall case after the fix passed its 5 s deadline under load, the read having already thrown
  and the thread closing the stream (120). Not reproduced in the 900 repetitions after it, 600 of them timed phase by phase; its cause is
  unknown, and the deadline was left as it is.
- An imported `publishedAt` past the year 9999, restored unclamped, gives the date field a day it cannot show (130).

## The lot's counts

- Fix-backs: 1, block 20's comments. (Corrected: FIXBACKS.)
- Cascaded rebases: 1, blocks 20 and 30. (Corrected: CASCADES.)
- Runs that cascade re-triggered: 2. (Corrected: RUNS. Already 16 before the publisher's rework, 2 by the first
  cascade and 14 by the push of the second and third, every branch having moved, which passes the lot's 14 blocks,
  ADR 0043's failure criterion: the operator's fix-backs landed on blocks 10 and 100, at the bottom of the stack, so
  each rebased nearly every branch above.)
- The operator's reading of the bodies: to be filled before the stack merges. (Corrected: no remark.)

## Next step

Wrap: the holistic review over `git diff lot/0.51.0-ktfmt-formats-the-kotlin-code..origin/fix/the-stall-test-holds`,
then the closing block, then the operator's review and `gh stack merge --rebase`, and the tag
`lot/0.52.0-the-pin-credits-its-people`. (Corrected: the review and the closing blocks 130 and 140 are done. The
operator reviews the stack, #364 to block 140's pull request; after the merge and the tag, the importer, ADR 0055's
second lot, gets its specification.)
