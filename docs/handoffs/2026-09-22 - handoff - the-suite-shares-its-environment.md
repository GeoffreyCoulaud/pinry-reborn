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

## The runner, which is where it was worth it

Read with `gh run view <id> --log` on 2026-09-22. The suite's own reported duration, and the wall
clock of the gate step that carries it:

| Run | Suite | Step |
|---|---|---|
| `35656525990`, `main`, before | 96.90 s | 2 m 15 s |
| `35704751098`, `main`, before | 88.61 s | 1 m 55 s |
| `35714806870`, this branch | 24.02 s | 53.5 s |

**The runner gained more than the workstation**, 73% against 42%, which is the shape of the defect
rather than a surprise: the per-file environment was 37 spawns of about 869 ms, and a runner has
too few cores to hide them behind each other. This is also where the 2 m 18 s of the backlog item
went.

## What is not validated

No holistic review and no specification review: tier Direct runs neither.

## Next step

Nothing this leaves open. `maxWorkers` deserves a second look only if the client gate becomes the
run's critical path again, and it would then be measured on the runner rather than here.
