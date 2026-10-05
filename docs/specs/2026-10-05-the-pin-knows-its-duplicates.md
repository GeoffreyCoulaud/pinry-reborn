# The pin knows its duplicates

Date: 2026-10-05
Status: Accepted by the operator on 2026-10-05. One specification review ran, `.reviews/the-pin-knows-its-duplicates-spec.md`, its 1 CRITICAL,
8 MAJOR and 17 MINOR closed in this document. Frozen when the lot's last block merges.
Lot: `0.48.0`.
Branches: one stack: 10 `feat/a-media-yields-its-frames`, 20 `feat/pdq-hashes-a-frame` on 10,
30 `feat/frame-hashes-are-stored-in-bands` on 20, 40 `feat/the-worker-finds-duplicates` on 30,
50 `feat/the-api-serves-duplicates` on 40, 60 `feat/pins-merge` on 50, 70 `feat/the-dialog-lists-duplicates` on 60,
80 `feat/the-dialog-merges-a-group` on 70.
ADR: `docs/adr/0051-duplicates-are-found-by-frame-hashes-in-bands.md`, written in block 10, records decisions A, D
to L. The routes follow `docs/adr/0039-a-batch-route-is-all-or-nothing.md`,
`docs/adr/0042-the-presentation-owns-the-refusal-codes.md` and `docs/adr/0044-a-response-code-declares-its-set.md`.

## 1. Goal

A pin whose media looks like another pin of the same user says so: a marker on its tile, and in its dialog the list
of its likely duplicates, from which the user merges a group into one pin or dismisses a false match. Closes the
backlog item "Perceptual `ImageHash` (pHash)", whose warning before the pin is written is left as a limit (ADR 0051,
consequences).

## 2. What exists today

- A pin has at most one media (`uq_media_pin_id`, `dbmigration/1.28.sql`). `media.content_hash` is a SHA-256,
  indexed by `ix_media_content_hash`, read by the archive import (`UserDataImportRunner.digested`) and the export.
- A media is still when `animated` is false; an animated image has `animated` true and an `image/` type; a video has
  `animated` true and a `video/` type (`Media.isVideo`, `MediaIngestion`). A video lasts at most 120 seconds
  (`docs/adr/0047-a-video-is-repackaged-never-re-encoded.md`).
- A media becomes final in three places: `SetPinMedia.set` (upload, `mediaRepository.save` in its own transaction),
  `DownloadPinMedia.download` (address, inside `transactionRunner.inTransaction`), and
  `UserDataImportRunner.createPin` (inside the fenced `advance`). Replacing a media deletes its row and inserts one
  with a new id (`EbeanMediaRepository.saveWithin`).
- Decoders run as child processes through `ProcessRunner`, under a timeout and `prlimit`
  (`docs/adr/0050-a-medias-limits-hold-in-four-layers.md`, decision 4). `ProcessRunner` reads standard output as a
  string, so a decoder writes its product to a file beside its input. Nothing reads pixels in the JVM. The render
  semaphore of ADR 0050, decision 3, is in `GetPinMediaRendition`, on the API's side; the worker is bounded by its
  worker count.
- Task kinds are `object`s with a `KIND` and `MAX_ATTEMPTS` (`PinDownloadTask`); a `TaskHandler` bean is found by
  `TaskHandlerRegistry`; `TaskContext.renewLease` keeps a long task's claim (`UserDataExportTaskHandler`).
  `NewTask.dedupKey` returns the live task, pending or running, instead of inserting a second.
  `GarbageCollectionLifecycle` runs its sweeps at startup, then every `garbage-collection.interval` (`P1D` by
  default), each in its own `try`.
- Nothing cascades: every foreign key is `on delete restrict`, and `foreign_keys` is not enabled on the datasource.
  Media rows are deleted by hand in five places: `PinRecycleBin` (twice), `DeletePinMedia`, `AccountDeletionCleaner`
  and the replace above.
- `raw(` takes only a plain string literal of `RawSqlOutsideInventory.INVENTORY`, each with its reason. Ebean
  expands a collection bound to `?1` in a raw expression, by substring replacement (`RawExpressionBuilder`,
  `ebean-orm/ebean` `master`, 2026-10-05), so `?1` would also rewrite `?10`: one raw expression carries one numbered
  parameter. An expression index is `@Index(definition = ...)` alone (`api/AGENTS.md`).
