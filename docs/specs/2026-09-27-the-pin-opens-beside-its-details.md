# The pin opens beside its details

Date: 2026-09-27
Status: Draft for the operator. One specification review ran, `.reviews/the-pin-opens-beside-its-details-spec.md`,
its 1 CRITICAL, 6 MAJOR and 7 MINOR closed in this document. Frozen when the lot's last block merges.
Branches: one stack: 10 `feat/the-pin-opens-beside-its-details`, 20 `feat/the-viewer-steps-through-the-grid` on 10,
30 `feat/the-pin-is-edited-beside-its-image` on 20.
ADR: `docs/adr/0045-react-aria-is-a-direct-dependency.md`, written in block 20 (decision F). The contract does not
change.

## 1. Goal

The pin viewer gives the image the room: the dialog fills the window but for a margin, the image on one side and
the pin's details in a column beside it. From it the user steps to the next pin, retries a failed download, and edits
the pin with the image still in view. The operator iterated on a throwaway canvas of mockups, variant "A · Côte à
côte", its edit and mobile artboards (claude.ai artifact "Pin viewer mockups").

## 2. What exists today

- **`PinDialog`** (`components/PinGrid.tsx:80`) stacks the image (`MEDIUM` rendition, `max-h-[60vh]`), the
  description, the tag names and the board names as bare lists, then Delete alone at one end, Edit and Close at the
  other. It sits in `Modal.Container size="lg" scroll="outside"`, which HeroUI caps at `max-w-lg`, 32rem
  (`modal.css:241`, `@heroui/styles` 3.2.6). Edit swaps the whole dialog for `PinEditForm` (specification
  2026-09-20, decision K).
- **The opened pin is looked up in the placeable tiles** (`PinGrid.tsx:239`), and `placeableTiles`
  (`lib/tiles.ts:45`) leaves out a pin whose download is `PENDING`.
- **`PinEditForm`** chooses what happens to the image with three radios, keep, replace by a file, fetch from the
  address, the last one shown only while the address field holds something. The address is a field of the form.
- **The task centre offers Retry on every failed download** (`components/TaskCentre.tsx:22`), from the download's
  `sourceUrl`, through `useSetPinImage`, which is `PUT /api/v1/pins/{pinId}/image` with a `sourceUrl`.
- **A download already failed gets a new one.** `RequestPinImageDownload.request` checks neither the pin's image
  state nor a previous download: it enqueues a task and `upsertPending`s the row. `EbeanTaskQueue` deduplicates on a
  live task only (`EbeanTaskQueue.kt:53`). Each half has its test (`EbeanImageDownloadRepositoryTest.kt:52`,
  `EbeanTaskQueueTest.kt:70`); nothing drives the two together over HTTP.
- **The server splits the failures already**: `DownloadPinImage` sends `UNREACHABLE` and `INTERNAL_ERROR` through
  `failRetryable`, which fails the row only once `MAX_ATTEMPTS` are spent, and every other reason through
  `failPermanent` (`DownloadPinImage.kt:66-71`, `:79`, `:95`, `:132`). `FETCH_FAILED` is a redirect without
  `Location` or an unexpected 4xx (`FetchException.kt:21`, `HttpImageFetcher.kt:44-66`).
- **A settled download rereads the pins it names** (`useImageDownloads`, `images.ts:104`), mounted by the task
  centre in the header of every signed-in screen.

## 3. Decisions

Each is the operator's answer of 2026-09-27 on the canvas or in Discuss, or its consequence.

**A. The dialog is the window less a uniform margin.** `Modal.Container size="cover" scroll="inside"`. HeroUI pads the
container `1rem`, `2.5rem` from `sm` (`modal.css:97-98`), and `.modal__dialog--cover` is `h-full w-full`
(`modal.css:248`); `inside` bounds the dialog by the container (`max-h-full`, `modal.css:206`). The container also
carries `sm:w-fit` and `cover` adds no modifier to it, so the width is measured in block 10's reading, `sm:w-full` on
the container being the fallback. Below `sm` the dialog is the whole screen: `max-sm:p-0` on the container and
`max-sm:rounded-none` on the dialog, `cover` removing neither (`modal.css:97`, `:180`). From `lg` the image and the
column sit side by side and the column scrolls on its own (`overflow-y-auto`); below `lg` they stack, image first,
and the dialog's content scrolls as one (`overflow-y-auto`). `scroll="outside"` goes: it existed because nothing
scrolled inside the dialog, and its auto-height container would give the cover dialog its content's height.

