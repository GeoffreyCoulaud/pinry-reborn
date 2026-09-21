# Handoff: the header searches and wears the name

Date: 2026-09-21
Specification: `docs/specs/2026-09-21-the-header-searches-and-wears-the-name.md`
ADRs: `docs/adr/0040-search-is-a-parameter-of-the-catalogue.md`
Blocks: 10 `feat/the-catalogue-takes-a-query` (#182), 20 `feat/the-pin-search-route-goes` (#183),
25 `feat/the-tag-field-pauses` (#185), 30 `feat/the-tags-match-in-sql` (#184), 35
`feat/the-similarity-goes` (#186), 40 `feat/the-header-wears-the-name` (#187), 50
`feat/the-header-searches` (#188). Written in the last code block, and corrected in the closing
block `fix/the-holistic-findings`.
Tier: Spec. The specification review ran before a line was written: 0 CRITICAL, 6 MAJOR, 9 MINOR,
all closed in the document. The holistic review ran at the head of Wrap: 0 CRITICAL, 4 MAJOR,
6 MINOR, every one of them fixed in the closing block `fix/the-holistic-findings`, which is where
this document is corrected.

## Current state

**Search is a parameter of the catalogue, on both sides.** `GET /api/v1/pins` and
`GET /api/v1/boards/{boardId}/pins` take an optional `q`, matched in SQLite over the description
and the tag names; `GET /api/v1/pins/search` and every line of similarity scoring are gone;
`GET /api/v1/tags/search` matches in the database too. The header carries the name of the
application and a search field, and the field writes its term into the address of the screen it
is on.

- **One predicate, two routes.** `QPinModel.matchingText` in `queries/PinQueries.kt`:
  `description contains q OR id in (select pin id from pin tag where tag name contains q)`, built
  with `or()`/`endOr()` so the cursor's own predicates are ANDed at the root. No index, no accent
  folding, no relevance: the order stays the user's, through the grid's selector.
- **An absent `q` is the whole catalogue; a present and blank one earns 400** under the `code`
  `SEARCH_EMPTY_QUERY`, thrown by the use case, on the tag route too. `@NotBlank` refuses null,
  which is the absent parameter, so no annotation could express it. The three routes that take a
  `q` declare that 400 in the contract, under one shared description, `BLANK_QUERY_REFUSED`.
- **The tag suggestions are two bounded queries**: the names beginning with the term, then the
  names merely holding it, less what the first held. No `score` in the body.
- **The contract stands at `9.1.0`**, one major per block that broke it: `7.3.0` for `q`, `8.0.0`
  for the pin search route's removal, `9.0.0` for the tag body losing `score`. (Corrected in the
  closing block on 2026-09-21: it read `9.0.0`, and the closing block's own three declared 400s
  are the additive `9.1.0`.)
- **The header's `<h1>` is the application's name and the way home.** The house icon left
  `AppNav`; a screen with a title of its own renders it as an `<h2>` beside the name, with a `·`
  between them. The credentials screen carries the name and no search field.
- **The search field writes `q` after a 300 ms pause, with `replace: true`, and reads it back.**
  The address is the state: a reload and a bookmark keep the search with nothing stored, and an
  address the field did not write is adopted, which is what makes the application's name the way
  out of a search. ~~the back button keep the search with nothing stored, and one history entry is
  kept per search rather than one per keystroke~~ On a board the field searches that board and a
  link under it offers the same term over everything. (Corrected in the closing block on
  2026-09-21: the struck clause is refuted. Every write replaces, the first one included, so
  there is one history entry per screen visit and none per search: the back button leaves the
  screen rather than stepping out of the search. That is the behaviour, not a defect the closing
  block left standing, and the specification's decision K carries the same correction.)
- **An empty result names the term.** `pins_empty` states an empty account, which is false of a
  search that matched nothing, and the recourse differs.

## Where the pieces live

- `api/api-persistence-sqlite/.../queries/PinQueries.kt`: the text predicate, as an extension on
  `QPinModel`. It is there and not in `PinRepository.kt` because `ArchitectureKonsistTest` refuses
  a recyclable query bean named outside `queries/` and `pagination/`.
- `api/api-persistence-sqlite/.../repositories/TagRepository.kt`: the prefix query and the
  contains query, and the comment saying why neither is `istartsWith` or `icontains`.
- `clients/apps/webapp/src/components/SearchField.tsx`: the input, the navigation and the widening
  link. It renders nothing on a screen that does not pass it, `AppHeader` holding only the slot.
- `clients/apps/webapp/src/lib/searches.ts`: `searchTermOr`, which turns whatever the address bar
  holds into a term or nothing. Read by the route validator and by the field, so the client never
  sends the blank `q` the API refuses.
- `clients/apps/webapp/src/debounce.ts`: `useDebounced(value, delay)`, used by the search field and
  by the edit form's tag field. It holds a timer and state, so it is not in `lib/`. The 300 ms is
  its own default, stated once here, neither caller passing a delay.

## Block 25, which did not exist when the specification was written

The lot was specified with six blocks. Block 25 was added mid-lot, on 2026-09-21, out of a tier-2
question block 10 raised: no field in the web application debounced anything, and the edit form's
tag field fired one request per character. Decision P had folded that fix into block 50, which was
writing a pause anyway; the operator pulled it into a block of its own so the defect would be
fixed where it lives rather than as a passenger of the search field.

**It then refuted its own premise.** The block had been argued as the root-cause fix for a journey
case that had timed out twice at 5000 ms in continuous integration. A probe measured the nine
requests at 34 ms of a 653 ms case, and the pause itself at 319 ms: the case got *slower*. What
dominates it is opening the dialogue, 260 ms, which no block of this lot touches. The operator's
answer, asked at the moment of discovery, was to keep the pause for what it actually does, one
request per typed tag instead of nine, and to raise the suite's `testTimeout` to 15000 ms as the
separate fix for the timeout (decision Q). Section 5 of the specification carries the table.

## The holistic review's findings, and the exit each one took

Ten findings, 0 CRITICAL, 4 MAJOR and 6 MINOR. **All ten were fixed inside the lot**, in the
closing block; none was refused, backlogged or accepted as a limit.

| Finding | Exit |
|---|---|
| MAJOR, the search field could not be cleared by the application's name, and adopted no address it had not written | Fixed. The field keeps the term it last wrote in a `useRef` and adopts `term` when the two differ. A journey case joins `search from the header`, and the reading below is what shows it in a browser |
| MAJOR, the new 400 was in no operation's contract | Fixed. `GET /api/v1/pins`, `GET /api/v1/boards/{boardId}/pins` and `GET /api/v1/tags/search` declare it, the contract regenerated at `9.1.0` |
| MAJOR, `commons-text` outlived `TextSimilarity` | Fixed. The three build lines are gone |
| MAJOR, `limit` on the tag route had lost its lower bound | Fixed, and at the root rather than only at the edge: `TagRepository.findTagsForUserMatching` serves nothing for a non-positive limit, and the controller clamps with `coerceIn(MIN_LIMIT, MAX_LIMIT)` as the catalogue's `pageSize` does |
| MINOR, the tag subquery was not scoped to the reader | Fixed. `matchingText` takes the reader, and the subquery filters on `tag.author.id` |
| MINOR, one pause declared as two constants with the same comment twice | Fixed. `useDebounced`'s `delay` defaults to 300 and both callers ask for no delay |
| MINOR, a journey stub served a `score` the contract no longer declares | Fixed. The field is out of the stub |
| MINOR, `replace: true` on every write, against what three documents claimed | Fixed in the documents, the behaviour being the one that was wanted: this file and the specification's decision K both say one entry per screen visit now |
| MINOR, the plan case bound three parameters by position | Fixed. The binds come from the statement's own `?` count, which is what let the author scope above be added without the case throwing |
| MINOR, `boardQuery` read as a value and was a builder | Fixed. `findActivePinsForBoard` chains in one expression, as `findPinsForUser` does |

## Pitfalls

- **An `or()` left unclosed swallows the cursor's predicates**, and the test that catches it is the
  *second* page of a search, not the first. Removing `.endOr()` fails exactly one test.
- **A grep on a type's name finds files that are not callers.** `SearchResultMapper` and
  `TagSearchResultOutputDto` merely contain the substring. Reading the imports is what separates
  them; the compile is the other half.
- **A grep for `PinSearch` does not find every caller of a route.** One integration test used the
  literal path. `git grep "pins/search"` is the one that finds them.
- **A detekt baseline outlives the file it names, and says nothing.** Nothing in the gate reports a
  stale entry, so entries are removed with the deletion or never.
- **Count the requests, and measure the duration before blaming them for it.** Block 25's whole
  lesson, and it cost a block decided on the opposite belief.
- **`contains` and `startsWith`, never `icontains` or `istartsWith`.** Ebean's case-insensitive
  operators render `lower(column) like ?` with the bind lowercased in Java, a Unicode-aware fold
  against SQLite's ASCII-only `lower()`, so the two sides disagree on a non-ASCII name.
- **The credentials screen carries the application's name**, so `m.app_name()` no longer says a
  session opened. A test reaching for "we are past sign-in" wants the home screen's own control.
- **The home header has seven tab stops now**, the search field being the second. The task centre's
  journey counts them and breaks on any control added to the bar.
- **The gate renders nothing and sees no layout.** Every client block of this lot but block 25 was
  read in a headless browser against the built bundle, and that reading caught a defect in both.
- **A declared `@APIResponse` silences the generated one.** SmallRye stops deriving the success
  response as soon as an operation declares any response of its own, so adding the 400 alone took
  the `200` off all three routes: the first regeneration was 13 insertions against 34 deletions,
  a break dressed as an addition. The 200 is written out beside it.
- **A field that writes the address has to read it too.** The route component stays mounted across
  a search-only change of the address, so state seeded once at mount is state that fights every
  navigation the field did not originate. The journey that catches it changes the address from
  outside while the field is mounted; one that mounts fresh on the address passes either way.

## What is not validated

- **No browser is in the gate.** The headless reading is a workstation habit, not a check. Block 25
  was not read at all, deliberately: its diff is an import, a constant and one variable, so for
  identical state the DOM is identical.
- **The 15000 ms test bound rests on arithmetic**, the ten-times ratio between this workstation and
  a GitHub runner. Nothing measures it.
- **A cursor is read with whatever `q` accompanies it.** `findCursorPivot` resolves the pivot by
  identifier alone, so a cursor minted on an unfiltered page and replayed with a `q` names a row
  the filtered page cannot hold. The client cannot produce the pair, the term being part of the
  query key; nothing on the API's side guards it.
- **Search matches by substring and tolerates no typo.** That is the product change decision G
  records, and the backlog carries the engine study.
- **No search was ever run against a real API from the browser.** The journeys stub the matching
  and the headless reading served a stub too; what the two sides agree on is the contract.
- ~~**The holistic review has not run**, this document being written in the last code block. The
  closing block records its findings here.~~ (Corrected in the closing block on 2026-09-21: it
  ran, and the table above is what it found.)
- **The three declared 400s are declared and not exercised as declared.** The integration tests
  assert the status, the content type and the `code` against a live server, and the gate refuses a
  contract that differs from the sources; nothing compares the two.
- **The tag route's lower bound on `limit` is held twice and measured once.** The repository's
  refusal has a test at `limit = 0`; the controller's clamp is asserted through the searcher it
  calls, not against a running server.

## Next step

The lot tag: an annotated `lot/X.Y.Z-the-header-searches-and-wears-the-name` on the closing
merge, pushed. Then Improve, over the report the closing block's pull request carries.
