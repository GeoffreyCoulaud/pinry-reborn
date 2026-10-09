# Handoff: data shapes are decided in the specification

Date: 2026-10-09
Tier: Spec, a process lot: `docs/adr/0056-data-shapes-are-decided-in-the-specification.md` stands for the
specification. Specification review `.reviews/data-shapes-are-decided-in-the-spec-spec.md`.
Lot `0.53.0`, one block: 10 `docs/data-shapes-are-decided-in-the-spec`, the pull request this file arrives in. Written
in block 10 from its own report.

## Current state

- **`agents/data-modelling.md` gathers the 35 rules of decision 4**, for the lead writing a specification and its
  reviewer, never a teammate's brief. Root `AGENTS.md` lists it with those two readers.
- **A specification that adds or changes a datum has a `Data shape` section** (Spec phase of `agents/workflow.md`),
  and `agents/reviews/spec.md` makes a missing section, a missing fact or a "None" without its reason a finding.
- **An operator's correction may land as a block on top of the stack** (Integrate), on decision 6's three conditions
  and below the closing block; Wrap counts the fixes on top. ADR 0043's `Status:` line names ADR 0056.
- **`agents/engineering.md` lost the seven bullets the Consequences list** and points to the new document; the
  migration history rule now names the foreign keys lot. `api/AGENTS.md` forbids an inline value class in a
  persistence model.
- **The backlog** orders the foreign keys item (the Before-beta flattening folded into it) and the third-party import
  item by ADR 0056, and holds one item per thematic lot of decision 7.4.

## Evidence

- Block 10: budget 177 lines, 7 files against `main` at `ac3de3e8`, the dated documents outside the count; 178 lines,
  7 files at `bbea9ff5`, with the tier-1 fix from the lead's review.
- `command grep -n 'versioned or additive' agents/engineering.md` prints nothing, nor does a grep of the seven bold
  leads and of "append-only until beta". `agents/data-modelling.md` counts 35 bullets from `## Types` on.
- `dagger call prose` green at the branch's tip.

## Departures from the specification

- Decision 7.4 names three lots and not which inventory unit each takes. This block assigned the units left over by
  decisions 7.1 and 7.2: 11, 22 and 23 (nullability) to the sealed states lot, which also carries decision 8's two
  questions; 25 (contract wording) to the types lot; unit 20's database renames to the foreign keys item. The operator
  approved it (Tier-2 questions).
- "Closed unions are `sealed`" left `agents/engineering.md`, and its exhaustive `when` stays there as a bullet of its
  own.
- The uniqueness rule keeps engineering's "no read-before-write answers what an index already answers".
- `agents/reviews/spec.md` also makes a shape that breaks a rule a finding, the reviewer being one of the document's
  two readers (decision 1).
- The pull request was opened with `git push origin` and `gh pr create`: the branch is in no `gh stack` stack, so
  `gh stack submit` had nothing to submit.

Tier-1 fixes: `agents/workflow.md`'s opening line names the new document, and one pre-existing line of Integrate's
Detail over 120 columns was reflowed with the edit beside it. From the lead's review, `api/AGENTS.md` says how the API
builds a value type, "Value objects are `data class` or `@JvmInline value class`" having left `agents/engineering.md`:
a `@JvmInline value class` when it wraps one field, a `data class` otherwise.

## Pitfalls

- **The evidence guard refuses `python3 -` with a heredoc**, even for an edit: file content goes through the edit tool.

## Tier-2 questions

- Discuss settled every decision of ADR 0056 with the operator.
- Block 10's assignment of the leftover inventory units to the thematic lots, under departures. Answer: « Oui ok ».

## What is not validated

- The holistic review, waived by the operator in Discuss on 2026-10-09, the lot being one block.
- No test holds a rule of `agents/data-modelling.md` yet: the Konsist test of decision 3 lands with the first value
  class, in the next lot.
- Each lot's specification re-reads the code, `inventory.md` holding the line numbers of `1fc81bdc`.

## The lot's counts

- Fix-backs: 0. Fixes on top: 0. Cascaded rebases: 0. Runs re-triggered: 0.
- The operator's reading of the bodies: no remark.

## Next step

After the merge and the tag `lot/0.53.0-data-shapes-are-decided-in-the-spec`, decision 7.1's lot: `HttpUrl`, the
person and the archive, its specification carrying the first `Data shape` section.
