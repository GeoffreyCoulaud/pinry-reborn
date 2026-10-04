import { toast } from "@heroui/react";
import {
	type DropPartition,
	type DropRefusal,
	type FileVerdict,
	partitionDrop,
} from "./lib/drops";
import {
	byteRefusal,
	isStorableFile,
	type MeasuredUpload,
	type UploadLimits,
	uploadRefusal,
} from "./lib/uploads";
import { urisFromDrop } from "./lib/uris";
import { m } from "./paraglide/messages.js";

const REFUSALS: Record<DropRefusal, () => string> = {
	TOO_MANY_BYTES: m.file_too_heavy,
	TOO_MANY_PIXELS: m.file_too_large,
	UNSUPPORTED_FORMAT: m.file_unsupported,
	UNREADABLE: m.file_unreadable,
	UNSUPPORTED_DROP: m.drop_unsupported,
};

const READING_DELAY_MS = 300;

/** How long a video's header may take to read before the server is left to judge its frame. */
const VIDEO_HEADER_TIMEOUT_MS = 10_000;

/** An element refused speaks where the gesture happened, and the gesture owns no form. */
export function refuse(refusal: DropRefusal) {
	toast.danger(REFUSALS[refusal]());
}

/** A video's frame read from its header alone, `null` where the browser cannot tell. */
function videoPixels(file: File): Promise<number | null> {
	const source = URL.createObjectURL(file);
	const video = document.createElement("video");
	let timeout: ReturnType<typeof setTimeout> | undefined;
	return new Promise<number | null>((resolve) => {
		timeout = setTimeout(() => resolve(null), VIDEO_HEADER_TIMEOUT_MS);
		// A file with no video track reads as 0 x 0.
		video.onloadedmetadata = () =>
			resolve(video.videoWidth * video.videoHeight || null);
		video.onerror = () => resolve(null);
		video.preload = "metadata";
		video.src = source;
	}).finally(() => {
		clearTimeout(timeout);
		URL.revokeObjectURL(source);
	});
}

/** The pixel count a limit is read against, which nothing short of a decoder knows. */
async function measured(file: File): Promise<MeasuredUpload> {
	if (file.type.startsWith("video/")) {
		return {
			size: file.size,
			type: file.type,
			pixels: await videoPixels(file),
		};
	}
	// A file with no type is the server's to judge.
	if (!file.type.startsWith("image/")) {
		return { size: file.size, type: file.type, pixels: null };
	}
	const bitmap = await createImageBitmap(file);
	const measurement = {
		size: file.size,
		type: file.type,
		pixels: bitmap.width * bitmap.height,
	};
	// A decoded bitmap is four bytes a pixel, so ten photographs held at once are hundreds of
	// megabytes (MDN, `ImageBitmap.close()`).
	bitmap.close();
	return measurement;
}

/** One file, judged before a byte of it is sent: the file kept, or the reason it is not. */
async function judge(
	file: File,
	limits: UploadLimits | undefined,
): Promise<FileVerdict> {
	// A drop bypasses `accept`, which only the file picker honours, so a format refused costs no
	// decode at all.
	if (!isStorableFile(file, limits)) {
		return "UNSUPPORTED_FORMAT";
	}
	const tooHeavy = byteRefusal(file, limits);
	if (tooHeavy !== null) {
		return tooHeavy;
	}
	let measurement: MeasuredUpload;
	try {
		measurement = await measured(file);
	} catch {
		return "UNREADABLE";
	}
	return uploadRefusal(measurement, limits) ?? { file, measurement };
}

/**
 * What a gesture hands over, judged in full before anything of it is shown. The files are measured
 * in series so one decoded bitmap is held at a time, and the file picker enters here too.
 */
export async function judgeDrop(
	files: readonly File[],
	uriList: string,
	limits: UploadLimits | undefined,
): Promise<DropPartition> {
	const verdicts: FileVerdict[] = [];
	// A wallpaper takes seconds to decode, and a drop that says nothing meanwhile reads as lost.
	// Delayed so that a drop read at once flashes nothing.
	let reading: string | undefined;
	const announce = setTimeout(() => {
		reading = toast(m.drop_reading({ count: files.length }), {
			isLoading: true,
			timeout: 0,
		});
	}, READING_DELAY_MS);
	try {
		for (const file of files) {
			verdicts.push(await judge(file, limits));
		}
	} finally {
		clearTimeout(announce);
		if (reading !== undefined) {
			toast.close(reading);
		}
	}
	return partitionDrop(verdicts, urisFromDrop(uriList));
}
