# The selection starts a board

Date: 2026-09-27
Status: Draft for the operator. One specification review ran, `.reviews/the-selection-starts-a-board-spec.md`,
its 3 MAJOR and 11 MINOR closed in this document. Frozen when the lot's last block merges.
Branches: one stack: 10 `feat/a-board-is-created-with-its-pins`, 20 `feat/the-selection-starts-a-board` on 10.
(Corrected: blocks 13 `refactor/board-membership-in-bulk` and 16 `refactor/recycle-bin-in-bulk` were inserted
between 10 and 20 after the operator's review of #242, decision H; 20 now stacks on 16.)
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
resolved by `PinBoardSetter`'s rules, then the board saved, then each pin saved with the board added. *(Corrected by
decision H: one `addPinsToBoard` call files them.)* The resolution
is made public beside `resolveBoards`, as `resolvePins`. The `BoardNameAlreadyTakenException` catch stays outside
the transaction, so the holder is read after the rollback, as `BoardUpdater` already does. Duplicated identifiers are
filed once. Every refusal of section D happens before the first write; the transaction guards a write failing after
the board is saved.

**D. The refusals are the ones each rule already earns.** `POST /api/v1/boards` adds 403 `PinForbidden` and 404
`PinInBodyNotFound`, both shared already. Its 409 is declared inline on `createBoard`, as `softDeleteBoard`'s is,
carrying `BOARD_NAME_ALREADY_EXISTS` and `PIN_ALREADY_SOFT_DELETED`: a shared response needs two users and this has
one. The 201 answers `pinCount` as the number of distinct pins filed. *(Corrected: the shared `BoardNameTaken` was
left with one user, `PUT`, so the closing block inlined that 409 too and both routes describe the name alike.)*

