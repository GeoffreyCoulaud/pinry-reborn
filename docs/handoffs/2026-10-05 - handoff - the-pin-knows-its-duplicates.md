# Handoff: the pin knows its duplicates

Date: 2026-10-05
Tier: Spec. Specification `docs/specs/2026-10-05-the-pin-knows-its-duplicates.md`; ADR
`docs/adr/0051-duplicates-are-found-by-frame-hashes-in-bands.md`. Discussion with the operator on 2026-10-05,
specification review `.reviews/the-pin-knows-its-duplicates-spec.md`.
Lot `0.48.0`, one stack of 10 code blocks *(corrected in the closing block: 11, then the closing block's two)*: 10 `feat/a-media-yields-its-frames` (#323), 20 `feat/pdq-hashes-a-frame`
(#324), 30 `feat/frame-hashes-are-stored-in-bands` (#326), 40 `feat/the-worker-finds-duplicates` (#327), 43
`feat/the-worker-hashes-media` (#328), 46 `feat/the-worker-sweeps-fingerprints` (#329), 50
`feat/the-api-serves-duplicates` (#330), 53 `feat/the-api-lists-duplicates` (#331), 60 `feat/pins-merge` (#332), 70
`feat/the-dialog-lists-duplicates` (#333), 80 `feat/the-dialog-merges-a-group` (the pull request this file arrives
in). Written in block 80 from the block reports collapsed in those pull requests; the closing block corrects it after
the holistic review.

## Current state

- **A media yields its frames** (10): the port `FrameSampler`, implemented by `VipsImageTransformer` and
  `FfmpegVideoProcessor`, hands one luminance frame at a time, each decoded by a child process to a PPM beside the
  input and read by `PpmReader` (`api-utilities`), which refuses a raster past `MediaLimits.MAX_FRAME_SIDE` (512). A
  frame per whole second, padded to four evenly spaced frames under four seconds; an animated image with zero delays
  gives four frames over its pages. *(Corrected in the closing block: the first 120 seconds at most, ADR 0047's
  longest video, so an animated image of any length costs at most 120 decoder runs.)*
- **`PdqHasher` hashes a frame exactly as Meta's reference does** (20), in `api-domain`, with `java.lang.Math` (the
  architecture rule refuses `kotlin.math` there). Thresholds 31 and 49 are constants.
- **Frame hashes are stored in sixteen 16-bit bands** (30), migration `1.31`: `media.fingerprint_version`, the frame
  table with one expression index per band plus `ix_media_frame_media`, and the pair table with a unique index on its
  two pin ids plus `ix_pin_duplicate_second_pin`. `MediaFrameRepositoryInterface.findNear` ORs sixteen raw band
  expressions; the true distance is checked in Kotlin.
- **The worker finds duplicates** (40, 43, 46): one task kind, `media.fingerprint`, drains every media below
  `FINGERPRINT_VERSION`, newest first, renewing its lease after each *(corrected in the closing block: before each
  and after each frame, and its candidates are looked up before the write transaction opens)*. It is enqueued after
  an upload, inside a download's transaction, after an import run, and by `GarbageCollectionLifecycle`, whose
  `ReapFingerprints` also deletes the frames of a gone media and the pairs of a gone pin. Two pins of one author and
  one motion level pair when 80 % of either media's unique frames find a frame of the other within 31 bits, both
  ways. A media that cannot be decoded is stamped with no frames. `MediaAdapterProducers` routes a video to ffmpeg and
  anything else to vips. *(Corrected in the closing block: deleting a pin's media deletes that pin's pending pairs,
  at `DeletePinMedia`.)*
- **The API serves duplicates** (50, 53, 60), contract `22.3.0`: `PinOutputDto.hasPendingDuplicates` on every pin,
  read for a whole page in one repository call; `GET /api/v1/pins/{pinId}/duplicates` (`{pin, rejected}` items, empty
  for a recycled pin); `PUT .../duplicates/{otherPinId}` with `{rejected}`, refused with the new
  `DUPLICATE_DOES_NOT_EXIST` for an absent or hidden pair; `POST /api/v1/pins/merges`, all or nothing, the kept pin
  gaining every board and tag and filling a blank description and a missing page address, the absorbed pins recycled
  at one instant and their pairs not carried.
- **The web application shows them** (70, 80): a marker in a tile's bottom corner; in the dialog, the pending
  candidates and the rejected ones folded beneath, each opened in the same dialog whether or not the grid holds it,
  with no previous or next. The pending list is a merge form: *Keep* over the open pin and each candidate, *Include*
  per candidate, *Merge (n)* counting the group. After a merge the dialog shows the kept pin, the absorbed pins leave
  every cached catalogue, and the boards and duplicate lists are read again. *(Corrected in the closing block: the
  catalogues are read again too, after a merge and after a recycle of a marked pin, so a partner loses its marker;
  the kept pin is read through its own query, `usePin`, which an edit invalidates.)*

## Evidence

- Block 10: `dagger call gate` green at `9fa4985f`; budget 490 lines, 10 files against `main` (#323).
- Block 20: gate green at `7b5e5bbe`; budget 349 lines, 19 files (#324). Mutating the box filter's half-window and
  the quality divisor failed the exact cases. Excerpt measurement, the evidence for the backlog item "A video's
  excerpt is not found as such" (#324's report, its table): a 3-second cut of a 10-second `mandelbrot` finds 4 of 4
  frames when it starts on 0, 1, 2 or 5 s, 3 of 4 on 3, 4, 6 or 7 s, and 1 or 0 of 4 when it starts half a second off.
- Block 30: `./gradlew gate` green; budget 497 lines, 13 files (#326). Over 100 000 random frames on SQLite 3.53.4
  (`bands.py`, in #326's report): `MULTI-INDEX OR` over the sixteen indexes, mean 412 candidates per lookup (one in
  243 rows), lookup median 1.12 ms against a 68.5 ms full read.
- Block 40: gate green; budget 229 lines, 10 files (#327). Before its split, 836 lines over 19 files.
- Block 43: gate green; budget 432 lines, 10 files (#328).
- Block 46: gate green; budget 403 lines, 16 files (#329). Without the motion-level filter, the still against the
  looped video pairs.
- Block 50: gate green at `287f711e`; budget 240 lines, 17 files (#330). Before its split, 666 lines over 27 files.
  `oasdiff changelog` (`tufin/oasdiff:v1.31.0`) against `main`: 7 `response-required-property-added`, no error.
- Block 53: gate green at `87e06126`; budget 453 lines, 19 files (#331). `oasdiff changelog`: 9 infos, 2 of them
  `endpoint-added`, no error.
- Block 60: gate green at `1601e601`; budget 375 lines, 7 files (#332).
- Block 70: gate green at `1a7c92e0`; budget 426 lines, 11 files (#333). Read headless in Firefox 156.0.1 over
  WebDriver BiDi at 390x844 and 1280x800, light and dark, against a stub API; two layout defects fixed before the push.
- Block 80: `dagger call gate` green at `d8fa014d` (Vitest 69 files, 358 tests); budget 365 lines, 9 files against
  `feat/the-dialog-lists-duplicates`. The journey "merge a group of duplicates" was red before the implementation (no
  *Keep this pin* radio). Removing the boards' invalidation fails it (`expected 1 to be greater than 1`); removing the
  kept pin's fallback fails its second case, a kept candidate the grid has not loaded. Read headless the same way as
  block 70, against a stub API with a merge route: the group form, a candidate kept and another left out, the dialog
  after the merge, the grid after it. The reading found one defect the journey had not: the dialog closed after a
  merge whose kept pin was a candidate. The open pin left the cache before the kept one was shown, which unmounted the
  form and dropped `mutate`'s success callback; fixed in `d8fa014d` by showing the kept pin first, in the same render.
  Twenty screenshots in the session's scratchpad, `shots80/{phone,desktop}-{light,dark}-{1-grid,2-group,3-chosen,4-merged,5-grid-after}.png`.
- Continuous integration: one red run, on #332, below.
- Closing block, split in two (below): `./gradlew gate` green on `fix/the-duplicates-lot-closes` at `f309b35e`;
  budget 173 lines, 18 files against `feat/the-dialog-merges-a-group`. The clients' typecheck, lint, Knip and
  Vitest (69 files, 359 tests) green on `fix/the-duplicates-lot-closes-the-webapp`, and `dagger call gate` green at
  its `3d635be7`, the whole stack's top; budget 121 lines, 4 files against `fix/the-duplicates-lot-closes`. Mutations, each failing the case named: `DeletePinMedia` without
  `deletePending`, the new `DuplicateFindingIntegrationTest` case; the pair relations left optional, two
  `EbeanPinDuplicateRepositoryTest` cases (a gone pin's pair shown); the merge without the catalogues' reread, the
  merge journey's marker assertion; the recycle without it, the new delete journey case; the kept pin left unread,
  the merge journey's second case.

## Pitfalls

- **`dagger call gate`, and so the `pre-push` hook, cannot run from a linked git worktree**: the gate's git steps see
  no history there.
- **`gh stack submit` pushes every branch of the stack**: block 70's submit pushed block 53's fix before the lead did.
- **A red run hid behind Gradle's local cache**: #332 failed on a coverage branch of block 53's
  `EbeanPinDuplicateRepositoryTest`, reached or not depending on random UUID order. Fixed on 53 as `f8a25f5d` (a pin's
  pair read from both sides), cascaded by the lead.
- **`gh stack rebase --upstack` refuses branches not yet on `origin`**: before the first submit, a split's branches
  are rebased with `git rebase --onto` (40).
- **Jackson's Kotlin module reads an absent non-null `Boolean` as `false`**, so `{}` sent to the `PUT` would restore
  the pair; the field is `Boolean?` with `@NotNull`, answering 400 (50).
- **detekt's `DestructuringDeclarationWithTooManyEntries` refuses four components, tests included** (30, 40, 50, 60),
  and `MagicNumber` refuses `values[8]` and up (30). A changed detekt rule needs `./gradlew --stop` (30).
- **`@DbForeignKey(noConstraint = true)` also drops the index Ebean generates for a relation** (30).
- **`vips ppmsave` flattens alpha on black** unless given `[background=255]`; ffmpeg's GIF muxer never writes a zero
  delay; the gate's container has vips 8.15 and ffmpeg 6.1, the workstation newer (10).
- **`BaseTest` clears every mock before each test**, so a stub written in `init` is gone; a suite counting files under
  `media.data_dir/tmp` must wait for the drain (`IntegrationTest.awaitFingerprintDrain()`) (43).
- **A query keyed under `["pins", ...]` is rewritten as a catalogue** by the pin writers' `setQueriesData`; the
  duplicates live under `["duplicates", ...]` (70).
- **A modal hides the grid from the accessibility tree**: a test reading tiles behind an open dialog needs
  `hidden: true` (70).
- **A mutation that removes the open pin from the cache must show the next pin first**, in the same tick: once the
  dialog's form unmounts, TanStack Query drops the callbacks passed to `mutate`. jsdom did not show it (80).
- **The evidence guard refuses `sed -i`, a heredoc into `python3`, and a redirection into a scratch file**; a
  background command's own output file holds a server's log (80).
- **Ebean joins an optional `@ManyToOne` left**, so `firstPin.softDeletedAt.isNull` is true for a pin that is gone;
  `optional = false` makes it inner, with no migration (closing block).
- **SmallRye publishes a Kotlin `Boolean?` as `["boolean", "null"]` whatever `type` says**; `@field:Schema(nullable =
  false)` is read as set and wins over the Kotlin metadata (closing block).
- **`./gradlew detekt` reports every baselined finding**; the gate's `detekt<SourceSet>` tasks are the ones that read
  the baselines (closing block).

## Departures from the specification

- Decision E and blocks 20 and 40 (corrected in the specification): a pair is 80 % both ways and always a
  duplicate, excerpts leaving the lot; `relation` is dropped from the contract.
- Block 10: no new adapter classes, the two existing ones implementing `FrameSampler`; `sample` takes the `Media`.
- Block 30: two indexes the specification did not list; "concentrated in one band" packs 31 bits in the first two.
- Block 40 split in three (40/43/46) and block 50 in two (50/53), both recorded under the block table; block 40's split
  stayed with its author. Comparability is a second query rather than joins on the band lookup.
- Block 60: the merge cases joined `PinUpdaterIntegrationTest`.
- Block 70: a video shows its dimensions, the contract carrying no duration; the French for *Restore* is "Rétablir".
- Block 80: the journey runs on a board's screen, the one screen whose boards query is mounted, so "the boards are read
  again" is a request it counts. *Merge (n)* counts the whole group, kept pin included. A second case keeps a candidate
  the grid has not loaded: the dialog shows it, with no previous or next.

- Closing block: split in two on another seam than the workflow's, the API findings alone touching 18 counted
  files: `fix/the-duplicates-lot-closes` holds the API findings, `fix/the-duplicates-lot-closes-the-webapp` the web
  application's, the specification's cell and this handoff. `main` was told the seam first. `DeletePinMedia` deleting
  pending pairs departs from decision J's "sweep, not deletion sites": a pin with no media is never hashed again nor
  swept, so its pairs would stay. The web application's two fixes change no layout and were not read headless; the
  journeys hold both behaviours.

Tier-1 fixes: `DbMigrationModelCoverageTest` decodes XML entities before comparing an index definition (30);
`MeImportIntegrationTest` waits for the drain before counting staged files (43); the raised-version case of
`DuplicateFindingIntegrationTest` reads the pairs at every renewal, the lease now being renewed before a media's
sampling (closing block).

## Tier-2 questions

- Discuss settled questions A to N with the operator; O, excerpts leaving the lot and filed in the backlog, came from
  block 20's measurement ("3, et on note ça au backlog pour plus tard. C'est une feature supplémentaire la détection
  d'extraits, on veut la détection de doublons dans ce lot.").
- P is pending: whether headless screenshots go in a pull request. *(Corrected in the closing block: still
  unanswered.)* The closing block asked none.

## What is not validated

- Recycling a pin does not refresh its partners' markers until the grid reloads; a merge leaves the absorbed pins'
  partners the same way (70, 80).
- Sampling a long animated image starts one vips process per sampled frame, each decoding the pages before it, with
  no bound on an animated image's length (`ponytail:` comment, 10).
- The `PUT` body's `rejected` is `boolean | null` in the contract while null is refused with 400: left for the
  holistic review.
- The drain's lease renewal under a short lease (43); the import's and the download's enqueues end to end (46); vips
  8.15 on a 16-bit or grey PNG (10); the C++ reference built from `pdq/cpp` (20).
- After a merge, the kept pin is missing from a board's cached catalogue it joined until that catalogue is fetched
  again, as after an edit (80).
- Everything against the running API rather than a stub, in the web application (70, 80).

*(Corrected in the closing block: the markers after a recycle or a merge, the long animated image, the `PUT` body,
vips on a grey or 16-bit PNG and the kept pin's absence from a board's catalogue are fixed or now tested; see the
holistic review below. The lease renewal is unit-tested per frame and still not run under a short lease. The C++
reference and the running API stay not validated.)*

## The holistic review

Not run yet: it reads the top of this stack at the head of Wrap, over
`git diff lot/0.47.0-the-boards-wear-their-cover..origin/feat/the-dialog-merges-a-group`, and the closing block records
its findings here. *(Corrected in the closing block: it ran, `.reviews/the-pin-knows-its-duplicates-holistic.md`,
0 CRITICAL, 3 MAJOR, 10 MINOR. Every finding was fixed inside the lot.)*

- MAJOR, sampling unbounded and the lease outlived: `FrameSampler.instants` takes 120 seconds at most; the drain
  renews before a media's sampling and after each frame.
- MAJOR, partners keep their marker after a recycle or a merge: the catalogues are read again after a merge, and
  after a recycle of a pin a cached catalogue showed marked.
- MAJOR, `DeletePinMedia` left pending pairs: one `deletePending(pinId)` at that site, with an integration case
  (departure above).
- MINOR, only three exception types stamp a media: fixed at the adapters rather than by catching
  `RuntimeException`, which `agents/engineering.md` forbids and which would swallow the `TaskLeaseLostException` the
  per-frame renewal throws. vips reports an unreadable delay as undecodable; `PpmReader` refuses a header with no
  side or an impossible sample range, which reached an `IllegalArgumentException` or a division by zero.
- MINOR, band lookups under the write lock: `duplicatesOf` runs before the transaction.
- MINOR, `rejected` published nullable: `@field:Schema(nullable = false)`; the contract says `boolean`, the
  operation being new against `main`.
- MINOR, the kept pin a snapshot: read through `usePin`, seeded by the merge, invalidated by an edit.
- MINOR, the 80 % bound untested below: a case at 75 % makes no pair.
- MINOR, PGM and 16-bit branches unevidenced: a white grey PNG at 8 and 16 bits sampled through vips; vips 8.18
  wrote a P6 at 255 for both on the workstation, and the gate's 8.15 runs the same case.
- MINOR, Hamming distance three times: `PdqHash.distance`, called by the repository and the drain.
- MINOR, `shown()` materialising every active pin: `withActivePins()` joins both pins by key, a plan test holding it.
- MINOR, the specification's table cell: marked corrected.
- MINOR, bare decision letters: "specification 2026-10-05" added in `PinGrid` and the merge journey.

## The backlog

"Perceptual `ImageHash` (pHash)" is deleted (80). "A video's excerpt is not found as such" was filed under Features
(20). "`foreign_keys` is off", "Import follow-ons", "Search matches by substring and tolerates no typo", "Advanced pin
/ tag / board management" and "Visual understanding" stay open, the specification's section 5 saying why.
*(Corrected in the closing block: reconciled against `docs/backlog.md`, no item changes; no finding took the backlog
as its exit.)*

## The lot's counts

Fix-backs 1 (block 53's flaky coverage branch); cascaded rebases 1; the runs they re-triggered and the operator's
reading of the bodies: filled in by the closing block. *(Corrected in the closing block: runs re-triggered 3 against
13 blocks, from `gh run list` on 2026-10-05: #331 at `f8a25f5d`, #332 at `489ecd5a` and #333 at `15479690`, two
earlier runs of #333 cancelled. The operator's reading of the bodies: no remark so far, the review of the stack not
having started.)*

## Next step

Wrap: the holistic review over the diff above, then the closing block, the operator's review of the stack, and the
tag `lot/0.48.0-the-pin-knows-its-duplicates` once it merges. *(Corrected in the closing block: the review and the
closing block are done; the operator's review of the stack, question P, the merge and the tag remain.)*
