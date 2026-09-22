# Handoff: the account is reachable

Date: 2026-09-22
Specification: `docs/specs/2026-09-22-the-account-is-reachable.md`
ADRs: none. Every decision is the web application's own and lives in the specification.
Blocks: 10 `feat/the-account-screen` (#193), 20 `feat/the-password-and-the-account-go` (#194).
Written in block 20, the lot's last code block.
Tier: Spec. The specification review ran before a line was written: 0 CRITICAL, 6 MAJOR,
12 MINOR, all closed in the document. The holistic review runs at the head of Wrap, this lot
holding two blocks, and the closing block corrects this document with what it found.

## Current state

**A signed-in user can reach the account they are in.** `/account` is a screen, opened from the
fifth navigation icon, and its heading is the name `GET /api/v1/me` answers. It carries the
password form, and a dangerous section holding "sign out everywhere" and the account's deletion.
Four routes the API served and the bundle never called are now called.

- **The three writes end the session, and the screen says so before it acts.** Each section states
  what its controls cost. On `204` or `202` the application calls `auth.forget()` and puts `null`
  in the session query; the route guard then lands the user on `/sign-in`. No cookie is cleared:
  only `DELETE /api/v1/sessions` clears `pinry_session`, and after the two `/me` writes the cookie
  survives and is dead.
- **`packages/auth` gained `forget()`**, which clears `token` and `renewAfter` and calls nothing.
  Without it the package keeps a `renewAfter` in the past and its middleware spends a refused
  renewal on the credentials screen's first request.
- **The session guard is `guarded` in `router.tsx`**, wrapping a screen at its route declaration.
  The four route bodies lost three statements each; `src/test/guards.test.ts` holds it.
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
- **The holistic review has not run**, this document being written in the last code block. The
  closing block records its findings here.

## Next step

Import and export, which the specification left out on purpose: a machine with states and a
resumable upload, and its own document.
