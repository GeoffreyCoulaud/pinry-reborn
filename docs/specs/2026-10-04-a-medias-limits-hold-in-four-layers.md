# A media's limits hold in four layers

Date: 2026-10-04
Status: Draft for the operator. Two specification reviews ran, `.reviews/0.46.0-spec.md` on a first draft and
`.reviews/0.46.0-spec-2.md` on this one, its 2 CRITICAL, 4 MAJOR and 13 MINOR closed in this document. Frozen when
the lot's last block merges.
Lot `0.46.0`. Branches: one stack, each block on the previous one: 10 `refactor/one-process-runner`, 20
`refactor/libvips-runs-in-a-child-process`, 30 `feat/the-media-limits-decide`, 33 `feat/a-media-stores-its-frames`,
36 `feat/the-handshake-publishes-pixels-per-frame`, 40 `feat/a-rendition-degrades`, 50
`feat/the-webapp-measures-a-video`.
(Corrected: the splits added 25 `refactor/vips-thumbnail-renders-in-a-child-process` above 20, 43
`feat/a-rendition-is-served-as-judged` and 46 `feat/renders-wait-their-turn` above 40, and the closing block 60
`fix/the-media-limits-close` tops the stack.)
ADR: `docs/adr/0050-a-medias-limits-hold-in-four-layers.md`, written in block 10.

## 1. Goal

No media, crafted or ordinary, can exhaust the API's memory or stall its renders, and a costly media is shown poorer
rather than not at all. Closes the backlog item "A video rendition miss has no single flight".

## 2. What exists today

- `GetPinMediaRendition.serveRendition` renders a miss on the request's thread, with no bound on how many run at once.
  A render that throws answers `500` (`ThrowableMapper`), and the next request replays it.
- One video poster at `-threads 2` peaks at 211 MB (SMALL), 331 MB (MEDIUM) and 747 MB (LARGE) for a 4K clip
  (`real-check-poster-memory.log`, handoff `2026-10-03 - handoff - the-pin-holds-a-video.md`, "The real check").
- `VipsImageProbe` holds an image's first frame to `media.max_pixels` (`VipsImageProbe.kt:54`), and
  `VipsImageTransformer` renders an animated image with every frame (`n = -1`): a GIF whose later frames redraw a
  1x1 sub-rectangle of a large canvas is small in bytes and huge decoded.
- `MediaIngestion` holds a video to the same `media.max_pixels` (`MediaIngestion.kt:114`).
- libvips runs in the JVM through vips-ffm 1.9.8, with no timeout; vips-ffm offers a kill only on a raw
  `MemorySegment` (`VipsHelper.image_set_kill`). ffmpeg runs as a child process under `media.video_timeout`.
- `Media` stores neither a frame count nor a duration.
- The rendition's ETag is built from the requested animated flag (`MediaController.kt:183-184`), not from the cache key.
- The web application measures an image's pixels before sending it (`drops.ts`, `measured`) and no video's.
- `proxy.conf` sets no `proxy_read_timeout`, so nginx answers `504` to a request waiting past 60 s.

## 3. What was measured

Report `.reviews/0.46.0-measurements.md`, 2026-10-04: a container from `api/Dockerfile`'s base (Ubuntu 26.04.1, vips
8.18.0, ffmpeg 8.0.1) on a Ryzen 5 7600X, 12 vCPU, medians of 3 runs; each figure there names its log. The second
review's own runs are under its scratchpad's `review2/logs/`, named below. Synthetic content, landscape only, amd64
only; H.265, VP9 and AV1 unmeasured.

- **A decoder's memory follows one frame, its CPU every frame.** A 7000x7000 GIF whose later frames redraw 1x1
  weighs 34 KB at 10 frames and 56 KB at 1000; rendered whole it peaks at 362 MiB and 405 MiB, and takes 0.6 s and
  44 s: about 1 Gpx per CPU second for libvips on a GIF. ffmpeg decodes H.264 at 0.4 to 0.7 Gpx per CPU second.
