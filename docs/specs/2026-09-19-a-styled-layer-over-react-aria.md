# The web application takes a styled layer over React Aria Components

Date: 2026-09-19
Status: Written 2026-09-19 on four operator answers of the same day: HeroUI over the two alternatives,
the perimeter set at "habillage plus identity" rather than a swap alone or a new layout, the default
accent kept, and a split by nature rather than by screen. One specification review closed, its fourteen
findings recorded in this document; the one it rated CRITICAL overturned section 3.2, which claimed
HeroUI's theming and this repository's "agree with no adapter" when the two declare a colliding
`@custom-variant dark`.
Branches: block 10 `feat/a-styled-layer-over-react-aria`, block 20 `feat/the-application-gets-a-hierarchy`.
ADRs: one, `docs/adr/0035-a-styled-layer-over-react-aria-components.md`, written in block 10. It extends
`docs/adr/0027-the-web-application-stack.md` decision 2, which names React Aria Components as the stack's
load bearing primitive set and says nothing about what styles it. It records four decisions: the library
and its version, the accent left at its default, the `data-theme` agreement, and which of the two
`@custom-variant dark` declarations governs the application's own utilities.

## 1. Goal

Make the web application pleasant to look at without changing what it does or how it is operated. The
interface works and passes its journeys; what it lacks is a palette, a surface vocabulary and a
typographic hierarchy. Today every one of those is written by hand, one Tailwind class at a time, at each
call site.

The operator's constraint is that the answer be an existing system rather than a house design.

## 2. What exists today

```
$ wc -l clients/apps/webapp/src/routes/*.tsx clients/apps/webapp/src/components/*.tsx clients/apps/webapp/src/styles.css
  109 clients/apps/webapp/src/routes/CreatePin.tsx
   87 clients/apps/webapp/src/routes/Credentials.tsx
  188 clients/apps/webapp/src/routes/Home.tsx
   84 clients/apps/webapp/src/components/TaskCentre.tsx
   30 clients/apps/webapp/src/components/ThemeSwitch.tsx
   15 clients/apps/webapp/src/styles.css
  513 total
$ grep -n "react-aria-components\|tailwindcss\|\"react\"" clients/apps/webapp/package.json
17:    "react": "19.3.0",
18:    "react-aria-components": "1.21.1",
22:    "@tailwindcss/vite": "4.3.3",
32:    "tailwindcss": "4.3.3",
```

`styles.css` holds fifteen lines: the Tailwind import, the dark variant and three `color-scheme`
declarations. It declares no colour of its own. Every colour in the application is therefore a literal at
its call site:

```
$ grep -rn "bg-white\|bg-current\|border-current\|bg-black" clients/apps/webapp/src
clients/apps/webapp/src/routes/Credentials.tsx:15:const FIELD = "rounded border border-current/30 px-2 py-1"
clients/apps/webapp/src/routes/Credentials.tsx:54:        <button type="submit" disabled={session.isPending} className="rounded bg-current/10 py-1">
clients/apps/webapp/src/routes/Home.tsx:70:        <p style={ratio} className="grid place-content-center rounded bg-current/5 p-2 text-center">
clients/apps/webapp/src/routes/Home.tsx:99:      <Button slot="close" className="self-end rounded bg-current/10 px-2 py-1">
clients/apps/webapp/src/routes/Home.tsx:148:        className="fixed inset-0 grid place-items-center bg-black/40 p-4"
clients/apps/webapp/src/routes/Home.tsx:150:        <Modal className="max-h-full w-full max-w-2xl overflow-auto rounded bg-white p-4 dark:bg-neutral-900">
clients/apps/webapp/src/routes/CreatePin.tsx:9:const FIELD = "rounded border border-current/30 px-2 py-1"
clients/apps/webapp/src/routes/CreatePin.tsx:102:          className="rounded bg-current/10 py-1"
clients/apps/webapp/src/components/ThemeSwitch.tsx:20:        className="rounded bg-current/10 px-2 py-1"
clients/apps/webapp/src/components/TaskCentre.tsx:6:const ACTION = "rounded bg-current/10 px-2 py-1 text-sm"
clients/apps/webapp/src/components/TaskCentre.tsx:16:    <li className="flex flex-col gap-1 border-b border-current/10 py-2 last:border-0">
clients/apps/webapp/src/components/TaskCentre.tsx:69:      <Popover className="max-w-sm rounded border border-current/20 bg-white p-3 dark:bg-neutral-900">
```

