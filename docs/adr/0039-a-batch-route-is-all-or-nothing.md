# 0039. A batch route is all or nothing

Status: Accepted
Date: 2026-09-20
Specification: `docs/specs/2026-09-20-the-pin-is-editable-and-the-boards-arrive.md`, decisions D,
E and F.
Related: `docs/adr/0038-one-route-writes-a-pin.md`, which decides the shape of a write to one pin.
Written because the convention set here binds every batch route the contract gains after it.

## Context

The web application's grid has carried `selectionMode` since the first client lot, with nothing
consuming a selection. Giving the selection a bar that files pins under a board, takes them out of
one, or deletes them, means acting on many pins at one gesture.

Doing that with the routes that exist means one request per pin: forty pins filed under a board is
forty transactions, any of which can fail on its own, leaving a state no message describes.

The recycle bin already serves three shapes for two collections: restore one, delete one for good,
and empty everything. Adding bulk to it without a rule would give the bin two grammars, one per
collection, and the operator asked for one.

## Decision

1. **Membership in bulk is board oriented**: `POST /api/v1/boards/{boardId}/pins` adds the pins its
   `{pinIds}` names, `DELETE /api/v1/boards/{boardId}/pins` removes them. Both sit beside the
   `GET` that lists a board's pins, and both add or remove rather than replace, so a pin's other
   boards are untouched.

   **Fails if** a caller wants to replace a selection's whole board set, which no gesture in the
   application asks for.

2. **A batch route is all or nothing.** Every identifier is resolved before the first write; one
   that fails refuses the whole call and nothing is written. An identifier that resolves to nothing
   earns 404 and one that is another user's earns 403, whether it was named in the path or in the
   body. There is no per-identifier report and no partial success.

   **Fails if** a client acts on identifiers it did not just read, which is a client defect and not
   a case to serve.

3. **A collection of identifiers whose whole purpose is the batch is `@NotEmpty`**, which is
   `PinIdsInputDto.pinIds` and `BoardIdsInputDto.boardIds`. `DELETE /api/v1/pins` with no body would
   read as "delete every pin I own"; the guard answers 400 and keeps that reading from ever being
   written. A list inside a write of one pin is not covered: `PinUpdateInputDto`'s `tags` and
   `boardIds` take the empty list, which is how a user clears their tags or files a pin under no
   board (`docs/adr/0038-one-route-writes-a-pin.md`, last consequence).

4. **The recycle bin reads the same on both collections.** Recycling and restoring act in bulk
   (`DELETE /api/v1/pins`, `POST /api/v1/pins/recycled/restore`,
   `POST /api/v1/boards/recycled/restore`); a permanent delete acts on one; emptying is the route
   that already exists.

   **Fails if** a user needs to purge a chosen subset of a large bin, for which emptying is the
   answer today.

## Consequences

- **A bulk route is one transaction**, and its ownership check runs before its first write. A check
  interleaved with the writes turns all or nothing into all or some.
- **404 over a body identifier departs from RFC 9110, section 15.5.5**, which defines the status by
  the target resource the URI names. A `POST /api/v1/boards/{boardId}/pins` answering 404 over a
  `pinId` therefore tells generic tooling that the endpoint is gone. That cost is accepted against
  one rule instead of two: the application reads the problem response's code, which says which
  identifier failed, and no status is declared in the contract for it to break.
- **Telling an unknown identifier from a foreign one is no longer hidden.** `BOARD_INVALID_MEMBERSHIP`
  conflated them at 400 and is deleted. Identifiers are random version 4 UUIDs, so enumerating
  another user's boards is not a threat this API defends against; if it ever becomes one, the answer
  is 404 everywhere rather than a second grammar.
- **No new output type.** A batch answers 204, or the error the first refused identifier earns. The
  contract gains no report shape, and a client that wants to know which identifiers survived rereads
  them.
- **Emptying a bin keeps its bodyless `DELETE`.** `DELETE /api/v1/pins/recycled` and
  `DELETE /api/v1/boards/recycled` mean "everything" and are left alone: giving one of them a body
  would make a single operation mean two things.
- **`DELETE` carries a body on two routes**, `/api/v1/pins` and `/api/v1/boards/{boardId}/pins`, and
  no route in this repository does that today. It is legal (RFC 9110, section 9.3.5), and two
  things can still drop the body: an intermediary that strips it, and the server not binding it at
  all. Both land on decision 3's 400 rather than on a silent full delete, and the first test written
  for `DELETE /api/v1/pins` is an empty body asserting that 400, which fails loudly if the entity
  never reaches the resource method.
- **The next batch route follows this shape** rather than deciding again: board oriented where it is
  about membership, all or nothing, `@NotEmpty`, no report.
