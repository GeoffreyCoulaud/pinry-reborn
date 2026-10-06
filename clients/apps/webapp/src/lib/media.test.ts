import { describe, expect, it } from "vitest";
import {
	codecsOf,
	isPlayable,
	isVideo,
	soundLine,
	videoFileName,
	weightLine,
} from "./media";

const MP4 = 'video/mp4; codecs="avc1.640028,mp4a.40.2"';
const WORDS = {
	mono: "mono",
	stereo: "stereo",
	many: (count: number) => `${count} channels`,
};

describe("codecsOf", () => {
	it("reads the video track's codec, then the audio track's", () => {
		expect(codecsOf(MP4)).toEqual(["avc1.640028", "mp4a.40.2"]);
	});

	it("reads an unquoted parameter beside another, whatever its case", () => {
		expect(codecsOf("video/webm; CODECS=vp09.00.10.08; foo=bar")).toEqual([
			"vp09.00.10.08",
		]);
	});

	it("reads no codec from a type without the parameter, or no type", () => {
		expect(codecsOf("image/png")).toEqual([]);
		expect(codecsOf(null)).toEqual([]);
	});
});

describe("weightLine", () => {
	it("Given a video, Then its weight, format, video codec and measured rate", () => {
		const media = {
			mimeType: MP4,
			byteSize: 8_400_000,
			videoBitRate: 4_200_000,
		};

		expect(weightLine(media, "en")).toBe("8.4 MB · MP4 · H.264 · 4.2 Mb/s");
		// French spaces a unit with a narrow no-break space.
		expect(weightLine(media, "fr")).toBe("8,4 Mo · MP4 · H.264 · 4,2 Mbit/s");
	});

	it("Given an image, Then its weight in kilobytes and its format alone", () => {
		expect(
			weightLine({ mimeType: "image/jpeg", byteSize: 412_300 }, "en"),
		).toBe("412 kB · JPEG");
	});

	it("Given a codec and a type this table does not name, Then they are shown as stored", () => {
		const media = {
			mimeType: 'video/quicktime; codecs="ap4h"',
			videoBitRate: 640_000,
		};

		expect(weightLine(media, "en")).toBe("video/quicktime · ap4h · 640 kb/s");
	});

	it("Given a media with nothing measured, Then the line is empty", () => {
		expect(weightLine({}, "en")).toBe("");
	});
});

describe("soundLine", () => {
	it("Given an AAC stereo track, Then its codec, channels and rate", () => {
		const media = { mimeType: MP4, audioChannels: 2, audioBitRate: 128_000 };

		expect(soundLine(media, "en", WORDS)).toBe("AAC stereo, 128 kb/s");
	});

	it("Given a mono or a surround track, Then its channels are named", () => {
		const opus = 'video/webm; codecs="vp09.00.10.08, opus"';

		expect(soundLine({ mimeType: opus, audioChannels: 1 }, "en", WORDS)).toBe(
			"Opus mono",
		);
		expect(soundLine({ mimeType: opus, audioChannels: 6 }, "en", WORDS)).toBe(
			"Opus 6 channels",
		);
	});

	it("Given a track the server could not count, Then its codec alone", () => {
		expect(
			soundLine(
				{ mimeType: 'video/mp4; codecs="avc1.64001F, mp4a.6B"' },
				"en",
				WORDS,
			),
		).toBe("MP3");
	});

	it("Given a video without an audio codec, Then it has no sound", () => {
		expect(
			soundLine(
				{ mimeType: 'video/webm; codecs="vp09.00.10.08"' },
				"en",
				WORDS,
			),
		).toBeNull();
	});
});

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
