# Review mandate: holistic

**Artefact: a whole lot on `main`**, `git diff <previous lot tag>..origin/main`, every code block merged and nothing
in flight. The base is a tag and not a message: `git tag -l 'lot/*'` lists the annotated lot tags, the newest is the
lot before this one, and you determine your own range from it rather than being told it. `origin/main` and not
`main`, a local ref being whatever the shared working tree last left behind. Run once per lot in tier Spec, at the
head of Wrap, in an agent the lead dispatches by name;
the name is there so a report that does not arrive can be asked for again, which is the only second
message you will ever get. **Your findings become the lot's closing block**, with its own pull
request, and that is their one destination: every finding is against merged code by construction, so
none of them is counted separately and none of them gates a merge that has already happened.

Each pull request was read alone, by the human. Your value is what that reading cannot see: what
the blocks do to each other, and what the lot does to the project as a whole. Read the
specification, the handoff, then the full diff, `git diff <previous lot tag>..origin/main`.

Report findings as `SEVERITY | file:line | issue | suggested fix`, most severe first, SEVERITY one of
`CRITICAL`, `MAJOR`, `MINOR`. **Do not edit anything.** Say plainly if you find nothing. Stay inside
the repository.

1. **Cross-cutting invariants.** Taken together, do the changes break an invariant no single block
   owns? Layering and dependency direction, purity of the domain, package boundaries, atomicity
   across steps, ordering assumptions, resource lifetimes.
2. **Contract uniformity.** Are public surfaces consistent across the whole change, not only correct
   one by one? Status codes, error payload shape, configuration key naming, log fields, timestamp
   formats. One endpoint left in the framework's default error format breaks what the others uphold.
3. **Spec conformance.** Implemented exactly: nothing invented, nothing silently dropped. List what
   the specification asked for that you cannot find in the diff, and every `(Corrected: ...)` it
   gained, with whether the code matches the correction.
4. **Emergent failure modes.** Off-by-one, partial-batch and rollback behaviour, races, retries that
   are not idempotent, cross-filesystem moves assumed atomic, unbounded growth, error paths that
   swallow the cause.
5. **Test suite as a whole.** Do the tests, collectively, still discriminate? Shared fixtures that
   weaken assertions, mocks that assert their own configuration, coverage achieved by tests that
   would pass against a broken implementation.
6. **Covered but unrequested.** The gate enforces branch coverage inside its perimeter only, and each
   ecosystem draws its own: on the API side `api-application` and the Ebean model packages are outside it
   (`agents/engineering.md`), and on the clients' side the bound covers `clients/apps/webapp/src/lib/**`
   alone, the whole view being outside it (`clients/AGENTS.md`). A branch outside a perimeter may have no
   test at all, which is its own finding. Coverage anyway proves nothing
   about whether anyone asked for the branch. For each new conditional, option, parameter, fallback
   and error path, name the line of the specification that demanded it; code whose only
   justification is the test covering it is an unrequested feature, and you say what to delete.
7. **Living documentation.** Are the living documents updated in the same commits as the behaviour
   they describe? Does any of them now describe behaviour the lot changed?
8. **Reference integrity.** Do cross-file references, relative links and heading anchors still
   resolve? Renumbering a section silently breaks anchors pointing into it.
9. **Diff hygiene.** Files that should not have changed, refactors nobody requested, generated or
   build artefacts, leftover scaffolding, formatting churn unrelated to the work.
10. **Red before green, across the lot.** Run `git log --oneline` over the range. Behaviour that
    arrived with no failing run behind it, in a block or in a fix applied between blocks, is a
    finding against the process. Where a test commit's body carries no failing output, run the test
    at that commit yourself before reporting. **Never move the shared working tree**: read a file with
    `git show <commit>:<path>`, and where a run needs a tree, make one with `git worktree add` and
    remove it. The tree is on `main` when you start and the closing block branches from it right
    after you, so a detached HEAD you leave behind is the next block's problem. Never infer
    compliance from the absence of evidence.
11. **Self-sufficient comments.** A comment states the why where it stands. One that defers to an
    identifier the reader must open elsewhere (a decision id, a section number, a ticket) explains
    nothing without that document. External references and clickable links are acceptable when they
    carry enough context.
