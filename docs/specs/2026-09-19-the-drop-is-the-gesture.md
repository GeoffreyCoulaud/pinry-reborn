# The drop is the gesture

Date: 2026-09-19
Status: Drafted 2026-09-19. One specification review ran: 0 CRITICAL, 6 MAJOR and 6 MINOR, all
closed in this document. Awaiting the operator. Frozen when the lot's closing block merges.
Branches: block 10 `feat/the-drop-area-answers-the-drag`, block 20 `feat/a-refusal-is-a-toast`,
block 30 `feat/a-drop-on-the-grid-opens-the-form`, block 40 `feat/a-drop-carries-several-pins`
ADRs: `docs/adr/0037-a-refused-element-speaks-in-a-toast.md` carries decision E, the application
gaining a notification surface being a decision no specification should hold alone. Decision G goes
here and to no ADR: it sends a field `contract/openapi.json` already declares and
`PinCreator` already stores, on a path this lot does not redraw, so it corrects a client that was
dropping data rather than deciding anything about the protocol. No other decision touches a public
surface, a protocol between two components, or a library setting.

`docs/specs/2026-09-19-the-header-becomes-icons.md` made adding a pin a dialog with a drop area.
The drop area does not answer a drag, the grid behind it answers nothing at all, and one drop is one
pin. This lot makes the drop the gesture: it works over the whole screen, it carries several pins,
and it says no where the gesture happened.

## 1. Goal

Four changes the operator asked for, and three that fall out of them:

1. The dialog's drop area takes an active style while something is dragged over it.
2. The dialog carries a close cross, top right. Escape and the backdrop keep working.
3. A drop on the pins view opens the creation dialog, filled.
4. A drop carrying several images or addresses is a queue: the form says which pin of the group it
   is on, and an `Ignore` button sits just before `Add`, present only above one entry.
5. An address dropped is an address fetched, which closes a `P1` backlog item (section 6).
6. Every refusal of an element becomes a toast, wherever the element came from.
7. `Remove` on the thumbnail, because a file chosen can otherwise no longer be un-chosen.

## 2. What exists today

```
$ wc -l clients/apps/webapp/src/components/CreatePinDialog.tsx clients/apps/webapp/src/routes/Home.tsx \
    clients/apps/webapp/src/images.ts
  192 clients/apps/webapp/src/components/CreatePinDialog.tsx
  219 clients/apps/webapp/src/routes/Home.tsx
  176 clients/apps/webapp/src/images.ts
$ command grep -n 'sourceMediaUrl' clients/apps/webapp/src/images.ts
151:        sourceMediaUrl: "url" in source ? source.url : null,
$ python3 -c "import json;print(json.load(open('contract/openapi.json'))['components']['schemas']['PinCreationInputDto']['properties']['sourceMediaUrl'])"
{'type': ['string', 'null']}
$ command grep -o "role: '[a-z]*'" clients/node_modules/.pnpm/react-aria@3.52.1_*/node_modules/react-aria/dist/private/toast/useToast.mjs | sort -u
role: 'alert'
role: 'alertdialog'
$ command grep -c "^  it(" clients/apps/webapp/src/journeys/create-a-pin-by-uploading-a-file.journey.test.tsx
7
```

`choose()` takes the first image of a drop and refuses a drop carrying none. `Home.tsx` has no drag
handler. `submit()` builds `source` from the address field and then overwrites it with the file, and
`useCreatePin` writes `sourceMediaUrl` from that `source`, so an address typed beside a chosen file
never reaches the server.

## 3. The decisions

