/** Whether a pin's stored `mimeType` is a video, its `codecs` parameter left to `canPlayType`. */
export function isVideo(
	mimeType: string | null | undefined,
): mimeType is string {
	return mimeType?.toLowerCase().startsWith("video/") ?? false;
}

/** The extensions of the video types the server stores, as `ExportMediaExtension` maps them. */
const VIDEO_EXTENSIONS: Record<string, string> = {
	"video/mp4": "mp4",
	"video/webm": "webm",
};

/** What the fallback's link saves the original as: its address ends in `media`. */
export function videoFileName(mimeType: string): string {
	const essence = mimeType.replace(/;.*/s, "").trim().toLowerCase();
	return Object.hasOwn(VIDEO_EXTENSIONS, essence)
		? `video.${VIDEO_EXTENSIONS[essence]}`
		: "video";
}

/** `canPlayType` answers `""`, `maybe` or `probably`: only the first rules a video out (ADR 0047, decision 6). */
export function isPlayable(answer: string): boolean {
	return answer !== "";
}

/**
 * The `codecs` parameter of a type, the video track's first and the audio track's second if any.
 * @internal
 */
export function codecsOf(mimeType: string | null | undefined): string[] {
	const parameter = (mimeType ?? "")
		.split(";")
		.map((part) => part.trim())
		.find((part) => part.toLowerCase().startsWith("codecs="));
	return (parameter ?? "")
		.slice("codecs=".length)
		.replaceAll('"', "")
		.split(",")
		.map((codec) => codec.trim())
		.filter((codec) => codec !== "");
}

/** The names of the codecs the server writes (`CodecsParameter`), by their RFC 6381 prefix. */
const CODEC_NAMES: [string, string][] = [
	["avc1", "H.264"],
	["hvc1", "H.265"],
	["vp09", "VP9"],
	["av01", "AV1"],
	["mp4a.40", "AAC"],
	["mp4a.6b", "MP3"],
	["opus", "Opus"],
];

function codecName(codec: string): string {
	const lower = codec.toLowerCase();
	return CODEC_NAMES.find(([prefix]) => lower.startsWith(prefix))?.[1] ?? codec;
}

const FORMAT_NAMES: Record<string, string> = {
	"image/jpeg": "JPEG",
	"image/png": "PNG",
	"image/gif": "GIF",
	"image/webp": "WebP",
	"video/mp4": "MP4",
	"video/webm": "WebM",
};

/** What the facts column reads of a media. */
interface Measured {
	mimeType?: string | null;
	byteSize?: number | null;
	videoBitRate?: number | null;
	audioChannels?: number | null;
	audioBitRate?: number | null;
}

function unit(locale: string, name: string, digits: number) {
	return new Intl.NumberFormat(locale, {
		style: "unit",
		unit: name,
		maximumFractionDigits: digits,
	});
}

/** A rate in bits per second, in megabits from one. */
function rate(bitsPerSecond: number, locale: string): string {
	return bitsPerSecond >= 1_000_000
		? unit(locale, "megabit-per-second", 1).format(bitsPerSecond / 1_000_000)
		: unit(locale, "kilobit-per-second", 0).format(bitsPerSecond / 1_000);
}

/** A weight in bytes, in megabytes from one. */
function weight(bytes: number, locale: string): string {
	return bytes >= 1_000_000
		? unit(locale, "megabyte", 1).format(bytes / 1_000_000)
		: unit(locale, "kilobyte", 0).format(bytes / 1_000);
}

/** The type's short name, "MP4" for `video/mp4; codecs=...`. */
function formatName(mimeType: string): string {
	const essence = mimeType.replace(/;.*/s, "").trim().toLowerCase();
	return FORMAT_NAMES[essence] ?? essence;
}

/** The weight, then the format, its video codec and that codec's rate: "8.4 MB", "MP4", "H.264", "4.2 Mb/s". */
export function weightParts(media: Measured, locale: string): string[] {
	const video = isVideo(media.mimeType)
		? codecsOf(media.mimeType)[0]
		: undefined;
	const parts = [
		media.byteSize == null ? undefined : weight(media.byteSize, locale),
		media.mimeType == null ? undefined : formatName(media.mimeType),
		video === undefined ? undefined : codecName(video),
		media.videoBitRate == null ? undefined : rate(media.videoBitRate, locale),
	];
	return parts.filter((part) => part !== undefined);
}

/** The words for a track's channels, which the catalogues hold. */
export interface ChannelWords {
	mono: string;
	stereo: string;
	many: (count: number) => string;
}

/** A video's sound, "AAC stereo, 128 kb/s", or `null` for a video without any. */
export function soundLine(
	media: Measured,
	locale: string,
	words: ChannelWords,
): string | null {
	const audio = codecsOf(media.mimeType)[1];
	if (audio === undefined) {
		return null;
	}
	const channels = media.audioChannels;
	const named =
		channels == null
			? codecName(audio)
			: `${codecName(audio)} ${layoutOf(channels, words)}`;
	const bitRate = media.audioBitRate;
	return bitRate == null ? named : `${named}, ${rate(bitRate, locale)}`;
}

function layoutOf(channels: number, words: ChannelWords): string {
	if (channels === 1) {
		return words.mono;
	}
	return channels === 2 ? words.stereo : words.many(channels);
}
