# 0049. The pin's medium is a media

Status: Accepted
Date: 2026-10-02
Specification: `docs/specs/2026-10-02-the-pin-holds-a-video.md`, decisions C1, N1, ii, iii.
Written in block 10.

## Context

A pin held one `Image`. It may now hold a video, and every name built on "image" for the pin's medium would lie:
about 1400 identifiers in the API, four routes, two tables, the configuration keys, the export archive's directory.
Three use cases each staged, probed, promoted and built the storage key of an original, and each would have had to
learn the video. The project is in alpha and deployed nowhere (`git tag -l 'v*'` and `gh release list` both empty).

## Decision

1. **The pin's medium is a `Media`, an image or a video**, and every name for it follows: `Media`, `MediaStore`,
   `MediaDownload`, `MediaFormat`; the routes `/pins/{pinId}/media`, `/pins/{pinId}/media/status`,
   `/me/media-downloads`; the tables `media` and `media_download`; the keys `media.*` and the directory
   `/var/lib/pinry/media`. "Image" stays where it names an image: the libvips probe, the WebP transformer, MIME
   literals. The contract goes to `21.0.0`.

2. **One use case, `MediaIngestion`, is the only way an original enters storage**: the upload, the download and the
   import call it. It asks libvips first and ffprobe when libvips cannot read the file; the video probe refuses a
   stream with no duration or a single frame, so a still in a video container is never stored as a video.

   **Fails if** a storage key is built anywhere else, or an AVIF or HEIC is stored as `video/*`.

3. **The export archive carries `media/` and format version 2**; the import refuses version 1. The import stores a
   video as the archive carries it once probed, so a round trip keeps its bytes and its hash.

4. **The refusals name the medium**: the `IMAGE_*` codes become `MEDIA_*`, and the upload gains `MEDIA_TOO_LONG`
   (422) and `MEDIA_CODEC_UNSUPPORTED` (415); the download reasons gain `TOO_LONG`, `UNSUPPORTED_CODEC` and
   `NO_MEDIA_FOUND`, open as `reasonCode` already is. `MEDIA_TOO_LARGE` shares its name with an import issue kind,
   in another set.

## Consequences

- **A configuration, a volume or an archive written before this lot no longer fits**, which the alpha allows.
- **The rename is one block of about 1400 identifiers**, reviewed by replaying its script rather than by reading
  it.
