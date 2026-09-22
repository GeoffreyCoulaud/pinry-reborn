# The account is reachable

Date: 2026-09-22
Status: Approved by the operator on 2026-09-22; one specification review ran, its 0 CRITICAL,
6 MAJOR and 12 MINOR closed in this document. Frozen when the lot's closing block merges.
Branches: block 10 `feat/the-account-screen`, block 20 `feat/the-password-and-the-account-go`
ADRs: none. The decisions below pick no library, no library setting, no storage format, no protocol
between two components and no boundary. They move no public surface the API publishes, the API
being untouched, and `packages/auth`'s own interface gains one method with one caller, which is a
package change and not a boundary move; they define no error contract either: decision D *reads*
the one `BaseErrorMapper` already publishes. They are the web application's own, so they go to this
document and nowhere else, as decisions I to N of
`docs/specs/2026-09-21-the-header-searches-and-wears-the-name.md` did.

`docs/specs/2026-09-20-the-pin-is-editable-and-the-boards-arrive.md` left four things the API serves
and the web application does not reach. The lot before this one took search. This one takes account
management, and leaves import and export, which is a machine with states and a resumable upload and
deserves its own document.

## 1. Goal

A signed-in user can read the name of the account they are in, change its password, close every
session it holds, and delete it. Four routes the API already serves and the bundle never calls.

## 2. What exists today

- **`GET /api/v1/me` answers `UserOutputDto`**, which is `id` and `name` and nothing else
  (`contract/openapi.json`). No file under `clients/apps/webapp/src` calls it: the header wears the
  product's name, not the account's (`AppHeader.tsx`).
- **`PUT /api/v1/me/password` takes `currentPassword` and `newPassword`** and answers `204`.
  `PasswordChanger.changePassword` ends with `sessionRevoker.revokeAll(user)`, inside the same
  transaction as the write
  (`api/api-usecases/src/main/kotlin/.../usecases/PasswordChanger.kt`).
- **`DELETE /api/v1/me` answers `202`** and requires the header `X-Reauthentication`, parsed as
  `password <base64url(password)>`: absent is `403`, unparseable or an unsupported kind is `400`
  (`.../presentation/quarkus/security/ReauthenticationHeader.kt`). `AccountDeleter.requestDeletion`
  also calls `revokeAll`, then enqueues the deletion task.
- **`DELETE /api/v1/sessions` answers `204`** and revokes every session the account holds, the
  caller's included; on a cookie transport the response clears `pinry_session`
  (`SessionController.revokeAllSessions` and its `revocationResponse`). The two `/me` writes clear
  no cookie: `MeController` returns a bare `204` and a bare `202`, so the browser keeps a cookie
  that is simply dead, every request with it earning a `401`.
- **The refusals carry a `code`** in an `application/problem+json` body (`ProblemDetail`). The ones
  these two writes can produce, with the status `BaseErrorMapper.statusFor` gives each:

  | `code` | Status | What happened |
  |---|---|---|
  | `REAUTHENTICATION_FAILED` | 403 | The current password is wrong, or the deletion header is absent |
  | `PASSWORD_PREVIOUSLY_USED` | 422 | The new password is one this account has held before |
  | `PASSWORD_CHANGED_TOO_SOON` | 429 | The current password is younger than the minimum interval |
  | `PASSWORD_CHANGE_COLLISION` | 409 | Another change landed first |
  | `TOO_MANY_AUTHENTICATION_ATTEMPTS` | 429 | The attempt limiter is holding the account closed |
  | `UNSUPPORTED_REAUTHENTICATION_FACTOR` | 400 | The deletion header did not parse. Not a user error: a defect in decision G's encoder is what produces it |

  Two of the six share a status, which is why the client reads the code.

  **The table is read off `BaseErrorMapper.statusFor` and not off the contract**, which declares
  neither these statuses nor their bodies: `contract/openapi.json` carries `204, 400, 401, 403` for
  `PUT /api/v1/me/password` and `202, 401, 403` for `DELETE /api/v1/me`. That is why `refusalCode`
  takes `unknown` below, and why annotating the controllers is section 7's work and not this lot's.
