# Review mandate: holistic

**Artefact: a whole lot on `main`**, `git diff <previous lot tag>..origin/main`, every code block merged and nothing
in flight. Run once per lot in tier Spec, at the head of Wrap, in an agent the lead dispatches by name.

**This document states its mandate before its argument**
(`docs/adr/0029-a-workflow-phase-states-its-mandate-before-its-argument.md`, decision 1). The bullets bind. The
`**Detail.**` paragraph that closes a section explains and binds nothing.

## What you deliver

- **Determine your own range**: `git tag -l 'lot/*'` lists the annotated lot tags, the newest is the lot before this
  one. `origin/main` and not `main`.
- **Read the specification, the handoff, then the full diff.**
- **Write your report to `.reviews/<lot>-<mandate>.md`**, the lot the brief names and `holistic` as the mandate.
- **Report findings there as `SEVERITY | file:line | issue | suggested fix`**, most severe first, SEVERITY one of
  `CRITICAL`, `MAJOR`, `MINOR`. Say plainly if you find nothing.
- **Return these in your message and nothing else**: the report's path, the counts by severity, the three
  findings you would fix first, and the marker line.
- **End every message you return with the line `END OF MESSAGE`**, whether or not it carries a report.
- **Do not edit anything.** Stay inside the repository.

**Detail.** The base is a tag and not a message, and a local `main` is whatever the shared working tree last left
behind. The name is there so a report that does not arrive can be asked for again, which is the only second message you
will ever get. Your findings become the lot's closing block, with its own pull request, and that is their one
destination: every finding is against merged code by construction, so none of them is counted separately and none of
them gates a merge that has already happened. Each pull request was read alone, by the human; your value is what that
reading cannot see, which is what the blocks do to each other and what the lot does to the project as a whole. The
report goes to a file because the message channel truncates
(`docs/adr/0032-a-number-carries-its-source-and-a-report-carries-its-file.md`, decisions 1 and 2), and `.reviews/` is
in `.gitignore`, so writing there stays inside the repository and no closing block's `git add` can carry a review onto
`main`. The marker line is on every message and not only on one carrying a report, because the failure the file leaves
is the short return message being cut, and that message is not a report.

## What you look for

1. **Cross-cutting invariants.** Taken together, do the changes break an invariant no single block owns? Layering and
   dependency direction, purity of the domain, package boundaries, atomicity across steps, ordering assumptions,
   resource lifetimes.
2. **Contract uniformity.** Are public surfaces consistent across the whole change, not only correct one by one? Status
   codes, error payload shape, configuration key naming, log fields, timestamp formats.
3. **Spec conformance.** Implemented exactly: nothing invented, nothing silently dropped. List what the specification
   asked for that you cannot find in the diff, and every `(Corrected: ...)` it gained, with whether the code matches the
   correction.
4. **Emergent failure modes.** Off-by-one, partial-batch and rollback behaviour, races, retries that are not
   idempotent, cross-filesystem moves assumed atomic, unbounded growth, error paths that swallow the cause.
5. **Test suite as a whole.** Do the tests, collectively, still discriminate? Shared fixtures that weaken assertions,
   mocks that assert their own configuration, coverage achieved by tests that would pass against a broken
   implementation.
6. **Covered but unrequested.** For each new conditional, option, parameter, fallback and error path, name the line of
   the specification that demanded it; code whose only justification is the test covering it is an unrequested feature,
   and you say what to delete. A branch outside a coverage perimeter may have no test at all, which is its own finding.
7. **Living documentation.** Are the living documents updated in the same commits as the behaviour they describe? Does
   any of them now describe behaviour the lot changed?
8. **Reference integrity.** Do cross-file references, relative links and heading anchors still resolve?
9. **Diff hygiene.** Files that should not have changed, refactors nobody requested, generated or build artefacts,
   leftover scaffolding, formatting churn unrelated to the work.
10. **Test evidence, across the lot.** `agents/engineering.md` retired the red run on 2026-09-19, so behaviour that
    arrived with no failing run behind it is not a finding. What is: behaviour no test exercises, a structural
    assertion nothing was shown to break, and a threshold met by a test that would pass against a broken
    implementation. **Never move the shared working tree**: read a file with `git show <commit>:<path>`, and where a
    run needs a tree, make one with `git worktree add` and remove it.
11. **Self-sufficient comments.** A comment states the why where it stands. One that defers to an identifier the reader
    must open elsewhere (a decision id, a section number, a ticket) explains nothing without that document. External
    references and clickable links are acceptable when they carry enough context.

**Detail.** One endpoint left in the framework's default error format breaks what the others uphold, which is why
contract uniformity is read across the whole change. The gate enforces branch coverage inside its perimeter only, and
each ecosystem draws its own: on the API side `api-application` and the Ebean model packages are outside it
(`agents/engineering.md`), and on the clients' side the bound covers `clients/apps/webapp/src/lib/**` alone, the whole
view being outside it (`clients/AGENTS.md`). Coverage anyway proves nothing about whether anyone asked for the branch.
Renumbering a section silently breaks anchors pointing into it. The working tree is on `main` when you start and the
closing block branches from it right after you, so a detached HEAD you leave behind is the next block's problem.