| # | Decision | Why |
|---|---|---|
| A | A dragged element over a drop target shows an active style, driven by a depth counter | `dragenter` and `dragleave` both bubble, and moving from a container onto its own child fires `dragleave` at the container (MDN, `dragleave` event). A handler reading `event.target === event.currentTarget` cannot tell that from a real exit, so the style flickers on every tile crossed |
| A' | The counter is a pure function in `src/lib/`, and the target exposes `data-dragging` | The module is not the bubbling fix, which is the counting; it is the coverage perimeter, `src/lib/**` being the whole of it (`clients/AGENTS.md`). The attribute is what makes A falsifiable at all: jsdom computes no style, so `enter, enter, leave` leaving `data-dragging` set is the assertion, and a handler written the naive way fails it |
| B | The close control is `Modal.CloseTrigger`, named with `m.close()` | It renders a `CloseButton` with `slot="close"`, which react-aria wires to the dialog's own close. Its `aria-label` is the hardcoded string `Close` (`@heroui/react/dist/components/close-button/close-button.js`), and `...rest` is spread after it, so the catalogue's string overrides it |
| B' | **Accepted limit**: the override of decision B is observable in no test this repository can write | `baseLocale` is `en`, `m.close()` in English is exactly the `Close` HeroUI hardcodes, and nothing in `src/journeys/` switches locale. A test asserting the name passes whether the override wins or the fallback does. Introducing a locale switch for one attribute buys a guard on that attribute and a reload hazard everywhere: Paraglide's `setLocale` reloads the document unless told not to. The override is written because it is correct, not because it is guarded |
| C | The close cross carries no tooltip, and so departs from decision D of the previous lot | That decision's premise is that an icon alone is not discoverable. A close cross in a dialog's top right corner is the one icon that is, and `IconButton` cannot render it: the control here is `Modal.CloseTrigger`, not `Button`, and wrapping it by hand would write the name twice at one site, which is what `IconButton` exists to prevent |
| D | A drop on the pins view opens the dialog. The target is the whole screen, and the active style is an overlay over the grid | A full grid is a field of images, so a ring around it reads as decoration. Aiming at the header or the margin is a slip that a narrower target punishes for nothing |
| E | An element refused is a toast, one per distinct reason, from all four origins: a drop on the grid, a drop on the dialog's area, the file picker, and the re-judge at submission | The verdict belongs to the gesture, not to an entry it does not concern. It is also the only place a drop refused in full can speak: no dialog opens for it. Recorded in `docs/adr/0037-a-refused-element-speaks-in-a-toast.md` |
| F | A pin created and refused by the server keeps its message in the form, beside the button | A different event: the entry is still on screen and the message belongs to it |
| G | An entry carries provenance and bytes independently. The address field is never disabled, is always sent as `sourceMediaUrl`, and supplies the bytes only when no file is chosen | `sourceMediaUrl` is provenance the server stores, exports and imports and never fetches (`PinController` reads a blank one as none and hands it to `PinCreator`; the bytes go through `PUT /api/v1/pins/{pinId}/image`). Today's `"url" in source ? source.url : null` therefore drops the provenance of every uploaded pin |
| H | One element dropped replaces the current entry's file. Several dropped go to the end of the queue. An address dropped is additive and never removes a file | Dropping one thing on an open form is the correction gesture, and taking it away would empty drag and drop of its point. Dropping three is "add these three", not "replace mine and add two". An address touches provenance alone, so it has nothing to remove |
| I | `Remove` on the thumbnail empties the current entry's element. `Ignore` abandons the current entry and advances | Two verbs, two effects on the counter: `Remove` leaves it alone, `Ignore` advances it. Without `Remove`, decision G leaves no way back to an address once a file is chosen |
| J | The counter is the compact `{current}/{total}`, visible only above one entry, with `pin_progress` as its accessible name | The operator settled the compact form on 2026-09-19. A slash between two numbers needs no catalogue entry; the sentence a screen reader gets does |
| K | The queue advances on the server's success, as `onSuccess: close` does today. A refusal keeps the entry on screen | An entry the server would not take is an entry to correct, not one to skip past |
| L | The file picker takes `multiple` and enters by the same path as a drop | What is chosen with the mouse and what is dropped are judged once, in one place |
| M | Each file is measured in series and its `ImageBitmap` closed at once | A decoded bitmap is width by height by 4 bytes, so a 4000 by 3000 photo holds 48 MB and ten held together 480 MB. `close()` releases the graphical resources immediately (MDN, `ImageBitmap.close()`). `measured()` closes nothing today |
| N | One drop is judged in full when it lands, so its own total is fixed before its first entry is shown. A later drop on an open form appends, and the total it moves is one the user has read | A total that moves while a single drop is being worked through is a counter nobody can read; a total that grows because the user dropped more is the drop answering |

