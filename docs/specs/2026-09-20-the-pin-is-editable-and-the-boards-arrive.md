# The pin is editable and the boards arrive

Date: 2026-09-20
Status: Draft, awaiting the operator's review; one specification review ran, its 1 CRITICAL,
8 MAJOR and 7 MINOR closed in this document. Frozen when the lot's closing block merges.
Branches: block 10 `feat/one-route-writes-a-pin`, block 15 `feat/the-sub-routes-go`, block 20
`feat/the-batch-routes`, block 25 `feat/the-dead-setters-go` (Corrected: added on 2026-09-20. Block
15 left `PinTagger.setTags` and `PinBoardSetter.setBoards` without a production caller, and removing
them there would have cleared the strict 600; the operator settled on a block of their own after
block 20, which already touches `PinBoardSetter.kt`), block 30 `feat/the-grid-chooses-its-order`,
block 40
`feat/the-boards-have-a-screen`, block 50 `feat/a-board-has-a-grid`, block 60
`feat/a-pin-is-editable`, block 70 `feat/a-pin-changes-its-image`, block 80
`feat/the-recycle-bin`, block 90 `feat/the-grid-consumes-its-selection`
ADRs: `docs/adr/0038-one-route-writes-a-pin.md` carries decisions B, C and H, and decision E' for
the write of one pin; `docs/adr/0039-a-batch-route-is-all-or-nothing.md` carries decisions D, E,
E', F and G. Decision O's
component layer is `docs/adr/0035-a-styled-layer-over-react-aria-components.md` and decision P's
limit is `docs/adr/0033-the-grid-keeps-every-page-it-scrolls.md`. Every other decision below is
this document's alone, none of them picking a library, a storage format, a protocol between two
components, a boundary, a public surface or an error contract.

`docs/specs/2026-09-10-web-application.md` built the first client and filed, in section 6, the item
this lot answers: what the API serves and the web application does not reach yet. Three lots since
then styled that client and gave it a gesture. This one gives it the rest of a pin's life, and the
boards that a pin belongs to.

## 1. Goal

A user can create a pin and can do nothing else to it. There is no way to correct a description, to
change an image, to file a pin under a board, to delete one, or to get one back. Boards, which the
API has served since before the first client existed, have no screen at all.

The lot builds, on the client: the boards, a board's own grid, an editable pin, the image replaced
or fetched again, the recycle bin, a selection the grid acts on, and a sort the user chooses. It
repairs, on the API, the two gaps building it exposes: no route writes a pin's description, and no
route acts on several pins at once.

The lot builds no search, no account screen, no import and no export. Section 7 names them with the
observable that would show one had slipped in, and section 6 files what stays open.

Every design choice below comes from a question put to the operator during Discuss, one at a time.

## 2. What exists today

The contract serves 50 operations. The web application calls eleven of them, seven from its own
source and four from the shared authentication package.

```
$ python3 -c "import json;d=json.load(open('contract/openapi.json'));print(sum(len([k for k in o if k in ('get','post','put','patch','delete')]) for o in d['paths'].values()))"
50
$ command grep -rhoE 'client\.(GET|POST|PUT|DELETE)\("/api/v1/[^"]*"' clients/apps/webapp/src clients/packages/auth/src | sort -u
client.DELETE("/api/v1/me/image-downloads/{pinId}"
client.DELETE("/api/v1/sessions/current"
client.GET("/api/v1/handshake"
client.GET("/api/v1/me/image-downloads"
client.GET("/api/v1/pins"
client.GET("/api/v1/pins/{pinId}"
client.GET("/api/v1/sessions/current"
client.POST("/api/v1/pins"
client.POST("/api/v1/sessions"
client.POST("/api/v1/users"
$ command grep -rn "pins/{pinId}/tags\|pins/{pinId}/boards" clients/apps/webapp/src clients/packages/auth/src clients/packages/api-client/src
clients/packages/api-client/src/schema.d.ts:2012:    "/api/v1/pins/{pinId}/boards": {
clients/packages/api-client/src/schema.d.ts:2280:    "/api/v1/pins/{pinId}/tags": {
$ command grep -n "info-version" api/api-application/src/main/resources/application.properties
38:quarkus.smallrye-openapi.info-version=6.2.0
$ ls -a contract/frozen/
.  ..  .gitkeep
```

The eleventh call is `PUT /api/v1/pins/{pinId}/image`, written through the constant `PIN_IMAGE` in
`clients/apps/webapp/src/images.ts`, which the grep above does not match. The two routes this lot
removes appear in the generated schema and in no call site, so removing them breaks nothing in this
repository.

