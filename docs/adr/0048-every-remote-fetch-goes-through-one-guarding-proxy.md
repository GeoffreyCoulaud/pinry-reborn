# 0048. Every remote fetch goes through one guarding proxy

Status: Accepted
Date: 2026-10-02
Specification: `docs/specs/2026-10-02-the-pin-holds-a-video.md`, decisions E2, F1, G1, H1, M1, P1, T1, Y2, v.
Written in block 10.

## Context

A video seen on a page is rarely a file: sites cut it into HLS or DASH segments, often with the audio apart, and the
player shows a `blob:` address. yt-dlp extracts it from hundreds of sites, reassembling the segments without
re-encoding. It makes its own requests, to the page, its redirects and the CDN, none of which pass the API's SSRF
guard, `AddressPolicy`, which lives in `HttpImageFetcher`.

That guard has a hole of its own: it resolves the host and checks the address, then the JDK client resolves the host
again to connect. A resolver answering a public address, then a private one, passes it.

Behind a proxy, a refusal reaches neither client as such: the JDK reports a refused tunnel as
`IOException: Tunnel failed, got: 403`, and yt-dlp exits 1 for a refusing proxy as for a page with no video
(measured by the specification review, `.reviews/the-pin-holds-a-video-spec.md`).

## Decision

1. **A page address goes to yt-dlp, a file address to the direct fetcher**, decided by the worker from the
   response's `Content-Type`: `text/html` and `application/xhtml+xml` to yt-dlp, anything else to the direct path.
   The client sends one kind of request.

2. **Each download runs its own proxy, which carries every connection of that download.** Bound to the loopback, it
   resolves each host once, checks the address with `AddressPolicy`, connects to that address, one request per
   connection, and records what it refused and what it could not reach. yt-dlp is given it with `--proxy`, the
   direct fetcher with a `ProxySelector`, and neither checks an address itself. The record, not a status or a
   message, is what names `URL_NOT_ALLOWED` or `UNREACHABLE`. Written with the JDK's `ServerSocket` and virtual
   threads: the fetch module depends on no framework.

   **Fails if** any connection of either path reaches a private address under `AddressPolicy.Standard`, the proxy
   resolves a host twice for one connection, or a refused download ends with another reason than `URL_NOT_ALLOWED`.

3. **yt-dlp runs twice per page**: `--dump-single-json` first, which downloads nothing and from which the worker
   refuses a live stream, a duration or a size past the bounds, with an exact reason; then the download, with the
   format the first run chose. The format is chosen by a chain of `-f` selectors, H.264, then VP9, then AV1, then
   H.265, each with AAC, Opus or MP3. The worker bounds the run in time and its directory in bytes.
   (Corrected: every selector also takes a protocol filter, `https?`, `m3u8(_native)?` or `http_dash_segments`, so
   no format goes to an external program outside `--proxy`, and `--downloader native` keeps an HLS download in
   yt-dlp's own networking; the chain ends with `/b`, for a format that declares no codec, which ingestion judges,
   blocks 102 and 103. Since block 124 the second run fetches no page: it loads the first run's report, its
   `webpage_url` stripped, with `--load-info-json`, so the format it downloads is one the first run's filters chose.)

4. **yt-dlp is pinned by pip and raised by Dependabot**: `api/tools/yt-dlp/requirements.in` (`yt-dlp[default]`)
   compiled to a `requirements.txt` carrying every pin and hash, installed with `pip install --require-hashes` in a
   venv on the apt `python3`, in the API's image and the gate's container, under Dependabot's `pip` ecosystem,
   weekly. It never updates itself: the code that runs is the code the gate ran. uv would be one tool more for one
   package; the release's standalone binary is a file Dependabot cannot follow, and Ubuntu's package was five
   months old.

5. **Deno ships beside it**, copied from a pinned `denoland/deno:bin` image (95.8 MB): yt-dlp needs a JavaScript
   runtime and `yt-dlp-ejs` for YouTube, and enables `deno` by default.

## Consequences

- **yt-dlp's freshness follows the releases.** Sites change weekly; an image a few months old fails on some of them,
  and the failure reads as a page with no video. A key pointing at one's own binary is the next step if it matters.
- **Python and Deno join the API's image**, as the interpreters of one tool.
- **`media.download.allow_private_addresses` now opens both paths at once**, being read by the proxy alone.
