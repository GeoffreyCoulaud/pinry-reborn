/** Two digits, as a date or time field writes a month, a day, an hour or a minute. */
function twoDigits(value: number): string {
	return String(value).padStart(2, "0");
}

/**
 * The instant of a date field's day and a time field's time, read in the browser's zone; no time
 * is 00:00 (specification 2026-10-08, decision H).
 */
export function instantOf(day: string, time: string): string {
	// A date and a time with no offset are local time to `Date`, where a date alone would be UTC.
	return new Date(`${day}T${time || "00:00"}`).toISOString();
}

/** The day and the time a date field and a time field show for an instant, in the browser's zone. */
export function dayAndTimeOf(instant: string): { day: string; time: string } {
	const at = new Date(instant);
	const year = String(at.getFullYear()).padStart(4, "0");
	return {
		day: `${year}-${twoDigits(at.getMonth() + 1)}-${twoDigits(at.getDate())}`,
		time: `${twoDigits(at.getHours())}:${twoDigits(at.getMinutes())}`,
	};
}
