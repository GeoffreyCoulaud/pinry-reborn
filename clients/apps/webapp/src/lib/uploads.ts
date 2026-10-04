import type { Schemas } from "@pinry-reborn/auth";
import { isVideo } from "./media";

/** Why this deployment will not store this file, told before a byte of it is sent. */
export type UploadRefusal =
	| "TOO_MANY_BYTES"
	| "TOO_MANY_PIXELS"
	| "UNSUPPORTED_FORMAT"
	| "UNREADABLE";

/** The limits the handshake publishes, read from the contract rather than retyped (4.3). */
export type UploadLimits = Schemas["HandshakeOutputDto"]["limits"];

/** What a file would cost the server: a picture's pixels, which the browser decodes, and no video's. */
export interface MeasuredUpload {
	size: number;
	type: string;
	pixels: number | null;
}

/**
 * The limits are the deployment's, so they are read from the handshake rather than held here: a
 * bound written into the bundle drifts from the instance that configures it (specification
 * 2026-09-10, 4.3). Unknown limits refuse nothing, and the upload then meets the server's own
 * answer.
 */
export function uploadRefusal(
	upload: MeasuredUpload,
	limits: UploadLimits | undefined,
): UploadRefusal | null {
	if (limits === undefined) {
		return null;
	}
	return (
		byteRefusal(upload, limits) ??
		((upload.pixels ?? 0) > limits.maxPixelsPerFrame ? "TOO_MANY_PIXELS" : null)
	);
}

/** The bound a file is weighed against: an untyped file can be either, so the larger one. */
function byteBound(type: string, limits: UploadLimits): number {
	if (type === "") {
		return Math.max(limits.maxImageBytes, limits.maxVideoBytes);
	}
	return isVideo(type) ? limits.maxVideoBytes : limits.maxImageBytes;
}

/** The one limit a file's size and type answer, read before a decode that can take seconds. */
export function byteRefusal(
	file: { size: number; type: string },
	limits: UploadLimits | undefined,
): "TOO_MANY_BYTES" | null {
	return limits !== undefined && file.size > byteBound(file.type, limits)
		? "TOO_MANY_BYTES"
		: null;
}

/**
 * A drop bypasses `accept`, which only the file picker honours, so the type is read here too. The
 * formats are the deployment's, as the limits are: an SVG is a picture the browser decodes and the
 * storage refuses. Before the handshake answers, only what the browser calls a picture or a video
 * passes, and a file it names no type for always does: the format then meets the server's answer.
 */
export function isStorableFile(
	file: { type: string },
	limits: UploadLimits | undefined,
): boolean {
	if (file.type === "") {
		return true;
	}
	if (limits === undefined) {
		return file.type.startsWith("image/") || isVideo(file.type);
	}
	return limits.mediaTypes.includes(file.type);
}

/** A file picker's `accept`, which offers what `isStorableFile` would pass. */
export function acceptOf(limits: UploadLimits | undefined): string {
	return limits?.mediaTypes.join(",") ?? "image/*,video/*";
}
