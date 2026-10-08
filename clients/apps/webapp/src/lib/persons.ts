import { hostOf } from "./uris";

/** A person as the API names one, by its name and its addresses (specification 2026-10-08, decision C). */
interface Person {
	name: string;
	urls: readonly string[];
}

/** The name, then the host of each address, so two homonyms read apart (decision G). */
export function personLabel(person: Person): string {
	return person.urls.length === 0
		? person.name
		: `${person.name} (${person.urls.map(hostOf).join(", ")})`;
}

/** One person chosen once: the name and the addresses together are what identifies it. */
export function personKey(person: Person): string {
	return JSON.stringify([person.name, person.urls]);
}