**Decision E deletes the form's upload alert.** `CreatePinDialog.tsx` holds two `role="alert"`
paragraphs; the upload one goes and the `create.isError` one stays (decision F). A react-aria toast
carries `role="alert"`, so the query shape is unchanged; it becomes `alertdialog` when it holds a
focusable element, which HeroUI's default toast content may well do. **Read the rendered DOM before
writing the query.**

**Decision H is the operator's, taken against my recommendation on 2026-09-19.** I had proposed that
a drop never replace, with `Remove` as the way back. The operator answered that a single element
dropped must modify the current entry, drag and drop losing its point otherwise. Recorded here
because a reader will otherwise read the branch on the dropped count as an accident. Decisions H and
J rest on that conversation and on nothing in the tree; the operator's approval of this document is
what settles them.

## 4. The change

| Block | Where | What |
|---|---|---|
| 10 | `lib/drags.ts` | New. `dragDepth(depth, "enter" \| "leave" \| "drop")`, with its test |
| 10 | `components/CreatePinDialog.tsx` | The drop area reads the depth and exposes `data-dragging`; `Modal.CloseTrigger` in the dialog's top right |
| 20 | `main.tsx`, `test/app.tsx` | `Toast.Provider` at the root |
| 20 | `components/CreatePinDialog.tsx` | The four refusals become toasts and the inline alert goes; `Remove` on the thumbnail |
| 20 | `images.ts` | `PinCreation` carries `sourceMediaUrl` of its own, read from the form (decision G) |
| 20 | `messages/{en,fr}.json` | `remove` |
| 30 | `lib/uris.ts` | New. `urisFromDrop(text)`, with its test: `text/uri-list` lines, `#` comments dropped, `http` and `https` alone kept |
| 30 | `lib/drops.ts` | New. The partition of a drop into elements kept and reasons refused, with its test |
| 30 | `components/CreatePinDialog.tsx` | The address field becomes controlled so a drop can fill it; the drop path reads `text/uri-list` |
| 30 | `routes/Home.tsx` | Drag handlers on `<main>` (Corrected: on `window`, the paragraph below saying why), the overlay, and the dialog opened with what was dropped |
| 30 | `messages/{en,fr}.json` | `drop_to_add`, `drop_unsupported` |
| 35 | `routes/Home.tsx` | The grid declares its own `overflow`, so the waterfall scrolls inside its box rather than pushing the document |
| 40 | `lib/drops.ts` | (Corrected: this row was not in the table) `entriesOf` and `withDrop`, the queue's pure half, with their tests |
| 40 | `components/CreatePinDialog.tsx` | The queue: entries, the current index, the counter, `Ignore`, `multiple` on the picker |
| 40 | `messages/{en,fr}.json` | `ignore`, `pin_progress` |

**What the drop path does, once, in both places (block 30).** Files are partitioned by
`isImageFile`; the addresses come from `urisFromDrop(dataTransfer.getData("text/uri-list"))`; each
file is measured and judged against the handshake's limits (decisions M and N). What is kept becomes
entries, what is not becomes one toast per distinct reason (decision E). A drop that keeps nothing
opens no dialog.

**`drop_unsupported` exists because an address can be refused too.** A drop carrying a `blob:` alone,
which a browser tab does hand over, maps to no `UploadRefusal`, and all four existing messages speak
of a file. It reads `Nothing in that drop could become a pin.`

**The overlay and the dialog's area are two targets and one counter.** Each holds its own depth
(decision A), and the dialog being portalled out of `<main>` by react-aria's `Overlay`, a drop on it
never also reaches the grid's handler.

