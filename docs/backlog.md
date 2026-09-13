# Backlog

**Living document.** What is still open, banded by nature first and by priority second. What already shipped lives
in git history, the handoffs under `docs/handoffs/`, and the annotated `lot/X.Y.Z-*` tags, not here.

## How to use this file

- This file holds **open items only**. Do not keep a "shipped" log here: completed work is recorded by git
  history, the handoffs under `docs/handoffs/`, and the annotated `lot/X.Y.Z-*` tags.
- **Banded by nature**, as the headings below show and as `agents/workflow.md`, The backlog, lists them with
  what each one holds. A limit is not debt and is not counted as debt.
- **Open work is grouped by priority**, not by module. `P0` = product decisions that shape the data model and
  the UI. `P1` = client ergonomics needed for the web UI and the browser extension, and the process debt a lot
  pays for in correctness. `P2` = operational debt (not UI blockers). A priority may hold nothing.
- **An item holds in two lines**: the symptom and where it lives, plus a pointer to the dated document that
  carries the reasoning. Never a copy of that reasoning (`agents/writing.md`, Rules). The exception is an item
  whose reasoning was only ever written here, which keeps it: dated documents are append-only, so compressing
  such an item destroys the argument rather than relocating it. Those are marked below. There is **no cap on
  the number of items**.
- **A lot closes the items adjacent to its subject**, and its spec says why it leaves any of them
  (`docs/adr/0018-a-block-is-a-pull-request.md`, decision 6). This file is not where adjacent work waits.
- **Only one of a review finding's exits is this file**, and it is not the default one
  (`docs/adr/0010-review-finding-dispositions.md`; the exits are listed in `agents/workflow.md`, The backlog).
  Wrap states which exit each finding took. This file receives what the operator declined, not what was merely
  outside the original scope.
- When an item is picked up, note the branch or sub-project next to it; when it merges, **delete it from this
  file** (its record now lives in the handoff and the tag).

---

## Open work

### P1: Client ergonomics (needed for the web UI and browser extension), and process debt

- **Browser-extension CORS origin.** Deferred from the CORS sub-project (decision B1): the extension
  does not exist yet and has no stable ID, so no origin is wired for it. When it ships, add its
  `chrome-extension://<id>` / `moz-extension://<id>` origin to `api.cors.origins`. See
  `docs/handoffs/2026-07-21 - handoff - cors.md`.
- **Import follow-ons.** Import shipped (`docs/specs/2026-08-14-user-data-import.md`,
  `docs/adr/0015-import-identifies-by-natural-key.md`, branch `feat/user-data-import`); what it
  deliberately left out is here rather than in the spec's out-of-scope list, because these are work
  someone will do rather than limits: **selective import** (one board, or skipping the recycle bin)
  and its mirror **partial export**; **merging metadata onto a pin that already exists**, which is
  the option the v1 "skip" rule forecloses; and **making a pin with no medium travel**, which needs
  the export to carry `ImageDownload` so a pending or failed download survives the round trip.
- **The grid cannot cap the pages it holds.** A capped `useInfiniteQuery` drops the pages at the far end
  and nothing reloads them upward, so block 6 ships no cap and the query keeps every page scrolled.
  See `docs/specs/2026-09-10-web-application.md`, section 4.7. New 2026-09-11.
- **A pin cannot be created without a source page URL.** `sourceContextUrl` is non-null in `Pin`, `not
  null` in its column and required on `PinCreationInputDto`; the file entry has no page URL to give.
  See `docs/specs/2026-09-10-web-application.md`, section 4.8. New 2026-09-11.
- **Every settled download refetches the whole accumulated catalogue, sequentially.** `images.ts`
  invalidates `["pins"]`, and TanStack Query refetches all stored pages in series: fifty pages scrolled
  cost fifty `GET /api/v1/pins` per settled download.
  See `docs/handoffs/2026-09-11 - handoff - web-application.md`, finding 10. New 2026-09-12.
- **A pull request pays two cold Gradle builds**, one in `verify` and one in `build-image`, and the two
  run in series because `build-image` needs `verify`, so a pull request waits about fourteen minutes.
  See `docs/handoffs/2026-09-11 - handoff - web-application.md`, which measures it, and
  `docs/handoffs/2026-09-09 - handoff - monorepo-and-pipeline.md`. New 2026-09-09, raised to `P1` 2026-09-12.
- **`Session.renewAfter` reaches the client and nothing reads it**, so no client renews although both
  renewal answers exist. The `/pins/new` half of this item shipped 2026-09-13.
  See `docs/handoffs/2026-09-11 - handoff - web-application.md`. New 2026-09-12.

### P2: Operational debt

- **The `raw(` calls in production have never been audited.** Thirteen remain across nine files, and
  nothing records which are legitimate and which stand in for a relation the model does not hold, as
  `PinRepository:167`'s `id in (select ...)` did for `ImageDownloadModel`.
  See `docs/specs/2026-09-10-web-application.md`, section 4.11. New 2026-09-11.
