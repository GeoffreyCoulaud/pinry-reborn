import { Label, ListBox, Select } from "@heroui/react"
import { THEME_PREFERENCES, type ThemePreference } from "../lib/theme"
import { m } from "../paraglide/messages.js"
import { useTheme } from "../theme"

const LABELS: Record<ThemePreference, () => string> = {
  system: m.theme_system,
  light: m.theme_light,
  dark: m.theme_dark,
}

export function ThemeSwitch() {
  const theme = useTheme()

  return (
    <Select
      className="w-40"
      value={theme.preference}
      onChange={(value) => theme.choose(value as ThemePreference)}
    >
      <Label>{m.theme()}</Label>
      <Select.Trigger>
        <Select.Value />
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover>
        <ListBox>
          {THEME_PREFERENCES.map((preference) => (
            <ListBox.Item key={preference} id={preference} textValue={LABELS[preference]()}>
              {LABELS[preference]()}
              <ListBox.ItemIndicator />
            </ListBox.Item>
          ))}
        </ListBox>
      </Select.Popover>
    </Select>
  )
}
