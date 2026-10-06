# Handoff: the duplicates are compared

Date: 2026-10-06
Tier: Spec. Specification `docs/specs/2026-10-05-the-duplicates-are-compared.md`; ADR
`docs/adr/0052-duplicates-are-resolved-in-one-call.md`. Discussion with the operator on 2026-10-05, prototype v10
validated the same day, specification review `.reviews/the-duplicates-are-compared-spec.md`.
Lot `0.49.0`, one stack of 14 code blocks, where the specification planned 7: 10
`feat/a-media-records-its-tracks` (#337), 20 `feat/the-api-describes-a-media` (#338), 30
`feat/the-api-resolves-duplicates` (#340), 40 `feat/the-dialog-compares-duplicates` (#341), 41
`feat/the-comparator-decides-each-version` (#342), 43 `feat/the-stage-compares-and-zooms` (#343), 46
`feat/the-comparator-shows-the-facts` (#344), 47 `refactor/the-dialog-opens-no-duplicate` (#345), 48
`feat/the-row-replaces-the-list` (#346), 50 `feat/the-comparator-plays-both-versions` (#347), 55
`feat/the-comparator-plays-animated-images` (#348), 60 `refactor/the-reject-route-goes` (#349), 65
`refactor/the-merge-route-goes` (#350, the pull request this file arrived in), 70
`feat/a-media-is-still-animated-or-video` (#353, inserted after the operator's review). Block 15 was dropped. The
closing block is two pull requests, split on the file bound: `fix/the-resolver-rejects-in-one-statement` (#351), the
reviews' API findings, then `fix/the-duplicates-comparison-closes` (#352), the client findings, the documents and
this file's corrections. Written in block 65 from the block reports collapsed in those pull requests and the lead's
notes; corrected by the closing block after the holistic reviews and the operator's review.

## Current state

- **A media records its rates and its sound** (10): migration `1.32` adds `video_bit_rate`, `audio_channels` and
  `audio_bit_rate` to `media`, nullable. A rate is the sum of the track's packet sizes over the media's duration, from
  the `ffprobe -count_packets` run ingestion already made; the channels are the first audio track's. An image stores
  three nulls. Nothing fills a row stored before this lot (decision F dropped, below), and since block 70 a video
  stored before it no longer reads at all.
- **A pin describes its media** (20), contract `22.4.0`: `PinOutputDto.createdAt`, and on `PinMediaStateDto`
  `durationMillis` (a video's, null for an image), `videoBitRate`, `audioChannels` and `audioBitRate`.
- **One call resolves a group** (30, 60, 65), contract `23.0.0`: `POST /api/v1/pins/{pinId}/duplicates/resolutions`,
  `{decisions: {<pinId>: KEEP | MERGE | REJECT}}`, all or nothing, answering the kept pin. `DuplicateResolver`
  applies the rejections against every kept or merged pin of the group first, one statement per rejected pin, then
  absorbs the `MERGE` pins into the `KEEP` pin, filling from the oldest, every write inside `resolve`'s own
  transaction. The group rules raise `DUPLICATE_RESOLUTION_INVALID`, on the wire 400
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
  span; muted. A video is a `<video>` the clock seeks; a GIF or a WebP is decoded with `ImageDecoder`, and drawn on
  a canvas when it is animated, its duration the sum of its frames' (a missing delay, or one of 10 ms or less,
  counting 100 ms); any other image is still and fetched once, by its `<img>`. A decoder is closed when the query
  cache drops it. Without `ImageDecoder` an animated image plays on its own as an `<img>` and a pair holding one has
  no bar.
- **A media is a still image, an animated image or a video** (70): `Media` is a sealed hierarchy. A `Media.Video`
  cannot be built without its duration and its video rate, its `Sound` is both channels and rate or nothing, and an
  image has no field for either. Ingestion builds the kind from the probe's type, and the mapper refuses a video row
  missing a column. Contract and schema are unchanged.

## Evidence

- Block 10: `dagger call gate` green at `164321cc`, then at `3fa82d5d` after the fix-back; budget 129 lines, 19 files
  against `main` (#337). An end-to-end case uploads an H.264 MP4 with AAC stereo, a VP9 and Opus WebM, a silent VP9
  WebM and a GIF, and reads each row back; the rates are asserted within 1 % of the packet sums
  `ffprobe -show_entries packet=stream_index,size` gives.
- Block 20: gate green at `525a394b`; budget 99 lines, 9 files (#338). `oasdiff changelog` (`tufin/oasdiff:v1.31.0`)
  against the parent: 58 infos, no error.
- Block 30: gate green at `81cbd833`, then at `eb23d57b` after the operator's fix-back; budget 489 lines, 16 files,
  from 531 by tightening the tests, 498 after the fix-back (#340). `oasdiff changelog`: 1 info, `endpoint-added`. One
  integration test per refusal of section 4, each asserting every pin and every pair unchanged.
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
- Block 65: `dagger call gate` green at its code commit, now `b4096b65` after block 30's cascade, and again at
  `1fd7609b` once that cascade's conflict was resolved; budget 374 lines, 8 files against
  `refactor/the-reject-route-goes`. `oasdiff changelog` against the parent: 1 error
  `api-path-removed-without-deprecation` for `POST /api/v1/pins/merges`, 1 info `api-schema-removed` for
  `PinMergeInputDto`, 1 info `api-version-not-bumped`, the major having been raised against `main` by block 60. After
  the operator's fix-back, green at `10fad5a9`, 399 lines and 8 files; moving the save back out of the transaction
  fails detekt's `RowMergedOutsideTransaction`.
- Block 70: gate green at `5112d7f2` (`1437db55` adds a line of the specification); budget 361 lines, 45 files
  against `10fad5a9`, past the file bound without a split (question M).
- Closing block, API half: `dagger call gate` green at `6ea92275`; budget 70 lines, 16 files against
  `feat/a-media-is-still-animated-or-video`. Client half: gate green at the branch's tip; budget 247 lines, 18 files against
  `fix/the-resolver-rejects-in-one-statement`. A journey case asserting the JPEG never fetched fails with
  `image/jpeg` added to the decoded types (3 runs of 3).
- Closing block, read headless in Firefox 157, light and dark, 390x844 and 1280x800: two GIFs, a video beside a GIF
  (one bar, the video without controls and in step, the GIF on a canvas, "2 sec" in the facts) and two JPEGs (two
  `<img>`, no bar, each original requested once, by its `<img>`).
- Vitest at the top of the stack: 74 files, 404 tests, coverage 100 % of lines and branches over `src/lib`. Every
  headless reading's screenshots and scripts are in the session's scratchpad, `read{41,43,46,47,48,50,55,closing}/`.
- Continuous integration: one red run, on #347, below.

## Pitfalls

- **A dispatch while the previous teammate is still working races on the one working tree**: on 2026-10-06 around
  09:10 the lead dispatched block 55 while block 50 was answering a fix-back, and 50 committed its fix onto 55's
  branch with 55's uncommitted work (`7a7a0534`). The lead stopped 55 and reset the branch to `dc20a99f`; nothing was
  pushed. The race fix reached the closing block as a patch in the scratchpad.
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
- **The evidence guard refuses `sed -i`, `git apply`, a heredoc or stdin into `python3`, and a redirection outside
  `$TMPDIR`**: export `TMPDIR` under the scratchpad first (48, 60, 65, closing); a patch is applied with the edit tool.
- **`gh stack rebase --abort` restores every branch of the cascade**, not only the one in conflict, so phase 5's
  "abort, the teammate rebases its branch, the cascade resumes" cannot be followed literally: the conflict is resolved
  inside the cascade, by the teammate of the branch in conflict (65, closing).
- **GitHub refuses to change a stacked pull request's base** ("Cannot change the base branch because the pull request
  is part of a stack"). Inserting a block mid-stack (70) took `gh stack unstack`, `gh pr edit <n> --base`,
  `gh stack link` with every pull request in order (a new stack), then `gh stack init <branches>` to recreate the
  local tracking, which checks the top branch out. `gh stack rebase` also refuses a branch not yet pushed, so the
  branches above 70 were rebased onto it by hand.

## Departures from the specification

- **Decision F and block 15 dropped** (question I): the operator holds development data disposable, so no instance
  keeps a row this lot did not measure. `probe_version` left block 10 as a fix-back. A back-fill waits for the first
  real deployment.
- **Five splits, recorded under the specification's block table**: block 40 in 40, 41, 43, 46, 48 (questions J and
  K), 48 in 47 and 48, 50 in 50 and 55, 60 in 60 and 65; the last three by the lead on the bounds. Block 30 was
  brought under the bound by tightening rather than split.
- Block 10: one end-to-end ingestion test rather than one per case; `FfprobeReport` refuses a zero duration as
  undecodable.
- Block 30: `PinMerger` delegated to the resolver's merge until block 65 deleted it; the group rules are a use-case
  error sharing the wire code `VALIDATION_ERROR`.
- Block 40: an animated image showed its original on the stage until block 55 rather than its still rendition, the
  contract carrying no `animated` flag.
- Block 48: the row stacks the pending candidates while any is pending, and every candidate under *Review*.
- Block 50: the bar's time labels read "3.6 / 4.0s", the facts column keeping "2.4 sec".
- Block 55: a still image beside a video gave the video the bar, alone and with no slider, where block 50 left it its
  native controls. That pair is unreachable, a still image never pairing with a video (lot `0.48.0`'s decision F), and
  the closing block records it in the specification as a correction to decision D.
- Closing block: a frame of 10 ms or less counts 100 ms, where decision D said zero; corrected in the specification.
- Block 65: the merge cases `PinMergerTest` held that the resolver did not already cover (a kept description and page
  kept, the absorbed tags and boards gained, the page filled from the older) moved into `DuplicateResolverTest`. After
  the operator's review, the merge's writes moved into `resolve`'s transaction and the helper `afterAbsorbing` only
  builds the kept pin.
- Block 70: not in the specification, inserted after the operator's review of #337 and not split past the file
  bound (question M).

Tier-1 fixes: block 50's half-speed clock and the slider's start cap (found in its own reading); block 60's
`PinDuplicateRepositoryInterface.setRejected`, narrowed to an `Instant` answering nothing once the restore and the
caller reading its boolean had left; in the closing block, the race in
`a-video-this-browser-cannot-play-falls-back.journey.test.tsx` ("replaced by a playable one", red on #347's run
37440107797 at `93419293` and predating the lot), whose replacing download now runs until the fallback has been seen.

Fix-backs, from the lead's reading of the screenshots or the operator: block 10, `probe_version` removed (question I);
block 41, the footer below the fold at 1280x800; block 50, the time labels overflowing a phone's dialog; from the
operator's review, block 30 (`eb23d57b`) and block 65 (`10fad5a9`), below; the closing block, for block 70's review.

## Tier-2 questions

- Discuss settled questions A to H with the operator; they are the specification's decisions.
- I, decision F reopened while block 15 was written: dropped, the development database being reset after the merge.
- J, block 40 at 1792 lines: split in four (40, 43, 46, 48). Answer: "reco ok".
- K, block 40 still at 639 lines after J: split again into 40, the logic, and 41, the comparator. Answer: "reco ok".
- Block 50's seam (videos, then animated images) was a bound question answered by the lead.
- The closing block at 25 files, phase 6's seam not helping since the documents are outside the count: split into
  the API findings, then the client findings and the documents. A bound question, answered by the lead ("(a)").
- L, the operator's comment on #337 asking for `Media` as a sealed hierarchy: a block 70 below the closing block,
  with its own holistic review. Answer: "a".
- M, block 70 at 45 files: no split, a line in the specification saying why. Answer: "a".

## What is not validated

- The whole web application against the running API in a test, rather than a stub (41 to 55); the operator tested
  the stack on the development instance by hand.
- Safari and Chrome; touch on a real phone, where no pinch is implemented (43); arm64 (10).
- A GIF with a missing delay or one of 10 ms or less in a real browser; WebP animations (55). A long GIF's
  up-front decode of every frame, which keeps it pending, its video partner on native controls, until it ends (55,
  the review's second finding, left as it is).
- A decoder closed when the query cache drops it, in a browser: unit-tested only (closing).
- A long-GOP video's seeking cost while playing (50); the ffprobe report's size and parse time on a long video (10).
- The dark scrollbars in a desktop Firefox with a window (41).
- Reading a development database that predates migration `1.32`: expected to fail on its first video (70).

## The holistic review

`.reviews/the-duplicates-are-compared-holistic.md`, over
`git diff lot/0.48.0-the-pin-knows-its-duplicates..origin/refactor/the-merge-route-goes`: no CRITICAL, no MAJOR, 12
MINOR. Every one is fixed in the closing block, the API half taking 5, 6 and 11 and the client half the rest.

1. Every image fetched again for `ImageDecoder`, and a still image beside a video given the bar: only a GIF or a WebP
   is decoded; the still-beside-a-video pair is recorded as unreachable in the specification. Fixed.
2. A decoder never closed: `closeDroppedAnimations` closes it when the query cache drops it, wired in `main.tsx`. The
   up-front decode of every frame is left as it is, as the review advised. Fixed.
3. No test where blocks 50 and 55 meet: two journey cases, a video beside an animated image with `ImageDecoder`, and
   a JPEG beside a GIF of one frame (the still path, the JPEG never fetched for decoding); the mixed pair read
   headless. Fixed.
4. A frame of 10 ms or less played ten times too fast: it counts 100 ms, checked against Firefox's and Chromium's
   sources, with a case in `clock.test.ts` and a correction to decision D. Fixed.
5. One `UPDATE` per (rejected, held) couple: `setRejected` takes the held pins, one statement per rejected pin; the
   repository test adds a pair between two named pins that must stay pending. Fixed.
6. The merge's KDoc cited ADR 0051's superseded fill order: `afterAbsorbing`'s cites ADR 0052, decision 3. Fixed.
7. The refused submit's remount resting on a returned promise: one comment at `pins.ts`. Fixed.
8. `Rendition`'s unused `"LARGE"`: reverted with its comment. Fixed.
9. `PinGrid`'s comment naming an ambiguous "decision B", and `fromList`: the reference dropped, the flag renamed
   `outsideGrid`. Fixed.
10. Ten comments ending in a bare decision letter: dropped, except `clock.ts` and `zoom.ts`, which name the
    specification. Fixed.
11. Two closing braces on one line in `MediaHostingIntegrationTest.kt`: split. Fixed.
12. `resolveDuplicates.test.tsx` asserting its own fixture: it asserts the request's path and body and the answer
    handed on once, against a route of its own. Fixed.

Block 70 had its own review, `.reviews/the-duplicates-are-compared-holistic-70.md`, over `10fad5a9..1437db55`: no
CRITICAL, no MAJOR, 7 MINOR, the API ones fixed in the closing block's lower half.

1. `FfmpegVideoProcessor` cast any `Media` to a video: it samples a `Media.Video` and no longer implements
   `FrameSampler`, the wiring's `when` routing by kind. Fixed.
2. The mapper read a rate without channels as a video with no sound: it refuses either half alone, with a row in
   `MediaModelMapperTest`. Fixed.
3. This handoff did not know block 70, and its reset line described the consequence before it: both rewritten. Fixed.
4. `MeasuredMedia.duration`, read by nothing since 70: deleted, with `ProbeResult`'s. Fixed.
5. Ingestion's constructors called positionally: named arguments, the pair dropped. Fixed.
6. No test that a single-frame image is stored as a `StillImage`: the end-to-end table asserts each media's kind.
   Fixed.
7. Six commas with no space after them in test constructors: refused, the operator deferring formatting to a
   formatter ("On ne corrige pas cette erreur particulière, ce serait une perte de temps si on met un linter en
   place"); the backlog item below.

## The operator's review

On GitHub, 2026-10-06, after the closing block's first push.

- #337, `Media.kt`: `Media` as a sealed hierarchy, a video always holding its rate and an image no sound. Block 70,
  question L.
- #340, `PinUpdaterIntegrationTest`: the test helpers not explicit enough, and `open` used as a variable name. Block
  30's fix-back renamed both. The question "Pourquoi Detekt ou un autre linter ne l'a pas vu ?" takes no rule, `open`
  being a soft keyword: "pas besoin".
- #340, `PinUpdaterIntegrationTest`: "Il nous faut un formatteur de code, ce genre d'écriture partiellement chainé / à
  la ligne hybride est pas bonne. Hors lot, mais à mettre au backlog." The backlog item below; the chain is left as
  it is.
- #340, `DuplicateResolver.kt`, the suppression's "Both callers read [kept] in the transaction they call this from":
  "Peut devenir faux. On doit apporter une réponse structurelle pour l'empécher." Block 65's fix-back moved the writes
  inside the transaction, where the detekt rule sees them, and dropped the suppression.

## The operator's testing

The operator tested the whole stack on the development instance, after the review above, and proposed to merge with
two cosmetic remarks sent to the backlog, verbatim:

- "L'offset de vidéo dans le comparateur de fusion est trop grossier, il peut être impossible de synchroniser deux
  vidéos courtes à la frame près." The slider steps by 100 ms. Exit: backlog, P1.
- "Les images animées sont affichées animées par défaut dans la grille, mais les renditions animées des vidéos sont
  animées uniquement au survol. On devrait avoir ces renditions animées en lecture par défaut, pas de système de
  survol (ne marche de toute façon pas sur mobile)". Exit: backlog, P1.

## The backlog

Three items added: "The Kotlin code has no formatter" under P2, from the operator's review; the comparator's coarse
offset and the video tile animated only under the pointer under P1, from the operator's testing. The specification
names no adjacent item; "A video's excerpt is not found as such" stays open.

## The lot's counts

Read with `gh run list --branch <branch>` for each of the lot's 16 branches, before the closing block's fix-backs
were pushed; each of those pushes runs the closing branches it moves once more, and no branch below them.

- Fix-backs: 7. Blocks 10, 41 and 50 before the review, each pushed before the next block; blocks 30 and 65 from
  the operator's review; the closing block twice, for block 70's review and for the operator's testing.
- Cascaded rebases: 3. Block 30's fix-back and block 65's, pushed within a minute of each other, then block 70's
  insertion, which moved both closing branches.
- Runs those cascades re-triggered: 24 on the branches above the one that moved, 10 of them cancelled by the next push,
  so 14 ran to the end. Counted as started runs, 24 is more than the lot's 16 blocks, which is ADR 0043's failure
  criterion; counted as finished runs, 14 is not.
- 44 runs in all; one red, on #347, predating the lot.
- The operator's reading of the bodies: no remark so far.

## Next step

The operator's review of the stack's last changes, then `gh stack merge --rebase` and the tag
`lot/0.49.0-the-duplicates-are-compared`. **Then reset the development database**: since block 70 a video stored
before this lot no longer reads, and a page listing one fails, since a page's media are read together.