- `PinRepositoryContentHashTest` reads a query's plan with `explain query plan` and asserts the index it uses.
- A batch route is all or nothing (ADR 0039); a response declares every code it can return (ADR 0042, decision 3).
  The contract is `22.1.0`.
- In the web application, `PinGrid` opens a pin found among the loaded pages only (`openedId`), and derives the
  dialog's previous and next from them; the tile's top corners hold the selection check and the video badge.

## 3. Decisions

Each is the operator's answer of 2026-10-05 in Discuss, the letter of the question in brackets.

**A. The search runs in the worker, after ingestion** (A, E). Nothing is computed on a request. A pin is written
first and learns its duplicates once the worker has hashed its media.

**B. A duplicate is shown, then merged by group** (B, L). The dialog lists the pin's candidates. One choice, *to
keep*, covers the open pin and every pending candidate; each candidate has an *include* check, checked by default.
*Merge* acts on the kept pin and the included candidates in one call. The tile carries one marker, "has duplicates",
while its pin has a pending candidate; only the dialog names the relation *(corrected: there is no relation to
name, decision E)*. A pin opened from the list has no previous
or next: those belong to the grid.

**C. A false match is rejected per pair, remembered and reversible** (C, D). *Not a duplicate* hides the candidate
from the pending list; rejected candidates stay listed, folded, each with *restore*.

**D. The lookup is exact multi-index hashing on four 64-bit columns** (E). Sixteen expression indexes, one per
16-bit band, `((hash_k >> s) & 65535)` with `k` in 0..3 and `s` in 48, 32, 16, 0. The query ORs sixteen raw
expressions, one per band, each `<band> in (?1)` bound to seventeen values: the band's value and its sixteen one-bit
neighbours. The sixteen literals join `INVENTORY` under one reason. The index definitions read the band expressions
from one Kotlin object, and a test holds each literal to the object's expression. The true distance is checked in
Kotlin with `java.lang.Long.bitCount`. The specification review measured the plan on SQLite 3.53.4 over 100 000
random rows: `MULTI-INDEX OR` over the sixteen indexes, no `SCAN`, 410 candidate rows per lookup.

**E. A media is a bag of frames, after vPDQ** (G, I). Two media are a pair when 80 % or more of the unique frames of
either find a frame of the other within 31 bits. Both ways is `DUPLICATE`; one way only makes the media whose frames
were found an excerpt of the other. *(Corrected on 2026-10-05 in block 20: a pair is 80 % both ways, always a
duplicate, and excerpts leave the lot. Block 20 measured a frame a quarter second off its source's whole seconds at
32 to 36 bits from its nearest source frame, so an excerpt was found only when it started on a whole second. The
operator moved excerpt detection to the backlog as a feature of its own.)*

**F. Two motion levels** (F). A media is compared only with media whose `animated` is equal: still with still,
animated image or video with animated image or video.

**G. PDQ is written in Kotlin** (H), following Meta's `hashing.pdf`, and held to both of Meta's acceptance
conditions (`pdq/README.md`, "Writing Your Own Hashing"): on the same pixels, exactly the reference's hash; through
our own decoding, within 10 bits of it for an image of quality 80 or more. The thresholds are Meta's recommended
ones, 31 and 49, as constants: the deployment model gives no reason to tune them.

**H. Merge** (J, L, and N on carrying pairs and `sourceMediaUrl`, asked while the spec was written). The kept pin keeps its media, its description, `sourceContextUrl` and `sourceMediaUrl`. It
gains every active board and every tag of the absorbed pins. A blank description and a null `sourceContextUrl` are
filled from the first absorbed pin, in the request's order, that has one. `sourceMediaUrl` is never filled: it names
where the kept media came from. The absorbed pins go to the recycle bin at one instant. Pending pairs of an absorbed
pin are not carried to the kept pin: a pair says two media look alike, and the kept media was not measured against
the absorbed pin's partners. A recycled pin's pairs are hidden (decision J) and come back with it.

