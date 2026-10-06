import { describe, expect, it } from "vitest";
import { decide, storedDecisions, submitOf } from "./duplicates";

/** A version made at `second` past a fixed instant, at its dimensions. */
const version = (id: string, width: number, height: number, second = 0) => ({
	id,
	createdAt: new Date(Date.UTC(2026, 0, 1, 0, 0, second)).toISOString(),
	media: { width, height },
});

type Version = Parameters<typeof storedDecisions>[0];
const pending = (pin: Version) => ({ pin, rejected: false });
const rejected = (pin: Version) => ({ pin, rejected: true });

describe("the kept version", () => {
	it("Given a pending candidate with more pixels than the open pin, Then it is kept and the open pin merged", () => {
		const open = version("open", 800, 600);
		const larger = version("larger", 1600, 1200);
		const smaller = version("smaller", 400, 300);

		expect(storedDecisions(open, [pending(larger), pending(smaller)])).toEqual({
			open: "MERGE",
			larger: "KEEP",
			smaller: "MERGE",
		});
	});

	it("Given two versions of as many pixels, Then the older one is kept", () => {
		const open = version("open", 800, 600, 5);
		const older = version("older", 600, 800, 1);
		const younger = version("younger", 800, 600, 9);

		expect(
			storedDecisions(open, [pending(younger), pending(older)]),
		).toMatchObject({ older: "KEEP", open: "MERGE", younger: "MERGE" });
	});

	it("Given a larger rejected candidate, Then it stays rejected and the open pin is kept", () => {
		const open = version("open", 800, 600);
		const larger = version("larger", 1600, 1200);

		expect(storedDecisions(open, [rejected(larger)])).toEqual({
			open: "KEEP",
			larger: "REJECT",
		});
	});

	it("Given older candidates the API never measured, Then the measured open pin is kept", () => {
		const open = version("open", 1, 1, 5);
		const older = new Date(Date.UTC(2026, 0, 1)).toISOString();
		const absent = { id: "absent", createdAt: older };
		const bare = { id: "bare", createdAt: older, media: null };

		expect(storedDecisions(open, [pending(absent), pending(bare)])).toEqual({
			open: "KEEP",
			absent: "MERGE",
			bare: "MERGE",
		});
	});
});

describe("a decision", () => {
	it("Given another version kept, Then the previously kept one is merged", () => {
		const before = { open: "KEEP", other: "REJECT", third: "MERGE" } as const;

		expect(decide(before, "other", "KEEP")).toEqual({
			open: "MERGE",
			other: "KEEP",
			third: "MERGE",
		});
	});

	it("Given a version rejected, Then the kept one stays kept", () => {
		const before = { open: "KEEP", other: "MERGE" } as const;

		expect(decide(before, "other", "REJECT")).toEqual({
			open: "KEEP",
			other: "REJECT",
		});
	});
});

describe("the submit", () => {
	const open = version("open", 800, 600);
	const first = version("first", 400, 300);
	const second = version("second", 400, 300);

	it("Given a pending candidate, Then the stored state opens on a merge of both pins", () => {
		const group = [pending(first)];

		expect(submitOf(storedDecisions(open, group), group)).toEqual({
			kind: "MERGE",
			count: 2,
		});
	});

	it("Given every candidate rejected, Then the stored state opens disabled", () => {
		const group = [rejected(first), rejected(second)];

		expect(submitOf(storedDecisions(open, group), group)).toBeNull();
	});

	it("Given no version merged and one pending candidate rejected, Then it rejects that one alone", () => {
		const group = [pending(first), rejected(second)];
		const decisions = {
			open: "KEEP",
			first: "REJECT",
			second: "REJECT",
		} as const;

		expect(submitOf(decisions, group)).toEqual({ kind: "REJECT", count: 1 });
	});

	it("Given a rejected candidate merged under Review, Then it merges both pins", () => {
		const group = [rejected(first)];

		expect(submitOf({ open: "KEEP", first: "MERGE" }, group)).toEqual({
			kind: "MERGE",
			count: 2,
		});
	});
});
