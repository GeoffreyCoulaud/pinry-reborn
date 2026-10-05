# The duplicates are compared

Date: 2026-10-05
Status: Draft. One specification review ran, `.reviews/the-duplicates-are-compared-spec.md`, its 1 CRITICAL, 5 MAJOR
and 21 MINOR closed in this document. Frozen when the lot's last block merges.
Lot: `0.49.0`.
Branches: one stack: 10 `feat/a-media-records-its-tracks`, 20 `feat/the-api-describes-a-media` on 10,
30 `feat/the-api-resolves-duplicates` on 20, 40 `feat/the-dialog-compares-duplicates` on 30,
50 `feat/the-comparator-plays-both-versions` on 40, 60 `refactor/the-merge-and-reject-routes-go` on 50.
ADR: `docs/adr/0052-duplicates-are-resolved-in-one-call.md`, written in block 10, records decisions B and C and the
refusals of section 4. Decision D needs none: it is confined to one component and a library can replace it there.
Decision E needs none: three descriptive columns read at ingestion, as `frames` and `duration_millis` were.
The design is prototype v10, validated by the operator on 2026-10-05 (https://claude.ai/artifact/37v6yVtYkCvurge9RatRdy).

## 1. Goal

The pin dialog of lot `0.48.0` lists a pin's duplicates with a control per row and no way to see two versions side
by side. This lot replaces that list with a comparator: the versions of the group on one stage, a decision per
version, and the whole decision applied in one call.

## 2. What exists today

- `PinDuplicates.tsx` lists the pending candidates as a merge form (*Keep*, *Include*, *Merge (n)*) and folds the
  rejected ones beneath, each with *Not a duplicate* or *Restore*. A candidate opens in the same dialog
  (`open-a-duplicate.journey.test.tsx`).
- `useRejectDuplicate` sends `PUT /api/v1/pins/{pinId}/duplicates/{otherPinId}` per click; `useMergePins` sends
  `POST /api/v1/pins/merges` (`pins.ts`). After a merge the dialog shows the kept pin through `usePin`, and the
  catalogues, the boards and the duplicate lists are read again.
- `PinMerger.merge` applies decision H of `docs/specs/2026-10-05-the-pin-knows-its-duplicates.md`;
  `PinDuplicates.setRejected` stamps one shown pair's `rejected_at`; `PinDuplicateController` serves both and the
  `GET`, whose list is the stored pairs of the open pin and nothing transitive (`PinDuplicates.list`).
- A video's `mime_type` carries its codecs as RFC 6381 states them, `video/mp4; codecs="avc1.640028,mp4a.40.2"`, the
  video track's then the audio track's if any (`MediaIngestion.video`, `CodecsParameter`), and the contract publishes
  it as `PinMediaStateDto.mimeType`. Nothing stores an audio track's channels or bit rate.
- `media` stores `frames` and `duration_millis` since migration `1.30` (lot `0.46.0`), which wrote one frame and no
  duration into the rows already stored. `duration_millis` is a video's alone: an image's probe has no duration
  (`ImageProbe`), and `MediaLimits` counts an animated image's every frame because it has none.
- A pin carries `createdAt`; `PinOutputDto` does not. The contract is `22.3.0`.
- `media.url` with no `size` is the original's bytes, read by `PinMedia` for an image and a video; `animated=false`
  asks for the still rendition (`lib/tiles.ts`).
- `ImageDecoder` exists in Firefox 133 and Chrome 94, and in Safari only as a preview:
  `curl -s https://raw.githubusercontent.com/mdn/browser-compat-data/main/api/ImageDecoder.json`, read on 2026-10-05,
  `support.firefox.version_added` `133`, `chrome` `94`, `safari` `preview`.
- The archive export carries no pair (`ExportContent`).

## 3. Decisions

Each is the operator's answer of 2026-10-05 in Discuss, the letter of the question in brackets.

**A. The comparator is prototype v10** (the prototype's validation, and G).
- The pin dialog shows one row while the pin has a candidate: stacked thumbnails, then "N possible duplicates" and
  *Compare* while N candidates are pending, or "N rejected" and *Review* when every one is rejected.
- *Compare* replaces the dialog's content. The stage shows the version under review left of a vertical line and the
  kept one right of it, a grip moving the line. Wheel, double-click and *−* / *+* zoom from 100 % to 800 %, a drag
  pans once zoomed, and both sides share one zoom, kept across versions. The arrow keys switch versions; on the grip
  they move the line. With the kept version under review, the stage shows it alone.
- Under the stage, the decision for the version under review: *Keep*, *Merge*, *Not a duplicate*. Then a strip of
  every version, start-aligned: a crown on the kept one, the rejected ones faded, the current one outlined. The open
  pin cannot be rejected, and the kept one is changed only by keeping another.
- Beside the stage, a column of facts with no button: size, with "the largest" on the largest; duration for an
  animated media; sound for a video; weight and format; added; boards; tags.
- The comparator opens on the stored state. The kept version is the one with the most pixels among the open pin and
  the pending candidates, the oldest pin on a tie, so the open pin under *Review*. A pending candidate starts as
  merged, a rejected one as rejected.
- The footer: *Cancel*, back to the pin with nothing sent; and the submit, which sends every version's decision. It
  reads "Merge N pins into one" when N - 1 versions are merged, "Reject N duplicates" when no version is merged and N
  pending candidates are rejected, and is disabled otherwise. So a group with a pending candidate opens enabled, and
  one whose every candidate is rejected opens disabled.
- After the call the dialog shows the kept pin, as after lot `0.48.0`'s merge. A refused call says so and reads the
  list again, the comparator staying open on it.
- A candidate is no longer opened from the dialog.

**B. One call applies the decision** (B, C, D). `POST /api/v1/pins/{pinId}/duplicates/resolutions`, the open pin in
the path, ADR 0052. Sending per click could reorder or fail alone and leave the screen and the server apart.

**C. A rejection holds against the group** (C). A rejected pin's pair with every kept or merged pin it pairs with
gets `rejected_at`, before any pin is recycled, so it is not the kept pin's pending candidate once the open pin is
merged.

**D. Animated media play in step** (A, and the prototype).
- One play and pause, one progress bar over the longer duration, the shorter's span shaded on it. Outside its span a
  version holds its first or last frame; both restart at 0 when the longer ends. Sound is muted.
- An *Offset* slider under the bar moves the shorter version, from 0 to the difference of durations, kept while the
  comparator is open; it ends at the same x as the bar, the time labels in fixed-width boxes. Two durations within
  0.3 s of each other have no slider.
- A video is a `<video>` the clock seeks and plays; its duration is the contract's. An animated image is decoded
  frame by frame with `ImageDecoder` from its original bytes and drawn on a canvas; its duration is the sum of its
  frames' durations, a frame stating none or zero counting 100 ms as the browsers play it. The facts column shows
  that duration.
- Where `ImageDecoder` is missing, an animated image plays on its own as an `<img>` and shows no duration, and a pair
  holding one has no bar and no slider; a video beside it keeps its own controls. No library stands in (A).

**E. A media records its rates and its sound** (E, and the operator's addition of the video's rate on 2026-10-06).
`media` gains `video_bit_rate`, `audio_channels` and `audio_bit_rate`. A rate is measured, never read from a header:
the sum of the track's packet sizes over the media's duration, from the `ffprobe` run that already reads every packet
(`-count_packets`). WebM states no rate per track (`bit_rate=N/A` on a VP9 and Opus file made with `ffmpeg -f lavfi`,
2026-10-06, whose packets sum to 213 682 and 74 486 b/s), and a measure is the same on the source and on the
repackaged file. The channels are the first audio track's `channels`. The codecs and whether there is sound are read
from `mimeType`'s `codecs` parameter, which every video already carries. The column shows the format as the type's
short name, the video codec and its rate, "MP4 · H.264 · 4.2 Mb/s", then the sound, "AAC stereo, 128 kb/s" or "No
sound".

**F. No back-fill** (F). Media stored before this lot keep null rates and channels, and those stored before lot
`0.46.0` a null duration and one frame. The alpha runs on development data, which an export, a reset and an import
refill through `MediaIngestion`; that refill also drops every rejection, the export carrying no pair, and the worker
finds the pairs again as pending. The handoff says so.

## 4. The contract

| Operation | Change |
|---|---|
| `PinOutputDto.createdAt` | required instant |
| `PinMediaStateDto.durationMillis` | integer or null; a video's, null for an image and for a video stored before lot `0.46.0` |
| `PinMediaStateDto.videoBitRate`, `.audioChannels`, `.audioBitRate` | integer or null each; the rates in bits per second |
| `POST /api/v1/pins/{pinId}/duplicates/resolutions` | body `{decisions: {<pinId>: KEEP / MERGE / REJECT}}`, at most `PinIdsInputDto.MAX_IDENTIFIERS` entries; 200 the kept pin's `PinOutputDto` |
| `POST /api/v1/pins/merges`, `PUT /api/v1/pins/{pinId}/duplicates/{otherPinId}` | removed |

The resolution's refusals, every pin resolved before the first write and nothing written on a refusal (ADR 0039, as
ADR 0052 decision 2 narrows it):
- 400 `MALFORMED_BODY`: a key that is not a UUID, or a value outside the three.
- 400 `VALIDATION_ERROR`: not exactly one `KEEP`; the path's pin missing from `decisions`, or `REJECT`; no pin named
  but the path's; more entries than the bound.
- 403 `PIN_INSUFFICIENT_PERMISSIONS`, 404 `PIN_DOES_NOT_EXIST`, 409 `PIN_ALREADY_SOFT_DELETED`, for the path's pin.
- 404 `DUPLICATE_DOES_NOT_EXIST`: a named pin other than the path's is not among its shown duplicates.

Its effect, in one transaction: decision C's rejections; then the `MERGE` pins absorbed into the `KEEP` pin as
decision H of lot `0.48.0` says, filling from the absorbed pins oldest first (ADR 0052, decision 3). A candidate the
body does not name is left as it is.

Block 20 makes the contract `22.4.0`, block 30 `22.5.0`, block 60 `23.0.0`.

## 5. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/a-media-records-its-tracks` | Decision E's columns. |
| 20 | `feat/the-api-describes-a-media` | Section 4's fields. |
| 30 | `feat/the-api-resolves-duplicates` | Decisions B and C. |
| 40 | `feat/the-dialog-compares-duplicates` | Decision A. |
| 50 | `feat/the-comparator-plays-both-versions` | Decision D. |
| 60 | `refactor/the-merge-and-reject-routes-go` | Section 4's removals. |

Block 40 is the likeliest to pass a bound; it splits at a number between 40 and 50.

### Block 10

- Migration `1.32`: the three columns of decision E, nullable. `Media` gains them, `FfprobeReport` reads them,
  `MediaIngestion` stores them.
- An ingestion test per case: an H.264 MP4 with AAC stereo stores a video rate, 2 channels and an audio rate; a VP9
  WebM with Opus, whose tracks state no `bit_rate`, stores both rates within 1 % of its packets' sums; a VP9 WebM
  without sound stores null audio; a GIF stores three nulls.
- Carries this specification, ADR 0052 and ADR 0051's status line. Its consumer is block 20.

### Block 20

- The fields of section 4. `ResolvePinMediaState.statesFor` already reads each `Media` row and the pin is already
  loaded, so no read is added.
- `oasdiff changelog` against `main` lists the five properties, each for every operation that answers a pin, at
  info level, and no error; `info.version` is `22.4.0`.
- The web application's fixtures answer the new fields; their consumer is block 40.

### Block 30

- `DuplicateResolver` in `api-usecases`, which takes `PinMerger`'s merge, and the route.
- An integration test per refusal of section 4, each leaving every pin and pair as it was.
- A group of four, the open pin merged into a candidate, a third merged, a fourth rejected: the kept pin holds the
  union of boards and tags, the two absorbed pins share one `softDeletedAt`, and the rejected pin's pair with the
  kept pin is rejected; once the open pin is restored from the recycle bin, its `GET .../duplicates` lists the
  rejected pin with `rejected: true`.
- Two absorbed pins with descriptions fill the kept pin's blank one from the older.
- Rejections alone, the open pin kept: its pairs are rejected and no pin is recycled. A group of three naming one
  candidate leaves the other's pair pending.
- `info.version` is `22.5.0`. Its consumer is block 40.

### Block 40

- Decision A. On the stage a still image is its original; an animated image and a video show their still
  rendition (`animated=false`) until block 50 plays them.
- `useResolveDuplicates` replaces `useMergePins` and `useRejectDuplicate`; `PinDuplicates.tsx` gives way to the
  row and the comparator.
- Unit tests: the default kept version, a tie going to the oldest, a larger rejected candidate under *Review* left
  as rejected; the submit's label and state, a pending candidate opening enabled and an all-rejected group opening
  disabled; the zoom kept under the pointer and clamped to 100 % and 800 %; the codecs read from a `mimeType`; the format and sound lines.
- Journeys: "merge a group of duplicates" through the comparator, the open pin merged into a larger candidate;
  "reject a duplicate", then *Review* shows it faded and merges it; a refused submit shows its message and the list
  read again. "open a duplicate" is deleted.
- Read headless before the push, two themes by a phone's and a desktop's width.

### Block 50

- Decision D. The clock is a pure function of the shared time, the offset and the two durations, unit-tested: a
  version held on its first frame before its span and on its last after it, both back to 0 at the longer's end, no
  slider under a 0.3 s difference and one at 0.4 s. A frame of zero duration counts 100 ms in the sum.
- Without `ImageDecoder` (jsdom has none): two animated images show two `<img>` and no bar; a video and an animated
  image show the video's own controls, an `<img>`, and no bar.
- Read headless in Firefox before the push: two videos of different lengths with the slider moved, two animated
  GIFs, light and dark, phone and desktop.

### Block 60

- The two routes of section 4 leave the controller with their DTOs, `PinMerger` and `PinDuplicates.setRejected`.
- `oasdiff changelog` lists the two removals as breaking; `info.version` is `23.0.0`.

## 6. Adjacent backlog items

None. "A video's excerpt is not found as such" is about finding duplicates, which this lot leaves as it is.

## 7. Out of scope

- **In-step play without `ImageDecoder`** (decision D). Observed in block 50's tests without it.
- **Back-filling stored media** (decision F). Observed as no migration or worker code writing the new columns of an
  existing row.
- **Restoring a rejected candidate to pending** (ADR 0052, consequences). Observed as no `PENDING` among the
  decision's values.
- **Opening a candidate from the dialog** (decision A). Observed as "open a duplicate" deleted in block 40.
- **An animated image's duration on the server.** `MediaLimits` reads a null duration as "decode every frame", and
  the comparator alone needs it. Observed as `durationMillis` null for every image.
- **Measuring or storing an offset between two media**, refused in the prototype's review. Observed as no column
  beyond decision E's.
- **Playing sound.** Observed as both players muted in block 50.
