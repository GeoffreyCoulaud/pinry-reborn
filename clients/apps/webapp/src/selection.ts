import { useState } from "react";
import type { Key, Selection } from "react-aria-components";

/**
 * The selection a `GridList` reports, held as the keys of the rows it still shows: a key left
 * behind by a row the gesture has just taken away would keep the bar up over nothing.
 */
export function useSelection(rows: readonly { id: string }[]) {
	const [selected, setSelected] = useState<Set<Key>>(new Set());

	return {
		ids: [...selected]
			.map(String)
			.filter((id) => rows.some((row) => row.id === id)),
		clear: () => setSelected(new Set()),
		props: {
			selectionMode: "multiple" as const,
			selectedKeys: selected,
			// `all` is the keyboard's select-all, resolved here against the rows loaded rather than
			// carried as a word that would silently take in every page loaded after it.
			onSelectionChange: (keys: Selection) =>
				setSelected(
					keys === "all" ? new Set(rows.map((row) => row.id)) : new Set(keys),
				),
		},
	};
}
