/** Two durations closer than this play together (specification 2026-10-05-the-duplicates-are-compared, decision D). */
const NO_SLIDER_UNDER_MS = 300;

/** Firefox and Chromium play a frame stating no duration, or 10 ms or less, for 100 ms. */
const UNSTATED_FRAME_MS = 100;
const UNPLAYED_FRAME_MICROS = 10_000;

/** How far the shorter version can be moved, in ms: the difference of durations, or 0 for no slider. */
export function slackOf(durations: readonly number[]): number {
	const slack = Math.max(...durations) - Math.min(...durations);
	return slack > NO_SLIDER_UNDER_MS ? slack : 0;
}

/** Each version's time at the shared `time`, the shorter moved by `offset`, held on its first or last frame outside its span. */
export function localTimes(
	time: number,
	offset: number,
	durations: readonly number[],
): number[] {
	const shorter = slackOf(durations) > 0 ? Math.min(...durations) : null;
	return durations.map((duration) =>
		Math.min(duration, Math.max(0, time - (duration === shorter ? offset : 0))),
	);
}

/** The shared time `elapsed` later, both versions back to 0 at the longer's end. */
export function advance(time: number, elapsed: number, length: number): number {
	const next = time + elapsed;
	return next >= length ? 0 : next;
}

/** An animated image's frame durations in ms, from the microseconds its decoder states. */
export function frameMillis(micros: readonly (number | null)[]): number[] {
	return micros.map((one) =>
		one != null && one > UNPLAYED_FRAME_MICROS
			? one / 1_000
			: UNSTATED_FRAME_MS,
	);
}

/** The index of the frame shown at `time`, the last one from the end on. */
export function frameAt(frames: readonly number[], time: number): number {
	let end = 0;
	const at = frames.findIndex((frame) => {
		end += frame;
		return time < end;
	});
	return at < 0 ? frames.length - 1 : at;
}
