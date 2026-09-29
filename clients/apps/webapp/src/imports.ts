import type { Schemas } from "@pinry-reborn/auth";
import { useSyncExternalStore } from "react";
import { auth } from "./api";
import {
	type Attempt,
	type ChunkAnswer,
	type FileIdentity,
	nextStep,
	RETRY_MS,
	refusedChunk,
	type UploadStep,
} from "./lib/imports";
import { m } from "./paraglide/messages.js";
import { getLocale } from "./paraglide/runtime.js";

export type Import = Schemas["UserDataImportOutputDto"];

/** How far a running import is, which the task centre shows as well. */
export function importProgress(row: Import): string {
	const count = new Intl.NumberFormat(getLocale());
	return row.announcedPins === null
		? m.import_starting()
		: m.import_running({
				processed: count.format(row.processedPins),
				announced: count.format(row.announcedPins),
			});
}

/** The upload in this tab: bytes sent, and whether it sends, waits on the user, or was refused. */
export interface Upload {
	importId: string;
	file: File;
	sent: number;
	state: "SENDING" | "PAUSED" | "STOPPED";
	code: string | null;
}

// One record per import, under its id, so a reload can ask for the same file.
const RECORD = "pinry-import-";

/** A private window, or site data the browser blocks, throws: the import then has no record. */
export function writeRecord(id: string, { name, size, lastModified }: File) {
	try {
		localStorage.setItem(
			RECORD + id,
			JSON.stringify({ name, size, lastModified }),
		);
	} catch {
		// A reload then offers the cancel button only, as another browser would.
	}
}

export function importRecord(id: string): FileIdentity | null {
	try {
		return JSON.parse(
			localStorage.getItem(RECORD + id) ?? "null",
		) as FileIdentity | null;
	} catch {
		return null;
	}
}

function forgetRecord(id: string) {
	try {
		localStorage.removeItem(RECORD + id);
	} catch {
		// Nothing could be written there either.
	}
}

/**
 * The latest import's, while it waits on its archive, and this tab's upload's, which a read begun
 * before its import opened does not know of: no record outlives what it serves.
 */
export function pruneRecords(latest: Import | null) {
	const kept = [
		latest?.state === "AWAITING_ARCHIVE" ? latest.id : null,
		upload?.importId,
	];
	try {
		for (const key of Object.keys(localStorage)) {
			if (key.startsWith(RECORD) && !kept.some((id) => key === RECORD + id)) {
				localStorage.removeItem(key);
			}
		}
	} catch {
		// Nothing could be written there either.
	}
}

// The store lives above the router, so changing screen leaves the upload running.
let upload: Upload | null = null;
let controller = new AbortController();
let resume = () => {
	// Nothing to resume until an upload pauses.
};
const listeners = new Set<() => void>();

/** The browser shows its own sentence whatever the page sets (MDN, `beforeunload` event). */
function keepPage(event: BeforeUnloadEvent) {
	event.preventDefault();
}

function publish(next: Upload | null) {
	upload = next;
	if (next !== null && next.state !== "STOPPED") {
		addEventListener("beforeunload", keepPage);
	} else {
		removeEventListener("beforeunload", keepPage);
	}
	for (const listener of listeners) {
		listener();
	}
}

export function useUpload() {
	return useSyncExternalStore(
		(listener) => {
			listeners.add(listener);
			return () => listeners.delete(listener);
		},
		() => upload,
	);
}

/** What a request of the upload came back with, or `null` for one that never reached the API. */
async function answerOf(
	request: Promise<{ data?: Import; error?: unknown; response: Response }>,
) {
	try {
		const { data, error, response } = await request;
		if (data === undefined) {
			return refusedChunk(response.status, error);
		}
		return { uploadedBytes: data.uploadedBytes };
	} catch {
		return null;
	}
}

function putChunk(id: string, file: File, offset: number, chunkBytes: number) {
	const chunk = file.slice(offset, offset + chunkBytes);
	return answerOf(
		auth.client.PUT("/api/v1/me/imports/{id}/archive", {
			params: { path: { id }, query: { offset } },
			body: chunk as unknown as string,
			// openapi-fetch serialises anything but `FormData` as JSON, which sends a `Blob` as `{}`.
			bodySerializer: () => chunk,
			headers: { "Content-Type": "application/octet-stream" },
			signal: controller.signal,
		}),
	);
}

/** What an upload holds from its first chunk to its last. */
interface Transfer {
	importId: string;
	file: File;
	chunkBytes: number;
	settle: () => Promise<unknown>;
}

export async function send(transfer: Transfer, from: Attempt) {
	const { importId, file, chunkBytes, settle } = transfer;
	const { signal } = controller;
	let attempt = from;
	let step: UploadStep = from;
	let sent = 0;
	while (step.next === "SEND" || step.next === "COMPLETE") {
		attempt = step;
		sent = attempt.next === "SEND" ? attempt.offset : file.size;
		publish({ importId, file, sent, state: "SENDING", code: null });
		if (attempt.failures > 0) {
			await new Promise((resolve) => setTimeout(resolve, RETRY_MS));
		}
		if (signal.aborted) {
			return;
		}
		const answer: ChunkAnswer =
			attempt.next === "SEND"
				? await putChunk(importId, file, attempt.offset, chunkBytes)
				: await answerOf(
						auth.client.POST("/api/v1/me/imports/{id}/archive/complete", {
							params: { path: { id: importId } },
						}),
					);
		if (signal.aborted) {
			return;
		}
		step = nextStep(answer, attempt, file.size);
	}
	if (step.next === "PAUSE") {
		const paused = { ...attempt, failures: 0 };
		resume = () => void send(transfer, paused);
		publish({ importId, file, sent, state: "PAUSED", code: null });
		return;
	}
	if (step.next === "STOP") {
		publish({ importId, file, sent, state: "STOPPED", code: step.code });
		void settle();
		return;
	}
	// The server's row takes over once read, so the stale one never shows in between.
	forgetRecord(importId);
	await settle();
	if (!signal.aborted) {
		publish(null);
	}
}

export function resumeUpload() {
	resume();
}

/** Stops the upload in this tab and leaves the server's import alone. Tests reset with it. */
export function dropUpload() {
	controller.abort();
	controller = new AbortController();
	publish(null);
}