(Corrected twice in block 30, both against the running application. First, a React portal bubbles
its events along the React tree and not the DOM, so a drop on the dialog's area was taken twice and
raised two identical toasts; the dialog is now a sibling of `<main>`. Then the screen's target
stopped being an element at all: `<main>` is one screen tall, and a page scrolled past it leaves
the pointer over the body, where nothing cancels `dragover` and the browser opens the image in the
tab. Seen in LibreWolf, where the whole-page drop did not work at all while the dialog's own area
did; not seen in Chromium, which was dropped on above the fold. The handlers are on `window` while
the dialog is closed, and nothing is listening while it is open, so the dialog owns its own gesture
and the counter cannot be entered twice. The overlay is anchored to the viewport rather than to
`<main>`, which is the same defect seen from the other side: it scrolled away with the document.)

## 5. Blocks

| Block | Branch | Journeys |
|---|---|---|
| 10 | `feat/the-drop-area-answers-the-drag` | None added. `create a pin by uploading a file` gains two cases: the close cross closes the dialog, and `enter, enter, leave` over the area leaves `data-dragging` set |
| 20 | `feat/a-refusal-is-a-toast` | None added. Four of the seven cases of `create a pin by uploading a file` move their assertion from the area to the toast (Corrected: five of them do, as the case table below already says, and an eighth case is added for `Remove`, decision I naming no other way to observe it) |
| 30 | `feat/a-drop-on-the-grid-opens-the-form` | `drop an image on the grid to add a pin` added to `REQUIRED_JOURNEYS` |
| 35 | `feat/the-grid-scrolls-inside-its-own-box` | None added, and none can be. jsdom computes no layout, and an assertion on the utility would test the instrument. Its check is the measurement in the browser, stated below |
| 40 | `feat/a-drop-carries-several-pins` | `add several pins from one drop` added to `REQUIRED_JOURNEYS` |

