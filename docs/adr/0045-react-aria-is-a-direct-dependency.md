# 0045. React Aria is a direct dependency

Status: Accepted
Date: 2026-09-27
Specification: `docs/specs/2026-09-27-the-pin-opens-beside-its-details.md`, decision F.
Written in block 20.
Extends: `docs/adr/0035-a-styled-layer-over-react-aria-components.md`, which put React Aria Components
and HeroUI in the stack and nothing under them.

## Context

The pin viewer steps to the next pin on a horizontal swipe. React Aria's `useMove` normalises mouse,
touch and pointer events into one move with its deltas, and ends it on `pointercancel` as on
`pointerup`, which is what a swipe the browser takes back for a vertical scroll needs. It is exported
by `react-aria` alone: neither `react-aria-components` 1.21.1 nor `@heroui/react` 3.2.6 re-exports it.

`react-aria` was already in the tree, under both of them:

```
$ npm view react-aria-components@1.21.1 dependencies.react-aria
3.52.1
$ npm view @heroui/react@3.2.6 dependencies.react-aria
^3.52.1
$ npm view react-aria@3.52.1 time --json | grep '"3.52.1"'
  "3.52.1": "2026-09-04T19:29:41.184Z",
$ command grep -c useMove node_modules/.pnpm/react-aria-components@1.21.1*/node_modules/react-aria-components/dist/types/exports/index.d.ts
0
```

## Decision

1. **`react-aria` is a direct dependency of the web application, pinned at `3.52.1`**, the version
   React Aria Components 1.21.1 pins exactly, published past `minimumReleaseAge`. The tree gains no
   package: the application links the copy the other two already share.

   **Fails if** the two versions drift apart, `react-aria-components` pinning one release and this
   manifest another: the tree then holds two copies of the interaction layer, each with its own
   global state for focus, pointer and text selection. The two are raised together.

2. **Only `useMove` is taken from it, and only its `onPointerDown`, for a touch pointer.** Its
   `onKeyDown` swallows the arrows the dialog steps on, and its `onPointerDown` prevents the default
   of a mouse press, which would stop a mouse from dragging the image out of the page.

## Consequences

- **A plain `pnpm install` wrote the new importer without its peers**, `version: 3.52.1` rather than
  `3.52.1(react-dom@19.3.0(react@19.3.0))(react@19.3.0)`, and linked a second, peerless copy
  (pnpm 12.3.4, the lockfile reported up to date). `pnpm install --fix-lockfile` resolved it to the
  shared copy; `node_modules/.pnpm` then holds one `react-aria@3.52.1` directory.
- **`useMove` is tested in jsdom and the swipe's other half only on a phone**: jsdom dispatches the
  pointer events but applies neither `touch-action` nor the browser's own `pointercancel`.
