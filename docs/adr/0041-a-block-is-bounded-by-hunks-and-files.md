# 0041. A block is bounded by hunks and files

Status: Accepted
Date: 2026-09-23
Supersedes: `docs/adr/0018-a-block-is-a-pull-request.md`, decision 1's bounds, and
`docs/adr/0028-the-budget-follows-the-ecosystem.md`, decision 1.

## Context

A block is bounded today by under 600 diff lines, plus production bounds per ecosystem (under 200
under `api/`, under 400 under `clients/`). The count adds insertions and deletions, so a line edited
in place costs two, and nothing bounds how many files a reviewer has to open.

Czerwonka, Greiler and Tilford, *Code Reviews Do Not Find Bugs* (Microsoft, ICSE 2015, page 2):
"The decrease however only starts to be noticeable for reviews with 20 or more changed files."
Stepanović, *From Async Code Reviews to Co-Creation Patterns* (InfoQ, 2022-11-08), shows review
engagement per 100 lines falling as a pull request grows, with no threshold. The line bound is the
operator's preference, informed by Adrienne Braganza's *Looks Good to Me* (Manning).

## Decision

1. **A block changes under 500 lines and under 20 files**, both strict, the same for the whole
   repository. The production bounds per ecosystem are gone.
2. **Lines are counted per hunk of `git diff -U0`**: each hunk costs the larger of its deleted and
   added counts, and the block costs the sum. A line edited in place costs one; an addition and a
   deletion in separate places cost both.
3. **The exclusions do not move**: the dated documents and the `linguist-generated` files count
   toward neither bound. A binary file counts one file and no line.

**Fails if** a lot's split blocks each need the other to be reviewed: a pull request whose review
comments ask for a sibling's diff.

## Consequences

- `agents/workflow.md` carries the measuring command, so two readers reach the same number.
- Blocks that were legal can now be refused, and the reverse: a block of in-place edits costs about
  half what it did. Pull request #194, 572 lines over 14 files, would now split.
