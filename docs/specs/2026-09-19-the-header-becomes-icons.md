# The header becomes icons and adding a pin a dialog

Date: 2026-09-19
Status: Approved by the operator on 2026-09-19; one specification review ran, its 1 CRITICAL,
3 MAJOR and 11 MINOR closed in this document. Frozen when the lot's closing block merges.
Branches: block 10 `feat/the-header-becomes-icons`, block 20 `feat/adding-a-pin-is-a-dialog`
ADRs: `docs/adr/0036-the-icons-come-from-lucide.md` carries decision A. The other two decisions the
review mandate classes architectural go to this document and nowhere else: decision B removes the
client route `/pins/new`, which no document publishes and which the contract does not describe, the
public surface this repository negotiates on being `contract/openapi.json`; decision M extends
`UploadRefusal`, a type local to `clients/apps/webapp/src/lib/uploads.ts` that no package exports
and that never crosses the wire.

`docs/specs/2026-09-19-a-styled-layer-over-react-aria.md` gave the application its controls and its
hierarchy. This lot removes the chrome that survived it: a labelled dropdown, three worded buttons
and a whole screen that a dialog does.

## 1. Goal

Every control in the header is worded, and one of them is a dropdown carrying a title for a choice
between three values. Adding a pin is a route. Every field of the creation form spends a full row of
vertical space on a label that a floating one would buy back, and the file is asked for through the
browser's own file input.

Eight changes, seven asked by the operator and the eighth born from them:

1. The theme control is one icon that cycles the three preferences. No title, no dropdown.
2. Sign out is an icon.
3. Add a pin is an icon.
4. Downloads is an icon, its count a badge, absent at zero.
5. Adding a pin is a dialog, not a page.
6. The creation form drops its prominent labels for floating ones.
7. The file input becomes a clickable drop area.
8. Home and the credentials screens share a header, so a visitor who has not signed in can still
   choose a theme.

## 2. What exists today

```
$ wc -l clients/apps/webapp/src/routes/CreatePin.tsx clients/apps/webapp/src/routes/Home.tsx \
    clients/apps/webapp/src/components/ThemeSwitch.tsx clients/apps/webapp/src/components/TaskCentre.tsx
  104 clients/apps/webapp/src/routes/CreatePin.tsx
  213 clients/apps/webapp/src/routes/Home.tsx
   38 clients/apps/webapp/src/components/ThemeSwitch.tsx
   83 clients/apps/webapp/src/components/TaskCentre.tsx
$ command grep -rn 'renderApp("/pins/new")' clients/apps/webapp/src/journeys/ | wc -l
6
$ command grep -rn 'Downloads (' clients/apps/webapp/src/journeys/ | wc -l
9
$ command grep -rn '\.upload(' clients/apps/webapp/src/journeys/
create-a-pin-by-uploading-a-file.journey.test.tsx:32
create-a-pin-by-uploading-a-file.journey.test.tsx:72
a-failed-download-surfacing-in-the-task-centre.journey.test.tsx:112
$ command grep -rn "app_name" clients/apps/webapp/src --include=*.tsx --include=*.ts | grep -v paraglide/
$ python3 -c "import json;print(list(json.load(open('contract/openapi.json'))['components']['schemas']['HandshakeOutputDto']['properties']))"
['contractVersion', 'limits', 'renditionSizes']
```

Three upload call sites in two journey files: two of them are the creation form's, the third is the
task centre's own file input inside its popover, which this lot leaves alone.

`app_name` sits in both catalogues and is imported nowhere. The handshake publishes no list of
accepted media types, which bounds what the drop area can refuse (section 6).

## 3. The decisions

