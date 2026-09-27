# Handoff: the selection starts a board

Date: 2026-09-27
Tier: Spec. Specification `docs/specs/2026-09-27-the-selection-starts-a-board.md`. One stack: block 10
`feat/a-board-is-created-with-its-pins` (PR #242), block 20 `feat/the-selection-starts-a-board`. Written in block 20;
the closing block corrects it after the holistic review.

## Current state

- **`POST /api/v1/boards` creates a board with the pins it files**, from `BoardCreationInputDto` (`name`,
  `description`, optional `pinIds`), all or nothing in one transaction. `PUT` keeps `BoardInputDto`. The 201 answers
  the distinct pins filed as `pinCount`.
- **Its refusals are the ones each rule already earns**: 403 `PinForbidden`, 404 `PinInBodyNotFound`, and a 409
  declared inline carrying `BOARD_NAME_ALREADY_EXISTS` and `PIN_ALREADY_SOFT_DELETED`. The contract is at `20.0.0`.
- **A constraint on a list's element now runs**: the API build sets `-Xemit-jvm-type-annotations`, so `[null]` in an
  identifier list answers 400 on four routes where it answered 500, and a blank tag is refused on `PUT /pins/{id}`.
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
- Block 20: `dagger call gate` green at `18c907bf`; budget 347 lines, 17 files against block 10.
- The journey "add selected pins to a new board" failed on the missing menu item before the implementation, then
  passed.
- Read headless in Firefox 156.0.1 against a stubbed API and the built bundle, at 1100 px, both themes: the menu with
  "New board…" first, the dialog, both 409 sentences with the selection kept, the grid with no bar after a success,
  and "Pins: 2" on the boards screen. Then every dialog holding a field (board, new pin, pin edit, export, account
  deletion), where each field's computed background now differs from its dialog's in both themes.

## Pitfalls

- **A Kotlin annotation on a type argument is dropped from the bytecode** without `-Xemit-jvm-type-annotations`; it
  compiles, and nothing fails until a test sends the value the constraint was meant to refuse.
- **HeroUI's default field is invisible on a dialog in the dark theme.** A field, select or radio inside a dialog takes
  `variant="secondary"`; one on a page keeps the default.
- **MSW hands every handler the same request**: a handler that reads the body and falls through leaves the next one
  an unusable body. Read `request.clone()`.
- **The application writes `data-theme` when it mounts**, so a headless reading that sets it before the screen has
  rendered reads the light theme.

## Departures from the specification

- Block 10: `List<@NotNull UUID>` needed the compiler flag to run at all, which the specification did not foresee. The
  operator kept the flag build-wide ("A."), blank tags refused included.
- Block 20: "The boards screen's journeys pass unchanged" held for every journey but one fake. The name-taken case
  answered a bare 409, which a code-reading `BoardRefusal` reads as the general refusal; it now answers the API's
  problem body.
- Block 20, tier 1: the `refused` problem-body helper, copied in three journeys, moved to `src/test/app.tsx`.

## What is not validated

- The 403 and the recycled-pin 409 of `POST /boards` with pins at the integration level; the use-case tests cover
  them.
- The headless reading at phone width.

## Next step

The holistic review at the head of Wrap, then the closing block. After that, the operator's pending request of
2026-09-27: the pin viewer's redesign, after a throwaway design proof of concept.
