import { type RefObject, useLayoutEffect, useState } from "react";

/** The column's width, which the layout gives the item and only a browser can measure. */
export function useColumnWidth(ref: RefObject<HTMLElement | null>): number {
	const [width, setWidth] = useState(0);
	useLayoutEffect(() => {
		const measured = ref.current?.clientWidth ?? 0;
		if (measured !== width) {
			setWidth(measured);
		}
	});
	return width;
}
