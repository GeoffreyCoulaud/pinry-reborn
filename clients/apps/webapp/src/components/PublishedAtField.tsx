import { Input, Label, TextField } from "@heroui/react";
import { X } from "lucide-react";
import { dayAndTimeOf, instantOf } from "../lib/instants";
import { m } from "../paraglide/messages.js";
import { IconButton } from "./IconButton";

/** The pin's publication instant, as a day and a time in the browser's zone (specification 2026-10-08, decision H). */
export function PublishedAtField({
	instant,
	onChange,
}: {
	instant: string | null;
	onChange: (instant: string | null) => void;
}) {
	const { day, time } =
		instant === null ? { day: "", time: "" } : dayAndTimeOf(instant);
	// A day with no instant changes nothing, so the field keeps showing the pin's date.
	const choose = (chosenDay: string, chosenTime: string) => {
		const chosen = instantOf(chosenDay, chosenTime);
		if (chosen !== null) {
			onChange(chosen);
		}
	};

	return (
		<div className="flex flex-wrap items-end gap-2">
			<TextField
				type="date"
				value={day}
				onChange={(chosen) =>
					chosen === "" ? onChange(null) : choose(chosen, time)
				}
				variant="secondary"
			>
				<Label>{m.published_day()}</Label>
				<Input />
			</TextField>
			{/* One piece, so a phone wraps the clear button with the time rather than alone. */}
			<div className="flex items-end gap-2">
				<TextField
					type="time"
					value={time}
					isDisabled={day === ""}
					onChange={(chosen) => choose(day, chosen)}
					variant="secondary"
				>
					<Label>{m.published_time()}</Label>
					<Input />
				</TextField>
				{instant === null ? null : (
					<IconButton
						icon={X}
						name={m.published_clear()}
						variant="ghost"
						onPress={() => onChange(null)}
					/>
				)}
			</div>
			<p className="w-full text-sm text-muted">
				{m.published_zone({
					zone: Intl.DateTimeFormat().resolvedOptions().timeZone,
				})}
			</p>
		</div>
	);
}
