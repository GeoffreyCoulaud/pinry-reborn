# The header searches and wears the name

Date: 2026-09-21
Status: Approved by the operator on 2026-09-21; one specification review ran, its 0 CRITICAL,
6 MAJOR and 9 MINOR closed in this document. Frozen when the lot's closing block merges.
Branches: block 10 `feat/the-catalogue-takes-a-query`, block 20 `feat/the-pin-search-route-goes`,
block 30 `feat/the-tags-match-in-sql`, block 35 `feat/the-similarity-goes`, block 40
`feat/the-header-wears-the-name`, block 50 `feat/the-header-searches`
ADRs: `docs/adr/0040-search-is-a-parameter-of-the-catalogue.md`, written in block 10, carries
decisions A, B, C, D, E and F, all of which move the public surface `contract/openapi.json`
publishes, C being an error contract. Decision H, the versions, goes to no record: the contract
guard reads each block's `info-version` against `origin/main` and refuses one that hides what it
did, so the rule is enforced where it is written rather than decided here. Decisions I to N are the
web application's own: no library, no storage format, no protocol, no boundary and no error
contract, so they go to this document and nowhere else.

`docs/specs/2026-09-20-the-pin-is-editable-and-the-boards-arrive.md` gave the web application the
rest of a pin's life and left, in its section 6, the item this lot starts on: search is one of the
four things the API serves and the client does not reach. It turns out the API's own search is the
part that needs replacing.

## 1. Goal

A user with a thousand pins has no way to find one. The API has a search route, and what it does is
load every pin the user owns into memory, score each one against the query with Jaro-Winkler and
trigrams, and hand back at most twenty matches with no cursor and no order the caller chooses. It
reads descriptions alone: a pin tagged `cat` is not found by `cat`.

The lot makes a search a filtered catalogue, on both sides:

1. `GET /api/v1/pins` and `GET /api/v1/boards/{boardId}/pins` take an optional `q`, matched by the
   database over the description and the tags.
2. `GET /api/v1/pins/search` is removed, and the similarity scoring with it.
3. `GET /api/v1/tags/search` matches in the database too, for the same reason.
4. The header carries a search field. It writes `q` into the address of the screen it is on, so the
   home screen searches the whole collection and a board searches that board.
5. The page title gives way to the application's name, which links to the home screen; the house
   icon leaves the navigation.

Decisions A to G, I, J and L come from a question put to the operator during Discuss, one at a
time; decision O from a question put to them after the specification review raised it, and
decision P from the tier-2 question section 3 records.
Decision I' is the only one the review settled without asking, and it is the operator's to
overturn.

## 2. What exists today

