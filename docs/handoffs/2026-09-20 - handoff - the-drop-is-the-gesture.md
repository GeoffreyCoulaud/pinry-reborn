# Handoff: the drop is the gesture

Date: 2026-09-20
Specification: `docs/specs/2026-09-19-the-drop-is-the-gesture.md`
ADR: `docs/adr/0037-a-refused-element-speaks-in-a-toast.md`
Blocks: 10 `feat/the-drop-area-answers-the-drag` (#159), 20 `feat/a-refusal-is-a-toast` (#160),
30 `feat/a-drop-on-the-grid-opens-the-form` (#161), 35 `feat/the-grid-scrolls-inside-its-own-box`
(#162), 40 `feat/a-drop-carries-several-pins` (#163), closing `fix/the-holistic-findings`
Tier: Spec. The specification review ran before a line was written: 0 CRITICAL, 6 MAJOR, 6 MINOR,
all closed in the document. The holistic review ran at the head of Wrap over
`git diff lot/0.27.0-the-header-becomes-icons..origin/main`: 0 CRITICAL, 3 MAJOR, 7 MINOR, and the
section below names the exit each one took.

## Current state

**The gesture is the whole screen.** While the creation dialog is closed, `Home` listens for the
four drag events on `window` and veils the viewport with an overlay; a drop is judged where it
landed and opens the dialog already holding what survived. While the dialog is open nothing on the
window counts or judges, so the dialog owns its own gesture and no drop is counted twice. **One
window listener never unsubscribes**: the bare canceller on `dragover` and `drop`, without which a
drop missing the dialog's own area makes the browser navigate to the file and destroys the queue.

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
that way and the CSS had never said so, so the whole document scrolled. Two visual defects it
surfaced were fixed after block 35's pull request and are recorded nowhere else: `5690e78a` gives
the grid `-mx-4` so the scrollbar sits at the viewport edge rather than sixteen pixels inside the
shell's padding, and `153e08be` takes the shell's bottom padding off (`p-4` to `px-4 pt-4`) so no
band of background stands below the scroll container at any scroll position. Both were seen on the
running application and neither carries a number: what each one looked like is in its commit
message, and the only figure the lot has for this area is the measurement below.

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

## What the holistic review found, and where each finding went

`.reviews/the-drop-is-the-gesture-holistic.md` holds the report. Every finding was fixed in the
closing block; none went to the backlog, none was refused.

- **MAJOR, `lib/drops.ts`: a refused file shifted every address behind it**, so a kept file was
  paired with another picture's address and a phantom entry asked the server to fetch it. Fixed:
  `partitionDrop` drops the address standing at a refused file's index, the pair being one picture.
- **MAJOR, `CreatePinDialog.tsx`: the server's refusal survived `Ignore`**, so the next entry opened
  already saying it had been refused. Fixed: `advance()` calls `create.reset()` first.
- **MAJOR, `Home.tsx`: nothing cancelled `dragover` while the dialog was open**, so a drop missing
  its area navigated to the file and lost the queue. Fixed: the cancelling is its own effect and
  never unsubscribes; only the counting and the judging are gated on the dialog being closed.
- **MINOR, `CreatePinDialog.tsx`: `take()` corrected the entry the user had left.** Fixed: the
  entries and the index are one state, so the updater reads the index the user is on now.
- **MINOR, `Home.tsx`: the handshake landing mid-drag tore the listeners down.** Fixed: `limits` is
  read from a ref inside the drop, and the effect depends on the dialog's state alone.
- **MINOR, the specification's block 35 check had no recorded result.** Fixed: re-run, below.
- **MINOR, block 35's two follow-on fixes were recorded nowhere.** Fixed: in the current state above.
- **MINOR, fourteen comments closed on a bare `(decision X)` pointer**, and ten ran past the
  two-line rule. Fixed: the letter is gone where the sentence stands alone, and the surplus with it.

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
- **Cancelling a drag is not the same job as counting it.** Unsubscribing the window listeners to
  stop double-counting also stopped the cancelling, and the browser then navigates to any file
  dropped where nothing prevents the default. The two jobs are two effects for this reason.
- **`overflow-y: visible` beside `overflow-x: hidden` computes to `auto`** (CSS Overflow, the
  computed-value rule). Flipping one axis therefore neutralises nothing: to see the grid push the
  document again, both utilities come off.
- **`ResizeObserver`, `IntersectionObserver` and `createImageBitmap` are stubbed in
  `src/test/setup.ts`**, and the toast queue is a module-level singleton cleared between tests. A
  journey that renders a toast without the provider of `src/test/app.tsx` finds nothing.

## What is not validated

- **Nothing runs the whole application in a browser here.** The layouts of this lot were read in
  headless Chromium against the built stylesheet, on the markup the journeys render: the overlay's
  colour (block 30), the `Remove` button's hit area (block 20), and the queue's counter and button
  row (block 40). That catches paint and layout; it does not exercise a real drag.
- **The grid's scroll fix has no test and can have none.** jsdom computes no layout. Its check is
  the measurement the specification carries beside block 35:
  `[document.scrollingElement.scrollHeight, innerHeight]`, within a pixel or two of each other.
  Re-run by the closing block on 2026-09-20 and **`[1234, 1234]`**, against **`[1788, 1234]`** with
  both `overflow` utilities taken off the grid, which is what says the check can fail at all. What
  produced them: the bundle `pnpm run build` writes, served with a stubbed API answering the
  session, the handshake and forty ready pins, read in Chromium 153.0.8010.52 at
  `--headless --window-size=1600,1321`. **Not the operator's running application**: no API, no real
  images, and the numbers therefore differ from the 2228 against 1321 the specification carries.
- **The entry corrected after an await is fixed and untested.** Racing a drop's judgement against a
  click on `Ignore` has no deterministic shape in jsdom, and the queue is view code, outside the
  coverage bound. What holds it is the state's shape: the index cannot be read stale because the
  updater is the only thing that reads it.
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

The closing block's pull request merged, the lead tags `lot/0.28.0-the-drop-is-the-gesture` on that
merge and pushes it, then reports the lot and its friction points. The backlog needed no change:
the one item this lot closes was deleted in block 30's own pull request, and nothing here was
refused or handed to another lot.