Twelve lines in five files, and three of them are the same literal written twice. The floating surface
`bg-white ... dark:bg-neutral-900` is in `Home.tsx` for the modal and `TaskCentre.tsx` for the popover.
The interactive surface `rounded bg-current/10` is in five files, once under a name (`ACTION`) and four
times inline. `const FIELD = "rounded border border-current/30 px-2 py-1"` is byte-identical at
`Credentials.tsx:15` and `CreatePin.tsx:9`.

**Those two `dark:` utilities are the only ones the application has.** The same grep for `dark:` across
`src` returns them and nothing else outside `src/paraglide/` and a message key named `theme_dark`.

**Raw HTML rather than React Aria Components**, recounted: the theme `<select>` (`ThemeSwitch.tsx:17`),
the sign out `<button>` (`Home.tsx:178`), the credentials form's three inputs and its submit button
(`Credentials.tsx:37,41,50,54`), and the pin creation form's four inputs and its submit button
(`CreatePin.tsx:75,79,84,88,99`). Eleven controls, carrying no styling beyond a border and a radius.

## 3. The change

### 3.1 The library

HeroUI v3 (`@heroui/react`), read from the registry on 2026-09-19:

```
$ curl -s https://registry.npmjs.org/@heroui/react | python3 -c "import sys,json;d=json.load(sys.stdin);v=d['dist-tags']['latest'];print(v,d['time'][v])"
3.2.6 2026-09-17T19:13:54.511Z
$ curl -s https://registry.npmjs.org/@heroui/react/3.2.6 | python3 -c "import sys,json;print(json.load(sys.stdin)['peerDependencies'])"
{'react': '>=19.0.0', 'react-dom': '>=19.0.0', 'react-aria': '^3.52.1', 'tailwindcss': '>=4.0.0', '@react-aria/ssr': '^3.10.1', '@react-aria/utils': '^3.34.1', 'react-aria-components': '^1.21.1', '@internationalized/date': '^3.12.4'}
```

**React Aria Components is a peer, not a dependency.** The range is `^1.21.1` and the workspace has
exactly `1.21.1` (`clients/apps/webapp/package.json:18`), so the application keeps one copy of the
primitive set and one set of its contexts. This is the property the two rejected candidates do not have,
and the reason the choice is not a matter of taste: HeroUI v3 is the primitives already in the stack,
dressed.

**Rejected, with what each would have cost:**

- **A copy-paste kit** (`react-aria-components-tailwind-starter`). No runtime dependency, and no
  replacement either: it moves the styling from the call sites into files the repository then maintains.
  The lot's goal is to stop writing this by hand, not to write it once more in a tidier place.
- **shadcn/ui**, on Radix. The grid is `Virtualizer` with `WaterfallLayout`, which Radix has no equivalent
  of, so React Aria Components stays whatever else arrives. The result is two focus and overlay systems in
  one application, each unaware of the other's open dialog.

**One piece of Radix arrives anyway:**

```
$ curl -s https://registry.npmjs.org/@heroui/react/3.2.6 | python3 -c "import sys,json;print(json.load(sys.stdin)['dependencies'])"
{'input-otp': '1.5.0', '@heroui/styles': '3.2.6', 'tailwind-variants': '3.3.1', '@react-types/color': '3.2.0', '@react-types/shared': '3.36.1', '@react-stately/utils': '3.12.1', '@radix-ui/react-avatar': '1.2.6'}
```

