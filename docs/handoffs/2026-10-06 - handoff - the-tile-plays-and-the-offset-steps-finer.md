# Handoff: a video's tile plays, and the offset steps finer

Date: 2026-10-06
Branch: `fix/the-tile-plays-and-the-offset-steps-finer`, one block, lot `0.50.0`
Tier: Direct, written inline by the lead. No specification, and the holistic review did not run, Direct skipping it.

## Current state

`dagger call gate` green at `c1938bf7`; budget 238 lines, 6 files against `main`, the renamed journey counting whole.
Both P1 items from the operator's testing of lot `0.49.0` are closed and deleted from the backlog.

- **A video's tile shows its animated rendition at once**, as an animated image plays, with no hover. `PinGrid`'s
  hover state is gone; the journey `hover a video tile` became `a video tile plays its animation`.
- **The comparator's offset steps by 10 ms**, its label in hundredths. The browser exposes no frame rate, so the step
  is finer than any frame rather than one frame: at worst 5 ms from the alignment, against 17 ms per frame at 60 fps.

## Tier-2 questions

- A, the tier: Direct.
- B, a step of one measured frame with `requestVideoFrameCallback`, or a fixed step: the question became C.
- C, 10 ms alone, 10 ms with a tighter playback resync, or 1 ms: "a", 10 ms alone.

## Pitfalls

- **Playback tolerates 150 ms of drift** (`StageMedia`'s `DRIFT_MS`) before seeking a video back onto the clock, so
  two playing videos can sit several frames apart whatever the offset. Alignment to the frame is read paused, where
  the resync is at 1 ms. Left as it is (question C).
- **A video whose animated rendition the server cannot draw now says "Preview unavailable" in the grid**, where it
  said so only under the pointer.
- **Renaming a journey's file needs `REQUIRED_JOURNEYS` in `src/lib/journeys.ts`**, which the gate's
  `journeys.test.ts` compares to the directory.

## Evidence

Read headless in Firefox 157 over WebDriver BiDi against a Node stub API, at 1280x800 light and 390x844 dark: each
video tile's `src` ends in `&animated=true` and its GIF is past its first frame, the image's has no `animated`; 37
presses of the right arrow on the offset read `370` and "0.37s", which fits beside the bar. The stub and the driver
are in the session's scratchpad, `read/`.

## What is not validated

- A phone, and Safari and Chrome. A grid of many video tiles all animating at once: never measured.
- No holistic review, Direct skipping it.

## Next step

Nothing this block opens.
