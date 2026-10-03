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
the holistic review.

## Current state

- **A pin's medium is a `Media`**, an image or a video: code, routes (`/pins/{pinId}/media`, `/me/media-downloads`),
  tables (`media`, `media_download`, migrations `1.28` and `1.29__dropsFor_1.28`), the `media.*` keys and the export's
  `media/` directory at format version 2 (ADR 0049). The contract is at `21.0.0`.
- **A video is at most 120 seconds and 50 MiB** (`media.max_video_seconds`, `media.max_video_bytes`), an image keeping
  `media.max_image_bytes`. The handshake publishes the three bounds and the ten upload types in `mediaTypes`.
- **`api-video-ffmpeg` runs ffprobe and ffmpeg as processes**, with `-format_whitelist mov,matroska -protocol_whitelist
  file` before `-i`. A video is repackaged with `-c copy`, never re-encoded, into WebM or MP4 as its codecs choose, its
  stored type carrying an RFC 6381 `codecs` parameter built from the stream's extradata (ADR 0047).
- **One ingestion path**, `MediaIngestion`, stages, probes (libvips first, ffprobe when libvips cannot open the file or
  reads a format the enum lacks), repackages a video and promotes, for the upload, the download and the import. An
  imported MP4 is stored as the archive carries it; a WebM is repackaged, which keeps its bytes.
- **The original answers `Range`** with `206` and a quoted `ETag`. A video's still rendition is its poster drawn by
  libvips; `?animated=true` is its first three seconds as an animated WebP; both are cached like any rendition.
- **Two orphan sweeps**: an original with no row, and a staged file, or a yt-dlp run's directory, older than
  `garbage-collection.orphan_grace`, which a boot check keeps longer than `media.download.extraction_timeout`.
- **Every remote fetch goes through one `GuardingProxy` per download** (`api-fetch-http`), bound to the loopback, which
  resolves each host once, checks it with `AddressPolicy`, serves `CONNECT` and plain HTTP one request per connection,
  and keeps the record that names `URL_NOT_ALLOWED` or `UNREACHABLE` (ADR 0048).
- **A page address yields its video through yt-dlp.** The download reads the fetched response's `Content-Type`:
  `text/html` and `application/xhtml+xml` go to `PageMediaExtractor` (`api-fetch-ytdlp`), anything else, a missing
  header included, down the direct path. yt-dlp runs twice behind the proxy, a `--dump-single-json` that refuses a live
  stream, a duration or a size past the bounds, then the download of the chosen format, each run bounded in time and in
  bytes. Its refusals arrive as `NO_MEDIA_FOUND` and `TOO_LONG`; the proxy's as `URL_NOT_ALLOWED` and `UNREACHABLE`.
- **yt-dlp is pinned by hash** (`api/tools/yt-dlp/requirements.txt`, pip-compile's output) in a venv in both images,
  Deno copied from `denoland/deno:bin`, both raised by Dependabot.
- **The download renews its lease** on each read of the body and each heartbeat of an extraction, granted at most
  every third of `tasks.lease_duration`.
- **The web application plays a video**: a `<video controls>` in the pin's view with a poster, a fallback (poster,
  sentence, download link) where `canPlayType` answers `""` or the element errors; a play badge and a muted hover
  preview in the grid; uploads of a video judged by type and bytes; every sentence that named the medium says "media".

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
  origin fetches before the renewal and 1 after.
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
- Block 110: green at its tip, log `gate-110.log` in the teammate's scratchpad; budget 165 lines, 10 files against
  `feat/the-extractor-is-wired`. `ModeBMediaHostingIntegrationTest` pins the `<video src>` of a local page as a
  `video/webm; codecs=` media through the wired application and real yt-dlp, and a page with no video ends `FAILED`
  with `NO_MEDIA_FOUND`.
- Continuous integration green on #265 to #290 (`gh pr view <n> --json statusCheckRollup`, 2026-10-03); #291 was
  running when this file was written.
- Read headless in Firefox 156.0.1 over WebDriver BiDi at 1280x800, each against a throwaway stub API: blocks 70, 75,
  80, 85 and 110 (the readings each report details). Block 70's found one defect, fixed before its push.

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
  each yt-dlp run.
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

## Tier-2 questions

- Block 10: keep or drop the old tables, answered "reco ok, supprime" by the operator.
- Block 40: the split, answered A by the operator.
- Block 53: the split, answered "a" by the operator; the literal reading of "the AVIF answers 415", a TIFF included,
  accepted by the lead.
- Blocks 54, 56, 80, 90, 100 and 102: their splits, answered by the lead as listed above.
- Block 103: the protocol filter came as the lead's instruction of 2026-10-03.
- Block 104: the stale-directory sweep, the lead's answer of 2026-10-03.
- Blocks 20, 30, 42, 45, 50, 55, 57, 58, 59, 60, 70, 75, 85, 92, 95 and 110: none.

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
  to 12 functions. Each gives its reason inline.

## What is not validated

- No real site was extracted: every page the suites serve is local, a `<video src>`, an HLS playlist or JSON-LD. No
  site makes the generic extractor set `is_live`, so the live refusal is asserted on written JSON alone (103).
- `--downloader native` has no test of its own; the HLS tests pass with it (103).
- No browser but Firefox was driven; no phone width, no real touch device; H.265 never failed `canPlayType` there
  (70, 75). An undecodable video hovered in the grid was not driven (75). The download link was not tried against the
  real API (70); a real file picker's `accept` filtering and an `.mkv` with an empty browser type against the real API
  were not either (80).
- The poster `thumbnail` picks was not judged on a real clip, and the time to render a 50 MiB, 120-second video was
  not measured; nor was `-count_packets`'s cost on one (40, 60). `+faststart`'s `moov` placement is not asserted (45).
- Block 57's two sentences were not read headless; the French sentences are read in a browser only for
  `NO_MEDIA_FOUND` (110), the parity test holding the rest (85).
- The proxy wiring tests of block 95 were never seen red against a fetcher that bypasses the proxy, only at
  compilation.
- Dependabot opening a recompiled `requirements.txt` pull request was not seen (100); it runs on `main` alone.
- The extractor in the production build: injected by `DownloadPinMedia` since block 110, but `dagger call smoke` was not
  run on it, and nothing read its log for an extraction.
- The new packages' CVEs were not triaged; `security/vex.openvex.json` is unchanged (specification section 7).

## The holistic review

Not run yet: it reads the top of this stack at the head of Wrap, and the closing block records its findings here.

## The backlog

**Video support** is deleted in block 110. The other items of the specification's section 6 keep the exits it states.
Nothing was filed during the lot.

## The lot's counts

Fix-backs 3, listed above, none a cascaded rebase as far as the reports show. Cascaded rebases, the runs they
re-triggered and the operator's reading of the bodies: filled in by the closing block.

## Next step

Wrap: the holistic review over `git diff lot/0.44.0-knip-and-biome-keep-the-clients-clean..origin/feat/a-page-address-yields-its-video`,
then the closing block, then the operator's review of the stack, and the tag `lot/0.45.0-the-pin-holds-a-video` once
it merges.