`@radix-ui/react-avatar` is a runtime dependency of `@heroui/react` 3.2.6. It is a leaf that renders an
image with a fallback, it manages no focus and no layer, and no screen here uses an avatar today. Named so
that a reader of the lockfile is not surprised by it. `@heroui/styles@3.2.6` brings `tw-animate-css@1.4.0`
and `tailwind-variants@3.3.1` the same way.

### 3.2 The theme, and the variant collision

The theme is a set of CSS variables that `@heroui/styles` declares and `styles.css` may override. The
accent stays at its default, per the operator's answer of 2026-09-19: on a wall of images the accent
shows on buttons and the focus ring alone, and moving it later is one declaration.

**The variables agree with this repository's convention.** HeroUI declares them under
`.dark, [data-theme="dark"]` (`@heroui/styles@3.2.6`, `dist/themes/default/variables.css:191-194`), and
`theme.ts:17-22` already writes a *resolved* `light` or `dark` into `document.documentElement.dataset.theme`
so that "the stylesheet never reads the machine itself". No adapter is needed for the variables, and
HeroUI's own component CSS is switched by them alone: unpacked, no file under `dist/components/` contains
a `dark:` utility, and `dist/variants/index.css` is the only file in the package mentioning
`prefers-color-scheme`.

**The variant does not agree, and this is what the specification review caught.** `@heroui/styles` declares
`@custom-variant dark` itself, with a branch the house one has not got:

```
$ curl -sL https://registry.npmjs.org/@heroui/styles/-/styles-3.2.6.tgz | tar xz && sed -n '84,106p' package/dist/variants/index.css
@custom-variant dark {
  /* Explicit: .dark class or data-theme="dark" on element or ancestor */
  &:is(.dark, .dark *, [data-theme="dark"], [data-theme="dark"] *) {
    ...
  }

  /* Fallback: system prefers dark color scheme */
  @media (prefers-color-scheme: dark) {
    &:not(:is(.dark, .dark *, [data-theme="dark"], [data-theme="dark"] *)) & {
      ...
    }
  }
}
```

Read against `theme.ts`, that fallback is a defect and not a convenience. A user who chooses `light` on a
machine that prefers dark gets `data-theme="light"` on the root, which is not inside any dark marker, so
the media branch matches and every `dark:` utility fires anyway. That is the exact failure the journey
`choosing a theme against the system` is named after, and the journey asserts `dataset.theme` rather than
a computed style, so it cannot fail on it.

**Decision: the house variant is declared last and governs the application's utilities.** `styles.css`
becomes, in this order: `@import "tailwindcss"`, `@import "@heroui/styles"`, then the existing
`@custom-variant dark` and the three `color-scheme` declarations. HeroUI's components are unaffected,
switching on variables rather than on the variant. The selective import that omits `./variants` was
rejected: it spells six import lines where one suffices, and it breaks the day HeroUI declares the variant
in another file.

**The durable guard is the absence of the subject.** Block 10 removes both of the application's `dark:`
utilities with the surfaces that carry them, and block 20 writes none, so a criterion in section 5 holds
the application at zero. The ordering is what protects the next one anybody writes, and ADR 0035 records
why it is the order it is.

### 3.3 What block 10 replaces, and what it does not

**The primitives do not map one to one.** HeroUI v3 ships no `GridList`, `GridListItem`,
`GridListLoadMoreItem`, `Collection`, `Virtualizer`, `WaterfallLayout` or `Size`, which are seven of the
eleven React Aria Components imports in `Home.tsx:3-15`; its own listbox documentation tells the reader to
import `Virtualizer` from `react-aria-components`. **The grid is untouched by this lot.**

