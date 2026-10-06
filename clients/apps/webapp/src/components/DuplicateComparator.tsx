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
	pixelsOf,
	type Submit,
	storedDecisions,
	submitOf,
} from "../lib/duplicates";
import { isVideo, soundLine, weightLine } from "../lib/media";
import { tileStillSource } from "../lib/tiles";
import { UNZOOMED } from "../lib/zoom";
import { m } from "../paraglide/messages.js";
import { getLocale } from "../paraglide/runtime.js";
import {
	type Duplicate,
	type Pin,
	useDuplicates,
	useResolveDuplicates,
} from "../pins";
import { DuplicateStage } from "./DuplicateStage";
import { IconButton } from "./IconButton";
import { RenditionImage } from "./RenditionImage";
import { useArrowKeys } from "./useArrowKeys";

function submitLabel(submit: Submit) {
	if (submit === null) {
		return m.compare_nothing();
	}
	return submit.kind === "MERGE"
		? m.compare_merge_count({ count: submit.count })
		: m.compare_reject_count({ count: submit.count });
}

/** The facts beside the stage, with no control among them (decision A). */
function Facts({
	version,
	open,
	largest,
}: {
	version: Pin;
	open: boolean;
	largest: boolean;
}) {
	const media: Partial<NonNullable<Pin["media"]>> = version.media ?? {};
	const locale = getLocale();
	const seconds = new Intl.NumberFormat(locale, {
		style: "unit",
		unit: "second",
		maximumFractionDigits: 1,
	});
	const added = new Intl.DateTimeFormat(locale, { dateStyle: "medium" });
	const names = (list: { name: string }[], none: string) =>
		list.length > 0 ? list.map((one) => one.name).join(", ") : none;

	return (
		<aside className="flex min-w-0 flex-col gap-4 lg:min-h-0 lg:overflow-y-auto lg:border-s lg:border-separator lg:ps-5">
			<div>
				<h3 className="font-semibold">{version.description}</h3>
				{open ? (
					<p className="text-sm text-muted">{m.compare_open_pin()}</p>
				) : null}
			</div>
			<dl className="grid grid-cols-[auto_1fr] gap-x-3.5 gap-y-1.5 tabular-nums [&_dd]:min-w-0 [&_dt]:text-muted">
				<dt>{m.compare_size()}</dt>
				<dd>
					{media.width != null && media.height != null
						? m.media_dimensions({ width: media.width, height: media.height })
						: null}{" "}
					{largest ? (
						<span className="text-sm font-semibold text-warning">
							{m.compare_largest()}
						</span>
					) : null}
				</dd>
				{media.durationMillis == null ? null : (
					<>
						<dt>{m.compare_duration()}</dt>
						<dd>{seconds.format(media.durationMillis / 1000)}</dd>
					</>
				)}
				{isVideo(media.mimeType) ? (
					<>
						<dt>{m.compare_sound()}</dt>
						<dd>
							{soundLine(media, locale, {
								mono: m.compare_sound_mono(),
								stereo: m.compare_sound_stereo(),
								many: (count) => m.compare_sound_channels({ count }),
							}) ?? m.compare_sound_none()}
						</dd>
					</>
				) : null}
				<dt>{m.compare_weight()}</dt>
				<dd>{weightLine(media, locale)}</dd>
				<dt>{m.compare_added()}</dt>
				<dd>{added.format(new Date(version.createdAt))}</dd>
				<dt>{m.boards()}</dt>
				<dd>{names(version.boards, m.boards_none())}</dd>
				<dt>{m.tags()}</dt>
				<dd>{names(version.tags, m.compare_tags_none())}</dd>
			</dl>
		</aside>
	);
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
	const [view, setView] = useState(UNZOOMED);
	const [split, setSplit] = useState(50);
	const step = (by: number) =>
		setIndex((current) => (current + by + versions.length) % versions.length);
	useArrowKeys(
		() => step(-1),
		() => step(1),
	);

	const under = versions[index] ?? pin;
	const kept = versions.find((one) => decisions[one.id] === "KEEP") ?? pin;
	const decision = decisions[under.id];
	const most = Math.max(...versions.map(pixelsOf));
	const submit = submitOf(decisions, duplicates);

	return (
		<div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto lg:grid lg:grid-cols-[minmax(0,1fr)_20rem] lg:grid-rows-[auto_minmax(0,1fr)_auto] lg:overflow-hidden">
			<header className="col-span-full flex items-center gap-2">
				<IconButton
					icon={ArrowLeft}
					name={m.compare_back()}
					variant="ghost"
					onPress={close}
				/>
				<h2 className="font-semibold">{m.compare_heading()}</h2>
			</header>
			{/* From `lg` the stage takes what is left, so the decision, the strip and the footer stay in view. */}
			<div className="flex min-w-0 flex-col gap-3 lg:min-h-0">
				<DuplicateStage
					under={under}
					kept={kept}
					view={view}
					setView={setView}
					split={split}
					setSplit={setSplit}
				/>
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
			<Facts
				version={under}
				open={under.id === pin.id}
				largest={most > 0 && pixelsOf(under) === most}
			/>
			<footer className="col-span-full flex flex-wrap justify-end gap-3 border-t border-separator pt-3">
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
