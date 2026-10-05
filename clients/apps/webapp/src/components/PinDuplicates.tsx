import {
	Button,
	Checkbox,
	Disclosure,
	Radio,
	RadioGroup,
	toast,
} from "@heroui/react";
import { type ReactNode, useId, useState } from "react";
import { tileStillSource } from "../lib/tiles";
import { m } from "../paraglide/messages.js";
import {
	type Duplicate,
	type Pin,
	useDuplicates,
	useMergePins,
	useRejectDuplicate,
} from "../pins";
import { RenditionImage } from "./RenditionImage";

/** One candidate, opened by its thumbnail, with the gesture that rejects or restores it. */
function DuplicateRow({
	duplicate,
	open,
	toggle,
	isDisabled,
	children,
}: {
	duplicate: Duplicate;
	open: (pinId: string) => void;
	toggle: () => void;
	isDisabled: boolean;
	children?: ReactNode;
}) {
	const { pin, rejected } = duplicate;
	const media = pin.media;
	return (
		<li className="flex flex-col gap-1">
			<div className="flex items-center gap-3">
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
								{m.media_dimensions({
									width: media.width,
									height: media.height,
								})}
							</span>
						) : null}
					</span>
				</button>
				<Button
					size="sm"
					variant="outline"
					isDisabled={isDisabled}
					onPress={toggle}
				>
					{rejected ? m.duplicate_restore() : m.duplicate_reject()}
				</Button>
			</div>
			{children}
		</li>
	);
}

/**
 * The pin's likely duplicates: the pending ones listed as a group to merge, the rejected ones folded
 * beneath (specification 2026-10-05, decisions B and C). A pin with none shows nothing.
 */
export function PinDuplicates({
	pinId,
	open,
	merged,
}: {
	pinId: string;
	open: (pinId: string) => void;
	merged: (kept: Pin) => void;
}) {
	const duplicates = useDuplicates(pinId).data ?? [];
	const reject = useRejectDuplicate(pinId);
	const merge = useMergePins(merged);
	const [keep, setKeep] = useState(pinId);
	const [excluded, setExcluded] = useState<ReadonlySet<string>>(new Set());
	const heading = useId();
	const pending = duplicates.filter((one) => !one.rejected);
	const rejected = duplicates.filter((one) => one.rejected);
	// A kept candidate rejected meanwhile hands the choice back to the open pin.
	const kept = pending.some((one) => one.pin.id === keep) ? keep : pinId;
	const isIncluded = (id: string) => id === kept || !excluded.has(id);
	const group = [pinId, ...pending.map((one) => one.pin.id)].filter(
		(id) => id === pinId || isIncluded(id),
	);
	const busy = reject.isPending || merge.isPending;
	const include = (id: string, isSelected: boolean) =>
		setExcluded((current) => {
			const next = new Set(current);
			if (isSelected) {
				next.delete(id);
			} else {
				next.add(id);
			}
			return next;
		});
	const row = (duplicate: Duplicate, controls?: ReactNode) => (
		<DuplicateRow
			key={duplicate.pin.id}
			duplicate={duplicate}
			open={open}
			isDisabled={busy}
			toggle={() =>
				reject.mutate(
					{ otherPinId: duplicate.pin.id, rejected: !duplicate.rejected },
					{ onError: () => toast.danger(m.duplicate_refused()) },
				)
			}
		>
			{controls}
		</DuplicateRow>
	);
	const pendingRow = (duplicate: Duplicate) => {
		const { id, description } = duplicate.pin;
		return row(
			duplicate,
			// Under the description, past the thumbnail's width.
			<div className="flex items-center gap-4 ps-15">
				<Radio value={id} aria-label={m.merge_keep_pin({ description })}>
					<Radio.Content>
						<Radio.Control>
							<Radio.Indicator />
						</Radio.Control>
						{m.merge_keep()}
					</Radio.Content>
				</Radio>
				<Checkbox
					variant="secondary"
					aria-label={m.merge_include_pin({ description })}
					isSelected={isIncluded(id)}
					isDisabled={id === kept}
					onChange={(isSelected) => include(id, isSelected)}
				>
					<Checkbox.Content>
						<Checkbox.Control>
							<Checkbox.Indicator />
						</Checkbox.Control>
						{m.merge_include()}
					</Checkbox.Content>
				</Checkbox>
			</div>,
		);
	};

	return (
		<>
			{pending.length > 0 ? (
				<section className="flex flex-col gap-2">
					<h3 id={heading} className="text-sm text-muted">
						{m.duplicates()}
					</h3>
					<RadioGroup
						variant="secondary"
						aria-label={m.merge_kept()}
						value={kept}
						onChange={setKeep}
						className="flex flex-col gap-2"
					>
						<Radio value={pinId}>
							<Radio.Content>
								<Radio.Control>
									<Radio.Indicator />
								</Radio.Control>
								{m.merge_keep_this()}
							</Radio.Content>
						</Radio>
						<ul aria-labelledby={heading} className="flex flex-col gap-3">
							{pending.map(pendingRow)}
						</ul>
					</RadioGroup>
					<Button
						className="self-start"
						isDisabled={busy || group.length < 2}
						onPress={() =>
							merge.mutate(
								{
									keptPinId: kept,
									absorbedPinIds: group.filter((id) => id !== kept),
								},
								{ onError: () => toast.danger(m.merge_refused()) },
							)
						}
					>
						{m.merge({ count: group.length })}
					</Button>
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
						<ul className="flex flex-col gap-2 pt-2">
							{rejected.map((duplicate) => row(duplicate))}
						</ul>
					</Disclosure.Content>
				</Disclosure>
			) : null}
		</>
	);
}
