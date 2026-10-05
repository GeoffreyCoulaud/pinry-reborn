# The duplicates are compared

Date: 2026-10-05
Status: Draft, before the specification review. Frozen when the lot's last block merges.
Lot: `0.49.0`.
Branches: one stack: 10 `feat/a-media-records-its-tracks`, 20 `feat/the-api-describes-a-media` on 10,
30 `feat/the-api-resolves-duplicates` on 20, 40 `feat/the-dialog-compares-duplicates` on 30,
50 `feat/the-comparator-plays-both-versions` on 40, 60 `refactor/the-merge-and-reject-routes-go` on 50.
ADR: `docs/adr/0052-duplicates-are-resolved-in-one-call.md`, written in block 10, records decisions B and C.
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
  `PinDuplicates.setRejected` sets one pair's `rejected_at`; `PinDuplicateController` serves both and the `GET`.
- `media` stores `width`, `height`, `byte_size`, `mime_type`, `animated`, `frames` and `duration_millis`
  (`dbmigration/1.30.sql`, which filled nothing for rows already stored). `FfprobeReport` reads a video's codec and its
  audio codec, and nothing stores either. A pin carries `createdAt`; `PinOutputDto` does not.
- The contract exposes a media's `mimeType`, `width`, `height` and `byteSize` (`PinMediaStateDto`). It is `22.3.0`.
- `media.url` with no `size` is the original's bytes, read by `PinMedia` for an image and a video. The animated
  rendition is a WebP (`VipsImageTransformer`).
- `ImageDecoder` exists in Firefox 133 and Chrome 94, and in Safari only as a preview (`mdn/browser-compat-data`,
  `api/ImageDecoder.json`, read on 2026-10-05).

## 3. Decisions

Each is the operator's answer of 2026-10-05 in Discuss, the letter of the question in brackets.

