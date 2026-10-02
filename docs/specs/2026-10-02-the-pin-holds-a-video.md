# The pin holds a video

Date: 2026-10-02
Status: Approved by the operator on 2026-10-02. One specification review ran,
`.reviews/the-pin-holds-a-video-spec.md`, its 0 CRITICAL, 16 MAJOR and 23 MINOR closed in this document. Frozen when
the lot's closing block merges.
Lot: `0.45.0`. Branches: one stack, each block on the branch below it, listed in section 5.
ADRs, all written in block 10: `docs/adr/0047-a-video-is-repackaged-never-re-encoded.md` (what a stored video
is), `docs/adr/0048-every-remote-fetch-goes-through-one-guarding-proxy.md` (how a remote video is fetched) and
`docs/adr/0049-the-pins-medium-is-a-media.md` (the rename, the routes, the tables, the ingestion path, the export
format and the refusals).

The backlog's Features band carries **Video support**. This lot takes it: a pin's medium may be a short video,
uploaded, fetched from a file address or extracted from a web page, and played in the web application.

## 1. Goal

A signed-in user pins a video of at most 120 seconds and 50 MiB, from a file, from the address of a video file, or
from the address of a page that shows one. The grid shows its poster with a play badge and plays it muted on hover;
the pin's view plays it with sound. A browser that cannot decode it shows the poster, a sentence and a download link.

## 2. What exists today

- **A pin holds at most one `Image`** (`api-domain/.../entities/Image.kt`), read through
  `ImageRepositoryInterface.findByPinId`; `Pin.image` exists and no mapper fills it.
- **`ImageFormat` is closed to PNG, JPEG, WebP and GIF**, decided by libvips' `vips-loader`
  (`VipsImageProbe.formatOf`). A file libvips cannot open at all, a video included, becomes
  `UndecodableImageException` (`VipsImageProbe.kt:37-38`); `UnsupportedImageFormatException` is a format libvips
  reads and the enum lacks, such as AVIF (`heifload`). Nothing reads a `Content-Type`.
- **An original is staged, probed, promoted, then saved**, in three places that each build the storage key
  `originals/<userId>/<pinId>/<id>.<ext>`: `SetPinImage.kt:60`, `DownloadPinImage.kt:138`,
  `UserDataImportRunner.kt:655`.
- **Renditions are WebP, rendered on the first `GET ?size=` and cached**; when no downscale is needed and the source
  is not animated, the original is served instead (`GetPinImageRendition.kt:45-51`).
- **The original is served whole**: `ImageController.serveOriginal` answers `200` with no `Accept-Ranges`, and its
  `ETag` is the raw hash, unquoted (`ImageController.kt:163`). `http/RangeHeader.kt` parses `bytes=a-` and
  `bytes=a-b` for the export alone; the `206` response is built in private functions of `MeExportController`
  (lines 188 to 230).
- **The SSRF guard is in `HttpImageFetcher`**: it resolves the host and checks the address (`:102`), then the JDK
  client resolves the host again to connect (`:78`).
- **A download's task lease is never renewed** (`DownloadPinImage` never calls `TaskContext.renewLease`), and
  `tasks.lease_duration` is one minute: a fetch longer than that is reclaimed and runs twice.
- **No sweep walks `originals/` or `tmp/`** (`ReapOrphanedStorage` covers renditions and the data archives).
- **`images.max_file_bytes` is 30 MiB** and `BodyLimitCheck` refuses a boot where it is not under
  `quarkus.http.limits.max-body-size`, 32M.
- **The API runs no external process** (no `ProcessBuilder` under `api/`), and ships libvips from apt
  (`api/Dockerfile:16-18`, `.dagger/gradle.Dockerfile:5`). Neither base image has `python3`.
- **The web application refuses a video as `UNSUPPORTED_FORMAT`**: `isStorableFile` checks `mediaTypes`, which
  lists images only, before `createImageBitmap` measures the file (`drops.ts:52-55`). It holds no `<video>` and no
  `canPlayType`. `TaskCentre.tsx:50` hardcodes `accept="image/*"`. `REASONS` and `RETRIABLE` in
  `downloadReasons.ts` are keyed by every `DownloadReasonDto`, so a reason the server adds fails `tsc` until the
  client has its sentence.
- **The contract is at `20.0.0`** (`application.properties:40`).

## 3. The decisions

Each letter is the question the operator answered on 2026-10-02, in Discuss or, for T1, X1 and Y2, in reading this
document.

