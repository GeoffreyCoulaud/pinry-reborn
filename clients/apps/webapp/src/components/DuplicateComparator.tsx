import {
	Button,
	Spinner,
	ToggleButton,
	ToggleButtonGroup,
	toast,
} from "@heroui/react";
import { ArrowLeft, Crown } from "lucide-react";
import { useState } from "react";
import {
	type Decision,
	type Decisions,
	decide,
	type Submit,
	storedDecisions,
	submitOf,
} from "../lib/duplicates";
import { isVideo } from "../lib/media";
import { tileStillSource } from "../lib/tiles";
import { m } from "../paraglide/messages.js";
import {
	type Duplicate,
	type Pin,
	useDuplicates,
	useResolveDuplicates,
} from "../pins";
import { IconButton } from "./IconButton";
import { RenditionImage } from "./RenditionImage";

function submitLabel(submit: Submit) {
	if (submit === null) {
		return m.compare_nothing();
	}
	return submit.kind === "MERGE"
		? m.compare_merge_count({ count: submit.count })
		: m.compare_reject_count({ count: submit.count });
}

/** A still image's original; a video's still rendition until block 50 plays it. */
function stageSource(url: string, mimeType?: string | null) {
	return isVideo(mimeType) ? tileStillSource(url, "LARGE") : url;
}

/** The group on one stage, opened on its stored state, with a decision per version (decision A). */
function Comparison({
	pin,
	duplicates,
	close,
	busy,
	submit: send,
}: {
	pin: Pin;
	duplicates: Duplicate[];
	close: () => void;
	busy: boolean;
	submit: (decisions: Decisions) => void;
}) {
	const versions = [pin, ...duplicates.map((one) => one.pin)];
	const [decisions, setDecisions] = useState(() =>
		storedDecisions(pin, duplicates),
	);
	const [index, setIndex] = useState(() =>
		Math.max(
			0,
			versions.findIndex((one) => decisions[one.id] !== "KEEP"),
		),
	);

	const under = versions[index] ?? pin;
	const url = under.media?.url;
	const decision = decisions[under.id];
	const submit = submitOf(decisions, duplicates);

	return (
		<div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto lg:overflow-hidden">
			<header className="flex items-center gap-2">
				<IconButton
					icon={ArrowLeft}
					name={m.compare_back()}
					variant="ghost"
					onPress={close}
				/>
				<h2 className="font-semibold">{m.compare_heading()}</h2>
			</header>
			{/* From `lg` the stage takes what is left, so the decision, the strip and the footer stay in view. */}
			<div className="flex min-w-0 flex-col gap-3 lg:min-h-0 lg:flex-1">
				<div className="relative h-80 shrink-0 bg-background-secondary lg:h-auto lg:min-h-0 lg:flex-1">
					{url ? (
						<img
							src={stageSource(url, under.media?.mimeType)}
							alt={under.description}
							className="absolute inset-4 size-[calc(100%-2rem)] object-contain"
						/>
					) : null}
				</div>
				<ToggleButtonGroup
					aria-label={m.compare_decision()}
					selectionMode="single"
					disallowEmptySelection
					className="self-start"
					selectedKeys={decision ? [decision] : []}
					onSelectionChange={(keys) => {
						const [chosen] = keys;
						setDecisions((current) =>
							decide(current, under.id, chosen as Decision),
						);
					}}
				>
					{/* The kept version changes by keeping another; the open pin is never rejected. */}
					<ToggleButton id="KEEP">
						<Crown aria-hidden />
						{m.merge_keep()}
					</ToggleButton>
					<ToggleButton id="MERGE" isDisabled={decision === "KEEP"}>
						<ToggleButtonGroup.Separator />
						{m.compare_merge()}
					</ToggleButton>
					<ToggleButton
						id="REJECT"
						isDisabled={decision === "KEEP" || under.id === pin.id}
					>
						<ToggleButtonGroup.Separator />
						{m.duplicate_reject()}
					</ToggleButton>
				</ToggleButtonGroup>
				<ul
					aria-label={m.compare_versions()}
					className="flex shrink-0 gap-2.5 overflow-x-auto pb-0.5"
				>
					{versions.map((version, at) => (
						<li key={version.id} className="shrink-0">
							<button
								type="button"
								aria-label={version.description}
								aria-current={at === index}
								onClick={() => setIndex(at)}
								className={`relative block w-26 rounded-xl border-2 outline-none focus-visible:ring-2 focus-visible:ring-focus lg:w-34 ${at === index ? "border-accent" : "border-transparent"}`}
							>
								{version.media?.url ? (
									<RenditionImage
										src={tileStillSource(version.media.url, "SMALL")}
										alt=""
										className={`aspect-3/2 w-full rounded-[10px] object-cover ${decisions[version.id] === "REJECT" ? "opacity-35 grayscale" : ""}`}
									/>
								) : null}
								{decisions[version.id] === "KEEP" ? (
									<Crown
										aria-hidden
										className="absolute end-1.5 top-1.5 size-5 rounded-full bg-warning p-0.5 text-warning-foreground"
									/>
								) : null}
							</button>
						</li>
					))}
				</ul>
			</div>
			<footer className="flex flex-wrap justify-end gap-3 border-t border-separator pt-3">
				<Button variant="secondary" onPress={close}>
					{m.cancel()}
				</Button>
				<Button
					isDisabled={submit === null || busy}
					onPress={() => send(decisions)}
				>
					{submitLabel(submit)}
				</Button>
			</footer>
		</div>
	);
}

/** The comparator in place of the pin's content, back on the pin or on the kept pin once applied. */
export function DuplicateComparator({
	pin,
	close,
	merged,
}: {
	pin: Pin;
	close: () => void;
	merged: (kept: Pin) => void;
}) {
	const duplicates = useDuplicates(pin.id).data;
	const resolve = useResolveDuplicates(pin.id, (kept) => {
		close();
		if (kept.id !== pin.id) {
			merged(kept);
		}
	});

	if (duplicates === undefined) {
		return <Spinner aria-label={m.compare_heading()} className="m-auto" />;
	}
	return (
		<Comparison
			pin={pin}
			duplicates={duplicates}
			close={close}
			busy={resolve.isPending}
			submit={(decisions) =>
				resolve.mutate(decisions, {
					onError: () => toast.danger(m.compare_refused()),
				})
			}
		/>
	);
}
