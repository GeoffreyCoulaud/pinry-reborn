/** What happened to a drop target, as the three events a drag over it produces. */
export type DragStep = "enter" | "leave" | "drop"

/**
 * The target's depth after `step`, dragged over when it is above zero. `dragenter` and `dragleave`
 * both bubble, so only counting tells a crossing into a child from a real exit (MDN, `dragleave`).
 */
export function dragDepth(depth: number, step: DragStep): number {
  if (step === "drop") return 0
  if (step === "enter") return depth + 1
  return Math.max(0, depth - 1)
}
