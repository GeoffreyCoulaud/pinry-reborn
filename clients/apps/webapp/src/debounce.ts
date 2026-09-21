import { useEffect, useState } from "react"

/** The pause a field waits out before it asks the API (specification 2026-09-21, decision K). */
const PAUSE_MS = 300

/**
 * The value as it stood once it had held still for `delay` milliseconds. A field that asks the API
 * for what it holds asks once per pause rather than once per keystroke (specification 2026-09-21,
 * decision P). It holds a timer and state, so it lives here and not in `lib/`.
 */
export function useDebounced<T>(value: T, delay: number = PAUSE_MS): T {
  const [settled, setSettled] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])

  return settled
}