```
$ command grep -n "info-version" api/api-application/src/main/resources/application.properties
38:quarkus.smallrye-openapi.info-version=7.2.0
$ command grep -rn "pins/search" clients/apps/webapp/src clients/packages/*/src
clients/packages/api-client/src/schema.d.ts:2134:    "/api/v1/pins/search": {
$ command grep -rln 'm\.home_heading()\|"Your pins"' clients/apps/webapp/src/journeys
clients/apps/webapp/src/journeys/open-the-application.journey.test.tsx
clients/apps/webapp/src/journeys/session-expiry.journey.test.tsx
clients/apps/webapp/src/journeys/sign-up.journey.test.tsx
clients/apps/webapp/src/journeys/sign-in.journey.test.tsx
clients/apps/webapp/src/journeys/open-a-board-and-browse-its-pins.journey.test.tsx
clients/apps/webapp/src/journeys/add-several-pins-from-one-drop.journey.test.tsx
clients/apps/webapp/src/journeys/drop-an-image-on-the-grid-to-add-a-pin.journey.test.tsx
$ sed -n '21p' clients/apps/webapp/src/journeys/drop-an-image-on-the-grid-to-add-a-pin.journey.test.tsx
  return (await screen.findByRole("heading", { name: "Your pins" })).closest("main") as HTMLElement
$ command grep -rn "<AppHeader" clients/apps/webapp/src
clients/apps/webapp/src/routes/Credentials.tsx:21:      <AppHeader heading={title} />
clients/apps/webapp/src/routes/Home.tsx:95:        <AppHeader heading={m.home_heading()}>
clients/apps/webapp/src/routes/Board.tsx:35:      <AppHeader heading={heading}>
clients/apps/webapp/src/routes/Boards.tsx:162:      <AppHeader heading={m.boards()}>
clients/apps/webapp/src/routes/Recycled.tsx:243:      <AppHeader heading={m.recycle_bin()}>
$ command grep -rn "House" clients/apps/webapp/src --include="*.tsx"
clients/apps/webapp/src/components/AppNav.tsx:3:import { House, LayoutGrid, LogOut, Trash2, type LucideIcon } from "lucide-react"
clients/apps/webapp/src/components/AppNav.tsx:43:      <NavIcon to="/" icon={House} name={m.home_heading()} />
$ cd api && wc -l $(git ls-files "*Search*.kt" "*TextSimilarity*.kt" | command grep -v Board)
  173 api-application/src/test/kotlin/.../application/PinSearchIntegrationTest.kt
  160 api-application/src/test/kotlin/.../application/TagSearchIntegrationTest.kt
    6 api-domain/src/main/kotlin/.../domain/entities/SearchResult.kt
   40 api-presentation-quarkus/src/main/kotlin/.../controllers/PinSearchController.kt
   40 api-presentation-quarkus/src/main/kotlin/.../controllers/TagSearchController.kt
   10 api-presentation-quarkus/src/main/kotlin/.../dtos/output/PinSearchOutputDto.kt
   10 api-presentation-quarkus/src/main/kotlin/.../dtos/output/TagSearchOutputDto.kt
   33 api-presentation-quarkus/src/main/kotlin/.../mappers/SearchResultMapper.kt
   59 api-presentation-quarkus/src/test/kotlin/.../controllers/PinSearchControllerTest.kt
   51 api-presentation-quarkus/src/test/kotlin/.../controllers/TagSearchControllerTest.kt
   46 api-usecases/src/main/kotlin/.../usecases/PinSearcher.kt
   46 api-usecases/src/main/kotlin/.../usecases/TagSearcher.kt
   13 api-usecases/src/main/kotlin/.../usecases/exceptions/SearchError.kt
   76 api-usecases/src/main/kotlin/.../usecases/search/TextSimilarity.kt
  186 api-usecases/src/test/kotlin/.../usecases/PinSearcherTest.kt
  166 api-usecases/src/test/kotlin/.../usecases/TagSearcherTest.kt
  260 api-usecases/src/test/kotlin/.../usecases/search/TextSimilarityTest.kt
 1375 total
$ git grep -ln findAllTagsForUser | command grep src/main
api/api-domain/src/main/kotlin/.../domain/repositories/TagRepositoryInterface.kt
api/api-persistence-sqlite/src/main/kotlin/.../sqlite/repositories/TagRepository.kt
api/api-usecases/src/main/kotlin/.../usecases/TagSearcher.kt
api/api-usecases/src/main/kotlin/.../usecases/exports/UserDataExportBuilder.kt
$ git grep -c "findPinsForUser\|findActivePinsForBoard" -- api | command grep -v persistence-sqlite/src/main
api/api-domain/src/main/kotlin/.../domain/repositories/PinRepositoryInterface.kt:2
api/api-persistence-sqlite/src/test/kotlin/.../sqlite/PinRepositoryPaginationTest.kt:8
api/api-persistence-sqlite/src/test/kotlin/.../sqlite/PinRepositorySoftDeleteTest.kt:2
api/api-persistence-sqlite/src/test/kotlin/.../sqlite/PinRepositoryTest.kt:13
api/api-usecases/src/main/kotlin/.../usecases/BoardPinLister.kt:1
api/api-usecases/src/main/kotlin/.../usecases/PinGetter.kt:1
api/api-usecases/src/main/kotlin/.../usecases/exports/UserDataExportBuilder.kt:1
api/api-usecases/src/test/kotlin/.../usecases/BoardPinListerTest.kt:3
api/api-usecases/src/test/kotlin/.../usecases/PinGetterTest.kt:2
api/api-usecases/src/test/kotlin/.../usecases/exports/UserDataExportBuilderTest.kt:2
api/api-usecases/src/test/kotlin/.../usecases/exports/UserDataExportFixtures.kt:1
api/config/detekt/baseline-api-domain-main.xml:1
api/config/detekt/baseline-api-domain.xml:1
$ command grep -n -A 4 "LongParameterList:" api/config/detekt/detekt.yml
120:  LongParameterList:
121-    active: true
122-    allowedFunctionParameters: 5
123-    allowedConstructorParameters: 6
124-    ignoreDefaultParameters: false
```