**I. A media records the algorithm's version** (M, and the operator's question of how to bump it). `media` gains
`fingerprint_version`, null until hashed; `FINGERPRINT_VERSION = 1` in the code.
- One task kind, `media.fingerprint`, `MAX_ATTEMPTS` 3, with one dedup key, drains every media whose version is null
  or below the constant, newest first, reading again until none is left, and renewing its lease after each media.
- It is enqueued after `SetPinMedia`'s save, inside `DownloadPinMedia`'s transaction, once after an import run, and
  by `GarbageCollectionLifecycle` at startup and on each sweep, which also covers an enqueue that was lost.
- A media that cannot be decoded is logged and stamped with the version and no frames, so it never holds the drain.
- Hashing a media again deletes its frames and its pin's pending pairs, keeps its pin's rejected pairs, and compares
  only with media at the current version.

**J. Pairs belong to pins, and the search includes the recycle bin** (K). A pair row holds the two pin ids in
ascending order, its relation and `rejected_at` *(corrected: no relation, decision E)*. A pair is shown, and counts for the marker, only when both pins are
active; a hidden pair does not exist for the API. Restoring a pin shows its pairs again with nothing recomputed.
Frame rows whose media is gone and pair rows whose pin is gone are deleted by one sweep in
`GarbageCollectionLifecycle`, not at the five deletion sites; until then the joins of every read drop them. Both
tables declare their relations with `@DbForeignKey(noConstraint = true)`, so turning `foreign_keys` on can never
refuse a pin's or a media's delete because of them.

**K. Sampling** (K). A frame is taken at each whole second of the media's timeline: a video's, or an animated
image's from libvips' `delay`. A media shorter than four seconds also gives frames evenly spaced over its length, up
to four in all, the whole-second ones kept so two media still sample the same instants. An animated image whose
delays are all zero gives four frames evenly spaced over its frames, all of them if it has four or fewer. A frame is
decoded at most 512 pixels on its longer side, a bound held in `MediaLimits`, its alpha flattened. Identical hashes of
one media are kept once. Sampling runs in the worker, under its worker count and not under the render semaphore,
which guards requests.

**L. The contract** (B, C, L). `relation` reads from the path's pin and is an `x-extensible-enum` (ADR 0044):
`DUPLICATE`, `EXCERPT` (the candidate is an excerpt of this pin), `SOURCE` (this pin is an excerpt of the
candidate). *(Corrected: `relation` is dropped, decision E; an item is `{pin, rejected}`.)* `pin` is the
candidate's `PinOutputDto`, so the dialog can show and open a pin the grid has not loaded.
Block 50 makes it `22.2.0`, block 60 `22.3.0`.

| Operation | Body | Answer | Refusals |
|---|---|---|---|
| `PinOutputDto.hasPendingDuplicates` | | required boolean on every pin | |
| `GET /api/v1/pins/{pinId}/duplicates` | | 200 `{duplicates: [{pin, relation, rejected}]}`, empty for a recycled pin | 403 `PIN_INSUFFICIENT_PERMISSIONS`, 404 `PIN_DOES_NOT_EXIST` |
| `PUT /api/v1/pins/{pinId}/duplicates/{otherPinId}` | `{rejected: boolean}` | 200 the same item | 403 `PIN_INSUFFICIENT_PERMISSIONS`, 404 `PIN_DOES_NOT_EXIST`, 404 `DUPLICATE_DOES_NOT_EXIST` (no pair, or a hidden one) |
| `POST /api/v1/pins/merges` | `{keptPinId, absorbedPinIds}` | 200 the kept pin's `PinOutputDto` | 400 `VALIDATION_ERROR` (empty list, a repeated id, the kept pin among the absorbed), 403 `PIN_INSUFFICIENT_PERMISSIONS`, 404 `PIN_DOES_NOT_EXIST`, 409 `PIN_ALREADY_SOFT_DELETED` |

`DUPLICATE_DOES_NOT_EXIST` is new, in `ProblemCode` and `ErrorCode`. The merge resolves every pin before its first
write and writes nothing on a refusal (ADR 0039).

