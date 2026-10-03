import type { RefusalCode } from "@pinry-reborn/auth";
import { MediaRefusal } from "./media";
import { m } from "./paraglide/messages.js";

type MediaRefusalCode = RefusalCode<"/api/v1/pins/{pinId}/media", "put">;

/**
 * One sentence per refusal of an upload the user can act on, keyed by the code: `MEDIA_TOO_LONG`
 * shares a 422 with `MEDIA_INVALID`. A key the contract stops declaring fails the typecheck.
 */
const REFUSALS = {
	MEDIA_TOO_LONG: m.media_too_long,
	MEDIA_CODEC_UNSUPPORTED: m.media_codec_unsupported,
} satisfies Partial<Record<MediaRefusalCode, () => string>>;

// `hasOwn` and not `in`: the code is the server's string, and `constructor` would answer otherwise.
function hasSentence(code: string | null): code is keyof typeof REFUSALS {
	return code !== null && Object.hasOwn(REFUSALS, code);
}

/** Why the media was refused, or the screen's own general sentence for anything else. */
export function mediaRefusal(error: unknown, general: () => string): string {
	const code = error instanceof MediaRefusal ? error.code : null;
	return hasSentence(code) ? REFUSALS[code]() : general();
}
