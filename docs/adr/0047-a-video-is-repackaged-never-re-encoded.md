# 0047. A video is repackaged, never re-encoded

Status: Accepted
Date: 2026-10-02
Specification: `docs/specs/2026-10-02-the-pin-holds-a-video.md`, decisions A, B1, D1, J1, K1, L1, M1, Q1, R1, S1.
Written in block 10.

## Context

A pin may hold a short video. Re-encoding would let any file in and play everywhere, at the cost of minutes of CPU
per video, a loss of quality, and a second hard problem the operator declined to take on. Videos come from the web,
already seen in a browser, so the files worth keeping are mostly playable as they are.

What browsers play was read from MDN's guides (`mdn/content`, `files/en-us/web/media/guides/formats/`, 2026-10-02),
Baseline and the browser compatibility data carrying no entry for a codec's playback. Chromium, Firefox and Safari
all play H.264, VP9, AAC, MP3 and Opus. AV1 plays on Safari only with a hardware decoder, H.265 on Chromium and
Firefox only with one or with the system's decoder. VP9 and Opus are documented in WebM for all three, in MP4 for
Firefox alone. WebM admits VP8, VP9 and AV1 with Vorbis or Opus, and nothing else.

ffmpeg reaches the API by one of three routes (Maven Central, 2026-10-02): its command line run as a process; JavaCV
1.5.14 (August 2026, ffmpeg 8.1), which loads it into the JVM; or a wrapper of the command line,
ffmpeg-cli-wrapper 0.9.2 (April 2026) or Jaffree 2024.08.29. The Ubuntu package adds 353 MB to the API's image,
measured in `eclipse-temurin:25-jre@sha256:bb036ed6…` (463 MB to 816 MB), LLVM and Mesa being most of it; the static
binaries of `mwader/static-ffmpeg:8.0` weigh 280 MB.

## Decision

1. **A video is repackaged with `-c copy` and never re-encoded.** Video codecs H.264, H.265, VP9, AV1; audio AAC,
   Opus, MP3 or none. Anything else is refused, naming the codec.

   **Fails if** a stored video is not bit for bit the source's tracks: `ffprobe` of the stored file reports another
   codec, profile or frame count than the upload.

2. **The stored container follows the codecs**: WebM when every track fits it, MP4 with `+faststart` otherwise. Each
   combination thereby lands in the container MDN documents best for it.

3. **ffprobe and ffmpeg read two demuxers only**, `mov` and `matroska`, and the `file` protocol only, with
   `-format_whitelist mov,matroska -protocol_whitelist file -nostdin` on every call. A playlist or a concatenation
   could otherwise read a file of the server into the output.

   **Fails if** an HLS playlist naming a local file is probed or repackaged instead of refused.

4. **ffmpeg runs as a process, installed from apt.** A crash on a hostile file ends the process and not the API, a
   timeout ends it by `destroy`, and its security fixes arrive with the base image Dependabot already raises. JavaCV
   would run that parser inside the API; a wrapper would add a dependency for an argument list. The 353 MB are
   accepted: the operator weighs maintenance over bytes.

5. **A video's renditions are its poster**, ffmpeg's `thumbnail` filter over the first frames, drawn by libvips.
   The original plays where an animation would.

6. **The client decides what it can play.** The stored `mimeType` carries the `codecs` parameter; the web
   application asks `canPlayType`, and falls back to the poster and a download link on an empty answer or an `error`
   event. The server never guesses the browser.

## Consequences

- **H.265 and AV1 do not play everywhere**, outside Baseline by design; the fallback is what such a viewer gets.
- **The `.avi`, `.flv`, `.ts`, `.ogv` and `.wmv` formats are refused**, and a format joins in one name.
- **The API image is 353 MB heavier.** A minimal ffmpeg compiled in the image would save most of it, at the price of
  owning its security fixes and an emulated arm64 compile on each release.
