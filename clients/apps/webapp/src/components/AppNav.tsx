import { Focusable, Tooltip, buttonVariants } from "@heroui/react"
import { Link, type LinkProps } from "@tanstack/react-router"
import { LayoutGrid, LogOut, Trash2, UserRound, type LucideIcon } from "lucide-react"
import { m } from "../paraglide/messages.js"
import { useSignOut } from "../session"
import { IconButton } from "./IconButton"
import { TaskCentre } from "./TaskCentre"

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
 * What every signed-in screen's bar carries: the screens the user moves between, the downloads
 * still running, and the way out of the session. `AppHeader` renders it nowhere: the credentials
 * screen carries the same bar, and an icon put inside it would be offered to a visitor with no
 * session (specification 2026-09-20, decision I).
 */
export function AppNav() {
  const signOut = useSignOut()

  return (
    <>
      {/* No way home here: the bar's name is the one, and two controls doing one thing is chrome
          (specification 2026-09-21, decision J). */}
      <NavIcon to="/boards" icon={LayoutGrid} name={m.boards()} />
      <NavIcon to="/recycled" icon={Trash2} name={m.recycle_bin()} />
      <NavIcon to="/account" icon={UserRound} name={m.account()} />
      {/* A download outlives the screen it was started from, so what reports it is on all of them. */}
      <TaskCentre />
      <IconButton
        icon={LogOut}
        name={m.sign_out()}
        variant="ghost"
        onPress={() => signOut.mutate()}
      />
    </>
  )
}
