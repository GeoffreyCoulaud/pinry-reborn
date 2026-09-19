# Handoff: the drop is the gesture

Date: 2026-09-20
Specification: `docs/specs/2026-09-19-the-drop-is-the-gesture.md`
ADR: `docs/adr/0037-a-refused-element-speaks-in-a-toast.md`
Blocks: 10 `feat/the-drop-area-answers-the-drag` (#159), 20 `feat/a-refusal-is-a-toast` (#160),
30 `feat/a-drop-on-the-grid-opens-the-form` (#161), 35 `feat/the-grid-scrolls-inside-its-own-box`
(#162), 40 `feat/a-drop-carries-several-pins`
Tier: Spec. The specification review ran before a line was written: 0 CRITICAL, 6 MAJOR, 6 MINOR,
all closed in the document. The holistic review runs at the head of Wrap and is not in this file
yet; the closing block corrects this paragraph with what it found.

## Current state

**The gesture is the whole screen.** While the creation dialog is closed, `Home` listens for the
four drag events on `window` and veils the viewport with an overlay; a drop is judged where it
landed and opens the dialog already holding what survived. While the dialog is open nothing on the
window listens at all, so the dialog owns its own gesture and no drop is counted twice.

**A drop carries several pins.** What a drop keeps becomes a queue of entries, one entry per
picture: the file, the address it was found at, or both. The form works through them one at a time,
says `1/2` above the fields and `Pin 1 of 2` to a screen reader, and carries `Ignore` beside
`Add a pin` while more than one entry stands. The queue advances on the server's word alone, and a
refusal keeps the entry on screen to be corrected. The file picker takes several files too and
enters by the path a drop does, so what is chosen with the mouse and what is dropped are judged
once, in one place (`src/drops.ts`).

**Every refusal speaks where the gesture happened**, as a toast (ADR 0037), from all four origins:
a drop on the grid, a drop on the dialog's area, the file picker, and the re-judge at submission.
The form's inline upload alert is gone; the one for a pin the server refused stays, that being the
entry's own event and not the gesture's.

**An uploaded pin keeps its provenance.** `sourceMediaUrl` is read from the address field and always
sent, and it supplies the bytes only when no file is chosen. The client used to derive it from the
source and so dropped the address of every uploaded pin. This closed the `P1` item about an image
dragged from another browser tab: that drop carries an address and no file at all.

**The grid scrolls inside its own box**, `<main>` being one screen tall. The application was built
that way and the CSS had never said so, so the whole document scrolled.

## Where the pieces live

- `src/lib/drags.ts`: `dragDepth`, the enter-against-leave counter both drop targets hold.
- `src/lib/uris.ts`: `urisFromDrop`, the `text/uri-list` lines a server could fetch.
- `src/lib/drops.ts`: the pure half of a drop. `partitionDrop` (what is kept, one reason per
  distinct refusal), `entriesOf` (the entries a drop becomes) and `withDrop` (what a later drop does
  to the queue).
- `src/drops.ts`: the impure half, shared by the grid and the dialog: the measurement, the per-file
  verdict, and `refuse()`.
- `components/CreatePinDialog.tsx`: the queue, the drop area, the close cross.
- `routes/Home.tsx`: the window handlers, the overlay, and the dialog opened on what was dropped.

## Pitfalls

- **A React portal bubbles its events along the React tree, not the DOM.** A dialog portalled out of
  `<main>` still delivers its drops to a handler on `<main>`, and every drop on the dialog was taken
  twice. `Home` returns a fragment for this reason: the dialog is a sibling of `<main>`.
- **A drop target that is an element is not the screen.** `<main>` is one screen tall, so a page
  scrolled past it leaves the pointer over the body, where nothing cancels `dragover` and the
  browser opens the image in the tab. Seen in LibreWolf, not seen in Chromium, which was dropped on
  above the fold.
- **A Tailwind opacity modifier composes with the token's own alpha.** Every `*-soft` colour in
  HeroUI is already a `color-mix` against `transparent`, so `bg-accent-soft/90` painted about 13% of
  alpha. Read the emitted rule before trusting a soft colour to cover anything.
- **react-aria writes no `overflow` on what it virtualizes**, deliberately: `Virtualizer`'s
  `CollectionRoot` drops `useScrollView`'s `scrollViewProps` and passes `allowsWindowScrolling:
  true`. The application's CSS decides whether the collection scrolls or the window does.
- **A toast's `role="alert"` is on its content, not on the toast**, which carries `alertdialog`
  unconditionally. The region portals into `document.body` under `data-react-aria-top-layer`, which
  `ariaHideOutside` keeps visible, so `screen` sees a toast and `within(dialog)` does not.
- **The address field is controlled** since block 30, and the queue's entry is the authority:
  `FormData` still carries the field, and `submit()` does not read it.
- **The uncontrolled fields are reset by a key.** The description and the page a pin comes from are
  the DOM's own state; the form carries `key={at}` so advancing the queue empties them rather than
  carrying the last entry's text into the next.
- **`ResizeObserver`, `IntersectionObserver` and `createImageBitmap` are stubbed in
  `src/test/setup.ts`**, and the toast queue is a module-level singleton cleared between tests. A
  journey that renders a toast without the provider of `src/test/app.tsx` finds nothing.

## What is not validated

- **Nothing runs the whole application in a browser here.** The layouts of this lot were read in
  headless Chromium against the built stylesheet, on the markup the journeys render: the overlay's
  colour (block 30), the `Remove` button's hit area (block 20), and the queue's counter and button
  row (block 40). That catches paint and layout; it does not exercise a real drag.
- **The grid's scroll fix has no test and can have none.** jsdom computes no layout. Its check is
  the measurement the specification carries beside block 35, on the running application:
  `[document.scrollingElement.scrollHeight, innerHeight]`, within a pixel or two of each other.
- **`Modal.CloseTrigger`'s `aria-label` override is observable in no test** (decision B'):
  `baseLocale` is `en` and `m.close()` is exactly the string HeroUI hardcodes.
- **A drop of several files from one browser tab is untested against a real browser.** The pairing
  of files with addresses is index by index, which is what `dataTransfer` hands over, and only a
  real drag says whether a tab ever sends more addresses than files.

## What this lot leaves open

- **The handshake does not publish the media types the storage accepts** (`P1`, unchanged). The
  browser still judges a format on `type.startsWith("image/")`; it is the server's surface and this
  lot touched neither it nor the contract.
- **"What the API serves and the web application does not reach yet"** (Features), for the reason
  the previous lot gave: adjacency of file is not adjacency of subject.
- **The grid keeps every page it scrolls** (Known limits) stays a limit, `Home.tsx` being edited
  here without `usePins` being touched.

## Next step

Wrap: the holistic review over `git diff lot/0.27.0-the-header-becomes-icons..origin/main` with
nothing in flight, then the closing block that fixes its findings, reconciles the backlog, corrects
this file and freezes the specification.
