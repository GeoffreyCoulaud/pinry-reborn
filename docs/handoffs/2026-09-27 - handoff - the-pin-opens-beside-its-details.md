# Handoff: the pin opens beside its details

Date: 2026-09-27
Tier: Spec. Specification `docs/specs/2026-09-27-the-pin-opens-beside-its-details.md`. One stack: block 10
`feat/the-pin-opens-beside-its-details` (PR #250), block 20 `feat/the-viewer-steps-through-the-grid` (PR #251),
block 30 `feat/the-pin-is-edited-beside-its-image`. Written in block 30; the closing block corrects it after the
holistic review.

## Current state

- **The pin viewer is the window less HeroUI's margin**: `Modal.Container size="cover" scroll="inside"`, the whole
  screen with square corners below `sm`. From `lg` the image and a fixed 360 px column sit side by side, each
  scrolling on its own; below `lg` they stack, image first, and the dialog scrolls as one. The frame is
  `components/PinSides.tsx`, shared by reading and editing.
- **The column** holds Edit, Delete and Close, the description, the source page's host name as a link, the tags as
  chips and the boards as links to `/boards/$boardId`.
- **The image side shows one of four states**: the `LARGE` rendition, "Downloading" with a spinner, the failure's
  reason, or a sentence saying the pin has no image.
- **Retry is offered where a failure can pass**: `retriable` in `downloadReasons.ts`, a
  `Record<Known<"DownloadReasonDto">, boolean>`, classes `UNREACHABLE` and `INTERNAL_ERROR` as retriable and the
  seven reasons the server fails permanently as not. The viewer and the task centre both read it; the task centre no
  longer offers Retry on a permanent failure. The viewer retries from the pin's `sourceMediaUrl`, the task centre
  from the failed download's `sourceUrl`.
- **The opened pin is looked up in every loaded pin**, so a pin that turns `PENDING` after Retry stays in the dialog.
- **The viewer steps through the grid**: previous and next on the image's edges, `←` and `→` on the document, and a
  horizontal touch swipe through `useMove` from `react-aria`, a direct dependency since ADR 0045. Past the last loaded
  pin, next fetches the grid's next page itself. Nothing steps while editing.
- **Editing keeps the image in view**: Edit puts the form in the column, under the heading "Edit the pin", with
  Cancel and Save sticky at its foot. What happens to the image is one choice on the image side, a HeroUI
  `ToggleButtonGroup` of Keep, File and Address. Under Keep and File the image address is a field of the column, and
  correcting it fetches nothing. Under Address the field moves to the image side, and an empty one disables Save
  where it used to fall back to keeping the image. File shows a drop box in the image's place, then the file's
  preview with its name and "New image, not saved yet".
- **The contract does not change.**

## Evidence

- Block 10: `dagger call gate` green at `18a7ebf5`; budget 304 lines, 13 files against `main`; `oasdiff changelog`
  (`tufin/oasdiff:v1.31.0`) `main` to HEAD: "No changes detected". `ModeBImageHostingIntegrationTest`: 17 tests, the
  new case a failed download followed by a second `PUT` that settles `READY`. CI run 36350444532 green (PR #250's
  report).
- Block 20: `dagger call gate` green at `b0addaf4`; budget 276 lines, 10 files against block 10. Mutations each
  failing a case: the listener not gated by editing, the touch filter removed, the last delta in place of the sum,
  `useMove`'s `onKeyDown` spread, `!isFetchingNextPage` dropped. CI run 36351317131 green (PR #251's report).
- Block 30: `dagger call gate` green at `14094003`; budget 357 lines, 7 files against block 20. The journey "edit a
  pin's description, tags and boards" passes unchanged. Mutations each failing a case of "replace a pin's image with
  a file": the address field kept in the column under Address; the empty address no longer disabling Save; a
  corrected address under Keep sending a fetch.
- Headless readings, Firefox 156.0.1 over WebDriver BiDi against a Node stub of the API and the built bundle, both
  themes, the same geometry in each:

