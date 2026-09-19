# Writing and documentation

**This document states its mandate before its argument**
(`docs/adr/0029-a-workflow-phase-states-its-mandate-before-its-argument.md`, decision 1). The bullets bind. The
`**Detail.**` paragraph that closes a section explains and binds nothing.

## The regimes

| Regime     | Property                                                         | Rule                                                   |
|------------|------------------------------------------------------------------|--------------------------------------------------------|
| **Dated**  | Append-only once frozen: records what was believed on that date. | Never edit a frozen document; write a superseding one. |
| **Living** | Describes the present; drifts silently.                          | Updated in the same commit as the change.              |

| Document                                       | Regime                                                                             |
|------------------------------------------------|------------------------------------------------------------------------------------|
| `README.md`, `SECURITY.md`, `docs/backlog.md`  | living                                                                             |
| `AGENTS.md` (root, `api/` and `clients/`), `agents/*.md`, `agents/reviews/*` | living: updated in the same commit as the change they describe |
| `contract/openapi.json`                        | generated: written by the API's build, refused by the gate when stale, never edited by hand |
| `docs/specs`, `docs/adr`, `docs/handoffs`      | dated, append-only                                                                 |

## Rules

- **A lot's dated documents freeze when its last block merges**, not at writing and not at the first pull request that
  carries them. Until then a block's pull request may correct them, in the `(Corrected: ...)` form at the sentence it
  corrects, never a rewrite. After the freeze, changes go in a new dated document, cross-linked both ways, the old one
  marked `Status: Superseded by <file>`.
- **Every measured figure in a dated document carries the identifier of what produced it**: the run id, the probe's
  label, the pull request, the file. **It binds documents written from lot `0.18.0` on**; the dated documents already
  written are append-only and cannot be brought into conformance.
- **The backlog is the pressure valve**: findings the operator declined, or that genuinely belong to another lot, are
  proposed for it rather than done or lost. What the operator authorized is fixed in the lot instead.
- **A backlog item holds in two lines**, plus a pointer to the dated document that carries the reasoning. Symptom and
  where it lives, nothing else.
- **An item whose reasoning lives nowhere else keeps it**, and the file marks the items in that state. Put no count
  here. **This is an exception to inherit, not to create**: a new item is filed by a lot that has a spec and a handoff,
  so its reasoning goes there and the entry stays at two lines.
- **A dated document does not put a number on a living file**: it records what it did; the count is read where it
  lives. Say "the items this lot leaves open are 1, 2 and 14", never "the band holds three".
- **A living document does not count what it mentions.** At best it names where the list is read ("the modules
  `api/settings.gradle.kts` declares"), at worst it writes the list out. Correcting a count is not enough: rephrase
  it. Measurements, thresholds, sizes and examples are not counts and stay.

**Detail.** A backlog item holds in two lines because the argument is usually already written in the spec or handoff of
the lot that filed it, and copying it here stores it twice and makes the file unreadable at the length that costs. The
inherited exception exists because dated documents are append-only: a finding whose argument was only ever written into
the backlog cannot be moved out of it now, and compressing it would destroy it rather than relocate it. A count in this
file would drift on the next edit. The source rule is
`docs/adr/0032-a-number-carries-its-source-and-a-report-carries-its-file.md`, decision 3: lot `0.17.0` transcribed a
probe that had enabled two Gradle settings as the figure for one of them, and the raw evidence carried both the
configuration and the label that the transcription dropped.

## Style

- **A document under `agents/` states its mandate before its argument**: short imperative bullets, one rule each and no
  justification, then a `**Detail.**` paragraph that explains and binds nothing
  (`docs/adr/0029-a-workflow-phase-states-its-mandate-before-its-argument.md`, decision 1). Every document under
  `agents/` is converted.
- **A closed list is written out in one living document**, the one carrying the rule that closes it; every other living
  document names the list and states no member and no total
  (`docs/adr/0029-a-workflow-phase-states-its-mandate-before-its-argument.md`, decision 2). The dated documents are
  append-only and exempt. A list a document decides keeps its members there.
- **Everything in the repository is in English** (identifiers, comments, logs, commits, PRs, docs); conversation stays
  in the user's language; genuine domain data keeps its own language.
- **Write in plain language**: lead with the point, active voice, present tense, short sentences, common words; lists,
  tables and headings where they carry structure faster than prose. Applies to everything written for the repository and
  every message to the user.
- **A comment holds in two lines.** Past that it is documentation and goes where documentation lives (spec, ADR,
  backlog, handoff); the comment keeps the one sentence that says why plus the pointer.
- **Comments explain why, not what.** Density matches the surrounding file.
- **No abbreviations in code, comments, KDocs or logs**: domain terms are spelled out ("garbage collection", not "GC").
  Narrative documents may abbreviate after the first definition.
