/** What happened to a drop target, as the three events a drag over it produces. */
export type DragStep = "enter" | "leave" | "drop"

/**
 * The target's depth after `step`, dragged over when it is above zero.
 *
 * `dragenter` and `dragleave` both bubble, so moving from a container onto its own child fires a
 * `dragleave` at the container the pointer never left (MDN, `dragleave` event). Counting is what
 * tells that from a real exit; comparing the event's target against its currentTarget is not.
 */
export function dragDepth(depth: number, step: DragStep): number {
  if (step === "drop") return 0
  if (step === "enter") return depth + 1
  return Math.max(0, depth - 1)
}
