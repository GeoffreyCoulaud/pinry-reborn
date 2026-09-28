/**
 * The keys `expected` declares and `present` does not. Paraglide falls back to the base
 * locale silently, so this comparison is the only thing that catches an untranslated
 * message (docs/specs/2026-09-10-web-application.md, section 4.6).
 */
export function keysMissingFrom(present: object, expected: object): string[] {
	return Object.keys(expected)
		.filter((key) => !(key in present))
		.toSorted();
}
