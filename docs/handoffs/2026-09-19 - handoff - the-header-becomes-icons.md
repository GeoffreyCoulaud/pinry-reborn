# Handoff: the header becomes icons and adding a pin a dialog

Date: 2026-09-19
Specification: `docs/specs/2026-09-19-the-header-becomes-icons.md`
ADR: `docs/adr/0036-the-icons-come-from-lucide.md`
Blocks: 10 `feat/the-header-becomes-icons`, 20 `feat/adding-a-pin-is-a-dialog`,
closing `fix/the-holistic-findings`
Tier: Spec. Both reviews ran: the specification review returned 1 CRITICAL, 3 MAJOR and 11 MINOR,
the holistic 0 CRITICAL, 3 MAJOR and 6 MINOR.

## Current state

The web application's header carries four icons and no words. `components/AppHeader.tsx` holds the
screen's heading and renders the theme control itself, so the credentials screens carry one too: a
visitor with no session could not choose a theme before this lot, `Credentials.tsx` having no
`useTheme` at all. The three session actions are its children, and Home is the only screen passing
any.

The theme control is one button cycling the three preferences, its accessible name saying which one
is on. `app_name` is gone from both catalogues; the product names itself in `index.html`'s `<title>`
alone.

Adding a pin is a dialog over the grid, not the route `/pins/new`, which is deleted. Its content is
mounted only while it is open, so the fields, the file, the thumbnail and the refusal leave with it
and the object URL is revoked by the cleanup rather than by a close path written by hand. The form's
labels float instead of taking a row each, and the file is asked for through a dashed drop area
showing a thumbnail of what was chosen.

Every upload refusal is now pronounced when the file is chosen, not at submission. Four exist:
too heavy, too many pixels, not an image, and unreadable. The last closes a defect the creation
screen carried, `createImageBitmap` called with no guard inside a promise nobody awaited.

## What the reviews caught, and where each finding went

The specification review ran before a line was written and every one of its findings was closed in
the specification. The one that mattered most was a false claim of mine: I had written that
`user-event` refuses a hidden input, and it does not. `upload()` guards `isDisabled` alone and then
calls `click`, whose only style check reads `pointer-events`. The real reason react-aria's
`FileTrigger` is unusable here is that it passes `filterDOMProps(rest, {global: true})` without
`labelable`, so `aria-label` and its three siblings are dropped and the input has no accessible name
at all. The decision survived; the reason and the pitfall it had taught did not.

The holistic review's findings all became the closing block, except one:

- **A drop carrying no file discarded the file already accepted, in silence.** `choose` called
  `setChosen(null)` before knowing what it had been given. The gesture is the ordinary one on a pin
  board: dragging an image out of another browser tab hands over `text/uri-list` and an empty
  `dataTransfer.files`.
- **The drop area's focus was invisible.** Its only focusable element is `sr-only` and nothing drew
  a ring, where the control it replaced carried HeroUI's `.input` focus block.
- **Decisions D and E had no guard**: removing the trigger's `Tooltip.Content` and the badge's
  `aria-hidden` together left all thirteen journeys green.
- **The red-green norm is the exception**, settled by the operator rather than fixed. See below.

## The norm that changed

`agents/engineering.md` no longer requires proving a run red. "Test first" stays; the proof does
not. Two holistic reviews in a row found the proof absent and the coverage genuine, and the previous
lot had buried the finding in a handoff line rather than settling it. Three of this lot's own fixes
are a hit area, a padding and a label's position: they have no observable outside layout and paint,
jsdom computes neither, and an assertion on the class would test the instrument.

`agents/engineering.md` is also converted to the mandate-before-argument form of
`docs/adr/0029-a-workflow-phase-states-its-mandate-before-its-argument.md`, which it was the last
document under `agents/` to owe. `agents/writing.md` no longer names it as pending.

## Pitfalls

- **`FileTrigger` drops every `aria-*` labelling property**, and so does any react-aria component
  whose props go through `filterDOMProps(rest, {global: true})`. It is not a visibility problem.
- **A tooltip's accessible name is not computed from its content.** `findByRole("tooltip", { name })`
  never resolves although the element is there; read `textContent`. A tooltip also survives the loss
  of focus for react-aria's close delay, so a query right after a tab can return the previous one.
- **Every icon button goes through `components/IconButton.tsx`.** Decision D's invariant is that the
  tooltip and the accessible name are one string; four hand-written sites held it nowhere, and the
  fifth would have been a paste that edited one of the two.
- **A refusal pronounced at the choice now leaves an already accepted file in place.** That is
  deliberate: dropping a PDF over a good image says so and keeps the image. It also means the
  too-heavy case behaves that way, which the review did not name.
- **The floating label needs both `peer-focus:` and `peer-not-placeholder-shown:`.**
  `:placeholder-shown` alone cannot see focus, the placeholder being a space that focus does not
  remove. A variant name Tailwind does not know emits nothing and breaks no build, so read the
  compiled CSS rather than the source.

## What is not validated

- **Nothing runs the application.** The three fixes that came from the operator's own reading of it,
  the hit area, the vertical rhythm and the label floating on focus, were each invisible to a green
  suite and to both reviews.
- **The drop area's hit area and its focus ring are asserted by class, not by behaviour.** jsdom
  computes no layout, so neither the stretched pseudo-element nor the ring is read as a user would
  see it.

## What this lot leaves open

Two items filed in `docs/backlog.md`, both `P1`: the handshake publishing the media types the
storage accepts, and an image dragged from another browser tab being refused rather than fetched
from the address that drop does carry. The adjacent item "What the API serves and the web
application does not reach yet" stays open: this lot restyled controls that already existed and
built no surface.

**The second item was costed and deferred by the operator on 2026-09-19**, so its reasoning is here
rather than in the entry. It is about fifty lines and asks nothing new of the server: the form
already has an `Image address` field the server fetches, and such a drop carries that address in
`dataTransfer.getData("text/uri-list")`. What it needs is a pure `uriFromDrop` with its test, the
address field becoming controlled so a drop can fill it, and a drop handler trying files, then the
address, then refusing. The pure function is where the work is: `text/uri-list` may carry several
lines, a line opening with `#` is a comment, and the scheme must be filtered to `http` and `https`,
a tab sometimes handing over a `blob:` the server cannot reach. Fetching the bytes in the browser
instead is refused: it would need CORS on the origin site and fail often, where the server reaches
the address from its own position, which is the whole argument behind the address field. No new
failure mode either, an address that is not an image being refused by the server and surfacing in
the task centre exactly as a typed one does.