**E. The contract goes to `20.0.0`.** A value added to the 409's `code` enum is
`response-property-enum-value-added` for `oasdiff` v1.31.0, an error; the review reproduced it on a copy of the
contract, with 3 info besides (`new-optional-request-property`, `response-non-success-status-added` twice).
*(Corrected: block 10 shipped a fifth change, `request-property-pattern-added` on the tags of
`PUT /api/v1/pins/{pinId}`: the compiler flag of block 10's row made a blank tag refused there.)*

**F. `BoardForm` moves to `components/BoardForm.tsx`** and takes an optional `pinIds`, so the boards screen and the
selection bar share one form. "New board…" is the menu's first item and opens it in a modal; a success closes the
modal and clears the selection. `BoardRefusal` reads the problem body's `code` rather than the status, so a
recycled pin in the selection shows the general refusal and not the name-taken sentence; both keep the selection.
The menu's `renderEmptyState` is never reached any more and goes; `boards_empty` stays, the boards screen reading it.

**G. A contract break is acceptable; a departure from REST is the price of avoiding one.** Weighed on this lot: a
separate `POST /api/v1/boards/with-pins` would have kept `19.x` by putting a body variant in the path, which the
operator refused. When a break is really unwanted, the operator may accept such a departure case by case, as the
contract already carries some (`…/restore`, `/tags/search`, `DELETE` with a body). Neither option was taken for the
refusal codes themselves: ADR 0042, decision 3, stands. `agents/engineering.md` carries the rule.

**H. A batch write costs a constant number of reads and writes only what changes.** The operator's review of #242:
the five batch loops (`BoardCreator`, `PinBoardSetter`'s add and remove, `PinRecycleBin`'s `softDeleteAll` and
`restoreAll`) called `savePin`, `softDeletePin` or `restorePin` once per pin, each rewriting and rereading the whole
pin. `PinRepositoryInterface` gains bulk methods: the pins read in one typed query-bean query (`id.isIn`), the loaded
models changed and saved together (`saveAll`, which Ebean sends as a JDBC batch and which writes the changed
properties alone), missing join rows saved as models the same way, join rows removed by a query-bean `.delete()`.
Nothing is written as SQL or as a property named by a string (`asUpdate().set(…)`, `setRaw`), so a renamed column
fails to compile. No model carries `@Cache`, so no second-level cache is involved; writes go through the beans the
transaction loaded, so its persistence context holds nothing stale. All or nothing still holds: every pin is
resolved before the first write, inside one transaction. *(Corrected: one bound parameter per identifier meets the
embedded SQLite's cap of 250 000, so the closing block bounds the batch bodies' lists at 10 000 identifiers, a 400
past it, the operator's answer "a" to the second holistic review.)*

## 4. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/a-board-is-created-with-its-pins` | `BoardCreator` tests: pins given are filed under the new board; a pin of another account, an unknown one and a recycled one each throw their `PinBoardSetting` error and no board is saved; with a recording `TransactionRunner` fake, `saveBoard` and every `savePin` run inside `inTransaction`. *(Corrected by decision H: one `addPinsToBoard` call files them.)* A unit test builds `BoardCreationInputDto` with and without `pinIds`, for the default's branch. Integration tests on `POST /api/v1/boards`: two `pinIds` answer 201, `pinCount` 2, and the board's pins route lists both; one pin named twice answers `pinCount` 1; one unknown identifier answers 404 and `GET /api/v1/boards` holds no board of that name; a body without the `pinIds` key answers 201 and `pinCount` 0, as today; `"pinIds": [null]` answers 400, and if it answers 500 `List<@NotNull UUID>` goes on both DTOs in this block. `oasdiff changelog` against `main` lists exactly E's four changes, all on `POST /api/v1/boards`, and none on `PUT /api/v1/boards/{boardId}`; `info-version` is `20.0.0`. The web application's `useCreateBoard` compiles against the regenerated schema unchanged. `pinIds` has its client consumer in block 20. Carries this specification, ADR 0039's two correction lines, and decision G's rule as one bullet of `agents/engineering.md`, "This project's API contract". *(Corrected: `[null]` answered 500, Kotlin dropping a type argument's annotation from the bytecode. Block 10 first put `List<@NotNull UUID>` on four DTOs with `-Xemit-jvm-type-annotations`; after the operator's review of #242 ("H -> a"), Jackson's `NewStrictNullChecks` refuses the null from the Kotlin type instead, as `MALFORMED_BODY`, and the element annotations went. The flag stays build-wide ("A.") for the tags' `List<@NotBlank String>`, so `PUT /api/v1/pins/{pinId}` now refuses a blank tag, `oasdiff` listing that as a fifth change.)* |
| 13 | `refactor/board-membership-in-bulk` | Decision H, for the three membership writes (`BoardCreator`, `PinBoardSetter`'s add and remove). A persistence test captures the SQL (Ebean's logged statements) of filing one pin and of filing ten: the number of reads does not grow with the pins, and each pin's `UPDATE` sets `updated_at` alone. The existing integration tests of the three routes pass unchanged. No `asUpdate().set(…)` nor `setRaw` in the diff. |
| 16 | `refactor/recycle-bin-in-bulk` | Decision H, for `PinRecycleBin.softDeleteAll` and `restoreAll`: the same SQL capture, each `UPDATE` setting `soft_deleted_at` and `updated_at` alone; the existing integration tests of `DELETE /api/v1/pins` and `POST /api/v1/pins/recycled/restore` pass unchanged. |
| 20 | `feat/the-selection-starts-a-board` | A journey, "add selected pins to a new board": two tiles selected, "New board…", a name, submit; the request carries both `pinIds`, the selection bar is gone and the address is still the grid's. The fake's POST answers `pinCount` from `pinIds.length`, so the boards screen's count only shows the list being read again. A 409 with `BOARD_NAME_ALREADY_EXISTS` shows the name-taken sentence, a 409 with `PIN_ALREADY_SOFT_DELETED` the general refusal, and the selection stays in both. The boards screen's journeys pass unchanged with the moved form. Read headless before the push, both themes. |

## 5. Adjacent backlog items

No adjacent item. Three were considered: "A board has no cover" concerns how `/boards` shows a board, not how one
is made; the CORS origin and the import follow-ons touch neither route nor screen.

## 6. Out of scope

- **Renaming with pins.** `PUT` is unchanged: observed as `BoardInputDto` still its body and no `oasdiff` change on
  it.
- **Opening the new board after creating it.** Observed as block 20's journey still on the grid after the success.
