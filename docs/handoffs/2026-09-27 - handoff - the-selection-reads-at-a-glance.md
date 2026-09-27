# Handoff: the selection reads at a glance

Date: 2026-09-27
Tier: Direct, one block, `fix/selection-and-drop-feedback`. No specification, no review.

## Current state

- **The boards and account screens take the whole width**, as the grids already did. The credentials screen keeps
  its narrow centred form.
- **The selection tick is `size-6`, bordered dark inside a thin white ring**, on the grid and in the bin's rows
  alike. A shadow was too faint on a light photograph, and a dark border alone vanished on a dark one in the dark
  theme, both seen by the operator.
- **A selected tile is ringed and tinted in the accent colour**, off react-aria's `data-selected`.
- **A drop whose images take over 300 ms to read raises a loading toast** ("Reading dropped images: N") until
  `judgeDrop` has judged them, on the grid's drop and in the dialogs' drop box alike.
- **A file heavier than `maxFileBytes` is refused before it is decoded** (`byteRefusal`), where it used to be decoded
  and then refused. Found while reading the drop; the operator chose to fix it in this block.

## Evidence

- `dagger call gate` green at `68dfb311`, then at `2b758ac6` with the weight read first. Budget 72 lines, 11 files
  against `main`.
- The heavy-file journey case asserts `createImageBitmap` is never called, and failed before `drops.ts` read the
  size first.
- The new journey case, "Given images still being read, Then the screen says so until the form opens", failed
  before `drops.ts` changed.
- Read headless in Firefox 156.0.1 against a stubbed API and the built bundle, at 1280 and 380 px: both screens full
  width, two tiles selected ringed and tinted, and twelve 6000×4000 PNGs dropped on the window showing the toast at
  800 ms and gone at 3 s.

## Pitfalls

- **HeroUI's toast is `role="alert"`**, so an immediate loading toast shadowed the refusals journeys read by that
  role; the delay is what keeps a fast drop silent.
- **`toast.promise` always ends on a success toast**, which a drop does not want; the toast is closed by hand.
- **Headless Firefox reports `hover: none`**, so every tick shows in a headless reading whether or not anything is
  selected.

## What is not validated

- The toast and the tint in the dark theme; the tick was read headless in it.

## Next step

Lot 2 of the operator's request of 2026-09-27: adding the selection to a new board (its design question is open:
two client requests, or one endpoint creating the board with its pins), then the pin viewer's redesign after a
throwaway design POC.
