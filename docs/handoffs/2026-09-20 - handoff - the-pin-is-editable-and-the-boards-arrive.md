# Handoff: the pin is editable and the boards arrive

Date: 2026-09-20
Specification: `docs/specs/2026-09-20-the-pin-is-editable-and-the-boards-arrive.md`
ADRs: `docs/adr/0038-one-route-writes-a-pin.md`, `docs/adr/0039-a-batch-route-is-all-or-nothing.md`
Blocks: 10 `feat/one-route-writes-a-pin` (#166), 15 `feat/the-sub-routes-go` (#167), 20
`feat/the-batch-routes` (#168), 25 `feat/the-dead-setters-go` (#169), 30
`feat/the-grid-chooses-its-order` (#170), 40 `feat/the-boards-have-a-screen` (#171), 50
`feat/a-board-has-a-grid` (#172), 60 `feat/a-pin-is-editable` (#173), 70
`feat/a-pin-changes-its-image` (#174), 75 `feat/the-labels-follow-the-application` (#175), 80
`feat/the-recycle-bin` (#176), 85 `feat/a-pin-is-deleted` (#177), 90
`feat/the-grid-consumes-its-selection` (#178), and the closing block in two pull requests,
`fix/the-holistic-findings` (#179) and `fix/the-holistic-findings-documents` (#180).
Tier: Spec. The specification review ran before a line was written: 1 CRITICAL, 8 MAJOR, 7 MINOR,
all closed in the document. (Corrected in the closing block: the holistic review has run. 0
CRITICAL, 4 MAJOR, 13 MINOR, every one of them fixed, and the table below says where.)

## Current state

**A pin has the rest of its life.** It is created, read, edited, given another image, deleted and
brought back. Boards have a screen, a grid of their own, and a bar that files pins under them.

- **One route writes a pin.** `PUT /api/v1/pins/{pinId}` replaces `description`,
  `sourceContextUrl`, `sourceMediaUrl`, `tags` and `boardIds` in one transaction, and
  `PUT /{pinId}/tags` and `PUT /{pinId}/boards` are gone with it. An edit is therefore a
  read-modify-write and the last tab to save wins, which the ADR records as accepted.
- **Five batch routes act on many identifiers at one gesture**, all or nothing: an identifier that
  resolves to nothing earns 404 and another user's earns 403, wherever it was named, and nothing is
  written either way. The contract stands at `7.1.0`. (Corrected in the closing block: **`7.2.0`**,
  and the grammar has a third arm the ADR did not state, **409** over a recycled pin. All five
  routes now declare their `204` and their `400` by hand, SmallRye publishing a validation `400`
  for a `POST` with a validated body and not for a `DELETE`.)
- **`BOARD_INVALID_MEMBERSHIP` is gone.** A board named in a body now earns what a board named in a
  path earns. It existed to hide which of the two happened, and identifiers are random version 4
  UUIDs.
- **Every grid chooses its order**, and the choice lives in the route's search parameters, so a
  reload and the back button keep it with nothing stored. The default is newest first, which is not
  the API's default.
- **The grids act on their selection.** A bar appears over any grid whose selection is not empty:
  on the catalogue, file the pins under a board or delete them; on a board's own grid, take them out
  of that board as well; in either bin, restore them. `/boards` carries no bar, a board being
  deleted from its own row.
- **A delete, a removal from a board and a restore edit the cached pages in place.** The grid keeps
  every page it scrolls (ADR 0033), so invalidating to change one tile would refetch every page
  loaded. `lib/tiles.ts` holds the two pure walks, `replacePins` and `removePins`.
- **The strings react-aria writes itself follow the application, not the browser.** An
  `I18nProvider` on paraglide's `getLocale()` wraps the tree at the composition root.
- **Every signed-in screen carries the same bar**: the three navigation icons, the task centre and
  sign-out, all of them in `AppNav`. The closing block moved the last two out of `Home`, where a
  download started by a drop had nothing reporting it once the user left that screen.

## Where the pieces live

- `api/api-usecases/.../PinUpdater.kt`: the whole-pin write, one `inTransaction` wrapping the
  resolutions and the save. `PinTagger` and `PinBoardSetter` keep their resolution halves alone.
- `clients/apps/webapp/src/components/PinGrid.tsx`: the virtualiser, the tile, the pin dialog and
  the selection's own gestures. Home and a board's screen render the same component.
- `clients/apps/webapp/src/components/SelectionBar.tsx`: the bar, the tick a row carries, and
  `useSelection`, the hook that holds the keys and resolves the keyboard's select-all.
- `clients/apps/webapp/src/components/PinEditForm.tsx`: the fields, the tag field over
  `GET /api/v1/tags/search`, the board select, and the one image choice of three.
- `clients/apps/webapp/src/components/ImageDropBox.tsx`: the drop area both dialogs give an image
  through, the drag counting and the judging with it. Added by the closing block, out of the copy
  the edit form had made of the creation dialog's.
- `clients/apps/webapp/src/routes/Recycled.tsx`: both tabs, their rows, their selections and the
  emptying that asks first.
- `clients/apps/webapp/src/boards.ts`, `recycled.ts`, `pins.ts`: the queries and the writes, each
  one saying which cache it edits and which it reloads.

## The holistic review's findings

`.reviews/the-pin-is-editable-and-the-boards-arrive-holistic.md`, over
`lot/0.29.0-the-handshake-publishes-the-media-types..origin/main`: 0 CRITICAL, 4 MAJOR, 13 MINOR.
**Every one took the default exit and was fixed**, none refused, none filed, none accepted as a
limit. The closing block is two pull requests, the code on one side (#179) and this document, the
backlog, the ADR and the comment pointers on the other (#180), the first half standing at 563
counted lines against a strict 600 and 392 production lines under `clients/` against a strict 400.

| Severity | Finding | Exit |
|---|---|---|
| MAJOR | Editing a pin out of a board left its tile in that board's grid | Fixed in #179. The saved pin names the boards it is still in, and every other board's catalogue drops it. The edit journey gained a case on `/boards/$boardId` |
| MAJOR | A board's `pinCount` went stale on three of five writes | Fixed in #179 |
| MAJOR | `ReplaceField` duplicated `CreatePinDialog`'s drop area, comments included | Fixed in #179. One `ImageDropBox` carries the box, the drag counting, the judging and the file input |
| MAJOR | The five batch routes did not publish one contract | Fixed in #179, by the review's second branch: an `@APIResponse` alone does not bring the `400` back on a `DELETE`, so all five declare it. Contract `7.2.0` |
| MINOR | `updatePin` carried `@Valid` alone, so a bodyless `PUT` answered 500 | Fixed in #179, and wider than asked. The review proposed a backlog item for the bodies that predate the lot; there are **seven**, not nine, and they are fixed in place with a Konsist assertion that keeps them fixed. Nothing was filed |
| MINOR | ADR 0039 said "four" batch bodies | Corrected in #180: five, named |
| MINOR | ADR 0039's decision 2 named 404 and 403 and not 409 | Corrected in #180, and the bulk case over a recycled pin is in `BoardMembershipIntegrationTest` (#179) |
| MINOR | `DELETE /api/v1/boards/{boardId}/pins` had no empty-body case | Fixed in #179 |
| MINOR | A failed reread rejected a write the server had accepted | Fixed in #179 |
| MINOR | `BoardRefusal.nameTaken` meant a name taken on one path of four | Fixed in #179. The other three throw a plain `Error` |
| MINOR | Three `Field` components, one deciding `isRequired` from a magic string | Fixed in #179 |
| MINOR | Two collection shapes inside `Recycled.tsx` | Fixed in #179 |
| MINOR | The task centre's tab count became "reachable within ten stops" | Fixed in #179: six stops, which is the home header's own order |
| MINOR | "Specification" named two documents, and a bare decision letter three | Fixed in #180. The first pointer to a document in a file carries its date |
| MINOR | Sign-out and the task centre were the home screen's alone | Fixed in #179. Both sit in `AppNav`, which every signed-in screen renders |
| MINOR | This document's three errors | Corrected in #180, in the `(Corrected: ...)` clauses above |
| MINOR | `agents/reviews/holistic.md` item 10 asked for a check nothing can fail | Fixed in #180: the item now asks what the norm asks, a test that exists and coverage that is genuine |

## Pitfalls

- **A nested `inTransaction` is safe and is what makes a write atomic.** `beginTransaction()` hands
  back the same `ScopedTransaction` and Ebean counts the scopes, so only the outermost commit
  reaches the database. The opposite belief is what block 10 was pushed with and the operator
  refused: **anything resolved outside the transaction is committed on its own**, which is how a
  refused write left an orphan tag behind.
- **An empty `DELETE` body answered 500, not 400.** RESTEasy Reactive binds no body it did not
  receive and hands the resource method a null entity, and Kotlin's non-null intrinsic throws before
  any validation runs. `@NotNull` beside `@Valid` on the four batch bodies is what makes the
  sentence true. (Corrected in the closing block: **five** batch bodies, and the pair is now on
  every validated body in the presentation module, the seven that predate this lot included.
  `ArchitectureKonsistTest` refuses a `@Valid` parameter carrying no `@NotNull`, so the next route
  cannot repeat it.)
- **The pins query key carries the board and the order.** `setQueryData(["pins"], ...)` reaches
  nothing; every write in `pins.ts` and `boards.ts` goes through `setQueriesData` or invalidates by
  prefix. This was a live defect twice, in block 30 and again in block 85.
- **A selection checkbox is named by the row it sits in**, react-aria putting its own `Select` in
  front of the row's label. A test that queries it by an exact string is querying react-aria's
  internal wording; match the row's own name instead.
- **`GET /api/v1/boards/recycled` takes no cursor and no sort**, so the bin's Boards tab renders no
  selector and chases no page.
- **A HeroUI control is a compound component**, `Tabs`, `Select` and `Checkbox` included. Read the
  rendered DOM rather than guessing the shape.
- **The gate renders nothing and sees no layout.** Every client block of this lot was read in a
  headless browser against the built bundle, and that reading caught a defect in most of them.

## What is not validated

- **No browser is in the gate**, so nothing keeps a layout defect from landing after the block that
  introduced it. The reading is a workstation habit, not a check.
- **A batch route's refusal was never exercised against a real API from the client.** The journeys
  stub the refusal; the routes' own all-or-nothing behaviour is covered by the API's integration
  tests alone.
- **Adding pins to a board leaves the tiles' `boards` array stale** in the pages already loaded,
  until something rereads them. The board's own catalogue and the pin counts are marked stale at
  once; nothing on screen shows the difference. (Corrected in the closing block: that last sentence
  was true of the two membership calls and of nothing else. `useUpdatePin`, `useRecyclePins` and
  `useRestorePins` moved a board's `pinCount` and marked nothing stale, `countActivePinsInBoard`
  counting the active pins a board holds; all three now invalidate `["boards"]`.)
- **One journey case sits close to the container's test timeout.** `Given the form, Then one request
  carries the whole pin and the tile follows it` types 26 characters, each keystroke of the tag
  field costing a request, and the closing block added a round trip to the save. It took 647 ms on
  the workstation and timed out once at the default 5000 ms inside `dagger call gate`, then passed
  on the rerun and on continuous integration. Nothing raised the timeout: a number chosen to make a
  flake go away is not a measurement.

## Next step

Wrap: the holistic review over `git diff lot/0.29.0-the-handshake-publishes-the-media-types..origin/main`,
then the closing block with its findings, the backlog reconciled and this document corrected, then
the lot tag. (Corrected in the closing block: all of it is done but the tag, which the lead pushes
on this pull request's merge as an annotated `lot/0.30.0-the-pin-is-editable-and-the-boards-arrive`.
The backlog was reconciled and **needed no edit**: block 90's rewrite of the Features item holds
against what shipped, no call reaching `/api/v1/pins/search`, `/api/v1/me`, `/api/v1/me/password`,
`/api/v1/me/imports` or `/api/v1/me/exports`, and "A board has no cover" is still true of
`BoardOutputDto`. No finding took the backlog exit.)

What the next lot inherits: search, account management, import and export are what the API serves
and the web application does not reach, which is the backlog's own Features item and the place to
start.
