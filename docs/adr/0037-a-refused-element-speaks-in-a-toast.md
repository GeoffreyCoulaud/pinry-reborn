# 0037. A refused element speaks in a toast

Status: Accepted
Date: 2026-09-19
Specification: `docs/specs/2026-09-19-the-drop-is-the-gesture.md`, decision E.
Related: `docs/adr/0035-a-styled-layer-over-react-aria-components.md`, which chose the control layer
this surface is taken from. Written because the specification review found that adopting a
notification surface for the whole application is a decision no specification should hold alone.

## Context

Until now a file refused by the browser said so in a `<p role="alert">` under the creation form's
drop area, the only place an element could be offered. A drop becomes a gesture on the whole screen:
it lands on the grid, with no form open and possibly none to open, since a drop whose every element
is refused opens nothing. The message has nowhere to go.

The task centre is not that place. It reads `useImageDownloads`, which is what the server is
downloading; a refusal the browser pronounced has no row there, and giving it one would mean
inventing a local store, a component and a lifetime for it.

HeroUI 3.2.6 ships the surface: `Toast.Provider` at the root, `toast()` at the call site. It wraps
react-aria's `UNSTABLE_ToastRegion` and `UNSTABLE_ToastQueue`, and a toast carries `role="alert"`,
which is the role the inline paragraph already had.

## Decision

1. **An element the browser refuses speaks in a toast**, wherever the element came from: a drop on
   the grid, a drop on the form's area, the file picker, or the re-judge at submission.

   **Fails if** a refusal needs an action attached to it, a toast being read and then gone.

2. **What the server refuses keeps its message in the form**, beside the control that sent it. The
   entry is still on screen and the message belongs to it.

3. **This is not a notification centre.** Nothing else moves here: the task centre keeps the
   server's downloads and their recourse, and the screens keep their own `role="alert"` paragraphs
   for a query that failed.

## Consequences

- **`Toast.Provider` is mounted at the application's root**, in `main.tsx` and in `src/test/app.tsx`,
  so a component that calls `toast()` needs no provider of its own.
- **A toast's role is `alert` until it holds a focusable element, then `alertdialog`.** HeroUI's
  default content decides that, so a test reads the rendered DOM rather than the role react-aria's
  hook suggests.
- **The `UNSTABLE_` prefix is react-aria's, not HeroUI's.** The application calls `Toast` and
  `toast()`, which are HeroUI's public API, and a react-aria rename reaches it through a HeroUI
  version rather than through this decision.