| # | Decision | Why |
|---|---|---|
| A | The icons are `lucide-react` | `docs/adr/0036-the-icons-come-from-lucide.md` |
| B | `/pins/new` is removed; the dialog is state held by `Home` | The task centre already sits in the header behind it, which is the whole reason the creation screen carried one |
| C | The theme button's accessible name is its current state: `System theme`, `Light theme`, `Dark theme`. The cycle walks `THEME_PREFERENCES` in the order it already declares | With no title and no dropdown the name is the only thing that says where the user is. The operator holds the tri-state button and not an order, so the cycle takes the constant's |
| D | The four icon buttons carry a HeroUI `Tooltip` holding the same string as their accessible name | An icon alone is not discoverable; the react-aria tooltip opens on hover and on keyboard focus |
| E | The downloads icon is always present, its badge only above zero and `aria-hidden` | `Downloads (n)` already carries the count; a visible badge read as well says it twice |
| F | Floating labels, written in the house: `peer` plus `:placeholder-shown`, the real `<Label>` kept | HeroUI v3 removed `labelPlacement` and offers no replacement (`heroui.com/en/docs/react/migration/input`, read 2026-09-19; `command grep -rn labelPlacement` over the installed `@heroui/react` returns nothing). Keeping the label keeps the accessible name and the required marker |
| G | The image address and the drop area both stay, and the address stays required until a file is chosen | Unchanged from today |
| H | Field order: image address, drop area, description, page it comes from | The two ways to give an image touch, so their exclusivity needs no sentence; what describes the pin follows |
| I | The drop area shows a thumbnail of the chosen file and its name | On a pin board the thing being pinned is an image, and it is the only check that catches a wrong file before the upload |
| J | Add a pin keeps a filled primary button, the other three are `ghost`, no separator | The screen's primary verb keeps its weight, stays first in the document for the keyboard, and `order-last` keeps it on the right |
| K | `AppHeader` holds the screen's `<h1>` and renders the theme control itself; session actions are its children | The theme is the only control true on every screen, signed in or not |
| K' | `app_name` is deleted from both catalogues | The product names itself in `index.html`'s `<title>`; a banner repeating it on every screen is the chrome this lot removes |
| L | A drop keeps the first file whose `type` starts with `image/` (Corrected in block 20: a drop carrying no image at all is refused as `file_unsupported` rather than ignored. This decision and decision M contradicted each other there, M pronouncing every refusal at the choice, and a drop that took nothing in silence would let the user believe it had.) | Drag and drop bypasses `accept`, which only the file picker honours |
| M | Two new refusals, `file_unsupported` and `file_unreadable`, stated under the drop area, and every refusal is pronounced when the file is chosen rather than at submission | The recourse differs: choose another file, or fetch an undamaged one. Judging at choice time is also what lets the thumbnail show only an accepted file |
| N | The dialog's content is mounted only while it is open | Fields, file, thumbnail and refusal leave with it, and the object URL is revoked by the cleanup rather than by a close path written by hand |

**Decision C walks `THEME_PREFERENCES` and writes no second list.** The constant is
`["system", "light", "dark"]`, and `nextPreference` steps along it and wraps. The specification
review asked for the cycle's order to be protected against a test written from the same constant;
the operator settled it on 2026-09-19 by holding the tri-state button alone and no particular order,
so there is nothing left to protect and the duplication is refused. What the journey still has to
fail on is a cycle that skips a preference or does not wrap.

**Decision D repeats what decision E refuses to repeat, and that is accepted here.** HeroUI's
`Tooltip` is react-aria's `TooltipTrigger`, which sets `aria-describedby` on the trigger, so a
button whose tooltip holds its own name is announced with that string as name and again as
description. A description is announced after a pause and is skippable; the badge's repetition sat
inside the name itself, with nothing between the two readings. The alternative, a tooltip worded
differently from the name, gives a sighted user and a screen-reader user two different words for one
control, which is worse.

**Decision J moves the theme control and decision K is why.** Today the four are siblings in
`Home.tsx`, in the document order add-a-pin, task centre, theme, sign out. Once `AppHeader` renders
the theme control itself, the theme control leaves that third position; the three session actions
keep their order relative to one another, add a pin stays first in the document, and `order-last`
still puts it on the right.

**Decision M names the drop area, and the name is the invitation.** The area's accessible name is
`drop_image`, which is also its visible text: a label whose visible words differ from its
programmatic name is what WCAG's Label in Name exists against. `image_file` is not removed, the task
centre still using it, so the two `getByLabelText("Image file")` calls in
`create-a-pin-by-uploading-a-file.journey.test.tsx` move to the new string and the one in
`a-failed-download-surfacing-in-the-task-centre.journey.test.tsx` does not.

## 4. The change

