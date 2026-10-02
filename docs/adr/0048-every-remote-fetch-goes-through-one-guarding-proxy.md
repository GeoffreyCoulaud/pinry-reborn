# 0048. Every remote fetch goes through one guarding proxy

Status: Accepted
Date: 2026-10-02
Specification: `docs/specs/2026-10-02-the-pin-holds-a-video.md`, decisions E2, F1, G1, H1, P1.
Written in block 10.

## Context

A video seen on a page is rarely a file: sites cut it into HLS or DASH segments, often with the audio apart, and the
player shows a `blob:` address. yt-dlp extracts it from hundreds of sites, reassembling the segments without
re-encoding. It makes its own requests, to the page, its redirects and the CDN, none of which pass the API's SSRF
guard, `AddressPolicy`, which lives in `HttpImageFetcher`.

That guard has a hole of its own: it resolves the host and checks the address, then the JDK client resolves the host
again to connect. A resolver answering a public address, then a private one, passes it.

## Decision

1. **A page address goes to yt-dlp, a file address to the direct fetcher**, decided by the worker from the
   response's `Content-Type`. The client sends one kind of request.

2. **One local proxy carries every remote connection of the worker.** Bound to the loopback, it resolves each host
   once, checks the address with `AddressPolicy`, and connects to that address; one request per connection. yt-dlp
   is given it with `--proxy`, the direct fetcher with a `ProxySelector`, and neither checks an address itself.
   Written with the JDK's `ServerSocket` and virtual threads: the fetch module depends on no framework.

   **Fails if** any connection of either path reaches a private address under `AddressPolicy.Standard`, or the
   proxy resolves a host twice for one connection.

3. **yt-dlp is pinned by uv and raised by Dependabot**: `api/tools/yt-dlp/pyproject.toml` and `uv.lock`, installed
   with `uv sync --frozen` in the API's image and the gate's container, under Dependabot's `uv` ecosystem, weekly.
   It never updates itself: the code that runs is the code the gate ran.

## Consequences

- **yt-dlp's freshness follows the releases.** Sites change weekly; an image a few months old fails on some of them,
  and the failure reads as a page with no video. A key pointing at one's own binary is the next step if it matters.
- **Python joins the API's image**, as the interpreter of one tool.
- **`media.download.allow_private_addresses` now opens both paths at once**, being read by the proxy alone.