- **The poster is the exception**: `thumbnail=n=100` holds a hundred frames at the rendition's size. The 8K 60 fps
  poster peaks at 745 MiB at MEDIUM (480) and 1102 MiB at LARGE (960) (`review2/logs/large-poster-8k60.log`).
- **`vipsheader -a` counts the bomb's 1000 frames in 20 ms and 37 MiB**, decoding nothing.
- **Peak RSS of one render at MEDIUM**: a 50 MP PNG 121 MiB (0.83 s); a 480x270 GIF of 100 frames 93 MiB (2.2 s); a
  video poster 244 / 331 / 748 MiB at 1080p / 4K / 8K (0.31 / 0.70 / 2.54 s), from one frame 114 / 204 / 524 MiB
  (0.07 / 0.12 / 0.24 s); the animated rendition of an 8K 60 fps clip 683 MiB (3.84 s).
- **An address-space cap is set by threads, not pixels.** glibc reserves 64 MiB of address space per thread arena,
  so vips sits at 0.7 to 1.1 GiB of address space for 72 MiB of RSS, and ffmpeg 0.75 to 1.1 GiB above its RSS.
  `MALLOC_ARENA_MAX=2` and `VIPS_CONCURRENCY=2` bring a 50 MP PNG to 249 MiB on 2 cores and on 12, with no change in
  time. ffmpeg's filter graph still grows with the cores: the 8K poster with arenas at 2 is 1117 MiB on 2 cores and
  1337 MiB on 12, and `-filter_threads 2` brings 12 cores to 1154 MiB (`review2/logs/cores0-1-poster-8k60.log`,
  `cores0-11-poster-8k60.log`, `cores0-11-poster-8k60-ft2.log`).
- **A cap at the bisected minimum is not safe**: 5 of 10 runs passed there. A too tight cap fails as
  `glib: Error creating thread` or `vips_tracked: out of memory`, exit 1, like any decoder refusal.
- **`-threads 2` bounds ffmpeg's decoder only**: the 8K poster used about 3.5 cores.
- **A process start costs 20 ms** (`vipsheader`) to 40 ms (`ffprobe`). `libvips-tools` adds 0.13 MiB to the image.

## 4. Decisions