| Block | Where | What |
|---|---|---|
| 10 | `clients/apps/webapp/package.json` | `lucide-react` added at the version that has aged out |
| 10 | `components/AppHeader.tsx` | New. The bar, the screen's `<h1>`, the theme control, and a slot for the session's actions |
| 10 | `components/ThemeSwitch.tsx` | A `Tooltip` around an icon-only `Button` cycling three states; `Select`, `ListBox` and `Label` go |
| 10 | `components/TaskCentre.tsx` | The trigger becomes an icon-only `Button` inside `Badge.Anchor`; the badge is `aria-hidden` and rendered only above zero. The accessible name is unchanged |
| 10 | `routes/Home.tsx` | Header extracted to `AppHeader`; add a pin becomes an icon link; sign out becomes an icon |
| 10 | `routes/Credentials.tsx` | The bare `<h1>` becomes `AppHeader`, with no action passed |
| 10 | `lib/theme.ts` | `nextPreference(current)` added, stepping along `THEME_PREFERENCES` and wrapping, with its test |
| 10 | `messages/en.json`, `messages/fr.json` | `theme_current` added; `theme`, `app_name` removed |
| 10 | `clients/AGENTS.md` | `lucide-react` joins the stack line for the web application |
| 20 | `components/CreatePinDialog.tsx` | New, from `routes/CreatePin.tsx` less its header: a `Modal.Backdrop` whose content mounts only while open, a `Field` helper for the floating labels, a drop area, four refusals |
| 20 | `routes/Home.tsx` | The icon link becomes the dialog's trigger and holds its open state |
| 20 | `routes/CreatePin.tsx`, `router.tsx` | The file and `newPinRoute` go |
| 20 | `lib/uploads.ts` | `UploadRefusal` gains `UNSUPPORTED_FORMAT` and `UNREADABLE`; the format check is a pure function with its test |
| 20 | `messages/en.json`, `messages/fr.json` | `drop_image`, `file_unsupported`, `file_unreadable` added |

**The floating label.** The `<Label>` follows the `<Input>` in the document so Tailwind's `peer-*`
variants reach it, and react-aria links the two by identifier rather than by order, so
`getByLabelText` keeps working. The input carries `placeholder=" "`, which is what
`:placeholder-shown` reads, and `pt-6 pb-1`, which wins because `.input` is declared in
`@layer components` and utilities come after it (`@heroui/styles/dist/index.css`, line 1:
`@layer theme, base, components, utilities;`). The transition carries
`motion-reduce:transition-none`.

**The drop area is not react-aria's `FileTrigger`, and the reason is the accessible name.**
`react-aria-components/dist/private/FileTrigger.mjs` calls `filterDOMProps(rest, {global: true})`
without `labelable`, and `filterDOMProps` keeps `aria-label`, `aria-labelledby`, `aria-describedby`
and `aria-details` in `labelablePropNames`, which `global` does not admit. The input is rendered with
no label, no `aria-label` and `className: ""`, so it has no accessible name and no query reaches it.
It exposes no `onDragOver` or `onDrop` either, which decision L needs. The area is therefore a styled
`<label>` around an `<input type="file" class="sr-only">`, with native drag handlers.

This is not a claim about visibility. `user-event@14.6.7` does not refuse a hidden input:
`upload()` guards `isDisabled` alone and then calls `click`, whose only style check reads
`pointer-events`. Probed on the installed chain against `display: none`, `visibility: hidden` and
`sr-only`, all three took the file. `sr-only` is chosen because it keeps the input focusable and
openable from the keyboard, not because the others would fail a test.

**The refusal moves to the choice.** `measured()` runs when a file is chosen, not in `submit`. A
file whose type is not an image is refused before decoding; a decode that throws is caught and
refused as unreadable. `submit` re-runs `uploadRefusal` on the stored measurement, for the case where
the handshake's limits arrived after the file did.

**One adjacent defect, tier 2, answered by the operator during Discuss.** `measured()` calls
`createImageBitmap` with no guard and `submit` is launched with `void`: a file with an image media
type and damaged content rejects into nothing, and the user gets an inert button and no message.
Decision M covers it. It is tier 2 and not tier 1 by size: a new refusal value, two messages in both
catalogues, and every refusal moved out of `submit`, inside a file the same block rewrites into a
dialog. The obligation the tier carries is to ask at the moment of discovery, which was met.

## 5. Blocks

| Block | Branch | Journeys |
|---|---|---|
| 10 | `feat/the-header-becomes-icons` | `choosing a theme against the system` presses the button three times and reads the accessible name and `data-theme` at each, so a cycle that skips a preference or does not return to its start is red; the downloads badge is absent at zero and present at one, both halves in one test; the credentials screen carries a theme control |
| 20 | `feat/adding-a-pin-is-a-dialog` | The three journeys that render `/pins/new` open the dialog from the grid instead; a non-image dropped on the area is not taken and the address stays required; a file with an image media type and unreadable content states so under the area |