## 4. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/a-media-yields-its-frames` | Decision K. |
| 20 | `feat/pdq-hashes-a-frame` | Decision G. |
| 30 | `feat/frame-hashes-are-stored-in-bands` | Decisions D, I's column and J's tables. |
| 40 | `feat/the-worker-finds-duplicates` | Decisions A, E, F, I and J's sweep. |
| 50 | `feat/the-api-serves-duplicates` | Decisions C and L, but the merge. |
| 60 | `feat/pins-merge` | Decision H. |
| 70 | `feat/the-dialog-lists-duplicates` | Decisions B's marker and list, C. |
| 80 | `feat/the-dialog-merges-a-group` | Decision B's merge. |

Blocks 30 and 40 are the likeliest to pass a bound; one that does splits at a number between its own and the next.

### Block 10

- A port `FrameSampler` in `api-domain` that hands the caller one luminance frame at a time, so a video's 120
  frames are never held at once. Adapters in `api-imaging-vips` and `api-video-ffmpeg` write PPM files beside the
  input; one PPM reader in `api-utilities`, which both already use for `ProcessRunner`. The reader refuses a header
  past the 512-pixel bound: it reads a raster a capped child wrote and decodes nothing. Its consumer is block 40.
- A video of 10 seconds made with `ffmpeg -f lavfi -i testsrc2` gives 10 frames; one of 2 seconds gives 4. A
  three-frame GIF gives 3; a GIF of 8 frames at 1 second each gives 8; one of 40 frames at 100 ms each gives 4; one of
  10 frames whose delays are all zero gives 4.
- A PNG with transparency decodes with no failure. A frame is at most 512 pixels on its longer side.
- Every decoder runs through `ProcessRunner`; a decoder that fails is a failure the caller sees.
- Carries this specification and ADR 0051.

### Block 20

- `PdqHasher.hash(luma)` returns a 256-bit hash and a quality, in `api-domain` beside `MediaLimits`: pure
  arithmetic, no I/O. Its consumer is block 40.
- **Exact, on the same pixels.** A few small RGB images, generated with a fixed seed, and their hashes from
  Meta's C++ reference through `pdqhash` (PyPI, which wraps it), join the test resources with the command that
  produced them. `PdqHasher` gives exactly those hashes. Installing `pdqhash` is a one-off for the fixtures, not a
  dependency of the build; a sandbox that refuses it is a blocker.
- **Close, through our decoding.** Meta's eight images `pdq/data/reg-test-input/dih/bridge-*.jpg` join the test
  resources of `api-imaging-vips` with Meta's BSD notice, and their hashes from `pdq/cpp/output-regtest/out` as an
  expected table. Decoded by block 10's sampler, each is within 10 bits of the reference's, quality 80 or more.
