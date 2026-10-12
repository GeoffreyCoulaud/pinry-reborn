# Handoff: the client's projects reference each other

Date: 2026-10-13
Blocks: one, `clients/the-projects-reference-each-other`
Tier: Direct. No spec, no review; the operator chose the shape and the tier in Discuss.

## Current state

Finding the references of a package's export now reaches the packages that import it, in Serena as in any
editor driven by tsserver: `createApiClient` is found in `auth`, and `Schemas` in fifteen files of the web
application, where each search used to stop at the package's own boundary.

- `auth` and `api-client` are `composite`, and emit their declarations into their `dist/`, ignored by git
  and left behind by the pipeline.
- `clients/tsconfig.json` lists the three projects; `auth` and the web application reference what they import.
- `pnpm run typecheck` is one `tsc --build` from `clients/`. The packages lost their own `typecheck` script.

## Why it takes project references

tsserver searches the projects it has loaded, and it loads the others only through a solution
`tsconfig.json` and the references of a `composite` project (`forEachAncestorProjectLoad` in
`typescript.js`). Each consumer has to reference what it imports: a solution alone found nothing. A
referenced project may not disable emit (TS6310), hence the declarations and `tsc --build`.

## Pitfalls

- **A new package that `clients/tsconfig.json` does not list is typechecked by nothing**, silently: the old
  recursive run found every package with a script, `tsc --build` finds only the projects listed.
- **The web application cannot be `composite`**: `renderApp` in `src/test/app.tsx` infers a type it cannot
  name (TS2883). Nothing references it, so nothing needs it to be.
- **A stale `src/schema.d.ts` in `api-client` fails the typecheck with errors that look like this change's**:
  two TS2322 on `mediaType` in the web application. It is generated and ignored; `pnpm --filter
  @pinry-reborn/api-client run generate` refreshes it.
- **Serena's own configuration is local** (`.serena/` is ignored): `ls_workspace_folders` lists `api` and
  `clients`, the Kotlin server finding no Gradle build at the repository's root.

## What is not validated

No holistic review and no specification review: tier Direct runs neither. `.dagger/` is TypeScript too and
stays outside Serena, having no `node_modules` of its own to resolve against.

## The lot's counts

- Fix-backs: 0. Fixes on top: 0. Cascaded rebases: 0. One run re-triggered, by this section pushed after the
  first.
- The operator's reading of the body: no remark; the operator enabled auto-merge.

## Next step

The tag `lot/0.57.0-the-projects-reference-each-other` once auto-merge lands #437. Then ADR 0055's third-party
importer.
