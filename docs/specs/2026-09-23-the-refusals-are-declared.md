# The refusals are declared

Date: 2026-09-23
Status: Approved by the operator on 2026-09-23; two adversarial reviews closed, their findings
recorded in this document. Frozen when the last block merges.
ADR: `docs/adr/0042-the-presentation-owns-the-refusal-codes.md`.

## 1. Goal

Every operation of the contract declares the refusals it can return, each with a `ProblemDetail`
body and the `enum` of its codes, so a client reads refusals from its generated types. Closes the
backlog item "The contract declares none of the refusals the web application reads".

## 2. What exists today

Measured by an inventory of every operation on 2026-09-23 (controllers traced to the `BaseError`s
they can throw, then `BaseErrorMapper.statusFor`; framework mappers by what each operation reads).
Appendix A is its result.

- **53 operations, 50 protected.** Appendix A holds 136 (operation, status) pairs; 41 are declared
  with a body today. No protected operation's `401` has one.
- **SmallRye adds bodyless responses on its own**: `401`/`403` on protected operations, `400` on
  those with an input. 17 of those `403`s can never happen. Declaring an operation's own `400` or
  `403` stops SmallRye adding its own there.
- **Wrong today**: `POST /api/v1/sessions` answers `429 TOO_MANY_AUTHENTICATION_ATTEMPTS`, undeclared,
  and its `401` says the limiter answers `401`; `POST /api/v1/me/exports`'s `429` omits
  `TOO_MANY_AUTHENTICATION_ATTEMPTS`; `PUT /api/v1/pins/{pinId}/image` declares none of its six
  refusals; the batch `400`s omit `MALFORMED_BODY`; the declared `404`s omit `UNKNOWN_ROUTE`, which a
  malformed path UUID produces.