**B. The column is a fixed `22.5rem`** whatever the window: its text does not grow with the screen, so the image takes
what the window adds. It holds, in order:
- Edit (icon and label) and Delete (icon, `aria-label`) at its start, Close (icon, `aria-label`) alone at its far end;
- the description;
- "Source page" as a link showing the address's host name, opening a new tab, absent without `sourceContextUrl`;
- the tags as HeroUI chips under their label, the boards as links to `/boards/$boardId` under theirs, each group
  absent when empty.
Icons are `lucide-react`'s, already a dependency.

**C. The image side shows one of four states.** `READY`: the image, whole (`object-contain`), in the `LARGE`
rendition, `MEDIUM` (480 px by default, `RenditionsConfig`) being smaller than the side it now fills; `Rendition` in
`lib/tiles.ts` widens to carry it. (Corrected: after the operator's test of block 10, "on veut afficher l'original
au final", the image is the original, shown at its own size or shrunk to fit and never upscaled, since a small one
stretched to the side read badly. While it loads, the rendition the grid chose for its tiles, carried to the viewer
and not assumed `MEDIUM`, fills the original's box, so the viewer shows what the HTTP cache holds rather than a blank.
`Rendition` keeps its two values.) `PENDING`: a spinner and "Downloading". `FAILED`: the reason `downloadReason`
gives, and Retry when decision D allows it and the pin has a `sourceMediaUrl`, which Retry fetches from. No image at
all: a sentence saying so.

**D. Retry is offered for a failure that can pass, and nowhere else.** One table in `downloadReasons.ts`,
`Record<Known<"DownloadReasonDto">, boolean>` like `REASONS` beside it
(`docs/adr/0044-a-response-code-declares-its-set.md`), so a reason the server adds fails `tsc` until it is classed.
The split is the client's copy of the server's: `UNREACHABLE` and `INTERNAL_ERROR`, the two reasons `DownloadPinImage`
retries itself, are retriable; the seven it fails permanently are not, `FETCH_FAILED` among them, the same request
earning the same answer. A code the bundle does not know is not retriable. The task centre reads the same table and
drops Retry on a permanent failure; choosing a file and dismissing stay on every failure. The two Retries fetch from
two addresses and that is intended: the viewer from the pin's `sourceMediaUrl`, which the user may have corrected
(decision H), the task centre from the failed download's own `sourceUrl`, the only one its row carries.

**E. Retry keeps the viewer open.** The opened pin is looked up in every loaded pin, not in the placeable tiles, so a
pin that turns `PENDING` stays in the dialog and shows decision C's `PENDING`, then its image once the download settles
and the grid rereads it.

**F. The viewer steps through the grid** (block 20). The order is the grid's placeable tiles, with the opened pin kept
in when it is not placeable (decision E). Three gestures, none of them while editing:
- previous and next buttons on the image side's edges, each disabled where there is no pin that way;
- `←` and `→` on the dialog;
- a horizontal swipe on the image side.
Past the last loaded pin, when the grid has another page, next calls `fetchNextPage` itself, since the grid's sentinel
need not be in view, and moves once the page arrives, disabled while it loads. (Corrected: after the operator's
test of block 20, "En navigant d'un pin à l'autre, il y a un flash blanc le temps que l'image charge", the viewer
preloads the previous and next pins' placeholder, the rendition the grid chose (decision C's correction), and never
their original, so a step shows that rendition at once and the original over it when it arrives. The operator's
answer of 2026-09-28. `←` and `→` listen on the document, `Modal.Dialog` passing no key handler to the DOM.)

The swipe uses `useMove` from `react-aria`, a new direct dependency at 3.52.1, published 2026-09-04: the version
`react-aria-components` 1.21.1 pins exactly, so the tree gains no package. Block 20's ADR 0045 records it, with its
**Fails if** the two versions drift apart and put two interaction layers in the tree. `useMove` is exported by
`react-aria` alone, not by `react-aria-components` nor `@heroui/react`.
- The image side is `touch-action: pan-y`, so the browser keeps the vertical scroll and cancels the gesture instead.
- `useMove` ends on `pointercancel` as on `pointerup` (`useMove.mjs:182`, `:198`, react-aria 3.52.1), so the viewer
  sums `deltaX` and `deltaY` over the move and navigates only when the horizontal distance is at least `SWIPE_PX` and
  larger than the vertical one.
