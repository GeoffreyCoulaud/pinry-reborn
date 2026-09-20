import { Focusable, Tooltip, buttonVariants } from "@heroui/react"
import { Link, type LinkProps } from "@tanstack/react-router"
import { House, LayoutGrid, type LucideIcon } from "lucide-react"
import { m } from "../paraglide/messages.js"

/**
 * A link and not a button: the icon goes somewhere, so it opens in a new tab and answers a middle
 * click like any other address. `Focusable` is what lets the tooltip hang off a link the router
 * owns: react-aria passes its trigger's props through context, which a foreign element gets no
 * other way. The button's own variant carries the look the bar's other icons have.
 */
function NavIcon({ to, icon: Icon, name }: { to: LinkProps["to"]; icon: LucideIcon; name: string }) {
  return (
    <Tooltip>
      <Focusable>
        <Link
          to={to}
          aria-label={name}
          className={buttonVariants({ variant: "ghost", isIconOnly: true })}
        >
          <Icon aria-hidden />
        </Link>
      </Focusable>
      <Tooltip.Content>{name}</Tooltip.Content>
    </Tooltip>
  )
}

/**
 * The screens a signed-in user moves between. `AppHeader` renders it nowhere: the credentials
 * screen carries the same bar, and an icon put inside it would be offered to a visitor with no
 * session (specification decision I).
 */
export function AppNav() {
  return (
    <>
      <NavIcon to="/" icon={House} name={m.home_heading()} />
      <NavIcon to="/boards" icon={LayoutGrid} name={m.boards()} />
    </>
  )
}
