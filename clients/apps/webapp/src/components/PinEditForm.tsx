import {
	Button,
	Input,
	Label,
	ListBox,
	Select,
	Tag,
	TagGroup,
	TextArea,
	TextField,
	ToggleButton,
	ToggleButtonGroup,
} from "@heroui/react";
import { type ReactNode, useEffect, useId, useState } from "react";
import { useBoards } from "../boards";
import { useDebounced } from "../debounce";
import { downloadReason } from "../downloadReasons";
import { type ImageSource, useHandshake, useSetPinImage } from "../images";
import type { KeptFile } from "../lib/drops";
import { m } from "../paraglide/messages.js";
import { type Pin, useTagSearch, useUpdatePin } from "../pins";
import { ImageDropBox } from "./ImageDropBox";
import { PinSides } from "./PinSides";

/**
 * Free text over the author's own names. The server decides which names are one tag, folding to
 * ASCII, so the field asks it and offers what it answers rather than deciding it is looking at a
 * new tag (specification 2026-09-20, decision L). It asks once the typing pauses, not once per
 * character (specification 2026-09-21, decision P).
 */
function TagField({
	names,
	onChange,
}: {
	names: readonly string[];
	onChange: (names: readonly string[]) => void;
}) {
	const [typed, setTyped] = useState("");
	const asked = useDebounced(typed);
	const offered = (useTagSearch(asked).data ?? []).filter(
		(name) => !names.includes(name),
	);

	function add(name: string) {
		const held = name.trim();
		if (held !== "" && !names.includes(held)) {
			onChange([...names, held]);
		}
		setTyped("");
	}

	return (
		<div className="flex flex-col gap-2">
			<TextField value={typed} onChange={setTyped} variant="secondary">
				<Label>{m.tags()}</Label>
				<Input
					onKeyDown={(event) => {
						// The field sits inside the pin's form, where Enter would save it: here it names a tag.
						if (event.key !== "Enter") {
							return;
						}
						event.preventDefault();
						add(typed);
					}}
				/>
			</TextField>
			{names.length > 0 ? (
				<TagGroup
					aria-label={m.tags_chosen()}
					onRemove={(keys) => onChange(names.filter((name) => !keys.has(name)))}
				>
					<TagGroup.List items={names.map((name) => ({ id: name }))}>
						{(tag) => (
							<Tag>
								{String(tag.id)}
								<Tag.RemoveButton />
							</Tag>
						)}
					</TagGroup.List>
				</TagGroup>
			) : null}
			{/* Last, and outlined: a suggestion appears and goes as the user types, so it moves nothing
          above it, and a chip that reads as flat text is one nobody presses. */}
			{offered.length > 0 ? (
				<ul className="flex flex-wrap gap-2">
					{offered.map((name) => (
						<li key={name}>
							<Button size="sm" variant="outline" onPress={() => add(name)}>
								{name}
							</Button>
						</li>
					))}
				</ul>
			) : null}
		</div>
	);
}

/** The boards the pin is filed under. The list arrives whole, so there is no page to chase. */
function BoardField({
	ids,
	onChange,
}: {
	ids: readonly string[];
	onChange: (ids: readonly string[]) => void;
}) {
	const boards = useBoards();

	return (
		<Select
			selectionMode="multiple"
			variant="secondary"
			placeholder={m.boards_none()}
			value={ids}
			onChange={(chosen) => onChange(chosen.map(String))}
		>
			<Label>{m.boards()}</Label>
			<Select.Trigger>
				<Select.Value />
				<Select.Indicator />
			</Select.Trigger>
			<Select.Popover className="min-w-44">
				<ListBox aria-label={m.boards()} items={boards.data ?? []}>
					{(held) => (
						<ListBox.Item id={held.id} textValue={held.name}>
							{held.name}
							<ListBox.ItemIndicator />
						</ListBox.Item>
					)}
				</ListBox>
			</Select.Popover>
		</Select>
	);
}