**Block 35 was not in this document and is the operator's, adopted on 2026-09-20.** Block 30's
overlay defect was the symptom: the page scrolled where the application is built so that only the
grid does. Measured on the running application, the document held 2228 pixels of content against a
window of 1321, and every ancestor overflowed, `<main>` included. The grid's own box is correctly
bounded (`clientHeight` 1224, its parent's 1224) and carries `overflow-y: visible`, so its 2147
pixels of waterfall spill out of it.

The cause is react-aria's, and it is deliberate: `Virtualizer`'s `CollectionRoot` keeps
`useScrollView`'s `contentProps` and drops the `scrollViewProps` that carry the `overflow`, passing
`allowsWindowScrolling: true` (`react-aria-components@1.21.1`, `dist/private/Virtualizer.mjs`). The
application's CSS decides whether the collection scrolls or the window does, and it had not said.

**Its check is a measurement, not a test.** On the running application, on the pins view:

```js
[document.scrollingElement.scrollHeight, innerHeight]
```

Two numbers within a pixel or two of each other. Before block 35 they were 2228 and 1321.

**`create-a-pin-by-uploading-a-file.journey.test.tsx` is rewritten across three blocks**, and its
seven cases go as follows. The file is 235 lines today, which is why it is named here rather than
left to a block to discover.

| Case | Block | What it becomes |
|---|---|---|
| a file heavier than the deployment stores | 20 | The message is read off the toast; no request still leaves |
| a file whose bytes decode to nothing | 20 | Same, and the area still keeps the file already accepted |
| anything but an image dropped on the area | 20, 30 | 20 moves the message to the toast; 30 gives its `dataTransfer` stub a `getData`, which the new drop path calls |
| the keyboard on the drop area | 10 | Unchanged in substance; block 10 edits the area's classes around it |
| a drop carrying no file at all | 20, 30 | 20 moves the message; 30 turns it around, that drop now carrying an address which fills the field, and the refusal case becomes a `blob:` reaching `drop_unsupported` |
| limits that arrive after the file | 20 | The re-judge at submission is decision E's fourth origin: the message is a toast and the file still leaves the entry |
| a file the deployment stores | 20 | The request body now carries `sourceMediaUrl` from the field rather than `null` (decision G) |

Block 30 does not split. A drop on the grid has nowhere to say no until the toasts exist, which is
why the toasts are block 20 and not part of it.

Each block is green and coherent alone: block 10 changes how the drop area answers and adds one
control, block 20 moves every refusal to the surface ADR 0037 records and fixes the provenance,
block 30 gives the grid a drop with one entry behind it, block 40 makes that entry a queue.

**Both bounds, estimated per block**, the 400 production lines under `clients/` and the strict 600
total:

| Block | Production | Total |
|---|---|---|
| 10 | about 60 | about 120 |
| 20 | about 90 | about 230 |
| 30 | about 130 | about 350 |
| 40 | about 90 | about 190 |

The totals carry the journey rewrites and the new `src/lib/` tests, which run 17 to 92 lines each in
this tree. No block is near either bound, so none splits. The estimate is not evidence: each block
sums `git diff --numstat` against `main` at its first green run and says so in its pull request.

**What jsdom cannot see, and what is asserted instead.** The active style and the overlay are layout
and paint, and nothing asserts a class. `dragDepth` is a pure function with 100% of `src/lib/`
behind it; `data-dragging` is what ties it to the target (decision A'), and the overlay's presence
in the document is behaviour rather than paint.

## 6. Adjacent backlog items

- **"An image dragged from another browser tab is refused rather than fetched"** (`P1`, new
  2026-09-19) is closed by this lot, in block 30. Its reasoning is in
  `docs/handoffs/2026-09-19 - handoff - the-header-becomes-icons.md`, and decision G is what makes
  the address it carries reach the server.
- **"The handshake does not publish the media types the storage accepts"** (`P1`, new 2026-09-19)
  stays open. It is the server's surface and the contract's, where this lot touches neither, and the
  browser's judgement stays `type.startsWith("image/")` exactly as it is today. The operator's to
  accept.
- **"What the API serves and the web application does not reach yet"** (Features) stays open, for
  the reason the previous lot gave: adjacency of file is not adjacency of subject.
- **"The grid keeps every page it scrolls"** (Known limits) lives in `Home.tsx`, which block 30
  edits. It is a recorded limit and not work, and nothing here touches `usePins`.

## 7. Out of scope

**Run at each block's tip, against `main`**: `git diff --stat main` shows no path under `api/`, no
`contract/openapi.json`, and nothing outside `clients/apps/webapp/` and this lot's dated documents.
The grid, the tiles, the task centre and the pin dialog are untouched. Fetching an address's bytes
in the browser stays refused: it needs CORS on the origin site and fails often, where the server
reaches the address from its own position.

## 8. Pitfalls

- **`dragover` must call `preventDefault()` or no `drop` fires at all.** The handler that reads the
  depth is not the handler that allows the drop.
- **A toast's role is `alert` until it holds something focusable, then `alertdialog`.** Read the
  DOM HeroUI renders rather than the role react-aria's hook suggests.
- **HeroUI wraps `UNSTABLE_ToastRegion` and `UNSTABLE_ToastQueue`.** The prefix is react-aria's, not
  HeroUI's: `Toast` and `toast()` are HeroUI's public API and are what this lot calls.
- **`Modal.CloseTrigger` hardcodes `aria-label="Close"` in English**, and nothing here can catch a
  version that stops letting `...rest` override it (decision B').
- **A `dataTransfer` stub with no `getData` throws** once block 30's path reads `text/uri-list`.
  Three of them sit in the upload journey today.
- **`measured()` leaks its bitmap today** (decision M). A tier 1 fix, contained in the function.
- **The address field becomes controlled** in block 30 so a drop can fill it, which changes how the
  form is read at submit: `FormData` still carries it, but the state is now the authority.
