import { useEffect } from "react";

/**
 * `←` and `→` on the document: the dialog holds the focus once open, and passes on no key handler.
 * A video's own arrows seek it, and a control that handled the key first keeps it.
 */
export function useArrowKeys(previous?: () => void, next?: () => void) {
	useEffect(() => {
		const step = (event: KeyboardEvent) => {
			if (
				event.altKey ||
				event.ctrlKey ||
				event.metaKey ||
				event.shiftKey ||
				event.defaultPrevented ||
				event.target instanceof HTMLMediaElement
			) {
				return;
			}
			if (event.key === "ArrowLeft") {
				previous?.();
			}
			if (event.key === "ArrowRight") {
				next?.();
			}
		};
		document.addEventListener("keydown", step);
		return () => document.removeEventListener("keydown", step);
	}, [previous, next]);
}