Ten properties of that state carry the design. Each was read.

1. **`DELETE /api/v1/pins/{pinId}` is a soft delete.** The controller method is named
   `softDeletePin` and calls `PinRecycleBin`. The same holds of a board:
   `BoardRecycleBin.softDelete` takes the board and nothing else, so deleting a board leaves its
   pins alone.
2. **No route writes a pin's description or its source addresses.** `PinCreationInputDto` carries
   `sourceContextUrl`, `sourceMediaUrl` and `description`, and `/api/v1/pins/{pinId}` serves `GET`
   and `DELETE` only.
3. **`PinOutputDto` already carries `tags` and `boards`**, as `List<TagOutputDto>` and
   `List<BoardRefDto>`. A `PUT` that replaces the pin has them in its output type already.
4. **`PinTagsInputDto` takes names, not identifiers**, and the operation's own `description` states
   that a name is an identity per author under an ASCII fold
   (`PinController.kt`, the `@Operation` on `setTags`). A name the author does not hold yet becomes
   a tag. `GET /api/v1/tags/search` documents nothing: its `description` is null in
   `contract/openapi.json`.
5. **`GET /api/v1/boards/{boardId}/pins` has the signature of `GET /api/v1/pins`**: the same
   `cursor`, the same `pageSize`, the same `PinSortStrategyInputEnum` and the same default of
   `CREATED_AT_ASC`. `GET /api/v1/boards/recycled` takes no sort at all, and the pins' bin has
   `PinRecycleBinSortStrategyInputEnum`, which is the pins' enum plus `DELETED_AT_DESC`.
6. **The client sends no `sort`.** `usePins` passes `{ cursor, pageSize }` and nothing else, so every
   grid in the application is ordered oldest first and a pin just created lands below every page the
   user has not loaded.
7. **`BoardOutputDto` carries `name`, `description` and `pinCount`**, no cover and no image, and
   `GET /api/v1/boards` takes no cursor: the board list arrives whole.
8. **`PUT /api/v1/pins/{pinId}/image` already replaces an image the pin carries, atomically.**
   `RequestPinImageDownload.request` refuses nothing when an image exists, and
   `DownloadPinImage.promoteAndSwap` reads the superseded image, swaps inside a transaction, and
   deletes the old bytes only after a real swap. `PinImageState.derive` answers `READY` with a
   `replacement` sub-state while the new download runs, which the contract carries as
   `ReplacementDto` and which no client reads today. A `DELETE` before the `PUT` would drop the
   current image ahead of a fetch that can fail; `clients/apps/webapp/src/images.ts` already refuses
   that shape in a comment, for its own reason.
9. **A status is chosen once per error code, and an identifier in a body is the one place that
   departs.** `BaseErrorMapper.statusFor` is a flat table, one arm per `ErrorCode`, exhaustive with
   no `else` so a code without a status fails to compile. Every `*_DOES_NOT_EXIST` maps to 404 and
   every `*_INSUFFICIENT_PERMISSIONS` to 403. `BOARD_INVALID_MEMBERSHIP` is the exception at 400,
   raised by `PinBoardSettingInvalidBoardError` with one message for both cases, "do not exist or
   are not owned by the user", which `BoardMembershipIntegrationTest` pins at 400 for an unknown
   board and for another user's alike.
10. **No error code and no error status reaches the contract.** Neither
    `BOARD_INVALID_MEMBERSHIP` nor `BOARD_DOES_NOT_EXIST` appears in `contract/openapi.json`, and an
    operation declares an error status only where an annotation poses one, as
    `GET /api/v1/tags/search` shows with its 200, 401 and 403 alone. Changing which status a
    situation earns is therefore invisible to the contract and to `contract-guard`.

## 3. The decisions

