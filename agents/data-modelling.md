# Data modelling

The rules a datum's shape follows, in the domain, storage, the contract and the archive. Process is in
`agents/workflow.md`; Kotlin and backend norms in `agents/engineering.md`.

**This document states its mandate before its argument**
(`docs/adr/0029-a-workflow-phase-states-its-mandate-before-its-argument.md`, decision 1). The bullets bind. The
`**Detail.**` paragraph that closes a section explains and binds nothing.

## Reach

- **Its readers are the lead writing a specification and the specification's reviewer.** It is not put in a
  teammate's brief: a teammate acts on a specification that has decided.
- **A specification decides each datum's shape against these rules**, in the `Data shape` section the Spec phase of
  `agents/workflow.md` requires.
- **It covers the domain, storage, the contract and the archive**, not the interface.
- **It states principles, in no language**: each ecosystem root says in its own `AGENTS.md` how it applies them.

**Detail.** The rules are `docs/adr/0056-data-shapes-are-decided-in-the-specification.md`, decisions 1 to 5, drawn
from the operator's corrections that `corrections.md`, beside that ADR, lists. The interface is out because its errors
are not the model's: the publisher given a multi-value field (#375) was an interface design error, the model having it
right.

## Types

- **A standard type that names the concept comes first** (`UUID`, `Instant`, `URI`); a value class only for an
  invariant the standard type lacks. Never a bare string or integer for a concept.
- **A type never carries a storage or serialisation format**: the encoding is the adapter's mapper's.
- **An invariant is held by the type's shape when the shape can express it**: an enum, sealed variants, a nested
  nullable object for fields optional together, a set. Otherwise a fallible factory returns null or a sealed result;
  only a mapper calls it, and the mapper turns the failure into an exception.
- **The normalisation that defines equality lives in the type's constructor or factory**, and never throws.
- **Elements distinct by nature form a set.**
- **A concept's name says what it is, and a rename crosses every layer.**

## Identity

- **A natural key decides whether a datum already exists**: an identifier an archive or a client supplies never
  decides it, and a client never chooses a new row's identifier.
- **A matching identity reuses the row, and a differing one creates a row**: no existing row is modified to reconcile.
- **The database is the authority on uniqueness**: no read-before-write answers what an index already answers, and
  only the unique violation is translated, told apart by its type. The written exception survives:
  `UserDataExportRequester.createPending`'s `findPendingForUser`, which orders its refusals, 409 ahead of 429.
- **A unique index covers recycled rows.**
- **A join table is unique on its pair and indexed on each side.**
- **A symmetric pair is stored in a canonical order.**
- **A find-or-create runs inside the transaction of the write that needs it.**
- **The domain's equality matches the database's identity**: a case fold or an order the unique index applies, the
  domain type applies too.

## Storage

- **A list held in one column uses a standard serialisation (JSON) in its canonical form**, never a home-made
  delimiter; references to entities are a join table.
- **No binary in the database.**
- **Derived data is not stored.**
- **A column nothing reads is deleted**, unless its reader is named.
- **A write over many rows reads in one query and writes in one batch.**
- **Alpha data is disposable**: no backfill, no version column to heal old rows.

## Nullability

- **Nullability comes from the type, and no annotation restates it**; refusing a blank value is not nullability.
  Open question, for its lot (ADR 0056, decision 8): `@NotNull` on request bodies, which `ArchitectureKonsistTest`
  requires because RESTEasy hands a null body to a Kotlin parameter that is not nullable
  (`docs/adr/0039-a-batch-route-is-all-or-nothing.md`).
- **A collection is never nullable: its absence is empty.** A third state (unchanged, unknown, no filter, not loaded)
  takes a shape of its own, never null. An entity is never built with a collection it did not load.
- **An optional datum is optional whatever its entry path.**

## Contract

- **The contract describes a behaviour, never a storage mechanism.**
- **A datum the user identifies by its content is named on the wire by that content**, never by an identifier.
- **The server writes every address a client follows.**
- **The wire carries what the client acts on**: domain states the client treats alike are one.
- **A nullable output property is always present, null when absent**; a `PUT` carries every property, null included.
- **During the alpha a breaking change raises the contract's major version**, and the archive's `formatVersion` does
  not change.

## Archive

- **The archive names by natural keys**: its identifiers are discarded.
- **An entity, with an identity and managed for itself, has its own section**; a value without one travels inside the
  lines that carry it.
- **A concept has one shape, on the wire and in the archive.**
- **A fact about the work is restored as written**; what the server stamps follows the server's rules.

## Cross-cutting

- **An invariant is held by structure** (a type, an index, a transaction, a test), never by a comment.
- **A dependent row is deleted with what it depends on, on every deletion path**; a periodic sweep collects what an
  interruption leaves.

**Detail.** Seven bullets left `agents/engineering.md` for these rules: "Closed unions are `sealed`" and "Value
objects are `data class` or `@JvmInline value class`" for the first and third rules of Types; "Nullability at the
boundary is resolved at the boundary" for the fallible factory only a mapper calls; "The wire format is not the domain
model" for the type that carries no format; "Identifiers, casing and normalisation are decided once" for the
normalisation in the type and the equality that matches the database; "Anything a client depends on is versioned or
additive" for the alpha rule of Contract; and "The database is the authority on uniqueness" under its own name.
