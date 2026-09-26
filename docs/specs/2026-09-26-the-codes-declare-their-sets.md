# The codes declare their sets

Date: 2026-09-26
Status: Draft for the operator. One specification review ran, `.reviews/the-codes-declare-their-sets-spec.md`,
its 2 MAJOR and 5 MINOR closed in this document. Frozen when the lot's closing block merges.
Branches: one stack, each block on the branch below it: 10 `refactor/the-failures-are-declared`,
20 `feat/the-open-codes-are-published`, 30 `feat/the-export-says-gone`; the closing block is
`fix/the-codes-declare-their-sets-closes`, on 30.
ADRs: `docs/adr/0044-a-response-code-declares-its-set.md`, which records decisions A to F and
amends `docs/specs/2026-09-25-the-data-travels.md` decision I, whose open fields stayed plain
strings. `agents/engineering.md` carries the rule so the next field is not weighed from scratch.

The backlog item "the contract's other string fields are not weighed as open or closed" asked for
the weighing. Every code-carrying string of the contract turned out to be weighed already; what the
Discuss of 2026-09-26 found wrong is how the open ones are open, and one closed set that is too fine.

## 1. Goal

A client knows every value a code field can carry today, and which fields may carry more tomorrow.
Our own client fails `pnpm run typecheck` when the server adds a value it has no sentence for. A
closed set holds only values that change what the client does.

## 2. What exists today

- **Closed, and right**: `PinImageStatusDto`, `DownloadStatusDto`, `UserDataImportStateDto` (each
  value handled differently by the web application, `ImportSection.tsx`, `lib/notices.ts`), the
  refusal codes declared per response (`docs/adr/0042-the-presentation-owns-the-refusal-codes.md`),
  and the input enums, whose closure costs a client nothing.
- **Closed, and too fine: `UserDataExportStateDto`.** `EXPIRED`, `DELETED` and `SUPERSEDED` are handled
  identically everywhere (`ExportSection.tsx`: the request button; `lib/notices.ts`: no notice), and
  the download already answers all three with one `410 EXPORT_GONE` (`BaseErrorMapper`). The domain
  already groups them as `UserDataExportState.isGone`.
- **Open, and undeclared**: `reasonCode` (three positions) and `failureCode` on both data rows are
  `"type": ["string", "null"]` in `contract/openapi.json`, `UserDataImportIssueOutputDto.kind` a
  non-null `"type": "string"`, none with a list of values. `reasonCode` and `kind` publish a domain
  enum's `name` (`ImageDownloadDtoMapper`, `PinImageStateMapper`, `UserDataImportIssueDtoMapper`),
  so renaming a domain constant changes the wire silently, the defect ADR 0042 removed for refusal
  codes.
- **`failureCode` is a `String` from the domain to the DTO** (`UserDataExport.failureCode`,
  `UserDataImport.failureCode`), written from string constants: `USER_GONE`, `DISK_FULL`,
  `BUILD_FAILED` in `UserDataExportBuilder`, `EXPORT_INTERRUPTED` in `ReapUserDataExports`,
  `USER_GONE`, `IMPORT_FAILED`, `ARCHIVE_UNREADABLE`, `MANIFEST_MISSING`,
  `UNSUPPORTED_FORMAT_VERSION` in `UserDataImportRunner`, `IMPORT_INTERRUPTED` in
  `ReapUserDataImports`. Nothing else writes the column, so a stored row holds one of those names:
  `grep -rnwE "USER_GONE|DISK_FULL|BUILD_FAILED|EXPORT_INTERRUPTED|IMPORT_FAILED|ARCHIVE_UNREADABLE|MANIFEST_MISSING|UNSUPPORTED_FORMAT_VERSION|IMPORT_INTERRUPTED" api/*/src/main`
  finds these four files and nothing else.
- **The web application's sentence tables are keyed by hand** (`downloadReasons.ts`,
  `importIssues.ts`, `dataFailures.ts`), their lookups typed `string`; a value the server adds
  reaches the general sentence, or the server's own `message` for a download, and nothing says so.
  Their comments cite the rule this lot replaces.

## 3. The decisions

Each is the operator's answer of 2026-09-26 in Discuss.

