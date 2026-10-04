import { afterEach, describe, expect, it, vi } from "vitest";
import { judgeDrop } from "../drops";

const LIMITS = {
	maxImageBytes: 1000,
	maxVideoBytes: 2000,
	maxVideoSeconds: 120,
	maxPixelsPerFrame: 10_000,
	mediaTypes: ["video/mp4"],
	maxImportChunkBytes: 100,
	maxImportArchiveBytes: 10_000,
};

const VIDEO = new File(["ok"], "cat.mp4", { type: "video/mp4" });

/** The video's source set as the browser would answer it, `nothing` being a header never read. */
function answering(event: "loadedmetadata" | "error" | "nothing") {
	vi.spyOn(HTMLVideoElement.prototype, "src", "set").mockImplementation(
		function (this: HTMLVideoElement) {
			if (event !== "nothing") {
				queueMicrotask(() => this.dispatchEvent(new Event(event)));
			}
		},
	);
}

/** The video judged, and whether the object URL it was read through was revoked. */
async function judged() {
	const created = vi.spyOn(URL, "createObjectURL");
	const revoked = vi.spyOn(URL, "revokeObjectURL");
	const drop = judgeDrop([VIDEO], "", LIMITS);
	await vi.advanceTimersByTimeAsync(60_000);
	const partition = await drop;
	expect(revoked).toHaveBeenCalledWith(created.mock.results[0]?.value);
	return partition;
}

afterEach(() => {
	vi.useRealTimers();
	vi.restoreAllMocks();
});

describe("the video a drop measures", () => {
	it("Given a frame past the per-frame bound, Then the video is refused for its size", async () => {
		vi.useFakeTimers();
		answering("loadedmetadata");
		vi.spyOn(HTMLVideoElement.prototype, "videoWidth", "get").mockReturnValue(
			101,
		);

		expect((await judged()).refusals).toEqual(["TOO_MANY_PIXELS"]);
	});

	it("Given a frame within the bound, Then the video is kept with its pixels", async () => {
		vi.useFakeTimers();
		answering("loadedmetadata");

		expect((await judged()).files[0]?.measurement.pixels).toBe(10_000);
	});

	it.each([
		["a header with no picture", "loadedmetadata", 0],
		["a header the browser cannot read", "error", 100],
		["a header never read", "nothing", 100],
	] as const)(
		"Given %s, Then the video is kept unmeasured for the server to judge",
		async (_, event, width) => {
			vi.useFakeTimers();
			answering(event);
			vi.spyOn(HTMLVideoElement.prototype, "videoWidth", "get").mockReturnValue(
				width,
			);

			expect((await judged()).files[0]?.measurement.pixels).toBeNull();
		},
	);
});