- **Both 429s carry a `Retry-After`.** `PasswordChangedTooSoonError` and
  `TooManyAuthenticationAttemptsError` are both `ThrottledError`, and `BaseErrorMapper` sets the
  header for every one of them.
- **`packages/auth` holds the session and the transport.** `signOut()` calls
  `DELETE /api/v1/sessions/current` and then clears its `token` and `renewAfter`. Nothing else
  clears them: an application that revokes a session by another route leaves the package believing
  one is open, and its `renewIfDue` middleware then spends a request on a renewal the API refuses.
- **The application calls `auth.client` directly** for everything that is not the session itself
  (`boards.ts`, `images.ts`, `pins.ts`, `recycled.ts`). Only `packages/api-client` and
  `packages/auth` import the HTTP client, which is the boundary `pnpm run boundaries` enforces, and
  `auth.client` is inside it. `images.ts` is the closest precedent for `me.ts`: it already calls a
  route under `/api/v1/me`.
- **Four routes repeat the same session guard**, three statements each, and five lines in `Home.tsx`
  where a comment sits between them: `Home.tsx:86-90`, `Boards.tsx:156-158`, `Board.tsx:25-27`,
  `Recycled.tsx:233-235`.
- **`downloadReasons.ts` sits at `src/` and not `src/lib/`** because it imports the message
  catalogue, which `clients/AGENTS.md` forbids `lib/` to do. Anything mapping a code to a sentence
  lands outside the coverage perimeter for that reason.

## 3. The decisions

**A. The account is a route, `/account`, reached from a fifth icon in `AppNav`.** Two sections: the
password, and the dangerous part, which carries closing every session and deleting the account. The
account's name is the screen's heading and no section of its own, `UserOutputDto` holding nothing
else worth showing. The alternative was a menu on the account's name in the header, which would
move `Sign out` two lots after it was put where it is, and would ask `GET /api/v1/me` on every
screen for a name nobody reads.

**B. Deleting the account asks for the password in a dialog.** The password is the factor
`X-Reauthentication` requires anyway, so the friction is real rather than decorative; the dialog is
what keeps a delete button from sitting armed on an open screen. Retyping the account's name was
refused: one more field, and a copy and paste crosses it.

**C. The three writes end the session, and the screen says so before and acts on it after.** A line
under each control states that it closes every session, this one included. On `204` or `202` the
application clears the session and lands on `/sign-in`. Reopening a session behind the user's back
with the new password was refused: it keeps a credential in memory to replay a sign-in.

Clearing the session is `auth.forget()` and the session query, and no cookie: only the sessions
route clears `pinry_session`, and after the two `/me` writes the cookie survives and is dead. A
test asserting a cleared cookie on those two cannot pass.

**D. Each refusal in the table above has its own sentence**, bar the last.
"Already used" and "too soon" are refusals the user can act on; collapsed into one "that was
refused" they send the user round in a circle. `TOO_MANY_AUTHENTICATION_ATTEMPTS` and
`PASSWORD_CHANGED_TOO_SOON` share a status, so the `code` is what is read, and a code this bundle
does not know falls back to one general sentence, as `downloadReasons.ts` falls back to the
server's own message. `UNSUPPORTED_REAUTHENTICATION_FACTOR` takes that fallback too: it is a client
defect and there is nothing to tell the user about it.

The `Retry-After` the two 429s carry is dropped, and the sentences say to try again later without
a number. Naming it means reading a header the typed client does not surface and formatting a
duration in two locales, for a refusal that a self-hosted account meets when it has just changed
its password and immediately changed it again.

**E. `packages/auth` gains one method, `forget()`**, which clears `token` and `renewAfter` and calls
nothing. The three writes here are revocations the API has already performed, so the package needs
to stop believing in a session rather than to end one; `signOut()` keeps doing both. The `DELETE`
calls stay in the application, beside every other call it makes through `auth.client`.

**F. The session guard is extracted, in block 10, and the account screen is its fifth caller.** The
operator adopted it on 2026-09-22: a screen that needs the guard is the lot that should write it
once, and filing it would have preserved the cause. It goes where the routes are declared, in
`router.tsx`, as a function wrapping a screen's component:

