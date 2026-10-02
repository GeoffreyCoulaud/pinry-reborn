# The pin holds a video

Date: 2026-10-02
Status: Draft for the specification review. Frozen when the lot's closing block merges.
Lot: `0.45.0`. Branches: one stack, each block on the branch below it, listed in section 5.
ADRs: `docs/adr/0047-a-video-is-repackaged-never-re-encoded.md` and
`docs/adr/0048-every-remote-fetch-goes-through-one-guarding-proxy.md`, both written in block 10: this lot adds
the API's first external processes (ffmpeg, yt-dlp), a storage format (the video containers), a public surface
(the `/media` routes) and a network boundary (the proxy).

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
  (`VipsImageProbe.formatOf`). Nothing reads a `Content-Type`, the upload's or the fetched response's: an HTML page
  is staged, then refused as `INVALID_IMAGE`.
- **An original is staged, probed, promoted, then saved**, in three places that each build the storage key
  `originals/<userId>/<pinId>/<id>.<ext>`: `SetPinImage.kt:60`, `DownloadPinImage.kt:138`,
  `UserDataImportRunner.kt:655`.
- **Renditions are WebP, rendered on the first `GET ?size=` and cached** (`GetPinImageRendition`,
  `FilesystemRenditionCache`).
- **The original is served whole**: `ImageController.serveOriginal` answers `200` with no `Accept-Ranges`, and its
  `ETag` is the raw hash, unquoted. `http/RangeHeader.kt` parses `bytes=a-` and `bytes=a-b` for the export alone; the
  `206` response is built in private functions of `MeExportController` (lines 188 to 230).
- **The SSRF guard is in `HttpImageFetcher`**: it resolves the host, checks the address with `AddressPolicy`, then
  the JDK client resolves the host again to connect.
- **A download's task lease is never renewed** (`DownloadPinImage` never calls `TaskContext.renewLease`), and
  `tasks.lease_duration` is one minute: a fetch longer than that is reclaimed and runs twice.
- **No sweep walks `originals/` or `tmp/`** (`ReapOrphanedStorage` covers renditions and the data archives).
- **`images.max_file_bytes` is 30 MiB** and `BodyLimitCheck` refuses a boot where it is not under
  `quarkus.http.limits.max-body-size`, 32M.
- **The API runs no external process** (no `ProcessBuilder` under `api/`), and ships libvips from apt
  (`api/Dockerfile:16-18`, `.dagger/gradle.Dockerfile:5`).
- **The web application measures every dropped file with `createImageBitmap`** (`drops.ts`), so a video is refused
  as `UNREADABLE`. It holds no `<video>` and no `canPlayType`. `TaskCentre.tsx:50` hardcodes `accept="image/*"`.
- **The contract is at `20.0.0`** (`application.properties:40`).

## 3. The decisions

Each letter is the question the operator answered on 2026-10-02 in Discuss.