| File | Becomes HeroUI | Stays as it is |
|------|----------------|----------------|
| `Home.tsx` | `Button` (the close button and the raw sign out `<button>`); `ModalOverlay`/`Modal`/`Dialog` become `Modal` with `Modal.Backdrop`, `Modal.Container` and `Modal.Dialog` | The whole grid: `Virtualizer`, `WaterfallLayout`, `Size`, `GridList`, `GridListItem`, `GridListLoadMoreItem`, `Collection`, and the `Tile` |
| `TaskCentre.tsx` | `Button`; `DialogTrigger`/`Popover`/`Dialog` become `Popover` with `Popover.Trigger`, `Popover.Content` and `Popover.Dialog` | The file input behind its `sr-only` label |
| `ThemeSwitch.tsx` | The native `<select>` becomes `Select` with `Select.Trigger`, `Select.Popover` and a `ListBox` | The house `useTheme` and its three preferences |
| `Credentials.tsx` | The three inputs become `TextField`/`Label`/`Input`, the checkbox `Checkbox`, the submit `Button type="submit"` | The `onSubmit` handler and its `FormData` |
| `CreatePin.tsx` | The three text and URL inputs and the submit button, as above | The file input, and the `onSubmit` handler |

**Both `bg-white ... dark:bg-neutral-900` sites are inside that table**, so the floating surfaces arrive
painted from `--overlay` at block 10 and section 3.4's token work is what is left after it.

**One journey changes, and it is three assertions rather than one:**

```
$ grep -n "toHaveValue\|selectOptions\|findByLabelText" clients/apps/webapp/src/journeys/choosing-a-theme-against-the-system.journey.test.tsx
18:    const switcher = await screen.findByLabelText("Theme")
19:    expect(switcher).toHaveValue("system")
22:    await user.selectOptions(switcher, "Dark")
35:    expect(await screen.findByLabelText("Theme")).toHaveValue("dark")
```

`toHaveValue` and `selectOptions` both read a native `<select>`. HeroUI's `Select` is a trigger plus a
listbox in a popover (heroui.com/en/docs/react/components/select, read 2026-09-19), which has an
accessible name and no value attribute: the two `toHaveValue` become assertions on the trigger's
accessible name, and `selectOptions` becomes a click on the trigger and then on the option. **The journey
keeps its name and its meaning**: a preference chosen against the system's is honoured and survives a
reload.

**Three behaviours must not silently change**, because the forms depend on them and no percentage covers
the view:

- `Credentials.tsx:26` reads its fields with `new FormData(event.currentTarget)` and the names `name`,
  `password` and `rememberMe`. HeroUI's `Input` accepts the standard `<input>` attributes and `Checkbox`
  takes `name`, so the handler is untouched. The sign in, sign up and session journeys are the check.
- `CreatePin.tsx:69` is the same shape, with `sourceContextUrl`, `description`, `sourceMediaUrl` and
  `file`, and it carries one thing the credentials form does not: `required={file === null}` at `:84`,
  whose enforcement is the browser's native validation. **No journey asserts that submitting with neither
  an address nor a file is refused**, so block 10 adds that assertion to
  `create-a-pin-from-a-URL-through-to-the-tile-appearing` rather than trusting the swap.
- `TaskCentre.tsx:32` hides a file input behind a styled `<label>` with `sr-only`. It stays as it is: a
  file picker is not a control HeroUI replaces, and `getByLabelText("Image file")` is what two journeys
  use.

### 3.4 The identity (block 20)

What the swap does not fix, and what the operator's answer of 2026-09-19 puts in scope:

- **The remaining literals come from tokens.** After block 10 the floating surfaces are gone with their
  components; what is left is the interactive and separator literals of section 2's grep that belong to no
  replaced control: `bg-current/5` on the tile placeholder (`Home.tsx:70`), `border-current/10` on the task
  row (`TaskCentre.tsx:16`), and `bg-black/40` on the overlay if HeroUI's backdrop is overridden. They
  become `--surface`, `--separator` and `--backdrop`.
- **The header gains a hierarchy.** Today the heading, three controls and a link sit in one flex row at one
  weight. The sign out control becomes secondary to the create action, which is the screen's primary verb.
- **The grid gains its two missing states.** An account with no pins shows a header and an empty
  rectangle; a first load shows nothing at all until the first page arrives, and
  `browse-the-grid-and-load-a-second-page.journey.test.tsx` asserts only on images that have arrived, so
  no journey observes either. The empty one is a user path nobody has walked and arrives as a named
  journey; the loading one is a transient the same journey observes on its way, by asserting the status
  element before the first page resolves.

