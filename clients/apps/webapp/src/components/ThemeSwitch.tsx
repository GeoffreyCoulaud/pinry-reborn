import { Button, Tooltip } from "@heroui/react"
import { Monitor, Moon, Sun, type LucideIcon } from "lucide-react"
import { nextPreference, type ThemePreference } from "../lib/theme"
import { m } from "../paraglide/messages.js"
import { useTheme } from "../theme"

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
  const Icon = ICONS[theme.preference]
  const label = m.theme_current({ theme: LABELS[theme.preference]() })

  return (
    <Tooltip>
      <Button
        variant="ghost"
        isIconOnly
        aria-label={label}
        onPress={() => theme.choose(nextPreference(theme.preference))}
      >
        <Icon aria-hidden />
      </Button>
      <Tooltip.Content>{label}</Tooltip.Content>
    </Tooltip>
  )
}
