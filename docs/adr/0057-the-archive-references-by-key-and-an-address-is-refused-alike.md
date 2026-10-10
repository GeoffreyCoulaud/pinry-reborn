# 0057. The archive references by key, and an address is refused alike

Status: Accepted
Date: 2026-10-10
Supersedes: `docs/adr/0056-data-shapes-are-decided-in-the-specification.md`, decision 13, and decision 7.1's "pin
lines naming theirs by `{name, urls}`".
Specification: `docs/specs/2026-10-10-addresses-and-people-take-their-shape.md`, decisions C, F and G.

## Context

ADR 0056, decision 7.1, types every address as `HttpUrl` and gives the user data archive a `persons.jsonl` entry. It
leaves open how the API refuses an address, and the shape of the archive's references, which the third-party importer
of ADR 0055 will write. Its decision 13 rejected naming a person from a pin line by an archive identifier. Every
decision below is the operator's, in Discuss on 2026-10-09 and 2026-10-10, unless it says it is the lead's.

## Decision

### An address

1. **Only `null` is no address.** A blank text is refused as any text `HttpUrl`'s factory refuses, on every entry
   path: the API answers 400 `VALIDATION_ERROR`, the import reports `FIELD_INVALID` and refuses the line.
2. **One code for every refused address** (the lead's, announced and not refused): the media download route answers
   `VALIDATION_ERROR` too, and `MEDIA_SOURCE_URL_INVALID` leaves the contract.

### The archive

3. **A reference is an object holding its natural key**: `{name}` for a tag and a board, `{url}` for a collection.
   The manifest's `user` is `{name}`.
4. **No record carries the database's identifier.** The manifest's `exportId` stays: it names the archive, not a
   record.
5. **A `persons.jsonl` line carries an `id`**, a non-blank text of at most 200 characters, unique within the entry.
   The export writes the person's database identifier; a third-party tool may write its own.
6. **A pin line names its publisher as `{id}` and its creators as a list of `{id}`.** A person is a composite value,
   and repeating its name and every address on each pin naming it is the duplication an identifier avoids. A tag or a
   board is one text, so naming it by its key repeats nothing.
7. **The identifier only links the lines of one archive.** The import finds or creates the person by its name and
   addresses, as `docs/adr/0015-import-identifies-by-natural-key.md` identifies every record, then forgets it.
8. **A pin line naming an identifier no person line carries creates the pin without that person and reports it**,
   as a digest mismatch is reported on a pin still created.

## Rejected

- **A blank address read as none** on the API: the empty string would be a third state beside null and an address.
- **Repeating `{name, urls}` on every pin line** (ADR 0056, decision 13): the duplication decision 6 removes.
- **A bare string for a one-field reference**: the shape would depend on the key's field count.
- **Refusing the pin line** whose person is unknown: a missing credit is worth less than the pin.

## Consequences

- Decisions 5 and 6 depart from two rules of `agents/data-modelling.md`, "The archive names by natural keys" and "A
  concept has one shape, on the wire and in the archive", where a person is `{name, urls}`. That document gains the
  exception: a composite value the archive references many times has its own section and is referenced by an
  identifier that decides nothing.
- `contract/openapi.json` takes a major version: a blank address once accepted is refused, and a code leaves a set.
- ADR 0056's `Status:` line names this ADR.
