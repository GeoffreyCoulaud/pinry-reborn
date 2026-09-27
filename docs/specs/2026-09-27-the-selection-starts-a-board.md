# The selection starts a board

Date: 2026-09-27
Status: Draft for the operator. One specification review ran, `.reviews/the-selection-starts-a-board-spec.md`,
its 3 MAJOR and 11 MINOR closed in this document. Frozen when the lot's last block merges.
Branches: one stack: 10 `feat/a-board-is-created-with-its-pins`, 20 `feat/the-selection-starts-a-board` on 10.
ADR: none new. Decisions A and B extend `docs/adr/0039-a-batch-route-is-all-or-nothing.md`, decisions 1 and 3,
which block 10 records there by a correction line each; the rest follows ADR 0039 and
`docs/adr/0042-the-presentation-owns-the-refusal-codes.md` as they stand.

## 1. Goal

A selection of pins goes to a board that does not exist yet in one gesture: the selection bar's "Add to a board"
menu offers "New board…", which asks for a name and a description and files the selection under the board it
creates. All or nothing: a refused pin leaves no empty board behind.

## 2. What exists today

- `POST /api/v1/boards` creates an empty board from `BoardInputDto` (`name`, `description`), which
  `PUT /api/v1/boards/{boardId}` shares to rename one. `BoardCreator.create` saves it and turns
  `BoardNameAlreadyTakenException` into `BoardNameAlreadyExistsError`, reading the name's holder after the refusal.
- `POST /api/v1/boards/{boardId}/pins` files pins under an existing board, all or nothing (ADR 0039):
  `PinBoardSetter` resolves every pin (403 another account's, 404 unknown, 409 recycled) before the first write.
- The web application's menu lists existing boards only (`PinGrid.tsx`, `PinGestures`). `BoardForm`, private to
  `routes/Boards.tsx`, creates or renames a board. `BoardRefusal` reads the status alone: `nameTaken` is
  `status === 409` (`boards.ts:27`), which holds today because a board write's only 409 is the name.

## 3. Decisions

Each is the operator's answer of 2026-09-27 in Discuss, or its consequence.

**A. One request, on the route that creates a board.** Chaining the two existing routes leaves an empty board when
the second fails, which ADR 0039 exists to prevent. This is a second path for filing pins in bulk beside the one
ADR 0039, decision 1, names: a creation route may carry the members it is created with.

**B. Creation gets its own body, `BoardCreationInputDto`**: `name` and `description` as `BoardInputDto` constrains
them, and `pinIds: List<UUID>` defaulting to the empty list, so it publishes as optional. `PUT` keeps
`BoardInputDto`, where a list of pins would mean nothing. The empty list is allowed: this is a write of one board,
not a batch, which extends ADR 0039, decision 3, from a write of one pin to a write of one resource.

**C. `BoardCreator.create` takes `pinIds`, defaulting to the empty list, and writes in one transaction**: every pin
resolved by `PinBoardSetter`'s rules, then the board saved, then each pin saved with the board added. The resolution
is made public beside `resolveBoards`, as `resolvePins`. The `BoardNameAlreadyTakenException` catch stays outside
the transaction, so the holder is read after the rollback, as `BoardUpdater` already does. Duplicated identifiers are
filed once. Every refusal of section D happens before the first write; the transaction guards a write failing after
the board is saved.

**D. The refusals are the ones each rule already earns.** `POST /api/v1/boards` adds 403 `PinForbidden` and 404
`PinInBodyNotFound`, both shared already. Its 409 is declared inline on `createBoard`, as `softDeleteBoard`'s is,
carrying `BOARD_NAME_ALREADY_EXISTS` and `PIN_ALREADY_SOFT_DELETED`: a shared response needs two users and this has
one. The 201 answers `pinCount` as the number of distinct pins filed.

**E. The contract goes to `20.0.0`.** A value added to the 409's `code` enum is
`response-property-enum-value-added` for `oasdiff` v1.31.0, an error; the review reproduced it on a copy of the
contract, with 3 info besides (`new-optional-request-property`, `response-non-success-status-added` twice).

**F. `BoardForm` moves to `components/BoardForm.tsx`** and takes an optional `pinIds`, so the boards screen and the
selection bar share one form. "New board…" is the menu's first item and opens it in a modal; a success closes the
modal and clears the selection. `BoardRefusal` reads the problem body's `code` rather than the status, so a
recycled pin in the selection shows the general refusal and not the name-taken sentence; both keep the selection.
The menu's `renderEmptyState` is never reached any more and goes; `boards_empty` stays, the boards screen reading it.

## 4. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/a-board-is-created-with-its-pins` | `BoardCreator` tests: pins given are filed under the new board; a pin of another account, an unknown one and a recycled one each throw their `PinBoardSetting` error and no board is saved; with a recording `TransactionRunner` fake, `saveBoard` and every `savePin` run inside `inTransaction`. A unit test builds `BoardCreationInputDto` with and without `pinIds`, for the default's branch. Integration tests on `POST /api/v1/boards`: two `pinIds` answer 201, `pinCount` 2, and the board's pins route lists both; one pin named twice answers `pinCount` 1; one unknown identifier answers 404 and `GET /api/v1/boards` holds no board of that name; a body without the `pinIds` key answers 201 and `pinCount` 0, as today; `"pinIds": [null]` answers 400, and if it answers 500 `List<@NotNull UUID>` goes on both DTOs in this block. `oasdiff changelog` against `main` lists exactly E's four changes, all on `POST /api/v1/boards`, and none on `PUT /api/v1/boards/{boardId}`; `info-version` is `20.0.0`. The web application's `useCreateBoard` compiles against the regenerated schema unchanged. `pinIds` has its client consumer in block 20. Carries this specification and ADR 0039's two correction lines. |
| 20 | `feat/the-selection-starts-a-board` | A journey, "add selected pins to a new board": two tiles selected, "New board…", a name, submit; the request carries both `pinIds`, the selection bar is gone and the address is still the grid's. The fake's POST answers `pinCount` from `pinIds.length`, so the boards screen's count only shows the list being read again. A 409 with `BOARD_NAME_ALREADY_EXISTS` shows the name-taken sentence, a 409 with `PIN_ALREADY_SOFT_DELETED` the general refusal, and the selection stays in both. The boards screen's journeys pass unchanged with the moved form. Read headless before the push, both themes. |

## 5. Adjacent backlog items

No adjacent item. Three were considered: "A board has no cover" concerns how `/boards` shows a board, not how one
is made; the CORS origin and the import follow-ons touch neither route nor screen.

## 6. Out of scope

- **Renaming with pins.** `PUT` is unchanged: observed as `BoardInputDto` still its body and no `oasdiff` change on
  it.
- **Opening the new board after creating it.** Observed as block 20's journey still on the grid after the success.
