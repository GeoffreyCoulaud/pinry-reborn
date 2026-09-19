import { Monitor, Moon, Sun, type LucideIcon } from "lucide-react"
import { nextPreference, type ThemePreference } from "../lib/theme"
import { m } from "../paraglide/messages.js"
import { useTheme } from "../theme"
import { IconButton } from "./IconButton"

const ICONS: Record<ThemePreference, LucideIcon> = {
  system: Monitor,
  light: Sun,
  dark: Moon,
}

const LABELS: Record<ThemePreference, () => string> = {
  system: m.theme_system,
  light: m.theme_light,
  dark: m.theme_dark,
}

/** One button for three preferences: with no title and no list, its name says where the user is. */
export function ThemeSwitch() {
  const theme = useTheme()

  return (
    <IconButton
      icon={ICONS[theme.preference]}
      name={m.theme_current({ theme: LABELS[theme.preference]() })}
      variant="ghost"
      onPress={() => theme.choose(nextPreference(theme.preference))}
    />
  )
}