- **Only `onPointerDown` is taken from `moveProps`**, and only for a touch pointer. Its `onKeyDown` swallows `←` and
  `→` (`preventDefault` and `stopPropagation`, `useMove.mjs:207-219`), which would keep them from the dialog once
  focus sits on the next button. Its `onPointerDown` prevents the default on every primary press, which would stop a
  mouse from dragging the image out of the page.
- `SWIPE_PX` is 48 and no measurement set it: it is the knob the operator tunes on a phone. jsdom dispatches the
  pointer events but neither `touch-action` nor the browser's `pointercancel`, so the phone is the only check of
  those.

**G. Editing keeps the image in view** (block 30). Edit puts the form in the column and leaves the image side where
it is. The column holds a heading, the description, the source page, the image address, the tags and the boards;
Cancel and Save stay at its foot while the fields scroll.

**H. What happens to the image is one choice, made on the image.** A HeroUI `ToggleButtonGroup`, single selection
that cannot be emptied, sits on the image side: Keep, File, Address. The radios go.
- **Keep**: the image as it is. The address is an ordinary field of the column: it is a piece of the pin's
  metadata, useful on its own, and correcting it fetches nothing.
- **File**: the drop box on the image side, then the file's preview in the image's place with "New image, not saved
  yet". A dropped address fills the address, as today.
- **Address**: the address field leaves the column for the selector, the same value in one place at a time, and the
  image carries "Fetched from this address on save". Save is disabled while the address is empty, as it is while File
  holds no file.
The replacement's own `PENDING` and `FAILED` sentences stay at the head of the column.

## 4. Blocks

Every block is read headless before its push, both themes. Expected at 1280×800: the dialog `1200×720` (else decision
A's fallback), the column 360 px wide beside the image. At 800×1000: the dialog `720×920`, the image above the column.
At 390×844: the dialog `390×844`, square corners.

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/the-pin-opens-beside-its-details` | Decisions A to E. The journey "open a pin" also finds the source page's host name as a link, the tags, and each board as a link to its `/boards/$boardId`. A new journey, "retry a failed download from the pin": a pin failed `UNREACHABLE` opens on its reason; Retry sends `PUT /api/v1/pins/{pinId}/image` with the pin's `sourceMediaUrl`; the dialog stays open and says the image is downloading. A pin failed `FETCH_FAILED` shows its reason and no Retry. The task centre's journey: its fixtures that press Retry fail `UNREACHABLE` (`download()` in `test/app.tsx` gives `FETCH_FAILED` today), and a `FETCH_FAILED` row has no Retry. An API integration test beside the existing download tests: a download failed against an address that refuses it, then a second `PUT` with an address that serves an image, and the pin's image status polled to `READY`. `oasdiff changelog` against `main` lists nothing. |
| 20 | `feat/the-viewer-steps-through-the-grid` | Decision F. A journey, "step through the grid from a pin": next shows the second pin's description, then `→` with focus still on next shows the third's, `←` the second's again; previous is disabled on the first pin; while editing there is no previous nor next and `→` in the description moves the caret. With the grid's sentinel never reached (the setup's `IntersectionObserver` stub overridden in that case) and page 2 held unanswered, next on page 1's last pin requests page 2, is disabled until it is answered, then shows its first pin. A swipe dispatched as touch pointer events 60 px left shows the next pin; 60 px left and 80 px down shows the same pin; 30 px left shows the same pin. A mouse drag of 60 px shows the same pin. Carries ADR 0045. |
| 30 | `feat/the-pin-is-edited-beside-its-image` | Decisions G and H. The journey "replace a pin's image with a file" chooses File in the selector rather than a radio. With Address chosen, the column holds no address field and the selector does, carrying what the column held; the field emptied leaves Save disabled, where today an emptied field falls back to keep. The journey "edit a pin's description, tags and boards" passes with its queries unchanged or with the field names alone changed. As a guard of what H keeps: with Keep, a corrected address is saved and no `PUT /api/v1/pins/{pinId}/image` is sent. |

## 5. Adjacent backlog items

- **"The grid keeps every page it scrolls"** (`docs/adr/0033-the-grid-keeps-every-page-it-scrolls.md`) is not
  closed: next past the last loaded pin adds a page to the same query, as scrolling does.
- No other item concerns the viewer, the edit form or the task centre.

## 6. Out of scope

- **An address for the opened pin.** The dialog is still state of the grid; observed as the journeys' address
  unchanged while a pin is open.
- **Zooming the image.**
- **The mockups' variants B and C**, which the operator set aside.
