# Handoff: a call past `renewAfter` renews the session

Date: 2026-09-14
Branch: `fix/session-renewal-on-outgoing-call`, one block, pull request #134
Tier: Direct, written inline by the lead. No specification, and **the holistic review did not run**,
which Direct skips by the tier table rather than by a waiver the operator gave.

## Current state

`dagger call gate` green at `b297bee1`, run by `pre-push`. Continuous integration green on #134:
`verify` 2 m 52 s, `gate` 3 s. The diff is 125 counted lines against 600, with 48 production lines
under `clients/` against 400 and none under `api/`.

The contract did not move: `POST /api/v1/sessions/current/renew` has been served since the session
lot and this block is the first caller.

## What was built

- **`packages/auth` renews before the call that found the session due.** The `onRequest` middleware
  that already carried the bearer token now also holds the `renewAfter` the last session answer left
  behind. Past that date, the call awaits a renewal and then goes out.
- **One renewal is shared** across the calls a page load leaves with, through a single in-flight
  promise cleared on settle.
- **`adopt`** factors what all three session answers leave behind, the token and `renewAfter`, out of
  `openSession`, `currentSession` and the new `renew`.
- **The journey `session renewal`**, two tests: the shared renewal counted at exactly one, and a
  refused renewal that still lets its call through.
- **The test fixture's session is dated from the run.** Its fixed dates had fallen into the past,
  which the new check reads as a session permanently due.

The web application was not touched. The check sits at the one point every route of both clients
passes through, so the extension inherits it unwritten.

## Pitfalls

- **The renewal excludes itself** by `schemaPath`, or it checks itself in a loop (openapi-ts.dev,
  Middleware & Auth).
- **The `Authorization` header is set after the renewal, not before.** A bearer renewal answers a new
  token. The web application is on the cookie and would never have shown this; the extension will.
- **A refused renewal is swallowed** and the call goes out, so only the API's own 401 ends a session.
  The stored `renewAfter` stays past, so the next call tries again, which is what a transient failure
  wants.
- **MSW answers an undeclared route with an error**, so a journey whose session is due and which
  declares no renewal route fails rather than warning. That is why the fixture is future dated.

## What is not validated

- **Nothing exercises the bearer half.** The renewal's token adoption is written for the extension,
  which does not exist; the journeys are all on the cookie.
- **The query cache keeps the session it first read.** Nothing in the application reads `expiresAt`
  or `renewAfter`, so a renewal does not refresh what `useSession` holds. Visible the day a screen
  shows a session's expiry.
- **No holistic review**, Direct skipping it.

## Next step

Nothing this block opens. The backlog's remaining `P1` on the web application is the manual theme
switch; the deployment (`Caddyfile` and a webapp image) is still what turns the application into
something a user can open.
