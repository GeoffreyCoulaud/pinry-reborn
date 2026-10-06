import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { closeDroppedAnimations } from "../motions";

describe("an animated image's decoder", () => {
	it("Given an animated image, a still one and another query in the cache, Then dropping them closes the animated image's decoder alone", () => {
		const client = new QueryClient();
		closeDroppedAnimations(client);
		const close = vi.fn();
		client.setQueryData(["animation", "/gif"], { decoder: { close } });
		client.setQueryData(["animation", "/jpeg"], null);
		client.setQueryData(["pins"], { decoder: { close } });

		client.removeQueries({ queryKey: ["pins"] });
		expect(close).not.toHaveBeenCalled();
		client.removeQueries({ queryKey: ["animation"] });

		expect(close).toHaveBeenCalledOnce();
	});
});