| # | Decision | Why |
|---|---|---|
| A | The pin's lifecycle and the boards travel in one lot | Without a screen that creates and shows boards, nothing validates that a pin entered one or left it |
| B | `PUT /api/v1/pins/{pinId}` writes the whole pin: `description`, `sourceContextUrl`, `sourceMediaUrl`, `tags` and `boardIds` | `docs/adr/0038-one-route-writes-a-pin.md` |
| C | `PUT /api/v1/pins/{pinId}/tags` and `PUT /api/v1/pins/{pinId}/boards` are removed | `docs/adr/0038-one-route-writes-a-pin.md` |
| D | Membership in bulk is board oriented: `POST /api/v1/boards/{boardId}/pins` adds, `DELETE /api/v1/boards/{boardId}/pins` removes, both carrying `{pinIds}` | `docs/adr/0039-a-batch-route-is-all-or-nothing.md` |
| E | A batch route is all or nothing, and every identifier it refuses answers 404 when it resolves to nothing and 403 when it is another user's, wherever it was named. Nothing is written either way | `docs/adr/0039-a-batch-route-is-all-or-nothing.md` |
| E' | `BOARD_INVALID_MEMBERSHIP` is split into `BOARD_DOES_NOT_EXIST` and `BOARD_INSUFFICIENT_PERMISSIONS`, so a board named in a body earns what a board named in a path earns | Property 9 makes it the only code that departs from the table's rule, and it exists to hide which of the two happened. The operator settled on 2026-09-20 that the existence it hides is not worth a second grammar: identifiers are random version 4 UUIDs, so enumerating another user's boards is not a threat this API defends against. Property 10 is why this costs no contract change |
| F | The recycle bin reads the same on both collections: recycling and restoring act in bulk, a permanent delete acts on one, and emptying is the route that already exists | `docs/adr/0039-a-batch-route-is-all-or-nothing.md` |
| G | `PinIdsInputDto.pinIds` and `BoardIdsInputDto.boardIds` carry `@NotEmpty`, which answers 400. `PinUpdateInputDto`'s `tags` and `boardIds` deliberately do not | `docs/adr/0039-a-batch-route-is-all-or-nothing.md`, decision 3. On a batch route the empty list is a caller defect and `DELETE /api/v1/pins` with no body would read as "delete every pin I own". On the write of one pin the empty list is how a user clears their tags or files a pin under no board. 400 and not 404: an empty list is a malformed request, not an identifier that failed to resolve |
| H | The contract's version rises three times: `6.3.0` in block 10, which adds one operation; `7.0.0` in block 15, which removes two; `7.1.0` in block 20, which adds five | `docs/adr/0038-one-route-writes-a-pin.md`, first consequence, for the break. `.dagger/src/index.ts` lists `api-version-not-bumped` among the rules `contract-guard` throws on, so every block that touches the document raises the version, whatever the change's severity |
| I | Three routes join the client: `/boards`, `/boards/:id` and `/recycled`, reached by icons a signed-in screen passes to `AppHeader` as children | `docs/specs/2026-09-19-the-header-becomes-icons.md`, decision K: the bar renders the theme control itself and the screen's actions are its children. `routes/Credentials.tsx` renders the same bar, so an icon put inside it would be offered to a visitor with no session |
| J | `/recycled` is one screen with two tabs, Pins and Boards | The two collections carry the same three gestures, so one entry point and one grammar |
| K | The pin dialog reads by default and switches to a form on a button, with Save and Cancel | Saving once is what decision B buys. The edit surface is not the reading surface: what shows a pin is an image and its words, what edits it is a set of fields |
| L | The tag field is free text, its suggestions coming from `GET /api/v1/tags/search`, and a name the author does not hold creates the tag | Property 4. Typing `Landscape` where `landscape` exists offers the existing tag, the fold being the server's identity rule |
| M | The image has two controls and no delete: replace it with a file, and fetch it again from `sourceMediaUrl`, the second absent when the pin holds no address. Both are one `PUT /api/v1/pins/{pinId}/image`, which is `useSetPinImage` as it stands | Property 8: the route already supersedes, atomically, and the old image stands until the new one lands. Having no delete control is also what keeps the rule that a pin is given either an upload or an address, with no intermediate state to validate |
| N | `/boards` is a list of name, description and pin count, with create, rename and delete | Property 7: the contract serves no cover, so a tile grid would mean one `GET /api/v1/boards/{boardId}/pins` per board or a new field. Neither is worth a lot that already changes the contract twice |
| O | The grids act on their selection: the pin grid, a board's grid and both tabs of the bin. `/boards` is a `GridList` and carries no selection bar, a board being deleted from its own row | `selectionMode` has been set on the grid since the first client lot with nothing consuming it. react-aria's `GridList` carries selection whatever the layout, the word grid naming the accessibility role; the layer is `docs/adr/0035-a-styled-layer-over-react-aria-components.md` |
| P | A delete removes the tiles from the query cache in place; an edit goes through `rereadSettledPins` | The grid keeps every page it scrolls (`docs/adr/0033-the-grid-keeps-every-page-it-scrolls.md`), so invalidating to drop one tile refetches every page loaded |
| Q | Each grid's header carries a sort selector, and the choice lives in the route's search parameters. The defaults are `CREATED_AT_DESC` on the grid and on a board, `DELETED_AT_DESC` in the bin's Pins tab. The bin's Boards tab shows none | Property 6 is a defect this lot would make worse by putting a selection bar on a grid whose newest pins are off screen. The URL persists the choice across a reload and the back button with nothing stored. Property 5 is why the Boards tab has no selector |
| R | Deleting a pin sends it to the bin, and the bin is how it comes back. No undo toast | An undo that expires is a second recovery path for the same event, and the bin has to exist anyway for the boards |

