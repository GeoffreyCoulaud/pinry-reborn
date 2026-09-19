# 0036. The icons come from lucide-react

Status: Accepted
Date: 2026-09-19
Specification: `docs/specs/2026-09-19-the-header-becomes-icons.md`, decision A.
Related: `docs/adr/0035-a-styled-layer-over-react-aria-components.md`, which chose the control layer,
and `docs/adr/0027-the-web-application-stack.md`, which chose the stack under it. This document is
written because the specification review found no test separating a leaf dependency from those two,
and the repository's answer to that is an ADR rather than a sentence in a spec.

## Context

The header's four controls lose their words, so the application needs icons for the first time.

HeroUI 3.2.6 ships none for application use: its package declares no export path containing `icon`,
and `dist/index.d.ts` declares no component of that kind. What it draws inside `Select.Indicator` and
`ListBox.ItemIndicator` is internal to those components.

Surveyed 2026-09-19, at the version the registry then served:

| Candidate              | Version | `time.modified`      | Unpacked | The three theme states |
|------------------------|---------|----------------------|----------|------------------------|
| `lucide-react`         | 1.47.0  | 2026-09-17T07:29:00Z | 35.2 MB  | `Monitor`, `Sun`, `Moon` |
| `@heroicons/react`     | 2.2.0   | 2026-05-12T13:51:49Z | 3.7 MB   | no screen icon          |
| `react-icons`          | 5.7.0   | 2026-08-12T09:46:59Z | 88.3 MB  | aggregates other sets   |
| `@tabler/icons-react`  | 3.47.0  | 2026-09-18T22:25:19Z | 66.2 MB  | yes                     |

```
$ npm view lucide-react@1.47.0 time.modified dist.unpackedSize
2026-09-17T07:29:00.528Z
35233784
$ curl -sL https://registry.npmjs.org/lucide-react/-/lucide-react-1.47.0.tgz -o lucide.tgz
$ tar -xzf lucide.tgz package/dist/lucide-react.d.ts
$ for n in Monitor Sun Moon LogOut Plus Download ImageUp; do printf "%s " "$n"; command grep -c "declare const $n:" package/dist/lucide-react.d.ts; done
Monitor 1
Sun 1
Moon 1
LogOut 1
Plus 1
Download 1
ImageUp 1
$ tar -xzf lucide.tgz package/package.json -O | python3 -c "import json,sys;d=json.load(sys.stdin);print(d['peerDependencies'],d['sideEffects'])"
{'react': '^16.5.1 || ^17.0.0 || ^18.0.0 || ^19.0.0'} False
```

The unpacked sizes are what `node_modules` holds, not what ships: `sideEffects: false` over per-icon
ES modules lets Vite drop every icon the application does not import.

`@tabler/icons-react@3.47.0` was published inside the 1440-minute window
`clients/pnpm-workspace.yaml` declares, so `pnpm install --frozen-lockfile` would have refused it on
2026-09-19 with `ERR_PNPM_NO_MATURE_MATCHING_VERSION`. That is a reason to pin an aged-out version,
never to waive the delay (`clients/AGENTS.md`).

## Decision

1. **The web application's icons come from `lucide-react`.** The theme control cycles three states
   and needs three icons that read as one family; `@heroicons/react` has no screen icon, so its
   `system` state would be drawn from a different vocabulary than its two siblings.

   **Fails if** a later screen wants an icon the set does not carry and the substitute reads as
   foreign beside the ones already placed.

2. **No icon is written by hand.** Path data copied into the repository is unreadable in review, and
   it counts against the block's production bound, which is there to measure what a human rereads.

3. **`react-icons` is refused although it carries every set.** It is an aggregator: choosing it
   defers the choice this document exists to make, and a codebase free to pull from any set has no
   visual vocabulary at all.

## Consequences

- **Swapping the set is a rename of imports**, and nothing else. Nothing in the application's design
  depends on `lucide-react`: it exports pure presentational components and no context, no hook and
  no style. That is what separates it from ADR 0035's choice, which decided how every control is
  written and carries a peer relationship with `react-aria-components`.
- **`clients/AGENTS.md` names it on the stack line**, under the living regime.
- **The bundle grows by the icons imported**, not by the package. Nothing measures this yet; the
  clients' gate bounds coverage, not bundle size.
