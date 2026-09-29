# 0027. The web application's stack, and a coverage bound that covers logic

Status: Partially superseded by `0046-knip-and-biome-check-the-clients.md` (decision 4's tool: Biome's
`noImportCycles` and `noRestrictedImports` hold the boundary)
Date: 2026-09-10
Specification: `docs/specs/2026-09-10-web-application.md`, questions K to T and sections 4.5 and 4.6
Related: `docs/adr/0024-three-projects-share-one-repository.md`, whose decision 1 gives `clients/` its own
root, decision 4 leaves the generated client uncommitted, and decision 11 this one revises;
`docs/adr/0025-the-pipeline-is-written-in-typescript.md`, which already put TypeScript in the repository;
`docs/adr/0018-a-block-is-a-pull-request.md`, the budget block 1 is measured against.

## Context

**ADR 0024 named the root and left the contents open.** `clients/` holds the JavaScript workspace, the web
application ships as a static bundle, the generated client is produced at install time. Which package
manager declares the workspace, which router, which UI primitives, which internationalisation library and
what the gate refuses were all left to the lot that builds the first client. This is that lot.

**The one hard requirement comes from the grid.** A pin board is a masonry wall of thousands of tiles, and
the failure it must not have is reflow: tiles moving under the reader as images arrive. That needs
virtualisation and a layout fed by dimensions known before any byte of image arrives, and it needs
selection and drag and drop later, on a control that stays operable from a keyboard.

**The second comes from the gate.** ADR 0024 decision 11 said the coverage bound covers "the code the
architecture separates from the view", by analogy with `api-application` sitting outside the API's bound.
The analogy assumed a layered client with a domain to separate. There is no domain here: a client of a
contract holds view, wiring, and a thin band of pure derivations. So the sentence had no referent, and
leaving it unresolved would have produced either a bound over rendering, which measures jsdom, or no bound
at all.

**Three libraries from the operator's opening list were measured and dropped**, each on a fact read on
2026-09-10 rather than remembered: `react-photo-album` 3.6.1 does not virtualise, its `InfiniteScroll`
keeping every node mounted and lowering the fidelity of offscreen photos; `masonic` 4.1.0 virtualises and
does masonry but has published nothing since 2025-04-22; TanStack Virtual does not do masonry on its own,
so pairing it with a photo album would have meant writing the placement function by hand.

### What was rejected

- **A hand written HTTP client.** The contract is generated and committed already, so a typed client is a
  build step rather than a file somebody maintains (ADR 0024 decision 4).
- **A runtime internationalisation library** (`i18next` and its family). A catalogue read at runtime is a
  bundle of strings and a lookup that fails at runtime; a compiler emits typed functions, which is the
  mechanism the contract already uses one level up.
- **npm and yarn.** Neither refuses an undeclared import. pnpm's isolated tree does, which is the property
  that matters when a workspace grows a second application and shared packages between them.
- **A browser in the gate** (Playwright, `@vitest/browser-*`). It answers one question, whether the grid
  settles without a visible jump, and charges a permanent cost on every run for it. That question is
  judged in use instead.

## Decision

1. **pnpm declares the workspace**, at `clients/`, with `apps/*` and `packages/*`. Its isolated tree is the
   reason: an import nobody declared does not resolve, which is the only mechanical guard a JavaScript
   monorepo has against a package quietly depending on its sibling's dependency.

2. **The stack is Vite, React, TanStack Router, TanStack Query, React Aria Components, Tailwind CSS and
   Paraglide JS.** React Aria Components is the load bearing one: it is the only maintained primitive set
   that carries accessible drag and drop, grid selection and virtualisation together, so the grid, the
   popovers and the dialogs come from one vocabulary rather than three. TanStack Router is chosen for typed
   and validated search parameters, which is where a pin board keeps its filters.

3. **The coverage bound covers pure functions, at 100% of lines and branches, and nothing else.** It is the
   bound `api/build.gradle.kts` already applies per package on the API side, and the reason it can be 100
   is the same: on a pure function an uncovered line is a missing test or dead code, never an unreachable
   defensive branch, so any figure below 100 would be arbitrary. Concretely the bound covers
   `apps/webapp/src/lib/**`, which is where formatting, display derivations and the search parameter codecs
   live; the view is outside it. **This revises ADR 0024 decision 11**, whose first half assumed a layer
   that does not exist here.

4. **The boundary half of decision 11 stands, and dependency-cruiser holds it.** Three rules, each
   forbidding something real: no cycles; the view does not import the HTTP client, only `packages/auth` and
   `packages/api-client` do; and `packages/api-client` does not import `packages/auth`. The third has a
   direction and the direction has a reason: `api-client` holds a file a command rewrites at every install,
   and code written by hand should not sit downstream of a generator's output. So `api-client` exports the
   types and a `createClient` factory, knowing nothing about sessions, and `auth` builds the authenticated
   client on top.

5. **A named journey is what the gate really refuses to lose.** A percentage falling is a signal; a journey
   disappearing is a regression nobody sees. `src/lib/journeys.ts` names the journeys, and a test compares
   that list against the files under `src/journeys/` in both directions, so neither a deleted test nor a
   name quietly dropped from the list passes. Each block adds its own and none is removed.

6. **Catalogue parity is a test, not a compiler flag.** Paraglide has no strict mode: a message missing
   from a locale falls back to the base locale silently and every other gate step stays green. One Vitest
   case compares the key sets of `messages/en.json` and `messages/fr.json`. English and French from the
   first lot, so that the second locale is a habit rather than a migration.

## Consequences

- **Continuous integration needs no change, and that is the whole point of ADR 0024 decision 5.**
  `dagger call clients-gate` is called by `gate`, and `validate.yml` already calls `gate` and nothing else.
  A second ecosystem entered the pipeline without a line of workflow.
- **`pre-push` and every pull request now pay a JavaScript install.** Measured on a workstation on
  2026-09-10: 15.2 seconds for the whole function with a warm pnpm store, and 3.6 seconds for the install
  alone against an empty one. The store is a cache volume, so a runner pays the cold figure every time and
  a workstation pays it once.
- **The coverage bound protects a small surface, deliberately.** What the view does is checked by the
  journeys, not by a percentage. A block that puts logic in a component instead of in `lib/` moves it out
  of the bound without anything going red, and the review is what catches that.
- **The bundle carries no server and the deployment needs a reverse proxy.** The application and the API
  are one public origin, which is what `docs/adr/0026-one-session-two-transports.md` needs; nothing in this
  repository configures that proxy yet, and development reproduces it with Vite's own.
- **The lockfile is generated and excluded from the block budget**, marked `linguist-generated` in
  `clients/.gitattributes`, as `.dagger/.gitattributes` already does for `/sdk/**`. The budget measures
  what a human rereads.
- **Dependabot sees none of `clients/` yet.** `.github/dependabot.yml` declares `gradle` and `docker` at
  `/api` and `github-actions` at `/`. A `npm` ecosystem entry at `/clients` is the obvious follow on and no
  block here writes it.
- **Routing is code-based to start with.** TanStack Router's file-based mode writes a route tree into
  `src/`, which then sits inside the coverage, lint and boundary perimeters this ADR sets up, and one route
  does not pay for it. The block that adds enough routes to want the generator moves it, and nothing above
  changes when it does.
