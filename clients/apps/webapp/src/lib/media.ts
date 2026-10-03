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
