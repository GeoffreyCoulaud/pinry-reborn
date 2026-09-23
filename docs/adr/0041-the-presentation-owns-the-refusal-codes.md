# 0041. The presentation owns the refusal codes

Status: Accepted
Date: 2026-09-23
Specification: `docs/specs/2026-09-23-the-refusals-are-declared.md`.

## Context

A refusal's `code` is the name of an `ErrorCode` constant, an enum of `api-usecases`:
`BaseErrorMapper` writes `exception.code.name`. Renaming that constant changes the public contract,
and nothing says so. No use case reads `ErrorCode`: it only tags the exceptions for the mapper.

## Decision

1. **The wire codes are a presentation enum, `ProblemCode`**, which absorbs `FrameworkErrorCode`
   and holds one constant per `ErrorCode`, spelled as today.
2. **`BaseErrorMapper` maps each `ErrorCode` to its `ProblemCode` and status in one exhaustive
   `when`**, replacing `statusFor`. A renamed `ErrorCode` fails to compile there, and the wire keeps
   its name.
3. **`ProblemDetail.code` is typed `ProblemCode`**, so the contract declares the closed list and a
   client can type its lookup tables against it.

**Fails if** a use case needs to branch on a refusal's code: it then reads the exception's class.

## Consequences

- **Each refusal has two names**, its exception class and its `ErrorCode` constant. Removing
  `ErrorCode` for a sealed `BaseError` hierarchy was weighed and left for later: it touches every
  exception class for no gain at the boundary.
- **Renaming a `ProblemCode` constant is a contract change**, visible in `contract/openapi.json` and
  in the client's typecheck for every code it reads.
