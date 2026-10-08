import {
	Button,
	Description,
	InputGroup,
	Label,
	Tag,
	TagGroup,
	TextField,
} from "@heroui/react";
import { useState } from "react";
import { useDebounced } from "../debounce";
import { m } from "../paraglide/messages.js";

/** Chosen values as chips inside the field; suggestions once the typing pauses (specification 2026-09-21, decision P). */
export function ChipField<T>({
	label,
	chosenLabel,
	values,
	onChange,
	search,
	fromTyped,
	keyOf,
	labelOf,
}: {
	label: string;
	chosenLabel: string;
	values: readonly T[];
	onChange: (values: readonly T[]) => void;
	search: (asked: string) => { data?: readonly T[] };
	fromTyped: (typed: string) => T;
	keyOf: (value: T) => string;
	labelOf: (value: T) => string;
}) {
	const [typed, setTyped] = useState("");
	const asked = useDebounced(typed);
	const keys = values.map(keyOf);
	const offered = (search(asked).data ?? []).filter(
		(value) => !keys.includes(keyOf(value)),
	);

	function add(value: T) {
		if (!keys.includes(keyOf(value))) {
			onChange([...values, value]);
		}
		setTyped("");
	}

	return (
		<div className="flex flex-col gap-2">
			<TextField value={typed} onChange={setTyped} variant="secondary">
				<Label>{label}</Label>
				<InputGroup fullWidth className="flex-wrap gap-1 p-1">
					{values.length > 0 ? (
						// The default takes the field's own colour: the surface stands a chip out of it.
						<TagGroup
							variant="surface"
							aria-label={chosenLabel}
							onRemove={(removed) =>
								onChange(values.filter((value) => !removed.has(keyOf(value))))
							}
						>
							<TagGroup.List
								items={values.map((value) => ({ id: keyOf(value), value }))}
							>
								{(chosen) => (
									<Tag>
										{labelOf(chosen.value)}
										<Tag.RemoveButton />
									</Tag>
								)}
							</TagGroup.List>
						</TagGroup>
					) : null}
					<InputGroup.Input
						className="min-w-24 px-2 py-1"
						onKeyDown={(event) => {
							// Inside the pin's form, where Enter would save it: here it adds a value.
							if (event.key !== "Enter") {
								return;
							}
							event.preventDefault();
							const held = typed.trim();
							if (held !== "") {
								add(fromTyped(held));
							}
						}}
					/>
				</InputGroup>
				<Description>{m.enter_to_add()}</Description>
			</TextField>
			{/* Last, so a suggestion coming and going moves nothing; outlined, so it reads as pressable. */}
			{offered.length > 0 ? (
				<ul className="flex flex-wrap gap-2">
					{offered.map((value) => (
						<li key={keyOf(value)}>
							<Button size="sm" variant="outline" onPress={() => add(value)}>
								{labelOf(value)}
							</Button>
						</li>
					))}
				</ul>
			) : null}
		</div>
	);
}