```tsx
component: guarded(Home)
```

A wrapper at the declaration and not inside each body, because a component wrapping the screen's
own JSX would re-indent four whole files, and a hook returning an element would have to answer
"nothing to render" and "carry on" with two different values. Here the route bodies lose three
statements, one call and one import each, and nothing else moves. `Credentials` is not wrapped: it
is the screen a visitor with no session is sent to.

**G. The reauthentication header is built by a pure function** in `src/lib/`, which is where the
coverage bound bites. It encodes the password as UTF-8, then base64url, and keeps the padding:
`btoa` pads, so keeping it is one transformation fewer, and `Base64.getUrlDecoder()` accepts padded
and unpadded alike (measured on JDK 25.0.4-tem on 2026-09-22: `c2VjcmU=` and `c2VjcmU` both decode
to `secre`).

**What the function may not do is stop at `btoa`.** The same decoder refuses standard base64:
`decode("Pz8+")` throws `Illegal base64 character 2b`, which the parser turns into a `400`. The
password `??>` produces exactly those bytes, so the `+` and `/` have to become `-` and `_`.

`btoa` and `TextEncoder` are the first platform globals under `src/lib/`. The norm there is "no
fetch, no DOM, no clock": its target is I/O and the ambient present, and both of these are
deterministic functions of their argument, available in Node and in jsdom. Nothing enforces the
norm mechanically, `.dependency-cruiser.json` carrying no rule about `lib/`.

**H. The password form is on the account screen, not on a route of its own.** Two sections on one
screen is one route, one guard and one heading; two routes would be two of each for two forms
nobody navigates between.

## 4. The change

Under `clients/apps/webapp/src`:

- `routes/Account.tsx`: the screen. `AppHeader` with the account's name as its heading, `AppNav`,
  then the password form and the dangerous part.
- `me.ts`: the account's queries and mutations, as `boards.ts` is the boards'. `useMe`,
  `useChangePassword`, `useDeleteAccount`, all through `auth.client`.
- `session.ts`: `useSignOutEverywhere`, beside `useSignOut`, and one `endSession` the three writes
  share, which calls `auth.forget()` and puts `null` in the session query.
- `lib/refusals.ts`: `refusalCode(error: unknown): string | null`, which reads the `code` out of
  whatever `openapi-fetch` left in `error` and answers `null` for anything that is not a problem
  body. Pure, and inside the coverage bound.
- `passwordRefusals.ts`: the code to sentence table, at `src/` for the reason section 2 gives.
- `lib/reauthentication.ts`: `passwordFactor(password: string): string`, decision G.
- `router.tsx`: the `/account` route, and `guarded`, decision F.
- `routes/{Home,Boards,Board,Recycled}.tsx`: the guard deleted, and the `useSession` call and
  import that served it alone.
- `components/AppNav.tsx`, `lib/journeys.ts`, and `messages/{en,fr}.json`.

Under `clients/packages/auth/src`: `forget()`, decision E.

Nothing under `api/`, and `contract/openapi.json` does not move.

