# 0033. The grid keeps every page it scrolls

Status: Accepted
Date: 2026-09-14
Specification: none. Tier Direct, the operator deciding in Discuss and this document recording it.
Amends: nothing. `docs/specs/2026-09-10-web-application.md` section 4.7 left the cap open and pointed
at the backlog; the entry closes here as an accepted limit.
Related: `docs/adr/0027-the-web-application-stack.md`, which chose react-aria.

## Context

Block 6 shipped the grid with no cap on `useInfiniteQuery`, and section 4.7 records why: at a cap of
five over seven pages the query dropped the pages at the far end and nothing reloaded them, so a user
scrolling back up found a grid two pages shorter than the one they scrolled down. The upward half
needs a trigger at the top of the scroll container, and react-aria's `GridListLoadMoreItem` is
documented "used at the end of a GridList" (react-aria.adobe.com/GridList).

The entry that survived asked for the cap without saying what would carry it. Two things settle it.

### No component carries both the waterfall and the cap

Surveyed 2026-09-14, at the latest version the registry serves:

| Component                                   | Waterfall | Trigger at the top    | Anchors a prepend      |
|---------------------------------------------|-----------|-----------------------|------------------------|
| react-aria `WaterfallLayout` 1.21.1 (in use) | yes       | no                    | no                     |
| react-aria `GridLayout` 1.21.1               | no        | no                    | no                     |
| `@virtuoso.dev/masonry` 1.4.3                | yes       | no                    | no                     |
| `masonic` 4.1.0                              | yes       | no                    | no                     |
| `react-virtuoso` 4.18.13                     | no        | yes, `startReached`   | yes, `firstItemIndex`  |

`VirtuosoMasonryProps` publishes `columnCount`, `context`, `data`, `initialItemCount` and
`ItemContent` on top of `ScrollerProps`, and nothing else (virtuoso.dev/masonry/api-reference).
`startReached` and `firstItemIndex` exist on `Virtuoso`, `VirtuosoGrid` and `TableVirtuoso`, every one
of them a single column or a uniform grid. `masonic`'s `useInfiniteLoader` fires "when the last
rendered index surpasses the total number of items" (`masonic@4.1.0/types/use-infinite-loader.d.ts`),
which is the end alone.

The two properties are disjoint across the whole offer, and the reason is not an oversight: a waterfall
assigns each item to the shortest column so far, so dropping a page re-columns everything after it.
An anchored prepend needs an index-to-position map that a waterfall does not have.

### What the cap would buy is smaller than what it costs

A pin on the wire, the fields of `PinOutputDto` filled as a real one is:

```python
import json
pin = {
    "id": "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11",
    "authorId": "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11",
    "sourceContextUrl": "https://www.pixiv.net/en/artworks/123456789",
    "sourceMediaUrl": "https://i.pximg.net/img-original/img/2026/01/02/03/04/05/123456789_p0.jpg",
    "description": "A harbour at dusk, with the lamps just lit",
    "tags": [{"name": "landscape"}, {"name": "dusk"}, {"name": "harbour"}],
    "boards": [{"id": "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f12", "name": "Inspiration"}],
    "softDeletedAt": None,
    "image": {"status": "READY", "url": "/api/v1/pins/0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11/image",
              "width": 1920, "height": 1080},
}
n = len(json.dumps(pin, separators=(",", ":")))
print(f"{n} bytes a pin")
for pages in (25, 100, 500):
    print(f"{pages} pages of 40: {pages * 40 * n / 1024 / 1024:.1f} MiB")
```

```
594 bytes a pin
25 pages of 40: 0.6 MiB
100 pages of 40: 2.3 MiB
500 pages of 40: 11.3 MiB
```

An estimate and not a capture: no deployment holds a catalogue to read it off. It is the right order of
magnitude, and the order is what decides here. Five hundred pages is twenty thousand pins scrolled in
one sitting for eleven mebibytes of JSON. The DOM, which is the expensive half, is already bounded:
the virtualiser mounts the visible tiles alone.

## Decision

1. **The grid keeps every page it scrolls.** `usePins` declares no `maxPages` and no
   `getPreviousPageParam`. What bounds the screen is the virtualiser, not the query.

   **Fails if** a profile on a real catalogue shows the retained pages, and not the images, as what
   costs the session its memory.

2. **The waterfall is what the grid is for, and the cap does not outbid it.** Trading
   `WaterfallLayout` for a uniform grid would make the cap writable, by turning index into position.
   It is refused: the layout is what a pin board looks like, and eleven mebibytes after twenty thousand
   pins is not a reason to change what the user sees on every visit.

3. **`useInfiniteQuery` is not the thing to replace.** It carries `maxPages` and both directions
   already (tanstack/query, Infinite Queries). The half that is missing is the trigger, and that
   belongs to whatever draws the grid.

## Consequences

- **The backlog entry closes as a limit, not as work.** It moves to Known limits, pointing here.
- **A cap becomes writable the day the layout stops being a waterfall**, which decision 2 refuses for
  now and not forever. Search, boards and the recycle bin all draw the same grid; if one of them wants
  a uniform layout, the cap comes with it for free.
- **Nothing measures the retained pages.** Decision 1's failure mode needs a profile on a catalogue
  nobody has yet, so this decision rests on an estimate until one exists.
- **The API's backward cursor stays unused by the web application.** `previousCursor` is serialised on
  every page and `CursorDirection.BACKWARD` is implemented; export and the extension may still want
  them.
