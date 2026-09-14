# Handoff: a page size is clamped, and a rule says so

Date: 2026-09-14
Branch: `fix/page-size-clamped`, one block
Tier: Direct, written inline by the lead. No specification, and **the holistic review did not run**,
which Direct skips by the tier table rather than by a waiver the operator gave.

## Current state

`dagger call gate` green before the push. The diff is 316 counted lines against 600, 79 of them
production under `api/` against 200; this handoff sits outside the count as a dated document.

`/api/v1/me/exports`, `/api/v1/me/imports` and `/api/v1/me/imports/{id}/issues` now clamp `pageSize`
into `1..PinGetter.MAX_PAGE_SIZE`, as the four other paged endpoints already did. The backlog entry
that named them is deleted.

## What was built

- **`PageSizeForwardedUnclamped`**, a detekt rule over the use cases: in a function declaring a
  `pageSize` parameter, every mention of it has to be what `coerceIn` is called on. Registered in
  `PinryRuleSetProvider`, activated in `config/detekt/detekt.yml` with `includes: ['**/api-usecases/**']`.
- **The three clamps**, in `UserDataExportGetter.list`, `UserDataImportGetter.list` and
  `UserDataImportIssueLister.list`, each with a use-case test asserting the store receives `1` and
  `MAX_PAGE_SIZE` for a request of `0` and of `10_000`.

## Pitfalls

- **The rule reads names, not resolved members**, like the rest of the rule set. A page size parameter
  named otherwise goes unseen, a member named `pageSize` read on another receiver inside such a
  function is taken for the parameter, and `coerceAtLeast(1).coerceAtMost(max)` bounds the same value
  and is reported all the same.
- **Its scope is a path filter and has to stay one.** Outside the use cases the same name carries a
  value already clamped: the repositories forward it and `ModelPaginationHelper` computes on it, so a
  rule without `includes` reports every one of them.
- **A changed detekt rule is not picked up by a live Gradle daemon** (`api/AGENTS.md`). `./gradlew --stop`
  before trusting a local run, or the rule reports against a cached classpath and the red never comes.
- **Registration alone is not activation.** `PinryRuleSetProviderTest` compares the provider's rules to
  what `detekt.yml` names and to what it sets `active: true`, because a custom rule set is excluded
  from detekt's configuration validation and a forgotten key costs the rule in silence.

## What is not validated

- **No integration test asks these endpoints for `?pageSize=0`.** The clamp is held at the use case,
  by the three unit tests, as the four older ones are.
- **No holistic review**, Direct skipping it.

## Next step

Nothing this block opens. The backlog's `P2` band still holds the `raw(` audit, the table rebuild's
unexercised row-carrying path, and `foreign_keys` being off.