- **Two defects**: a multipart upload with no `file` part likely answers `500`
  (`ImageController.kt:89`, a non-null `FileUpload`); a body over `quarkus.http.limits.max-body-size`
  gets a bodyless `413` from `HttpServerCommonHandlers.enforceMaxBodySize`, which ends the response
  itself, so no mapper sees it (read from `quarkus-vertx-http` 3.37.1's bytecode).
- `ProblemDetail.code` is a `String` filled from `ErrorCode` by `BaseErrorMapper` and from
  `FrameworkErrorCode` elsewhere.

## 3. The rules every block applies

- **A refusal is declared** as `Schema(allOf = [ProblemDetail::class], properties = [SchemaProperty(name = "code", enumeration = [...])])`
  under `application/problem+json`, with a one-line description. A spike on 2026-09-23 (reverted)
  measured the shape: SmallRye writes it as-is, and openapi-typescript renders `code` as the union.
- **A refusal repeated identically across operations** is one `components.responses` entry,
  referenced with `@APIResponse(responseCode = ..., ref = ...)`. About 13 lines per inline
  annotation (120 columns, `api/config/detekt/detekt.yml:701`) make this what holds the budget.
- **Declared**: each pair of appendix A, the race-only `404` of `GET /api/v1/boards` included. **Not
  declared**: an unserved path, `405`, `500`, CORS.
- **`PUT /api/v1/pins/{pinId}/image` is two methods SmallRye merges**: a status both arms answer is
  declared on both with the same full union, and the merged enum checked with `jq`.
- **An operation that declares a response also declares its success**, SmallRye no longer
  generating it; the acceptance below checks it.
- **The contract's version**: `oasdiff` rates adding an enum to a response that already has a body
  as a break (`response-property-enum-value-added`, measured), so most blocks raise the major. The
  operator accepted it (majors are free while `contract/frozen/` is empty); a block that breaks
  nothing raises the minor.

## 4. Blocks

| Block | Branch | Content |
|---|---|---|
| 10 | `refactor/the-problem-codes` | `ProblemCode` replaces `FrameworkErrorCode`; `BaseErrorMapper`'s one `when` replaces `statusFor`; `ProblemResponses` and the mappers take a `ProblemCode`, `ProblemDetail.code` stays `String`; `BaseErrorMapperTest` gains a case over `ErrorCode.entries`; `agents/engineering.md` names `ProblemCode`. The wire does not move: every test asserting a `code` passes unchanged. This spec and ADR 0042 ride here |
| 20 | `feat/the-account-refusals` | The build filter (`SessionSecurityRequirementFilter` or a sibling) points every protected operation's `401` at one `components.responses` entry carrying `AUTHENTICATION_REQUIRED`, `AUTHENTICATION_FAILED`, `SESSION_EXPIRED`, and drops nothing else; `ContractSchemaDeclarationTest` holds every declared code to `ProblemCode`, following `$ref`; `MeController`, `SessionController` (its `429` and `401` fixed), `UserController`; a test settles whether a bad token on a public route answers `401`, and the contract says what it shows; the client's `REFUSALS` keyed by the codes the two account operations declare (`packages/auth` exposes what is needed), its lookup narrowed by a type guard, `lib/refusals.ts`'s comment corrected; the backlog item deleted |
| 30 | `fix/a-missing-file-part-is-a-bad-request` | A multipart upload with no `file` part answers `400 VALIDATION_ERROR`, a failing test first |
| 40 | `fix/an-oversize-body-carries-a-problem` | A body over the limit answers `413` with a `ProblemDetail` and a new `ProblemCode.BODY_TOO_LARGE`, a failing test first. Candidate: a Vert.x route ordered before the body handler, refusing on `Content-Length`. If no mechanism covers it, the teammate stops and asks (tier 2). The two upload routes declare the code |
| 50 | `feat/the-pin-refusals` | `PinController`, `TagSearchController` |
| 60 | `feat/the-recycle-bin-refusals` | `PinRecycleBinController`, `BoardRecycleBinController` |
| 70 | `feat/the-board-refusals` | `BoardController` |
| 80 | `feat/the-image-refusals` | `ImageController` (all four routes), `MeImageDownloadController` |
| 90 | `feat/the-archive-refusals` | `MeExportController`, `MeImportController` |
| 100 | `feat/no-refusal-without-a-body` | The filter drops the bodyless refusals left (the 17 false `403`s); `quarkus.smallrye-openapi.auto-add-bad-request-response=false`; a test that every non-2xx response declared carries a `code` enum. It catches a declared refusal without codes, not an undeclared one: a new route's refusals stay the reviewer's to check |

The seams follow the controllers. Each block measures itself after its first commit and splits by
operation if it passes 500 lines or 20 files (`docs/adr/0041-a-block-is-bounded-by-hunks-and-files.md`).

## 5. Acceptance, per block

- `dagger call gate` green, `contract/openapi.json` regenerated.
- For each operation the block touches, `jq` over its `responses` gives exactly appendix A's
  statuses, the shared `401` on protected operations, and its success statuses as on `main`; each
  refusal's `code` enum equals appendix A's codes, the `401` through its `$ref`.
- Block 20's pull request shows the guard working (evidence only): removing
  `PASSWORD_PREVIOUSLY_USED` from the `422` turns `clients-gate`'s typecheck red, and misspelling it
  turns `ContractSchemaDeclarationTest` red.

## 6. Backlog

This lot closes "The contract declares none of the refusals the web application reads" and files
nothing.

## 7. Out of scope

| Not done | Observable |
|---|---|
| Declaring `500`, `405` and an unserved path's `404` | No operation carries them |
| Reading `Retry-After` in the client | No client code reads it |
| Consuming the new declarations in the web application beyond the account screen | Only `passwordRefusals.ts`, `me.ts`, `lib/refusals.ts` and `packages/auth`'s exports change under `clients/` |

## Appendix A. The refusals each operation owns

Paths under `/api/v1`. Every protected operation also answers the shared `401`. Block 40 adds
`BODY_TOO_LARGE` to the two `413`s.

| Operation | Status | Codes |
|---|---|---|
| POST /users | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| POST /users | 409 | USERNAME_ALREADY_EXISTS |
| POST /users | 415 | UNSUPPORTED_MEDIA_TYPE |
| POST /sessions | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| POST /sessions | 401 | AUTHENTICATION_FAILED |
| POST /sessions | 415 | UNSUPPORTED_MEDIA_TYPE |
| POST /sessions | 429 | TOO_MANY_AUTHENTICATION_ATTEMPTS |
| PUT /me/password | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| PUT /me/password | 403 | REAUTHENTICATION_FAILED |
| PUT /me/password | 409 | PASSWORD_CHANGE_COLLISION |
| PUT /me/password | 415 | UNSUPPORTED_MEDIA_TYPE |
| PUT /me/password | 422 | PASSWORD_PREVIOUSLY_USED |
| PUT /me/password | 429 | TOO_MANY_AUTHENTICATION_ATTEMPTS, PASSWORD_CHANGED_TOO_SOON |
| DELETE /me | 400 | UNSUPPORTED_REAUTHENTICATION_FACTOR |
| DELETE /me | 403 | REAUTHENTICATION_FAILED |
| DELETE /me | 429 | TOO_MANY_AUTHENTICATION_ATTEMPTS |
| GET /tags/search | 400 | SEARCH_EMPTY_QUERY |
| GET /tags/search | 404 | UNKNOWN_ROUTE |
| POST /boards | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| POST /boards | 409 | BOARD_NAME_ALREADY_EXISTS |
| POST /boards | 415 | UNSUPPORTED_MEDIA_TYPE |
| GET /boards | 404 | BOARD_DOES_NOT_EXIST |
| GET /boards/{boardId} | 403 | BOARD_INSUFFICIENT_PERMISSIONS |
| GET /boards/{boardId} | 404 | BOARD_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| PUT /boards/{boardId} | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| PUT /boards/{boardId} | 403 | BOARD_INSUFFICIENT_PERMISSIONS |
| PUT /boards/{boardId} | 404 | BOARD_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| PUT /boards/{boardId} | 409 | BOARD_NAME_ALREADY_EXISTS |
| PUT /boards/{boardId} | 415 | UNSUPPORTED_MEDIA_TYPE |
| DELETE /boards/{boardId} | 403 | BOARD_INSUFFICIENT_PERMISSIONS |
| DELETE /boards/{boardId} | 404 | BOARD_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| DELETE /boards/{boardId} | 409 | BOARD_ALREADY_SOFT_DELETED |
| GET /boards/{boardId}/pins | 400 | SEARCH_EMPTY_QUERY |
| GET /boards/{boardId}/pins | 403 | BOARD_INSUFFICIENT_PERMISSIONS |
| GET /boards/{boardId}/pins | 404 | BOARD_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| POST /boards/{boardId}/pins | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| POST /boards/{boardId}/pins | 403 | BOARD_INSUFFICIENT_PERMISSIONS, PIN_INSUFFICIENT_PERMISSIONS |
| POST /boards/{boardId}/pins | 404 | BOARD_DOES_NOT_EXIST, PIN_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| POST /boards/{boardId}/pins | 409 | PIN_ALREADY_SOFT_DELETED |
| POST /boards/{boardId}/pins | 415 | UNSUPPORTED_MEDIA_TYPE |
| DELETE /boards/{boardId}/pins | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| DELETE /boards/{boardId}/pins | 403 | BOARD_INSUFFICIENT_PERMISSIONS, PIN_INSUFFICIENT_PERMISSIONS |
| DELETE /boards/{boardId}/pins | 404 | BOARD_DOES_NOT_EXIST, PIN_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| DELETE /boards/{boardId}/pins | 409 | PIN_ALREADY_SOFT_DELETED |
| DELETE /boards/{boardId}/pins | 415 | UNSUPPORTED_MEDIA_TYPE |
| POST /boards/recycled/{boardId}/restore | 403 | BOARD_INSUFFICIENT_PERMISSIONS |
| POST /boards/recycled/{boardId}/restore | 404 | BOARD_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| POST /boards/recycled/{boardId}/restore | 409 | BOARD_NOT_SOFT_DELETED |
| POST /boards/recycled/restore | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| POST /boards/recycled/restore | 403 | BOARD_INSUFFICIENT_PERMISSIONS |
| POST /boards/recycled/restore | 404 | BOARD_DOES_NOT_EXIST |
| POST /boards/recycled/restore | 409 | BOARD_NOT_SOFT_DELETED |
| POST /boards/recycled/restore | 415 | UNSUPPORTED_MEDIA_TYPE |
| DELETE /boards/recycled/{boardId} | 403 | BOARD_INSUFFICIENT_PERMISSIONS |
| DELETE /boards/recycled/{boardId} | 404 | BOARD_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| DELETE /boards/recycled/{boardId} | 409 | BOARD_NOT_SOFT_DELETED |
| GET /pins | 400 | SEARCH_EMPTY_QUERY |
| GET /pins | 403 | PIN_INSUFFICIENT_PERMISSIONS |
| GET /pins | 404 | PIN_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| POST /pins | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| POST /pins | 415 | UNSUPPORTED_MEDIA_TYPE |
| DELETE /pins | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| DELETE /pins | 403 | PIN_INSUFFICIENT_PERMISSIONS |
| DELETE /pins | 404 | PIN_DOES_NOT_EXIST |
| DELETE /pins | 409 | PIN_ALREADY_SOFT_DELETED |
| DELETE /pins | 415 | UNSUPPORTED_MEDIA_TYPE |
| GET /pins/{pinId} | 403 | PIN_INSUFFICIENT_PERMISSIONS |
| GET /pins/{pinId} | 404 | PIN_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| PUT /pins/{pinId} | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| PUT /pins/{pinId} | 403 | PIN_INSUFFICIENT_PERMISSIONS, BOARD_INSUFFICIENT_PERMISSIONS |
| PUT /pins/{pinId} | 404 | PIN_DOES_NOT_EXIST, BOARD_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| PUT /pins/{pinId} | 409 | PIN_ALREADY_SOFT_DELETED |
| PUT /pins/{pinId} | 415 | UNSUPPORTED_MEDIA_TYPE |
| DELETE /pins/{pinId} | 403 | PIN_INSUFFICIENT_PERMISSIONS |
| DELETE /pins/{pinId} | 404 | PIN_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| DELETE /pins/{pinId} | 409 | PIN_ALREADY_SOFT_DELETED |
| GET /pins/recycled | 403 | PIN_INSUFFICIENT_PERMISSIONS |
| GET /pins/recycled | 404 | PIN_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| POST /pins/recycled/restore | 400 | VALIDATION_ERROR, MALFORMED_BODY |
| POST /pins/recycled/restore | 403 | PIN_INSUFFICIENT_PERMISSIONS |
| POST /pins/recycled/restore | 404 | PIN_DOES_NOT_EXIST |
| POST /pins/recycled/restore | 409 | PIN_NOT_SOFT_DELETED |
| POST /pins/recycled/restore | 415 | UNSUPPORTED_MEDIA_TYPE |
| POST /pins/recycled/{pinId}/restore | 403 | PIN_INSUFFICIENT_PERMISSIONS |
| POST /pins/recycled/{pinId}/restore | 404 | PIN_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| POST /pins/recycled/{pinId}/restore | 409 | PIN_NOT_SOFT_DELETED |
| DELETE /pins/recycled/{pinId} | 403 | PIN_INSUFFICIENT_PERMISSIONS |
| DELETE /pins/recycled/{pinId} | 404 | PIN_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| DELETE /pins/recycled/{pinId} | 409 | PIN_NOT_SOFT_DELETED |
| PUT /pins/{pinId}/image | 400 | IMAGE_SOURCE_URL_INVALID, VALIDATION_ERROR, MALFORMED_BODY |
| PUT /pins/{pinId}/image | 403 | IMAGE_INSUFFICIENT_PERMISSIONS |
| PUT /pins/{pinId}/image | 404 | IMAGE_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| PUT /pins/{pinId}/image | 413 | IMAGE_TOO_LARGE |
| PUT /pins/{pinId}/image | 415 | UNSUPPORTED_MEDIA_TYPE |
| PUT /pins/{pinId}/image | 422 | IMAGE_INVALID |
| GET /pins/{pinId}/image | 400 | IMAGE_RENDITION_SIZE_INVALID |
| GET /pins/{pinId}/image | 403 | IMAGE_INSUFFICIENT_PERMISSIONS |
| GET /pins/{pinId}/image | 404 | IMAGE_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| DELETE /pins/{pinId}/image | 403 | IMAGE_INSUFFICIENT_PERMISSIONS |
| DELETE /pins/{pinId}/image | 404 | IMAGE_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| GET /pins/{pinId}/image/status | 403 | IMAGE_INSUFFICIENT_PERMISSIONS |
| GET /pins/{pinId}/image/status | 404 | IMAGE_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| GET /me/image-downloads | 404 | UNKNOWN_ROUTE |
| DELETE /me/image-downloads/{pinId} | 404 | IMAGE_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| DELETE /me/image-downloads/{pinId} | 409 | IMAGE_DOWNLOAD_IN_PROGRESS |
| POST /me/exports | 400 | UNSUPPORTED_REAUTHENTICATION_FACTOR |
| POST /me/exports | 403 | REAUTHENTICATION_FAILED |
| POST /me/exports | 409 | EXPORT_ALREADY_IN_PROGRESS |
| POST /me/exports | 429 | EXPORT_TOO_SOON, TOO_MANY_AUTHENTICATION_ATTEMPTS |
| GET /me/exports | 404 | UNKNOWN_ROUTE |
| GET /me/exports/{id} | 403 | EXPORT_INSUFFICIENT_PERMISSIONS |
| GET /me/exports/{id} | 404 | EXPORT_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| GET /me/exports/{id}/download | 403 | EXPORT_INSUFFICIENT_PERMISSIONS |
| GET /me/exports/{id}/download | 404 | EXPORT_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| GET /me/exports/{id}/download | 409 | EXPORT_NOT_READY |
| GET /me/exports/{id}/download | 410 | EXPORT_GONE |
| GET /me/exports/{id}/download | 416 | RANGE_NOT_SATISFIABLE |
| DELETE /me/exports/{id} | 403 | EXPORT_INSUFFICIENT_PERMISSIONS |
| DELETE /me/exports/{id} | 404 | EXPORT_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| POST /me/imports | 409 | IMPORT_ALREADY_IN_PROGRESS |
| GET /me/imports | 404 | UNKNOWN_ROUTE |
| GET /me/imports/{id} | 403 | IMPORT_INSUFFICIENT_PERMISSIONS |
| GET /me/imports/{id} | 404 | IMPORT_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| PUT /me/imports/{id}/archive | 403 | IMPORT_INSUFFICIENT_PERMISSIONS |
| PUT /me/imports/{id}/archive | 404 | IMPORT_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| PUT /me/imports/{id}/archive | 409 | IMPORT_NOT_AWAITING_ARCHIVE, IMPORT_CHUNK_OFFSET_MISMATCH |
| PUT /me/imports/{id}/archive | 413 | IMPORT_ARCHIVE_TOO_LARGE |
| PUT /me/imports/{id}/archive | 415 | UNSUPPORTED_MEDIA_TYPE |
| PUT /me/imports/{id}/archive | 507 | IMPORT_INSUFFICIENT_STORAGE |
| POST /me/imports/{id}/archive/complete | 403 | IMPORT_INSUFFICIENT_PERMISSIONS |
| POST /me/imports/{id}/archive/complete | 404 | IMPORT_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| POST /me/imports/{id}/archive/complete | 409 | IMPORT_NOT_AWAITING_ARCHIVE, IMPORT_ARCHIVE_EMPTY |
| GET /me/imports/{id}/issues | 403 | IMPORT_INSUFFICIENT_PERMISSIONS |
| GET /me/imports/{id}/issues | 404 | IMPORT_DOES_NOT_EXIST, UNKNOWN_ROUTE |
| DELETE /me/imports/{id} | 403 | IMPORT_INSUFFICIENT_PERMISSIONS |
| DELETE /me/imports/{id} | 404 | IMPORT_DOES_NOT_EXIST, UNKNOWN_ROUTE |
