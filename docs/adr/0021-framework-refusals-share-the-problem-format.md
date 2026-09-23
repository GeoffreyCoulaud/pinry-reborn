# 0021. Framework refusals share the problem format

Status: Accepted; decision 2 is superseded by `docs/adr/0042-the-presentation-owns-the-refusal-codes.md`,
and the Consequence "The oversize body stays outside the format" is amended by the refusals lot
(`docs/specs/2026-09-23-the-refusals-are-declared.md`): `OversizeBodyRefusal` answers a body declaring its
length past the limit with a problem, and only a chunked one stays bodyless.
Date: 2026-09-07
Specification: `docs/specs/2026-09-05-p2-debt-elimination.md`, section 3 (D6) and section 4.5.
Related: `docs/adr/0020-two-reviews-and-an-inline-act.md` (the lot's regime); `agents/engineering.md`,
"One error format, declared once, applied everywhere".

## Context

**The promise and what a client read.** `agents/engineering.md` promised one error format "including
framework-generated responses (unauthenticated, unhandled media types, malformed payloads, method not
allowed, the fallback handler)". Five mappers existed (`UnauthorizedException`,
`AuthenticationFailedException`, `ConstraintViolationException`, `RangeNotSatisfiableException`,
`BaseError`) and nothing else was mapped. Measured in block 6 before any change: a path no resource
serves answered an HTML page in test mode; a method the path does not serve, a `Content-Type` the
route does not read, an `Accept` it cannot produce and a body that is not JSON answered a bare status
with no body; a body Jackson could not bind answered Quarkus's own JSON (`objectName`,
`attributeName`, in dev and test only); an unhandled exception answered Quarkus's JSON error with an
id. The backlog item that opened this named the last but one.

**Four facts from the Quarkus 3.37.1 sources**, read from the sources jars in the Gradle cache
during block 6, shaped the family:

- `ServerJacksonMessageBodyReader.readFrom` rethrows `MismatchedInputException` and
  `InvalidDefinitionException` as themselves and wraps every other read failure
  (`StreamReadException`, `DatabindException`, `StreamConstraintsException`) in a
  `WebApplicationException(cause, 400)` carrying no entity. A mapper on `JsonProcessingException`, the
  specification's first draft, would see the first and never the rest. `BuiltinMismatchedInputExceptionMapper`
  sits on the exact class, which resolution takes before any parent
  (`RuntimeExceptionMapper.searchMapperInClassHierarchy`).
- `RuntimeExceptionMapper.mapException` returns a `WebApplicationException`'s own response untouched
  when it carries an entity, and consults the mappers otherwise, walking the class hierarchy for
  every class; the comment above the call, "we match superclasses only if not a
  WebApplicationException", describes what the code does not do.
- The same method logs an unmapped `IOException` at DEBUG, "the client likely terminated the
  connection", and every other unmapped throwable at ERROR.
- `ClassRoutingHandler` throws `NotAllowedException` with a bare `405` and no `Allow` set,
  `NotSupportedException` when a `Content-Type` matches nothing the method consumes, and
  `NotAcceptableException` only against a declared `@Produces` list: no production route declares
  one, so the API answers no `406` today (`GET /api/v1/me` with `Accept: text/plain` answered `200`
  with JSON). `ParameterHandler` wraps a path or query value it cannot convert in a
  `NotFoundException`, per JAX-RS 3.2, and a header, cookie or form value in a `BadRequestException`.

**The oversize body is answered below JAX-RS.** `quarkus.http.limits.max-body-size` is `32M`. A
multipart body one byte over answers `413` with no body and no `Content-Type`, and the server log
shows "Response has already been written" where the mapper's response would have gone; a JSON body
one byte over has its connection closed while the client is still sending, and this client read no
response at all. Neither reaches a mapper.

## Decision

