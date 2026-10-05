import { Button, Disclosure, toast } from "@heroui/react";
import { useId } from "react";
import { tileStillSource } from "../lib/tiles";
import { m } from "../paraglide/messages.js";
import { type Duplicate, useDuplicates, useRejectDuplicate } from "../pins";
import { RenditionImage } from "./RenditionImage";

/** One candidate, opened by its thumbnail, with the gesture that rejects or restores it. */
function DuplicateRow({
	duplicate,
	open,
	toggle,
	isDisabled,
}: {
	duplicate: Duplicate;
	open: (pinId: string) => void;
	toggle: () => void;
	isDisabled: boolean;
}) {
	const { pin, rejected } = duplicate;
	const media = pin.media;
	return (
		<li className="flex items-center gap-3">
			<button
				type="button"
				onClick={() => open(pin.id)}
				className="flex min-w-0 flex-1 items-center gap-3 rounded text-start outline-none focus-visible:ring-2 focus-visible:ring-focus"
			>
				{media?.url ? (
					<RenditionImage
						src={tileStillSource(media.url, "SMALL")}
						alt=""
						className="size-12 shrink-0 rounded object-cover"
					/>
				) : null}
				<span className="flex min-w-0 flex-col">
					<span className="truncate">{pin.description}</span>
					{media?.width != null && media.height != null ? (
						<span className="text-sm text-muted">
							{m.media_dimensions({ width: media.width, height: media.height })}
						</span>
					) : null}
				</span>
			</button>
			{/* Outlined rather than flat: a button in a row of words is one nobody presses. */}
			<Button
				size="sm"
				variant="outline"
				isDisabled={isDisabled}
				onPress={toggle}
			>
				{rejected ? m.duplicate_restore() : m.duplicate_reject()}
			</Button>
		</li>
	);
}

/**
 * The pin's likely duplicates: the pending ones listed, the rejected ones folded beneath
 * (specification 2026-10-05, decisions B and C). A pin with none shows nothing.
 */
export function PinDuplicates({
	pinId,
	open,
}: {
	pinId: string;
	open: (pinId: string) => void;
}) {
	const duplicates = useDuplicates(pinId).data ?? [];
	const reject = useRejectDuplicate(pinId);
	const heading = useId();
	const pending = duplicates.filter((one) => !one.rejected);
	const rejected = duplicates.filter((one) => one.rejected);
	const row = (duplicate: Duplicate) => (
		<DuplicateRow
			key={duplicate.pin.id}
			duplicate={duplicate}
			open={open}
			isDisabled={reject.isPending}
			toggle={() =>
				reject.mutate(
					{ otherPinId: duplicate.pin.id, rejected: !duplicate.rejected },
					{ onError: () => toast.danger(m.duplicate_refused()) },
				)
			}
		/>
	);

	return (
		<>
			{pending.length > 0 ? (
				<section className="flex flex-col gap-2">
					<h3 id={heading} className="text-sm text-muted">
						{m.duplicates()}
					</h3>
					<ul aria-labelledby={heading} className="flex flex-col gap-2">
						{pending.map(row)}
					</ul>
				</section>
			) : null}
			{rejected.length > 0 ? (
				<Disclosure>
					<Disclosure.Heading>
						<Disclosure.Trigger className="inline-flex items-center gap-1 text-sm text-muted">
							{m.duplicates_rejected({ count: rejected.length })}
							<Disclosure.Indicator />
						</Disclosure.Trigger>
					</Disclosure.Heading>
					<Disclosure.Content>
						<ul className="flex flex-col gap-2 pt-2">{rejected.map(row)}</ul>
					</Disclosure.Content>
				</Disclosure>
			) : null}
		</>
	);
}
