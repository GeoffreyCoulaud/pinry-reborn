# The refusals are declared

Date: 2026-09-23
Status: Draft; the first review's twelve findings are recorded in this document, the scope then
widened to every route. Frozen when the last block merges.
ADR: `docs/adr/0042-the-presentation-owns-the-refusal-codes.md`.

## 1. Goal

Every operation of the contract declares the refusals it can return, each with a `ProblemDetail`
body and the `enum` of its codes, so a client reads refusals from its generated types. Closes the
backlog item "The contract declares none of the refusals the web application reads".

## 2. What exists today

Measured by an inventory of every operation on 2026-09-23 (controllers traced to the `BaseError`s
they can throw, then `BaseErrorMapper.statusFor`; framework mappers by what each operation reads):

- **53 operations, 50 protected.** 136 (operation, status) pairs are an operation's own; 41 of them
  are declared with a body. No `401` has one.
- **SmallRye adds bodyless responses on its own**: `401`/`403` on protected operations, `400` on those
  with an input. 17 of those `403`s can never happen.
- **Wrong today**: `POST /api/v1/sessions` answers `429 TOO_MANY_AUTHENTICATION_ATTEMPTS`, undeclared,
  and its `401` says the limiter answers `401`; `POST /api/v1/me/exports`'s `429` omits
  `TOO_MANY_AUTHENTICATION_ATTEMPTS`; `PUT /api/v1/pins/{pinId}/image` declares none of its six
  refusals; the batch `400`s omit `MALFORMED_BODY`; the declared `404`s omit `UNKNOWN_ROUTE`, which a
  malformed path UUID produces.
- **Two defects**: a multipart upload with no `file` part likely answers `500`
  (`ImageController.kt:89`, a non-null `FileUpload`), and a body over
  `quarkus.http.limits.max-body-size` is refused `413` by Vert.x before any mapper, likely with no
  problem body.
- `ProblemDetail.code` is a `String` filled from `ErrorCode` by `BaseErrorMapper` and from
  `FrameworkErrorCode` elsewhere. `changePassword`'s `204` is generated, and SmallRye stops
  generating it once the operation declares a response (`TagSearchController.kt:29-30`).

## 3. The rules every block applies

- **A refusal is declared** as `Schema(allOf = [ProblemDetail::class], properties = [SchemaProperty(name = "code", enumeration = [...])])`
  under `application/problem+json`, with a one-line description. A spike on 2026-09-23 (reverted)
  measured the shape: SmallRye writes it as-is, openapi-typescript renders `code` as the union, and
  `oasdiff` v1.31.0 reads `main` to it as no break and a value added to it as one error.
- **Declared**: each (operation, status) the operation owns, a malformed path UUID's
  `UNKNOWN_ROUTE` included, and the race-only `404` of `GET /api/v1/boards`. **Not declared**: an
  unserved path, `405`, `500`, CORS.
- **An operation that declares a response also declares its success**, SmallRye no longer
  generating it.
- **The contract's version** rises by a minor per block, or a major where `oasdiff` rates a break.

## 4. Blocks

| Block | Branch | Content |
|---|---|---|
| 10 | `refactor/the-problem-codes` | `ProblemCode` replaces `FrameworkErrorCode`; `BaseErrorMapper`'s one `when` replaces `statusFor`; `ProblemResponses` and the mappers take a `ProblemCode`, `ProblemDetail.code` stays `String`; `BaseErrorMapperTest` gains a case over `ErrorCode.entries`; `agents/engineering.md` names `ProblemCode`. The wire does not move: every test asserting a `code` passes unchanged. This spec and ADR 0042 ride here |
| 20 | `feat/the-account-refusals` | The build filter (`SessionSecurityRequirementFilter` or a sibling) drops SmallRye's bodyless refusals and adds one shared `401` (`AUTHENTICATION_REQUIRED`, `AUTHENTICATION_FAILED`, `SESSION_EXPIRED`) to every protected operation; `ContractSchemaDeclarationTest` holds every declared code to `ProblemCode`; `MeController`, `SessionController` (its `429` and `401` fixed), `UserController`, `HandshakeController`; a test settles whether a bad token on a public route answers `401`, and the contract says what it shows; the client's `REFUSALS` keyed by the codes the two account operations declare (`packages/auth` exposes what is needed), its lookup narrowed by a type guard, `lib/refusals.ts`'s comment corrected; the backlog item deleted |
| 30 | `fix/a-missing-file-part-is-a-bad-request` | A multipart upload with no `file` part answers `400 VALIDATION_ERROR`, a failing test first |
| 40 | `fix/an-oversize-body-carries-a-problem` | Vert.x's `413` carries a `ProblemDetail`, a failing test first; the two upload routes declare it |
| 50 | `feat/the-pin-refusals` | `PinController` (its image route included), `TagSearchController` |
| 60 | `feat/the-recycle-bin-refusals` | `PinRecycleBinController`, `BoardRecycleBinController` |
| 70 | `feat/the-board-refusals` | `BoardController` |
| 80 | `feat/the-archive-refusals` | `MeExportController`, `MeImportController`, `MeImageDownloadController`, `ImageController`; then the test that every non-2xx response in the contract carries a `code` enum, which makes a forgotten route red |

The seams follow the controllers so each block holds under 500 lines and 20 files
(`docs/adr/0041-a-block-is-bounded-by-hunks-and-files.md`): 48 pairs under `pins`, 38 under
`boards`, 34 under `me/exports`, `me/imports` and `me/image`, at roughly eight lines each. Each
block measures itself after its first commit.

## 5. Acceptance, per block

- `dagger call gate` green, `contract/openapi.json` regenerated.
- For each operation the block touches, `jq '.paths[...].<method>.responses | keys'` matches the
  inventory, and each refusal but the shared `401` carries its `code` enum.
- Block 20's pull request shows the guard working (evidence only): removing
  `PASSWORD_PREVIOUSLY_USED` from the `422` turns `clients-gate`'s typecheck red, and misspelling it
  turns `ContractSchemaDeclarationTest` red.

## 6. Backlog

This lot closes "The contract declares none of the refusals the web application reads" and files
nothing: the rule of block 80 keeps every route declared.

## 7. Out of scope

| Not done | Observable |
|---|---|
| Declaring `500`, `405` and an unserved path's `404` | No operation carries them |
| Reading `Retry-After` in the client | No client code reads it |
| Consuming the new declarations in the web application beyond the account screen | Only `passwordRefusals.ts` changes under `clients/` |
