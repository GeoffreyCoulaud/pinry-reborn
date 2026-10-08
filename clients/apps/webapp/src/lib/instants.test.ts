import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { dayAndTimeOf, instantOf } from "./instants";

describe("a day and a time read in the browser's zone", () => {
	// Restored after: a worker runs several test files in one process.
	beforeAll(() => {
		vi.stubEnv("TZ", "Europe/Paris");
	});
	afterAll(() => {
		vi.unstubAllEnvs();
	});

	it("Given a January day at 00:00 in Paris, Then the instant is the previous day at 23:00 UTC", () => {
		expect(instantOf("2026-01-15", "00:00")).toBe("2026-01-14T23:00:00.000Z");
	});

	it("Given a July day at 00:00 in Paris, Then the instant is the previous day at 22:00 UTC", () => {
		expect(instantOf("2026-07-15", "00:00")).toBe("2026-07-14T22:00:00.000Z");
	});

	it("Given a day with no time, Then the instant is that day at 00:00", () => {
		expect(instantOf("2026-07-15", "")).toBe("2026-07-14T22:00:00.000Z");
	});

	it("Given a five-digit year a date field accepts, Then there is no instant", () => {
		expect(instantOf("27576-01-01", "00:00")).toBeNull();
	});

	it("Given an instant read back, Then it gives the same day and time", () => {
		expect(dayAndTimeOf(instantOf("2026-03-05", "09:07") ?? "")).toEqual({
			day: "2026-03-05",
			time: "09:07",
		});
	});

	it("Given an instant late in the UTC day, Then it reads as the next day in Paris", () => {
		expect(dayAndTimeOf("2026-07-14T22:30:00Z")).toEqual({
			day: "2026-07-15",
			time: "00:30",
		});
	});
});
