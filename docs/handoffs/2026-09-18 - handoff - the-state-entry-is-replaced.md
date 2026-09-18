# Handoff: the engine state entry is replaced, not doubled

Date: 2026-09-18
ADR: `docs/adr/0031-the-gate-builds-once-and-keeps-its-cache.md`, decision 4, second correction
Blocks: one, `ci/the-state-entry-is-replaced-not-doubled`
Tier: Direct. No spec, no review; the operator chose the shape and the tier in Discuss.

## Current state

`verify` no longer saves a cache entry. It archives the stopped engine's state as before and hands
the archive to `engine-state` as a run artefact, the way it already hands `publish` the fast jar.
`engine-state` is the job `prune` became: it downloads the archive, deletes every `dagger-state-`
entry, and only then saves the new one. The quota peak is one entry instead of two.

Closes the `P2` backlog item "The engine state archive outgrew the Actions quota's margin", deleted
in this pull request.

## Why not the bound the backlog proposed

The item's proposed fix was a lower `gc.maxUsedSpace` in `.github/engine.json`, found by trial at a
full run on `main` each. Two things sent it elsewhere. The numbers of 2026-09-18, read with
`gh cache list` on `GeoffreyCoulaud/pinry-reborn`: the state entry is 4 428 109 864 bytes and the
release path's buildx blobs total 1 867 610 681, so two entries plus the blobs is 10.73 GB of ten,
and the blobs are down from the 5.46 GB ADR 0031's own correction recorded. The overflow is real and
it has already been eating the release path's cache. And the bound is not understood: 9 GB is
declared where run `35381866356` reports `13G /mnt/dagger/state`, so no value could have been chosen
rather than guessed.

The order of the two operations was the defect. With it fixed, the size of the archive bounds
nothing, and `gc.maxUsedSpace` stays where it is.

## Pitfalls

- **The fix is observable on `main` alone.** `engine-state` runs on no pull request, so the pull
  request proves only that nothing broke. What proves the fix is `gh cache list` after the merge
  run: one `dagger-state-` entry, and the buildx blobs still at 1.87 GB rather than lower.
- **Nothing is deleted before the artefact has landed.** The delete and the save are both gated on
  the download's `outcome`, so a lost artefact costs one cold run instead of leaving the prefix
  empty.
- **The archive crosses as an artefact, 4.4 GB up and 4.4 GB down**, which is a few minutes added to
  a push to `main` and to nothing else. `compression-level: 0`: the file is already zstd.
- **The absolute path is load bearing.** `engine-state` lays the archive down at
  `/mnt/dagger/state.tar.zst`, the path `verify` restores from, so the entry's layout is what it was.

## What is not validated

No holistic review and no specification review: tier Direct runs neither.

## Next step

Read `gh cache list` after this merges. If the buildx blobs stop falling, the item is closed for
good; if they keep falling, something else is over the quota and the next reading says what.
