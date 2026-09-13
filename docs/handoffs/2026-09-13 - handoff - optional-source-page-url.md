# Handoff: a pin no longer needs a source page URL

Date: 2026-09-13
Branch: `feat/optional-source-page-url`, block 10, pull request #118, merged as `1d9ca6d9`
Specification: `docs/specs/2026-09-13-optional-source-page-url.md`, frozen by that merge
Tier: Spec, implemented in a teammate. The specification review ran in a named agent and its seven
findings were closed in the document before the operator read it. **The holistic review did not
run**: the operator judged it overkill for a one-block mechanical lot, on 2026-09-13.

## Current state

`dagger call gate` green at the block's tip. Continuous integration green on #118: `verify` 9 m 7 s,
`build-image` 4 m 16 s, `gate` 2 s. The diff is 262 counted lines against a bound of 600, with 61
production lines under `api/` against 200 and 7 under `clients/` against 400.

The contract is at `6.0.0`. The response property widening to `["string", "null"]` is the break, and
`contract-guard` accepted the major.

## What was built

- **`sourceContextUrl` is nullable** in `Pin`, `PinModel`, `PinCreator.createPin`,
  `PinCreationInputDto` (the `@NotBlank` dropped), `PinOutputDto`, `ExportedPin` and `ImportedPin`.
  `PinController` reads a blank one as none, as it already did for `sourceMediaUrl`.
- **`1.23.sql`** rebuilds `pins` with the column nullable, by hand, on the model of `1.4.sql`.
- **`UserDataImportRunner`** falls back to the pin's description for an issue's subject, so a refused
  pin with no page URL still names itself in the report; `blankFault` now applies only to a value
  that is present.
- **`TableRebuildColumnsTest`**, the lot's one new guard: for every `*_tmp_rebuild` in the migration
  directory, the `create table`, the `insert into (...)` and the `select` name the same columns in
  the same order. It covers `1.4.sql` and `1.22.sql` as well as `1.23.sql`, and was seen red on a
  column dropped from `1.23.sql`'s `select`.
- **On the clients**, `PinCreation.sourceContextUrl` is `string | null` and the source page input is
  never `required`: a file from disk and a direct image address both name no page.

## Pitfalls

- **The rebuild of `pins` is legal only while `foreign_keys` is off.** `image_download` has
  referenced `pins` since `1.22.sql`. With the pragma on, `drop table pins` would be refused, and
  `ALTER TABLE ... RENAME TO` rewrites the references other tables hold only when it is on. The
  `P2` item that would turn the pragma on must reread `1.4.sql` and `1.23.sql` together.
- **The generator writes the placeholder, not the rebuild.** SQLite cannot lift a `not null` in
  place, so `generateDbMigration` leaves `-- not supported` and the rebuild is written by hand.
  `DbMigrationModelCoverageTest` refuses a placeholder left in place and a `.sql` with no model file.
- **`sourceContextUrl` stays in the contract's `required` list while nullable**, as its sibling
  already did: SmallRye marks every property of a Kotlin data class required. A client sends the key
  carrying `null`, not an absent key.

## What is not validated

- **No migration in this repository runs over a non-empty database.** The test database is created
  empty, so a rebuild's `insert ... select` always copies zero rows. The new guard reads the column
  lists in the file; it does not observe a row surviving. Option C2, a real replay to `1.22` with a
  seeded row, was put to the operator and declined as disproportionate.
- **One departure from the block table**: the export and import round trip for a null page URL rides
  the existing round trip, whose recycled pin now names no page, rather than a sixth seeded pin that
  would have needed a new binary image fixture.
- **No holistic review ran over this lot**, so nothing read the merged diff as a whole.

## Next step

The `P1` band, whose items this lot did not touch. The two adjacent ones it deliberately left open
are the settled download refetching the whole catalogue, in `images.ts`, and `foreign_keys` being
off, both still in `docs/backlog.md` with the observable that says they did not move.
