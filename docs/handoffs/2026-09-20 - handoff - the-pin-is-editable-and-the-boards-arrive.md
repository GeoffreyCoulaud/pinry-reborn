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
`feat/the-grid-consumes-its-selection`
Tier: Spec. The specification review ran before a line was written: 1 CRITICAL, 8 MAJOR, 7 MINOR,
all closed in the document. The holistic review has not run yet; it is Wrap's first step, and the
closing block corrects this document with what it produced.

## Current state

**A pin has the rest of its life.** It is created, read, edited, given another image, deleted and
brought back. Boards have a screen, a grid of their own, and a bar that files pins under them.

- **One route writes a pin.** `PUT /api/v1/pins/{pinId}` replaces `description`,
  `sourceContextUrl`, `sourceMediaUrl`, `tags` and `boardIds` in one transaction, and
  `PUT /{pinId}/tags` and `PUT /{pinId}/boards` are gone with it. An edit is therefore a
  read-modify-write and the last tab to save wins, which the ADR records as accepted.
- **Five batch routes act on many identifiers at one gesture**, all or nothing: an identifier that
  resolves to nothing earns 404 and another user's earns 403, wherever it was named, and nothing is
  written either way. The contract stands at `7.1.0`.
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

## Where the pieces live

- `api/api-usecases/.../PinUpdater.kt`: the whole-pin write, one `inTransaction` wrapping the
  resolutions and the save. `PinTagger` and `PinBoardSetter` keep their resolution halves alone.
- `clients/apps/webapp/src/components/PinGrid.tsx`: the virtualiser, the tile, the pin dialog and
  the selection's own gestures. Home and a board's screen render the same component.
- `clients/apps/webapp/src/components/SelectionBar.tsx`: the bar, the tick a row carries, and
  `useSelection`, the hook that holds the keys and resolves the keyboard's select-all.
- `clients/apps/webapp/src/components/PinEditForm.tsx`: the fields, the tag field over
  `GET /api/v1/tags/search`, the board select, and the one image choice of three.
- `clients/apps/webapp/src/routes/Recycled.tsx`: both tabs, their rows, their selections and the
  emptying that asks first.
- `clients/apps/webapp/src/boards.ts`, `recycled.ts`, `pins.ts`: the queries and the writes, each
  one saying which cache it edits and which it reloads.

## Pitfalls

- **A nested `inTransaction` is safe and is what makes a write atomic.** `beginTransaction()` hands
  back the same `ScopedTransaction` and Ebean counts the scopes, so only the outermost commit
  reaches the database. The opposite belief is what block 10 was pushed with and the operator
  refused: **anything resolved outside the transaction is committed on its own**, which is how a
  refused write left an orphan tag behind.
- **An empty `DELETE` body answered 500, not 400.** RESTEasy Reactive binds no body it did not
  receive and hands the resource method a null entity, and Kotlin's non-null intrinsic throws before
  any validation runs. `@NotNull` beside `@Valid` on the four batch bodies is what makes the
  sentence true.
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

- **The holistic review has not run.** It is Wrap's first step and its findings all go to the
  closing block.
- **No browser is in the gate**, so nothing keeps a layout defect from landing after the block that
  introduced it. The reading is a workstation habit, not a check.
- **A batch route's refusal was never exercised against a real API from the client.** The journeys
  stub the refusal; the routes' own all-or-nothing behaviour is covered by the API's integration
  tests alone.
- **Adding pins to a board leaves the tiles' `boards` array stale** in the pages already loaded,
  until something rereads them. The board's own catalogue and the pin counts are marked stale at
  once; nothing on screen shows the difference.

## Next step

Wrap: the holistic review over `git diff lot/0.29.0-the-handshake-publishes-the-media-types..origin/main`,
then the closing block with its findings, the backlog reconciled and this document corrected, then
the lot tag.