**A. The three open codes stay open.** A value added to a response `enum` is
`response-property-enum-value-added` for `oasdiff` v1.31.0, and a client generated with strict
enums fails to read it: every new reason would be a contract major. A reason or a kind has a correct
fallback; a state does not.

**B. An open code is published as `x-extensible-enum`**, the known values without closing the set.
Measured on 2026-09-26 with `tufin/oasdiff:v1.31.0 breaking --fail-on WARN` on two-document probes
(one `GET` answering an object whose `reasonCode` takes each shape, values `A,B` then `A,B,C`):

| Shape | Value added | Plain nullable `string` to this shape |
|---|---|---|
| `enum` | error, `response-property-enum-value-added` | error, same rule |
| `anyOf: [$ref to an enum, string]` | error, same rule | errors, `response-property-any-of-added` and `response-property-type-changed` |
| `x-extensible-enum`, inline | nothing | nothing |
| `anyOf: [$ref to an x-extensible-enum component, null]` | nothing | error, `response-property-list-of-types-widened` |
| `$ref to an x-extensible-enum component`, non-null | nothing | nothing, from a non-null `string` (the `kind` case) |

Removing a value from an `x-extensible-enum` also reports nothing. The fourth shape is the one
SmallRye emits for a nullable reference, enums included (`PinSortStrategyInputEnum`), so the three
`reasonCode` positions make the lot a contract major: `quarkus.smallrye-openapi.info-version` goes
to `19.0.0` in block 20. The row of the second shape and the fifth were reproduced by the
specification review.

**C. Each open code has a presentation twin**, an enum under `dtos/output` mapped from the domain by
an exhaustive `when`, as the states have: `DownloadReasonDto`, `UserDataImportIssueKindDto`,
`UserDataExportReasonDto`, `UserDataImportReasonDto`. A renamed domain constant then fails to
compile rather than changing the wire. A build-stage filter, `openapi/ExtensibleEnumsFilter.kt`,
turns each twin's component from `enum` into `x-extensible-enum`, from a list naming the four
classes (`Extensible.addExtension`, SmallRye OpenAPI model reference, through Context7).