## 4. Acceptance criteria

The view is outside the coverage bound (ADR 0027 decision 3), so `dagger call gate` passing says nothing
about whether either block did its work. These are what can fail. Each is run from the repository root
unless stated.

| # | Block | Command | What proves the block wrong |
|---|-------|---------|------------------------------|
| 1 | 10 | `grep -rn "dark:" clients/apps/webapp/src --exclude-dir=paraglide` | Any line outside the `theme_dark` message key. Today it returns `Home.tsx:150` and `TaskCentre.tsx:69`. |
| 2 | 10 | `grep -n "@import\|@custom-variant" clients/apps/webapp/src/styles.css` | `@custom-variant dark` appearing before `@import "@heroui/styles"`. |
| 3 | 10 | `cd clients && pnpm --filter @pinry-reborn/webapp run build` then `grep -c "prefers-color-scheme" apps/webapp/dist/assets/*.css` | Any occurrence. Recorded in the handoff whatever it is: it is the number that says HeroUI's fallback branch reached the bundle. |
| 4 | 10 | `cd clients && pnpm --recursive run test` | The theme journey failing, or any of the four journeys that type into a form (`sign-in`, `sign-up`, and the two pin creations). The new assertion in criterion 5 is part of it. |
| 5 | 10 | The new case in `create-a-pin-from-a-URL-through-to-the-tile-appearing.journey.test.tsx` | A submit with neither an address nor a file reaching `useCreatePin`. |
| 6 | 20 | `grep -rn "bg-white\|bg-current\|border-current\|bg-black" clients/apps/webapp/src` | Any line. Today it returns the twelve of section 2. |
| 7 | 10 | `git diff --stat main -- clients/pnpm-workspace.yaml` | Any change. An added `minimumReleaseAgeExclude` line is the release-age protection waived, which section 8 forbids. |
| 8 | 20 | `grep -c '^  "' clients/apps/webapp/src/lib/journeys.ts` | Anything but thirteen. `REQUIRED_JOURNEYS` holds twelve names today, and ADR 0027 decision 5's test enforces the match with `src/journeys/` in both directions. |

Criterion 1 is what makes the variant collision moot rather than merely ordered around, and criterion 2 is
what protects the next `dark:` utility anybody writes. Criterion 3 discriminates nothing on its own, since
Tailwind emits a variant only where it is used and criterion 1 holds the application at zero uses: it is
recorded because a non-zero count would mean the emitted CSS carries a branch no source line asked for.

## 5. Blocks

| Block | Branch | What it does | Files and lines |
|-------|--------|--------------|-----------------|
| 10 | `feat/a-styled-layer-over-react-aria` | `@heroui/react` in `package.json`, the import and variant order of section 3.2, the replacements of section 3.3's table, the theme journey's three assertions, the new assertion of criterion 5, and ADR 0035. No visual decision. | Production: the five `.tsx` of section 2 plus `styles.css`, 513 lines today. Outside production: `package.json` and two journey tests. `pnpm-workspace.yaml` does not change, per section 8. `pnpm-lock.yaml` is `linguist-generated` and outside both bounds. |
| 20 | `feat/the-application-gets-a-hierarchy` | Section 3.4: the remaining tokens, the header's hierarchy, the grid's empty and loading states, and the journey the empty state needs. | Production: `Home.tsx` (188 today), `TaskCentre.tsx` (84), `styles.css`, plus `src/lib/journeys.ts`. Outside production: one new journey test. Estimated well under both bounds. |

**Both bounds bind** (`agents/workflow.md`, What a block is): 400 production lines under `clients/` and 600
in the diff. Block 10 is the one at risk, and it is at risk on both: `styles.css` sits under
`clients/**/src/**` and is production by the prefix rule, so the production estimate is 513 lines touched
and not 498, and the manifests and the journey tests push the total above it.