**Decision B makes an edit a read-modify-write, and that is accepted.** Writing one field means
sending the others as they were read, so two tabs open on the same pin overwrite each other and the
last one wins. That was already true of the route decision C removes, which replaced a pin's whole
board set, and the application has one owner per pin. The ADR records it.

**Decision E chooses the simplest failure and not the kindest one.** A client that sends an
identifier it does not own has just read the grid, so the normal case is that every identifier
passes. Reporting per identifier would mean a new output type and a second error grammar for the
sake of a case the user cannot cause.

**Decision M is not a new protocol, which is why no ADR carries it.** The first draft of this
document prescribed a `DELETE` followed by a `PUT` and claimed no single route replaced an image.
The specification review refuted it against `DownloadPinImage.promoteAndSwap`, and property 8 is
that reading. What is left is a client calling an existing route twice over, with no decision
between components to record.

**Decision Q is adjacent work the operator adopted.** Property 6 is a defect of the existing grid,
not of anything this lot builds, and it is fixed here because the lot puts a selection bar on that
grid. It takes its own block rather than riding inside another.

## 4. The change

| Block | Where | What |
|---|---|---|
| 10 | `api/.../dtos/input/PinUpdateInputDto.kt` | New: `description`, `sourceContextUrl`, `sourceMediaUrl`, `tags`, `boardIds`, with the validation `PinCreationInputDto`, `PinTagsInputDto` and `PinBoardsInputDto` carry today, less `@NotEmpty` (decision G) |
| 10 | `api/.../controllers/PinController.kt` | `PUT /{pinId}` added, carrying the ASCII-fold `description` property 4 reads on `setTags` |
| 10 | `api/api-usecases/.../PinUpdater.kt` | New: one `fenced`, writing the fields, the tags and the boards inside it |
| 10 | `api/api-usecases/.../PinTagger.kt`, `PinBoardSetter.kt` | The resolution and the write split from the transaction, so `PinUpdater` composes them inside its own. `resolveBoard` throws `BoardRetrievalBoardDoesNotExistError` or `BoardRetrievalPermissionError` in place of `PinBoardSettingInvalidBoardError` |
| 10 | `api/api-usecases/.../exceptions/PinBoardSettingError.kt`, `ErrorCode.kt`, `mappers/BaseErrorMapper.kt` | `PinBoardSettingInvalidBoardError` and `BOARD_INVALID_MEMBERSHIP` deleted, the mapper's arm with them. The `when` being exhaustive with no `else`, a code left behind fails to compile |
| 10 | `api/api-application/src/test/.../BoardMembershipIntegrationTest.kt` | The two status assertions move from 400 to 404 and 403, the old route still standing until block 15 |
| 10 | `api/api-application/src/main/resources/application.properties`, `contract/openapi.json` | `info-version` to `6.3.0`, document regenerated |
| 15 | `api/.../controllers/PinController.kt` | `setTags` and `setBoards` removed |
| 15 | `api/.../dtos/input/PinTagsInputDto.kt`, `PinBoardsInputDto.kt` | Removed with their routes |
| 15 | `api/api-application/src/test/.../PinTaggingIntegrationTest.kt`, `BoardMembershipIntegrationTest.kt` | Rewritten onto `PUT /{pinId}`, every case kept |
| 15 | `api/api-application/src/main/resources/application.properties`, `contract/openapi.json` | `info-version` to `7.0.0`, document regenerated |
| 20 | `api/.../dtos/input/PinIdsInputDto.kt`, `BoardIdsInputDto.kt` | New, both `@NotEmpty` |
| 20 | `api/.../controllers/PinController.kt` | `DELETE /api/v1/pins` added |
| 20 | `api/.../controllers/PinRecycleBinController.kt`, `BoardRecycleBinController.kt` | `POST /recycled/restore` added to each |
| 20 | `api/.../controllers/BoardController.kt` | `POST` and `DELETE /{boardId}/pins` added |
| 20 | `api/api-usecases/.../PinRecycleBin.kt`, `BoardRecycleBin.kt`, `PinBoardSetter.kt` | The bulk methods, each one transaction, each resolving every identifier before its first write |
| 20 | `api/api-application/src/main/resources/application.properties`, `contract/openapi.json` | `info-version` to `7.1.0`, document regenerated |
| 25 | `api/api-usecases/.../PinTagger.kt`, `PinBoardSetter.kt`, `exceptions/PinTaggingError.kt`, `PinBoardSettingError.kt` | `setTags` and `setBoards` deleted with the error families only they raise, and the bulk of `PinTaggerTest.kt` and `PinBoardSetterTest.kt` with them. No `ErrorCode` arm goes: both families reuse `PIN_DOES_NOT_EXIST`, `PIN_INSUFFICIENT_PERMISSIONS` and `PIN_ALREADY_SOFT_DELETED` (Corrected in block 25: one family goes, not two. `PinBoardSettingError.kt` stays whole, its three errors being what `resolvePin` raises for the bulk routes block 20 added, and the file is untouched. `TagSearchIntegrationTest.kt` is the file the deletion reaches that this row does not name: its setup called `setTags` and now asks `TagCreator` for the tags the search reads) |
| 30 | `clients/.../components/SortSelect.tsx` | New: a `Select` over the sort values a screen passes it, writing the route's `sort` search parameter |
| 30 | `clients/.../pins.ts` | `usePins` takes a sort and sends it; the query key carries it |
| 30 | `clients/.../router.tsx`, `routes/Home.tsx` | The home route validates `sort`, defaulting to `CREATED_AT_DESC`; `Home` passes the selector to `AppHeader` |
| 40 | `clients/.../boards.ts` | New: `useBoards`, and the create, rename and delete mutations |
| 40 | `clients/.../routes/Boards.tsx` | New: a `GridList` of boards with no selection, a creation dialog, rename and delete per row |
| 40 | `clients/.../components/AppNav.tsx` | New: the navigation icons, rendered as `AppHeader`'s children by the signed-in routes and by nothing else |
| 40 | `clients/.../router.tsx`, `routes/Home.tsx` | `/boards`, and `AppNav` in `Home`'s header |
| 50 | `clients/.../components/PinGrid.tsx` | New, extracted from `routes/Home.tsx`: the virtualiser, the layout, the tiles and the sort |
| 50 | `clients/.../routes/Board.tsx` | New: the board's name and description above `PinGrid` |
| 50 | `clients/.../routes/Home.tsx`, `router.tsx`, `pins.ts` | `Home` renders `PinGrid`; `/boards/$boardId` with its own `sort`; `usePins` takes the board it lists |
| 60 | `clients/.../components/PinEditForm.tsx` | New: the fields, a tag field over `GET /api/v1/tags/search`, a board multi-select over `useBoards` |
| 60 | `clients/.../routes/Home.tsx` | The pin dialog gains its Edit button and holds which mode it is in |
| 60 | `clients/.../pins.ts`, `images.ts` | `useUpdatePin`, one `PUT`, then `rereadSettledPins`, which `images.ts` exports for it |
| 70 | `clients/.../components/PinEditForm.tsx` | The two image controls, both calling `useSetPinImage` as it stands, and the `replacement` sub-state shown while a fetch runs |
| 80 | `clients/.../routes/Recycled.tsx` | New: two `Tabs`, a grid of recycled pins with its sort, a grid of recycled boards with none, restore, delete for good, empty |
| 80 | `clients/.../recycled.ts` | New: the queries and mutations for both collections |
| 80 | `clients/.../router.tsx`, `components/AppNav.tsx` | `/recycled` and its icon |
| 80 | `clients/.../lib/tiles.ts`, `pins.ts` | `removePins`, a pure walk over the cached pages beside `replacePins`; `pins.ts` keeps the `setQueryData` call |
| 90 | `clients/.../components/SelectionBar.tsx` | New: the gestures the screen passes it, with the count |
| 90 | `clients/.../routes/Home.tsx`, `routes/Board.tsx`, `routes/Recycled.tsx` | The grids pass `selectedKeys` and render the bar when the selection is not empty |
| 90 | `clients/.../boards.ts`, `recycled.ts` | The five batch calls block 20 serves |
| 90 | `docs/backlog.md` | The Features item rewritten to what this lot leaves: search, account management, import and export |
| every client block | `clients/.../messages/en.json`, `fr.json` | The strings that block adds |

