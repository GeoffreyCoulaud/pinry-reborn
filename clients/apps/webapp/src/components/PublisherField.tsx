import { ComboBox, Input, Label, ListBox } from "@heroui/react";
import { X } from "lucide-react";
import { use, useEffect, useRef, useState } from "react";
import { ButtonContext, ComboBoxStateContext } from "react-aria-components";
import { useDebounced } from "../debounce";
import { personKey, personLabel } from "../lib/persons";
import { m } from "../paraglide/messages.js";
import { type Person, usePersonSearch } from "../pins";
import { IconButton } from "./IconButton";

/** A name entered is a person with no address: the form never edits a person's addresses (decision G). */
function entered(name: string): Person {
	return { name, urls: [] };
}

/** The combo box opens on typing alone, before the paused search answers: this opens it on the answer. */
function OpensOnResults({ results }: { results?: readonly Person[] }) {
	const state = use(ComboBoxStateContext);
	const shown = useRef(results);
	useEffect(() => {
		if (state?.isFocused && results?.length && shown.current !== results) {
			shown.current = results;
			state.open(null, "manual");
		}
	}, [state, results]);
	return null;
}

/**
 * The pin's publisher, one person: a suggestion chosen, or the name typed when none is. The text
 * is the value, so editing a chosen person's text makes it a name typed.
 */
export function PublisherField({
	publisher,
	onChange,
}: {
	publisher: Person | null;
	onChange: (publisher: Person | null) => void;
}) {
	const [chosen, setChosen] = useState(publisher);
	const [typed, setTyped] = useState(
		publisher === null ? "" : personLabel(publisher),
	);
	// A chosen person is not searched for: its label would only find itself.
	const asked = useDebounced(chosen === null ? typed : "");
	const found = usePersonSearch(asked);
	const offered = chosen === null ? (found.data ?? []) : [chosen];

	function settle(person: Person | null, text: string) {
		setChosen(person);
		setTyped(text);
		const name = text.trim();
		onChange(person ?? (name === "" ? null : entered(name)));
	}

	return (
		<ComboBox
			allowsCustomValue
			menuTrigger="input"
			variant="secondary"
			fullWidth
			items={offered}
			inputValue={typed}
			selectedKey={chosen === null ? null : personKey(chosen)}
			onInputChange={(text) =>
				settle(
					chosen !== null && text === personLabel(chosen) ? chosen : null,
					text,
				)
			}
			onSelectionChange={(key) => {
				const person = offered.find((held) => personKey(held) === key);
				if (person !== undefined) {
					settle(person, personLabel(person));
				}
			}}
		>
			<Label>{m.publisher()}</Label>
			<OpensOnResults results={chosen === null ? found.data : undefined} />
			{/* Beside the group, which takes its last child for the menu's trigger. */}
			<div className="flex items-center gap-1">
				<ComboBox.InputGroup className="flex-1">
					<Input
						onKeyDown={(event) => {
							// The text is already the value: Enter here does not save the pin.
							if (event.key === "Enter") {
								event.preventDefault();
							}
						}}
					/>
				</ComboBox.InputGroup>
				{/* Out of the combo box's button context, which would make it the menu's trigger. */}
				{typed === "" ? null : (
					<ButtonContext.Provider value={null}>
						<IconButton
							icon={X}
							name={m.publisher_clear()}
							variant="ghost"
							onPress={() => settle(null, "")}
						/>
					</ButtonContext.Provider>
				)}
			</div>
			{/* The popover takes the dialog's own colour in the dark theme: a step up stands it out. */}
			<ComboBox.Popover className="dark:bg-surface-tertiary">
				<ListBox>
					{(person: Person) => (
						<ListBox.Item
							id={personKey(person)}
							textValue={personLabel(person)}
						>
							{personLabel(person)}
						</ListBox.Item>
					)}
				</ListBox>
			</ComboBox.Popover>
		</ComboBox>
	);
}
