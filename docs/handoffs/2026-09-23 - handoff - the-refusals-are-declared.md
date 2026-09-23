# Handoff: the refusals are declared

Date: 2026-09-23
Specification: `docs/specs/2026-09-23-the-refusals-are-declared.md`
ADR: `docs/adr/0042-the-presentation-owns-the-refusal-codes.md`.
Blocks: 10 `refactor/the-problem-codes` (#197), 20 `feat/the-account-refusals` (#198),
30 `fix/a-missing-file-part-is-a-bad-request` (#199), 40 `fix/an-oversize-body-carries-a-problem` (#200),
50 `feat/the-pin-refusals` (#201), 60 `feat/the-recycle-bin-refusals` (#202),
70 `feat/the-board-refusals` (#203), 80 `feat/the-image-refusals` (#204),
90 `feat/the-archive-refusals` (#205), 100 `feat/no-refusal-without-a-body` (Corrected: #206).
Closing block: `fix/the-holistic-findings`, one pull request, its code and documents together
being 190 lines over 14 files (`git diff -U0` against `main`, ADR 0041), under both bounds.
Written in block 100, the lot's last code block, to be corrected in the closing one.
Tier: Spec. Two specification reviews ran before a line was written, their findings recorded in the
specification. The holistic review has not run yet: it runs at the head of Wrap. (Corrected: it ran,
`.reviews/the-refusals-are-declared-holistic.md`, 0 CRITICAL, 2 MAJOR, 10 MINOR; every finding is
listed below with its exit.)

## Current state

**Every operation of the contract declares the refusals it can return**, each with a
`ProblemDetail` body and the `enum` of its codes, so a client reads refusals from its generated
types. (Corrected: true after the closing block alone. Until then the operations reading a body
other than the two uploads did not declare the `413 BODY_TOO_LARGE` that `OversizeBodyRefusal` answers
on every route; decision "R1" below.) The contract went from `9.1.0` to `16.1.0` (Corrected: to
`17.0.0`, the closing block raising the major for the `413`s): most blocks raised the major, `oasdiff` rating
an enum added to a response that already has a body as a break (specification, section 3).

- **The wire's codes are `ProblemCode`**, a presentation enum that absorbed `FrameworkErrorCode`.
  `BaseErrorMapper.problemFor`, one exhaustive `when` over `ErrorCode`, gives each its code and
  status, so renaming an `ErrorCode` no longer renames a wire code (#197). (Corrected: not one
  constant per `ErrorCode`, as ADR 0042 decision 1 has it. The closing block maps
  `USER_DOES_NOT_EXIST` and `INVALID_PASSWORD` to `AUTHENTICATION_FAILED`, the code sign-in already
  answered them with, and deletes the three `ProblemCode`s the server could not send along with the
  dead `ErrorCode.INVALID_HTTP_AUTHORIZATION_SCHEME`.)
- **Refusals several operations repeat are one `components.responses` entry**, built by
  `SharedRefusalsFilter` from `ProblemCode` constants, and a controller names one with
  `ref = SharedRefusalsFilter.<CONSTANT>`. Every protected operation's `401` points at
  `Unauthenticated` (`AUTHENTICATION_REQUIRED`, `AUTHENTICATION_FAILED`, `SESSION_EXPIRED`).
- **No declared refusal is without a body** (block 100). The filter drops the bodyless `403`
  SmallRye added to seventeen operations, no role check here refusing one;
  `quarkus.smallrye-openapi.auto-add-bad-request-response=false` stops SmallRye adding its own `400`.
- **`ContractSchemaDeclarationTest` holds the declarations**: every declared code is a
  `ProblemCode` name, following `$ref`; every protected operation's `401` is the shared one; every
  declared non-2xx response carries a code enum. It cannot see an undeclared refusal: a new route's
  refusals stay the reviewer's to check (ADR 0042, Consequences). (Corrected: since the closing
  block it also holds each declared code to the status `problemFor`, or its `FRAMEWORK_STATUSES`
  table, answers it with, and every operation reading a body to a `413` carrying `BODY_TOO_LARGE`.)
- **Authentication is lazy** (`quarkus.http.auth.proactive=false`): a stale or wrong token no
  longer refuses the public routes, sign-in included (#198, the tier-2 answer below).
- **Two defects fixed**: a multipart upload with no `file` part answers `400 VALIDATION_ERROR`
  instead of `500` (#199); a body whose `Content-Length` passes the body limit answers `413
  BODY_TOO_LARGE` with a problem, through `OversizeBodyRefusal` (#200). `BodyLimitCheck` refuses
  the boot when an upload limit is not strictly under the body limit.
- **The web application reads the account refusals from the contract**: `packages/auth` exports
  `RefusalCode<Path, Method>`, and `passwordRefusals.ts` is keyed by the codes the two account
  operations declare (#198). The other screens do not consume the new declarations yet
  (specification, section 7).

The backlog item "The contract declares none of the refusals the web application reads" was
deleted in #198.

## Tier-2 questions and operator decisions

| Where | Question | Answer |
|---|---|---|
| Block 20 (#198) | A stale or wrong session token turned the public routes into a `401`, signing in again after a password change included: (A) lazy authentication, or (B) declare the `401` and backlog the defect | "A". The probe is a regression test in `SessionAuthIntegrationTest` |
| Block 40 (#200) | A chunked body carries no `Content-Length`, and RESTEasy Reactive ends its `413` itself, so no route or mapper can give it a body | "o1 + n1": the bodyless `413` past the body limit is accepted and each upload route's `413` says so (n1); `BodyLimitCheck` removes the configuration trap (o1). The `IOExceptionMapper` warning stays |
| Lead's session | The web application's journey tests time out under local load (blocks 40, 60, 80, 90; green on rerun and in continuous integration) | "P1": the backlog at Wrap, not fixed in this lot |
| Lead's session | Block 60 pushed outside the sandbox after the sandbox refused the command | "Q2": a teammate whose command the sandbox refuses stops and reports a blocker rather than rerunning it outside |
| Wrap (holistic review) | `OversizeBodyRefusal` answers `413 BODY_TOO_LARGE` on every route, yet only the two uploads declared it: (R1) declare it on every operation reading a body, a contract break, or (R2) state the gap in this handoff | "R1, et on assure que toutes les routes qui doivent l'avoir l'ont, structurellement." `SharedRefusalsFilter` adds the `413` to every operation with a `requestBody`, merging into an operation's own `413`, and `ContractSchemaDeclarationTest` refuses an operation reading a body without it. Contract `17.0.0` |

Block 90 also split `ReauthenticationFailed` at the lead's request: the two routes reading
`X-Reauthentication` name `ReauthenticationHeaderFailed`, whose description says an absent header
lands there. (Corrected: `ReauthenticationFailed` then served `PUT /me/password` alone, and the
closing block inlines it there.)

## Holistic review findings and their exits

Every finding of `.reviews/the-refusals-are-declared-holistic.md` was fixed in the closing block.

| Finding | Exit |
|---|---|
| MAJOR `BaseErrorMapperTest.kt:31`: the test tied the wire's names to `ErrorCode`'s | Fixed: it now asserts no two `ErrorCode`s share a wire code but the pair merged on purpose |
| MAJOR `agents/engineering.md:127`: the declaration rule lived in no living document | Fixed: one bullet under "This project's API contract", naming `SharedRefusalsFilter` |
| MINOR `OversizeBodyRefusal.kt:27`: `BODY_TOO_LARGE` undeclared on most operations reading a body | Fixed, decision "R1" |
| MINOR ADR 0021's status line | Fixed: decision 2 superseded by ADR 0042, the oversize Consequence amended |
| MINOR `agents/engineering.md:134`: lazy authentication, the `413` wording, a long line | Fixed |
| MINOR `ProblemCode.kt:22`: three codes the server cannot send | Fixed, the deviation from ADR 0042 decision 1 recorded under Current state |
| MINOR `SharedRefusalsFilter.kt:22`: any bodyless `403` dropped | Fixed: only SmallRye's own, described "Not Allowed", is dropped |
| MINOR `ContractSchemaDeclarationTest.kt:123`: a code never held to its status | Fixed: `problemFor` is public and the test reads it |
| MINOR `SharedRefusalsFilter.kt:90`: `UnsupportedReauthenticationFactor`'s description | Fixed |
| MINOR `SharedRefusalsFilter.kt:82`: `ReauthenticationFailed` shared by one operation | Fixed: inlined on `MeController.changePassword` |
| MINOR `clients/packages/auth/src/index.ts:24`: an untested branch | Fixed: deleted, the server-side test refusing the case it guarded |
| MINOR this handoff, line 10 and the opening sentence | Fixed: the corrections above |

## Pitfalls

- **Declaring an operation's own `400` or `403` stops SmallRye adding its own**, and declaring any
  response stops it generating the success: an operation that declares a refusal declares its
  success too (specification, section 3).
- **An empty `enumeration` widens the client's union to `string`**: openapi-typescript generates
  `code?: unknown`, and `RefusalCode` keeps string literals only (#198). Block 100's test now
  refuses such a declaration on the server side.
- **`SchemaProperty` over `allOf` puts no `type` beside `properties`**, and openapi-typescript
  renders `{ code?: "A" | "B" } & ProblemDetail`. The filter's shared entries take the same shape.
- **`PUT /pins/{pinId}/image` is two methods SmallRye merges**: a status both arms answer is
  declared on both with the same union. Its `422` sits on the multipart arm alone (#204).
- **A detekt baseline identifier carries the function's annotations**: declaring a response on a
  function with a baselined finding brings the finding back (#204).
- **`UNKNOWN_ROUTE` on a route with no path value** comes from a query value JAX-RS cannot convert,
  `limit=abc` for one (#201, #203).
- **`oasdiff` does not rate a removed non-success status as a break**: block 100 drops seventeen
  `403`s and the guard admits the diff at `16.0.0`; the minor is raised by the specification's rule.
- **The web application's journeys time out under local load**: a full `dagger call gate` beside a
  busy machine goes red on 15-second timeouts, `dagger call clients-gate` alone passes. Decision
  "P1" above. (Corrected: filed in the backlog under `P1` by the closing block.)
- **A hand-edited `contract/openapi.json` leaves `:api-application:test` up to date**: the document is
  read from the working tree and is no input of the task, so a mutation of it is only seen with
  `--rerun` (closing block).

## What is not validated

- **An undeclared refusal passes the gate.** The contract test reads what is declared; a new route
  that omits a refusal is caught only by review.
- **A chunked body past the body limit still gets a bodyless `413`**, by decision "n1".
- **`auto-add-bad-request-response=false` changes nothing observable today**: every operation with
  an input already declares its `400`. It holds for a route added later.
- **No browser is in the gate**; the web application's reading of the new declarations is limited
  to the account screen.
- **The holistic review has not run.** It runs at the head of Wrap, over
  `git diff lot/0.35.0-the-block-budget..origin/main`. (Corrected: it ran; its findings are above.)
- **`FRAMEWORK_STATUSES` is written by hand** in `ContractSchemaDeclarationTest`: a framework
  mapper that changes a code's status is not seen there, its own test being what pins it.

## Next step

Wrap: the holistic review, then the closing block with its findings, the backlog entry for the
journey timeouts (decision "P1") and this handoff corrected; then the lot's tag. (Corrected: all
done but the tag, `lot/X.Y.Z-the-refusals-are-declared` on the closing merge, which the lead
pushes.)
