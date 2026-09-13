import { THEME_PREFERENCES, type ThemePreference } from "../lib/theme"
import { m } from "../paraglide/messages.js"
import { useTheme } from "../theme"

const LABELS: Record<ThemePreference, () => string> = {
  system: m.theme_system,
  light: m.theme_light,
  dark: m.theme_dark,
}

/**
 * Three states and not two: question T asked for light and dark following the system *with* a
 * manual switch, so the switch has to be able to hand the theme back.
 */
export function ThemeSwitch() {
  const theme = useTheme()

  return (
    <label className="flex items-center gap-1 text-sm">
      {m.theme()}
      <select
        value={theme.preference}
        onChange={(event) => theme.choose(event.currentTarget.value as ThemePreference)}
        className="rounded bg-current/10 px-2 py-1"
      >
        {THEME_PREFERENCES.map((preference) => (
          <option key={preference} value={preference}>
            {LABELS[preference]()}
          </option>
        ))}
      </select>
    </label>
  )
}
