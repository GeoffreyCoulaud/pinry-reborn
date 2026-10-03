/** Whether a pin's stored `mimeType` is a video, its `codecs` parameter left to `canPlayType`. */
export function isVideo(
	mimeType: string | null | undefined,
): mimeType is string {
	return mimeType?.toLowerCase().startsWith("video/") ?? false;
}

/** `canPlayType` answers `""`, `maybe` or `probably`: only the first rules a video out (ADR 0047, decision 6). */
export function isPlayable(answer: string): boolean {
	return answer !== "";
}
