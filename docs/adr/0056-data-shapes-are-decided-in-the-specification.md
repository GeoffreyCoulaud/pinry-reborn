# 0056. Data shapes are decided in the specification

Status: Accepted
Date: 2026-10-09
Amends: `docs/adr/0043-blocks-stack-and-a-pull-request-is-written-for-a-tech-lead.md`, decision 3 (the fix-back).

## Context

Lot `0.52.0` met ADR 0043's failure criterion: 20 continuous integration runs re-triggered for 16 blocks
(`docs/handoffs/2026-10-08 - handoff - the-pin-credits-its-people.md`, The lot's counts). The operator's fix-backs
landed on blocks 10 and 100, at the bottom of the stack, and each rebased nearly every branch above. Block 10's was a
data-modelling correction: a domain type, `PersonUrls`, carried the database's encoding.

That correction was not the first. `corrections.md`, beside this file, lists the operator's data-modelling corrections
found across the handoffs, specifications, ADRs and pull request comments. No document gathers the rules they imply;
`agents/engineering.md` holds some, and one of them, "never a bare `String` or `Int`", no code follows.

Every decision below is the operator's, taken in Discuss on 2026-10-09.

## Decision

### The document

1. **`agents/data-modelling.md` gathers the data-modelling rules.** Its readers are the lead writing a specification
   and the specification's reviewer. It is not put in a teammate's brief: a teammate acts on a specification that has
   decided.
2. **It covers the domain, storage, the contract and the archive**, not the interface. The publisher given a
   multi-value field (#375) was an interface design error: the model had it right.
3. **It states principles, in no language.** Each ecosystem root says in its own `AGENTS.md` how it applies them. The
   API's states that **no inline value class appears in a persistence model**, Ebean silently breaking the migration
   generation; the mapper converts, and a Konsist test holds it.
4. **The rules are the following**, which the document carries and this ADR does not keep alive:
   - **Types.**
     - A standard type that names the concept first (`UUID`, `Instant`, `URI`); a value class only for an invariant
       the standard type lacks. Never a bare string or integer for a concept.
     - A type never carries a storage or serialisation format: the encoding is the adapter's mapper's.
     - An invariant is held by the type's shape when the shape can express it: an enum, sealed variants, a nested
       nullable object for fields optional together, a set. Otherwise a fallible factory returns null or a sealed
       result; only a mapper calls it, and the mapper turns the failure into an exception.
     - The normalisation that defines equality lives in the type's constructor or factory, and never throws.
     - Elements distinct by nature form a set.
     - A concept's name says what it is, and a rename crosses every layer.
   - **Identity.**
     - A natural key identifies; an identifier supplied by a client or an archive never does.
     - A matching identity reuses the row; a differing one creates a row; no existing row is modified to reconcile.
     - The database is the authority on uniqueness, and only the unique violation is translated, told apart by its type.
     - A unique index covers recycled rows.
     - A join table is unique on its pair and indexed on each side.
     - A symmetric pair is stored in a canonical order.
     - A find-or-create runs inside the transaction of the write that needs it.
     - The domain's equality matches the database's identity: a case fold or an order the unique index applies, the
       domain type applies too.
   - **Storage.**
     - A list held in one column uses a standard serialisation (JSON) in its canonical form, never a home-made
       delimiter; references to entities are a join table.
     - No binary in the database.
     - Derived data is not stored.
     - A column nothing reads is deleted, unless its reader is named.
     - A write over many rows reads in one query and writes in one batch.
     - Alpha data is disposable: no backfill, no version column to heal old rows.
   - **Nullability.**
     - Nullability comes from the type, and no annotation restates it; refusing a blank value is not nullability.
     - A collection is never nullable: its absence is empty. A third state (unchanged, unknown, no filter, not loaded)
       takes a shape of its own, never null. An entity is never built with a collection it did not load.
     - An optional datum is optional whatever its entry path.
   - **Contract.**
     - The contract describes a behaviour, never a storage mechanism.
     - A datum the user identifies by its content is named on the wire by that content, never by an identifier.
     - The server writes every address a client follows.
     - The wire carries what the client acts on: domain states the client treats alike are one.
     - A nullable output property is always present, null when absent; a `PUT` carries every property, null included.
     - During the alpha a breaking change raises the contract's major version, and the archive's `formatVersion` does
       not change.
   - **Archive.**
     - Natural keys; the archive's identifiers are discarded.
     - An entity, with an identity and managed for itself, has its own section; a value without one travels inside
       the lines that carry it.
     - A concept has one shape, on the wire and in the archive.
     - A fact about the work is restored as written; what the server stamps follows the server's rules.
   - **Cross-cutting.**
     - An invariant is held by structure (a type, an index, a transaction, a test), never by a comment.
     - A dependent row is deleted with what it depends on, on every deletion path; a periodic sweep collects what an
       interruption leaves.

### The specification

5. **A specification that adds or changes a datum has a `Data shape` section**: for each datum, its domain type, its
   identity and uniqueness, its storage encoding, its shape on the wire, and its place in the archive. The
   specification's reviewer refuses one left incomplete, citing `agents/data-modelling.md`.

### The fix on top

6. **An operator's correction to a block low in the stack may land as a new block on top of the stack** instead of a
   fix-back, only when all three hold: it fits in one block, the operator approves it, and it costs less than the
   rebase. Otherwise ADR 0043's fix-back stands.

### What follows

7. **The lots after this one**, in this order. `inventory.md`, beside this file, lists every violation of decision 4
   found in the code, with a proposed grouping:
   1. **`HttpUrl`, the person and the archive**: everything that changes the archive the importer will write. A value
      class over `java.net.URI`, absolute and http(s) only, normalised in its factory to RFC 3986's equivalences
      (sections 6.2.2 and 6.2.3: lower-case scheme and host, default port dropped, empty path as `/`, dot segments
      removed, percent-encoding in upper case), the trailing slash kept; every address of the domain takes it, and
      `Person.urls` becomes a `Set<HttpUrl>`. `persons.jsonl` enters the archive, every person in it, pin lines
      naming theirs by `{name, urls}`. The Konsist test of decision 3.
   2. **Foreign keys**: the migration history flattened into one baseline that declares `ON DELETE CASCADE`, then
      `foreign_keys` turned on, the development data being disposable. It takes the inventory's items that need a
      migration: derived and unread columns, column and table renames, `CHECK` constraints.
   3. **The importer** of `docs/adr/0055-third-party-imports-write-the-user-data-archive.md`.
   4. **The rest of the inventory, in thematic lots**: types, sealed states, batch writes and transactions.
8. **Two questions wait for their lot**: `@NotNull` on request bodies, which `ArchitectureKonsistTest` requires because
   RESTEasy does not read Kotlin's nullability, against the nullability rule; and whether SmallRye publishes the sealed
   wire states as `oneOf`.

### Rejected

9. Giving the document to every teammate: it fills their context with what the specification has already decided.
10. Dropping "never a bare string", or keeping it with a wrapper type for every collection: the operator keeps the
    rule, and a collection is a standard one of the element type.
11. A constructor that checks nothing and trusts its caller: nothing would keep another caller from building an
    invalid value.
12. Removing a path's trailing slash in `HttpUrl`: RFC 3986 does not make `/a` and `/a/` equivalent, and a wrong merge
    would be silent, where two people to merge are visible.
13. Persons referenced from pin lines by an archive identifier: the only entity referenced that way, against
    `docs/adr/0015-import-identifies-by-natural-key.md`.
14. Foreign keys without cascade, or in the first lot: "on fait les choses proprement", and the flattening reviews
    differently from the types.
15. One lot of the whole inventory: a stack of that length is ADR 0043's failure made worse.

## Consequences

- `agents/workflow.md`'s Spec phase names the `Data shape` section, and its Integrate phase the fix on top.
  `agents/reviews/spec.md` refuses an incomplete section. `agents/engineering.md` keeps what is not data modelling
  and points to the new document for what moved. `api/AGENTS.md` states decision 3's rule.
- `docs/specs/2026-10-08-the-pin-credits-its-people.md`, decision D, is overturned by the next lot: a person no pin
  names no longer leaves an export.
- The backlog gains one item per lot of decision 7 but the first, each pointing to `inventory.md`. The foreign keys
  item already open is that lot's.
- `inventory.md` holds line numbers of `1fc81bdc`; each lot re-reads the code before its specification.

## Block table

| Block | Branch | What its checks have to fail on |
|---|---|---|
| 10 | `docs/data-shapes-are-decided-in-the-spec` | `agents/data-modelling.md` exists, states its mandate before its argument (`agents/writing.md`, Style), and carries every rule of decision 4, the interface absent. `agents/workflow.md`'s Spec phase requires the `Data shape` section with its five facts, and the fix on top with its three conditions. `agents/reviews/spec.md` makes an incomplete section a finding. `grep -n 'versioned or additive' agents/engineering.md` prints nothing, and the modelling rules moved out of it appear in the new document. `api/AGENTS.md` forbids a value class in a persistence model. ADR 0043's `Status:` line names this ADR. The backlog holds the items of the Consequences. `dagger call prose` green. This block carries this ADR |

**Adjacent backlog items**: the foreign keys item (`foreign_keys` is off), taken by decision 7's second lot. A lot of
one block: the holistic review is waived.
