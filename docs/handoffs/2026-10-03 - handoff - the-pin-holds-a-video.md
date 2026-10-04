# Handoff: the pin holds a video

Date: 2026-10-03
Tier: Spec. Specification `docs/specs/2026-10-02-the-pin-holds-a-video.md`, ADRs
`docs/adr/0047-a-video-is-repackaged-never-re-encoded.md`,
`docs/adr/0048-every-remote-fetch-goes-through-one-guarding-proxy.md` and `docs/adr/0049-the-pins-medium-is-a-media.md`.
Lot `0.45.0`, one stack of 27 code blocks: 10 `refactor/the-image-becomes-a-media` (PR #265), 20
`feat/the-orphans-are-swept` (#266), 30 `feat/the-original-answers-ranges` (#268), 40 `feat/ffprobe-reads-a-video`
(#269), 42 `test/the-probe-reads-its-fixtures` (#270), 45 `feat/ffmpeg-repackages-a-video` (#271), 50
`refactor/one-ingestion-path` (#272), 53 `feat/a-video-is-ingested` (#273), 54 `feat/a-video-has-its-own-bounds`
(#274), 55 `feat/the-handshake-publishes-the-video-bounds` (#275), 56 `feat/an-uploaded-video-is-ingested` (#276), 57
`feat/a-fetched-or-imported-video-is-ingested` (#277), 58 `fix/an-imported-videos-bytes-match-its-type` (#278), 59
`fix/the-download-renews-its-lease` (#279), 60 `feat/a-video-has-renditions` (#280), 70 `feat/the-webapp-plays-a-video`
(#281), 75 `feat/the-grid-previews-a-video` (#282), 80 `feat/the-webapp-uploads-a-video` (#283), 85
`feat/the-webapp-says-media` (#284), 90 `feat/a-guarding-proxy` (#285), 92 `feat/the-proxy-forwards-plain-http` (#286),
95 `refactor/the-fetch-goes-through-the-proxy` (#287), 100 `feat/yt-dlp-extracts-a-page` (#288), 102
`feat/the-ytdlp-module-extracts-a-page` (#289), 103 `feat/the-extraction-holds-its-bounds` (#290), 104
`feat/the-extractor-is-wired` (#291), 110 `feat/a-page-address-yields-its-video` (the pull request this file arrives
in). Written in block 110 from the block reports collapsed in those pull requests; the closing block corrects it after
the holistic review. (Corrected: #292 is block 110. The closing blocks are 120 `fix/the-video-path-holds-its-resources`
(#293), 122 `fix/the-import-renews-its-lease-per-line` (#294), 124 `fix/the-download-is-bounded-in-time` (#295), 126
`fix/the-api-says-media` (#296), 128 `fix/a-stalled-download-is-abandoned` (#297), 130 `docs/the-records-match-the-code`
(#298), 132 `fix/the-webapp-says-media-everywhere` (#299) and 134 `docs/the-lot-closes`, the pull request these
corrections arrive in. Two blocks the operator added on 2026-10-04 follow it: 136, the grid hovers on the animated
rendition, and 138, the request log redacts credentials.) (Corrected: three blocks. 140
`refactor/the-guarding-proxy-runs-on-jetty`, the proxy on Jetty, the operator asked for while reviewing the stack.)

## Current state

- **A pin's medium is a `Media`**, an image or a video: code, routes (`/pins/{pinId}/media`, `/me/media-downloads`),
  tables (`media`, `media_download`, migrations `1.28` and `1.29__dropsFor_1.28`), the `media.*` keys and the export's
  `media/` directory at format version 2 (ADR 0049). The contract is at `21.0.0`.
- **A video is at most 120 seconds and 50 MiB** (`media.max_video_seconds`, `media.max_video_bytes`), an image keeping
  `media.max_image_bytes`. The handshake publishes the three bounds and the ten upload types in `mediaTypes`.
  (Corrected: since block 120 `media.max_pixels` applies to a video at ingestion too, refused as an image is.)
- **`api-video-ffmpeg` runs ffprobe and ffmpeg as processes**, with `-format_whitelist mov,matroska -protocol_whitelist
  file` before `-i`. A video is repackaged with `-c copy`, never re-encoded, into WebM or MP4 as its codecs choose, its
  stored type carrying an RFC 6381 `codecs` parameter built from the stream's extradata (ADR 0047).
- **One ingestion path**, `MediaIngestion`, stages, probes (libvips first, ffprobe when libvips cannot open the file or
  reads a format the enum lacks), repackages a video and promotes, for the upload, the download and the import. An
  imported MP4 is stored as the archive carries it; a WebM is repackaged, which keeps its bytes. (Corrected: since
  block 120 an archived MP4 is kept only when the probe finds it already repackaged, one video and one audio track and
  a tag matching its `codecs`; any other is repackaged.)
- **The original answers `Range`** with `206` and a quoted `ETag`. A video's still rendition is its poster drawn by
  libvips; `?animated=true` is its first three seconds as an animated WebP; both are cached like any rendition.
  (Corrected: the poster's frames are scaled to the rendition's side before `thumbnail` holds them (block 120), the
  poster and the preview decode on two threads (block 134), and a render reads the original through a hard link in
  `tmp/` rather than a copy (`MediaStore.stageStored`, block 120). The original's stream opens inside the response's
  `StreamingOutput` (block 126).)
- **Two orphan sweeps**: an original with no row, and a staged file, or a yt-dlp run's directory, older than
  `garbage-collection.orphan_grace`, which a boot check keeps longer than `media.download.extraction_timeout`.
  (Corrected: longer than twice it, one per yt-dlp run, since block 124.)
- **Every remote fetch goes through one `GuardingProxy` per download** (`api-fetch-http`), bound to the loopback, which
  resolves each host once, checks it with `AddressPolicy`, serves `CONNECT` and plain HTTP one request per connection,
  and keeps the record that names `URL_NOT_ALLOWED` or `UNREACHABLE` (ADR 0048). (Corrected: since block 140 it runs
  on Jetty 12.1, `ConnectHandler` and `ProxyHandler.Forward` resolving through one guard, and each request on a
  kept-alive connection is forwarded and checked on its own.)
- **A page address yields its video through yt-dlp.** The download reads the fetched response's `Content-Type`:
  `text/html` and `application/xhtml+xml` go to `PageMediaExtractor` (`api-fetch-ytdlp`), anything else, a missing
  header included, down the direct path. yt-dlp runs twice behind the proxy, a `--dump-single-json` that refuses a live
  stream, a duration or a size past the bounds, then the download of the chosen format, each run bounded in time and in
  bytes. Its refusals arrive as `NO_MEDIA_FOUND` and `TOO_LONG`; the proxy's as `URL_NOT_ALLOWED` and `UNREACHABLE`.
  (Corrected: since block 124 the second run loads the first run's report with `--load-info-json`, less its
  `webpage_url`, and fetches no page; since block 134 that report is the chosen entry alone when yt-dlp reports the
  page as a playlist.)
- **yt-dlp is pinned by hash** (`api/tools/yt-dlp/requirements.txt`, pip-compile's output) in a venv in both images,
  Deno copied from `denoland/deno:bin`, both raised by Dependabot.
- **The download renews its lease** on each read of the body and each heartbeat of an extraction, granted at most
  every third of `tasks.lease_duration`. (Corrected: the import offers the same throttled renewal on every line since
  block 122, `imports.lease_renewal_lines` deleted. A direct body is ended past `media.download.extraction_timeout`
  from the fetch's start (block 124), by a watchdog closing the client when no byte arrives at all (block 128).)
- **The web application plays a video**: a `<video controls>` in the pin's view with a poster, a fallback (poster,
  sentence, download link) where `canPlayType` answers `""` or the element errors; a play badge and a muted hover
  preview in the grid; uploads of a video judged by type and bytes; every sentence that named the medium says "media".
  (Corrected: since block 132 a video always gets the player, in a square box when its dimensions are missing; the
  player is keyed on the address, size and type; the download link names the file from the type; the catalogue keys
  say `media`. Since block 136 the hover swaps the tile's still for the animated rendition at the tile's size, in the
same `<img>`, and back when the pointer leaves; a touch swaps nothing. No `<video>` is mounted in the grid.)

## Evidence

- Block 10: `dagger call gate` green at `64d4a72`; budget 2,522 lines and 235 files against `main`, outside both
  bounds by decision C1. The script reproduces `1fe991c4` exactly on `497585e6`, the files outside the replay listed in
  #265.
- Block 20: green at `97749641`; budget 233 lines, 9 files (#266).
- Block 30: green at `a4a276c7`; budget 225 lines, 8 files (#268).
- Block 40: green at `448d213`; budget 495 lines, 17 files (#269). Without the flags ffprobe reads `mpegts.ts` and
  `playlist.m3u8` as videos that pass every other check.
- Block 42: green at `ecd86a2`; budget 62 lines, 9 files (#270).
- Block 45: green at `6faea2b`; budget 278 lines, 8 files (#271).
- Block 50: green at `caaffdbc`; budget 253 lines, 10 files (#272).
- Block 53: green at its tip; budget 190 lines, 16 files (#273).
- Block 54: green at its tip; budget 162 lines, 19 files (#274). On the compose stack, a 51 MiB body through nginx
  answered `413` in `application/problem+json` with `MEDIA_TOO_LARGE`.
- Block 55: green at its tip; budget 40 lines, 12 files (#275).
- Block 56: green at its tip; budget 373 lines, 19 files (#276).
- Block 57: green at its tip; budget 261 lines, 17 files (#277).
- Block 58: green at its tip; budget 164 lines, 10 files (#278). With `-map_metadata -1`, `av1.mp4`, `vp9-opus.webm`
  and `subtitled.mkv` each repackaged three times gave identical outputs.
- Block 59: green at `66c5de4e`; budget 195 lines, 7 files (#279). `MediaDownloadLeaseIntegrationTest` counted 3
  origin fetches before the renewal and 1 after. (Corrected: the suite is `LeaseRenewalIntegrationTest` since block
  122, which added the import's case to it.)
- Block 60: green at `9f00741d`; budget 277 lines, 14 files (#280).
- Block 70: green at `058cae0c`; budget 297 lines, 11 files (#281).
- Block 75: green at `ec30d0c9`; budget 121 lines, 5 files (#282).
- Block 80: green at `92c82239`; budget 387 lines, 19 files (#283).
- Block 85: green at `2022b952`; budget 79 lines, 9 files (#284).
- Block 90: green at `50a906aa`; budget 451 lines, 2 files (#285).
- Block 92: green at `82e411a4`; budget 136 lines, 2 files (#286).
- Block 95: green at `f37880f3`; budget 241 lines, 8 files (#287).
- Block 100: green at `e6e2247a`; budget 117 lines, 8 files (#288). `dagger call image` builds both platforms and
  refuses a `requirements.txt` with every hash altered; `dagger call smoke` reads `JS runtimes: deno-2.9.7` and
  `yt_dlp_ejs-0.8.0`.
- Block 102: green at `e1ea6c5c`; budget 479 lines, 10 files (#289).
- Block 103: green at `e212b656`; budget 165 lines, 5 files (#290).
- Block 104: green at `5ca1a65a`; budget 179 lines, 10 files (#291).
- Block 110: green at `60afebd1`, log `gate-110.log` in the teammate's scratchpad; budget 167 lines, 10 files against
  `feat/the-extractor-is-wired`. `ModeBMediaHostingIntegrationTest` pins the `<video src>` of a local page as a
  `video/webm; codecs=` media through the wired application and real yt-dlp, and a page with no video ends `FAILED`
  with `NO_MEDIA_FOUND`.
- Block 120: green, log `gate-120.log`; budget 174 lines, 16 files (#293).
- Block 122: green at `45e2430b`; budget 158 lines, 10 files (#294). `LeaseRenewalIntegrationTest`'s import, its tag
  lines each 200 ms late under a one-second lease, ended `RUNNING` before the change and `COMPLETED` after.
- Block 124: green at `186b0d7c`, log `gate-124.log`; budget 162 lines, 9 files (#295).
- Block 126: green; budget 107 lines, 14 files (#296).
- Block 128: green at `26764da7`, log `gate-128.log`; budget 82 lines, 4 files (#297). `:api-fetch-http:test --rerun`
  passed 15 runs of 15.
- Block 130: green at `3cee7951`, log `gate-130.log`; budget 29 lines, 5 files (#298).
- Block 132: green at `7d411814`, log `gate-132.log`; budget 212 lines, 17 files (#299).
- Block 134: see "The real check" below for its measurements; gate and budget in its pull request.
- Block 140: green at `70128463`, log `/tmp/gate-140.log`; budget 475 lines, 6 files against
  `fix/the-request-log-redacts-credentials`. The adapted `GuardingProxyTest` failed 5 of 22 against the hand-rolled
  proxy (`95a6ceef`'s message). `dependencyInsight` on `api-application` resolves Jetty 12.1.13 and slf4j-api 2.0.18.
- Continuous integration green on #265 to #290 (`gh pr view <n> --json statusCheckRollup`, 2026-10-03); #291 was
  running when this file was written. (Corrected: green on every pull request from #265 to #299 on 2026-10-04, read
  the same way; #287 and #295 each needed one rerun, counted below.)
- Read headless in Firefox 156.0.1 over WebDriver BiDi at 1280x800, each against a throwaway stub API: blocks 70, 75,
  80, 85 and 110 (the readings each report details). Block 70's found one defect, fixed before its push.

## The real check

Block 134 ran what ships against real sites on 2026-10-03 and 2026-10-04, the first time any block did. Each log is in
block 134's teammate's scratchpad, under the name given.

- **The pipeline.** `dagger call image` built `linux/amd64` and refused the altered hashes (`real-check-image.log`);
  `dagger call smoke` answered healthy after 1 s and read `deno-2.9.7` and `yt_dlp_ejs-0.8.0`
  (`real-check-smoke.log`).
- **yt-dlp's flags in the image** (yt-dlp 2026.08.19, ffmpeg 8.0.1, Deno 2.9.7), the image built locally from
  `dagger call quarkus-app export`. `extract.py` replays both runs with `YtDlpPageMediaExtractor`'s options, format
  chain and `--load-info-json`, without `--proxy` (the proxy lives in the JVM), then ffprobe and the repackage with
  `FfmpegVideoProcessor`'s flags.
  - `https://www.youtube.com/shorts/jNQXAC9IVRw`: format `133+251`, 19 s, merged to Matroska, probed H.264 320x240
    with Opus, repackaged to MP4 with `avc1` and Opus (`real-check-youtube.log`).
  - `https://www.w3schools.com/html/html5_video.asp`, a plain `<video>`: the second run failed with
    `EntryNotInPlaylist: There are no entries` (`real-check-plain-video.log`). yt-dlp reports a page of several videos
    as a playlist, and `--load-info-json` on the whole report fails where the chosen entry alone downloads
    (`real-check-playlist.log`). Fixed in block 134 (`8446b637`), the operator's answer AF.
  - `https://vimeo.com/76979871`: refused by Vimeo, "The web client only works when logged-in"
    (`real-check-vimeo.log`). An upstream limit; the lot passes no `--cookies` (specification section 7).
- **Through the running API** (`compose.yml`'s `api` service, so `GuardingProxy` on the real path; `api-check.py`
  creates a user and a pin, sets the media from the address and polls):
  - The YouTube Short: `READY` after 9 s, `video/mp4; codecs="avc1.4D400C,Opus"`, 320x240, 687,900 bytes; the
    original, the still (`image/webp`, 12,576 bytes) and the animated rendition (`image/webp`, 436,750 bytes) each
    answered `200` (`real-check-api-youtube.log`). Again after block 134's two fixes: `READY` after 4 s, same bytes
    (`real-check-api-youtube-2.log`).
  - The w3schools page, after the fix: `READY` after 2 s, `video/mp4; codecs="avc1.4D400C,mp4a.40.2"`, 320x176,
    585,930 bytes, its renditions `200` (`real-check-api-plain-video.log`).
- **The poster's peak memory** (`poster-memory.py`, `real-check-poster-memory.log`): peak RSS from `getrusage` of the
  image's ffmpeg running `FfmpegVideoProcessor`'s poster command on 5-second `testsrc2` clips encoded to H.264
  Matroska, on a host with 12 cores. The grid asks for SMALL (240) and MEDIUM (480) alone.

  | Clip | Graph | Default threads | `-threads 2` |
  |---|---|---|---|
  | 1080p | before block 120 | 802 MB | 722 MB |
  | 1080p | SMALL (240) | 226 MB | 144 MB |
  | 1080p | MEDIUM (480) | 322 MB | 241 MB |
  | 1080p | LARGE (960) | 687 MB | 605 MB |
  | 4K | before block 120 | 2,954 MB | 2,675 MB |
  | 4K | SMALL (240) | 567 MB | 211 MB |
  | 4K | MEDIUM (480) | 663 MB | 331 MB |
  | 4K | LARGE (960) | 1,028 MB | 747 MB |

  The review's own method, frames straight from `lavfi` and no decoder, gives 362 MB and 1,292 MB before block 120
  (the review measured 348 MB and 1,264 MB) and 149 MB and 182 MB at MEDIUM after it. What block 120 left was the
  decoder, one thread per core each holding its frames, and the two threads are block 134's (`d4bdf4d3`), the
  operator's answer AH.
- **Block 140 ran it again on the proxy on Jetty**, on 2026-10-04, through `compose.yml`'s `api` service built from
  `refactor/the-guarding-proxy-runs-on-jetty` (`92e92084`), its volume reset, with block 134's `api-check.py`. Each
  log is under `/tmp/`, under the name given.
  - The YouTube Short, through `CONNECT`: `READY` after 4 s, `video/mp4; codecs="avc1.4D400C,Opus"`, 320x240, 687,900
    bytes, the same bytes as block 134; the original, the still (12,576 bytes) and the animated rendition (436,750
    bytes) each `200` (`real-check-140-youtube.log`).
  - The w3schools page: `READY` after 2 s, `video/mp4; codecs="avc1.4D400C,mp4a.40.2"`, 320x176, 585,930 bytes, its
    renditions `200` (`real-check-140-plain-video.log`).
  - A direct file over plain `http://`, `http://httpbin.org/image/png`, through the forward path: `READY` after 1 s,
    `image/png`, 100x100, 8,090 bytes, its renditions `200` (`real-check-140-plain-http.log`).
  - Three addresses resolving to a private address, `http://` and `https://10.0.0.1.nip.io/video.mp4` (10.0.0.1)
    and `http://localtest.me:8080/q/health` (127.0.0.1): each `FAILED` with `URL_NOT_ALLOWED` after 1 s
    (`real-check-140-private.log`).
  - The `api` container's log over those six downloads holds no line from `org.eclipse.jetty`
    (`real-check-140-api.log`). The same image started with `QUARKUS_LOG_CATEGORY__ORG_ECLIPSE_JETTY__LEVEL=INFO`
    wrote five INFO lines for one download, the version, two `Started` and two `Stopped`
    (`real-check-140-jetty-info.log`): the WARN level is what silences them.

## Pitfalls

- **The pre-push gate holds the SSH connection about 8 minutes and GitHub drops it**: push with
  `GIT_SSH_COMMAND="ssh -o ServerAliveInterval=30 -o ServerAliveCountMax=20"`, hook kept.
- **`.claude/hooks/evidence-guard.py` refuses a redirection into a variable's path** and `$TMPDIR` is empty: write
  logs to a literal `/tmp/...` path or to `/dev/null`.
- **Gradle's local build cache can keep a stale kapt output** (`QImageModel` after the rename): `--no-build-cache`.
- **ffprobe 9.0.2 reads the whitelist either side of `-i`; ffmpeg does not.** The flags stay before `-i` for both.
- **A Matroska VP9 track carries no configuration record**: the VP9 `codecs` string reads `profile` and `pix_fmt`.
- **`libwebp_anim` merges identical consecutive frames**, and ffmpeg needs `-y` over a file `createTempFile` made.
- **`BaseTest` clears every mock and fails on an unused stub**: the image suites use the `NoVideoProcessor` fake.
- **`RowMergedOutsideTransaction` reports a save of a row passed through a property**; a new such save outside
  `inTransaction` needs the same suppression.
- **`FilesystemMediaStore` is at detekt's function limit**, already suppressed inline: the next method keeps the
  suppression or splits the class.
- **A lease lost mid-fetch or mid-extraction is `TaskLeaseLostException`**, which the fetch's generic catch routes
  through `failRetryable`: abandoned on a non-final attempt, `FAILED` on the last.
- **A tunnel refusal reaches the JDK client as a bare `IOException`**: the reason comes from the proxy's record, never
  from a status or a message.
- **jsdom's `canPlayType` answers `""`**, so `test/setup.ts` stubs it; a labelled `lucide-react` icon needs
  `role="img"`; user-event's `hover` on a row does not reach a child's pointer-enter handler.
- **yt-dlp**: a bare `ffmpeg_i` postprocessor argument reaches nothing, each postprocessor is named; the generic
  extractor gives a `<video src>` no codec, hence the chain's final `/b`; it makes one probing GET of a JSON-LD
  `contentUrl` during the first run; `extraction_timeout` bounds each of the two runs, so one extraction may last twice
  it.
- **A page is fetched three times**: once by the worker, which reads its `Content-Type` and closes it unread, then by
  each yt-dlp run. (Corrected: twice since block 124, the second run reading the first run's report.)
- **yt-dlp's report for `--load-info-json` must be the chosen entry, never a playlist around it**: the whole playlist
  fails with "There are no entries". The generic extractor reports a page of two `<video>` as a playlist. And the
  report must not carry `webpage_url`, which yt-dlp re-extracts when a DASH download raises `ReExtractInfo` (124).
- **The report's path is relative to the run's directory**: the suites' `media.data_dir` is relative, and yt-dlp
  runs in the run's directory (124).
- **A hard link in `tmp/` shares the original's modification time**, so the staged-file sweep may delete a link
  during a render. Harmless: ffmpeg keeps reading the open file, and the original stays. The link needs `tmp/` and
  `originals/` on one filesystem, one volume in the image (120).
- **`HttpClient.shutdownNow` is documented to promise nothing about a read in progress**; two suite tests pin that it
  ends one here, chunked and close-delimited (128).
- **`QuarkusMock.installMockForType` needs a hand-built delegate**: one delegating to the injected bean reaches the
  client proxy, which routes back to the mock (122).
- **ffmpeg's default decoder threads follow the host's cores**, each holding its own frames, so a render's memory
  grows with the machine; the poster and the preview pass `-threads 2` before `-i` (134).
- **`LoggingRequestResponseFilter` logs every header and body at INFO**, passwords and session tokens included, in
  the shipped image too. Found by block 134 on the compose stack. Since block 138 it logs the request line, the
  status and the headers alone, `Authorization`, `Cookie` and `Set-Cookie` as `<redacted>`, and never a body. Suite
  tests over `POST /users`, `POST /sessions` and a request authenticated each way hold that the log carries neither
  the password, nor the bearer token, nor the session cookie. **A log written before block 138 may hold them in clear**: nothing is deployed (`git tag -l 'v*'` is empty),
  but a workstation's compose logs may, and are to be deleted.
- **Jetty's forward proxy sends the response head with the body's first bytes** (`ProxyHandler`'s listener writes
  in `onContent`), so an origin that sends headers and then nothing fails the fetch at `request_timeout`, never at
  the body's deadline; the fetcher's stall tests stall after one byte (140).
- **Jetty answers `400` to a request without `Host`, or whose `Host` differs from its absolute-form authority**, a
  `CONNECT` included. The JDK client and yt-dlp send a matching one; a hand-written test request must too (140).
- **Jetty's defaults would refuse real traffic**: 8 KiB heads both ways, and `UriCompliance.DEFAULT` refuses `%2F`
  and `//` in a path. The proxy sets 64 KiB on the server and on its client, and `UriCompliance.UNSAFE`, the proxy
  never reading a path (140).
- **Each proxy is a Jetty server that logs five lines at INFO** as it starts and stops;
  `quarkus.log.category."org.eclipse.jetty".level=WARN` silences them (140).
- **Firefox's BiDi refuses commands on the initial context** without `-remote-allow-system-access`: create a tab with
  `browsingContext.create` (132).
- **An Edit whose `new_string` ends in a space can lose it**: three renamed JSON keys lost theirs (132).
- **The evidence guard refuses `python3 -` reading a script from stdin**, as it refuses a redirection into a
  variable's path: write the script with the edit tool first (134).
- **Kover counts each `?.` of a chain as a branch**: `a?.b()?.c()` leaves the second null branch unreachable and
  the package under 100 %. Resolve the null once (`orEmpty()`), then chain plainly.
- **An injected `@ApplicationScoped` bean is ARC's client proxy**: unwrap it with `ClientProxy.unwrap` before an `is`.
- **Dependabot opens two pull requests per Deno release**, the tag sitting in two `docker` entries (`/api` and
  `/.dagger`). pip-tools 7.6.1 writes `--no-index` into `requirements.txt`'s header, which Dependabot never reads.

## Departures from the specification

Every split, each recorded in the specification's block table in `(Corrected: ...)` form:

- **40 into 40 and 42**: 24 files and 523 lines, every fixture being the probe's. The operator chose A.
- **53 into 53, 54, 56 and 57**: an inventory of about 45 files, the operator's answer "a". **54 then gave the
  handshake to 55** at 26 files, the lead's answer; the lease moved from 56 to 58, then to 59.
- **58 split from 57**: the fix-back on 57's imported video would have taken 57 to 20 files, the lead's split.
- **80 into 80 and 85**: 25 files; the sentences that say "image" for the medium went to 85, the lead's answer a.
- **90 into 90 and 92**: 580 lines; plain HTTP forwarding went to 92, the lead's answer A.
- **100 into 100, 102 and 104**: an inventory of about 28 files; 100 the tools, 102 the module, 104 the wiring, the
  lead's answer A.
- **102 into 102 and 103**: 590 lines; the extraction's bounds went to 103, the lead's answer A.

Moves without a split: `media.video_timeout` and the ffmpeg producer from 40 to 53 (block 50 handles images alone);
`mediaTypes` from 56 to 80 (56 measured 24 files); `media.max_video_seconds` to 55, its first reader.

The fix-backs, each a block or a commit stacked above what it fixes, none rewriting a lower branch:

- **Block 58 from 57**: an archived H.264 in Matroska was stored as `video/mp4` over Matroska bytes.
- **The `rtmp`, `rtsp` and `mms` bypass closed in 103** (`e212b656`), listed unverified by 102: a protocol filter on
  every `-f` selector and `--downloader native`, so no format reaches an external program outside `--proxy`.
- **The stale yt-dlp directory sweep in 104** (`5ca1a65a`): a run's directory left by an API killed mid-run is
  deleted once its newest entry passes the grace.

Other departures, as each report details: the rename's drops pending in `1.29__dropsFor_1.28` (10); `416` as a shared
refusal and the renditions' `ETag` quoted (30); the probe applying the pixel aspect ratio, H.265 always `hvc1.`, MP3
always `mp4a.6B` (40); `poster` and `preview` taking a `StagedFile` (45); `animated` defaulted from the media, a video
still, and `preview`'s quality moved to the constructor (60); the player's key and swipe guard (70); the grid's touch
guard (75); `MeasuredUpload` and the shared `FilePreview` (80); the proxy's 64 KiB header bound (90); every hash altered
in the image's tamper check, and Dependabot's pip-compile detection corrected in decision G1 (100); the hostile
concatenation refused by ingestion's probe rather than by yt-dlp (102); the lease renewed per read rather than by a
timer (59).

Tier-1 fixes: the upload discards on any probe failure (50); two stale producer KDocs and a count of bounds (53); a 32M
comment (55); the `MediaError` and download sentences say "media" (56, 57); `AGENTS.md`'s claim that python3 is pinned
in the gate (100); `refusalOf` shared as `GuardingProxy.refusal()` (102).

The closing blocks' departures from the review's suggestions, as each report details:

- **120**: the poster's memory is pinned by the filter order (`posterFilters`), a child's peak memory being unreadable
  from the JVM; a video's pixel refusal reuses `ImageTooManyPixelsException`; nothing serialises video renders.
- **122**: the pin walk already renewed on every line, unthrottled; the 200-line period was the tag and board walks',
  so the integration case slows tag lines, in the lease suite, which runs under a one-second lease.
- **124**: a test that a failing video fetches the page once was dropped, yt-dlp's `ignoreerrors=only_download`
  never reaching the fallback; the re-extraction test replaced it.
- **128**: every body `IOException` becomes `FetchUnreachableException`, a reset before the deadline included, which
  the use case already retried as `UNREACHABLE`.
- **130**: the Dependabot group is renamed `docker`; a correction notes that decision v's "one page fetch more" no
  longer holds.
- **132**: the bare `image` catalogue key was renamed with the `image_*` keys.
- **134**: two code fixes the real check found, each a tier-2 question: the playlist report (AF) and the decoder's
  threads (AH).
- **140**: a request Jetty refuses gets Jetty's status (`400`, `431` past the head bound) where the hand-rolled proxy
  closed unanswered, an `ftp` target `502` from Jetty's client, an origin closing unanswered `502`; the origin gets
  no `Via` or `Forwarded`, which Jetty adds by default.

## Tier-2 questions

- Block 10: keep or drop the old tables, answered "reco ok, supprime" by the operator.
- Block 40: the split, answered A by the operator.
- Block 53: the split, answered "a" by the operator; the literal reading of "the AVIF answers 415", a TIFF included,
  accepted by the lead.
- Blocks 54, 56, 80, 90, 100 and 102: their splits, answered by the lead as listed above.
- Block 103: the protocol filter came as the lead's instruction of 2026-10-03.
- Block 104: the stale-directory sweep, the lead's answer of 2026-10-03.
- Blocks 20, 30, 42, 45, 50, 55, 57, 58, 59, 60, 70, 75, 85, 92, 95 and 110: none.
- The holistic review's hover finding (AE): the operator chose the animated rendition, "AF a AE a", 2026-10-04.
  Block 136.
- Block 134, the playlist report (AF): fix it in block 134, "AF a AE a", the operator, 2026-10-04.
- Block 134, the decoder's threads (AH): `-threads 2` in block 134, "recos ok", the operator, 2026-10-04.
- Block 134, the credentials in the request log, proposed for the backlog as tier 3: fixed in this lot by block 138
  instead, the operator's decision relayed by the lead on 2026-10-04.
- Blocks 120 to 132: none.
- Block 140, the proxy on Jetty (AI): the operator found the hand-rolled `GuardingProxy` hard to read and maintain
  for security-critical code; of Jetty 12.1, LittleProxy, a coroutine rewrite and Stripe's Smokescreen, the operator
  took the recommendation, Jetty, "reco ok", 2026-10-04.

## The operator's decisions of 2026-10-03

- **A check's ability to fail is proven by permanent suite tests, never by one-off mutation runs.** Taken during block
  95, where the permission classifier refused the mutation runs. Blocks 45, 56, 57, 75, 90 and 92 had shown their
  checks red by one-off mutations before it.
- **"chaque nom de test doit être autonome pour rendre le refactoring faisable"**: no test name leans on a sibling.
- **One fresh teammate per block**: no teammate chains blocks, a split's included. Block 95 was finished by a second
  teammate when its first one's session ended.

## For the holistic review

- **Two inline detekt suppressions were added**: `RowMergedOutsideTransaction` in block 50
  (`UserDataImportRunner.kt`), and `TooManyFunctions` on `FilesystemMediaStore` in block 60, when `openStaged` took it
  to 12 functions. Each gives its reason inline. (Corrected: the new `RowMergedOutsideTransaction` suppression is in
  `SetPinMedia.kt`; the one in `UserDataImportRunner.kt` predates the lot and only changed expression, as the holistic
  review found.)

## What is not validated

- No real site was extracted: every page the suites serve is local, a `<video src>`, an HLS playlist or JSON-LD. No
  site makes the generic extractor set `is_live`, so the live refusal is asserted on written JSON alone (103).
  (Corrected: block 134 extracted a YouTube Short and a plain `<video>` page through the running API, "The real
  check". No site with its own extractor other than YouTube was extracted, Vimeo asking for a login; the live refusal
  is still asserted on written JSON alone.)
- `--downloader native` has no test of its own; the HLS tests pass with it (103).
- No browser but Firefox was driven; no phone width, no real touch device; H.265 never failed `canPlayType` there
  (70, 75). An undecodable video hovered in the grid was not driven (75). The download link was not tried against the
  real API (70); a real file picker's `accept` filtering and an `.mkv` with an empty browser type against the real API
  were not either (80).
- The poster `thumbnail` picks was not judged on a real clip, and the time to render a 50 MiB, 120-second video was
  not measured; nor was `-count_packets`'s cost on one (40, 60). `+faststart`'s `moov` placement is not asserted (45).
  (Corrected: the poster's memory was measured by block 134, "The real check"; time is still not, and neither is an
  archived MP4's `+faststart`, ffprobe not reporting where `moov` sits (120).)
- Block 57's two sentences were not read headless; the French sentences are read in a browser only for
  `NO_MEDIA_FOUND` (110), the parity test holding the rest (85).
- The proxy wiring tests of block 95 were never seen red against a fetcher that bypasses the proxy, only at
  compilation.
- Dependabot opening a recompiled `requirements.txt` pull request was not seen (100); it runs on `main` alone.
- The extractor in the production build: injected by `DownloadPinMedia` since block 110, but `dagger call smoke` was not
  run on it, and nothing read its log for an extraction. (Corrected: block 134 ran `dagger call smoke` and two
  extractions through the running image.)
- The new packages' CVEs were not triaged; `security/vex.openvex.json` is unchanged (specification section 7).
- Deno's network access rests on Deno's default-deny permissions as yt-dlp invokes it; nothing asserts it (holistic
  review).
- An import of real large videos under the production one-minute lease; the suite proves the renewal with slow tag
  lines under a one-second lease (122).
- A direct download from a real remote origin that stalls; the tests use loopback origins through the real proxy
  (128).
- The TIFF detail end to end against a running server (126).
- Dependabot's grouping of three directories in one entry, which runs on GitHub alone (130).
- The fallback's download link against the real API and in Chrome or Safari; the square box at phone width; the
  player's key across a real replacement (132).
- A video rendition miss still has no single flight: a grid's first load draws every video tile's poster at once, about
  330 MB each for a 4K video at MEDIUM. And `media.max_pixels`, at its 50 MP default, refuses no video (8K is 33 MP).
  Filed in the backlog.
- LARGE (960) is asked for by no client; a 4K poster at that size still peaks at 747 MB (134).
- Neither proxy has a test of its connect timeout (140). The proxy on Jetty met real sites in "The real check".

## The holistic review

Not run yet: it reads the top of this stack at the head of Wrap, and the closing block records its findings here.
(Corrected: run on 2026-10-03 over `lot/0.44.0-knip-and-biome-keep-the-clients-clean..origin/feat/a-page-address-yields-its-video`
(`ed2ab5e6`), report `.reviews/the-pin-holds-a-video-holistic.md`: 0 CRITICAL, 4 MAJOR, 28 MINOR. Every finding was
fixed inside the lot except the grid's hover, which goes to block 136; none was refused or filed. Each, in the review's
order, with its exit:)

- MAJOR, the poster holding 100 full-resolution frames: block 120, frames scaled first and `media.max_pixels` applied to
  a video; the decoder's remaining cost bounded by block 134. The single flight it also named is filed in the backlog.
- MAJOR, the import's 200-line lease period: block 122, `renewLeaseIfDue` on every line, the key deleted.
- MAJOR, the second yt-dlp run's unfiltered `-f`: block 124, `--load-info-json` on the first run's report.
- MAJOR, ADRs 0047 and 0048 overclaiming the security bound: block 130, `(Corrected: ...)` notes.
- The boot check guarding half an extraction: block 124, twice the timeout.
- The direct path with no wall clock: block 124, a deadline on each read; block 128, a watchdog for a body that sends
  nothing.
- The rendition copying the original into `tmp/`: block 120, a hard link.
- The import's processor timeout reported as `MEDIA_UNREADABLE`: block 122, retried.
- An archived MP4 trusted as is: block 120, kept only when already repackaged.
- `MEDIA_CODEC_UNSUPPORTED` echoing `tiffload`, and two details for `MEDIA_INVALID`: block 126.
- `ByteRangeResponse` opening the stream at build time: block 126, an opener.
- `FilesystemMediaStore`'s suppression comment: block 120.
- The handoff naming `UserDataImportRunner.kt` for the new suppression: block 134, corrected above.
- Text the lot made stale: block 126; "no media at this url" by block 124.
- The animated rendition with no consumer: the operator chose to hover on it (AE), block 136.
- Dependabot's two pull requests per Deno release: block 130.
- `ingestArchived`'s KDoc: block 120, the parameter renamed `keepArchivedMp4`.
- `ExportReadme`: block 126.
- The contract's descriptions saying "image": block 126.
- Living text saying "image": block 130.
- `AGENTS.md`'s `image` and `smoke` rows: block 130.
- `api/AGENTS.md`'s recompile instruction: block 130.
- `agents/workflow.md`'s generated list: block 130.
- The specification's correction chains: block 130, a reading guide at the head of section 5. The workflow rule the
  review proposed (a split adds its block section once) is for Improve.
- ADR 0049's evidence and the script's usage: block 130.
- Comments citing bare decision letters: blocks 120 and 124.
- Catalogue keys naming "image": block 132.
- A video with null dimensions in an `<img>`: block 132.
- `VideoMedia` keyed by its address: block 132.
- `413 MEDIA_TOO_LARGE` and `415 UNSUPPORTED_MEDIA_TYPE` on the general sentence: block 132.
- One refusal, two wordings: block 132.
- The download link naming the file `media`: block 132.

## The backlog

**Video support** is deleted in block 110. The other items of the specification's section 6 keep the exits it states.
Nothing was filed during the lot. (Corrected: block 134 files one item, a video rendition miss with no single flight,
`P2`. The credentials in the request log are not filed: block 138 fixes them.)

## The lot's counts

Fix-backs 3, listed above, none a cascaded rebase as far as the reports show. Cascaded rebases, the runs they
re-triggered and the operator's reading of the bodies: filled in by the closing block. (Corrected, for the stack up to
block 134:)

- **Fix-backs: 7**, each a block or a commit stacked above what it fixes. Block 58 from 57; the `rtmp`, `rtsp` and
  `mms` bypass in 103; the stale yt-dlp directory sweep in 104; block 128's stalled download, from 124's deadline, and
  its flaky proxy test, from 90; block 134's playlist report, from 124, and its decoder threads, from 120.
- **Cascaded rebases: none.**
- **Runs re-triggered: 2**, both reruns rather than cascades: #287, an engine image pull failure, and #295, the flaky
  proxy test block 128 then fixed.
- **The operator's reading of the bodies: no remark.**
- (Corrected, block 140: the operator's review of the stack asked for one change, the proxy on Jetty, stacked above
  as block 140 rather than fixed back into 90 and 92; no cascaded rebase.)

## Next step

Wrap: the holistic review over `git diff lot/0.44.0-knip-and-biome-keep-the-clients-clean..origin/feat/a-page-address-yields-its-video`,
then the closing block, then the operator's review of the stack, and the tag `lot/0.45.0-the-pin-holds-a-video` once
it merges. (Corrected: the review and blocks 120 to 134 are done. Next, block 136, the grid hovers on the animated
rendition, and block 138, the request log redacts credentials, each stacked above. Then the operator's review of the
whole stack, the merge, and the tag `lot/0.45.0-the-pin-holds-a-video`.) (Corrected: block 140, the proxy on Jetty,
is stacked above 138; then the operator's review resumes.)