The package path `fr/geoffreyCoulaud/pinryReborn/api` is elided as `...` in the two listings above,
and nowhere else: the commands print it in full.

No hand-written client code calls `/api/v1/pins/search`: the one hit is the generated schema, which
`packages/api-client` rewrites at install and does not commit. The route is therefore removable
without touching a line of the client.

`findAllTagsForUser` keeps a caller after this lot, the export, so it stays.

The two catalogue routes already have the same signature: `cursor`, `pageSize`, `sort`, answering
`PinListOutputDto` (`PinController.listPins`, `BoardController.listBoardPins`). Their repository
methods, `findPinsForUser` and `findActivePinsForBoard`, share the same three pagination arguments
and both go through `ModelPaginationHelper.getPage`. **`findActivePinsForBoard` already takes five
parameters**, and so does `BoardPinLister.listActivePinsForBoard`: a sixth trips detekt's
`LongParameterList` at `allowedFunctionParameters: 5`, with `ignoreDefaultParameters: false`, so
three functions need an exception, which decision O grants and explains. No
`@Suppress("LongParameterList")` exists anywhere under `api/*/src/main` today.
(Corrected: false, and the pathspec is why. `api/*/src/main` matches no nested path, so the command
that established the claim returned nothing on a tree that holds dozens of them.
`git grep -n 'Suppress("LongParameterList")' -- api | command grep src/main | wc -l` prints 32 on
`origin/main`, read on 2026-09-21 at commit `67f655c4`. The annotation is this repository's usual
way of saying why a function is long, so decision O's three follow the house pattern rather than
setting a precedent. Decision O itself is unchanged.)

**A blank query earns 400 today through bean validation, not through the error the use case
throws.** Both search controllers declare `@QueryParam("q") @NotBlank query: String?`
(`PinSearchController.kt:26`, `TagSearchController.kt:26`), and both integration tests assert
`.body("code", equalTo("VALIDATION_ERROR"))` (`PinSearchIntegrationTest.kt:78-82`,
`TagSearchIntegrationTest.kt`). `BaseErrorMapper.kt:53` maps `SEARCH_EMPTY_QUERY` to 400, and
nothing on those two routes reaches it. Decision C is what changes that.

## 3. The decisions