| Viewport | Dialog | Column | Block 30's edit mode |
|---|---|---|---|
| 1280×800 | 1200×720 at (40, 40) | 360 wide, beside the 768 px image side | foot at y 684 to 736, the column's bottom |
| 800×1000 | 720×920 at (40, 40) | below the image | foot sticky at y 884 to 936 while the dialog scrolls |
| 390×844 | 390×844 at (0, 0), square corners | below the image | foot sticky at y 764 to 820 while the dialog scrolls |

  Block 30 read Keep, File (empty, then a file chosen through `input.setFiles`) and Address at each size. The
  address field sat in the column under Keep and File and on the image side under Address; Save was disabled under
  an empty File. The toggle buttons' background differs from the dialog's in both themes: light
  `oklch(0.94 0.001 286.375)` on `oklch(1 0 0)`, dark `oklch(0.274 0.006 286.033)` on `oklch(0.2103 0.0059 285.89)`.

## Pitfalls

- **The only pin in the grid going `PENDING` empties the tiles**, and the empty state would unmount the dialog; it
  waits while a pin is open (block 10).
- **A plain `pnpm install` linked a second, peerless `react-aria`** while reporting the lockfile up to date;
  `pnpm install --fix-lockfile` resolved it to the shared copy. ADR 0045 records it (block 20).
- **`Modal.Dialog` passes no key handler through to the DOM**, so the arrows listen on the document (block 20).
- **HeroUI 3.2.6's `ToggleButton` has no `secondary` variant**, only `default` and `ghost` (Context7,
  `/llmstxt/heroui_react_llms_txt`, and `toggle-button.css`). Its `default` background already differs from the
  dialog's in both themes (measured above), so the dialog rule of `clients/AGENTS.md` has nothing to apply to it.
- **React Aria's `ToggleButtonGroup` in single selection is a `radiogroup` of `radio`s**
  (`useToggleButtonGroup.mjs`, react-aria 3.52.1), so the journeys still find the options by the `radio` role.
- **`PinEditForm` takes the viewer's image as a prop** rather than importing `PinImage`: `PinGrid` imports the form,
  and the import graph is acyclic.

## Departures from the specification

- Block 10: decision A's fallback `sm:w-full` was not needed, `cover` measuring 1200×720 without it. "Source page"
  reuses the existing `source_page` message.
- Block 20: `←` and `→` listen on the document, not the dialog. `pinsRoute` in `test/app.tsx` awaits its
  `onRequest` hook, so a journey can hold a page back. `clients/AGENTS.md` names `react-aria` on the stack line.
- Block 30: `ImageDropBox` takes a `className`, so that under File the drop box fills the image's place. The first
  headless reading found it small in the middle of an empty 768×624 side.
- Block 30: under File, the chosen file's name stays beside "New image, not saved yet", with a `secondary` Remove
  button that brings the drop box back. Under Keep and Address the image side shows the viewer's own image states,
  Retry on a retriable failure included.
- Block 30: the options read "Keep", "File" and "Address"; `fetch_image_from_url` and `image_replace` are gone from
  both catalogues, and `image_keep` changed its text.

## What is not validated

- On a real phone: the vertical scroll `pan-y` keeps, the `pointercancel` the browser sends, whether `SWIPE_PX` = 48
  feels right, and the edit mode's sticky foot.
- In headless Firefox's dark theme the dialog's scrollbar renders as a white track, in the viewer as in block 10's
  edit form; not checked on a desktop Firefox.
- A real drag of a file onto the edit mode's drop box: the reading chose the file through `input.setFiles`, and the
  journey through `user.upload`.

## The backlog

The specification names one adjacent item, "The grid keeps every page it scrolls" (ADR 0033), left open: next past
the last loaded pin adds a page to the same query, as scrolling does. No block closed or filed an item.

## Next step

The holistic review at the head of Wrap, over
`git diff lot/0.42.0-the-selection-starts-a-board..origin/feat/the-pin-is-edited-beside-its-image`, then the closing
block, then the operator's review of the stack.