**A. A video is at most 120 seconds and 50 MiB**, two keys, `media.max_video_seconds` and
`media.max_video_bytes`. An image keeps its own bound, `media.max_image_bytes` (today's `images.max_file_bytes`).
`quarkus.http.limits.max-body-size` rises to 64M so that `BodyLimitCheck` holds, and `proxy.conf`'s
`client_max_body_size` to 96M, which its comment keeps above Quarkus' so that an oversized upload is refused by the
API in `application/problem+json` rather than by nginx in HTML.

**B1, K1. A video is never re-encoded.** Video codecs H.264, H.265, VP9 and AV1; audio codecs AAC, Opus and MP3, or
no audio. The first video track and the first audio track are kept, every other track dropped (a subtitle track
would otherwise make a WebM output fail). Anything else is refused, naming the codec. ADR 0047.

**C1, N1. `Image` becomes `Media`, in one mechanical block outside the bounds**, as decision E of
`docs/specs/2026-09-28-knip-and-biome-keep-the-clients-clean.md` let the formatting pass them. Code, routes, tables,
configuration keys and the default directory `/var/lib/pinry/media` follow; nothing is deployed (`git tag -l 'v*'`
and `gh release list` are both empty). ADR 0049.

**D1, X1. A video's still rendition is its poster, its animated rendition its first three seconds.** The poster is
the frame ffmpeg's `thumbnail` filter picks over the first 100 frames, drawn by libvips at the four sizes. The
animated rendition (`?animated=true`) is the first three seconds at 12 frames per second, encoded by ffmpeg's
`libwebp_anim` at the rendition's size and `media.renditions.webp_quality`: 0.3 s and 184 KB for a 320x240 lavfi
clip on ffmpeg 9.0.2, `libwebp_anim` being in the image's 8.0.1 too. Both correct an anamorphic source, whose pixels
are not square, so that they keep the proportions the video displays at (`scale=iw*sar:ih,setsar=1`). Both are
made on a rendition's cache miss and never stored apart. A video always takes the rendition path, at the smaller
of the requested size and its shortest side: the original is never served to an `<img>`. The web application asks
for the still and plays the original on hover; the original itself is never touched.

**E2, H1. A page address yields its video through yt-dlp, now.** The worker reads the response's `Content-Type`:
`text/html` and `application/xhtml+xml` go to yt-dlp; anything else, `application/octet-stream` and a missing
header included, goes down the direct path, where the probe judges. ADR 0048.

**F1, P1. One guarding proxy per download.** Each download starts its own proxy on the loopback, which resolves
each host once, checks the address with `AddressPolicy`, connects to that address, and records what it refused
or failed to reach. yt-dlp is given it with `--proxy`, the direct fetcher with a `ProxySelector`; the fetcher
loses its own guard. Written with the JDK alone (`ServerSocket`, virtual threads), not Vert.x as Discuss first
said: `api-fetch-http` depends on no framework. The proxy's record, not an HTTP status or yt-dlp's stderr, is what
turns a failure into `URL_NOT_ALLOWED` or `UNREACHABLE`: through a tunnel, the JDK reports a refusal as
`IOException: Tunnel failed, got: 403` (measured by the review). ADR 0048.

**G1, Y2. yt-dlp is pinned by pip**: `api/tools/yt-dlp/requirements.in` names `yt-dlp[default]`, the set yt-dlp
recommends, which carries `yt-dlp-ejs` at the version yt-dlp pins; `requirements.txt`, compiled from it by
`pip-compile --generate-hashes` (pip-tools, on the workstation only) with every transitive pin and its hash, is
marked `linguist-generated`. Dependabot recognises a pip-compile output by its header and recompiles it; block 100
confirms it in Dependabot's documentation and says where. In both images: `python3` and `python3-venv` from apt,
a venv under `/opt/yt-dlp`, `pip install --require-hashes --no-cache-dir -r requirements.txt`, its `bin/` on the
`PATH`. Dependabot's `pip` ecosystem raises it weekly. uv was the first answer, then dropped: one tool more for one
package. The release's standalone binary was weighed too: Dependabot cannot follow a release asset, and a pull
request opened by a workflow's own token starts no workflow. Ubuntu's `yt-dlp` is `2026.03.17-1`, five months old.
ADR 0048.

**T1. Deno ships with yt-dlp**, the JavaScript runtime yt-dlp 2026.08.19 enables by default and needs for YouTube,
Shorts included. Its binary is copied from a pinned `denoland/deno:bin` image (95.8 MB, `docker export` of the
2026-09-16 build, amd64 and arm64), in both images, Dependabot raising the tag. Without it a YouTube address ends
as a page with no video.

**I1. One lot, API then web application, yt-dlp last.**

**J1, S1. Two demuxers, before `-i`, on every call the API makes.** ffprobe and ffmpeg take
`-format_whitelist mov,matroska -protocol_whitelist file` before `-i`, and ffmpeg also `-nostdin` (ffprobe has no
such option and exits 1 on it). After `-i` they would be output options and an MPEG-TS would pass (measured by the
review). `mov` reads `.mp4`, `.mov`, `.m4v`, `.3gp`; `matroska` reads `.mkv`, `.webm`. A playlist or a
concatenation is refused at opening. yt-dlp's own ffmpeg and ffprobe calls get the same flags through
`--postprocessor-args`, `mpegts` added for its HLS fixup, and each yt-dlp run writes into an empty directory of its
own, so a relative entry of a hostile concatenation finds nothing. ADR 0047.

**L1. The stored container follows the codecs**: WebM when every kept track fits it (VP9 or AV1, with Opus or no
audio), MP4 otherwise. Repackaged with `-c copy -fflags +bitexact`, MP4 with `-movflags +faststart` and H.265
tagged `hvc1`. A pure function in `api-domain`.

**M1. AV1 and H.265 are accepted**, the web application falling back where the browser cannot decode them. The
stored `mimeType` of a video carries its `codecs` parameter (RFC 6381), built from the stream's extradata
(`ffprobe -show_data`: avcC, hvcC, av1C hold the exact bytes) and, for VP9, from a documented default; it is served
as the original's `Content-Type`. The web application asks `canPlayType`; an empty answer, or an `error` event on
the `<video>`, shows the poster, a sentence and a link to the original. yt-dlp asks for H.264, then VP9, then AV1,
then H.265, each with an accepted audio codec, through a chain of `-f` selectors: `-S` cannot express that order,
its codec ladder being fixed (`yt_dlp/utils/_utils.py`, read by the review).

**O1. Two orphan sweeps join the lot**: an original with no row, and a staged file past a grace.

**Q1, R1. ffmpeg and ffprobe run as processes, installed from apt.** Measured on 2026-10-02 with `du -sxm /` in a
container of `eclipse-temurin:25-jre@sha256:bb036ed6…` (Ubuntu 26.04.1, ffmpeg `8.0.1-3ubuntu2`): 463 MB after the
API's own `curl libvips42t64` layer, 816 MB with ffmpeg; `libllvm21` (135 MB) and `mesa-libgallium` (45 MB) by
`dpkg-query`. Accepted: the operator weighs maintenance over bytes. ADR 0047.

The lead adds four decisions, submitted with this document:

- **ii. One ingestion path.** A use case `MediaIngestion` stages, probes, repackages a video and promotes, called
  by the upload, the download and the import, which each build the storage key today. The dispatch: libvips first;
  if it cannot open the file (`Undecodable`) or reads a format the enum lacks (`Unsupported`), ffprobe. The video
  probe refuses a stream with no duration or a single frame, so an AVIF or a HEIC is refused, never stored as a
  video. ADR 0049.
- **iii. The export archive carries `media/`, format version 2**, the import refusing version 1. Taken in block
  10, whose script rewrites the archive's literal. The import stores a video as the archive carries it, after
  probing it: it was repackaged when first ingested, and a second pass would change its bytes. ADR 0049.
- **iv. A video stores `animated = true`** (decision X1) and the dimensions it displays at: a 90 degree rotation in
  the stream's side data swaps width and height, so a portrait phone video gets a portrait tile.
- **v. yt-dlp runs twice per page.** First `--dump-single-json`, which downloads nothing: the worker reads the
  duration, the live flag and the chosen format's size, and refuses with an exact reason. Then the download, with
  `-f` fixed to the format the first run chose. One page fetch more, and no reason read from stderr. ADR 0048.

## 4. The change

### The rename (block 10)

- **The script renames a closed list of identifiers**, written in the script and pasted in the block's report:
  every identifier where "image" names the pin's medium, case kept. `Image` to `Media`, `ImageStore` to
  `MediaStore`, `ImageDownload` to `MediaDownload`, `ImagesConfig` to `MediaConfig`, `ImageFormat` to
  `MediaFormat`, `PinImageState` to `PinMediaState`, the `IMAGE_*` codes to `MEDIA_*`, `INVALID_IMAGE` to
  `INVALID_MEDIA`, and their test names.
- **What "image" still names stays**: `ImageProbe` and its exceptions, `ImageTransformer`, `VipsImageTransformer`,
  `VImage`, `createImageBitmap`, the module `api-imaging-vips`, every MIME literal (`image/png`), the history
  migrations under `dbmigration/` and `model/`, and the client's sentences until block 80.
- **`IMAGE_TOO_LARGE` becomes `MEDIA_TOO_LARGE`**, the name an import issue kind already carries. Accepted: the two
  sit in separate sets.
- **Routes**: `/pins/{pinId}/media`, `/pins/{pinId}/media/status`, `/me/media-downloads`. **Keys**: `media.*`,
  `media.renditions.*`, `media.download.*`, also in `compose.yml` (`MEDIA_DATA_DIR: /data/media`) and in the
  `api/Dockerfile` comment that names them. **Tables**: `media`, `media_download`, by migration `1.28` as Ebean's
  generator writes it. If that drops and creates the tables, a workstation's rows are lost, which the operator
  accepts ("on casse des choses"), and block 20's sweep removes the originals left behind.
- **The export** writes `media/<id>.<ext>` and `EXPORT_FORMAT_VERSION = 2` (decision iii).
- **The client follows** in the same block: paths, schema names and the `pin.media` field.

### The video processor (blocks 40 and 45)

- **A new module `api-video-ffmpeg`** (role `video`, a new `Layer` in `ArchitectureKonsistTest`) implements a domain
  port `VideoProcessor`: `probe`, `repackage`, `poster`, `preview`. Each runs `ffprobe` or `ffmpeg` with decision J1's flags
  and `media.video_timeout` (`PT60S`), after which the process is destroyed.
- **`probe`** reads `ffprobe -of json -show_streams -show_format -show_data`: the demuxer, the codecs, the display
  dimensions, the duration, the extradata. It refuses an unlisted codec, a duration past the bound, a missing
  duration or a single frame.

### Ingestion (blocks 50 to 56)

- **`MediaIngestion`** (decision ii) stages with the larger of the two byte bounds, dispatches, applies the bound of
  what it found, repackages a video into a second staged file whose hash and size are the ones stored.
- **New refusals**: `MEDIA_TOO_LONG` (422) and `MEDIA_CODEC_UNSUPPORTED` (415) on the upload; `TOO_LONG` and
  `UNSUPPORTED_CODEC` as download reasons, their sentences in the same block, `downloadReasons.ts` requiring them.
  `LimitsDto` gains `maxImageBytes`, `maxVideoBytes`, `maxVideoSeconds` in place of `maxFileBytes`; `mediaTypes`
  lists the upload types: the four images, `video/mp4`, `video/webm`, `video/quicktime`, `video/x-matroska`,
  `video/x-m4v`, `video/3gpp`. `ExportMediaExtension` maps the two stored video types to `mp4` and `webm`, reading
  the type without its parameters.
- **The download renews its lease** while it fetches and while yt-dlp runs, every third of `tasks.lease_duration`.

### Serving (blocks 30 and 60)

- **The original answers `Range`** with `206`, `Accept-Ranges: bytes` and a quoted `ETag`; the `206` builder moves
  out of `MeExportController` to `http/`, both controllers calling it.
- **A video's still rendition**: `VideoProcessor.poster` writes a PNG to a staged file, `ImageTransformer.render`
  draws the WebP. **Its animated rendition**: `VideoProcessor.preview` writes the WebP itself, at the size and
  quality the rendition asks. The cache keeps both as it keeps any rendition.

### Fetching (blocks 90 to 110)

- **`GuardingProxy`** in `api-fetch-http`: one instance per download, bound to `127.0.0.1` on an ephemeral port,
  closed when the download ends. It serves `CONNECT` and absolute-form requests, one request per connection, and
  keeps a record: the addresses it refused, the hosts it failed to reach.
- **`HttpMediaFetcher`** returns the response's `Content-Type` with its stream, and reaches the network through the
  proxy alone.
- **A new module `api-fetch-ytdlp`** implements `PageMediaExtractor` with yt-dlp's two runs (decision v), both with
  `--proxy`, `--no-playlist`, `--playlist-items 1`, `--ignore-config`, `--no-plugin-dirs`, `--no-cache-dir`, and
  `--postprocessor-args` carrying J1's flags. The download writes into a fresh directory under the media `tmp/`,
  names its file with `--print after_move:filepath`, and is destroyed past `media.download.extraction_timeout`
  (`PT5M`) or when its directory passes `media.max_video_bytes`, which the worker checks while it runs. Its file then
  goes through `MediaIngestion`.
- **Reasons** come from the first run's JSON (`TOO_LONG`, `TOO_LARGE`, live refused as `NO_MEDIA_FOUND`), from the
  proxy's record (`URL_NOT_ALLOWED`, `UNREACHABLE`), and otherwise `NO_MEDIA_FOUND` for an extractor that found
  nothing. A page whose extractor knows no duration (a bare `<video src>`, an HLS stream) passes the first run and
  is bounded at ingestion.

### Web application (blocks 70 to 80)

- **`lib/media.ts`**: whether a `mimeType` is a video, and what `canPlayType`'s answer means. 100 % as `lib/` is.
- **The pin's view**: a `<video controls>` whose `poster` is the grid's rendition; decision M1's fallback.
- **The grid**: a video tile shows its rendition and a `Play` icon; on pointer hover it mounts a muted, looping
  `<video>`, unmounted when the pointer leaves.
- **The upload**: a video skips `createImageBitmap` and is judged by type and bytes alone; a file whose
  `File.type` is empty is sent and judged by the server; the previews draw a `<video>`; every `accept` follows
  `mediaTypes`; the upload refusals get sentences, and the sentences that say "image" for the medium say "media".

## 5. Blocks

| Block | Branch | Title |
|---|---|---|
| 10 | `refactor/the-image-becomes-a-media` | The rename, mechanical, outside the bounds |
| 20 | `feat/the-orphans-are-swept` | Originals with no row, and stale staged files |
| 30 | `feat/the-original-answers-ranges` | `Range` on the original |
| 40 | `feat/ffprobe-reads-a-video` | The `api-video-ffmpeg` module, `probe`, ffmpeg in both images |
| 42 | `test/the-probe-reads-its-fixtures` | The probe's other fixtures and their assertions |
| 45 | `feat/ffmpeg-repackages-a-video` | `repackage`, `poster`, the container function |
| 50 | `refactor/one-ingestion-path` | `MediaIngestion` for images, the three callers moved onto it |
| 53 | `feat/a-video-is-ingested` | The video branch, the bounds, the refusals, the handshake |
| 56 | `fix/the-download-renews-its-lease` | The lease renewed during a fetch |
| 60 | `feat/a-video-has-renditions` | The poster and the animated preview as renditions |
| 70 | `feat/the-webapp-plays-a-video` | The player and its fallback |
| 75 | `feat/the-grid-previews-a-video` | Badge and hover |
| 80 | `feat/the-webapp-uploads-a-video` | Upload, previews, sentences |
| 90 | `feat/a-guarding-proxy` | The proxy and its record |
| 95 | `refactor/the-fetch-goes-through-the-proxy` | The fetcher behind it, its `Content-Type` |
| 100 | `feat/yt-dlp-extracts-a-page` | pip, yt-dlp and Deno in both images, the `api-fetch-ytdlp` module |
| 110 | `feat/a-page-address-yields-its-video` | The worker's dispatch on `Content-Type` |

Each block measures its budget once committed, against its parent branch, and each test that guards a refusal is
seen red before the code that answers it. Between blocks 53 and 60 a video's tile has no rendition; the stack
merges whole.

### 10, the rename

- `dagger call gate` green, `contract/openapi.json` regenerated at `21.0.0`.
- The script, run on `main`, reproduces the block's diff except these files, listed in the report: the script,
  migration `1.28` with its model file, `EXPORT_FORMAT_VERSION` and the import's version test,
  `contract/openapi.json` and the contract's version, this specification and the three ADRs. (Corrected: also
  migration `1.29__dropsFor_1.28`, the tests reading the archive's version, and the two migration guard tests below.)
- `command grep -rnw` of each renamed identifier over `api/*/src` and `clients/apps/webapp/src`, `dbmigration/`,
  `model/` and `src/paraglide/` excluded, finds nothing. (Corrected: but `uq_images_pin_id` and
  `ux_image_download_pin` in `UniqueConstraintOutcomeTest`, which reads the whole history.)
- Migration `1.28` is what `GenerateDbMigration` writes; run a second time, it writes no `1.29`. Every constraint
  and index of the two tables is named after `media` or `media_download` (`sqlite_master` read in the migration
  guard tests). (Corrected: the generator creates the new tables and leaves the old ones pending a drop;
  `1.29__dropsFor_1.28` drops them, the operator's answer of 2026-10-02, and its two no-op markers are removed
  from `1.28` as `1.22` and `1.24` did.)

### 20, the sweeps

- A file under `originals/` whose media id has no row, older than `garbage-collection.orphan_grace` (new, `PT1H`),
  is deleted; the same file younger than the grace stays, which is what a promotion awaiting its transaction looks
  like. The grace is longer than `media.download.extraction_timeout`, so a running yt-dlp's directory is younger.
- A file under `tmp/` older than the grace is deleted, a younger one stays.
- An original that has its row stays at any age.

### 30, ranges

- `GET /media` of an image with `Range: bytes=0-9` answers `206`, `Content-Range: bytes 0-9/<size>`, ten bytes; no
  `Range` answers `200` with `Accept-Ranges: bytes`; `bytes=<size>-` answers `416`.
- The `ETag` is quoted, and `If-None-Match` with the quoted value answers `304`.
- The export's download keeps its behaviour, its existing tests unchanged.

### 40, the probe

- Fixtures generated by ffmpeg's `lavfi` sources, a few hundred KB each, their generating commands in one `README`:
  H.264 with AAC in `.mkv`, H.265 tagged `hev1` with AAC in `.mov`, VP9 with Opus in `.webm`, AV1 without audio in
  `.mp4`, H.264 with AC-3, a 121-second clip, an H.264 clip with a 90 degree display rotation, an MPEG-TS, an HLS
  playlist pointing at a local file, an AVIF still.
- `probe` returns the display dimensions, the duration and a `codecs` parameter per accepted fixture, the rotated
  one with width and height swapped; refuses AC-3 naming it, the 121-second clip, the MPEG-TS, the playlist and the
  AVIF.
- A process past `media.video_timeout` is destroyed and reported as a domain exception.
- ffmpeg is in `api/Dockerfile`, `.dagger/gradle.Dockerfile` and `api/AGENTS.md`'s setup.
- `VideoProcessor`'s consumer is block 50, which the pull request says. If the block passes 20 files, the fixtures
  that only `repackage` needs move to 45. (Corrected: every fixture is `probe`'s, and the block measured 24 files
  and 523 lines; the operator chose on 2026-10-03 to keep the H.264 with AAC, the MPEG-TS and the playlist here,
  each other case asserted on ffprobe's JSON written by hand, and to move the other seven fixtures to block 42.
  `media.video_timeout` and the processor's producer move to block 50.)

### 42, the probe's fixtures

- The seven fixtures block 40 left out, their commands added to the `README`: H.265 tagged `hev1` with AAC, VP9
  with Opus, AV1 without audio, H.264 with AC-3, the 121-second clip, the rotated clip, the AVIF still.
- `probe` returns their codecs, the rotated one with width and height swapped, and refuses AC-3 naming it, the
  121-second clip and the AVIF.

### 45, repackaging

- `repackage` gives MP4 for the H.264 and H.265 fixtures, H.265 tagged `hvc1`, and WebM for VP9 and AV1; each
  output probes to the same codecs, profile, level and packet count; repackaging the same fixture twice gives the
  same bytes; a fixture with a subtitle track repackages to WebM without it.
- `poster` gives a PNG whose dimensions equal what `probe` returns, the rotated fixture included; a fixture with a
  2:1 pixel aspect ratio gives a PNG twice as wide as its coded frame.
- `preview` gives an animated WebP of at most three seconds and more than one frame (`n-pages`), whose shortest side
  is the requested one.
- `repackage`, `poster` and `preview` refuse the MPEG-TS and the playlist themselves, not through `probe`.
- The container function at 100 % for every pair of decision L1.

### 50, one ingestion path

- The upload, the download and the import call `MediaIngestion`; the storage key is built once
  (`command grep -rn 'originals/' api/*/src/main` finds one site).
- Every existing image test passes unchanged in what it asserts.
- `media.video_timeout` (`PT60S`) joins `MediaConfig`, and a producer builds `FfmpegVideoProcessor` from it, moved
  here from block 40 so that the key is read where `VideoProcessor` is first called.

### 53, video ingestion

- Uploading each accepted fixture answers `201` and `GET /media` serves `video/mp4` or `video/webm` with its
  `codecs`; the AC-3 fixture answers `415 MEDIA_CODEC_UNSUPPORTED`, the 121-second clip `422 MEDIA_TOO_LONG`, a
  51 MiB file `413`, the AVIF `415`. Through the compose stack's nginx, the 51 MiB file still answers the API's
  `413` in `application/problem+json`.
- An image upload still refuses past `media.max_image_bytes` though under `media.max_video_bytes`.
- A video fetched from a file address becomes the pin's media.
- An export holding a video imports into an empty account with the same bytes and the same hash.
- `LimitsDto` answers the three bounds from overridden keys, not the defaults. The client reads `maxImageBytes`;
  `TOO_LONG` and `UNSUPPORTED_CODEC` have their sentences, `downloadReasons.ts` compiling.

### 56, the lease

- In a test with a short `tasks.lease_duration`, a fetch slower than the lease runs once: the task is never
  reclaimed, its lease renewed.

### 60, the renditions

- `GET /media?size=small` of a video answers a single-frame `image/webp` whose shortest side is
  `media.renditions.small`, and a second request is a cache hit.
- `GET /media?size=small&animated=true` of a video answers an `image/webp` of more than one frame, cached apart from
  the still.
- A video whose shortest side is under the requested size still answers `image/webp`, never `video/*`.

### 70, playing

- Journeys **play a video pin** and **a video this browser cannot play falls back**: the pin's view mounts
  `<video controls>` with the original's address and the rendition as `poster`; `canPlayType` answering `""`, or
  an `error` event, shows the fallback sentence and a link whose `href` is the original.
- `lib/media.ts` at 100 %.

### 75, previewing

- Journey **hover a video tile**: a video tile carries the play icon and an image tile does not; hovering mounts a
  muted `<video>` and leaving unmounts it.

### 80, uploading

- Journey **upload a video**: an `.mp4` drop sends the `PUT` without calling `createImageBitmap`; a video past
  `maxVideoBytes` is refused with no request sent; a file with an empty type is sent; `MEDIA_TOO_LONG` and
  `MEDIA_CODEC_UNSUPPORTED` show their sentences, an unknown code the general one.
- Every `accept` in the application reads `mediaTypes`.
- `command grep -n 'image' clients/apps/webapp/messages/en.json` lists no sentence naming the medium.

### 90, the proxy

- Under `AddressPolicy.Standard`, a request to `127.0.0.1` is refused and recorded; a `CONNECT` to a host whose stub
  resolver answers a private address is refused and recorded.
- A host whose stub resolver answers a public address and then a private one is connected to the first, checked
  address: one resolution per connection, the stub counting calls.
- A redirect hop is a new connection, checked again: with a test policy refusing one stub address, the second hop
  to it is refused and recorded.
- A host that does not answer is recorded as unreachable.
- The proxy's consumer is block 95, which the pull request says.

### 95, the fetcher behind it

- `HttpMediaFetcher` holds no `AddressPolicy` call and returns the `Content-Type`.
- A download refused by the proxy ends `FAILED` with `URL_NOT_ALLOWED`, over `http` and over `https`; one whose host
  does not answer ends with `UNREACHABLE`, retried.

### 100, yt-dlp

- `api/tools/yt-dlp/requirements.in` and `requirements.txt` pin yt-dlp, the second `linguist-generated` in
  `.gitattributes`; both images install it as decision G1 says, and `deno` as decision T1 says;
  `.github/dependabot.yml` gains the `pip` entry; `imageContext` and the gate's environment receive
  `requirements.txt`. A `requirements.txt` with one hash altered fails the image's build.
- In the API image, `yt-dlp --verbose` lists `deno` among its enabled JavaScript runtimes and `yt-dlp-ejs` among
  its components (`dagger call smoke` or a dedicated check in the image function, the block chooses and says).
- `PageMediaExtractor` extracts the `<video>` of a local HTML page; refuses a page with none; refuses a page whose
  JSON-LD gives a duration past the bound with no file written and no media body transferred (the origin may log a
  probing request); refuses a stream past `media.max_video_bytes` by destroying the process; destroys a run past the
  timeout.
- A page offering H.265 and H.264 yields H.264.
- An HLS page yields a file that ingestion accepts; a hostile concatenation named `.mp4` in an HLS stream is
  refused by the postprocessor's whitelist.
- With the proxy refusing every address, extraction fails `URL_NOT_ALLOWED` and the origin's log is empty.
- `AGENTS.md`'s claim that python3 is pinned in the API's gate is corrected.
- `PageMediaExtractor`'s consumer is block 110, which the pull request says.

### 110, the dispatch

- An integration test per path: a page address yields the video pin; a direct image address still yields the
  image; an HTML page with no video ends `FAILED` with `NO_MEDIA_FOUND`, which the client maps to a sentence in the
  same block.
- Deletes the backlog's **Video support** item.

## 6. Adjacent backlog items

| Item | Exit |
|---|---|
| **Video support** (Features) | Deleted in block 110's pull request |
| **Import from 3rd party sites** (Features) | Left open: a bulk import of collections is its own design; this lot pins one address at a time |
| **Import follow-ons** (`P1`) | Not adjacent: the archive changes its directory, not what travels |
| **Perceptual `ImageHash`** (Features) | Not adjacent: images only |
| **A table rebuild's row-carrying path**, **`foreign_keys` is off** (`P2`) | Not adjacent: migration `1.28` carries no row the operator wants kept, and no key points at either table |
| **`.dagger/` is neither linted nor formatted** (`P2`) | Left open: blocks 40 and 100 edit `.dagger/src/index.ts`, but linting it is a choice of tool for a third ecosystem |

## 7. Out of scope

| Not done | Observable |
|---|---|
| Re-encoding an original | `repackage` passes no `-c:v` or `-c:a` other than `copy`; only `poster` (`png`) and `preview` (`libwebp_anim`) encode, and only renditions |
| Choosing the poster | No route takes a timestamp |
| The video's duration in the API's responses | `MediaOutputDto` and `PinMediaStateDto` gain no field |
| Private or signed-in pages | yt-dlp gets no `--cookies` |
| More than one video per page, and what the extractor marks live | `--playlist-items 1`, and the first run refuses `is_live` |
| Triage of the new packages' CVEs | `security/vex.openvex.json` unchanged |

## 8. Pitfalls

- **The rename script must not touch a MIME literal, a kept name, or a frozen migration**: the tables were
  `images` and `image_download` (`1.4.sql`, `1.5.sql`), and `DbMigrationModelCoverageTest` reads the history.
- **A hand-written `ALTER TABLE ... RENAME TO` would leave the inline names `pk_images` and `uq_images_pin_id` in
  the table's SQL**, SQLite having no `ALTER INDEX`: `1.28` is the generator's, constraints named after the new
  tables.
- **The flags go before `-i`**: after it they are output options, and an MPEG-TS goes through.
- **ffprobe gives no VP9 level (`-99`)** and its JSON lacks H.264's constraint byte, H.265's tier and compatibility
  flags, AV1's tier: the `codecs` builder reads them from the extradata. A malformed string makes `canPlayType`
  answer `""`, so a playable video would show the fallback.
- **Two repackagings of one file differ without `-fflags +bitexact`** (Matroska writes random UIDs), and an MP4
  repackaged a second time differs from the first even with it (measured by the review).
- **`createImageBitmap` refuses a video**, and jsdom implements neither `canPlayType` nor `play`: `test/setup.ts`
  stubs them.
- **A proxy connection kept alive carries several hosts** (measured by the review): the proxy answers one request
  per connection.
- **The JDK client refuses Basic authentication on a proxy tunnel by default**; the proxy takes none, being bound to
  the loopback and refusing every private address.
- **The API's home directory, `/app`, is root-owned**: yt-dlp runs with `--ignore-config`, `--no-plugin-dirs` and
  `--no-cache-dir`.
- **yt-dlp's `--max-filesize` applies per fragment on HLS**, and `--match-filters` lets an unknown duration through
  with `<=?`: the bounds that hold are the worker's, on the directory and at ingestion.
- **YouTube needs both a JavaScript runtime and `yt-dlp-ejs`**: without the `default` extra, or without `deno` on
  the `PATH`, YouTube fails while other sites pass. A workstation may have its own `deno`, so a local run can pass
  where the image fails.
- **`denoland/deno:bin` holds the binary alone, linked against glibc**: it does not run in its own image, only once
  copied into ours.
- **The gate's environment is built from a context holding its Dockerfile alone** (`.dagger/src/index.ts:682-684`),
  and `imageContext` from the `Dockerfile` and the fast jar (`495-500`): both must be given `requirements.txt`.
- **Ubuntu's `python3` refuses a system-wide `pip install`** (PEP 668, externally managed): yt-dlp goes in a venv.
- **The workstation's ffmpeg is not the image's** (9.0.2 here, 8.0.1 there): tests assert behaviour, never a version.
