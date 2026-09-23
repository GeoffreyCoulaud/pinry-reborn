# The refusals are declared

Date: 2026-09-23
Status: Draft; one adversarial review closed, its twelve findings recorded in this document. Frozen
when block 10 merges.
Branch of block 10: `feat/the-refusals-are-declared`
ADR: `docs/adr/0041-the-presentation-owns-the-refusal-codes.md`.

## 1. Goal

Close the backlog item "The contract declares none of the refusals the web application reads". Today
renaming a refusal code leaves the gate green and the client showing the general sentence.

## 2. What exists today

- `contract/openapi.json` declares `204, 400, 401, 403` for `PUT /api/v1/me/password` and
  `202, 401, 403` for `DELETE /api/v1/me`, no body on any of them. `changePassword`'s `204` is
  generated: SmallRye stops generating it once the operation declares a response
  (`TagSearchController.kt:29-30`).
- `ProblemDetail.code` is a `String`, filled from `ErrorCode` (`api-usecases`) by `BaseErrorMapper`
  and from `FrameworkErrorCode` by the other mappers.
- `passwordRefusals.ts` keys its sentences in a `Record<string, …>`; `lib/refusals.ts` takes
  `unknown` because the contract declares no body.
- `POST /api/v1/me/exports` goes through `Reauthenticator`, and its `429` names only
  `EXPORT_TOO_SOON`.

## 3. The change

| Where | What |
|---|---|
| `mappers/ProblemCode.kt` | Replaces `FrameworkErrorCode`: its constants plus one per `ErrorCode`, same spelling |
| `BaseErrorMapper` | One exhaustive `when` from `ErrorCode` to `ProblemCode` and status, replacing `statusFor` |
| `ProblemResponses`, the other mappers | Take a `ProblemCode`; `ProblemDetail.code` stays `String` and receives its `name` |
| `MeController.changePassword` | `204`, then a `ProblemDetail` body per refusal: `400` (`VALIDATION_ERROR`, `MALFORMED_BODY`), `403` (`REAUTHENTICATION_FAILED`), `409` (`PASSWORD_CHANGE_COLLISION`), `422` (`PASSWORD_PREVIOUSLY_USED`), `429` (`PASSWORD_CHANGED_TOO_SOON`, `TOO_MANY_AUTHENTICATION_ATTEMPTS`) |
| `MeController.deleteAccount` | `400` (`UNSUPPORTED_REAUTHENTICATION_FACTOR`), `403` (`REAUTHENTICATION_FAILED`), `429` (`TOO_MANY_AUTHENTICATION_ATTEMPTS`) |
| `MeExportController.requestExport` | Its `429` also names `TOO_MANY_AUTHENTICATION_ATTEMPTS`, as a description only |
| `ContractSchemaDeclarationTest` | Every `code` enum under a response is a subset of `ProblemCode`'s names |
| Mapper unit tests | The 14 `assertEquals("<CODE>", …code)` in six files still compare strings, and pass; `BaseErrorMapperTest` gains one case over `ErrorCode.entries`: the body's `code` equals the constant's name |
| `contract/openapi.json` | Regenerated; `info-version` 9.1.0 to 9.2.0 |
| `packages/auth`, `passwordRefusals.ts`, `me.ts` | `REFUSALS` keyed by the union of codes the two operations declare, read from their generated error types (`packages/auth` exposes what is needed next to `Schemas`); the lookup narrows with a type guard, the `constructor` test kept |
| `lib/refusals.ts` | Its comment no longer says the contract declares no body |
| `agents/engineering.md` | "Status codes" names `ProblemCode` and the `when` in `BaseErrorMapper` |
| `docs/backlog.md` | The item deleted; one item added: the other routes' refusals declare no codes (ADR 0041, Consequences) |

Each refusal is declared as `Schema(allOf = [ProblemDetail::class], properties = [SchemaProperty(name = "code", enumeration = [...])])`,
one-line description. A spike on 2026-09-23 (reverted) measured this shape: SmallRye writes it
as-is, openapi-typescript renders `code` as the union, and `oasdiff` v1.31.0 reads `main` to it as
no break and a value added to it as one error.

## 4. Acceptance

- Every integration test asserting a `code` on the wire passes unchanged.
- `jq '.paths["/api/v1/me/password"].put.responses | keys'` gives `204, 400, 401, 403, 409, 422, 429`,
  and `.paths["/api/v1/me"].delete` gives `202, 400, 401, 403, 429`; each of those 4xx but `401`
  carries `application/problem+json` with the `code` enum of section 3.
- `contract-guard` passes at 9.2.0.
- The pull request shows the guard working (evidence only, not committed): removing
  `PASSWORD_PREVIOUSLY_USED` from the `422` turns `clients-gate`'s typecheck red, and misspelling it
  turns `ContractSchemaDeclarationTest` red.
- Production under `api/` is measured after the first implementation commit; the review's estimate
  is about 185 of the 200.

## 5. Blocks

| Block | Branch | Content |
|---|---|---|
| 10 | `feat/the-refusals-are-declared` | All of section 3, this spec and ADR 0041 |

## 6. Backlog

No other item is adjacent. This lot files one: the other routes' refusals, whose `code` stays a
plain `string` until each is annotated.

## 7. Out of scope

| Not done | Observable |
|---|---|
| Declaring the refusals of other routes | Only the two `MeController` operations gain a `code` enum |
| `401`, `415` and `500` on these routes | Their contract entries keep no content |
| Reading `Retry-After` in the client | No client code reads it |
