import { describe, expect, it } from "vitest";
import { isPlayable, isVideo, videoFileName } from "./media";

describe("isVideo", () => {
	it("reads a video type, its codecs parameter included", () => {
		expect(isVideo('video/mp4; codecs="avc1.64001F, mp4a.40.2"')).toBe(true);
		expect(isVideo("video/webm")).toBe(true);
	});

	it("reads the type whatever its case, which RFC 2045 leaves free", () => {
		expect(isVideo("Video/MP4")).toBe(true);
	});

	it("refuses an image and an absent type", () => {
		expect(isVideo("image/webp")).toBe(false);
		expect(isVideo(null)).toBe(false);
		expect(isVideo(undefined)).toBe(false);
	});
});

describe("isPlayable", () => {
	it("tries what the browser may or probably can play", () => {
		expect(isPlayable("maybe")).toBe(true);
		expect(isPlayable("probably")).toBe(true);
	});

	it("gives up on the empty answer", () => {
		expect(isPlayable("")).toBe(false);
	});
});

describe("videoFileName", () => {
	it("names a stored MP4 or WebM with its extension, its codecs parameter and case left out", () => {
		expect(videoFileName('video/mp4; codecs="avc1.64001F, mp4a.40.2"')).toBe(
			"video.mp4",
		);
		expect(videoFileName("Video/WebM")).toBe("video.webm");
	});

	it("leaves the extension to the browser for a type the server never stores", () => {
		expect(videoFileName("video/quicktime")).toBe("video");
	});
});