| # | Decision | Why |
|---|---|---|
| A | Search is an optional `q` on the two catalogue routes. No route of its own | A page of results is a page of catalogue with one predicate more: same body, same cursor, same orders. It is also what makes searching inside a board free, the board's route already having the catalogue's signature |
| B | A pin matches when its description contains `q`, or when the name of one of its tags does. The whole of `q` is one term, never split on spaces | Matching descriptions alone is the defect being fixed: on a pin board the tag is the metadata the user actually writes. Splitting on spaces asks what joins the parts, which is a question a filter does not have to answer |
| C | An absent `q` is the whole catalogue; a `q` that is present and blank earns 400 under the `code` `SEARCH_EMPTY_QUERY`, thrown by the use case as `SearchEmptyQueryError`. Block 30 takes `@NotBlank` off `TagSearchController` so that route answers the same `code` | No bean-validation annotation says "absent, yes; blank, no": `@NotBlank` refuses null, which is the absent parameter, and `@Size(min = 1)` admits a single space. So the catalogue throws, and `BaseErrorMapper.kt:53` maps it to 400. The refusal's `code` therefore changes from `VALIDATION_ERROR` to `SEARCH_EMPTY_QUERY`, on the tag route too: one refusal with two codes because two routes express it differently is exactly the kind of difference a client would have to learn. The client removes the parameter when the field empties rather than sending emptiness |
| D | No relevance and no score. The results keep the catalogue's own orders and its cursor | `LIKE` gives no ranking, and inventing one in SQL buys an order the operator did not ask for. The order stays the user's, through the selector the grid already carries |
| E | `GET /api/v1/pins/search` is removed, with `PinSearcher`, `PinSearchOutputDto`, the pin half of `SearchResultMapper` and `TextSimilarity` | Two routes serving one thing is the cost of keeping it, and nothing calls it |
| F | `GET /api/v1/tags/search` matches in the database too: the names beginning with `q` first, then the names containing it, no score in the body | The same in-memory scan over the same account, and a suggestion list has an order worth deciding: what you are typing the start of comes first |
| G | Similarity matching leaves the product. Fuzzy search returns with the storage engine that can do it | Recorded as a backlog item, section 6. Nothing in SQLite does it at the speed a keystroke needs, and Postgres is the study that decides |
| H | Three contract versions, one per block that moves the surface: `7.3.0`, `8.0.0`, `9.0.0` | The guard reads the contract against `origin/main` at each block, so each declares what it did. `contract/frozen/` is empty and the README states the alpha breaks freely |
| I | `AppHeader` renders the application's name as its `<h1>`, a link to `/`, and takes an optional `heading` rendered beside it | The operator's decision, Discuss question C, option 3. The home and boards screens pass none; a board, the bin and a search pass theirs, which is what tells the user where they are |
| I' | The credentials screen keeps that name and that link, and carries no search field: the field is passed in by the screens that have a grid, as `AppNav` already is | `AppHeader` has five callers and one of them is `Credentials.tsx`, which a visitor with no session sees. A search field there would search nothing, which is the trap `docs/specs/2026-09-20-the-pin-is-editable-and-the-boards-arrive.md`, decision I, records for this very component. The name is not a control: it is the product saying what it is, and its link lands on `/`, which sends a visitor back to signing in |
| O | `query: String? = null` is a sixth parameter on the board's path, and the three functions that reach six carry `@Suppress("LongParameterList")`: the interface, its implementation and `BoardPinLister.listActivePinsForBoard` | The operator's decision, put to them on 2026-09-21 after the specification review raised it. The alternative considered was a `PinPageQuery` parameter object, refused as eleven files of churn for no change in behaviour; the other, raising `allowedFunctionParameters` to 6, was refused because 5 is detekt's own default carried through its 2.0 rename (`functionThreshold: 6` to `allowedFunctionParameters: 5`, commit `b9dd0006`), so raising it would loosen the bound for the whole repository to fit one method. Three annotations say where the exception is; a threshold says nothing anywhere. `ignoreOverridden` is not among the rule's options, which is why the implementation needs its own |
| J | The house icon leaves `AppNav` | The name is the way home, and two controls doing one thing in one bar is the chrome this repository keeps removing |
| K | The field writes `q` into the address of the current route, after a 300 ms pause, with `replace: true` | The address is the state: a reload, a bookmark and the back button all keep the search with nothing stored. `replace` keeps one history entry per search rather than one per keystroke, and the pause keeps one request per search rather than one per keystroke |
| L | On a board, the field searches that board, and a link under it offers the same term over everything | The operator's decision, Discuss question F: a bar whose reach changes with the screen is a trap unless the screen says so. The placeholder names the board and the link names the way out |
| M | The grid's accessible name stays `home_heading`, and `app_name` returns to both catalogues | `home_heading` is what names the grid to a reader, and it is no longer a visible title. `app_name` was deleted by `docs/specs/2026-09-19-the-header-becomes-icons.md`, decision K', when the banner went; the name is back, this time as the way home |
| P | One `useDebounced(value, delay)` in `src/debounce.ts`, used by the search field and by the edit form's tag field | The tag field has no pause today and costs one request per character, which is the defect behind the journey that nearly timed out. Block 50 writes a pause anyway: shared, it removes the cause rather than adding a second copy beside it. The hook holds a timer and state, so it cannot live in `lib/`, which is pure functions and the whole coverage perimeter |
| N | An empty result says the term found nothing, not that the account is empty | `pins_empty` states an empty account, which is false of a search that matched nothing, and the recourse differs: add a pin, or search for something else |

**Decision B is one predicate, and it is composed as a closed junction.** The pin's tags are not a
path on `QPinModel`: `PinTagModel` is a join entity with no collection mapped on either side, so
the tag half is a subquery on the pin identifier. The whole reads
`description contains q OR id in (select pin id from pin tag where tag name contains q)`, built
with `or()` ... `endOr()` so that what `ModelPaginationHelper` adds afterwards is ANDed at the root
rather than absorbed into it. Pitfalls, section 8, carries what that costs if it is got wrong.

**Decision C makes the client the one that decides emptiness.** `q=` is not "everything": it is a
caller that did not mean to search, and the 400 says so where a silent full catalogue would not.

