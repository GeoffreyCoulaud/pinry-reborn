/** Two durations closer than this play together, with no offset to set (decision D). */
const NO_SLIDER_UNDER_MS = 300;

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
