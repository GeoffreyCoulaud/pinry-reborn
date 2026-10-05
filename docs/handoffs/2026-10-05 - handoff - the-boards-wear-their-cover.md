# Handoff: the boards wear their cover

Date: 2026-10-05
Tier: Spec. Specification `docs/specs/2026-10-05-the-boards-wear-their-cover.md`; no new ADR. Discussion with the
operator on 2026-10-05, specification review `.reviews/the-boards-wear-their-cover-spec.md`.
Lot `0.47.0`, one stack of 2 code blocks: 10 `feat/a-board-carries-its-cover` (PR #319), 20
`feat/the-boards-are-a-grid` (the pull request this file arrives in). Written in block 20 from the block reports
collapsed in those pull requests; the closing block corrects it after the holistic review.
(Corrected: the specification review ran, its 1 CRITICAL, 5 MAJOR and 9 MINOR closed; the CRITICAL found decision B's
first reason false and reopened it with the operator, who kept it. The closing block is `fix/the-boards-lot-closes`.)

## Current state

- **Every operation answering a board carries `coverUrl`**, required and nullable: create, list, get, update and
  restore from the recycle bin. The cover is derived: the board's newest pin by `(createdAt, id)` descending, among
  its pins not in the recycle bin that hold a media; none, and it is null. The contract is at `22.1.0` (10).
- **`BoardGetter.summarizeActiveBoardForUser`** replaces `countActivePinsForUserBoard`: one ownership check, then the
  count and the cover's pin id. The cover is one typed query rooted on the media table, filtered by a subquery on the
  board's memberships; the list costs one query more per board than before. The media's address comes from one
  function the pin mapper and the board mapper share (10).
- **`/boards` is a grid of square tiles** (20): a react-aria `GridList` with `layout="grid"`, so the arrow keys move
  between tiles in both directions, laid out by CSS grid at `repeat(auto-fill, minmax(10rem, 1fr))`. Each tile shows
  the cover cropped to a square (`aspect-square`, `object-cover`) through `RenditionImage`, at the rendition its width
  needs, with `animated=false` (`tileStillSource`); a board with no cover shows an empty square with an icon. The
  cover and the name are one link to the board; the count, rename and delete sit at the tile's foot.
  (Corrected: the cover loads lazily, the screen not being virtualised; the description wraps to two lines; a cover
  that fails to load is hidden from screen readers, the link keeping the board's name alone.)
- **`useColumnWidth`** moved from `PinGrid.tsx` to `src/columnWidth.ts`, the boards' tiles measuring their width the
  way the pins' do (20).

## Evidence

- Block 10: `dagger call gate` green at `4d93bb8`; budget 295 lines, 16 files against `main` (#319). `oasdiff
  changelog` (`tufin/oasdiff:v1.31.0`, `--level INFO`) against `main`: 5 `response-required-property-added` at INFO
  on the five operations, no `request-property-added`. `jq -r .info.version contract/openapi.json` prints `22.1.0`.
  Mutating the lookup (active-pin filter dropped, tie-break on `pinId` ascending) turned 2 of 32 tests red.
- Block 20: budget 208 lines, 9 files against `feat/a-board-carries-its-cover`; gate in its pull request. The new
  journey was red before the implementation on both counts: no `img` in the tile, and the right arrow landing on the
  row's link rather than on the next row.
- Read headless in Firefox 156.0.1 over WebDriver BiDi at 390x844 and 1280x800, light and dark, against a throwaway
  stub API serving a 1200x600 and a 500x1000 JPEG, a `422` for one cover and a board with none (20): every tile
  square at 165x165 at both widths, `?size=SMALL&animated=false` asked, both images cropped to the centre, the
  failed cover showing the "preview unavailable" icon, the empty board its placeholder. The reading found one
  defect, a tile with no description lifting its foot above its row's, fixed before the push (`mt-auto`).
- (Corrected: closing block, `dagger call gate` green at `45c53bf`; budget 67 lines, 6 files against
  `feat/the-boards-are-a-grid`. Read headless again the same way after its fixes: every cover `loading="lazy"`, the
  long description on 2 lines at both widths, the failed cover's stand-in `aria-hidden` with no `role`, the link's
  text the board's name alone.)

## Pitfalls

- **`oasdiff` is not installed on the workstation**: `docker run tufin/oasdiff:v1.31.0 changelog` reads the two
  documents mounted from a scratch directory (10).
- **`gh stack submit`'s output carries no line from the `pre-push` gate**: the push is read from the remote
  branch's sha (10).
- **`clients/packages/api-client/src/schema.d.ts` needs `pnpm --filter @pinry-reborn/api-client run generate`**
  after block 10's contract change, or the clients' typecheck does not see `coverUrl` (20).
- **Biome's `useComponentExportOnlyModules` refuses a hook exported beside components** (Fast Refresh): a shared hook
  goes to its own module, as `columnWidth.ts` did (20).
- **Under `layout="grid"` a `GridList` switches its rows to `keyboardNavigationBehavior="tab"`**
  (react-aria-components 1.21.1, `GridList.mjs`): the arrows move between rows and Tab enters one. Under the default
  stack layout the right arrow enters the row instead, which is what the journey tells apart (20).
- **Firefox's BiDi `browsingContext.setViewport` on the context `getTree` returns first** asks for
  `-remote-allow-system-access`; a tab made with `browsingContext.create` takes it. The theme is set by the profile's
  `layout.css.prefers-color-scheme.content-override` (0 dark, 1 light) (20).
- (Corrected: **the evidence guard refuses a redirection into a path held in a variable**, `> "$S/x.log"`, and
  `$TMPDIR` is unset in the agent's shell: a literal `/tmp/...` path passes, or a script written with the Write
  tool and run with `bash` (closing block).)

## Departures from the specification

- Block 10: the five integration cases all join `BoardsIntegrationTest`, restore included; `BoardGetterTest` gains a
  case refusing another user's board through the new method, decision D's single ownership check being its
  observable.
- Block 20: the tile picks its rendition by its measured width, as a pin's tile does, rather than always `SMALL`; the
  journey's `SMALL` is what a width under the small rendition gets. The cover is decorative (`alt=""`), the link it
  sits in being named by the board, so the journey reads the `<img>` element rather than an `img` role. The four
  screenshots are not attached by `gh`, which has no upload for a pull request's images.
- (Corrected: the closing block changes the recycle bin journey's failed thumbnail case, which read the stand-in as an
  `img` role; it now reads it by its title, the row being named by the description.)

Tier-1 fixes: the method counts in the `TooManyFunctions` suppression comments of `BoardRepositoryInterface` and
`BoardRepository` go from 12 to 13 (10). A comment of the "create a board and rename it" journey said the list had
no tile for want of a cover; removed (20).

## Tier-2 questions

None in any block.

## What is not validated

- The list's added per-board query is not timed (10; decision D accepts it).
- The tiles against the running API rather than a stub, and at a device pixel ratio above 1, where a 165 px tile
  asks for `MEDIUM` (20).
- (Corrected: a board whose newest pin holds a media over `MediaLimits` shows "preview unavailable" as its cover,
  even when an older pin could be drawn. Accepted by the operator on 2026-10-05, the specification's decision A.)
- (Corrected: whether the lazy covers load as a long list scrolls; the reading's five boards all fit the viewport.)

## The holistic review

Not run yet: it reads the top of this stack at the head of Wrap, and the closing block records its findings here.
(Corrected: it ran, not waived, `.reviews/the-boards-wear-their-cover-holistic.md`, 0 CRITICAL, 1 MAJOR, 5 MINOR,
each closed in the closing block `fix/the-boards-lot-closes`:

- MAJOR, no case showed the cover is the *newest* pin, `.asc()` passing all 32 tests: a case with two pins holding a
  media, the older carrying the greater fixed id; the `.asc()` mutation now fails it (commit message).
- MINOR, `useBoards`'s comment said there was nothing to load lazily: rewritten.
- MINOR, every cover requested at once on an unvirtualised screen: `RenditionImage` passes `loading="lazy"` through,
  and the cover asks for it.
- MINOR, a failed decorative cover's "preview unavailable" joined the board link's name: the stand-in of an `alt=""`
  rendition is `aria-hidden`, without `role` or label. The recycle bin's thumbnail, also `alt=""`, follows.
- MINOR, the description truncated to one line again: `line-clamp-2`.
- MINOR, a media over `MediaLimits` still wins the cover and shows "preview unavailable": an accepted limit, the
  operator's answer of 2026-10-05, recorded at the specification's decision A and below. Not in the backlog.)

## The backlog

"A board has no cover" is deleted (20). Nothing was filed.

## The lot's counts

Fix-backs, cascaded rebases, runs re-triggered and the operator's reading of the bodies: filled in by the closing
block.
(Corrected: fix-backs 0; cascaded rebases 0; runs they re-triggered 0; the operator's reading of the bodies: no
remark, the stack approved as "LGTM" on 2026-10-05.)

## Next step

Wrap: the holistic review over `git diff lot/0.46.0-a-medias-limits-hold-in-four-layers..origin/feat/the-boards-are-a-grid`,
then the closing block, the operator's review of the stack, and the tag `lot/0.47.0-the-boards-wear-their-cover`
once it merges. (Corrected: the review and the closing block are done; what remains is the operator's review of the
stack, its merge, and the tag.)