1. **The family.** Every refusal the framework decides before a use case runs answers
   `application/problem+json` with a `code`:

   | Exception | Status | `code` |
   |---|---|---|
   | `MismatchedInputException` | `400` | `MALFORMED_BODY` |
   | `WebApplicationException` whose cause is a `JsonProcessingException` | `400` | `MALFORMED_BODY` |
   | `jakarta.ws.rs.NotFoundException` | `404` | `UNKNOWN_ROUTE` |
   | `jakarta.ws.rs.NotAllowedException` | `405` | `METHOD_NOT_ALLOWED` |
   | `jakarta.ws.rs.NotSupportedException` | `415` | `UNSUPPORTED_MEDIA_TYPE` |
   | `jakarta.ws.rs.NotAcceptableException` | `406` | `NOT_ACCEPTABLE` |
   | Any other `WebApplicationException` | its own status | `HTTP_ERROR` |
   | `IOException` | `500` | `INTERNAL_ERROR`, `detail` null, logged at DEBUG |
   | Any other `Throwable` | `500` | `INTERNAL_ERROR`, `detail` null, logged at ERROR |

   The two body rows are one row of the specification split by the first fact above: one mapper on
   the class the reader rethrows, and the umbrella reading its cause for the classes it wraps.
   `InvalidDefinitionException`, rethrown too, is a definition problem on the server's side and lands
   on the last row. *(Corrected in block 10, a holistic finding: it lands on the `IOException` row,
   since `JsonProcessingException` extends `JacksonException` extends `java.io.IOException` and
   resolution takes the nearest class. That row therefore logs by whose failure it is: a
   `JacksonException`, definition or serialisation, at ERROR with its stack; an `EOFException` or
   `ClosedChannelException`, the client leaving, at DEBUG as Quarkus did; any other `IOException`, a
   disk among them, at WARN with its stack, where DEBUG hid a full disk.)*

2. **Two tables.** `BaseErrorMapper.statusFor` maps a use case's `ErrorCode`. `FrameworkErrorCode`,
   in the `mappers` package, holds every code the presentation layer mints itself: the seven above and
   the five the existing mappers minted as strings (`VALIDATION_ERROR`, `AUTHENTICATION_REQUIRED`,
   `AUTHENTICATION_FAILED`, `SESSION_EXPIRED`, `RANGE_NOT_SATISFIABLE`). No wire value changed.
   `agents/engineering.md` names both tables.

3. **The library setting.** `quarkus.rest.exception-mapping.disable-mapper-for` names
   `BuiltinMismatchedInputExceptionMapper` in `application.properties`, with the first fact as its
   reason. Without it the exact-class built-in wins and `MismatchedInputExceptionMapper` never runs.

4. **What a detail says.** The `500` carries none: an unmapped throwable's message is nobody's to
   publish. `MALFORMED_BODY` names where Jackson stopped, the property path (`description`,
   `tags[2]`) or "its root" for a bound failure, the parser's own message for a syntax failure, and
   never the class Jackson was binding to. `UNKNOWN_ROUTE` tells a path no resource serves from a path
   or query value the framework could not read, by the cause JAX-RS 3.2 makes it carry; the code is
   the same, the status being the specification's choice, not this API's. The umbrella publishes the
   exception's message: every `WebApplicationException` the API answers today is Quarkus's own, and the
   application throws none.

5. **`ProblemResponses` is an object**, absorbing `MediaTypes.kt`'s constant and the challenge value;
   every mapper calls it qualified. The presentation layer holds no top-level function beyond the
   `SecurityIdentity` extensions `agents/engineering.md` exempts.

6. **Test mode writes no OpenAPI document.** `@QuarkusTest` indexes `src/test`, so a test-only
   resource (`TestFailuresResource`, which drives the rows nothing in the API reaches on its own)
   would land in `docs/openapi.json` at every test run. The test `application.properties` empties
   `quarkus.smallrye-openapi.store-schema-directory`; the pre-commit hook and CI generate the document
   from the production build alone.

## Consequences

- **A `Throwable` mapper is the fallback handler the promise named.** It hides nothing from the
  log, ERROR with the stack as Quarkus did, and hides everything from the body. Quarkus's own error id
  is gone with its JSON; the log line carries the path.
- **The oversize body stays outside the format**, as an accepted limit of the specification's
  section 8. `MeImportController`'s `413 IMPORT_ARCHIVE_TOO_LARGE` remains the client's documented
  refusal, `imports.max_chunk_bytes` sitting under the HTTP bound on purpose.
- **A `406` needs a `@Produces`.** The mapper ships for the day a route declares one; today the
  integration case reaches it through the test resource alone.
- **A `WebApplicationException` thrown with an entity bypasses the family.** The application throws
  none; a future one must carry no entity or accept answering outside the format.
- **`NotAllowedException` carries no `Allow`**, so the `405` publishes none, against RFC 9110's
  "MUST"; Quarkus REST does not compute the set, and computing it here would mean re-implementing its
  routing table.
- **The built-in `MismatchedInputException` body is gone in dev and test too**: a developer reads
  `MALFORMED_BODY` and the property path where they read `objectName` and `attributeName`.
