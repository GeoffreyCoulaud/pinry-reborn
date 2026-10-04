# The boards wear their cover

Date: 2026-10-05
Status: Accepted by the operator on 2026-10-05. One specification review ran, `.reviews/the-boards-wear-their-cover-spec.md`,
its 1 CRITICAL, 5 MAJOR and 9 MINOR closed in this document. Frozen when the lot's last block merges.
Lot: `0.47.0`.
Branches: one stack: 10 `feat/a-board-carries-its-cover`, 20 `feat/the-boards-are-a-grid` on 10.
ADR: none new. The server writing a media's address for the client follows `PinMediaStateDto.url`, and an additive
field is a minor.

## 1. Goal

`/boards` becomes a grid of square tiles, each showing one image from the board, so a board is picked at a glance
rather than by reading its name. Closes the backlog item "A board has no cover".

## 2. What exists today

- `BoardOutputDto` carries `id`, `name`, `description` and `pinCount`; `Board.toDto(pinCount)` builds it at five
  call sites, four in `BoardController` and one in `BoardRecycleBinController`, for five operations: `POST` and
  `GET /api/v1/boards`, `GET` and `PUT /api/v1/boards/{boardId}`, and `POST /api/v1/boards/recycled/{boardId}/restore`.
- `GET /api/v1/boards` is not paginated and calls `BoardGetter.countActivePinsForUserBoard` once per board, which
  reads the board again to check its owner, then counts.
- `PinBoardModel` is an entity with two `@ManyToOne` and no timestamp column (`dbmigration/1.7.sql`).
  `MediaModel.pin` is a read-only `@ManyToOne` over `pin_id`; nothing navigates from a pin to its media.
- A pin's media is served at `/api/v1/pins/{pinId}/media`, a string `PinMediaStateMapper` writes inline. `?size=`
  sets the rendition's shortest side; left unsaid, `animated` is true for an image and false for a video, which then
  gives its poster (`GetPinMediaRendition`).
- The web application builds a tile's address with `tileMediaSource(url, rendition)` (`lib/tiles.ts`), and
  `RenditionImage` swaps an image that fails to load for "preview unavailable". `/boards` (`routes/Boards.tsx`) is a
  react-aria `GridList` of rows: name as a link, description, count, rename and delete.
- `clients/packages/api-client/src/schema.d.ts` is generated from the committed contract at install, so a required
  property added to a response becomes required in the client's types.

## 3. Decisions

Each is the operator's answer of 2026-10-05 in Discuss.

**A. The cover is derived, never chosen.** It is the board's newest pin, by `(createdAt, id)` descending, the web
grid's default order, among the pins filed under that board that are not in the recycle bin and have a media. No pin
qualifies: no cover. One image, not a mosaic of four: the same rule with four times the bytes, unreadable at a tile's
size.

**B. Newest by the pin's creation, not by its filing under the board.** A filing date costs a column on
`PinBoardModel`, a migration, both write sites, and a decision on whether the export carries it. Every existing
filing would be backfilled with its pin's `createdAt`, having no date of its own, so the two orders only diverge for
an old pin filed after the migration. The operator's answer, asked again after the review found the first reason
false.

**C. `BoardOutputDto` gains `coverUrl: String?`**, required and nullable like `PinOutputDto.sourceContextUrl`: the
media's address, or null. The server writes the address so the client does not rebuild it from an identifier. The
address moves into one function that `PinMediaStateMapper` and `BoardMapper` both call. All five operations carry it.

**D. `BoardGetter` reads the count and the cover under one ownership check**, replacing
`countActivePinsForUserBoard`, so each board costs one query more than today: the cover's lookup, one typed query
from `QMediaModel` filtered by a `QPinBoardModel` subquery on the board. Batching the list's per-board queries is not
this lot's subject.

**E. The tile is square, the image cropped to fill it**: CSS `aspect-ratio: 1` on the tile, `object-fit: cover` on
the image. `?size=` sets the shortest side, the side a square fills, so no rendition is smaller or larger than the
square needs on that side.

**F. The cover never animates.** The tile asks for `animated=false`: an animated image shows a still frame and a
video its poster. A grid of boards is read at a glance, and several tiles moving at once defeat that.

**G. The contract goes to `22.1.0`.** `oasdiff` v1.31.0 classes the addition as `response-required-property-added`
at INFO on the five operations, and `openapi-fetch` validates nothing at runtime. The guard does not require the
minor bump; the author makes it.

## 4. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/a-board-carries-its-cover` | Decisions A to D and G. |
| 20 | `feat/the-boards-are-a-grid` | Decisions E and F. |

### Block 10

- A persistence test of the cover's lookup, on one board: the newest pin is in the recycle bin, the next has no
  media, the third has one; a newer pin with a media is filed under another board only. The lookup answers the third.
  Two pins sharing a `createdAt`, with fixed identifiers `00000000-0000-0000-0000-000000000001` and `…02`, answer
  `…02`. A board whose pins all lack a media answers null, and so does an empty board.
- An integration test per operation: a board holding a pin with a media answers `coverUrl`
  `/api/v1/pins/{pinId}/media` on each of the five. On `GET /api/v1/boards`, an empty board answers `coverUrl` null,
  the key present.
- `BoardControllerTest` and `BoardRecycleBinControllerTest` stub the new getter.
- `oasdiff changelog` against `main` lists five `response-required-property-added` at INFO and nothing else;
  `jq -r .info.version contract/openapi.json` prints `22.1.0`.
- The web application's fixture `board()` (`test/app.tsx`) answers `coverUrl: null`, or the clients' typecheck fails.
  It is a fixture: `coverUrl`'s consumer is block 20.
- Carries this specification.

### Block 20

- A journey, "the boards screen shows each board's cover": the fake answers one board with a `coverUrl` and one with
  null. The first tile's image asks for that address with `size=SMALL` and `animated=false`, through
  `RenditionImage`; the second shows the placeholder, carrying the tile's square class, and no image.
- `GridList` takes `layout="grid"`, so the arrow keys move in both directions.
- Regression guard: the boards screen's existing journeys (create, rename, delete, open a board) pass with the tile
  replacing the row.
- Read headless before the push; the four screenshots (two themes by a phone's and a desktop's width) go in the pull
  request.
- Deletes the backlog item "A board has no cover".

## 5. Adjacent backlog items

No adjacent item. "Advanced pin / tag / board management" is unscoped; none of the others touch `/boards`.

## 6. Out of scope

- **Choosing a cover.** Observed as no `request-property-added` in block 10's `oasdiff changelog`.
- **Batching the list's per-board queries** (decision D). Observed as `listBoards` still mapping board by board.
