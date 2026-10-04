import { describe, expect, it } from "vitest";
import {
	acceptOf,
	byteRefusal,
	isStorableFile,
	uploadRefusal,
} from "./uploads";

const LIMITS = {
	maxImageBytes: 1000,
	maxVideoBytes: 2000,
	maxVideoSeconds: 120,
	maxPixelsPerFrame: 10_000,
	mediaTypes: [
		"image/png",
		"image/jpeg",
		"image/webp",
		"image/gif",
		"video/mp4",
	],
	maxImportChunkBytes: 100,
	maxImportArchiveBytes: 10_000,
};

const PNG = "image/png";
const MP4 = "video/mp4";

describe("the upload a deployment refuses", () => {
	it("Given a file inside both limits, Then nothing is refused", () => {
		expect(
			uploadRefusal({ size: 1000, type: PNG, pixels: 10_000 }, LIMITS),
		).toBeNull();
	});

	it("Given more bytes than the deployment stores, Then the file is refused for its weight", () => {
		expect(uploadRefusal({ size: 1001, type: PNG, pixels: 1 }, LIMITS)).toBe(
			"TOO_MANY_BYTES",
		);
	});

	it("Given more pixels than the deployment decodes, Then the file is refused for its size", () => {
		expect(uploadRefusal({ size: 1, type: PNG, pixels: 10_001 }, LIMITS)).toBe(
			"TOO_MANY_PIXELS",
		);
	});

	it("Given a video, Then its bytes alone are judged, the server decoding it", () => {
		expect(
			uploadRefusal({ size: 2000, type: MP4, pixels: null }, LIMITS),
		).toBeNull();
		expect(uploadRefusal({ size: 2001, type: MP4, pixels: null }, LIMITS)).toBe(
			"TOO_MANY_BYTES",
		);
	});

	it("Given a handshake that has not answered yet, Then the server is what refuses", () => {
		expect(
			uploadRefusal(
				{ size: 10_000, type: PNG, pixels: 100_000_000 },
				undefined,
			),
		).toBeNull();
		expect(byteRefusal({ size: 10_000, type: PNG }, undefined)).toBeNull();
	});

	it("Given only the file's size, Then its weight is judged before any decode", () => {
		expect(byteRefusal({ size: 1001, type: PNG }, LIMITS)).toBe(
			"TOO_MANY_BYTES",
		);
		expect(byteRefusal({ size: 1000, type: PNG }, LIMITS)).toBeNull();
	});

	it("Given a video, Then its weight is read against the video bound", () => {
		expect(byteRefusal({ size: 2000, type: MP4 }, LIMITS)).toBeNull();
		expect(byteRefusal({ size: 2001, type: MP4 }, LIMITS)).toBe(
			"TOO_MANY_BYTES",
		);
	});

	it("Given a file the browser names no type for, Then the larger bound is the one it can pass", () => {
		expect(byteRefusal({ size: 2000, type: "" }, LIMITS)).toBeNull();
		expect(byteRefusal({ size: 2001, type: "" }, LIMITS)).toBe(
			"TOO_MANY_BYTES",
		);
	});
});

describe("the file a pin's media may come from", () => {
	it("Given a media type the handshake publishes, Then the deployment stores the file", () => {
		expect(isStorableFile({ type: PNG }, LIMITS)).toBe(true);
		expect(isStorableFile({ type: MP4 }, LIMITS)).toBe(true);
	});

	it("Given a picture in a format the storage refuses, Then it is refused here", () => {
		expect(isStorableFile({ type: "image/svg+xml" }, LIMITS)).toBe(false);
		expect(isStorableFile({ type: "image/tiff" }, LIMITS)).toBe(false);
	});

	it("Given anything else, Then it is not, whatever a drop or a picker handed over", () => {
		expect(isStorableFile({ type: "application/pdf" }, LIMITS)).toBe(false);
		expect(isStorableFile({ type: "video/mp2t" }, LIMITS)).toBe(false);
	});

	it("Given a file the browser names no type for, Then it is sent and the server judges it", () => {
		expect(isStorableFile({ type: "" }, LIMITS)).toBe(true);
		expect(isStorableFile({ type: "" }, undefined)).toBe(true);
	});

	it("Given a handshake that has not answered yet, Then only a picture or a video passes", () => {
		expect(isStorableFile({ type: "image/svg+xml" }, undefined)).toBe(true);
		expect(isStorableFile({ type: "video/mp2t" }, undefined)).toBe(true);
		expect(isStorableFile({ type: "application/pdf" }, undefined)).toBe(false);
	});
});

describe("what a file picker offers", () => {
	it("Given the handshake, Then it offers the media types it publishes", () => {
		expect(acceptOf(LIMITS)).toBe(
			"image/png,image/jpeg,image/webp,image/gif,video/mp4",
		);
	});

	it("Given a handshake that has not answered yet, Then it offers any picture or video", () => {
		expect(acceptOf(undefined)).toBe("image/*,video/*");
	});
});
