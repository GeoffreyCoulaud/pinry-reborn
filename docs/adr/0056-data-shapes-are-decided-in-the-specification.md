# 0056. Data shapes are decided in the specification

Status: Accepted
Date: 2026-10-09
Amends: `docs/adr/0043-blocks-stack-and-a-pull-request-is-written-for-a-tech-lead.md`, decisions 3 (the fix-back)
and 7 (the closing block on top); `agents/engineering.md`, Design invariants, "The migration history is append-only
until beta".

## Context

Lot `0.52.0` met ADR 0043's failure criterion: 20 continuous integration runs re-triggered for 16 blocks
(`docs/handoffs/2026-10-08 - handoff - the-pin-credits-its-people.md`, The lot's counts). An operator's fix-back
landed on block 10, at the bottom of the stack, and rebased every branch above it; two more landed on block 100. Block
10's was a data-modelling correction: a domain type, `PersonUrls`, carried the database's encoding.

That correction was not the first. `corrections.md`, beside this file, lists the operator's data-modelling corrections
found across the handoffs, specifications, ADRs and pull request comments. No document gathers the rules they imply;
`agents/engineering.md` holds some, and one of them, "never a bare `String` or `Int`", no code follows.

Every decision below is the operator's, taken in Discuss on 2026-10-09.

## Decision

### The document

1. **`agents/data-modelling.md` gathers the data-modelling rules.** Its readers are the lead writing a specification
   and the specification's reviewer. It is not put in a teammate's brief: a teammate acts on a specification that has
   decided. Root `AGENTS.md` lists it with its two readers.
2. **It covers the domain, storage, the contract and the archive**, not the interface. The publisher given a
   multi-value field (#375) was an interface design error: the model had it right.
3. **It states principles, in no language.** Each ecosystem root says in its own `AGENTS.md` how it applies them. The
   API's states that **no inline value class appears in a persistence model**: the operator observed Ebean's
   migration generation break on one in silence, as `agents/engineering.md`'s "Verify every inline value class"
   rule, from the installed baseline (`c802a509`), already warned. The mapper converts. A Konsist test holds the rule
   from the first value class on, in the next lot.
4. **The rules are the following, 35 of them**, which the document carries one bullet each and this ADR does not keep
   alive:
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
     - A natural key decides whether a datum already exists; an identifier an archive or a client supplies never
       decides it, and a client never chooses a new row's identifier.
     - A matching identity reuses the row; a differing one creates a row; no existing row is modified to reconcile.
     - The database is the authority on uniqueness, and only the unique violation is translated, told apart by its
       type. The written exception survives: `UserDataExportRequester.createPending`'s `findPendingForUser`, which
       orders its refusals, 409 ahead of 429.
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
     - Nullability comes from the type, and no annotation restates it; refusing a blank value is not nullability. The
       document notes under this rule the open question of decision 8 on request bodies.
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
   optionality, its identity and uniqueness, its storage encoding, its shape on the wire, and its place in the
   archive. "None", with its reason, completes a fact. The specification's reviewer refuses a section with a fact
   missing, citing `agents/data-modelling.md`.

### The fix on top

6. **An operator's correction to a block low in the stack may land as a new block on top of the stack** instead of a
   fix-back, only when all three hold: it fits in one block, the operator approves it, and it costs less than the
   rebase. The cost is counted as ADR 0043's criterion counts it: the branches a fix-back would move, each a run
   re-triggered, against the one run of the new block. Once the closing block is on the stack, the fix goes below it
   and the closing block is rebased onto it, one more run. Wrap's counts record the fixes on top beside the fix-backs.
   Otherwise ADR 0043's fix-back stands.

### What follows

7. **The lots after this one**, in this order. `inventory.md`, beside this file, lists every violation of decision 4
   found in the code, with a proposed grouping:
   1. **`HttpUrl`, the person and the archive**: everything that changes the archive the importer will write. A value
      class over `java.net.URI`, absolute and http(s) only, normalised in its factory to RFC 3986's equivalences
      (sections 6.2.2 and 6.2.3: lower-case scheme and host, percent-encoding in upper case, percent-encoded
      unreserved characters decoded, dot segments removed, default port dropped, empty path as `/`), the trailing
      slash kept; every address of the domain takes it, and `Person.urls` becomes a `Set<HttpUrl>`. `persons.jsonl`
      enters the archive, every person in it, pin lines naming theirs by `{name, urls}`. The Konsist test of
      decision 3.
   2. **Foreign keys**: the migration history flattened now, rather than before beta, into one baseline that declares
      `ON DELETE CASCADE`, then `foreign_keys` turned on, the development data being disposable. The history is
      append-only again from that baseline. It takes the inventory's items that need a migration: derived and unread
      columns, column and table renames, the legacy `when_created` and `when_modified` names included, and `CHECK`
      constraints.
   3. **The importer** of `docs/adr/0055-third-party-imports-write-the-user-data-archive.md`.
   4. **The rest of the inventory, in three thematic lots**: types; sealed states; batch writes and transactions.
8. **Two questions wait for their lot**: `@NotNull` on request bodies, which `ArchitectureKonsistTest` requires because
   RESTEasy hands a null body to a Kotlin parameter that is not nullable (ADR 0039), against the nullability rule; and
   whether SmallRye publishes the sealed wire states as `oneOf`.

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

- **`agents/workflow.md`**: the Spec phase requires the `Data shape` section of decision 5; Integrate carries the fix
  on top of decision 6, and its Detail no longer says no lot has run a fix-back (lot `0.52.0` ran four); Wrap's
  counts gain the fixes on top. **`agents/reviews/spec.md`** makes a section with a fact missing a finding.
- **`agents/engineering.md`** keeps its language and backend norms and points to `agents/data-modelling.md`. These
  bullets leave it for the new document, each becoming one of decision 4's rules: "Closed unions are `sealed`"
  (its exhaustive `when` stays), "Value objects are `data class` or `@JvmInline value class`", "Nullability at the
  boundary is resolved at the boundary", "Anything a client depends on is versioned or additive" (rewritten as the
  alpha rule), "The wire format is not the domain model", "Identifiers, casing and normalisation are decided once",
  and "The database is the authority on uniqueness". "The migration history is append-only until beta" is rewritten
  to decision 7.2. Every other bullet stays.
- **`api/AGENTS.md`** states decision 3's rule, its Konsist test to land with the first value class.
- **The backlog**: the foreign keys item (P2) and the third-party import item (Features) point to this ADR for their
  order; the Before-beta "Flatten the migration history" item is folded into the foreign keys item; one item is filed
  for decision 7.4's three lots, pointing to `inventory.md`. The first lot is the handoff's next step.
- **Overturned in part by the next lot**, whose documents mark them `(Corrected: ...)` or supersede them:
  `docs/specs/2026-10-08-the-pin-credits-its-people.md` decisions A (the server normalises no address) and D (a
  person travels only inside pin lines), and decision E with
  `docs/adr/0055-third-party-imports-write-the-user-data-archive.md` decisions 8 and 11 (addresses become a set of
  normalised `HttpUrl`, a collection's identity a normalised address).
- `inventory.md` holds line numbers of `1fc81bdc`; each lot re-reads the code before its specification.

## Block table

| Block | Branch | What its checks have to fail on |
|---|---|---|
| 10 | `docs/data-shapes-are-decided-in-the-spec` | `agents/data-modelling.md` exists, states its mandate before its argument (`agents/writing.md`, Style), and carries the 35 rules of decision 4, one bullet each, the interface absent; root `AGENTS.md` lists it with its two readers. `agents/workflow.md`'s Spec phase requires the `Data shape` section with its six facts, Integrate the fix on top with its three conditions, its cost and its place below the closing block, and Wrap counts the fixes on top. `agents/reviews/spec.md` makes a missing fact a finding. Each `agents/engineering.md` bullet the Consequences list as leaving is absent from it by its bold lead and present in the new document; `grep -n 'versioned or additive' agents/engineering.md` prints nothing. `api/AGENTS.md` forbids a value class in a persistence model. ADR 0043's `Status:` line names this ADR. The backlog holds the changes of the Consequences. `dagger call prose` green. This block carries this ADR and the lot's handoff, with its counts |

**Adjacent backlog items**: the foreign keys item and the Before-beta flattening item, taken by decision 7.2's lot, and
the third-party import item, decision 7.3's. A lot of one block: the operator waived the holistic review in Discuss on
2026-10-09.