**The budget is measured at block 10's first green run**, by `git diff --numstat` against `main`, not at
the block's end. **Its seam is section 3.3's table, split by row**: `ThemeSwitch.tsx`, `Credentials.tsx`
and `CreatePin.tsx`, which are the controls and the forms, then `Home.tsx` and `TaskCentre.tsx`, which are
the overlays. The dependency, the import order and the ADR go with the first half; criterion 1 is met only
at the end of the second, and the split's first half states so in its pull request.

## 6. Adjacent backlog items

`docs/backlog.md` holds one item adjacent to this lot's subject, and it stays open: **"What the API serves
and the web application does not reach yet"** (Features), which lists boards, tags, search, the recycle
bin, account management, import and export, and editing or deleting a pin. This lot changes how the
existing screens look and adds none, so closing that item is a lot of its own with its own contract
surface. Section 7's first bullet declines the same ground from the other direction.

No item under P1, P2, Known limits or Before beta touches the web application's appearance.

## 7. Out of scope

Each bullet names how a reader would notice it moved.

- **No new screen and no new navigation.** No sidebar, no search bar, no route. That was option C of the
  perimeter question and the operator declined it on 2026-09-19; it is a design question, not a library
  one, and it would move the journeys. Observable: `src/router.tsx` is unchanged, and
  `REQUIRED_JOURNEYS` gains exactly the one name of criterion 8.
- **No brand colour.** The accent stays at HeroUI's default until someone has one to give. Observable:
  `styles.css` declares no `--accent`.
- **No component of the browser extension.** It does not exist yet, and nothing here is put in
  `clients/packages/` for it to share. Observable: `clients/packages/` gains no directory and neither
  existing package's `package.json` gains a dependency.
- **No change to the coverage perimeter.** `src/lib/**` holds pure functions and this lot adds none; the
  view is checked by its journeys, as ADR 0027 decision 3 sets out. Observable:
  `clients/apps/webapp/vite.config.ts:26` still reads `include: ["src/lib/**/*.ts"]`.

## 8. Pitfalls

- **No `minimumReleaseAgeExclude` entry is written, and none may be.** pnpm holds a version back for
  `minimumReleaseAge` minutes after publication so that a compromised release is not installed in the
  hours that follow the attack (pnpm.io/settings/dependency-resolution, read 2026-09-19); the default is
  1440 minutes since pnpm 11 and this workspace declares no other value (`pnpm config get
  minimumReleaseAge` answers `undefined`). An exclusion is that protection waived for one version.
  `@heroui/react@3.2.6` was published `2026-09-17T19:13:54.511Z` and is already past the window, so the
  block pins it and writes nothing. **If a newer release appears before the block installs, it pins the
  aged one rather than excluding the fresh one.**
- **The peers are declared, not inherited.** pnpm installs a peer automatically, but the manifest is what
  ADR 0027 decision 1 relies on: an import nobody declared should not resolve. `react-aria-components` is
  already declared and stays; what block 10 adds to `package.json` is `@heroui/react` alone, since no file
  imports the other peers by name.
- **`@heroui/styles` imports `tailwindcss` itself**, at `dist/index.css:6`, and declares
  `@layer theme, base, components, utilities` at `:1`. The vendor quickstart still prescribes both imports
  in the application's own stylesheet, so `styles.css` keeps its own. Criterion 3's build is where the
  doubling is settled: check that Tailwind's preflight appears once in the emitted CSS and record it.
- **A HeroUI component is a compound one.** `Select` is `Select.Trigger` plus `Select.Popover` plus a
  `ListBox`, `Modal` is `Modal.Backdrop` plus `Modal.Container` plus `Modal.Dialog`. A swap that keeps the
  old shape compiles and renders nothing useful.
- **`Home.tsx`'s modal has no trigger.** It is opened by a click on a grid tile, so the root is driven by
  `isOpen` and `onOpenChange` rather than by a `Modal.Trigger`, and the `Button slot="close"` at `:99` is
  what closes it. The dialog's accessible name comes from `aria-label` today and nothing in section 3.3
  changes that; `open a pin` asserts on `findByRole("dialog")`.
