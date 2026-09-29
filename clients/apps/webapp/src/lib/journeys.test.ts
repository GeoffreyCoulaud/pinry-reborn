import { describe, expect, it } from "vitest";
import { journeyTestFile, REQUIRED_JOURNEYS } from "./journeys";

/** Vite resolves the pattern at transform time, so a deleted test changes this list. */
const present = Object.keys(
	import.meta.glob("../journeys/*.journey.test.tsx"),
).map((path) => path.split("/").at(-1) ?? path);

describe("the journey list", () => {
	it("Given a journey's name, Then its test file is that name in kebab case", () => {
		expect(journeyTestFile("open the application")).toBe(
			"open-the-application.journey.test.tsx",
		);
	});

	it("Given the journey list, Then src/journeys holds one test per journey and no other", () => {
		const byName = (a: string, b: string) => a.localeCompare(b);
		expect(present.toSorted(byName)).toEqual(
			REQUIRED_JOURNEYS.map(journeyTestFile).toSorted(byName),
		);
	});
});