## 5. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `feat/the-account-screen` | Journey **open the account and sign out everywhere**, run on `DUE_SESSION` (`src/test/app.tsx`), whose `renewAfter` is a minute in the past: `/account` reached from the navigation icon shows the name `GET /api/v1/me` answered and not the product's name alone; the control sends `DELETE /api/v1/sessions` once; the credentials screen follows; and the first request that screen makes records **zero** `POST /api/v1/sessions/current/renew`, where the same journey without `forget()` records one. That count is what holds decision E up, and it is why the journey does not run on the default `SESSION`, whose `renewAfter` is half an hour ahead and which records zero either way. A visitor with no session reaching `/account` lands on `/sign-in`, which discriminates from a screen that renders empty. Decision F's own check is the suite as it stands, every journey that renders a guarded route passing with no assertion changed and `session expiry` still reading the alert, plus one count: no file under `clients/apps/webapp/src/routes/` holds `Navigate to="/sign-in"` any more, and `router.tsx` holds it once |
| 20 | `feat/the-password-and-the-account-go` | Journey **change the password**, on `DUE_SESSION`: the form sends `currentPassword` and `newPassword` to `PUT /api/v1/me/password`, on `204` the credentials screen follows, and that screen's first request records zero renewals. Each of the six codes in section 2's table produces its own sentence bar the last, and the two that share a status produce two different ones, which is the assertion that fails if the client reads the status; a code outside the table produces the general sentence and not `undefined`. `refusalCode` answers the `code` of a parsed `ProblemDetail`, and `null` for a string body, for `null`, and for an object with no `code`. Journey **delete the account**, on `DUE_SESSION`: the control opens a dialog, the dialog sends `DELETE /api/v1/me` with `X-Reauthentication` equal to `password ` plus the base64url of what was typed, on `202` the credentials screen follows, and that screen's first request records zero renewals; a dialog dismissed sends nothing. On `403` with `REAUTHENTICATION_FAILED` the dialog stays open, shows the wrong-password sentence and sends nothing further; on `429` with `TOO_MANY_AUTHENTICATION_ATTEMPTS` it shows that sentence and not the wrong-password one, which is the discriminator that the code and not the status is read here too. `passwordFactor("??>")` ends in `Pz8-` and its whole output matches `/^password [A-Za-z0-9_-]+=*$/`, a character class being what fails on a `+` where a round trip through a decoder does not; and a non-ASCII password encodes to the bytes its UTF-8 has, not one byte per character |

Each block is green and coherent alone. Block 10 ships the screen with the dangerous part holding
one control, and the password form and the delete control are absent rather than disabled. Block 20
adds both to a screen that already exists.

**Two blocks and not three.** The password and the deletion were drafted apart and the operator
merged them on 2026-09-22: at about 485 counted lines and 230 production they clear the strict 600
and the 400 with room, and the deletion's refusal criteria read `refusalCode` and the sentence
table, which the password half writes. Written apart they would have merged in that order anyway.

**Its seam, if the estimate is wrong**, is where they were split: the password form on one side,
the dialog and `passwordFactor` on the other. Block 20 measures `git diff --numstat` at its first
green run like any other, and splits there rather than argue with the bound.

Estimated diffs, in lines `git diff --numstat` would count: block 10 about 270, of which about 185
production under `clients/`, decision F's extraction being about 40 counted lines of it, a third of
them deletions; block 20 about 485 and 230, the two halves it merges being about 270 and 135 for
the password and about 215 and 95 for the deletion. The estimate is
not evidence: each block sums `git diff --numstat` against `main` at its first green run and says
so in its pull request.

## 6. Adjacent backlog items

| Item | Exit |
|---|---|
| **What the API serves and the web application does not reach yet** (Features) | Closed in part and rewritten, not deleted, in block 20's pull request. What stays is import and export, and the pointer becomes this document |
| **Advanced pin / tag / board management** (Features) | Left open, for the reason the previous lot gave: it asks for a data model that is genuinely user segmented, which no screen delivers |
| **Two-factor / step-up authentication** (Features) | Left open, and adjacent in one direction only: `X-Reauthentication` is the step-up header this lot sends its first value of, with the kind `password`. A second kind is that item's work, and the header's parser already refuses one |
| **Public profiles** (Features, gated on audience mechanics) | Left open, gated as the backlog's own heading says. It is the other half of this lot's subject and it needs an audience before it needs a screen |
| **Audience mechanics** (Features) | Left open. Every screen here is owner scoped, as the previous lot's were |
| **Browser-extension CORS origin** (`P1`) | Left open, its reason unchanged: the extension has no stable identifier. `forget()` is in `packages/auth`, which the extension will share, and changes nothing about its origin |
| **Import follow-ons** (`P1`) | Not adjacent. No block here touches the import |
| **A table rebuild's row-carrying path is exercised by nothing** (`P2`) | Not adjacent: no block touches `api/` |
| **`foreign_keys` is off** (`P2`) | Not adjacent for the same reason, and worth naming because deleting an account is the operation that would most obviously want the pragma. It is enqueued as a task on the API's side and this lot does not touch it |
| **Flatten the migration history** (Before beta) | Left open, and no block needs it |
| **Populate `contract/frozen/`** (Before beta) | Untouched. This lot does not move the contract at all |
| **Authentication attempt counters are per process** (Known limits) | A recorded limit, not work. It is what `TOO_MANY_AUTHENTICATION_ATTEMPTS` in section 2's table comes from, and decision D gives it a sentence rather than working around it |
| **The grid keeps every page it scrolls** (Known limits) | A recorded limit, and no block here renders a grid |

