import type { Schemas } from "@pinry-reborn/auth";

export type Decision = Schemas["DuplicateDecisionInputEnum"];
export type Decisions = Readonly<Record<string, Decision>>;

/** What the comparator reads of a version. */
interface Version {
	id: string;
	createdAt: string;
	media?: { width?: number | null; height?: number | null } | null;
}

/** A media the API never measured counts none, so it never takes the kept place. */
export function pixelsOf(version: Version): number {
	return (version.media?.width ?? 0) * (version.media?.height ?? 0);
}

/**
 * The stored state as decisions: the most pixels among the open pin and the pending candidates
 * kept, the oldest on a tie; the other pending ones merged, the rejected ones rejected.
 */
export function storedDecisions(
	open: Version,
	duplicates: readonly { pin: Version; rejected: boolean }[],
): Decisions {
	const pending = duplicates.filter((one) => !one.rejected);
	const kept = [open, ...pending.map((one) => one.pin)].reduce((best, one) => {
		const more = pixelsOf(one) - pixelsOf(best);
		return more > 0 ||
			(more === 0 && Date.parse(one.createdAt) < Date.parse(best.createdAt))
			? one
			: best;
	});
	const decisions: Record<string, Decision> = { [open.id]: "MERGE" };
	for (const { pin, rejected } of duplicates) {
		decisions[pin.id] = rejected ? "REJECT" : "MERGE";
	}
	decisions[kept.id] = "KEEP";
	return decisions;
}

/** One version's decision; keeping it hands the previously kept one to the merge. */
export function decide(
	decisions: Decisions,
	id: string,
	decision: Decision,
): Decisions {
	const next = { ...decisions, [id]: decision };
	if (decision === "KEEP") {
		for (const [other, held] of Object.entries(decisions)) {
			if (other !== id && held === "KEEP") {
				next[other] = "MERGE";
			}
		}
	}
	return next;
}

/** What the submit sends, by its label; `null` when it is disabled. */
export type Submit = { kind: "MERGE" | "REJECT"; count: number } | null;

/**
 * "Merge N pins into one" while N - 1 versions are merged, "Reject N duplicates" while none is and
 * N pending candidates are rejected, disabled otherwise.
 */
export function submitOf(
	decisions: Decisions,
	duplicates: readonly { pin: { id: string }; rejected: boolean }[],
): Submit {
	const merged = Object.values(decisions).filter((one) => one === "MERGE");
	if (merged.length > 0) {
		return { kind: "MERGE", count: merged.length + 1 };
	}
	const rejected = duplicates.filter(
		(one) => !one.rejected && decisions[one.pin.id] === "REJECT",
	);
	return rejected.length > 0
		? { kind: "REJECT", count: rejected.length }
		: null;
}
