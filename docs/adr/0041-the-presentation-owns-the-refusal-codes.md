# 0041. The presentation owns the refusal codes, and each response declares its own

Status: Accepted
Date: 2026-09-23
Specification: `docs/specs/2026-09-23-the-refusals-are-declared.md`.
Supersedes: `docs/adr/0021-framework-refusals-share-the-problem-format.md`, decision 2 ("Two tables").

## Context

A refusal's `code` is the name of an `ErrorCode` constant, an enum of `api-usecases`:
`BaseErrorMapper` writes `exception.code.name`. Renaming that constant changes the public contract,
and nothing says so. No use case reads `ErrorCode`: it only tags the exceptions for the mapper.

Typing `ProblemDetail.code` as one enum was weighed and refused: the pinned `oasdiff` rates a value
added to a response enum as a break (`response-property-enum-value-added`), so every new refusal
anywhere would raise the contract's major, on routes whose behaviour did not change.

## Decision

1. **The wire codes are a presentation enum, `ProblemCode`**, which absorbs `FrameworkErrorCode`
   and holds one constant per `ErrorCode`, spelled as today.
2. **`BaseErrorMapper` maps each `ErrorCode` to its `ProblemCode` and status in one exhaustive
   `when`**, replacing `statusFor`. A renamed `ErrorCode` fails to compile there, and the wire keeps
   its name.
3. **A response declares the codes it can return**, as an `enum` on `code` over
   `allOf: [ProblemDetail]`. `ProblemDetail.code` itself stays a `string`. Adding a code to a
   response is a break, since that route's behaviour changed; adding one elsewhere is not.
4. **A contract test holds every declared code to a `ProblemCode` name**, the annotation taking
   string literals the compiler does not check.

**Fails if** a use case needs to branch on a refusal's code: it then reads the exception's class.

## Consequences

- **Each refusal has three names**: its exception class, its `ErrorCode` constant, and the wire's
  `ProblemCode`. Removing `ErrorCode` for a sealed `BaseError` hierarchy was weighed and left for
  later: it touches every exception class for no gain at the boundary.
- **A route declares its codes only once someone annotates it.** Until then its `code` reads as a
  plain `string`.