- The rotations are not matches: `bridge-1-original` and `bridge-3-rotate-180` are more than 31 bits apart.
- A uniform frame's quality is 49 or less.
- Measured and reported, not asserted: the share of a 3-second cut's frames matched in its 10-second source when the
  cut starts on a whole second and half a second later, on `ffmpeg -f lavfi -i mandelbrot`. A half-second offset
  that falls under 80 % is a limit the pull request records. *(Corrected: the measurement moved excerpts to the
  backlog, decision E; the pull request reports it as that item's evidence.)*

### Block 30

- Migration `1.31`: `media.fingerprint_version`, the frame hash table `(media_id, hash_0..hash_3)` with its sixteen
  expression indexes, and the pair table with a unique index on its two pin ids. That index joins
  `UniqueConstraintOutcomeTest` as "no translation, deliberately": one drain runs at a time (one dedup key), it
  inserts only pairs its pin does not hold, and a `PUT` updates an existing row.
- The sixteen `raw(` literals join `INVENTORY`, and a test holds each to the band object's expression.
- A repository test, bound through Ebean: a hash at 31 bits from a stored one is found, spread across every band and
  concentrated in one; a hash at 32 bits concentrated in two bands is returned by the index and refused by the
  distance check.
- A plan test: the lookup's plan names the sixteen band indexes and holds no `SCAN` of the frame table.
- A measurement over 100 000 random frames, in the pull request with its command: candidates per lookup under one
  in 100 rows, and the lookup's time against a full scan's.
- Its consumer is block 40.

### Block 40

- `FingerprintMedia` drains outdated media; `MediaFingerprintTask`, its handler, the enqueues of decision I, and the
  orphan sweep.
- Same author, same motion level, other pin: a still image and its re-encoded copy at half size make a `DUPLICATE`,
  and the still stores one frame. The same picture pinned by another user makes nothing. A still and a video whose
  every frame is that still make nothing.
- A 10-second `mandelbrot` video and its 3-second cut starting on a whole second make the cut an excerpt; the video
  and itself re-encoded make a `DUPLICATE`. *(Corrected, decision E: the video and itself re-encoded make a pair,
  and its 3-second cut makes none.)*
- A black video's frames are all dropped; it gets its version and makes no pair.
- A corrupt media between two good ones: both good ones are hashed, the corrupt one is stamped with no frames.
- Raising the version: after the first media of a pair is hashed again and before the second, the pending pair is
  gone and a rejected pair is still there.
- A media replaced on its pin: the new media is hashed, and the sweep deletes the old one's frames.

### Block 50

- `hasPendingDuplicates` is true for both pins of a pending pair, false once rejected, false once either is
  recycled. `PinResponses` asks for a page's flags in one repository call over the page's pin ids, as it asks
  `ResolvePinMediaState.statesFor`; a unit test with a fake counts one call.
- `GET` lists both relations from either side *(corrected: lists the pair from either side, decision E)*, and answers an empty list for a recycled pin; the refusals of
  decision L.
- `PUT` rejects and restores; a pair hidden by a recycled pin is 404 `DUPLICATE_DOES_NOT_EXIST`.
- `oasdiff changelog` against `main` lists the new field and the two operations; `info.version` is `22.2.0`.
- The web application's fixture `pin()` answers `hasPendingDuplicates: false`; its consumer is block 70.

### Block 60

- An integration test per refusal of decision L's merge, each leaving every pin as it was.
- A merge of three pins: boards and tags are the union, the blank description is filled from the first absorbed
  pin that has one, `sourceMediaUrl` is unchanged, both absorbed pins are recycled with one `softDeletedAt`, and the
  kept pin holds no pair it did not hold before.
- `info.version` is `22.3.0`.

### Block 70

- The marker in a bottom corner of the tile, with an accessible name, when `hasPendingDuplicates`.
- The dialog lists pending candidates with their thumbnail, dimensions or duration, and relation *(corrected: no
  relation, decision E)*; *Not a duplicate*
  folds one under "Rejected (n)", *Restore* brings it back. Opening a candidate shows it in the dialog whether or not
  the grid has loaded it, with no previous or next.
- Journeys "reject a duplicate" and "open a duplicate".
- Read headless before the push, two themes by a phone's and a desktop's width.

### Block 80

- The group form of decision B: a radio group *to keep* over the open pin and the pending candidates, an *include*
  check per candidate, *Merge (n)*. After the merge the dialog shows the kept pin, the absorbed pins leave the grid,
  and the boards are read again.
- Journey "merge a group of duplicates": four pins, the third kept, the fourth unchecked.
- Read headless before the push.
- Deletes the backlog item "Perceptual `ImageHash` (pHash)".

## 5. Adjacent backlog items

- **"Perceptual `ImageHash` (pHash)"**: closed by block 80.
- **"`foreign_keys` is off"** stays open: decision J keeps this lot's tables out of its way, and turning the pragma
  on still has the deletion sites it names to settle.
- **"Import follow-ons"** stays open: its merging of an archive's metadata onto an existing pin happens at import,
  where decision H merges two pins the user holds.
- **"Search matches by substring and tolerates no typo"** stays open: it waits on Postgres, which this lot does not
  need (ADR 0051, consequences).
- **"Advanced pin / tag / board management"** stays open: unscoped, and the merge is the one piece of it this lot
  needed.
- **"Visual understanding"** stays open: an embedding per pin is a separate engine and a separate opt-in.

## 6. Out of scope

- **A warning before the pin is written**, and a screen of every duplicate. Observed as no operation on
  `POST /api/v1/pins` changing in `oasdiff changelog`, and no new route in the web application.
- **Duplicates across users.** Observed in block 40's test of the same picture pinned by another user.
- **Rotated or mirrored copies** (ADR 0051, consequences). Observed as a still storing one frame in block 40.
- **Carrying pairs at a merge** (decision H). Observed in block 60's merge test.
- **Excerpts** *(added on 2026-10-05 in block 20, decision E)*. Observed in block 40's test of the 3-second cut,
  which makes no pair. Filed in the backlog under Features.