This lot files no item. The duplicated session guard, which a first draft of this document proposed
to file, is fixed in block 10 instead: the operator refused the filing on 2026-09-22, the lot that
needs the guard being the one that should write it once.

## 7. Out of scope

Each row names how a reader notices if it changed anyway.

| Not done | Observable |
|---|---|
| Import and export | No call to `/api/v1/me/imports` or `/api/v1/me/exports` |
| Renaming the account | `UserOutputDto` is read and never written; the API serves no such route |
| Listing the sessions a user holds, or revoking one of them | The API serves no such route; the screen offers all or nothing |
| Any change under `api/`, the missing refusal annotations included | `git diff --numstat main...HEAD` names no path under `api/`, and `contract/openapi.json` is unchanged, so the 409, 422 and 429 of section 2's table stay undeclared |
| Any other screen's behaviour | The four routes decision F opens lose their guard and gain nothing; every journey that renders them passes with no assertion changed |
| Naming the wait a 429 carries | No client code reads `Retry-After`, and the two sentences hold no number |
| A second reauthentication kind | `passwordFactor` is the only producer of the header, and it writes `password ` |
| Changing what `signOut` does | `packages/auth` gains `forget` and no existing method's body moves |
| The extension | `clients/apps/extension` absent |

## 8. Pitfalls

- **A `204` and a `202` both leave `data` undefined.** `openapi-fetch` 0.17.0 returns
  `{ data: undefined, response }` for a 204 or a zero-length body
  (`node_modules/.pnpm/openapi-fetch@0.17.0/node_modules/openapi-fetch/src/index.js`, lines 238 to
  246), so success on these three writes is read from `response.ok` and never from `data`. `bodyOf`
  in `api.ts` would throw on every one of them.
- **The refusal body reaches `error` whatever its content type.** The same file parses a non-ok
  response with `response.text()` and then attempts `JSON.parse`, with no look at the content type
  (lines 267 to 274), so `application/problem+json` arrives parsed. What the generated types say
  about it is another matter: the contract declares no body for these statuses, so `error` is typed
  as holding nothing and `refusalCode` takes `unknown`.
- **`btoa` alone ships a header the API refuses**, decision G. The failure is not in every password,
  which is what makes it a pitfall: it needs a `+` or a `/` in the encoding, and most passwords
  produce neither.
- **Changing the password signs the user out**, and that surprises whoever wrote the form more than
  whoever uses it. The API revokes inside the write's transaction, so there is no window in which
  the old session still works.
- **`forget()` is not optional after these writes.** Left out, `packages/auth` keeps a `renewAfter`
  in the past and its middleware spends a renewal request, refused, on the first call the
  credentials screen makes. A journey run on the default `SESSION` cannot see it: that fixture's
  `renewAfter` is half an hour ahead, so `renewIfDue` returns immediately either way.
- **A wrapped screen is mounted later than it is today**, and two routes notice. `Board.tsx` calls
  `useBoards()` above its guard and `Home.tsx` calls `useHandshake()` and mounts its drag listeners
  there, so today both ask before the session is known; behind `guarded` they ask after. The
  requests are the same requests, in a different order, which is what to suspect first if a journey
  goes red on a count.
- **A new journey goes into `lib/journeys.ts` in the same block as its file.** `journeys.test.ts`
  compares the list against `src/journeys/` in both directions.
- **Every message exists in both locales.** Paraglide falls back in silence; `catalogues.test.ts`
  is what catches the French sentence nobody wrote.
