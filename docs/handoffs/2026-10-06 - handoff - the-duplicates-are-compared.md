# Handoff: the duplicates are compared

Date: 2026-10-06
Tier: Spec. Specification `docs/specs/2026-10-05-the-duplicates-are-compared.md`; ADR
`docs/adr/0052-duplicates-are-resolved-in-one-call.md`. Discussion with the operator on 2026-10-05, prototype v10
validated the same day, specification review `.reviews/the-duplicates-are-compared-spec.md`.
Lot `0.49.0`, one stack of 13 code blocks, where the specification planned 7: 10
`feat/a-media-records-its-tracks` (#337), 20 `feat/the-api-describes-a-media` (#338), 30
`feat/the-api-resolves-duplicates` (#340), 40 `feat/the-dialog-compares-duplicates` (#341), 41
`feat/the-comparator-decides-each-version` (#342), 43 `feat/the-stage-compares-and-zooms` (#343), 46
`feat/the-comparator-shows-the-facts` (#344), 47 `refactor/the-dialog-opens-no-duplicate` (#345), 48
`feat/the-row-replaces-the-list` (#346), 50 `feat/the-comparator-plays-both-versions` (#347), 55
`feat/the-comparator-plays-animated-images` (#348), 60 `refactor/the-reject-route-goes` (#349), 65
`refactor/the-merge-route-goes` (the pull request this file arrives in). Block 15 was dropped. Written in block 65
from the block reports collapsed in those pull requests and the lead's notes; the closing block corrects it after the
holistic review.

## Current state

- **A media records its rates and its sound** (10): migration `1.32` adds `video_bit_rate`, `audio_channels` and
  `audio_bit_rate` to `media`, nullable. A rate is the sum of the track's packet sizes over the media's duration, from
  the `ffprobe -count_packets` run ingestion already made; the channels are the first audio track's. An image stores
  three nulls. Nothing fills a row stored before this lot (decision F dropped, below).
- **A pin describes its media** (20), contract `22.4.0`: `PinOutputDto.createdAt`, and on `PinMediaStateDto`
  `durationMillis` (a video's, null for an image), `videoBitRate`, `audioChannels` and `audioBitRate`.
- **One call resolves a group** (30, 60, 65), contract `23.0.0`: `POST /api/v1/pins/{pinId}/duplicates/resolutions`,
  `{decisions: {<pinId>: KEEP | MERGE | REJECT}}`, all or nothing, answering the kept pin. `DuplicateResolver`
  applies the rejections against every kept or merged pin of the group first, then absorbs the `MERGE` pins into the
  `KEEP` pin, filling from the oldest. The group rules raise `DUPLICATE_RESOLUTION_INVALID`, on the wire 400
  `VALIDATION_ERROR`. `PUT /api/v1/pins/{pinId}/duplicates/{otherPinId}` (60) and `POST /api/v1/pins/merges` (65)
  are gone, with their DTOs, `PinDuplicates.setRejected` and `PinMerger`; no pair can be restored to pending.
- **The pin dialog compares the group** (40 to 48): while the pin has a candidate, a row of stacked thumbnails with
  "N possible duplicates" and *Compare*, or "N rejected" and *Review*. The comparator replaces the dialog's content:
  the version under review left of a line and the kept one right of it, a grip, a zoom from 100 % to 800 % shared by
  both sides, the arrow keys switching versions; the decision per version, the strip, a column of facts (size, "the
  largest", duration, sound, weight and format, added, boards, tags); *Cancel* and a submit whose label counts the
  merge or the rejections. One call through `useResolveDuplicates`; a refused call shows its message and the list
  read again. A candidate is no longer opened from the dialog.
- **Two versions play in step** (50, 55): `DuplicatePlayer` holds one clock, one bar over the longer duration and an
  *Offset* slider when the durations differ by more than 0.3 s; a version holds its first or last frame outside its
  span; muted. A video is a `<video>` the clock seeks; an animated image is decoded with `ImageDecoder` and drawn on
  a canvas, its duration the sum of its frames' (a zero or missing delay counting 100 ms). Without `ImageDecoder` an
  image plays on its own as an `<img>` and a pair holding one has no bar.

## Evidence

- Block 10: `dagger call gate` green at `164321cc`, then at `3fa82d5d` after the fix-back; budget 129 lines, 19 files
  against `main` (#337). An end-to-end case uploads an H.264 MP4 with AAC stereo, a VP9 and Opus WebM, a silent VP9
  WebM and a GIF, and reads each row back; the rates are asserted within 1 % of the packet sums
  `ffprobe -show_entries packet=stream_index,size` gives.
- Block 20: gate green at `525a394b`; budget 99 lines, 9 files (#338). `oasdiff changelog` (`tufin/oasdiff:v1.31.0`)
  against the parent: 58 infos, no error.
- Block 30: gate green at `81cbd833`; budget 489 lines, 16 files, from 531 by tightening the tests (#340).
  `oasdiff changelog`: 1 info, `endpoint-added`. One integration test per refusal of section 4, each asserting every
  pin and every pair unchanged.
- Block 40: gate green at `ce2f493d`; budget 320 lines, 5 files (#341). Written whole first, 1792 lines and 21 files
  (`wip/block-40-whole`, `44aa3eff`, local).
- Block 41: gate green at `894ad890`, then at `19e943be` after the fix-back; budget 367 lines, 9 files (#342). Read
  headless in Firefox 156 over WebDriver BiDi at 390x844 and 1280x800, light and dark, against a Node stub API, the
  stub recording each resolution's body.
- Block 43: gate green at `8415d320`; budget 401 lines, 9 files (#343). Read headless the same way, with real pointer
  drags, wheel notches and keys.
- Block 46: gate green at `30066adc`; budget 368 lines, 7 files (#344). Read headless with a JPEG, a PNG, an MP4 with
  AAC stereo and a silent WebM.
- Block 47: gate green at `7795ad83`; budget 111 lines, 5 files (#345). Read headless.
- Block 48: gate green at `fc7315d3`, then at `f3545feb` after a defect its own reading found; budget 445 lines, 9
  files (#346). Read headless, a first resolution refused with 404 included.
- Block 50: gate green at `93419293`, then at `dc20a99f` after the fix-back; budget 489 lines, 10 files (#347). Read
  headless with two H.264 test cards of 4 s and 2.4 s: positions and offsets read back from the videos'
  `currentTime`.
- Block 55: gate green at `fa9ec000`; budget 297 lines, 7 files (#348). Read headless in Firefox 157, each GIF frame's
  colour encoding its index, read back from the canvas's centre pixel at set offsets and positions.
- Block 60: gate green at `8c2d91ac`; budget 152 lines, 14 files (#349). `oasdiff changelog` against the parent: 1
  error `api-path-removed-without-deprecation` for the `PUT`, 1 info `api-schema-removed`; `info.version` `23.0.0`.
- Block 65: `dagger call gate` green at `b0393bf2`; budget 374 lines, 8 files against
  `refactor/the-reject-route-goes`. `oasdiff changelog` against the parent: 1 error
  `api-path-removed-without-deprecation` for `POST /api/v1/pins/merges`, 1 info `api-schema-removed` for
  `PinMergeInputDto`, 1 info `api-version-not-bumped`, the major having been raised against `main` by block 60.
- Vitest at the top of the web application's blocks: 73 files, 400 tests, coverage 100 % of lines and branches over
  `src/lib` (55). Every headless reading's screenshots and scripts are in the session's scratchpad,
  `read{41,43,46,47,48,50,55}/`.
- Continuous integration: one red run, on #347, below.

## Pitfalls

- **A dispatch while the previous teammate is still working races on the one working tree**: on 2026-10-06 around
  09:10 the lead dispatched block 55 while block 50 was answering a fix-back, and 50 committed its fix onto 55's
  branch with 55's uncommitted work (`7a7a0534`). The lead stopped 55 and reset the branch to `dc20a99f`; nothing was
  pushed. The patches are in the scratchpad, `race-fix.patch` for the closing block.
- **`gh stack submit` pushes every branch of the stack and opens a pull request for each one without** (47): a branch
  above still holding unsplit work has to be reset, or taken out of the stack, first.
- **A contract change needs `pnpm --filter @pinry-reborn/api-client run generate`** before the clients typecheck
  (40); after switching branches, `pnpm run messages` again, the generated catalogue keeping the other branch's keys
  (47).
- **The ffprobe report now holds one entry per packet**, about 81 bytes each (10); `VideoProbeResult` gained three
  required fields.
- **A function given to a React state setter runs later than the call**: block 50's clock ran at half speed reading a
  mutable local inside one.
- **react-aria swallows the arrow keys** in `ToggleButtonGroup` and in its slider, so the document's version switch
  never sees them there (43, 50). HeroUI's `Slider` puts no `aria-label` on its `<input type="range">` (50).
- **A mutation's own `onError` is awaited before the `mutate` call's** (`@tanstack/query-core` 5.102.8), which the
  refused submit's remount depends on (48).
- **Firefox breaks a line after the slash of "Mb/s"**: a unit in a narrow column needs `whitespace-nowrap` (46).
  `Intl.NumberFormat` in French writes "Mbit/s" with a narrow no-break space (40).
- **Biome's `noUnnecessaryConditions` misreads `RegExp.exec(...)?.[1]` as never null**: parse with `split` (40).
- **Headless Firefox paints every scrollbar white in dark theme**, a bare page included: not a finding (41).
  WebDriver BiDi in Firefox 156 refuses `setViewport` on the first listed context; a tab made with
  `browsingContext.create` accepts it, and `layout.css.prefers-color-scheme.content-override` picks the theme (41).
- **The evidence guard refuses `sed -i`, a heredoc or stdin into `python3`, and a redirection outside `$TMPDIR`**:
  export `TMPDIR` under the scratchpad first (48, 60, 65).

## Departures from the specification

- **Decision F and block 15 dropped** (question I): the operator holds development data disposable, so no instance
  keeps a row this lot did not measure. `probe_version` left block 10 as a fix-back. A back-fill waits for the first
  real deployment.
- **Five splits, recorded under the specification's block table**: block 40 in 40, 41, 43, 46, 48 (questions J and
  K), 48 in 47 and 48, 50 in 50 and 55, 60 in 60 and 65; the last three by the lead on the bounds. Block 30 was
  brought under the bound by tightening rather than split.
- Block 10: one end-to-end ingestion test rather than one per case; `FfprobeReport` refuses a zero duration as
  undecodable.
- Block 30: `PinMerger` delegated to `DuplicateResolver.absorb` until block 65 deleted it; the group rules are a
  use-case error sharing the wire code `VALIDATION_ERROR`.
- Block 40: an animated image showed its original on the stage until block 55 rather than its still rendition, the
  contract carrying no `animated` flag.
- Block 48: the row stacks the pending candidates while any is pending, and every candidate under *Review*.
- Block 50: the bar's time labels read "3.6 / 4.0s", the facts column keeping "2.4 sec".
- Block 55: a still image beside a video gives the video the bar, alone and with no slider, where block 50 left it its
  native controls; decoding is what tells a still image from an animated one.
- Block 65: the merge cases `PinMergerTest` held that the resolver did not already cover (a kept description and page
  kept, the absorbed tags and boards gained, the page filled from the older) moved into `DuplicateResolverTest`, and
  `absorb` became private.

Tier-1 fixes: block 50's half-speed clock and the slider's start cap (found in its own reading); block 60's
`PinDuplicateRepositoryInterface.setRejected`, narrowed to an `Instant` answering nothing once the restore and the
caller reading its boolean had left.

Fix-backs, from the lead's reading of the screenshots or the operator: block 10, `probe_version` removed (question I);
block 41, the footer below the fold at 1280x800; block 50, the time labels overflowing a phone's dialog.

## Tier-2 questions

- Discuss settled questions A to H with the operator; they are the specification's decisions.
- I, decision F reopened while block 15 was written: dropped, the development database being reset after the merge.
- J, block 40 at 1792 lines: split in four (40, 43, 46, 48). Answer: "reco ok".
- K, block 40 still at 639 lines after J: split again into 40, the logic, and 41, the comparator. Answer: "reco ok".
- Block 50's seam (videos, then animated images) was a bound question answered by the lead.

## What is not validated

- The whole web application against the running API rather than a stub (41 to 55).
- Safari and Chrome; touch on a real phone, where no pinch is implemented (43); arm64 (10).
- A GIF with a zero or missing delay in a real browser; WebP, APNG and AVIF animations (55).
- A long-GOP video's seeking cost while playing (50); the ffprobe report's size and parse time on a long video (10).
- The dark scrollbars in a desktop Firefox with a window (41).

## Items for the closing block

- Tier 1, the race in `a-video-this-browser-cannot-play-falls-back.journey.test.tsx` ("replaced by a playable one"):
  the second poll may settle before `expectTheFallback` reads the fallback, red on #347's run 37440107797 at
  `93419293` and predating the lot. Gate the second poll on the fallback being seen; the patch is the scratchpad's
  `race-fix.patch`.
- Tier 1, `MediaHostingIntegrationTest.kt:400` holds `}        }` (block 10), found by block 20.
- For the holistic review: every image on the stage is fetched again for `ImageDecoder`, a JPEG and a PNG included
  (55); decoding only `image/gif` and `image/webp` would avoid it. The decoder is never `close()`d, dropped with its
  query cache entry.

## The holistic review

Not run yet: it reads the top of this stack at the head of Wrap, over
`git diff lot/0.48.0-the-pin-knows-its-duplicates..origin/refactor/the-merge-route-goes`, and the closing block
records its findings here.

## The backlog

The specification names no adjacent item; "A video's excerpt is not found as such" stays open. No item changes in
the code blocks.

## The lot's counts

Fix-backs 3 (blocks 10, 41 and 50); cascaded rebases, the runs they re-triggered and the operator's reading of the
bodies: filled in by the closing block.

## Next step

Wrap: the holistic review over the diff above, then the closing block, the operator's review of the stack, and the
tag `lot/0.49.0-the-duplicates-are-compared` once it merges. **Then reset the development database**: a media stored
before this lot has no rates and no channels, and nothing fills them (decision F's correction).