Block 10 is green and coherent alone: the creation route survives it untouched, reached by an icon
instead of a worded link. Block 20 removes it.

**Block 10 rewrites the theme journey's assertions and keeps its screens.** Both its cases read
`findByLabelText("Theme")` and an accessible name ending in ` Theme`, which block 10 deletes along
with the `theme` message, so both go red inside block 10 unless it rewrites them. The second case
keeps rendering `/pins/new`, which block 10 leaves standing; block 20 moves it to the credentials
screen, which by then is the other screen carrying the control.

**The badge's observable is the element, not the text.** The badge is `aria-hidden` but present in
the document, so `getByRole` misses it either way, and at one download the trigger's own name is
already `Downloads (1)`, so a text query does not tell the badge from the button. The test reads the
element HeroUI marks `data-slot="badge"` inside `data-slot="badge-anchor"`
(`@heroui/react/dist/components/badge/badge.js`), and asserts its absence at zero and its content at
one in the same test, so the zero half cannot pass alone.

No journey is added and none is removed: the thirteen of `lib/journeys.ts` cover these paths already.

The production count is estimated at about 150 lines under `clients/` for block 10 and about 300 for
block 20, the deletion of `routes/CreatePin.tsx` counting in `git diff --numstat`. Both are inside
the 400 the ecosystem bounds, so no split follows. The estimate is not evidence: each block sums
`git diff --numstat` against `main` at its first green run and says so in its pull request.

## 6. Adjacent backlog items

One item is adjacent by file and stays open, and one limit is adjacent by file and is not an item:

- **"What the API serves and the web application does not reach yet"** (Features) lists boards, tags,
  search, the recycle bin, account management, import, export, and editing or deleting a pin. Every
  one of them is a surface this lot does not build: the lot restyles the controls that already exist
  and moves one of them into a dialog. Adjacency of file is not adjacency of subject, and adopting
  any part of it would make a third block with a spec of its own. It stays open, which is the
  operator's to accept.
- **"The grid keeps every page it scrolls"** (Known limits,
  `docs/adr/0033-the-grid-keeps-every-page-it-scrolls.md`) lives in `Home.tsx`, which block 10 edits.
  It is a recorded limit and not work: a lot closes items, and a limit is closed by the decision that
  records it. Block 10 touches the header of that file and not `usePins`, so nothing here moves it.

No other item in `docs/backlog.md` touches `clients/`.

This lot files one item:

- The handshake publishes the media types the storage accepts, so the browser refuses a format
  before the upload rather than after. Without it `file_unsupported` can only judge on
  `type.startsWith("image/")`, so an SVG or a TIFF passes here and is refused by the server.

## 7. Out of scope

What this lot does not change, and how a reader notices if it did: `git diff --stat` against `main`
shows no path under `api/`, no `contract/openapi.json`, and nothing outside
`clients/apps/webapp/`, `clients/AGENTS.md` and this lot's dated documents. The grid, the tiles and
the `PinDialog` that opens a pin are untouched; the credentials form keeps every field it has, only
its `<h1>` moving into `AppHeader`.

## 8. Pitfalls

- **A HeroUI control is a compound component**, and two of this lot's are: `Badge` is `Badge.Anchor`
  around the trigger with `Badge.Label` holding the count, and `Tooltip` is a root wrapping the
  trigger plus `Tooltip.Content`. Given the wrong shape either compiles and renders nothing usable.
  `clients/AGENTS.md` carries the rule; read the rendered DOM.
- **The badge's count must not reach the accessibility tree.** The button's name already carries it,
  and `downloads_partial` renders `1+` in both places.
- **`FileTrigger` drops every `aria-*` labelling property.** That is the reason it is unusable here,
  not visibility, and the same trap waits for any other react-aria component whose props go through
  `filterDOMProps(rest, {global: true})`.
- **The object URL of the thumbnail leaks if it is revoked on a close path.** Decision N mounts the
  content only while the dialog is open, so the revoke belongs to a `useEffect` cleanup and runs on
  every exit, including Escape and a click on the backdrop.
- **The theme control is now the only one on the credentials screens**, and `Credentials.tsx`
  carries no `useTheme` today. `AppHeader` renders it, so nothing else has to remember to.
