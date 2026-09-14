# Handoff: the grid keeps every page it scrolls

Date: 2026-09-14
Branch: `docs/the-grid-keeps-every-page`, one block
Tier: Direct, written inline by the lead. No specification, and **the holistic review did not run**,
which Direct skips by the tier table rather than by a waiver the operator gave.

## Current state

`dagger call gate` green before the push. The diff is 10 counted lines against 600, with 1 production
line under `clients/` against 400 and none under `api/`; the ADR and this handoff sit outside the count
as dated documents.

No behaviour changed. The block closes a backlog entry by deciding it is a limit, and touches two
comments so they point at the decision.

## What was built

- **`docs/adr/0033-the-grid-keeps-every-page-it-scrolls.md`.** The grid keeps every page, the waterfall
  is kept over the cap, and `useInfiniteQuery` is not the piece at fault. It carries the market survey
  and the memory estimate that close the question.
- **`docs/backlog.md`.** The `P1` entry is deleted and a two-line pointer enters Known limits.
- **Two comments** repointed from "question Y" and section 4.7 to the ADR, in `clients/apps/webapp/src/pins.ts`
  and in the third test of `browse-the-grid-and-load-a-second-page.journey.test.tsx`.

## Pitfalls

- **No component on the market carries both a waterfall and a capped bidirectional query**, surveyed
  2026-09-14. `@virtuoso.dev/masonry` publishes five props and no `endReached`; `masonic`'s
  `useInfiniteLoader` fires at the end alone; `startReached` and `firstItemIndex` exist only on
  Virtuoso's single-column and uniform components. Do not reopen this by naming a library without
  checking those two properties on it.
- **The cause is structural, not a gap in react-aria.** A waterfall assigns each item to the shortest
  column so far, so dropping a page re-columns everything after it and there is no index-to-position
  map for a prepend to anchor on.
- **The API's backward half is implemented and unused.** `previousCursor` is on every page and
  `CursorDirection.BACKWARD` works; nothing in the web application calls them.

## What is not validated

- **The memory figure is an estimate, not a capture.** 594 bytes a pin, so 11.3 MiB at 500 pages, from
  a representative `PinOutputDto` and not from a deployment. The ADR carries the probe.
- **Nothing profiles the retained pages** on a real catalogue, which is what decision 1's failure mode
  would need.
- **No holistic review**, Direct skipping it.

## Next step

Nothing this block opens. `P1` now holds nothing about the web application: the manual theme switch
the handoff of 2026-09-14 named as the last one shipped with `ThemeSwitch` and the journey
`choosing a theme against the system`, and that handoff is dated, not current. What is left is the
deployment (`Caddyfile` and a webapp image), which is what turns the application into something a user
can open, and the Features band, which lists what the API serves and the grid does not reach.
