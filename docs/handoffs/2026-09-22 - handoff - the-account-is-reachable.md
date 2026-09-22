# Handoff: the account is reachable

Date: 2026-09-22
Specification: `docs/specs/2026-09-22-the-account-is-reachable.md`
ADRs: none. Every decision is the web application's own and lives in the specification.
Blocks: 10 `feat/the-account-screen` (#193), 20 `feat/the-password-and-the-account-go` (#194),
and the closing block `fix/the-holistic-findings`.
Written in block 20, the lot's last code block, and corrected in the closing one.
Tier: Spec. The specification review ran before a line was written: 0 CRITICAL, 6 MAJOR,
12 MINOR, all closed in the document. The holistic review ran at the head of Wrap: 0 CRITICAL,
2 MAJOR, 8 MINOR, and the section below names each finding with the exit it took.

## Current state

**A signed-in user can reach the account they are in.** `/account` is a screen, opened from the
fifth navigation icon, and its heading is the name `GET /api/v1/me` answers. It carries the
password form, and a dangerous section holding "sign out everywhere" and the account's deletion.
Four routes the API served and the bundle never called are now called.

- **The three writes end the session, and the screen says so before it acts.** Each section states
  what its controls cost. On `204` or `202` the application calls `auth.forget()` and puts `null`
  in the session query; the route guard then lands the user on `/sign-in`. No cookie is cleared:
  only `DELETE /api/v1/sessions` clears `pinry_session`, and after the two `/me` writes the cookie
  survives and is dead. **Every query but the session goes with it**, the ordinary sign out
  included: the client outlives the credentials screen, and an inactive query is kept five minutes.
- **`packages/auth` gained `forget()`**, which clears `token` and `renewAfter` and calls nothing.
  Without it the package keeps a `renewAfter` in the past and its middleware spends a refused
  renewal on the credentials screen's first request.
- **The session guard is `guarded` in `router.tsx`**, wrapping a screen at its route declaration.
  The four route bodies lost three statements each; `src/test/guards.test.ts` holds it, by the
  redirect written once and by the count of wrapped declarations. The redirect replaces.
- **A refusal is read by its `code`, never its status.** `src/lib/refusals.ts` pulls the code out
  of the problem body openapi-fetch parsed, and `src/passwordRefusals.ts` gives five of the six
  codes a sentence of their own. `PASSWORD_CHANGED_TOO_SOON` and
  `TOO_MANY_AUTHENTICATION_ATTEMPTS` are both `429`, which is why the status cannot be the key.
  `UNSUPPORTED_REAUTHENTICATION_FACTOR` takes the general sentence with every unknown code: it
  says the client has a defect, which is nothing to tell the user about.
- **`X-Reauthentication` is built by `src/lib/reauthentication.ts`**, `password ` and the
  base64url of the password's UTF-8, padding kept.
- **The contract did not move**, and nothing under `api/` did. The three refusals the client now
  reads for are still undeclared there: `contract/openapi.json` carries `204, 400, 401, 403` for
  `PUT /api/v1/me/password` and `202, 401, 403` for `DELETE /api/v1/me`.

Closes the account-management half of the `Features` item "What the API serves and the web
application does not reach yet", rewritten in block 20's pull request. Import and export stay.

## The holistic review's findings

Eleven, and the exit each one took: ten fixed in the closing block, one the backlog's. The report's
own count line reads "2 MAJOR, 8 MINOR" and its body carries nine MINOR; nothing was dropped.

| Finding | Exit |
|---|---|
| MAJOR `session.ts`: ending a session left every other query in the cache, so the next account signed in on this browser was painted the previous one's | Fixed. `useEndSession` removes every query but the session, and `useSignOut` goes through it. A case in `sign-out.journey.test.tsx` holds the second account's own page back and reads what the grid paints in that window |
| MAJOR `guards.test.ts`: three of the five guarded routes would lose their guard with the suite green | Fixed. The file counts `component: guarded(` against the declarations, less the two credentials routes |
| MINOR `passwordRefusals.ts`: an inherited prototype key answers the lookup, and `constructor` takes the screen down | Fixed in both that file and `downloadReasons.ts`, the precedent it copied, with a test each |
| MINOR `me.ts`: the mutations declared an error type they cannot guarantee | Fixed. Both generics dropped, and `Refusal` narrows on `AccountRefusal` as `Boards.tsx` narrows its own |
| MINOR `Account.tsx`: a failed read of the account was silent | Fixed. `me.isError` renders the alert its sibling screens do, `account_unreadable` in both catalogues |
| MINOR `Account.tsx`: the refused revocation's toast was the lot's one error path no test reached | Fixed. A case in the account journey answers `DELETE /api/v1/sessions` with a 500 |
| MINOR `router.tsx`: the redirect pushed, so Back never left the credentials screen | Fixed. `<Navigate to="/sign-in" replace />` |
| MINOR `clients/AGENTS.md`: the `lib/` norm read absolute against `btoa` and `TextEncoder` | Fixed. The row names what the norm is about: no I/O and no ambient present, a deterministic global allowed |
| MINOR the specification's decision C described a screen that was built and then changed | Fixed. A `(Corrected: …)` clause on that decision |
| MINOR `Account.tsx`: three comments cited a decision letter with no document | Fixed. The full form in all three |
| MINOR the contract declares none of the refusal codes and statuses the client reads | The backlog, P1. It is API work this lot does not touch, and section 7 says so with its observable |

## Pitfalls

- **`btoa` alone ships a header the API refuses.** `Base64.getUrlDecoder()` throws on a `+` or a
  `/`, which the parser turns into a `400`. Most passwords produce neither, so the defect hides:
  `??>` is the one that shows it. The padding is the opposite case, accepted either way, so it
  stays rather than costing a transformation.
- **A `204` and a `202` leave `data` undefined.** Success on these three writes is read from
  `response.ok`; `bodyOf` in `api.ts` would throw on every one of them.
- **The refusal body reaches `error` whatever its content type**, openapi-fetch parsing a non-ok
  body with no look at the content type. The generated types say otherwise, the contract declaring
  no body for these statuses, which is why `refusalCode` takes `unknown`.
- **A journey that means to catch a missing `forget()` runs on `DUE_SESSION`.** The default
  `SESSION` has `renewAfter` half an hour ahead and records zero renewals either way. The count is
  read where the credentials screen's own first request lands, not after the screen behind it has
  settled.
- **The refusal sentences are tested where they live.** `passwordRefusals.ts` sits at `src/` and
  not `src/lib/`, importing the message catalogue as `downloadReasons.ts` does, so its table has a
  test in `src/test/` rather than coverage from the bound.
- **`queryClient.clear()` on the way out takes the redirect with it.** A removed query leaves its
  observer watching a destroyed one, so the guard never re-renders and the user stays on the screen
  they just left; all four session-ending journeys went red on it. The session query is written
  first and everything else removed around it.
- **Two controls in one section want one sentence, not the same one twice.** The first reading of
  the built screen showed the same line under the password form and under the dangerous section.
  A green gate cannot see that.

## What is not validated

- **No browser is in the gate.** Both blocks were read headless against the built bundle, block 20
  in Firefox with a stubbed API: the screen, the dialog, a refused change, and the dialog again at
  380 pixels wide. That is a workstation habit and not a check.
- **The deployment's own refusals are stubbed.** No test anywhere starts the API and a browser
  together, so what the two sides agree on is the contract, and the three statuses this client
  reads codes for are not in it.
- **`Retry-After` is dropped deliberately.** Both 429s carry one and neither sentence names a
  wait.
- **The holistic review ran and its findings are closed**, the table above naming each exit. What
  it named and nobody fixed is the contract's silence, which is the backlog's and not this lot's.

## Next step

Import and export, which the specification left out on purpose: a machine with states and a
resumable upload, and its own document.