- **A. The four layers of ADR 0050.**
- **B. `MediaLimits`, in `api-domain/.../domain/media`**, replaces `MediaBounds`. A pure data class built from
  `media.*` by `MediaAdapterProducers`:
  - `refuseIfOver(measured: MeasuredMedia)` throws the refusals the callers already map;
  - `renditionOf(media: Media, px: Int, animated: Boolean): RenditionMode`;
  - `MeasuredMedia` is what a probe returns, image or video alike: kind, width, height, frames, bytes, duration.
  - The pixel and frame bounds are compared there alone. The byte caps enforced while streaming (`FilesystemMediaStore`,
    yt-dlp's directory) and the duration ffprobe reports against stay where they are.
- **C. `enum class RenditionMode { WHOLE, FIRST_FRAME, ONE_FRAME_POSTER, NONE }`**, richest first, judged on two costs:
  - **pixels held at once**: a source frame, or for a poster with `thumbnail` also its 100 frames at the output size;
  - **pixels decoded**: width x height x the frames decoded. `frames` is ffprobe's `nb_read_packets` for a video,
    `n-pages` for an image. A poster decodes `min(frames, 100)`, an animated rendition
    `min(frames, ceil(3 x frames / duration))`; a duration that lies is bounded by `decoder_timeout` alone.
  - `NONE` when a source frame is past `max_pixels_per_frame`;
  - `WHOLE` when both costs are within `max_pixels_per_frame` and `max_pixels_per_render`;
  - otherwise an animated image gets `FIRST_FRAME`, and a video's animated rendition is judged again as its poster,
    which gets `ONE_FRAME_POSTER` when its held frames are past the bound. At the defaults a LARGE poster of a 16:9
    video holds 164 MP and is drawn from one frame; MEDIUM holds 41 MP and is whole.
  - **The cache key and the ETag name what is rendered, not what was asked**, so raising a bound renders anew and
    the browser does not revalidate the degraded bytes: `FIRST_FRAME` and a degraded animated rendition take the
    static key (`-s`), the bytes being the static rendition's, `ONE_FRAME_POSTER` its own (`-s1`). The ETag becomes
    `"<mediaId>-<key>"`.
- **D. `NONE` answers `422 MEDIA_RENDITION_UNAVAILABLE`**, and the web application shows "preview unavailable" where
  an `<img>` of a rendition fails: the grid's tile, the recycle bin's tile, and the video's poster in `PinMedia`.
- **D2. A decoder that times out or exits non-zero answers the same `422` and leaves an empty marker**
  `<key>.failed-<decoder_timeout seconds>-<decoder_memory bytes>` beside the key, through a new `RenditionCache`
  method. A miss reads the marker with the cache, before and after taking its permit: one with the current values
  answers `422` at once, one with other values is ignored and rendered over. A failed `store` or process start
  answers `500` and marks nothing. `evictMedia` and `ReapOrphanedStorage` work per media id and reclaim markers with
  the rest.
  (Corrected: a marker older than 24 hours, read from its file's modification time, is ignored and rendered over, so
  a failure the host caused, an out-of-memory kill, a full disk or a loaded machine, is replayed at most once a day.
  24 hours is chosen, not measured. The failure's cause is logged at `warn` where the marker is written.)
- **E. Configuration**, each key added in the block that reads it:
  - `media.video_timeout` becomes `media.decoder_timeout` (block 10), 60 s still: a render within the bounds takes
    about 8 s on the machine of section 3, GIF or H.264;
  - `media.decoder_memory` (block 10), 2 GiB: the largest address space measured with G's settings is 1154 MiB at
    MEDIUM, 1716 MiB for the LARGE 8K poster with arenas alone, which C now draws from one frame. Block 10 measures
    the four sizes on 2 and 12 cores under G's settings and corrects this default before it merges;
    (Corrected: block 10 measured them, the 8K 60 fps and 50 MP clips and the 50 MP PNG and 100-frame bomb of
    section 3, at 112, 240, 480 and 960, 3 runs uncapped then 5 under 2 GiB, logs `block10/logs/<case>.log` in the
    lot's scratchpad. The highest address space of what C keeps whole is 1181 MiB, the MEDIUM poster of the 8K clip
    on 12 cores (`cores12-cap2147483648-poster-8k60-480`); a one-frame poster reaches 1151 MiB, a preview 1127 MiB
    and vips 449 MiB. The LARGE 8K poster C draws from one frame reaches 1952 MiB
    (`cores12-cap2147483648-poster-8k60-960`). All 320 runs under 2 GiB passed on both core counts, so 2 GiB stays.)
    (Corrected: the key is `media.decoder_memory_bytes`, carrying its unit like the other byte limits.)
  - `media.max_pixels` becomes `media.max_pixels_per_frame` (block 36), 50,000,000 still: a 48 to 50 MP phone
    photograph and an 8K video (33 MP) pass;
  - `media.max_pixels_per_render` (block 40), 8,000,000,000, about 8 s: the 8K 60 fps animated rendition (6 Gpx)
    stays whole, the 1000-frame bomb (49 Gpx) keeps its first frame;
  - `media.render_concurrency` (block 40), 2: two of the largest whole renders measured, 748 MiB each, about 1.5 GiB.
- **F. `Media` gains `frames`, 1 for a still, and `duration`, null for an image**, filled at ingestion by a new
  migration. No backfill: the alpha is deployed nowhere (ADR 0049, context).
- **G. Every decoder runs in a child process** through one runner in `api-utilities`. Its surface is what its callers
  already need: a working directory, a timeout, a per-tick callback that may throw (yt-dlp's heartbeat and byte
  bound), stderr kept, the process and its descendants destroyed on any exit path, and an optional `prlimit --as`
  cap. The caller maps a timeout and a non-zero exit to its own exceptions. Under a cap the child's environment
  carries `MALLOC_ARENA_MAX=2` and `VIPS_CONCURRENCY=2`, and ffmpeg's render command gains `-filter_threads 2`
  beside `-threads 2`. ffmpeg, ffprobe and yt-dlp move to it; libvips moves to `vipsheader` and
  `vips thumbnail --size down --no-rotate`, as `resize` neither upscales nor rotates today, the frame's dimensions
  reaching the call through `RenditionSpec`. vips-ffm leaves the build, the image gains `libvips-tools`, and
  `--enable-native-access` stays, sqlite-jdbc needing it.
- **H. One fair `Semaphore`** of `media.render_concurrency` permits around every rendition miss, in
  `GetPinMediaRendition`. A request waits without a time limit. `proxy.conf` raises `proxy_read_timeout` on `/api/`
  to the wait of a cold grid's first page: 40 tiles (`pins.ts`, `PAGE_SIZE`) x 8 s / 2 permits = 160 s, so `180s`.
- **I. The handshake publishes `maxPixelsPerFrame`** in place of `maxPixels`: a break, so the contract goes to
  `22.0.0`. The web application refuses an image or a video past it before sending it.

## 5. Blocks

| Block | Branch | Decisions |
|---|---|---|
| 10 | `refactor/one-process-runner` | G (the runner), E (`decoder_*`) |
| 20 | `refactor/libvips-runs-in-a-child-process` | G (libvips) |
| 25 | `refactor/vips-thumbnail-renders-in-a-child-process` | (Corrected: added) G (libvips' renders) |
| 30 | `feat/the-media-limits-decide` | B |
| 33 | `feat/a-media-stores-its-frames` | F |
| 36 | `feat/the-handshake-publishes-pixels-per-frame` | E (`max_pixels_per_frame`), I (API and fixtures) |
| 40 | `feat/a-rendition-degrades` | C, D, D2 (API), E (the rest), H (Corrected: C, E (`max_pixels_per_render`)) |
| 43 | `feat/a-rendition-is-served-as-judged` | (Corrected: added) C (the use case), D (API) |
| 46 | `feat/renders-wait-their-turn` | (Corrected: added) D2, E (`render_concurrency`), H |
| 50 | `feat/the-webapp-measures-a-video` | D, I (web application) |

### Block 10

- Runner tests against real commands, one per item of G's surface: a command past its timeout is killed and
  reported; a non-zero exit is reported with its stderr; a callback that throws stops the run; a child's own child is
  destroyed with it; the working directory is the one given; `python3` allocating past a `prlimit` cap fails, under
  it succeeds.
- `FfmpegVideoProcessor` and `YtDlpPageMediaExtractor` call the runner; their suites pass unchanged, including the
  byte bound destroyed mid-transfer, the throwing heartbeat and both timeouts.
- `FfmpegVideoProcessorTest`'s "its decoder is bounded to two threads" also asserts `-filter_threads 2`.
- The measurement of `decoder_memory` under G's settings, posted in the pull request and corrected here.
- Carries this specification and ADR 0050.

### Block 20

- `vipsheader` reports format, width, height and frame count; the probe's suite passes against the same fixtures,
  plus an animated GIF's frame count.
- `vips thumbnail` renders the transformer's suite, plus an EXIF-oriented JPEG rendered unrotated and an image
  smaller than the size asked rendered at its own size. `ENCODER_VERSION` goes to `v2`.
- vips runs under `media.decoder_timeout` and `media.decoder_memory`.
- `api-imaging-vips` no longer depends on vips-ffm; `api/Dockerfile` and the gate's container install
  `libvips-tools`. `dagger call smoke` and one real render through the running image, in the pull request.
- Past the budget, the probe and the transformer split into 20 and 25.
  (Corrected: they split, the transformer alone bringing 16 files: 20 keeps the probe, `ProbeResult`'s frames and
  `libvips-tools`, 160 lines and 15 files; 25, `refactor/vips-thumbnail-renders-in-a-child-process`, the transformer,
  `RenditionSpec`'s frame dimensions, `ENCODER_VERSION` and vips-ffm's removal, 216 lines and 16 files.)

### Block 30

- `MediaLimits` unit tests: the per-frame bound one pixel under and one over, for an image and a video.
- `MediaIngestion` calls `refuseIfOver` once; no pixel comparison is left in the probes nor in `MediaIngestion`. The
  probes return `MeasuredMedia`, and their fakes follow.

### Block 33

- The migration adds `frames` and `duration`; an ingested still, animated GIF and video each store theirs.

### Block 36

- The handshake reads `maxPixelsPerFrame`; `dagger call contract-guard` is red at `21.1.0` and green at `22.0.0`.
  `MediaController`'s 422 description names `media.max_pixels_per_frame`.
- The web application's uses follow, `uploads.ts` and `uploads.test.ts` by its typecheck, and the two untyped
  handshake fixtures by name: `src/test/app.tsx` and `create-a-pin-by-uploading-a-file.journey.test.tsx`.
- Removes the backlog item.
  (Corrected: the item closes with the semaphore, which lands in block 46 after block 40's split.)

### Block 40

- `renditionOf` unit tests reach every `RenditionMode`: the bomb of section 3 gets `FIRST_FRAME`, a LARGE poster of a
  16:9 video `ONE_FRAME_POSTER`, a MEDIUM one `WHOLE`, an animated rendition past the per-render bound its poster.
- `GetPinMediaRendition` with fakes that block on a latch: once four cold misses have started under two permits, two
  are inside the fakes and two wait; a hit takes no permit; under one permit, a render that throws lets the next
  request render; a waiter finding the rendition cached once it holds a permit renders nothing.
- An animated GIF over the per-render bound is served the static key's bytes; after the bound is raised, the same
  request renders the animated key. A controller test: the degraded and the whole rendition carry different ETags.
- `ONE_FRAME_POSTER` runs ffmpeg without `thumbnail`; a stored media past the per-frame bound answers 422
  `MEDIA_RENDITION_UNAVAILABLE`, under the contract's `22.0.0`.
- A render that fails answers 422 and calls the processor once over two requests; after `decoder_timeout` changes,
  and separately after `decoder_memory` changes, the next request renders again.
- `proxy.conf`'s timeout.
- (Corrected: past the budget, the block splits into three. 40 keeps `RenditionMode`, `renditionOf` and
  `media.max_pixels_per_render`, whose caller arrives in 43. 43, `feat/a-rendition-is-served-as-judged`, the use
  case's modes, the key, the ETag, the one-frame poster and the `422`. 46, `feat/renders-wait-their-turn`, the
  semaphore, `media.render_concurrency`, the failure marker and `proxy.conf`.)

### Block 50

- The measurement of a video: a `<video preload="metadata">` on an object URL, `loadedmetadata` giving
  `videoWidth x videoHeight`, `0 x 0`, `error` or a timeout giving `null`; the object URL revoked on every path. A
  seam in `src/test/setup.ts`, jsdom firing neither event.
- `uploads.test.ts`: a video measured past `maxPixelsPerFrame` is refused `TOO_MANY_PIXELS`. A journey drops an
  oversized video and reads the refusal; the existing video journeys pass, and the comment "measuring one would
  refuse every video" (`upload-a-video.journey.test.tsx`) is rewritten.
- An `<img>` error on each view of decision D shows "preview unavailable".
  (Corrected: the video's poster is the fallback's `<img>`; a `<video poster>` that fails raises no event.)
- Read headless in Firefox before the push.

## 6. Out of scope

- Rendering renditions at ingestion: it hides the wait on a warm cache and bounds nothing on a cold one.
- A per-key single flight: two requests for one tile at once are rare.
- Saying in a response that a rendition was degraded.
- Rotating a rendition by its EXIF orientation: today's renditions are not rotated either.
