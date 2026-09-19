import { Button, Tooltip, type ButtonProps } from "@heroui/react"
import type { LucideIcon } from "lucide-react"

/**
 * An icon alone is not discoverable, and a tooltip worded differently from the accessible name
 * gives a sighted user and a screen reader two words for one control (decision D). One string
 * here is what holds them together at every site.
 */
export function IconButton({
  icon: Icon,
  name,
  ...props
}: { icon: LucideIcon; name: string } & ButtonProps) {
  return (
    <Tooltip>
      <Button isIconOnly aria-label={name} {...props}>
        <Icon aria-hidden />
      </Button>
      <Tooltip.Content>{name}</Tooltip.Content>
    </Tooltip>
  )
}
