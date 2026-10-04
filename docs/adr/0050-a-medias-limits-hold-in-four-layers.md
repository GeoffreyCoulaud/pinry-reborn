# 0050. A media's limits hold in four layers

Status: Accepted
Date: 2026-10-04
Specification: `docs/specs/2026-10-04-a-medias-limits-hold-in-four-layers.md`.

## Context

The limits on a media were scattered: bytes and duration in `MediaIngestion`, pixels in `VipsImageProbe` and again in
`MediaIngestion`, nothing at render. Each guarded one case and none said which resource it protected. So a video's
pixels were held to the images' bound, a rendition miss started a decoder per request with no bound on their number,
an animated image was probed on its first frame and rendered on all of them, and libvips ran in the JVM with no time
limit. Each resource is paid at a different moment: disk at storage, in bytes; memory at decoding, in pixels held at
once; CPU at decoding, in pixels decoded.

## Decision

1. **Admission refuses what the instance does not host**: bytes, duration, format, and pixels per frame, a frame past
   that bound having no rendition at all. The client reads the same bounds from the handshake and refuses first.
2. **A render degrades what costs too much**, never refuses it: past the pixels a render may decode, an animated image
   keeps its first frame, a video's animated rendition becomes its poster, and its poster is drawn from one frame.
   A frame past its bound, stored before the bound was lowered, answers `MEDIA_RENDITION_UNAVAILABLE`; so does a
   decoder that fails, leaving a marker in the cache so the failure is not replayed. A rendition's cache key and ETag
   name what was rendered, not what was asked, so raising a bound renders anew.
3. **The sum of renders waits its turn** behind one semaphore, images and videos alike.
4. **Every decoder runs in a child process** under a timeout and a kernel-enforced address-space cap, so a cost the
   bounds misjudge fails one render and never the JVM.
5. **One pure class, `MediaLimits`, holds the bounds and decides.** Probes measure, callers ask it.

   **Fails if** a pixel or frame bound is compared anywhere but `MediaLimits`, or a decoder is called in the JVM. A
   byte cap enforced while streaming stays where the bytes flow.

## Consequences

- **A rendition can be poorer than asked**, silently: the response does not say it was degraded.
- **Each render pays a process start.** Its cost is in the specification's measurements.
- **The defaults are measured, not chosen**: each one names its measurement, and a new decoder or format is measured
  before it gets a bound.
