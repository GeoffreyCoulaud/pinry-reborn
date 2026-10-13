# 0060. The refactorings come before the importer

Status: Accepted
Date: 2026-10-13
Amends: `docs/adr/0056-data-shapes-are-decided-in-the-specification.md`, decision 7.3 and 7.4 (their order, and the
types lot as one lot).
Specification: `docs/specs/2026-10-13-names-take-their-shape.md`.

## Context

ADR 0056, decision 7, put the third-party importer before the three refactoring lots. It wrote no reason for that
order: only decision 7.1's criterion, "everything that changes the archive the importer will write", is argued, and
lot `0.54.0` delivered it. Yet the sets, media and renames lots still change the archive (`inventory.md`, T6's
archive lists, T7), and the importer, a Python tool of its own root (ADR 0055, decision 14), would then be written
against a format about to move.

A re-reading of `inventory.md` at `f50a3e10` found the types lot past any stack ADR 0043 tolerates: most of its units
pass the 20-file bound alone, and ADR 0056's decision 15 already refused one lot of the whole inventory. Both decisions
below are the operator's, taken in Discuss on 2026-10-13.

## Decision

1. **The three refactoring lots run before the importer**: types, then sealed states, then batch writes and
   transactions. A refactoring that changes the archive lands before the tool that writes it.
2. **The types lot is six lots**, each with its own specification and stack, in this order:

| Lot | `inventory.md` items |
|---|---|
| Names | units 8a, 8b, 8d; T1's `RemoteCollection.name`; T4's name bounds defined twice; T5's `UserCreator` trim |
| Tasks | unit 17: `TaskKind`, `leaseId: UUID`, one `Lease`, typed payload and deduplication key (`AccountDeleter` included) |
| Media | units 18 and 19; 24's `RenditionSpec` and `ExportMediaExtension`; T2's `MediaFrame` words and storage layout; the `ck_media_variant` check that reads `mime_type` |
| Sets | units 9 and 10; T6's archive lists; `PersonInputDto.urls` without `uniqueItems` |
| Renames and contract | what unit 20 still holds after lots `0.55.0` and `0.54.0`; unit 25 |
| The rest | section 2's tokens, password and hashes, `formatVersion`, import issue fields, rendition and fingerprint keys, byte counts, pixel dimensions, bit rates, `StagedFile.path`, archive entry paths; 24's import counters |

3. **Media precedes renames**, a concept being renamed once it is typed; **names precede sets**, `resolveTags` taking
   a set of `TagName`.

## Consequences

- **The backlog** files one item per lot of decision 2 in place of the types item, and the importer item points here
  for its place.
- **Each lot re-reads the code before its specification**: `inventory.md`'s line numbers are `1fc81bdc`'s, and its
  `P/` is now `api-persistence-ebean`.
