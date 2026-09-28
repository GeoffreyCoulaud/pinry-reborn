# Handoff: the pin opens beside its details

Date: 2026-09-27
Tier: Spec. Specification `docs/specs/2026-09-27-the-pin-opens-beside-its-details.md`. One stack: block 10
`feat/the-pin-opens-beside-its-details` (PR #250), block 20 `feat/the-viewer-steps-through-the-grid` (PR #251),
block 30 `feat/the-pin-is-edited-beside-its-image`. Written in block 30; the closing block corrects it after the
holistic review. (Corrected: block 30 is PR #253; the closing block is `fix/the-pin-opens-beside-its-details-closes`,
on block 30. The holistic review ran; see "The holistic review".)

## Current state

- **The pin viewer is the window less HeroUI's margin**: `Modal.Container size="cover" scroll="inside"`, the whole
  screen with square corners below `sm`. From `lg` the image and a fixed 360 px column sit side by side, each
  scrolling on its own; below `lg` they stack, image first, and the dialog scrolls as one. The frame is
  `components/PinSides.tsx`, shared by reading and editing.
- **The column** holds Edit, Delete and Close, the description, the source page's host name as a link, the tags as
  chips and the boards as links to `/boards/$boardId`.
- **The image side shows one of four states**: the image, "Downloading" with a spinner, the failure's reason, or a
  sentence saying the pin has no image. The image is the original, at its own size or shrunk to fit and never
  upscaled, in a box sized to what it is drawn at; the rendition the grid chose for its tiles fills that box until the
  original loads (block 10's fix-back `cbda2bf0`, after the operator's test). (Corrected: since the closing block's
  fix-back, the placeholder goes once the original's `decode()` resolves rather than on its `load`, and stays if it
  rejects; a small spinner, labelled "Loading the full-size image", sits in the box's corner while the original is
  not decoded 300 ms after the pin is shown.)
- **Retry is offered where a failure can pass**: `retriable` in `downloadReasons.ts`, a
  `Record<Known<"DownloadReasonDto">, boolean>`, classes `UNREACHABLE` and `INTERNAL_ERROR` as retriable and the
  seven reasons the server fails permanently as not. The viewer and the task centre both read it; the task centre no
  longer offers Retry on a permanent failure. The viewer retries from the pin's `sourceMediaUrl`, the task centre
  from the failed download's `sourceUrl`. (Corrected: the edit form offers no Retry since the closing block, its
  Address being the one fetch it shows; and the image and the column are keyed by the pin, so a Retry or a delete
  in flight stays with the pin it was pressed on.)
