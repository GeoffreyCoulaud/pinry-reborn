# Handoff: a media's limits hold in four layers

Date: 2026-10-04
Tier: Spec. Specification `docs/specs/2026-10-04-a-medias-limits-hold-in-four-layers.md`, ADR
`docs/adr/0050-a-medias-limits-hold-in-four-layers.md`. Discussion with the operator on 2026-10-04 (questions A to Q),
specification reviews `.reviews/0.46.0-spec.md` and `.reviews/0.46.0-spec-2.md`, measurements
`.reviews/0.46.0-measurements.md`.
Lot `0.46.0`, one stack of 10 code blocks: 10 `refactor/one-process-runner` (PR #306), 20
`refactor/libvips-runs-in-a-child-process` (#307), 25 `refactor/vips-thumbnail-renders-in-a-child-process` (#308), 30
`feat/the-media-limits-decide` (#310), 33 `feat/a-media-stores-its-frames` (#311), 36
`feat/the-handshake-publishes-pixels-per-frame` (#312), 40 `feat/a-rendition-degrades` (#313), 43
`feat/a-rendition-is-served-as-judged` (#314), 46 `feat/renders-wait-their-turn` (#315), 50
`feat/the-webapp-measures-a-video` (the pull request this file arrives in). Written in block 50 from the block reports
collapsed in those pull requests; the closing block corrects it after the holistic review.
(Corrected: the closing block is 60 `fix/the-media-limits-close`, stacked on 50.)

## Current state

- **Every decoder runs in a child process** through one runner in `api-utilities`: ffmpeg, ffprobe and yt-dlp as
  before, libvips as `vipsheader` and `vips thumbnail --size down --no-rotate`. Each runs under
  `media.decoder_timeout` (60 s, formerly `media.video_timeout`) and a `prlimit --as` cap of `media.decoder_memory`
  (2 GiB), with `MALLOC_ARENA_MAX=2` and `VIPS_CONCURRENCY=2`, ffmpeg with `-threads 2 -filter_threads 2`. vips-ffm
  left the build; the image carries `libvips-tools`. Renditions' keys are `v2-` (`ENCODER_VERSION`).
  (Corrected: the key is `media.decoder_memory_bytes` (60). The runner waits for a child's output no longer than its
  timeout, an orphaned descendant holding the pipes otherwise keeping the run alive (60).)
- **`MediaLimits`** (`api-domain/.../domain/media`) holds every pixel and frame bound and decides: `refuseIfOver` at
  ingestion, `renditionOf` at render, returning `WHOLE`, `FIRST_FRAME`, `ONE_FRAME_POSTER` or `NONE`. It also carries
  `renderConcurrency`, `decoderTimeout` and `decoderMemory` (46). The probes return a sealed `MeasuredMedia`.
- **A `Media` stores `frames` and `duration`** (`duration_millis`), filled at ingestion. No backfill.
- **The handshake publishes `maxPixelsPerFrame`** (`media.max_pixels_per_frame`, 50,000,000); the contract is at
  `22.0.0`. **`media.max_pixels_per_render`** (8,000,000,000) degrades a costly render; a frame past the per-frame
  bound answers `422 MEDIA_RENDITION_UNAVAILABLE`.
- **The cache key and the ETag name what was rendered**: a degraded animated rendition takes the static key (`-s`),
  a one-frame poster `-s1`; the ETag is `"<mediaId>-<key>"`.
- **Rendition misses wait behind one fair semaphore** of `media.render_concurrency` (2) permits. A decoder that times
  out or exits non-zero answers the same `422` and leaves an empty marker `<key>.failed-<timeout s>-<memory bytes>`,
  so the failure is not replayed until either bound changes. `proxy.conf` sets `proxy_read_timeout 180s` on `/api/`.
  (Corrected: or until the marker is older than 24 hours, read from its file's modification time, a figure chosen
  and not measured; the failure's cause is logged at `warn` where the marker is written (60).)
- **The web application measures a video before sending it** (50): a detached `<video preload="metadata">` on an
  object URL reads `videoWidth x videoHeight`; `0 x 0`, `error` or 10 s without an answer leave the server to judge;
  the URL is revoked on every path. A video past `maxPixelsPerFrame` is refused `TOO_MANY_PIXELS` like an image.
- **A rendition the server cannot draw shows "Preview unavailable"** (50), through `RenditionImage`, on the grid's
  tile, the recycle bin's row and the unplayable video's poster. The failure is keyed on the source, so a tile whose
  animated rendition fails gets its still back when the pointer leaves.

## Evidence

- Block 10: `dagger call gate` green at `e21bb70`; budget 233 lines, 12 files (#306). The `decoder_memory`
  measurement under G's settings: 320 capped runs of 320 passed at 2 GiB, highest address space of what C keeps whole
  1181 MiB; the table is in #306 and the specification's correction of decision E.
- Block 20: green at `627eeb86`, log `gate-20.log`; budget 160 lines, 15 files (#307).
- Block 25: green at `01e8209f`, log `gate-25.log`; `dagger call smoke` green (`smoke-25.log`); budget 216 lines, 16
  files (#308). Three uploads through the running image rendered a JPEG, a portrait PNG and a 3-frame GIF in 46 to
  56 ms (`real-render.sh`).
- Block 30: green at `2f19861e`; budget 362 lines, 18 files (#310).
- Block 33: green at `57c8871e`; budget 83 lines, 10 files (#311). ffprobe on `vp9-opus.webm`: `nb_read_packets` 10,
  duration 1.008 s.
- Block 36: green at `5b747111`; budget 22 lines, 14 files (#312). The contract guard refused `21.1.0` and accepted
  `22.0.0`.
- Block 40: green at `c9fc1e5d`; budget 160 lines, 11 files (#313).
- Block 43: green at `1ca48005`; budget 203 lines, 13 files (#314).
- Block 46: green at `1e4164a6`; budget 301 lines, 17 files (#315). `GetPinMediaRenditionTest` passed 8 consecutive
  `--rerun`s; `nginx -t` accepted `proxy.conf`.
- Block 50: budget 306 lines, 16 files against `feat/renders-wait-their-turn`; gate in its pull request. A one-off
  mutation (the revocation removed) turned the 5 cases of `src/test/drops.test.ts` red.
- (Corrected: block 60: `dagger call gate` green at `166be58e`, log `gate-60.log`; budget 182 lines, 16 files against
  `feat/the-webapp-measures-a-video`. Under `prlimit --as=1048576`, `ffmpeg -version` and `vips --version` both fail
  with "failed to map segment from shared object".)
- Continuous integration green on #306 to #315 (`gh pr view <n> --json statusCheckRollup`, 2026-10-04).
- Read headless in Firefox 156.0.1 over WebDriver BiDi at 1280x800, light and dark, against a throwaway stub API
  answering `422` for some renditions, with `maxPixelsPerFrame` at 100,000 (50): a 640x360 MP4 picked was refused
  with "This file has more pixels than this server accepts.", a 160x120 one previewed and kept; the stand-in showed on
  the grid, on hover and back to the still on leaving, in the fallback and in the bin. The reading found one defect,
  the stand-in with no visible edge on the light theme, fixed before the push (`11b0ea93`).

## Pitfalls

- **A stale `META-INF/jandex.idx` in a local `api-imaging-vips/build/`** makes the boot fail with
  `AmbiguousResolutionException` after the Jandex plugin's removal: `rm -rf api/api-imaging-vips/build` (20).
- **`magick` writes no EXIF orientation into a JPEG it creates**: the oriented fixture went through a TIFF and
  `vips copy` (25).
- **A field added to `MediaLimits` touches every test building it positionally**, four use-case suites (40).
- **`org.junit.jupiter.api.Assertions.assertTimeoutPreemptively` returns `Unit` from Kotlin**; the Kotlin extension
  returns the value (46).
- **`clients/packages/api-client/src/schema.d.ts` needs `pnpm --filter @pinry-reborn/api-client run generate`** after
  each contract change (36, 43).
- **The ffmpeg suite needs `prlimit`**, Linux only: a macOS workstation cannot run it (10, `api/AGENTS.md`).
- **`isVideo` is a type guard `mimeType is string`**: on a `string` its false branch narrows to `never` (50).
- **Biome's `noNoninteractiveElementInteractions` flags an `<img onError>`**; suppressed inline with the reason (50).
- **jsdom fires no media event**: `test/setup.ts` makes a video's `src` setter fire `loadedmetadata` with a 100x100
  frame; React sets `src` as an attribute, so the players' `<video>` never meets the stub. user-event leaves from the
  element it last entered, even once it has left the tree (50).
- **`pkill -f` with a pattern the shell's own command line holds kills that shell** (50).
- **A media marked failed renders again only after 24 hours**: deleting `cache/<mediaId>/*.failed-*` under the data
  directory renders it on the next request (60).
- **A starved adapter test needs a cap below what the program needs to start**: 1 MiB stops vips and ffmpeg at the
  dynamic loader, before any decoding (60).

## Departures from the specification

- **20 into 20 and 25**, 31 files whole; **40 into 40, 43 and 46**, each estimate past 20 files. Both recorded in the
  specification's block table.
  (Corrected: the table gained rows 25, 43 and 46, and the header their branches, in the closing block.)
- `MeasuredMedia` carries no `kind`, the sealed type being the kind; a video's bytes come from ffprobe's `format.size`
  (30). (Corrected: from `staged.byteSize`, as an image's do, and "The file declares no size" is gone (60).) Defaults `frames = 1, duration = null` on `Media`, and `duration_millis` (33). A video's animated rendition
  degraded to its whole poster is `FIRST_FRAME`; a poster's held pixels are its hundred output frames alone (40).
  `ServedMedia.Rendition` drops `effectivePx` and `animated`, `ENCODER_VERSION` private to the use case (43).
  `MediaLimits` carries the concurrency and decoder bounds; `RenditionCache.mark` writes an empty entry (46). The
  runner is built by each adapter (10).
- The backlog item "A video rendition miss has no single flight" was deleted by block 36; it closes with block 46.
- Block 50: the poster of decision D is the fallback's `<img>`; a `<video poster>` that fails is not detected, the
  element reporting no error for its poster. The bin's stand-in is the icon alone, named and titled "Preview
  unavailable", its row already carrying the description. The 10 s header timeout is chosen, not measured.

Tier-1 fixes: `api/AGENTS.md` names `prlimit` (10). None in the other blocks.

## Tier-2 questions

None in any block.

## What is not validated

- arm64; H.265, VP9 and AV1 inputs; a portrait or very wide video; an anamorphic video's poster (10, 25).
- A probe timeout through the running API (20); a degraded rendition and the `422` end to end, one `@QuarkusTest`
  configuration being unable to store a media above a bound lowered afterwards (43).
- The semaphore under real decoders and load, and nginx's 180 s under a real slow page (46).
- The defaults' timing figures are section 3's, not re-run (40).
- The video measurement in a browser other than Firefox, on a real file picker's large video, and its timeout path in
  a browser (50).

## The holistic review

Not run yet: it reads the top of this stack at the head of Wrap, and the closing block records its findings here.
(Corrected: it ran, `.reviews/0.46.0-holistic.md`, 0 CRITICAL, 2 MAJOR, 5 MINOR, every one fixed in block 60:

- MAJOR, a decoder's failure reached no log: the cause is logged at `warn` where the marker is written.
- MAJOR, a transient failure marked a healthy media unavailable for good: the operator's answer "R -> A", a marker
  expiring after 24 hours, chosen and not measured; `GetPinMediaRenditionTest` pins 24 hours unavailable and one
  millisecond more rendered, `FilesystemRenditionCacheTest` the marker's time.
- MINOR, a video's bytes from ffprobe: from `staged.byteSize`; the "declares no size" refusal and its test are gone.
- MINOR, `ProcessRunner` read the output with no deadline: it reads within the time left to the timeout.
- MINOR, no test showed ffmpeg's and vips's renders under the cap: a 1 MiB starved case in each suite.
- MINOR, the block table missed 25, 43 and 46: added, with their branches, as corrections.
- MINOR, `media.decoder_memory` carried no unit: renamed `media.decoder_memory_bytes`.)

## The backlog

"A video rendition miss has no single flight" is deleted (block 36), closed by block 46. Nothing was filed.
(Corrected: reconciled in block 60; no item is adjacent to the lot's findings, none was filed or closed.)

## The lot's counts

Fix-backs, cascaded rebases, runs re-triggered and the operator's reading of the bodies: filled in by the closing
block.
(Corrected: fix-backs 0, cascaded rebases 0, runs they re-triggered 0 so far; the operator's reading of the bodies:
no remark.)

## Next step

Wrap: the holistic review over `git diff lot/0.45.0-the-pin-holds-a-video..origin/feat/the-webapp-measures-a-video`,
then the closing block, the operator's review of the stack, and the tag `lot/0.46.0-a-medias-limits-hold-in-four-layers`
once it merges.
(Corrected: the review and the closing block are done; what remains is the operator's review of the stack, its merge
and the tag.)
