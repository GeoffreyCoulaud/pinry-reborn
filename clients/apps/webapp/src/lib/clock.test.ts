import { describe, expect, it } from "vitest";
import { advance, frameAt, frameMillis, localTimes, slackOf } from "./clock";

describe("the shared clock", () => {
	it("Given the shared time before the shorter version's span, Then it holds its first frame", () => {
		expect(localTimes(500, 1_000, [4_000, 2_000])).toEqual([500, 0]);
	});

	it("Given the shared time past the shorter version's span, Then it holds its last frame", () => {
		expect(localTimes(3_500, 1_000, [4_000, 2_000])).toEqual([3_500, 2_000]);
	});

	it("Given the shared time inside the shorter version's span, Then it plays from the offset on", () => {
		expect(localTimes(1_500, 1_000, [2_000, 4_000])).toEqual([500, 1_500]);
	});

	it("Given two durations within 0.3 s, Then the offset moves neither", () => {
		expect(localTimes(1_000, 200, [2_000, 2_200])).toEqual([1_000, 1_000]);
	});

	it("Given one version alone, Then it is held on its last frame past its end", () => {
		expect(localTimes(5_000, 0, [4_000])).toEqual([4_000]);
	});

	it("Given the shared time reaching the longer's end, Then both start again at 0", () => {
		expect(advance(3_990, 16, 4_000)).toBe(0);
		expect(
			localTimes(advance(3_990, 16, 4_000), 1_000, [4_000, 2_000]),
		).toEqual([0, 0]);
	});

	it("Given the shared time short of the longer's end, Then it moves on by what elapsed", () => {
		expect(advance(1_000, 16, 4_000)).toBe(1_016);
	});

	it("Given two durations 0.2 s apart, Then there is no slider", () => {
		expect(slackOf([2_000, 2_200])).toBe(0);
	});

	it("Given two durations 0.4 s apart, Then the slider reaches their difference", () => {
		expect(slackOf([2_400, 2_000])).toBe(400);
	});

	it("Given one version alone, Then there is no slider", () => {
		expect(slackOf([4_000])).toBe(0);
	});

	it("Given a frame stating no duration and one stating zero, Then each counts 100 ms in the sum", () => {
		expect(frameMillis([50_000, null, 0])).toEqual([50, 100, 100]);
	});

	it("Given frames stating 10 ms and 20 ms, Then the first counts 100 ms, as the browsers play it, and the second 20 ms", () => {
		expect(frameMillis([10_000, 20_000])).toEqual([100, 20]);
	});

	it("Given frames of 100, 200 and 100 ms, Then a time names the frame shown then, the last one past the end", () => {
		const frames = [100, 200, 100];

		expect(frameAt(frames, 0)).toBe(0);
		expect(frameAt(frames, 150)).toBe(1);
		expect(frameAt(frames, 300)).toBe(2);
		expect(frameAt(frames, 400)).toBe(2);
	});
});