**A. The comparator is prototype v10** (the prototype's validation).
- The pin dialog shows one row while the pin has a candidate: stacked thumbnails, then "N possible duplicates" and
  *Compare* while one is pending, or "N rejected" and *Review* when every one is rejected (G).
- *Compare* replaces the dialog's content. The stage shows the version under review left of a vertical line and the
  kept one right of it, a grip moving the line. Wheel, double-click and *−* / *+* zoom from 100 % to 800 %, a drag
  pans once zoomed, and both sides share one zoom, kept across versions. The arrow keys switch versions; on the grip
  they move the line. With the kept version under review, the stage shows it alone.
- Under the stage, the decision for the version under review: *Keep*, *Merge*, *Not a duplicate*. Then a strip of
  every version, start-aligned: a crown on the kept one, the rejected ones faded, the current one outlined. The open
  pin cannot be rejected, and the kept one is changed only by keeping another.
- Beside the stage, a column of facts with no button: size, with "the largest" on the largest; duration and sound for
  an animated media, sound for a video only; weight and format; added; boards; tags.
- The footer: *Cancel*, back to the pin with nothing sent; and the submit, "Merge N pins into one" when a version is
  merged, "Reject N duplicates" when only rejections changed, disabled when nothing changed.
- The kept version by default is the one with the most pixels, the oldest pin on a tie. A pending candidate starts as
  merged, a rejected one as rejected.
- After the call the dialog shows the kept pin, as after lot `0.48.0`'s merge.

**B. One call applies the decision** (B, C, D). `POST /api/v1/pins/{pinId}/duplicates/resolutions`, the open pin in
the path, ADR 0052. Sending per click could reorder or fail alone and leave the screen and the server apart.

**C. A rejection holds against the group** (C). A rejected pin's pair with every kept or merged pin it pairs with
gets `rejected_at`, so it is not the kept pin's pending candidate once the open pin is merged.

**D. Animated media play in step** (A, and the prototype).
- One play and pause, one progress bar over the longer duration, the shorter's span shaded on it. Outside its span a
  version holds its first or last frame; both restart at 0 when the longer ends. Sound is muted.
- An *Offset* slider under the bar moves the shorter version, from 0 to the difference of durations, kept while the
  comparator is open; it ends at the same x as the bar, the time labels in fixed-width boxes. Two durations within
  0.3 s of each other have no slider.
- A video is a `<video>` the clock seeks and plays. An animated image is decoded frame by frame with `ImageDecoder`
  from its original bytes and drawn on a canvas.
- Where `ImageDecoder` is missing, an animated image plays on its own as an `<img>`, and the pair has no bar and no
  slider. No library stands in for it (A).

**E. A media records its tracks** (E). `media` gains `video_codec` and `audio_codec`, `audio_channels` and
`audio_bit_rate`, from the probe at ingestion. A video without sound has the audio columns null; a container that
states no audio bit rate leaves that one null. The column shows "AAC stereo, 128 kb/s", "No sound", or the codec
alone when the rate is unknown, and the format as the type's short name and the video codec, "MP4 · H.264".

**F. No back-fill** (F). Media stored before this lot keep null tracks, null durations and one frame. The alpha runs
on development data, which an export, a reset and an import refill through `MediaIngestion`. The handoff says so.

## 4. The contract

| Operation | Change |
|---|---|
| `PinOutputDto.createdAt` | required instant |
| `PinMediaStateDto.durationMillis` | integer or null; null for a still image and for a media stored before this lot |
| `PinMediaStateDto.videoCodec` | `x-extensible-enum` or null: `H264`, `H265`, `VP9`, `AV1`, from `VideoCodec`; null for an image |
| `PinMediaStateDto.audio` | `{codec, channels, bitRate}` or null; `codec` from `AudioCodec` (`AAC`, `OPUS`, `MP3`), `bitRate` in bits per second or null |
| `POST /api/v1/pins/{pinId}/duplicates/resolutions` | body `{decisions: {<pinId>: KEEP / MERGE / REJECT}}`; 200 the kept pin's `PinOutputDto` |
| `POST /api/v1/pins/merges`, `PUT /api/v1/pins/{pinId}/duplicates/{otherPinId}` | removed |

The resolution's refusals, every pin resolved before the first write and nothing written on a refusal (ADR 0039):
- 400 `VALIDATION_ERROR`: not exactly one `KEEP`; the path's pin missing from `decisions`, or `REJECT`; no pin named
  but the path's.
- 403 `PIN_INSUFFICIENT_PERMISSIONS`, 404 `PIN_DOES_NOT_EXIST`, 409 `PIN_ALREADY_SOFT_DELETED`, for the path's pin.
- 404 `DUPLICATE_DOES_NOT_EXIST`: a named pin other than the path's is not among its shown duplicates.

Its effect: the `MERGE` pins are absorbed into the `KEEP` pin as decision H of lot `0.48.0` says, oldest first where
that decision reads the request's order; then decision C. A candidate the body does not name is left as it is.

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

- Migration `1.32`: the four columns of decision E, nullable. `Media` gains them; `FfprobeReport` reads the first
  audio track's `channels` and `bit_rate`; `MediaIngestion` stores what the probe read. An image stores four nulls.
- An ingestion test per case: an H.264 video with AAC stereo stores its codecs, 2 channels and a rate; a VP9 WebM
  without sound stores `VP9` and null audio; a WebM whose Opus track states no `bit_rate` stores a null rate.
- Carries this specification and ADR 0052. Its consumer is block 20.

### Block 20

- The fields of section 4, read for a page in the calls that already build it.
- `oasdiff changelog` against `main` lists four added properties and no error; `info.version` is `22.4.0`.
- The web application's fixtures answer the new fields; their consumer is block 40.

### Block 30

- `DuplicateResolver` in `api-usecases`, which takes `PinMerger`'s merge, and the route.
- An integration test per refusal of section 4, each leaving every pin and pair as it was.
- A group of four, the open pin merged into a candidate, a third merged, a fourth rejected: the kept pin holds the
  union of boards and tags, the two absorbed pins share one `softDeletedAt`, and the rejected pin's pairs with the
  kept pin and the open pin are both rejected.
- Rejections alone, the open pin kept: its pairs are rejected and no pin is recycled.
- `info.version` is `22.5.0`. Its consumer is block 40.

### Block 40

- Decision A, with every media shown as its original's still on the stage: an animated media plays from block 50.
- `useResolveDuplicates` replaces `useMergePins` and `useRejectDuplicate`; `PinDuplicates.tsx` gives way to the
  row and the comparator.
- Unit tests: the default kept version, a tie going to the oldest; the submit's label and state; the zoom kept
  under the pointer and clamped to 100 % and 800 %.
- Journeys: "merge a group of duplicates" through the comparator, the open pin merged into a larger candidate;
  "reject a duplicate", then *Review* shows it faded and merges it. "open a duplicate" is deleted.
- Read headless before the push, two themes by a phone's and a desktop's width.

### Block 50

- Decision D. The clock is a pure function of the shared time, the offset and the two durations, unit-tested: a
  version held on its first frame before its span and on its last after it, both back to 0 at the longer's end.
- A test without `ImageDecoder` shows two `<img>` and no bar.
- Read headless in Firefox before the push: two videos of different lengths with the slider moved, two animated
  GIFs, light and dark, phone and desktop.

### Block 60

- The two routes of section 4 leave the controller with their DTOs, `PinMerger` and `PinDuplicates.setRejected`.
- `oasdiff changelog` lists the two removals as breaking; `info.version` is `23.0.0`.

## 6. Adjacent backlog items

None. "A video's excerpt is not found as such" is about finding duplicates, which this lot leaves as it is.

## 7. Out of scope

- **A fallback for a browser without `ImageDecoder`** (decision D). Observed in block 50's test without it.
- **Back-filling stored media** (decision F). Observed as no task kind added in the worker.
- **Restoring a rejected candidate to pending** (ADR 0052, consequences). Observed as no `PENDING` in the decision's
  values.
- **Measuring or storing an offset between two media**, refused in the prototype's review. Observed as no column
  beyond decision E's.
- **Playing sound.** Observed as both players muted in block 50.
