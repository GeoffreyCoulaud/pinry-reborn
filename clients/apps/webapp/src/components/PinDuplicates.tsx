import { Button } from "@heroui/react";
import { tileStillSource } from "../lib/tiles";
import { m } from "../paraglide/messages.js";
import { useDuplicates } from "../pins";
import { RenditionImage } from "./RenditionImage";

/** Enough thumbnails to read as a group without widening the row. */
const STACKED = 4;

/**
 * The pin's group as one row: its candidates stacked, how many are pending, and the way into the
 * comparator; once every one is rejected, how many and the way to review them (decision A).
 */
export function PinDuplicates({
	pinId,
	compare,
}: {
	pinId: string;
	compare: () => void;
}) {
	const duplicates = useDuplicates(pinId).data ?? [];
	const pending = duplicates.filter((one) => !one.rejected);
	if (duplicates.length === 0) {
		return null;
	}
	// The thumbnails are the ones the count counts.
	const counted = pending.length > 0 ? pending : duplicates;
	return (
		<div className="flex flex-wrap items-center gap-3 rounded-2xl border border-separator p-3">
			<div className="flex">
				{counted
					.slice(0, STACKED)
					.map(({ pin }) =>
						pin.media?.url ? (
							<RenditionImage
								key={pin.id}
								src={tileStillSource(pin.media.url, "SMALL")}
								alt=""
								className="-ms-2.5 size-9 rounded-lg border-2 border-overlay object-cover first:ms-0"
							/>
						) : null,
					)}
			</div>
			<span className="min-w-32 flex-1">
				{pending.length > 0
					? m.duplicates_pending({ count: pending.length })
					: m.duplicates_rejected({ count: duplicates.length })}
			</span>
			<Button size="sm" onPress={compare}>
				{pending.length > 0 ? m.compare() : m.review()}
			</Button>
		</div>
	);
}