/** What the save does to the image, one of three and applied after the pin is written. */
type ImageIntent = "keep" | "replace" | "fetch";

/** The file about to replace the image, drawn in its place. */
function FilePreview({ file }: { file: File }) {
	const [preview, setPreview] = useState<string | null>(null);

	// Mounted only while a file is chosen, so this revokes the URL on Cancel, on save and on Remove.
	useEffect(() => {
		const drawn = URL.createObjectURL(file);
		setPreview(drawn);
		return () => URL.revokeObjectURL(drawn);
	}, [file]);

	// A file not sent yet is not the pin, so it takes no name of the pin's.
	return preview ? (
		<img
			src={preview}
			alt=""
			className="min-h-0 w-full flex-1 object-contain"
		/>
	) : null;
}

/** Either choice with nothing to apply would save as though the image were being kept. */
function isIncomplete(
	intent: ImageIntent,
	chosen: KeptFile | null,
	address: string,
): boolean {
	return (
		(intent === "replace" && chosen === null) ||
		(intent === "fetch" && address.trim() === "")
	);
}

/**
 * What edits a pin is a set of fields in the column, and one choice made on the image
 * (specification 2026-09-27, decisions G and H). Every field is sent on every save: the route
 * replaces the pin, and an empty list clears.
 */
export function PinEditForm({
	pin,
	image,
	close,
}: {
	pin: Pin;
	image: ReactNode;
	close: () => void;
}) {
	const save = useUpdatePin();
	const setImage = useSetPinImage();
	const limits = useHandshake().data?.limits;
	const heading = useId();
	const form = useId();
	const [tags, setTags] = useState<readonly string[]>(
		pin.tags.map((tag) => tag.name),
	);
	const [boardIds, setBoardIds] = useState<readonly string[]>(
		pin.boards.map((board) => board.id),
	);
	// One value in one field at a time: the column's, or the selector's once the image is fetched from it.
	const [address, setAddress] = useState(pin.sourceMediaUrl ?? "");
	const [intent, setIntent] = useState<ImageIntent>("keep");
	const [chosen, setChosen] = useState<KeptFile | null>(null);
	const replacement = pin.image?.replacement;
	const addressField = (
		<TextField
			type="url"
			value={address}
			onChange={setAddress}
			variant="secondary"
		>
			<Label>{m.image_address()}</Label>
			{/* The form's wherever it sits, so Enter saves from the image side too. */}
			<Input form={form} />
		</TextField>
	);

	/** What the save applies to the image once the pin itself is written, or nothing. */
	function source(): ImageSource | null {
		if (intent === "replace" && chosen !== null) {
			return { file: chosen.file };
		}
		if (intent === "fetch") {
			return { url: address };
		}
		return null;
	}

	const incomplete = isIncomplete(intent, chosen, address);

	return (
		<PinSides
			image={
				<div className="flex h-full w-full flex-col items-center gap-3">
					<ToggleButtonGroup
						aria-label={m.image()}
						selectionMode="single"
						disallowEmptySelection
						selectedKeys={[intent]}
						onSelectionChange={(keys) => setIntent([...keys][0] as ImageIntent)}
					>
						<ToggleButton id="keep">{m.image_keep()}</ToggleButton>
						<ToggleButton id="replace">
							<ToggleButtonGroup.Separator />
							{m.image_from_file()}
						</ToggleButton>
						<ToggleButton id="fetch">
							<ToggleButtonGroup.Separator />
							{m.image_from_address()}
						</ToggleButton>
					</ToggleButtonGroup>
					{intent === "fetch" ? (
						<div className="w-full max-w-md">{addressField}</div>
					) : null}
					{intent === "replace" ? (
						<div className="flex min-h-0 w-full flex-1 flex-col items-center justify-center gap-2 text-center">
							{chosen === null ? (
								// A drop that also carries an address fills the address, as the creation screen does.
								<ImageDropBox
									limits={limits}
									className="min-h-48 w-full flex-1 justify-center"
									onDrop={(drop) => {
										if (drop.files[0] !== undefined) {
											setChosen(drop.files[0]);
										}
										if (drop.urls[0] !== undefined) {
											setAddress(drop.urls[0]);
										}
									}}
								/>
							) : (
								<>
									<FilePreview file={chosen.file} />
									<p>{chosen.file.name}</p>
									<p className="text-sm text-muted">{m.image_unsaved()}</p>
									<Button variant="secondary" onPress={() => setChosen(null)}>
										{m.remove()}
									</Button>
								</>
							)}
						</div>
					) : (
						// Its own size container, so the image fits in what the selector leaves of the side.
						<div className="flex min-h-0 w-full flex-1 items-center justify-center lg:[container-type:size]">
							{image}
						</div>
					)}
					{intent === "fetch" ? (
						<p className="text-sm text-muted">{m.image_fetched_on_save()}</p>
					) : null}
				</div>
			}
			column={
				<form
					id={form}
					aria-labelledby={heading}
					className="flex flex-1 flex-col gap-3"
					onSubmit={(event) => {
						event.preventDefault();
						const fields = new FormData(event.currentTarget);
						save.mutate(
							{
								pinId: pin.id,
								body: {
									description: String(fields.get("description")),
									// An address emptied is an address cleared, the replacement being total.
									sourceContextUrl:
										String(fields.get("sourceContextUrl")) || null,
									sourceMediaUrl: address || null,
									tags: [...tags],
									boardIds: [...boardIds],
								},
							},
							{
								// The pin first, then the option chosen: a fetch then reads the address the pin
								// holds rather than one the server has never seen. A refused pin touches no image.
								onSuccess: () => {
									const chosenSource = source();
									if (chosenSource === null) {
										close();
									} else {
										setImage.mutate(
											{ pinId: pin.id, source: chosenSource },
											{ onSuccess: close },
										);
									}
								},
							},
						);
					}}
				>
					<h2 id={heading} className="text-lg font-medium">
						{m.pin_editing()}
					</h2>
					{/* The sub-state the contract has carried since before any client read it: the pin keeps
              the image it has while the server downloads the one asked for. */}
					{replacement?.status === "PENDING" ? (
						<p role="status">{m.image_replacing()}</p>
					) : null}
					{replacement?.status === "FAILED" ? (
						<p role="alert">
							{downloadReason(replacement.reasonCode, replacement.message)}
						</p>
					) : null}
					{/* `secondary` on every control: the default variant takes the dialog's own colour in the dark theme. */}
					<TextField
						name="description"
						defaultValue={pin.description}
						variant="secondary"
					>
						<Label>{m.description()}</Label>
						<TextArea rows={3} />
					</TextField>
					<TextField
						name="sourceContextUrl"
						type="url"
						defaultValue={pin.sourceContextUrl ?? ""}
						variant="secondary"
					>
						<Label>{m.source_page()}</Label>
						<Input />
					</TextField>
					{/* Not a ternary: `noNegationElse` inverts it, and `noLeakedRender` refuses a variable as the else. */}
					{intent !== "fetch" && addressField}
					<TagField names={tags} onChange={setTags} />
					<BoardField ids={boardIds} onChange={setBoardIds} />
					{/* Two halves, two sentences: the pin is written before its image, so a refused image
              leaves the fields saved and only the image to try again. */}
					{save.isError ? <p role="alert">{m.pin_refused()}</p> : null}
					{setImage.isError ? <p role="alert">{m.image_refused()}</p> : null}
					{/* At the column's foot while the fields scroll above it. */}
					<div className="sticky bottom-0 mt-auto flex justify-end gap-2 bg-overlay py-2">
						<Button variant="ghost" onPress={close}>
							{m.cancel()}
						</Button>
						<Button
							type="submit"
							isDisabled={save.isPending || setImage.isPending || incomplete}
						>
							{m.save()}
						</Button>
					</div>
				</form>
			}
		/>
	);
}