**A. A video is at most 120 seconds and 50 MiB**, two keys, `media.max_video_seconds` and
`media.max_video_bytes`. An image keeps its own bound, `media.max_image_bytes` (today's `images.max_file_bytes`).
`quarkus.http.limits.max-body-size` rises to 64M so that `BodyLimitCheck` holds.

**B1. A video is never re-encoded.** Video codecs H.264, H.265, VP9 and AV1; audio codecs AAC, Opus and MP3, or no
audio track (K1). Anything else is refused, naming the codec. The reasoning is ADR 0047.

**C1. `Image` becomes `Media`.** One pin, at most one media, an image or a video. Renditions are WebP stills for both.

**D1. A video's renditions are its poster**, the frame ffmpeg's `thumbnail` filter picks among the first 100 frames,
rendered by libvips at the four sizes. Extracted on a rendition's cache miss, like any rendition, and never stored
apart: nothing new to export, sweep or migrate. No animated rendition; the original plays instead.

**E2, H1. A page address yields its video through yt-dlp, now.** The worker reads the fetched response's
`Content-Type`: a media type goes down today's path, an HTML page goes to yt-dlp. One route, the client unaware.

**F1, P1. One guarding proxy for every remote fetch.** A local HTTP proxy in the worker resolves each host once,
checks the address with `AddressPolicy`, and connects to that address. yt-dlp is given it with `--proxy`; the direct
fetcher with a `ProxySelector`, and loses its own guard. This closes the second resolution of section 2 on both paths.
Written with the JDK alone (`ServerSocket`, one virtual thread per connection), not Vert.x as Discuss first said:
`api-fetch-http` depends on no Quarkus module today, and the JDK covers it.

**G1. yt-dlp is pinned by uv**: `api/tools/yt-dlp/pyproject.toml` and its `uv.lock`, installed with
`uv sync --frozen` in the API image and in the gate's container, bumped weekly by Dependabot's `uv` ecosystem.

**I1. One lot, API then web application, yt-dlp last.**

**J1, S1. Any container in a closed list, repackaged.** ffprobe and ffmpeg run with
`-format_whitelist mov,matroska -protocol_whitelist file -nostdin`: the `mov` demuxer reads `.mp4`, `.mov`, `.m4v`
and `.3gp`, the `matroska` one `.mkv` and `.webm`. A playlist or a concatenation, which could read a file of the
server into the output, is refused at opening.

**L1. The stored container follows the codecs**: WebM when every track fits it (VP9 or AV1, with Opus or no audio),
MP4 otherwise. Always repackaged with `-c copy`, MP4 with `-movflags +faststart`. A pure function in `api-domain`.

**M1. AV1 and H.265 are accepted**, the web application falling back where the browser cannot decode them. yt-dlp
prefers H.264, then VP9, then AV1, then H.265.

**M1's fallback.** The stored `mimeType` of a video carries its `codecs` parameter (RFC 6381), for instance
`video/mp4; codecs="hvc1.1.6.L60.B0, mp4a.40.2"`, and is served as the original's `Content-Type`. The web
application asks `canPlayType` with it; an empty answer, or an `error` event on the `<video>`, shows the poster, a
sentence and a link to the original. No new field: `mimeType` already travels in `PinMediaStateDto`.

**N1. The rename is one mechanical block, outside the bounds**, as decision E of
`docs/specs/2026-09-28-knip-and-biome-keep-the-clients-clean.md` let the formatting pass them. Code, tables,
routes, configuration keys and the default directory `/var/lib/pinry/media` all follow; nothing is deployed.

**O1. Two orphan sweeps join the lot**: an original with no row, and a staged file past a grace.

**Q1, R1. ffmpeg and ffprobe run as processes, installed from apt.** Measured in the API's base image on 2026-10-02
(`eclipse-temurin:25-jre@sha256:bb036ed6…`, Ubuntu 26.04.1, ffmpeg `8.0.1-3ubuntu2`): the image grows from 463 MB to
816 MB, LLVM (135 MB) and Mesa (45 MB) being most of it. Accepted: the operator weighs maintenance over bytes.

The lead adds three decisions, submitted with this document:

- **ii. One ingestion path.** A use case `MediaIngestion` stages, probes, repackages a video and promotes, called by
  the upload, the download and the import, each of which builds the storage key today. The three copies would each
  have to learn the video branch otherwise.
- **iii. The export archive carries `media/`, format version 2**, the import refusing version 1, as the rename
  leaves no reader of version 1 in the code.
- **iv. A video stores `animated = false`**: its renditions are stills, and the rendition spec intersects the
  request with this flag.

## 4. The change

### The rename (block 10)

- **Every identifier where "image" names the pin's medium becomes "media"**, case kept: `Image` to `Media`,
  `ImageStore` to `MediaStore`, `ImageDownload` to `MediaDownload`, `ImagesConfig` to `MediaConfig`, `ImageFormat`
  to `MediaFormat`, `PinImageState` to `PinMediaState`, the `IMAGE_*` codes to `MEDIA_*`, `INVALID_IMAGE` to
  `INVALID_MEDIA`, and their test names.
- **What "image" still names stays**: `ImageProbe` and its exceptions (libvips probes an image), `ImageTransformer`
  and `VipsImageTransformer` (they draw WebP images), `VImage`, the module `api-imaging-vips`, and every MIME type
  literal (`image/png`).
- **Routes**: `/pins/{pinId}/media`, `/pins/{pinId}/media/status`, `/me/media-downloads`. **Keys**: `media.*`,
  `media.renditions.*`, `media.download.*`. **Tables**: `media`, `media_download`, by migration `1.28`.
- **The client follows** in the same block: paths, schema names and the `pin.media` field. Its sentences still say
  "image" until block 80.
- **The block's pull request carries the script that made it** and the command that replays it on the parent.

### Video ingestion (blocks 40 and 50)

- **A new module `api-video-ffmpeg`** (role `video`, a new `Layer` in `ArchitectureKonsistTest`) implements a domain
  port `VideoProcessor`: `probe`, `repackage`, `poster`. Each runs `ffprobe` or `ffmpeg` with section 3's flags and
  a timeout, `media.video_timeout`, after which the process is destroyed.
- **`probe`** reads `ffprobe -of json`: the demuxer, the streams' codecs, width, height and duration. It refuses an
  unlisted codec, a duration past the bound, or more than one video track, and builds the `codecs` parameter from
  `codec_name`, `profile` and `level`.
- **`MediaIngestion`** (decision ii) stages with the larger of the two byte bounds, asks `ImageProbe` first, and on
  `UnsupportedImageFormatException` asks `VideoProcessor.probe`. A video is repackaged into a second staged file,
  whose hash and size are the ones stored.
- **New refusals**: `MEDIA_TOO_LONG` (422) and `MEDIA_CODEC_UNSUPPORTED` (415) on the upload; `TOO_LONG` and
  `UNSUPPORTED_CODEC` as download reasons. `LimitsDto` gains `maxImageBytes`, `maxVideoBytes`, `maxVideoSeconds`
  in place of `maxFileBytes`, and `mediaTypes` lists the upload types: the four images, `video/mp4`, `video/webm`,
  `video/quicktime`, `video/x-matroska`.

### Serving (blocks 30 and 60)

- **The original answers `Range`** with `206`, `Accept-Ranges: bytes` and a quoted `ETag`; the `206` builder moves
  out of `MeExportController` to `http/`, both controllers calling it.
- **A video's rendition**: `VideoProcessor.poster` writes a PNG to a staged file, `ImageTransformer.render` draws
  the WebP, the cache keeps it as it keeps any rendition.

### Fetching (blocks 90 to 110)

- **`GuardingProxy`** in `api-fetch-http`, bound to `127.0.0.1` on an ephemeral port, started and stopped by a
  producer in `api-application`. It serves `CONNECT` and absolute-form requests, one request per connection.
- **`HttpMediaFetcher`** returns the response's `Content-Type` with its stream, and reaches the network through the
  proxy alone.
- **A new module `api-fetch-ytdlp`** implements `PageMediaExtractor`: `yt-dlp` with `--proxy`, `--no-playlist`,
  `--ignore-config`, `--no-cache-dir`, `--match-filters "!is_live & duration<=?<seconds>"`, `--max-filesize`, a
  format sort for section 3's codec order, an output path under the media `tmp/`, and `--print after_move:filepath`
  to name the file it wrote. Its file then goes through `MediaIngestion`. A page with no video ends the download
  with a new reason, `NO_MEDIA_FOUND`.
- **The download renews its lease** while it fetches and while yt-dlp runs, every third of `tasks.lease_duration`.

### Web application (blocks 70 and 80)

- **`lib/media.ts`**: whether a `mimeType` is a video, and what `canPlayType`'s answer means. 100 % as `lib/` is.
- **The grid**: a video tile shows its rendition and a `Play` icon; on pointer hover it swaps in a muted, looping
  `<video>` with `preload="none"` until hovered.
- **The pin's view**: a `<video controls>` whose `poster` is the grid's rendition; the fallback of decision M1.
- **The upload**: a video skips `createImageBitmap` and is judged by type and bytes alone, the server judging the
  rest; the previews draw a `<video>`; every `accept` follows `mediaTypes`; the new refusal codes and reasons get
  sentences, and the sentences that say "image" for the medium say "media".

## 5. Blocks

| Block | Branch | Title |
|---|---|---|
| 10 | `refactor/the-image-becomes-a-media` | The rename, mechanical, outside the bounds |
| 20 | `feat/the-orphans-are-swept` | Originals with no row, and stale staged files |
| 30 | `feat/the-original-answers-ranges` | `Range` on the original |
| 40 | `feat/ffmpeg-reads-a-video` | The `api-video-ffmpeg` module and ffmpeg in both images |
| 50 | `feat/a-video-is-ingested` | `MediaIngestion`, the bounds, the refusals |
| 60 | `feat/a-video-has-a-poster` | The poster as rendition |
| 70 | `feat/the-webapp-plays-a-video` | Badge, hover, player, fallback |
| 80 | `feat/the-webapp-uploads-a-video` | Upload, previews, sentences |
| 90 | `feat/the-fetch-goes-through-a-guarding-proxy` | The proxy, the fetcher behind it |
| 100 | `feat/yt-dlp-extracts-a-page` | uv, yt-dlp in both images, the `api-fetch-ytdlp` module |
| 110 | `feat/a-page-address-yields-its-video` | The worker's dispatch on `Content-Type` |

Each block measures its budget once committed, against its parent branch. Each must fail on what its subsection
lists, and each test that guards a refusal is seen red before the code that answers it.

### 10, the rename

- `dagger call gate` green, and `contract/openapi.json` regenerated at `21.0.0`.
- The script, run on `main`, reproduces the block's diff outside migration `1.28`, its model file and the script
  itself: `git diff` empty after replaying it. The reproduction is the review of the mechanical part (decision N1).
- `command grep -rniE 'image' api/*/src clients/apps/webapp/src` lists only the kept names of section 4 and MIME
  literals; the list is pasted in the block's report.
- Migration `1.28`, applied to a database holding a pin with an image and a failed download at `1.27`, keeps both
  rows under the new tables: a migration test.
- Carries this specification and both ADRs.

### 20, the sweeps

- A file under `originals/` whose media id has no row, older than `garbage-collection.orphan_grace` (new, `PT1H`),
  is deleted; the same file younger than the grace stays, which is what a promotion awaiting its transaction looks
  like.
- A file under `tmp/` older than the same grace is deleted, a younger one stays.
- An original that has its row stays at any age.

### 30, ranges

- `GET /media` of an image with `Range: bytes=0-9` answers `206`, `Content-Range: bytes 0-9/<size>`, ten bytes; no
  `Range` answers `200` with `Accept-Ranges: bytes`; `bytes=<size>-` answers `416`.
- The `ETag` is quoted, and `If-None-Match` with the quoted value answers `304`.
- The export's download keeps its behaviour, its existing tests unchanged.

### 40, ffmpeg

- Fixtures generated by ffmpeg's `lavfi` sources, a few hundred KB each, their generating command in a `README` next
  to them: H.264 with AAC in `.mkv`, H.265 with AAC in `.mov`, VP9 with Opus in `.webm`, AV1 without audio in `.mp4`,
  H.264 with AC-3, a 121-second clip, an MPEG-TS, an HLS playlist pointing at a local file.
- `probe` returns the dimensions, the duration and a `codecs` parameter per accepted fixture; refuses AC-3 naming
  it, the 121-second clip, the MPEG-TS and the playlist.
- `repackage` gives MP4 for the H.264 and H.265 fixtures and WebM for VP9 and AV1, each probed again unchanged in
  codecs; `poster` gives a PNG of the video's size.
- A process past `media.video_timeout` is destroyed and reported as a domain exception.
- ffmpeg is in `api/Dockerfile` and `.dagger/gradle.Dockerfile`, and in `api/AGENTS.md`'s setup.
- `VideoProcessor`'s consumer is block 50, which this block's pull request says.

### 50, ingestion

- Uploading each accepted fixture answers `201` and `GET /media` serves `video/mp4` or `video/webm` with its
  `codecs`; the AC-3 fixture answers `415 MEDIA_CODEC_UNSUPPORTED`, the 121-second clip `422 MEDIA_TOO_LONG`, a
  51 MiB file `413`.
- An image upload still refuses past `media.max_image_bytes` though under `media.max_video_bytes`.
- A video fetched from a file address becomes the pin's media; a download whose fetch outlasts
  `tasks.lease_duration` in a test with a short lease runs once, its lease renewed.
- An export holding a video imports into an empty account with the same bytes; a version-1 archive is refused.
- `LimitsDto` answers the three bounds from overridden keys, not the defaults. The client reads `maxImageBytes`.

### 60, the poster

- `GET /media?size=small` of a video answers `image/webp` whose shortest side is `media.renditions.small`, and a
  second request is a cache hit.
- `?animated=true` on a video answers the same still.

### 70, playing

- Journeys **play a video pin**, **a video this browser cannot play falls back**, **hover a video tile**: a video
  tile carries the play icon and an image tile does not; hovering mounts a muted `<video>` and leaving unmounts it;
  the pin's view mounts `<video controls>` with the original's address; `canPlayType` answering `""`, or an `error`
  event, shows the fallback sentence and a link whose `href` is the original.
- `lib/media.ts` at 100 %.

### 80, uploading

- Journey **upload a video**: an `.mp4` drop sends the `PUT` without calling `createImageBitmap`; a video past
  `maxVideoBytes` is refused with no request sent; `MEDIA_TOO_LONG` and `MEDIA_CODEC_UNSUPPORTED` show their
  sentences, an unknown code the general one.
- Every `accept` in the application reads `mediaTypes`.
- `command grep -n 'image' clients/apps/webapp/messages/en.json` lists no sentence naming the medium.

### 90, the proxy

- Through the proxy with `AddressPolicy.Standard`, a request to `127.0.0.1` is refused, a `CONNECT` to a host
  resolving to a private address is refused, and a redirect from a public origin to a private one is refused at the
  second connection.
- A host whose resolver answers a public address and then a private one is connected to the first, checked address:
  a test with a stub resolver counting calls, one call per connection.
- `HttpMediaFetcher` holds no `AddressPolicy` call; its tests reach a local origin through the proxy under
  `AllowAll`.

### 100, yt-dlp

- `api/tools/yt-dlp/pyproject.toml` and `uv.lock` pin yt-dlp; both images install it with `uv sync --frozen`;
  `.github/dependabot.yml` gains the `uv` entry; `imageContext` and the gate's environment receive both files.
- `PageMediaExtractor` extracts the `<video>` of a local HTML page through the proxy, refuses a page with none, and
  refuses a page whose video is past the duration bound before downloading it: the origin's request log holds no
  request for the media file.
- With the proxy refusing every address, extraction fails as unreachable: yt-dlp opened nothing directly.
- `AGENTS.md`'s claim that python3 is pinned in the API's gate is corrected.

### 110, the dispatch

- Journey-level integration: a page address yields the video pin; a direct image address still yields the image; an
  HTML page with no video ends `FAILED` with `NO_MEDIA_FOUND`, which the client maps to a sentence.
- Deletes the backlog's **Video support** item.

## 6. Adjacent backlog items

| Item | Exit |
|---|---|
| **Video support** (Features) | Deleted in block 110's pull request |
| **Import from 3rd party sites** (Features) | Left open: a bulk import of collections is its own design; this lot pins one address at a time |
| **Import follow-ons** (`P1`) | Not adjacent: the archive changes its directory, not what travels |
| **Perceptual `ImageHash`** (Features) | Not adjacent: images only |
| **A table rebuild's row-carrying path**, **`foreign_keys` is off** (`P2`) | Not adjacent: migration `1.28` renames, it rebuilds nothing, and no key points at either table |
| **`.dagger/` is neither linted nor formatted** (`P2`) | Left open: blocks 40 and 100 edit `.dagger/src/index.ts`, but linting it is a choice of tool for a third ecosystem |

## 7. Out of scope

| Not done | Observable |
|---|---|
| Re-encoding anything | No `-c:v` or `-c:a` other than `copy` in `api-video-ffmpeg`, `png` for the poster aside |
| An animated rendition of a video | `?animated=true` on a video answers a still |
| Choosing the poster | No route takes a timestamp |
| The video's duration in the API's responses | `MediaOutputDto` and `PinMediaStateDto` gain no field |
| Private or signed-in pages | yt-dlp gets no `--cookies` |
| Playlists and live streams | `--no-playlist`, and `!is_live` refuses them |
| Triage of the new packages' CVEs | `security/vex.openvex.json` unchanged |

## 8. Pitfalls

- **The rename script must not touch a MIME literal** (`image/webp`), nor the names section 4 keeps.
- **Ebean's migration generator may write a table rename as a drop and a create.** `1.28` is written by hand as two
  `ALTER TABLE ... RENAME TO` plus the index renames, SQLite having no `ALTER INDEX`.
- **Both flags go on every ffprobe and ffmpeg call**: `-format_whitelist` on one alone leaves the other open.
- **ffprobe gives no VP9 level (`-99`) and AV1's as `seq_level_idx`**, measured on lavfi clips with ffprobe 9.0.2: the
  `codecs` builder takes a documented default where a field is missing, and the fallback's `error` event covers a
  wrong guess.
- **A `.mov` may carry H.265 tagged `hev1`**; MP4 output takes `-tag:v hvc1`, which Safari requires.
- **`createImageBitmap` refuses a video**, and jsdom implements neither `canPlayType` nor `play`: `test/setup.ts`
  stubs them.
- **A proxy connection kept alive could carry a second host**: the proxy answers one request per connection.
- **The JDK client refuses Basic authentication on a proxy tunnel by default**; the proxy takes none, being bound to
  the loopback and refusing every private address.
- **The API's user has no home directory**: yt-dlp runs with `--ignore-config` and `--no-cache-dir`.
- **The gate's environment is built from a context holding its Dockerfile alone** (`.dagger/src/index.ts:682-684`),
  and `imageContext` from the `Dockerfile` and the fast jar (`495-500`): both must be given the uv files.
- **`BodyLimitCheck` reads every upload bound**: the video bound joins it in block 50.
- **The workstation's ffmpeg is not the image's** (9.0.2 here, 8.0.1 there): tests assert behaviour, never a version.
