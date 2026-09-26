# 0044. A response code declares its set, closed or extensible

Status: Accepted
Date: 2026-09-26
Specification: `docs/specs/2026-09-26-the-codes-declare-their-sets.md`.
Amends: `docs/specs/2026-09-25-the-data-travels.md`, decision I, whose open fields stayed plain strings.

## Context

Every code a response carries was already weighed open or closed (web application specification
4.10, ADR 0042, data-travels decision I), but the open ones were published as plain strings: no
client could learn their values, a renamed domain constant changed the wire silently, and our own
client's sentence tables drifted with nothing to say so. One closed set, the export's state, held
three values no client tells apart.

## Decision

1. **A response code is closed when an unknown value has no correct default, extensible when it
   has one.** A state is closed; a reason or a kind is extensible, carried beside a closed status.
   A route's refusal codes stay closed per response (ADR 0042): a new one changes what the route does.
2. **An extensible code is published as `x-extensible-enum`**, which `oasdiff` v1.31.0 reports
   neither on an added nor on a removed value, where a closed `enum` gaining one is a break.
3. **Every code, closed or extensible, is a presentation enum** mapped from the domain by an
   exhaustive `when`. A build-stage filter turns the extensible ones' components into
   `x-extensible-enum`.
4. **The client generates through `openapi-typescript`'s Node API with a `transform`**, the command
   line having none: an `x-extensible-enum` becomes its literals or any other string, and
   `Known<T>` keeps the literals, so a table keyed by it fails `tsc` when it misses one. (Corrected:
   block 25 uses the Node API with no `transform`. The field stays `string`, openapi-fetch's `Readable`
   mapping `string & {}` as an object; `generate.mjs` appends an `extensibleEnums` interface of each
   component's known values, which `Known<"Component">` reads. The rest of the decision holds.)
5. **A closed set holds only values that change the client's behaviour.** The export's `EXPIRED`,
   `DELETED` and `SUPERSEDED` become `GONE`, their cause in an extensible `reasonCode`, the field's
   name on every row.

**Fails if** a client needs to act differently on two values of one extensible code: that code then
hides a state, and the state is split out and closed.

## Consequences

- **A new reason or kind breaks no client**, and the contract guard does not ask for a major; a new
  state still does.
- **The generation step is code of ours**, about twenty lines against a documented option, and a
  committed type assertion fails `tsc` if it stops producing the literals. (Corrected: `generate.mjs` is
  11 lines calling `openapiTS` with no option and appending its interface to the output; no type
  assertion is committed, a lost step failing `index.ts` with TS2305 on the missing interface.)