**Block 10 adds a route nothing calls, and block 15 removes two nothing calls.** The new route's
consumer is block 60. That is the case "What a block is" admits, so both pull requests repeat it.

**The bar's gestures differ per screen and its component does not.** The pin grid and a board's grid
carry add to a board, remove from this board (the pin grid's variant filing under nothing), and
delete. The bin's Pins tab carries Restore alone, and its Boards tab the same, decision F putting a
permanent delete on one row at a time. Those five gestures are the five routes block 20 serves, so
none lands unwired.

**The sort selector's values differ per screen and its component does not either.** The grid and a
board offer the two values `PinSortStrategyInputEnum` declares; the bin's Pins tab offers those plus
`DELETED_AT_DESC`; the bin's Boards tab shows none. The component receives the list and the current
value and writes the search parameter.

**`PinUpdater` opens one transaction and composes no other.** `EbeanTransactionRunner.inTransaction`
calls `transaction.commit()` on whatever `beginTransaction()` returned, so a nested call that joined
the outer transaction would commit it, and nothing in this repository composes two of them today.
Block 15 removes the only callers of `PinTagger.setTags` and `PinBoardSetter.setBoards`, so block 10
splits each one's resolution and write from its `fenced` and calls those halves inside `PinUpdater`'s
own. No nesting, and nothing to measure about Ebean.
(Corrected in block 10, on the operator's review of PR #166: a nested `inTransaction` does **not**
commit the outer one. `beginTransaction()` returns the same `ScopedTransaction` and Ebean counts the
scopes, so only the outermost commit reaches the database, which
`EbeanTransactionRunnerTest`'s `Given a write in a nested inTransaction, Then a rollback of the outer
block discards it` has pinned since before this lot. Resolving outside the transaction was therefore
not a safeguard but the defect: a tag `TagCreator` invented was committed on its own and orphaned
when the write that asked for it then failed. `PinUpdater` wraps the resolutions and the save in one
`inTransaction`, the resolution halves of `PinTagger` and `PinBoardSetter` staying split, since one
route now writes what two used to.)

## 5. Blocks

| Block | Branch | Journeys |
|---|---|---|
| 10 | `feat/one-route-writes-a-pin` | None: no client path changes. `PinUpdaterIntegrationTest` covers the new route, one case per field, one for the empty `tags` and `boardIds` that clear, and one each for an unknown board and another user's board asserting 404 and 403 |
| 15 | `feat/the-sub-routes-go` | None. The two integration tests move onto the new route with every case kept, which is what proves nothing was served only by the routes removed |
| 20 | `feat/the-batch-routes` | None. Each route gets a case whose last identifier is another user's, asserting the status and then reading back that the first identifier's state is unchanged, which is how "nothing is written" is observed. `DELETE /api/v1/pins` with an empty body asserts 400, which fails loudly if the body never reaches the resource method |
| 25 | `feat/the-dead-setters-go` | None: no route and no client path reaches either method. `command grep` over `api/**/src/main` returning nothing but the definitions is what says they are dead, and the gate is silent about it because their unit tests still cover them, which is why the deletion is a block rather than a tier-1 fix |
| 30 | `feat/the-grid-chooses-its-order` | `choosing the grid's order`: the selector changes the request the grid sends and the order of the tiles, and the choice survives a reload of the same address |
| 40 | `feat/the-boards-have-a-screen` | `create a board and rename it`: a board created appears in the list, its new name survives a reload, and deleting it takes it out of the list. The credentials screen still carries the theme control and no navigation icon |
| 50 | `feat/a-board-has-a-grid` | `open a board and browse its pins`: the board's grid holds the pins the board holds and not the others, and loads a second page |
| 60 | `feat/a-pin-is-editable` | `edit a pin's description, tags and boards`: one request leaves, the dialog returns to reading, and the tile carries the new description without the grid refetching a page |
| 70 | `feat/a-pin-changes-its-image` | `replace a pin's image with a file`: one `PUT` leaves and the tile carries the new image; the fetch-again control is absent on a pin with no address and sends the pin's own address on a pin with one |
| 80 | `feat/the-recycle-bin` | `delete a pin and restore it from the recycle bin`: the tile leaves the grid with no page refetched, stands in the bin, and comes back |
| 90 | `feat/the-grid-consumes-its-selection` | `add several selected pins to a board`: two pins selected reach the board in one request, and the bar states the count |

Every journey above joins `REQUIRED_JOURNEYS` in `clients/apps/webapp/src/lib/journeys.ts`, in the
block that adds it. None is removed.

**The API's work is three blocks because of the budget, not the subject.** The production bound
under `api/` is a strict 200 and the diff bound is a strict 600, which test lines count against.
`PinTaggingIntegrationTest.kt` and `BoardMembershipIntegrationTest.kt` are 195 and 180 lines, and
block 15 rewrites both: that alone approaches the 600 before a production line is written. So block
10 adds the route and its use case, block 15 removes the two old ones and moves their tests, and
block 20 serves the five batch routes. Each is green and coherent alone, each raises the version its
own diff needs, and no block ends between a red test and the implementation that answers it.

**Blocks 30 to 90 are estimated between 100 and 350 production lines each under `clients/`**, against
a bound of 400. Block 90 is the one to watch: it touches three routes. If it lands over, the seam is
the bar and the two grids on one side, the bin's own selection on the other.

None of these figures is evidence: each block sums `git diff --numstat` against `main` at its first
green run and says so in its pull request, and a block that lands over a bound splits again.

**Block 50 extracts the grid out of `Home.tsx` and that is the block's real work.** `Home` holds the
window's drag listeners, the creation dialog, the pin dialog and the grid; only the grid and its
sort move. The drag gesture stays on the home screen, a board's grid not being a place a drop
creates a pin.

**Block 50 stands over both bounds and does not split, which is the operator's decision of
2026-09-20.** Measured at its tip, `git diff --numstat` against `main`: 609 lines total against a
strict 600, and 480 production lines under `clients/` against a strict 400. Of those 480, 357 are
one move: `components/PinGrid.tsx` created at 181 lines and `routes/Home.tsx` shortened by 176 of
the same code, which `git diff --numstat` counts on both sides. A split was measured and offered,
the extraction alone against the board's screen, at 358 and 251 lines; the operator declined it
because the budget measures what a human rereads and a reader of this block rereads the move once.
Nothing here was trimmed to fit, there being no padding to trim.

## 6. Adjacent backlog items

| Item | Exit |
|---|---|
| **What the API serves and the web application does not reach yet** (Features) | Closed in part and rewritten, not deleted, in block 90's pull request. The lot reaches boards, tags, the recycle bin, and editing and deleting a pin. The item stays for search, account management, import and export, and its pointer becomes this document |
| **Advanced pin / tag / board management** (Features) | Left open. It is the roadmap entry this lot's subject sits under and it names no work: what it asks for is a data model that is genuinely user segmented, which is the perceptual hash and the audience mechanics, not a screen. Closing it on the strength of screens built here would lose that |
| **Browser-extension CORS origin** (`P1`) | Left open, its reason unchanged: the extension has no stable identifier. Decision C changes the surface the extension will consume, which is why the ADR is written rather than the decision left here |
| **Import follow-ons** (`P1`) | Not adjacent. No block here touches the import |
| **A table rebuild's row-carrying path is exercised by nothing** (`P2`) | Not adjacent: no block adds a column or a table |
| **`foreign_keys` is off** (`P2`) | Not adjacent, and worth naming because blocks 10 to 20 write through existing relations rather than declaring new ones. Turning the pragma on is still the other lot's work |
| **Flatten the migration history** (Before beta) | Left open, and no block needs it |
| **Populate `contract/frozen/`** (Before beta) | Untouched. Decision H breaks the contract precisely because the directory is empty |
| **The grid keeps every page it scrolls** (Known limits) | A recorded limit, not work. Decision P is written against it rather than around it: no block here reloads a page to change one tile |
| **`RowMergedOutsideTransaction` reads a construction as an insert** (Known limits) | A recorded limit, not work, and named here because block 10 meets it at the gate: the rule's scope is every use case by path, and `PinUpdater` has exactly the shape it reads, a row rebuilt from an earlier read. Block 10 writes inside one `fenced`, which is what the rule asks for; the limit is unchanged |
| **Audience mechanics** (Features) | Left open, and the lot depends on it staying closed: every screen it builds is owner scoped |

This lot files one item, in block 90's pull request:

- **A board has no cover.** `/boards` is a list because `BoardOutputDto` carries no image and
  `GET /api/v1/boards` is not paginated. A tile grid needs either a cover field on that type or one
  request per board. See this document, decision N.

## 7. Out of scope

Each row names how a reader notices if it changed anyway.

| Not done | Observable |
|---|---|
| Search, by text or by tag | No call to `/api/v1/pins/search`; `/api/v1/tags/search` is called by the tag field alone |
| Account management: the profile, the password, deleting the account | No call to `/api/v1/me` or `/api/v1/me/password`, and no route under `/account` |
| Import and export | No call to `/api/v1/me/imports` or `/api/v1/me/exports` |
| A board's cover | `BoardOutputDto` is unchanged and `/boards` renders no image |
| Bulk permanent delete, in either bin | The contract gains no route under `/recycled` but the two `restore` ones |
| Per-identifier reporting on a batch route | No new output type under `dtos/output/` for a batch call |
| Reordering a board's pins by hand | No route and no control writes a position |
| The extension | `clients/apps/extension` absent |
| A browser in the gate | `command grep -n "playwright\|@vitest/browser" $(git ls-files '*package.json')` returns nothing |
| Any change to the drag gesture | `src/drops.ts` and `src/lib/drops.ts` are untouched by every block |

## 8. Pitfalls

- **A block that touches `contract/openapi.json` raises `info-version`, whatever it did.**
  `contract-guard` throws on `api-version-not-bumped`, which is not a severity but a rule name, so an
  addition is refused as loudly as a break. Decision H raises it three times for that reason.
- **The version is raised before the document is regenerated, not after.** The guard compares the
  generated document against `origin/main` and refuses a break whose version hides it, so the raise
  and the regeneration belong to one commit.
- **Nothing regenerates `contract/openapi.json` for you.** The gate refuses a stale document and
  names the command that refreshes it.
- **The generated client is what breaks first.** Removing two operations changes
  `clients/packages/api-client/src/schema.d.ts`, which is gitignored and rebuilt by `prepare`, so
  block 15's `pnpm typecheck` is what proves no call site used them. That is the check, not the grep
  in section 2.
- **`PinUpdater` must be one transaction and must not nest one.** `EbeanTransactionRunner` commits
  whatever `beginTransaction()` hands it, so a nested call that joined the outer transaction would
  commit it. Block 10 splits `PinTagger` and `PinBoardSetter` rather than calling them whole.
  (Corrected in block 10, on the operator's review of PR #166: nesting is safe and is what makes the
  write atomic. A nested `inTransaction` joins the outer scope and its commit does not reach the
  database, measured on a probe and pinned by two cases in `EbeanTransactionRunnerTest`. The pitfall
  is the opposite one: **anything resolved outside the transaction is committed on its own**, which
  is how a refused write left an orphan tag behind.)
- **A batch route resolves every identifier before its first write**, not inside the loop. Decision E
  is all or nothing, and a check interleaved with the writes makes it all or some.
- **An identifier that fails to resolve earns 404 or 403 wherever it was named**, the body included.
  The cost is real and accepted: RFC 9110, section 15.5.5, defines 404 by the target resource, so a
  `POST /api/v1/boards/{boardId}/pins` answering 404 over a `pinId` tells generic tooling that the
  endpoint is gone. The application never reads it that way, the problem response carrying the code
  that says which identifier failed.
- **An empty list is not an identifier that failed.** `@NotEmpty` answers 400, and that is the one
  place a batch route does not answer 404 or 403.
- **No control deletes an image.** `DELETE /api/v1/pins/{pinId}/image` stays in the contract and the
  web application does not call it: dropping the bytes ahead of a fetch that can fail is the failure
  decision M exists to avoid.
- **`removePins` lives in `lib/`.** The coverage bound is 100% of lines and branches over
  `src/lib/**` and nothing else, and a pure walk over `InfiniteData` pages is what `lib/` is for. A
  page left with an empty `pins` array is fine; a page dropped from the array breaks the cursor
  chain, and that is what the test has to fail on.
- **The sort belongs to the query key.** Two orders sharing one key make TanStack Query serve the
  first order's pages under the second, and the grid shows a page it never requested.
- **`rereadSettledPins` and `setPinImage` are module-private today.** Block 60 needs the first
  exported from `images.ts`; block 70 needs nothing new, `useSetPinImage` being the exported surface
  already.
- **A navigation icon put inside `AppHeader` reaches the credentials screen.** `Credentials.tsx`
  renders the same bar. `AppNav` is a child the signed-in routes pass, the way block 30 passes the
  sort selector.
- **A HeroUI control is a compound component**, `Tabs` and `Select` included. `clients/AGENTS.md`
  carries the rule; read the rendered DOM rather than guessing the shape.
- **The tag field's suggestions are the server's identities.** Typing a name that differs only by
  case or by an accent from one the author holds must offer that one, so the field queries before it
  decides it is looking at a new tag. `GET /api/v1/tags/search` documents none of this, which is why
  block 10 carries the fold's sentence onto `PUT /api/v1/pins/{pinId}`.
- **`sourceMediaUrl` is set on an uploaded pin too.** `CreatePinDialog` sends the address a drop
  carried whether or not the bytes came from it, and the server stores that field without ever
  fetching it. So the fetch-again control is live on such a pin, and it is worded as fetching from
  that address rather than as restoring anything.
- **`GET /api/v1/boards/recycled` takes no sort**, so the bin's Boards tab renders no selector.
  Passing one would write a search parameter no request reads.
