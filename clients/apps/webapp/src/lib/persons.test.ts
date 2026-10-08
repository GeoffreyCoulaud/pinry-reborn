import { describe, expect, it } from "vitest";
import { personKey, personLabel, personNamed } from "./persons";

describe("personLabel", () => {
	it("reads a person with no address as its name alone", () => {
		expect(personLabel({ name: "Ada", urls: [] })).toBe("Ada");
	});

	it("follows the name with the host of each address, so homonyms read apart", () => {
		expect(
			personLabel({
				name: "Ada",
				urls: ["https://art.example.test/ada", "https://ada.test/"],
			}),
		).toBe("Ada (art.example.test, ada.test)");
	});
});

describe("personNamed", () => {
	it("makes a person of a name alone, with no address", () => {
		expect(personNamed("Grace")).toEqual({ name: "Grace", urls: [] });
	});
});

describe("personKey", () => {
	it("tells apart two homonyms whose addresses differ", () => {
		expect(personKey({ name: "Ada", urls: ["https://ada.test/"] })).not.toBe(
			personKey({ name: "Ada", urls: [] }),
		);
	});

	it("gives one key to one name and one list of addresses", () => {
		expect(personKey({ name: "Ada", urls: ["https://ada.test/"] })).toBe(
			personKey({ name: "Ada", urls: ["https://ada.test/"] }),
		);
	});
});
