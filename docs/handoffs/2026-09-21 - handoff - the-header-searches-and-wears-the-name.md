# Handoff: the header searches and wears the name

Date: 2026-09-21
Specification: `docs/specs/2026-09-21-the-header-searches-and-wears-the-name.md`
ADRs: `docs/adr/0040-search-is-a-parameter-of-the-catalogue.md`
Blocks: 10 `feat/the-catalogue-takes-a-query` (#182), 20 `feat/the-pin-search-route-goes` (#183),
25 `feat/the-tag-field-pauses` (#185), 30 `feat/the-tags-match-in-sql` (#184), 35
`feat/the-similarity-goes` (#186), 40 `feat/the-header-wears-the-name` (#187), 50
`feat/the-header-searches` (this one). Written in the last code block, and the closing block
corrects it.
Tier: Spec. The specification review ran before a line was written: 0 CRITICAL, 6 MAJOR, 9 MINOR,
all closed in the document.

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
  which is the absent parameter, so no annotation could express it.
- **The tag suggestions are two bounded queries**: the names beginning with the term, then the
  names merely holding it, less what the first held. No `score` in the body.
- **The contract stands at `9.0.0`**, one major per block that broke it: `7.3.0` for `q`, `8.0.0`
  for the pin search route's removal, `9.0.0` for the tag body losing `score`.
- **The header's `<h1>` is the application's name and the way home.** The house icon left
  `AppNav`; a screen with a title of its own renders it as an `<h2>` beside the name, with a `·`
  between them. The credentials screen carries the name and no search field.
- **The search field writes `q` after a 300 ms pause, with `replace: true`.** The address is the
  state: a reload, a bookmark and the back button keep the search with nothing stored, and one
  history entry is kept per search rather than one per keystroke. On a board the field searches
  that board and a link under it offers the same term over everything.
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
  by the edit form's tag field. It holds a timer and state, so it is not in `lib/`.

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
- **The holistic review has not run**, this document being written in the last code block. The
  closing block records its findings here.

## Next step

Wrap: the holistic review over `git diff lot/0.30.0-the-pin-is-editable-and-the-boards-arrive..origin/main`,
then the closing block, which fixes its findings, files the lot's two backlog items (the engine
study behind fuzzy search, and the client suite spending more on starting than on testing),
rewrites the Features item "What the API serves and the web application does not reach yet" to
hold the three that are left, corrects this document, and then the lot tag.
