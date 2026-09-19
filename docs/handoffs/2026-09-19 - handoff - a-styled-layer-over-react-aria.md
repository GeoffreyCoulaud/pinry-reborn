# Handoff: a styled layer over React Aria Components

Date: 2026-09-19
Specification: `docs/specs/2026-09-19-a-styled-layer-over-react-aria.md`
ADR: `docs/adr/0035-a-styled-layer-over-react-aria-components.md`
Blocks: 10 `feat/a-styled-layer-over-react-aria`, 20 `feat/the-application-gets-a-hierarchy`,
closing `fix/the-holistic-findings`
Tier: Spec. Both reviews ran: the specification review returned 1 CRITICAL, 7 MAJOR and 6 MINOR,
the holistic 0 CRITICAL, 3 MAJOR and 6 MINOR.

## Current state

The web application's controls are HeroUI's, which are the React Aria Components already in the
stack with styling on top: `@heroui/react@3.2.6` declares `react-aria-components: ^1.21.1` as a peer
and the workspace pins exactly `1.21.1`, so one copy of the primitives and one set of their contexts.
The grid is untouched, HeroUI shipping no equivalent of `Virtualizer`, `WaterfallLayout` or
`GridList`.

No colour is written by hand any more. The twelve literals of the specification's section 2 are
gone, and nothing in `src` writes a `dark:` utility, which is what makes the collision in Pitfalls
harmless rather than merely ordered around. The accent stays at HeroUI's default, per the operator.

The home screen has a hierarchy and the three states it lacked: a first load, an account with
nothing in it, and a refusal. `an account with no pins` is the thirteenth journey.

## What the reviews caught, and the shape they share

Four defects in this lot were invisible to a green suite, and all four are the same shape: a
behaviour lives in the DOM or in a state machine, and nothing read what actually left the component.

- **`<Checkbox name="rememberMe">label</Checkbox>` rendered a bare `<div>`.** No input, no name, not
  clickable: every session opened as a non-persistent one. The only journey touching it asserted the
  *unchecked* path, which passes whether or not the field exists.
- **No field name in the creation form was verified.** That journey answered the POST without
  reading it, so a wrong `name` yields the string `"null"`, the request still goes out and the tile
  still appears.
- **The empty state told a refused account it was empty.** `tiles.length === 0` is reached on every
  state that is not pending, `isError` among them.
- **HeroUI's `@custom-variant dark` carries a `prefers-color-scheme` fallback** that fires against a
  theme chosen by hand. Caught by the specification review before a line was written, not after.

Each fix carries an assertion, and each assertion was proven non-vacuous by breaking what it guards.

## Pitfalls

- **A HeroUI control is a compound component.** Given the old single-element shape it compiles,
  renders no usable DOM and leaves the suite green. Read the rendered DOM, not the source.
  `clients/AGENTS.md` carries this now.
- **`styles.css`'s import order is load bearing**, the house `@custom-variant dark` coming after
  `@import "@heroui/styles"` on purpose (ADR 0035, decision 4).
- **The empty state has two truths.** It answers an account with nothing in it *and* an account whose
  pins are all still downloading, `placeableTiles` dropping every `PENDING` one. That is the
  operator's decision of 2026-09-19: those pins have nothing to show yet, and the task centre beside
  them says what is happening. A refusal is separate and has its own alert.
- **The nine `minimumReleaseAgeExclude` waivers are gone**, and the list may not grow again: pin a
  version that has aged out instead. `clients/pnpm-workspace.yaml` now declares `minimumReleaseAge`
  and `minimumReleaseAgeStrict`, which is what makes pnpm refuse rather than install and write itself
  a waiver. That change shipped outside this lot, in `b6995cf2`, born from the specification review.

## What the frozen documents got wrong

Both are append-only and froze when block 20 merged, so they are corrected here and not there.

- **Criterion 7 fails to the letter at the closing block**, which deletes twelve lines from
  `clients/pnpm-workspace.yaml`. The criterion refuses *any* change to that file; it was guarding
  block 10 against *adding* a waiver. Its intent held: no line was added to the list.
- **The specification's section 5 counted the budget on file sizes, not on the diff**, and was wrong
  by a factor of three: block 10 touched 167 production lines where 498 were feared. The seam it
  mandated was never needed. Measure `git diff --numstat` on a spike next time, not `wc -l`.

## What is not validated

- **Nothing checks that the application looks right.** No browser in the gate (ADR 0027), so every
  visual claim in this lot rests on a human having looked.
- **The header's DOM order has no guard.** The create link is first in source with `order-last`;
  a change putting it back would be silent. Left without a test rather than hanging an assertion
  foreign to its subject on an existing journey.
- **The bundle grew and the growth is accepted.** CSS went 10.55 kB to 433.44 kB (2.91 to 40.43
  gzipped), JS 561.52 to 670.22 kB, measured by building both ends of the range. The stylesheet ships
  calendar, slider, table and drawer CSS that nothing imports. The operator chose the global import
  over per-component ones on 2026-09-19: the maintenance of one import line per component is a
  silent failure when forgotten, and the component renders bare.
- **Blocks 10 and 20 carry no failing run in their commit bodies**, which `agents/engineering.md`
  asks for. The closing block's do.

## Next step

The lot closes nothing in `docs/backlog.md`. What the web application still does not reach is the
Features item "What the API serves and the web application does not reach yet": boards, tags, search,
the recycle bin, account management, import and export, and editing or deleting a pin. Each is a
screen, and the screens now have a vocabulary to be built from.
