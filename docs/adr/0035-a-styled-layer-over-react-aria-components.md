# 0035. A styled layer over React Aria Components

Status: Accepted
Date: 2026-09-19
Specification: `docs/specs/2026-09-19-a-styled-layer-over-react-aria.md`, sections 3.1 and 3.2.
Written in block 10.
Extends: `docs/adr/0027-the-web-application-stack.md` decision 2, which names React Aria Components
as the stack's load bearing primitive set and says nothing about what styles it.

## Context

Every colour, radius and surface in the web application was a Tailwind literal at its call site:
twelve such lines in five files on 2026-09-19, three of them the same literal written twice. The
operator asked for an existing system rather than a house design.

The grid rules out a second primitive set. It is `Virtualizer` with `WaterfallLayout`, which only
React Aria Components has, so anything that arrives beside it leaves two focus and overlay systems in
one application. That is what shadcn/ui on Radix would have cost, and a copy-paste kit would have
moved the styling into files this repository then maintains rather than replacing it.

## Decision

1. **`@heroui/react`, pinned at `3.2.6`.** HeroUI v3 declares `react-aria-components@^1.21.1` as a
   peer, not a dependency, and the workspace has exactly `1.21.1`: one copy of the primitive set and
   one set of its contexts. It is the primitives already in the stack, dressed. The version is
   pinned rather than ranged and it was published `2026-09-17T19:13:54.511Z`, past
   `minimumReleaseAge`, so it installs with no `minimumReleaseAgeExclude` entry.

   **Fails if** HeroUI widens that peer range past what the grid needs, which would put two copies of
   React Aria Components in the tree.

2. **The accent stays at HeroUI's default.** On a wall of images the accent shows on buttons and the
   focus ring alone, and nobody has a brand colour to give. Moving it later is one declaration in
   `styles.css`.

3. **`data-theme` is the agreement, and no adapter is needed for it.** HeroUI declares its variables
   under `.dark, [data-theme="dark"]` and `theme.ts` already writes a resolved `light` or `dark` into
   `document.documentElement.dataset.theme`. No file under the package's `dist/components/` carries a
   `dark:` utility, so HeroUI's own components switch on those variables alone.

4. **The house `@custom-variant dark` is declared last and governs this application's utilities.**
   `@heroui/styles` declares the variant too, with a `prefers-color-scheme: dark` fallback branch for
   elements inside no dark marker. Read against `theme.ts` that branch is a defect: a user choosing
   `light` on a machine that prefers dark gets `data-theme="light"`, which the fallback does not
   exclude, so every `dark:` utility fires anyway. `styles.css` therefore imports `tailwindcss`, then
   `@heroui/styles`, then declares the house variant. A selective import omitting `./variants` was
   rejected: six lines where one suffices, and it breaks the day HeroUI moves the declaration.

## Consequences

- **The application carries no `dark:` utility of its own**, both having left with the surfaces that
  held them. The ordering protects the next one anybody writes, and section 4's criterion 1 holds the
  count at zero.
- **`@radix-ui/react-avatar` is in the tree**, a runtime dependency of `@heroui/react` 3.2.6. It
  renders an image with a fallback, manages no focus and no layer, and no screen uses an avatar.
  `@heroui/styles` brings `tw-animate-css` and `tailwind-variants` the same way.
- **Both stylesheets import `tailwindcss`** and the emitted bundle carries the preflight once,
  measured on the block's build: one `tab-size:4` and one `prefers-color-scheme`, the latter being
  lightningcss's own `light-dark()` polyfill and not HeroUI's fallback branch.
- **A HeroUI component is a compound one**, so a swap that keeps the old shape compiles and renders
  nothing useful. `Modal` is `Modal.Backdrop` plus `Modal.Container` plus `Modal.Dialog`, and the pin
  dialog opens from a tile rather than a trigger, so its root is `Modal.Backdrop` alone: the `Modal`
  root is a `DialogTrigger` and warns when it has no pressable child.
- **The theme switch is no longer a native `<select>`**, so its journey reads the trigger's
  accessible name and clicks an option rather than calling `selectOptions`.
