# 0051. Duplicates are found by frame hashes in bands

Status: Accepted
Date: 2026-10-05
Specification: `docs/specs/2026-10-05-the-pin-knows-its-duplicates.md`, decisions A and D to L.
Written in block 10.

## Context

A user pins the same picture twice, resized, recompressed or converted, and the SHA-256 the media already carries
only finds a copy to the byte. Finding a near copy needs a perceptual hash, compared by Hamming distance, and a
B-tree orders by value: two hashes a few bits apart can sit at both ends of it, so no ordinary index finds them.

A video or an animated image is not one picture. Meta's vPDQ hashes a subset of a video's frames with PDQ, one per
second in its example, and compares two media as bags of unique frame hashes, by the share of each one's frames found
in the other (`facebook/ThreatExchange`, `vpdq/README.md`, 2026-10-05). PDQ is a 256-bit hash with a quality score;
Meta recommends a match at a distance of 31 or less and discarding a hash whose quality is 49 or less
(`pdq/README.md`). No PDQ artefact is on Maven Central (`search.maven.org`, query `pdq`, no result, 2026-10-05), and
the Java reference is a `0.0.1-SNAPSHOT` (`pdq/java/pom.xml`).

Postgres with pgvector indexes a `bit` column by Hamming distance with HNSW (`bit_hamming_ops`), but the search is
approximate, and the store is SQLite.

## Decision

1. **A media is a bag of PDQ frame hashes**: one frame for a still image, one per whole second for an animated image
   or a video, at least four when it has that many. A frame whose quality is 49 or less is dropped.
2. **PDQ is written in Kotlin in this repository**, held to Meta's two acceptance conditions: the reference's exact
   hash on the same pixels, and within 10 bits through our own decoding.
3. **Two media are compared only at the same motion level**, still against still, and animated image or video
   against either. They are a pair when 80 % or more of the unique frames of either find a frame of the other within
   31 bits: a duplicate both ways, an excerpt one way. *(Corrected on 2026-10-05 in block 20: a pair is 80 % both
   ways. A frame a quarter second off its source's whole seconds measured 32 to 36 bits from its nearest source
   frame, so excerpts leave this decision for the backlog; specification, decision E.)*
4. **The lookup is exact multi-index hashing.** A hash is four 64-bit columns, and sixteen expression indexes each
   index one 16-bit band. Two hashes within 31 bits share a band within 1 bit, by pigeonhole, so a lookup asks
   seventeen values per band, 272 in all, and checks the true distance of what comes back. The table carries the
   hash and nothing derived from it.
5. **The search runs in the worker after ingestion, never on a request.** A request reads a table of candidate pairs.
6. **A media records the version of the algorithm that hashed it.** Raising the version in the code hashes every
   media again, and compares only hashes of the same version. A media that cannot be decoded is stamped with no
   frames.
7. **A pair belongs to two pins**, with its relation *(corrected: no relation, decision 3)* and whether the user rejected it. It is shown only while both
   pins are active. Rows left by a deleted media or pin are swept, not deleted where the media or pin is, and carry
   no foreign key constraint.
8. **Merging keeps one pin whole**: its media, description and sources stay, it gains the absorbed pins' boards and
   tags, and fills only a blank description and a missing page address. The absorbed pins go to the recycle bin;
   their pairs are not carried over.
9. **The surface is one flag and three operations**: `hasPendingDuplicates` on every pin, the list of a pin's
   duplicates, the rejection of one, and the merge, all or nothing. A pair that does not exist, or is hidden,
   answers `DUPLICATE_DOES_NOT_EXIST`.

   **Fails if** a candidate media is found other than by the band query, or a request computes a hash.

## Consequences

- **The lookup's cost still grows with the data**, divided by about 240 against a full scan for uniformly spread
  hashes (272 probes, each matching one row in 65 536); the specification review measured 410 candidates over
  100 000 random rows. Postgres can keep the same indexes, or replace them with one HNSW index over the same
  columns and accept an approximate search.
- **A rotated or mirrored copy is not found**: PDQ is not invariant to either, and hashing the eight orientations
  is not done.
- **A duplicate is known some time after the pin is written**, the worker's delay, so nothing warns before the pin
  is written, which the backlog item asked for.
- **The frame reader is not a decoder** in the sense of ADR 0050, decision 5: it reads a raster a capped child
  process wrote, and refuses one past the 512-pixel bound `MediaLimits` holds.
