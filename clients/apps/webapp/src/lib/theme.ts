/** What the user chose. `system` is the default and the only one that follows the machine. */
export type ThemePreference = "system" | "light" | "dark"

/** What the page is painted in, once a preference has met the system's own. */
export type Theme = "light" | "dark"

/** A tuple rather than an array, so the cycle below reads its first entry without a null check. */
export const THEME_PREFERENCES = ["system", "light", "dark"] as const satisfies readonly ThemePreference[]

/** The preference after `current`, wrapping past the last. */
export function nextPreference(current: ThemePreference): ThemePreference {
  return THEME_PREFERENCES[THEME_PREFERENCES.indexOf(current) + 1] ?? THEME_PREFERENCES[0]
}

/** Anything this version does not know reads as `system`, a value an older one wrote included. */
export function readPreference(stored: string | null): ThemePreference {
  const known = THEME_PREFERENCES.find((preference) => preference === stored)
  return known ?? "system"
}

export function resolveTheme(preference: ThemePreference, systemPrefersDark: boolean): Theme {
  if (preference !== "system") return preference
  return systemPrefersDark ? "dark" : "light"
}