- **The download list is unbounded, unpaginated and polled every second, and nothing sweeps a failed
  row.** `GET /api/v1/me/image-downloads` returns every row the requester owns, a `FAILED` row lives
  until the user drops it, and no periodic sweep touches `image_download` where the worker runs four.
  See `docs/handoffs/2026-09-11 - handoff - web-application.md`. New 2026-09-12.
- **`foreign_keys` is off, so every declared key is unenforced, and that now has a consequence.**
  `datasource.db.url` sets `journal_mode`, `synchronous` and `busy_timeout` and not `foreign_keys`;
  `1.22.sql` spent a table rebuild on a constraint nothing checks, and turning the pragma on would make
  `ON DELETE RESTRICT` refuse the hard delete of a pin whose download failed.
  See `docs/handoffs/2026-09-11 - handoff - web-application.md`. New 2026-09-12.

## Known limits

Recorded where the decision lives. None is a copy: follow the pointer.

- **`RowMergedOutsideTransaction` reads a construction as an insert**, so a row rebuilt field by field
  from an earlier read walks through it. The rule's KDoc, "Three limits, each accepted";
  `docs/specs/2026-09-05-p2-debt-elimination.md` D8.
- **Soft-delete read isolation leaves residuals.**
  `docs/adr/0008-structural-soft-delete-read-isolation.md`, and
  `docs/specs/2026-07-29-single-representation-soft-delete.md` section 4.6.
- **A unique constraint's named outcome is not checked against what the code does.**
  `docs/adr/0009-unique-index-named-outcomes.md`, decision 1.
- **The evidence guard is fired on more tools than it inspects**, deliberately, and narrowing it
  would set a worse trap than the cost it saves.
  `docs/adr/0011-own-the-agent-instructions.md`, consequences.
- **The partial-index state guard has a declared reach**, and one part of it is a correctness gap
  rather than a documentation one: it pins the predicate, not the uniqueness columns that make
  `findOne()` return at most one row. `PartialUniqueIndexStates` and
  `api-persistence-sqlite/src/test/kotlin/.../migration/PartialUniqueIndexStatesTest.kt`.
- **Authentication attempt counters are per process, and holding one account's login closed is
  cheap.** `docs/adr/0013-in-memory-authentication-attempt-limiting.md`: decision 1 (counters reset
  on restart, correct only while the deployment is one instance), decision 3 (the measured cost of
  keeping a named account out, and the `forget_after` / last-step interaction behind it), decision 4
  (eviction is a bypass, and nothing purges outside a recorded failure).

## Before beta

Dated events. No session starts these early.

- **Flatten the migration history.** The migration history is append-only, and that already constrains
  fixes: `users`/`pins`/`boards`/`tags` keep `when_created` / `when_modified` column names that no
  longer match the domain's `createdAt` / `updatedAt`, kept only because rewriting an applied migration
  changes its checksum and breaks startup. At beta, collapse `1.0` to `1.n` into a single generated
  baseline and take that fix with it. Until then, when a fix is blocked only by an already-applied
  migration, prefer the clean design and record the debt here. New 2026-07-23.
- **Populate `contract/frozen/`.** It stays empty while the alpha breaks freely; when the first contract
  major becomes still served its document enters as `<major>.json`, and the support window is stated with
  it. See `docs/specs/2026-09-08-monorepo.md` section 4.3. New 2026-09-09.

## Features

- **What the API serves and the web application does not reach yet**: boards, tags, search, the recycle
  bin, account management, import and export, and editing or deleting a pin. The first client lot built
  sign up, sign in, the grid and creating a pin, and nothing else.
  See `docs/specs/2026-09-10-web-application.md`, sections 6 and 7. New 2026-09-12.
- **Perceptual `ImageHash` (pHash)** for pin deduplication / merging. Flagship of the sequenced **user-segmented base
  ** (see the roadmap section below).
- **Advanced pin / tag / board management** : Features that make the data model genuinely user-segmented and pleasant to
  use. To be explored.
- **Import from 3rd party sites**
  Initial candidates :
    - Pinterest (board import),
    - Danbooru / Gelbooru / Other booru (favorites import),
    - Instagram (saved collection import),
    - Reddit / Twitter / Pixiv (saved posts import).

  On some of these sites, a post may contain multiple media. We're not changing our semantic 1 pin = 1 media rule.
  Can be either a one-time import, or to sync a local pinry board with a remote source periodically, as the user
  chooses.
- **Video support** : Completes the 3rd party use case, since those allow posting videos as well. Videos are a 1st class
  citizen, just like images. Their renditions are the video's thumbnail in case of a still rendition, or an animated
  image of the first few seconds of the video (eg. 3s)
- **RBAC and quota system** : Allow admins to toggle features and define quotas per-role, from the API
- **Audience mechanics (public / private).** Until this lands everything stays `@Authenticated` and owner-scoped (
  non-owner → 403); no anonymous browsing, no public gallery, no shareable links. It will interact with boards (public /
  shared boards) and with the profile items.
- **Two-factor / step-up authentication**: TOTP + Passkey/WebAuthn, with a possible short-lived "sudo" elevation token
  for sensitive actions.

Gated on audience mechanics :

- **Hard-copy of a public pin or board** from user B into user A's own collection: a real, independent copy, not a soft
  link.
- **Public profiles**: the deferred slice of profile management.
