# Handoff: the client suite shares one environment per worker

Date: 2026-09-22
Blocks: one, `clients/the-suite-shares-its-environment`
Tier: Direct. No spec, no review; the operator chose the shape and the tier in Discuss.

## Current state

`clients/apps/webapp/vite.config.ts` declares `test.isolate: false`. Vitest reuses a worker's
environment across the files it runs instead of building one per file. `pnpm run test` goes from
13.88 s to 8.03 s on the workstation, and from 120 s to 65 s of processor time, which is the half
that matters on a runner with fewer cores than wall clock to hide them behind. 37 files, 175 tests,
coverage still at 100% of lines and branches.

Closes the `P1` backlog item "The client suite spends more on starting than on testing", deleted in
this pull request.

## What `vitest doctor` measured

Run on 2026-09-22 with Vitest 5.0.0, on a 12-core workstation, without coverage:

```
baseline (pool: forks · isolate: true)  11.58s
pool: 'threads'                         11.71s   (±0%)
pool: 'vmThreads'                       failed
pool: 'vmForks'                         failed
isolate: false                           6.36s   (-45%)
fsModuleCache: true                     11.44s   (±0%)
maxWorkers: 5 (with isolate: false)      5.67s   (-56%)
maxWorkers: 2 (with isolate: false)      8.39s   (-34%)
```

**The pool was the wrong suspect**, which is what the backlog item asked and what this settles.
`threads` measures nothing, and both VM pools fail the same way: `TransformStream is not defined`,
raised inside `@mswjs/interceptors` when `src/test/server.ts` imports `msw/node`. A VM context has
no web globals, so no pool that builds one can carry MSW. Isolation was the only lever.

**`maxWorkers: 5` is not taken**, though doctor recommends it alongside. Its gain over a lone
`isolate: false` is 11%, and the baseline drifted 10% between two runs of doctor an hour apart, so
the gain is the size of the noise. It is also a constant calibrated on twelve cores, where the default
(`cpus - 1`) follows whatever machine runs it.

## Pitfalls

- **The suite now keeps module state between files of the same worker.** MSW starts at the top of
  `src/test/setup.ts` and `session.ts` builds its client while it is imported, which
  `clients/AGENTS.md` already records; under `isolate: false` those run once per worker rather than
  once per file. Nothing went red, but a test that leaves a handler or a mutated store behind now
  reaches its neighbours. Doctor passed the suite twice with a shuffled file order under shared
  state, which it calls likely and not guaranteed.
- **A flake that appears after this is a state leak, not a flake.** The fix is the test that leaks,
  never a return to `isolate: true`.

## What is not validated

- **The runner's own figure.** The client gate took 2 m 18 s on GitHub; nothing here measures what
  it becomes. The pull request's run is the first reading.
- No holistic review and no specification review: tier Direct runs neither.

## Next step

Read the client gate's duration on the merge run. If the drop is far short of the processor time
saved here, the runner is bound by something else and `maxWorkers` deserves a second look, this
time measured where it runs.
