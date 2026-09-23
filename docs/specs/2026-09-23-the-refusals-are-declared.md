# The refusals are declared

Date: 2026-09-23
Status: Draft, awaiting the specification review. Frozen when block 10 merges.
Branch of block 10: `feat/the-refusals-are-declared`
ADR: `docs/adr/0041-the-presentation-owns-the-refusal-codes.md`.

## 1. Goal

Close the backlog item "The contract declares none of the refusals the web application reads". Today
renaming a refusal code leaves the gate green and the client showing the general sentence.

## 2. What exists today

- `contract/openapi.json` declares `204, 400, 401, 403` for `PUT /api/v1/me/password` and
  `202, 401, 403` for `DELETE /api/v1/me`, no body on any of them.
- `ProblemDetail.code` is a `String`, filled from `ErrorCode` (`api-usecases`) by `BaseErrorMapper`
  and from `FrameworkErrorCode` by the other mappers.
- `clients/apps/webapp/src/passwordRefusals.ts` keys its sentences by code in a
  `Record<string, …>`.
- `POST /api/v1/me/exports` goes through `Reauthenticator`, and its `429` names only
  `EXPORT_TOO_SOON`.

## 3. The change

| Where | What |
|---|---|
| `mappers/ProblemCode.kt` | Replaces `FrameworkErrorCode`: its constants plus one per `ErrorCode`, same spelling (ADR 0041) |
| `BaseErrorMapper` | One exhaustive `when` from `ErrorCode` to `ProblemCode` and status, replacing `statusFor` |
| `ProblemDetail`, `ProblemResponses`, the other mappers | `code: ProblemCode` |
| `MeController.changePassword` | `@APIResponse` with a `ProblemDetail` body for `400` (`VALIDATION_ERROR`, `MALFORMED_BODY`), `403` (`REAUTHENTICATION_FAILED`), `409` (`PASSWORD_CHANGE_COLLISION`), `422` (`PASSWORD_PREVIOUSLY_USED`), `429` (`PASSWORD_CHANGED_TOO_SOON`, `TOO_MANY_AUTHENTICATION_ATTEMPTS`) |
| `MeController.deleteAccount` | The same for `400` (`UNSUPPORTED_REAUTHENTICATION_FACTOR`), `403` (`REAUTHENTICATION_FAILED`), `429` (`TOO_MANY_AUTHENTICATION_ATTEMPTS`) |
| `MeExportController.requestExport` | Its `429` also names `TOO_MANY_AUTHENTICATION_ATTEMPTS` |
| `contract/openapi.json` | Regenerated; `info-version` 9.1.0 to 9.2.0, or the next major if `oasdiff` rates a break |
| `passwordRefusals.ts` | `REFUSALS` typed `Partial<Record<components["schemas"]["ProblemCode"], () => string>>` |

The annotations follow `MeExportController`: status, the codes in the description, and
`Content(PROBLEM_JSON, ProblemDetail)`.

## 4. Acceptance

- The wire does not move: every existing test asserting a `code` string passes unchanged.
- `ProblemDetail.code` in the contract is a `$ref` to an enum schema, and `schema.d.ts` renders it
  as a union of string literals.
- The pull request shows the guard working: renaming `PASSWORD_PREVIOUSLY_USED` in `ProblemCode`
  and regenerating turns `clients-gate`'s typecheck red (evidence only, not committed).

## 5. Blocks

| Block | Branch | Content |
|---|---|---|
| 10 | `feat/the-refusals-are-declared` | All of section 3, this spec and ADR 0041 |

## 6. Backlog

No other item is adjacent. Removing `ErrorCode` for a sealed hierarchy is ADR 0041's accepted
consequence, not an item.

## 7. Out of scope

| Not done | Observable |
|---|---|
| Declaring the refusals of other routes | Only the three operations above gain `@APIResponse` |
| Reading `Retry-After` in the client | No client code reads it |
