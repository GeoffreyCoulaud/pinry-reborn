import { useEffect, useState } from "react"
import { readPreference, resolveTheme, type ThemePreference } from "./lib/theme"

const KEY = "pinry-theme"
const DARK = "(prefers-color-scheme: dark)"

/** A private window, or site data the browser blocks, throws rather than answering. */
function stored(): ThemePreference {
  try {
    return readPreference(localStorage.getItem(KEY))
  } catch {
    return "system"
  }
}

/** The resolved theme and not the preference, so the stylesheet never reads the machine itself. */
function paint(preference: ThemePreference): void {
  document.documentElement.dataset.theme = resolveTheme(
    preference,
    window.matchMedia(DARK).matches,
  )
}

/** Called before the first render: a chosen theme otherwise costs a flash of the system's own. */
export function paintStoredTheme(): void {
  paint(stored())
}

export function useTheme() {
  const [preference, setPreference] = useState(stored)

  // The machine's own theme still moves under a `system` preference, and only a listener sees it.
  useEffect(() => {
    paint(preference)
    const media = window.matchMedia(DARK)
    const follow = () => paint(preference)
    media.addEventListener("change", follow)
    return () => media.removeEventListener("change", follow)
  }, [preference])

  function choose(next: ThemePreference): void {
    try {
      localStorage.setItem(KEY, next)
    } catch {
      // A preference that cannot be stored still holds for as long as this page lives.
    }
    setPreference(next)
  }

  return { preference, choose }
}