**One adjacent defect, tier 2, answered by the operator on 2026-09-21.** No field in the web
application debounces anything: the edit form's tag suggestions fire a request per character, which
is what put one journey of the previous lot within a timeout of failing. It is tier 2 and not tier
1 by reach, the fix touching a component this lot otherwise leaves alone. Asked at the moment of
discovery, answered by decision P, which folds it into block 50 because that block writes the pause
either way.

**Decision F needs no ranking expression.** The prefix matches and the contains matches are two
bounded queries, the second one asked only for the places the first left free, its results less
those already held. The limit is the caller's, capped at 20 by the route
(`TagSearchController.MAX_LIMIT`, unchanged by this lot) and sent as 8 by this client
(`pins.ts`, `TAG_SUGGESTIONS`), so at most two queries of at most that limit.

## 4. The change

| Block | Where | What |
|---|---|---|
| 10 | `docs/adr/0040-search-is-a-parameter-of-the-catalogue.md` | New. Decisions A, B, C, D, E, F |
| 10 | `api-domain/.../PinRepositoryInterface.kt` | `findPinsForUser` and `findActivePinsForBoard` take `query: String? = null`, the second one carrying `@Suppress("LongParameterList")`, decision O |
| 10 | `api-persistence-sqlite/.../PinRepository.kt` | One private predicate applied by both: the description and the tag subquery under one closed `or()`. The board's override carries the same suppression |
| 10 | `api-usecases/.../PinGetter.kt`, `BoardPinLister.kt` | `query` passed through, a present and blank one refused with `SearchEmptyQueryError`; `listActivePinsForBoard` carries the third suppression |
| 10 | `api-presentation-quarkus/.../PinController.kt`, `BoardController.kt` | `@QueryParam("q") query: String? = null` on both listings, no validation annotation, decision C saying why |
| 10 | `application.properties` | `info-version` to `7.3.0`; `contract/openapi.json` regenerated |
| 20 | `api-presentation-quarkus/.../controllers/PinSearchController.kt`, `dtos/output/PinSearchOutputDto.kt` | Deleted |
| 20 | `api-presentation-quarkus/.../mappers/SearchResultMapper.kt`, `PinResponses.kt` | The pin search halves go; the tag half stays |
| 20 | `api-usecases/.../PinSearcher.kt` and its test, `PinSearchIntegrationTest.kt`, `PinSearchControllerTest.kt` | Deleted |
| 20 | `api-domain/.../PinRepositoryInterface.kt`, `api-persistence-sqlite/.../PinRepository.kt` | (Corrected: this row is added on 2026-09-21, the table having no row for it.) `findAllPinsForUser` deleted, `PinSearcher` being its last production caller. Its eight test callers read the catalogue instead, and the soft-delete case named for it goes, the case above it asserting the same exclusion through `findPinsForUser` |
| 20 | `application.properties` | `info-version` to `8.0.0`; `contract/openapi.json` regenerated |
| 30 | `api-domain/.../TagRepositoryInterface.kt`, `api-persistence-sqlite/.../TagRepository.kt` | `findTagsForUserMatching(user, query, limit)`, prefix first then contains |
| 30 | `api-usecases/.../TagSearcher.kt` and `TagSearcherTest.kt` | Delegates to the repository; the scoring, the threshold and the sort go, and the test that asserts all three is rewritten around the order the repository now serves |
| 30 | `api-presentation-quarkus/.../controllers/TagSearchController.kt` | `@NotBlank` goes; `TagSearcher` throws `SearchEmptyQueryError` instead, decision C |
| 30 | `api-presentation-quarkus/.../dtos/output/TagSearchOutputDto.kt`, `mappers/SearchResultMapper.kt` | `score` leaves the body; `TagSearchControllerTest` and `TagSearchIntegrationTest` follow, the second one's `VALIDATION_ERROR` becoming `SEARCH_EMPTY_QUERY` |
| 30 | `api-usecases/.../exceptions/SearchError.kt` | Stays: `SearchEmptyQueryError` is what blocks 10 and 30 both throw |
| 30 | `application.properties` | `info-version` to `9.0.0`; `contract/openapi.json` regenerated |
| 35 | `api-domain/.../entities/SearchResult.kt`, `api-usecases/.../search/TextSimilarity.kt` and `TextSimilarityTest.kt` | Deleted. Nothing scores anything once blocks 20 and 30 have landed, and nothing before them can delete this |
| 40 | `components/AppHeader.tsx` | The `<h1>` becomes a link to `/` holding `app_name`; `heading` becomes optional and renders beside it |
| 40 | `components/AppNav.tsx` | The house icon and its `NavIcon` call go |
| 40 | `routes/Home.tsx`, `routes/Boards.tsx` | Pass no `heading`. `Home` keeps `m.home_heading()` as the grid's `label` |
| 40 | `routes/Credentials.tsx` | Unchanged in its source, and named here because decision I' is about it: it keeps `AppHeader` and gains the name and the link with every other screen |
| 40 | `messages/en.json`, `messages/fr.json` | `app_name` added back |
| 40 | The seven journey files of section 2's third command | Four read the heading, which becomes `m.app_name()`; `open a board and browse its pins` clicks that link instead of the house icon; `drop-an-image-on-the-grid` and `add-several-pins-from-one-drop` reach `<main>` through that same heading at line 21 and follow it to the new name |
| 50 | `components/SearchField.tsx` | New. The input, the navigation, and on a board the link that widens the search |
| 50 | `debounce.ts` | New. `useDebounced(value, delay)`, decision P |
| 50 | `components/PinEditForm.tsx` | The tag field reads its suggestions off the debounced term, one line |
| 50 | `components/AppHeader.tsx` | A second slot, between the name and the actions, for the screens that pass a field. `AppHeader` renders none itself, decision I' |
| 50 | `lib/searches.ts` and its test | `searchTermOr(value)`, the address's `q` read into a term or nothing, as `pinSortOr` reads the order |
| 50 | `router.tsx` | `/` and `/boards/$boardId` validate `q` beside `sort` |
| 50 | `pins.ts` | `usePins(sort, boardId, query)`, the term in the query key and in the request |
| 50 | `components/PinGrid.tsx` | Takes the term, forwards it, and states the empty result with it |
| 50 | `routes/Home.tsx`, `routes/Board.tsx` | Read `q`; `Home` passes a heading when there is one |
| 50 | `messages/en.json`, `messages/fr.json` | `search_placeholder`, `search_in_board`, `search_everywhere`, `search_results`, `search_empty` |
| 50 | `lib/journeys.ts` | The two journeys of section 5 |

