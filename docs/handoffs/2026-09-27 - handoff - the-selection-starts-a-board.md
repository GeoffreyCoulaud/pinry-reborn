# Handoff: the selection starts a board

Date: 2026-09-27
Tier: Spec. Specification `docs/specs/2026-09-27-the-selection-starts-a-board.md`. One stack: block 10
`feat/a-board-is-created-with-its-pins` (PR #242), block 20 `feat/the-selection-starts-a-board`. Written in block 20;
the closing block corrects it after the holistic review. (Corrected: block 20 is PR #243; the closing block is
`fix/the-selection-starts-a-board-closes`, on block 20. The holistic review ran; see "The holistic review".
After the operator's review of #242, blocks 13 `refactor/board-membership-in-bulk` (PR #246) and 16
`refactor/recycle-bin-in-bulk` (PR #247) were inserted between 10 and 20, the specification's decision H; 20 now
stacks on 16. The closing block is PR #245. A second holistic review read 13 and 16; see "The second holistic
review".)

## Current state

- **`POST /api/v1/boards` creates a board with the pins it files**, from `BoardCreationInputDto` (`name`,
  `description`, optional `pinIds`), all or nothing in one transaction. `PUT` keeps `BoardInputDto`. The 201 answers
  the distinct pins filed as `pinCount`.
- **Its refusals are the ones each rule already earns**: 403 `PinForbidden`, 404 `PinInBodyNotFound`, and a 409
  declared inline carrying `BOARD_NAME_ALREADY_EXISTS` and `PIN_ALREADY_SOFT_DELETED`. The contract is at `20.0.0`.
  (Corrected: `PUT /api/v1/boards/{boardId}`'s 409 is inline too since the closing block, the shared
  `BoardNameTaken` having had one user left, and both routes describe the name in the same words.)
- **A constraint on a list's element now runs**: the API build sets `-Xemit-jvm-type-annotations`, so `[null]` in an
  identifier list answers 400 on four routes where it answered 500, and a blank tag is refused on `PUT /pins/{id}`.
  (Corrected: since block 10's fix-back `9d34980b`, the null is refused by Jackson's `NewStrictNullChecks` reading
  the Kotlin type `List<UUID>`, as `MALFORMED_BODY`, and no element carries `@NotNull`. The flag stays for the tags'
  `List<@NotBlank String>`.)
- **A batch write costs a constant number of reads and writes only what changes** (decision H, blocks 13 and 16):
  `BoardCreator`, `PinBoardSetter`'s add and remove, and `PinRecycleBin`'s `softDeleteAll` and `restoreAll` go
  through `PinRepositoryInterface`'s bulk methods rather than one save per pin. `PinRepository` loads the pins,
  marks them modified in one batch (`markModified(pins, at)`), then inserts or deletes their memberships.
- **A pin's join rows are unique and indexed** (block 13's fix-back `d7c29376`, the operator's "a" on #246): migration
  1.27 adds unique `(pin_id, board_id)` and `(pin_id, tag_id)` indexes and one on `board_id` and on `tag_id`, after
  keeping the first copy of each pair already repeated. `savePin` writes each join row once, so a request naming a
  board or a tag twice no longer collides.
- **A batch body names at most 10 000 identifiers** (`PinIdsInputDto.MAX_IDENTIFIERS`, on `PinIdsInputDto`,
  `BoardIdsInputDto` and `BoardCreationInputDto`), answering 400 `VALIDATION_ERROR` past it, the contract carrying
  `maxItems`. A bulk read binds one parameter per identifier, and the embedded SQLite refuses a statement past 250 000
  (sqlite-jdbc 3.53.2.0: `pragma compile_options` lists `MAX_VARIABLE_NUMBER=250000`, and a 250 001-parameter
  statement fails with "too many SQL variables", probed in the closing block). `PinUpdateInputDto.boardIds` is not
  bounded: each board is resolved by its own read, so no statement binds the list.
- **Accepted alpha limit: a pin that already holds a blank tag answers 400 on every save from the web client**, which
  sends `pin.tags` back as it holds them. Only a direct API call could store one before `20.0.0`. The operator
  accepted blank tags being refused ("A."); no migration.
- **The selection bar's board menu starts with "New board…"**, which opens the board form in a dialog. The request
  carries the selection; a success closes the dialog and clears the selection, and the user stays on the grid.
- **`BoardForm` lives in `components/BoardForm.tsx`**, shared by the boards screen and the selection bar.
  `BoardRefusal` reads the problem body's `code`, so a recycled pin shows the general refusal and not the name-taken
  sentence. The menu's empty state is gone; `boards_empty` stays, the boards screen reading it.
- **Every control in a dialog uses HeroUI's `secondary` variant**: text fields, the text area, the pin form's select
  and its radios. The default variant takes the dialog's own background in the dark theme, with no border and no
  shadow, so all of them were invisible there. Found in block 20's headless reading; the operator chose to fix it in
  block 20.

## Evidence

- Block 10: `dagger call gate` green at `f105d85d`; budget 346 lines, 15 files against `main`. `oasdiff changelog`
  v1.31.0 `main` to HEAD: the four changes of decision E on `POST /api/v1/boards`, plus
  `request-property-pattern-added` on the tags of `PUT /api/v1/pins/{pinId}`, none on `PUT /api/v1/boards/{boardId}`
  (PR #242's report).
- Block 13: `dagger call gate` green at `0973b5a9`; budget 303 lines, 10 files against block 10 (PR #246). After its
  fix-back, green at `d7c29376`; budget 448 lines, 17 files against block 10.
- Block 16: `dagger call gate` green at `dc4da046`; budget 166 lines, 5 files against block 13 (PR #247). After its
  fix-back, green at `f82694b3`; budget 168 lines, 5 files against block 13.
- Block 20: `dagger call gate` green at `18c907bf`; budget 347 lines, 17 files against block 10.
- The journey "add selected pins to a new board" failed on the missing menu item before the implementation, then
  passed.
- Read headless in Firefox 156.0.1 against a stubbed API and the built bundle, at 1100 px, both themes: the menu with
  "New board…" first, the dialog, both 409 sentences with the selection kept, the grid with no bar after a success,
  and "Pins: 2" on the boards screen. Then every dialog holding a field (board, new pin, pin edit, export, account
  deletion), where each field's computed background now differs from its dialog's in both themes.
- Closing block: `dagger call gate` green at `899f104f`, again at `f162842f` after the rebase onto the new block 20,
  at `0052f67e` with the second holistic review's findings, and at `150a1f46` after the second cascade. The regenerated contract drops `BoardNameTaken`,
  inlines `PUT /api/v1/boards/{boardId}`'s 409 with no change to its codes, and gives the three identifier
  lists `maxItems` 10000.

## Pitfalls

- **A Kotlin annotation on a type argument is dropped from the bytecode** without `-Xemit-jvm-type-annotations`; it
  compiles, and nothing fails until a test sends the value the constraint was meant to refuse.
- **HeroUI's default field is invisible on a dialog in the dark theme.** A field, select or radio inside a dialog takes
  `variant="secondary"`; one on a page keeps the default. (Corrected: the rule now lives in `clients/AGENTS.md`,
  Gotchas.)
- **MSW hands every handler the same request**: a handler that reads the body and falls through leaves the next one
  an unusable body. Read `request.clone()`.
- **The application writes `data-theme` when it mounts**, so a headless reading that sets it before the screen has
  rendered reads the light theme.

## Departures from the specification

- Block 10: `List<@NotNull UUID>` needed the compiler flag to run at all, which the specification did not foresee. The
  operator kept the flag build-wide ("A."), blank tags refused included. (Corrected: after the operator's review of
  #242, "H -> a", `NewStrictNullChecks` replaced the element annotations, `9d34980b`.)
- After the operator's review of #242: decision H and blocks 13 and 16, which the specification did not plan.
- Block 20: "The boards screen's journeys pass unchanged" held for every journey but one fake. The name-taken case
  answered a bare 409, which a code-reading `BoardRefusal` reads as the general refusal; it now answers the API's
  problem body.
- Block 20, tier 1: the `refused` problem-body helper, copied in three journeys, moved to `src/test/app.tsx`.

## What is not validated

- The 403 and the recycled-pin 409 of `POST /boards` with pins at the integration level; the use-case tests cover
  them. (Corrected: the closing block shows both on the route, each leaving no board.)
- The headless reading at phone width.
- The closing block was not read headless: it changes a comment and a document on the web application's side, and
  nothing it shows.

## The holistic review

`.reviews/the-selection-starts-a-board-holistic.md`, over
`git diff lot/0.41.0-the-selection-reads-at-a-glance..origin/feat/the-selection-starts-a-board`: 0 CRITICAL,
0 MAJOR, 8 MINOR. Seven were fixed in the closing block; one is an accepted limit.

| Finding | Exit |
|---|---|
| The specification recorded none of block 10's departures at the sentences they correct | Fixed: `(Corrected: ...)` at decision E and at block 10's row; decision D records the closing block's inlined 409 |
| No integration test showed `POST /api/v1/boards`'s 409 for a recycled pin or 403 for a stranger's pin | Fixed: both in `BoardMembershipIntegrationTest`, each followed by an empty board list |
| `BoardNameTaken` had one user left, and the two routes described the name differently | Fixed as the lead chose: `PUT`'s 409 inline, `BoardNameTaken` deleted, one sentence for the name on both routes |
| `BoardController`'s comment named only the name's 409 beside the 201 | Fixed: "the refusals the route answers" |
| The dialog rule lived in this handoff's pitfalls alone | Fixed: a bullet in `clients/AGENTS.md`, Gotchas |
| `PinEditForm`'s comment sent the reader to `BoardForm` for its reason | Fixed: the reason stated in place |
| Nothing failed if the name's holder were read inside the transaction | Fixed: the name-taken test records `transactions.current` at the read and asserts it null |
| A pin already holding a blank tag answers 400 on every save from the web client | Accepted limit, recorded under "Current state"; the operator accepted blank tags refused ("A."), no migration |

## The second holistic review

`.reviews/the-selection-starts-a-board-holistic-2.md`, over blocks 13 and 16 and block 10's fix-back: 0 CRITICAL,
0 MAJOR, 8 MINOR, each fixed in the closing block.

| Finding | Exit |
|---|---|
| No test named two pins refused for different reasons, so the batch's refusal order rested on the code alone | Fixed: `[another user's pin, unknown id]` expects the permission error in `PinBoardSetterTest` and `PinRecycleBinTest` |
| The SQL capture counted neither the join rows' inserts nor their deletes | Fixed: one batched `insert into pin_board_model` for ten pins, one `delete from` statement |
| The identifier lists had no bound, and past SQLite's parameter cap the batch routes answered 500 | Fixed as the operator answered, "a": `@field:Size(max = 10_000)`, a 400, `maxItems` in the contract; see "Current state" |
| `findPinsByIds`'s test filed no pin under a recycled board | Fixed: the active pin also sits in a recycled board, and the expected boards are unchanged |
| Decision C and block 10's row still described one `savePin` per pin | Fixed: `(Corrected by decision H: ...)` at each |
| `BoardCreatorTest`'s comment spoke of a first read that no longer exists | Fixed: "the first pin checked" |
| `PinRepository`'s `TooManyFunctions` comment counted methods, and the count had gone stale | Fixed: the counts dropped |
| This handoff gave no evidence for blocks 13 and 16 | Fixed: one line each under "Evidence" |

## The backlog

Reconciled: the specification names no adjacent item, no block closed one, and the lot files none. The pin viewer's
redesign is the next lot, not an item.

## The lot's counts

- **Fix-backs: 3**: block 10's `9d34980b` after the operator's review of #242 ("H -> a"), block 13's `d7c29376`
  after their review of #246 ("a"), and block 16's `f82694b3`, its naming aligned on block 13's. Blocks 13 and 16
  are inserted blocks, not fix-backs.
- **Cascaded rebases: 2**. The first put block 20 onto 16 and the closing block onto 20, with one conflict, in the
  specification's block table; the second followed the fix-backs of 13 and 16, with none.
- **Runs re-triggered**: to be counted once the second cascade is pushed. Up to it, 4: three by the first cascade's
  push and one by the closing block's own push. The first cascade's push ran one per branch, 36322705750,
  36322739345, 36322769686, 36322804725 and 36322837300, two of them the first runs of blocks 13 and 16; the
  closing block's push after the second holistic review ran 36323491697. Before the cascade: 36315388379 on block
  10, 36318153418 on block 20 and 36318958845 on the closing block (`gh run list --branch <branch>`).
- **The operator's reading of the bodies**: to be filled in before the stack merges.

## Next step

The holistic review at the head of Wrap, then the closing block. After that, the operator's pending request of
2026-09-27: the pin viewer's redesign, after a throwaway design proof of concept. (Corrected: the review and the
closing block are done; the operator's review, the merge of the whole stack and the lot's tag remain.)