**D. The client reads `x-extensible-enum` as its known values or any string.**
`packages/api-client` generates through the Node API of `openapi-typescript` 7.13.0 with a
`transform` (openapi-ts.dev, "Node.js API", through Context7) instead of its command line: a schema
carrying the extension becomes `"A" | "B" | (string & {})`, plus `null` when its type admits it.
`packages/api-client` exports `Known<T>`, which keeps the literals. A table typed
`Record<Known<...>, ...>` then fails `tsc` when it lacks a known value; an unknown string and `null`
stay assignable. Measured on the same probe with the repository's `tsc` 6.0.3: a `@ts-expect-error`
on a table missing `B` holds, `tsc` exiting 0. The tables do not guard the transform: without it
the field is `string`, `Known<string>` is `never`, and `Record<never, ...>` is `{}`, which accepts
any table (reproduced by the specification review). So `packages/api-client` commits the guard: a
`@ts-expect-error` above a table missing one known value of each extensible component, which a
lost transform leaves unused, TS2578. (Corrected: openapi-fetch 0.17 wraps a response in
`Readable<T>`, which maps anything extending `object` and `string & {}` does, so the web application
failed `tsc` at five sites. The field stays `string`; `generate.mjs` appends an `extensibleEnums`
interface of each component's known values, which `Known<"DownloadReasonDto">` reads. Generated
through the command line, `index.ts` fails on the missing interface, TS2305, so no guard is committed.
The operator's answer (a) in block 20.)

**E. The export's three gone states become `GONE`, and its cause travels in `reasonCode`.** The
contract's state keeps only what changes the client's behaviour: `PENDING`, `READY`, `FAILED`,
`GONE`. The domain keeps its six states; the twin's `when` maps three to one.

**F. One `reasonCode` field replaces `failureCode`**, on the export and on the import: why the row
is in its state, the name the image rows already use. On an export it carries the failure for
`FAILED` and the state's own name for `GONE` (`EXPIRED`, `DELETED`, `SUPERSEDED`); on an import, the
failure for `FAILED`; otherwise `null`.

**G. One reason enum per side.** `UserDataExportReasonDto` lists the four export failures and the
three gone causes; `UserDataImportReasonDto` the six import failures. A shared enum would announce
`MANIFEST_MISSING` on an export. In the domain, `UserDataExportFailure` and `UserDataImportFailure`
replace the string constants; the column stays a string holding the same names, so no migration.
It is read with `valueOf`, as the `state` column is, so an unknown stored name throws as an unknown
state does.

**H. The rule is written once**, in `agents/engineering.md` under "This project's API contract": a
response code is a closed `enum` when an unknown value has no correct default, an
`x-extensible-enum` when it has one, the cause beside a closed status; both are presentation twins;
refusal codes stay closed per response, a new one changing what the route does (ADR 0042).
`clients/AGENTS.md` says how the client reads the extension.

## 4. Blocks

| Block | Branch | What its tests have to fail on |
|---|---|---|
| 10 | `refactor/the-failures-are-declared` | The domain gains `UserDataExportFailure` and `UserDataImportFailure`; both entities' `failureCode` takes them, and every producer section 2 lists writes an entry. A persistence test saves each entry of both through the repository and reads `failure_code` with SQL, asserting the entry's name; a row whose `failure_code` is not an entry throws on read. No file is added under `dbmigration/`. The contract is unchanged: `dagger call contract` produces the committed document. Carries this specification and ADR 0044. (Corrected: 23 files, past the bound, so block 10 carries the export side and block 15, `refactor/the-import-failures-are-declared` on 10, the import side; 20 stacks on 15.) |
| 20 | `feat/the-open-codes-are-published` | Mapper tests, as `PinImageStateMapperTest` is written, covering every entry of `DownloadReason` and `UserDataImportIssueKind`. A contract test: `DownloadReasonDto` and `UserDataImportIssueKindDto` carry `x-extensible-enum` equal to the twin's names and no `enum`, and the four positions reference them. `dagger call contract-guard` green at `19.0.0`, red at `18.1.0`. `downloadReasons.ts` and `importIssues.ts` typed by `Known<...>`, their comments rewritten, and the committed guard of decision D, which generating through the command line fails. `agents/engineering.md`'s "This project's API contract" names `x-extensible-enum` and `clients/AGENTS.md` names the `transform` (decision H). (Corrected: 24 files, past the bound, so block 20 carries the API side and block 25, `feat/the-client-reads-the-open-codes` on 20, the client side and both living documents; 30 stacks on 25. `clients/AGENTS.md` names the `extensibleEnums` interface, decision D's correction.) |
| 30 | `feat/the-export-says-gone` | `UserDataExportStateDto` is exactly `PENDING, READY, FAILED, GONE`, the mapper test mapping each gone state to `GONE` with its `reasonCode`, and each failure to `FAILED` with its. `failureCode` is absent from the contract and `reasonCode` references the two new components, published as in 20. The journeys of `docs/specs/2026-09-25-the-data-travels.md` blocks 20 and 60 still pass with `GONE` rows: request button, no link, no notice. `dataFailures.ts` becomes one table per side, typed by `Known<...>`, each known value mapped to its sentence or explicitly to the general one (`USER_GONE`, `IMPORT_FAILED`) or to none (the gone causes, which the section does not show), its comment rewritten. Deletes the backlog item (Corrected: 23 files, past the bound, so block 30 carries the export side, and block 35, `feat/the-import-says-why` on 30, the import's `reasonCode`, its table and the backlog item; the closing block stacks on 35.) |

Each block is green alone: 10 changes no wire; 20 publishes the extension and its client reading;
30 uses both. Block 20 carries the major; 30 stays at `19.0.0`, the guard comparing against
`origin/main`.

## 5. Adjacent backlog items

None besides the item this lot closes. The browser extension's CORS origin and the import
follow-ons touch no code field of the contract.

## 6. Out of scope

- **Sentences for the gone causes.** The account screen shows none today; `reasonCode` makes one
  possible, and nothing asks for it. Observed as no new message key for them in the catalogue.
- **The refusal codes' own mechanism.** ADR 0042 stands; decision H only writes down why. Observed
  as an empty diff on `ProblemCode.kt`, `SharedRefusalsFilter.kt` and `BaseErrorMapper.kt`.