## 5. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/the-catalogue-takes-a-query` | A pin whose tag matches and whose description does not is in the page; a pin matching neither is not; the same on a board's route, where a matching pin in another board stays out; a blank `q` earns 400 with `code` `SEARCH_EMPTY_QUERY` and content type `application/problem+json`; a tag stored `Cat` is found by `cat`, and a description holding `café` is found by `café` and not by `cafe`; **a second page read with a cursor holds only matching pins**, which is the junction pitfall's own test; a plan assertion in the repository test names a `SUBQUERY` line, the pin table being scanned here by design so the absence of `SCAN` proves nothing |
| 20 | `feat/the-pin-search-route-goes` | ~~`GET /api/v1/pins/search` answers 404, which discriminates: a controller still registered answers 400 on a missing `q`, the literal path winning over `@Path("/{pinId}")`~~; the contract holds no such path; `oasdiff` accepts `8.0.0`. (Corrected: the struck criterion was written as a test and the operator refused it on review of pull request #183, on 2026-09-21, as dead the day the lot ends. **What holds the block up is the contract**: the gate regenerates `contract/openapi.json` and refuses a committed document that differs, so a controller put back and not regenerated fails the gate, and one put back and regenerated restores the path the document no longer carries. The compile is the other half, `PinSearcher` and `PinSearchOutputDto` being gone) |
| 30 | `feat/the-tags-match-in-sql` | A name beginning with the term comes before a name merely containing it; a name matching neither is absent; a tag stored `Café` is found by `café` and not by `cafe`, and one stored `Cat` is found by `cat`; the limit is honoured across the two queries and holds no duplicate; the body carries no `score`; a blank `q` earns `SEARCH_EMPTY_QUERY` and no longer `VALIDATION_ERROR` |
| 35 | `feat/the-similarity-goes` | The suite that exists, less the tests deleted with the code. What would show the block wrong is a compile failure: a caller of `TextSimilarity` or `SearchResult` left standing |
| 40 | `feat/the-header-wears-the-name` | The name is a heading and a link, and clicking it from a board lands on the home screen; the home screen shows no second title; no control in the bar is named `Your pins` any more; the credentials screen carries the name and no search field, which holds for block 50 too |
| 50 | `feat/the-header-searches` | Journey **search from the header**: typing a term on the home screen puts it in the address, the grid holds the pins that match and not the others, a reload keeps both the term and the field's value, and emptying the field brings the whole catalogue back. Journey **search inside a board and widen it**: the same on a board, scoped to it, and the link under the field lands on the home screen with the term kept and the board dropped. Decision P's own check is a count: a term typed in one go asks the API once, not once per character, and the same holds of the tag field, whose journey is the one that measured 647 ms |

Each block is green and coherent alone. Block 10 leaves `/pins/search` standing
and working; block 20 removes it with its use case, so nothing is left callerless behind it; block
30 is the tag half, which block 20 does not touch; block 35 deletes what both have stopped calling
and can therefore come after neither one alone. Block 40 leaves the search field unbuilt and the
header already carries the name, which is the way home the house icon used to be. Block 50 is the
field.

**The client blocks follow the API blocks, and cannot precede them.** `packages/api-client` is
generated from `contract/openapi.json` at install: `q` has to be in the contract before `pins.ts`
can send it and typecheck.

**The order of blocks 30 and 35 is not free.** `TextSimilarity` and `SearchResult` keep a caller
until `TagSearcher` has stopped scoring, so the matching lands first and the deletion follows it.
Taken the other way round the first half does not compile, let alone go green.

Estimated diffs, in lines `git diff --numstat` would count: block 10 about 280, of which about 110
production under `api/`; block 20 about 530, of
which about 115 production (Corrected: block 20 measured 622 and 136, read by
`git diff --numstat main...HEAD` at commit `d30891b9` on 2026-09-21. It passes the strict 600 under
the waiver the operator granted on 2026-09-21, answering the block's tier-2 question on
`findAllPinsForUser`: delete it in block 20, and the block may pass the bound. The production count
stays under its own 200. The method's removal is 11 production lines and 28 test lines the estimate
did not carry; the rest is the estimate being an estimate); block 30 about 450, of which about 130 production; block 35 about 345,
of which 82 production; block 40 about 80 and block 50 about 280 under `clients/`. The estimate is
not evidence: each block sums `git diff --numstat` against `main` at its first green run and says so
in its pull request. **Block 30 is the one to watch**: a rewritten line counts twice, and its three
test files hold 377 lines between them (`TagSearcherTest` 166, `TagSearchIntegrationTest` 160,
`TagSearchControllerTest` 51). If it passes the strict 600, its seam is the controller's
`@NotBlank` and the `score` leaving the body on one side, the repository's matching on the other.

## 6. Adjacent backlog items

- **"What the API serves and the web application does not reach yet"** (Features) names search,
  account management, import and export. This lot closes the search part and the item is rewritten
  in the closing block to hold what is left, which is the other three.
- **"A board has no cover"** (Features) is adjacent by screen and stays open: it is a field on
  `BoardOutputDto` and a tile grid on `/boards`, and this lot touches neither. That is the
  operator's to accept.
- **"A table rebuild's row-carrying path is exercised by nothing"** and **"`foreign_keys` is off"**
  (P2) are adjacent by module and stay open: this lot writes no migration and adds no constraint.
- **"Semantic search by text"** and **"Search by image"** (Features, Visual understanding) are
  adjacent by subject and stay open. They name the same engine decision G sends to a study, pgvector
  being Postgres, but what they wait on is the inference service and the embeddings, not the storage
  engine: neither becomes reachable by moving a `LIKE` into SQL. That is the operator's to accept.

This lot files one item, decision G:

- Search matches by substring and no longer tolerates a typo. Restoring it wants an engine that can
  index for it, which is the study of a standalone Postgres rather than SQLite.

## 7. Out of scope

What this lot does not change, and how a reader notices if it did:

- **The recycle bin is not searchable.** `GET /api/v1/pins/recycled` gains no `q`, and
  `routes/Recycled.tsx` renders no search field: a `git diff --stat` against `main` shows the file
  untouched by every block. Its header still changes, the bin rendering `AppHeader` and `AppNav`,
  which is what blocks 40 and 50 are; what does not change is the screen's own source and its
  queries.
- **A cursor is read with the `q` it was minted under, and a mismatched pair is out of scope.**
  `findCursorPivot` resolves the pivot by identifier alone, so a cursor taken from an unfiltered
  page and replayed with a `q` names a row the filtered page cannot hold, and
  `ModelPaginationHelper` counts against a threshold that assumes the pivot is among the rows read.
  The client cannot produce the pair: the term is part of the query key, so changing it restarts the
  infinite query with no cursor at all. Nothing guards it on the API's side, and nothing in this lot
  makes it reachable.
- **No accent folding.** `cafe` does not find `café`; the observable is a repository test asserting
  that miss, so the day someone adds folding the test says so out loud rather than silently passing.
- **No index is added and no migration is written.** `ls api/api-persistence-sqlite/src/main/resources/dbmigration/`
  holds the same files after the lot as before. A `%term%` match cannot use an index, and the scan
  moves from the JVM to SQLite, which is the whole of the performance claim being made here.
- **No screen gains a route.** `router.tsx` declares the same six routes; `q` is a search parameter
  of two of them.
- **The tag field of the edit form keeps what it offers and when**: the same suggestions, from a
  query that now matches in SQL, and behind decision P's pause. Its journey asserts the same
  suggestions on the same keystrokes and stays green; what changes is the number of requests
  underneath, which is the point.

## 8. Pitfalls

- **An `or()` left unclosed swallows the cursor's predicates.** `ModelPaginationHelper` hands the
  base query to the sort strategy, which appends `timestamp < pivot OR (timestamp = pivot AND id <= pivotId)`
  at the root. A text predicate left inside an open junction makes the two one disjunction, and page
  two of a search comes back holding pins that match nothing. The block's own test is the second
  page, not the first: the first page is green either way.
- **`PinTagModel` maps no collection**, so `QPinModel` has no `.tags` path and no fetch join to
  make. The tag half is a subquery on the identifier, which is the shape `findPinIdsByContentHashForUser`
  already uses.
- **The match is `contains` and `startsWith`, never `icontains` or `istartsWith`.** SQLite's `LIKE`
  ignores collating sequences and is already case-insensitive over ASCII, which is the same fold
  `collate nocase` gives `ix_tags_author_name_nocase`, so a bare `LIKE` agrees with the index that
  defines a tag name's identity. Ebean's case-insensitive operators render `lower(column) like ?`
  with the bind lowercased in Java, a Unicode-aware fold against SQLite's ASCII-only `lower()`, so
  the two sides disagree on a non-ASCII name. `TagRepository.findUserTagByName` already refuses
  `ieq` for that reason and says so in its comment. Measured on 2026-09-21:
  `sqlite3 :memory: "create table t(name text); insert into t values('Cat'),('cat'),('CAT'),('Café'),('cafe'); select group_concat(name) from t where name like '%cat%'; select group_concat(name) from t where name like '%cafe%'; select lower('CAFÉ');"`
  prints `Cat,cat,CAT`, then `cafe`, then `cafÉ`.
- **The new parameter is declared `= null`, and that is what leaves eight test files alone.**
  `UserDataExportBuilder.kt:340` calls `findPinsForUser` positionally, and
  `UserDataExportBuilderTest.kt` and `UserDataExportFixtures.kt` stub it with MockK `every` blocks
  that match on exact arguments. Without the default they all fail to compile; with it they keep
  matching, the real call passing the same null.
- **The catalogue's default order is not the grid's.** The routes default to `CREATED_AT_ASC` and
  the client sends `CREATED_AT_DESC`. A search changes neither.
- **A request per keystroke is a timeout waiting to happen, and nothing in the client debounces
  anything today.** `PinEditForm.tsx:37` calls `useTagSearch(typed)` on every character; React
  Query caches by term, so a prefix already typed is a cache hit and a new one is a request. One
  journey of the previous lot sat at 647 ms on the workstation and timed out once inside
  `dagger call gate`. Decisions K and P are the two halves of the answer: the search field pauses,
  and the tag field pauses with it through the same hook.
- **A debounced field's test waits for the value, not for the timer.** `findBy*` and `waitFor`
  already poll; a fake clock installed around react-aria's own timers is what breaks first.
- **The gate renders nothing and sees no layout.** Both client blocks are read in a headless
  browser against the built bundle before their pull request is marked ready, the header being the
  one element every screen shows.