- **The opened pin is looked up in every loaded pin**, so a pin that turns `PENDING` after Retry stays in the dialog.
- **The viewer steps through the grid**: previous and next on the image's edges, `←` and `→` on the document, and a
  horizontal touch swipe through `useMove` from `react-aria`, a direct dependency since ADR 0045. Past the last loaded
  pin, next fetches the grid's next page itself. The previous and next pins' placeholder is preloaded, never their
  original, so a step shows an image at once (block 20's fix-back `04383d54`). Nothing steps while editing.
- **Editing keeps the image in view**: Edit puts the form in the column, under the heading "Edit the pin", with
  Cancel and Save sticky at its foot. What happens to the image is one choice on the image side, a HeroUI
  `ToggleButtonGroup` of Keep, File and Address. Under Keep and File the image address is a field of the column, and
  correcting it fetches nothing. Under Address the field moves to the image side, and an empty one disables Save
  where it used to fall back to keeping the image. File shows a drop box in the image's place, then the file's
  preview with its name and "New image, not saved yet". (Corrected: since the closing block the field belongs to the
  form through its `form` attribute wherever it sits, so Enter saves from the image side too.)
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
  corrected address under Keep sending a fetch. CI run 36352180952 green (PR #253), before the fix-backs.
  Rebased onto blocks 10 and 20's fix-backs by hand, the cascade having
  conflicted on `PinGrid.tsx`: `dagger call gate` green with the code at `02531d47`; budget 361 lines, 7 files
  against block 20.
- Closing block: budget 117 lines, 9 files against block 30, at `ecb99d50` with the findings alone. Its four new
  journey cases and the changed Address case each failed before the fix (`vitest run`, 4 failed of 17). The
  lockfile guard passes as it stands and fails on a peerless copy (three keys) and on a drifted version (a second
  version), both run by hand against the regular expression. Read headless at 1280×800 in both themes (Firefox
  156.0.1, the stub of block 30 answering the pin's `PUT`): Retry shown in the viewer and absent from the form under
  Keep and Address, Alt+→ leaving the pin where → steps, and Enter in the address field on the image side sending
  the pin's `PUT` then the image's. Rebased by hand onto block 30's rebase `de209f98`, the cascade having conflicted
  on `PinGrid.tsx` and the step journey: `retries` and the key now sit on the `PinImage` that takes `placeholder`.
  Budget 123 lines, 9 files against block 30 at `ef9e685f`. The same reading gave the same results on the rebased
  bundle, both themes.
- Closing block's fix-back `091cd0b1`, the operator's flash and spinner: two of three new "open a pin" cases failed
  before it (the spinner while decoding, none when decoded at once); the third, a rejected `decode()` keeping the
  placeholder, passed before and after. Read headless at 1280×800, both themes, with the stub's pin 1 original held
  2.5 s: stepping onto it, the placeholder and no spinner at 150 ms, the spinner (24×24, the box's bottom right) at
  550 ms, the original alone at 3 s; the fast original of pin 0 was drawn alone at 150 ms, with no spinner. Budget
  212 lines, 13 files against block 30 at `091cd0b1`.
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
  Read again after the rebase, same sizes and themes: at 1280×800 a 1920×1080 original drew at 768×432 in the
  768×624 area under the selector, and an 800×1200 one at 416×624 under Keep and 347×520 in the 520 px left under
  Address, the address field above it.

## Pitfalls

- **The only pin in the grid going `PENDING` empties the tiles**, and the empty state would unmount the dialog; it
  waits while a pin is open (block 10).
- **A plain `pnpm install` linked a second, peerless `react-aria`** while reporting the lockfile up to date;
  `pnpm install --fix-lockfile` resolved it to the shared copy. ADR 0045 records it (block 20). (Corrected: since
  the closing block, `src/test/lockfile.test.ts` refuses a second `react-aria` key in the lockfile, peerless or
  drifted, ADR 0045's **Fails if**.)
- **`Modal.Dialog` passes no key handler through to the DOM**, so the arrows listen on the document (block 20).
- **HeroUI 3.2.6's `ToggleButton` has no `secondary` variant**, only `default` and `ghost` (Context7,
  `/llmstxt/heroui_react_llms_txt`, and `toggle-button.css`). Its `default` background already differs from the
  dialog's in both themes (measured above), so the dialog rule of `clients/AGENTS.md` has nothing to apply to it.
- **React Aria's `ToggleButtonGroup` in single selection is a `radiogroup` of `radio`s**
  (`useToggleButtonGroup.mjs`, react-aria 3.52.1), so the journeys still find the options by the `radio` role.
- **The edit mode's image area is its own size container** (`lg:[container-type:size]`): the original's box fits
  `100cqh`, which would otherwise measure the whole side, the selector and the address field included.
- **jsdom has no `HTMLImageElement.decode`**, and loads no image: `src/test/setup.ts` stubs one that never settles,
  and a journey that wants the original decoded, or refused, says so with `vi.spyOn`.
- **The flash was the swap, not the step**: the operator saw it disappear under a throttled connection, where the
  step still inserts a fresh placeholder. So `decoding="sync"` was not added, and `OriginalImage` stays keyed by its
  address, which remounts it on each step whatever `PinImage`'s key.
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
  Retry on a retriable failure included. (Corrected: Retry left out of the form by the closing block, the holistic
  review's second MAJOR.)
- Block 30: the options read "Keep", "File" and "Address"; `fetch_image_from_url` and `image_replace` are gone from
  both catalogues, and `image_keep` changed its text.
- Closing block: the flash and the spinner the operator found while testing the stack are fixed in the closing
  block rather than in block 10, the layer they concern, as the operator chose ("b."): one run re-triggered instead
  of four.

## What is not validated

- On a real phone: the vertical scroll `pan-y` keeps, the `pointercancel` the browser sends, whether `SWIPE_PX` = 48
  feels right, and the edit mode's sticky foot.
- In headless Firefox's dark theme the dialog's scrollbar renders as a white track, in the viewer as in block 10's
  edit form; not checked on a desktop Firefox.
- A real drag of a file onto the edit mode's drop box: the reading chose the file through `input.setFiles`, and the
  journey through `user.upload`.
- The closing block's reading covered 1280×800 alone; nothing it changed depends on the layout.
- The flash itself: no headless reading shows a frame, so whether the decoded swap removes it is the operator's to
  see, on a fast connection where it showed.
- Stepping with a Retry in flight: the key that keeps it on its own pin is reasoned from React's reconciliation and
  shown by the refused Retry alone.

## The holistic review

`.reviews/the-pin-opens-beside-its-details-holistic.md`, over
`git diff lot/0.42.0-the-selection-starts-a-board..origin/feat/the-pin-is-edited-beside-its-image`: 0 CRITICAL,
2 MAJOR, 7 MINOR, each fixed in the closing block.

| Finding | Exit |
|---|---|
| Stepping kept one pin's Retry and delete state on the next: a refusal, a disabled Retry, a delete closing the viewer | Fixed: `key={pin.id}` on `PinImage` and `PinDetails`, not on `PinDialog`, whose remount would drop the next button's focus; a journey case steps from a refused Retry onto a pin with no alert |
| The edit form offered Retry on the saved address beside Address's own field | Fixed: `PinImage` takes `retries`, false from the form; a case under Keep and Address finds no Retry |
| `neighbours` restated `placeableTiles`' predicate | Fixed: `isPlaceable` in `lib/tiles.ts`, used by both |
| The arrows stepped with a modifier held, or on a key already handled | Fixed: the listener returns on Alt, Ctrl, Meta, Shift or `defaultPrevented`; one case presses Alt+→, then → once handled |
| No test for Retry absent without a `sourceMediaUrl`, nor for the refusal alert | Fixed: both in "retry a failed download from the pin", the alert in the stepping case above |
| Enter in the address field under Address no longer saved | Fixed: the form's `id` and the field's `form` attribute; the Address case saves with Enter |
| ADR 0045's **Fails if** had no guard | Fixed: `src/test/lockfile.test.ts` expects the manifest's version once as a package and once as a snapshot |
| `clients/AGENTS.md`'s dialog rule had an unrecorded exception | Fixed: one sentence naming `ToggleButton` and why |
| `ModeBImageHostingIntegrationTest`'s comment described a retry the viewer never offers | Fixed: "a new download from another address, as the edit form's Address sends it" |
| This handoff lacked block 30's pull request and run | Fixed: in the header and under "Evidence" |

## The backlog

The specification names one adjacent item, "The grid keeps every page it scrolls" (ADR 0033), left open: next past
the last loaded pin adds a page to the same query, as scrolling does. No block closed or filed an item.
(Corrected: reconciled in the closing block, which closes and files none either.)

## The lot's counts

- **Fix-backs: 3**, after the operator's test of the application: block 10's `cbda2bf0` (the original, never
  upscaled, under the grid's rendition), block 20's `04383d54` (the neighbours' placeholder loaded ahead), and the
  closing block's `091cd0b1` (the swap once decoded, the spinner).
- **Cascaded rebases: 3**, each conflicting and resolved by the teammate of its branch, block 30 and this closing
  block among them. Block 30's rebase carried a handoff update and no fix of its own, so it counts here, not above.
- **Runs re-triggered: 5**: the push of the rebased stack, one per branch, and the closing block's fix-back. The
  first runs, not counted, were 36350444532 on block 10, 36351317131 on block 20, 36352180952 on block 30 and 36352998151 on the closing block
  (`gh pr checks`).
- **The operator's reading of the bodies**: filled in before the stack merges.

## Next step

The holistic review at the head of Wrap, over
`git diff lot/0.42.0-the-selection-starts-a-board..origin/feat/the-pin-is-edited-beside-its-image`, then the closing
block, then the operator's review of the stack. (Corrected: the review and the closing block are done; the
operator's review, the merge of the whole stack and the lot's tag remain.)
