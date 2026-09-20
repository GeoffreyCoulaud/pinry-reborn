# 0038. One route writes a pin

Status: Accepted
Date: 2026-09-20
Specification: `docs/specs/2026-09-20-the-pin-is-editable-and-the-boards-arrive.md`, decisions B
and C.
Related: `docs/adr/0024-three-projects-share-one-repository.md`, which makes
`contract/openapi.json` the surface this repository negotiates on, and which is why a route removed
here is a decision and not an edit.

## Context

A pin is created by `POST /api/v1/pins`, carrying a description and two source addresses, and after
that nothing writes those three fields again. What can be written are a pin's tags, through
`PUT /api/v1/pins/{pinId}/tags`, and its boards, through `PUT /api/v1/pins/{pinId}/boards`. A user
who mistypes a description lives with it.

Giving the web application an edit form therefore meant adding a third write route beside the two
that exist, and a save became up to three requests. Three requests are three transactions: the
second one refused leaves the pin with a new description, its old tags and its old boards, and no
sentence the form can say about that state is true for long.

`PinOutputDto` already carries `tags` and `boards`, beside the description and the addresses. What
the API hands back as one object was writable only in three pieces.

## Decision

1. **`PUT /api/v1/pins/{pinId}` writes the whole pin**: `description`, `sourceContextUrl`,
   `sourceMediaUrl`, `tags` and `boardIds`, in one transaction, answering the pin as `GET` answers
   it.

   **Fails if** a client needs to write one field without knowing the others, which a client that
   has just read the pin does not.

2. **An identifier that fails to resolve earns 404, and one that is another user's earns 403,
   wherever it was named.** The pin in the path and every `boardId` in the body answer alike, and a
   soft-deleted pin is refused. `BOARD_INVALID_MEMBERSHIP` is deleted, it being the one code that
   departed from that rule.

   **Fails if** a caller has to tell the target resource's absence from a body identifier's absence
   by status alone; the problem response's code says which one failed.

3. **`PUT /api/v1/pins/{pinId}/tags` and `PUT /api/v1/pins/{pinId}/boards` are removed.** Writing
   only the tags means sending the other fields as they were read.

4. **Acting on several pins at once is not this route's job.** It stays board oriented and keeps its
   own shape, which `docs/adr/0039-a-batch-route-is-all-or-nothing.md` decides.

## Consequences

- **The contract breaks, and says so.** `quarkus.smallrye-openapi.info-version` reaches `7.0.0` on
  the block that removes the two routes, the block that adds the new one having taken it to `6.3.0`
  first. `contract/frozen/` holds no major, so nothing still served is broken, and the README states
  that the alpha breaks freely.
- **An edit is a read-modify-write, and the last writer wins.** Two clients editing one pin from the
  same read overwrite each other's fields. That was already true of the board route removed here,
  which replaced the whole set, and every pin has one owner.
- **A client that wants to add one tag has to send the tags it read.** The web application reads the
  pin into its form before it writes, so this costs it nothing. The browser extension, when it
  arrives, will read the pin it is filing before it files it, for the same reason.
- **The replacement is total, including the empty case.** `tags: []` clears the tags and
  `boardIds: []` takes the pin out of every board. A client that omits a field is refused rather
  than read as leaving it alone: there is one way to say "unchanged", which is to send what was
  read.
