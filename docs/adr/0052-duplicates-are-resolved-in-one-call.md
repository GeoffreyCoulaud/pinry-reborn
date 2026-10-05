# 0052. Duplicates are resolved in one call

Status: Accepted
Date: 2026-10-05
Specification: `docs/specs/2026-10-05-the-duplicates-are-compared.md`, decisions B and C.
Supersedes the merge and rejection routes of `docs/adr/0051-duplicates-are-found-by-frame-hashes-in-bands.md`,
decisions 8 and 9, which keep their effects.
Written in block 10.

## Context

Lot `0.48.0` gave each candidate of the pin dialog its own gesture: *Not a duplicate* sent a `PUT` per pair at the
click, *Merge* sent `POST /api/v1/pins/merges`. The comparator that replaces that list decides every version of the
group on one screen, then applies the whole decision. A request per click could arrive out of order or fail alone,
and leave the screen and the server disagreeing.

## Decision

1. **One call applies the group's decision**: `POST /api/v1/pins/{pinId}/duplicates/resolutions`, all or nothing
   (`docs/adr/0039-a-batch-route-is-all-or-nothing.md`). It replaces `POST /api/v1/pins/merges` and
   `PUT /api/v1/pins/{pinId}/duplicates/{otherPinId}`, which leave the contract.
2. **The path names the open pin, and the body the kept one.** Similarity is not transitive: a candidate pairs with
   the open pin, not always with the pin the user keeps, so only the open pin's list holds the whole group.
3. **The body gives each named pin one decision**, `{"decisions": {"<pinId>": "KEEP" | "MERGE" | "REJECT"}}`. An
   object keyed by pin cannot name a pin twice, and one `KEEP` among its values says which pin survives.
4. **The answer is the kept pin**, which is why the call is a `POST` that creates a resolution rather than a change
   to the list. A `PATCH` answers with its own target's new state, and the open pin's list can vanish with the open
   pin itself when it is merged. JSON Patch (RFC 6902) cannot express a merge: its result must be what the next `GET`
   returns, and no field of the list says which pin was kept. A `DELETE` misnames a rejection, which the list still
   shows.
5. **A rejection holds against the whole group**: the rejected pin's pair with every kept or merged pin it pairs
   with is rejected, so it does not come back as the kept pin's candidate once the open pin is merged.

   **Fails if** a gesture of the web application writes a duplicate's state through another route.

## Consequences

- **The body's format is the call's own**, in `application/json`. A rejection alone would fit a standard patch; a
  merge does not.
- **Restoring a rejected candidate to pending has no route.** The comparator's three states are keep, merge and
  reject, and a rejected candidate is restored by merging it.
