import { Link } from "@tanstack/react-router"
import type { ReactNode } from "react"
import { m } from "../paraglide/messages.js"
import { ThemeSwitch } from "./ThemeSwitch"

/**
 * The theme is the only control every screen carries, so the bar renders it and not its caller.
 * The name is every screen's `<h1>` and the way home, the house icon having left the navigation
 * (specification 2026-09-21, decisions I and J); a screen that is not the home one adds its own
 * title beside it.
 */
export function AppHeader({ heading, children }: { heading?: string; children?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-center justify-between gap-3 border-b border-separator pb-3">
      <div className="flex flex-wrap items-baseline gap-2">
        <h1 className="text-2xl font-semibold">
          <Link to="/">{m.app_name()}</Link>
        </h1>
        {heading !== undefined && (
          <>
            <span aria-hidden className="text-2xl text-muted">
              ·
            </span>
            <h2 className="text-2xl font-semibold">{heading}</h2>
          </>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {children}
        <ThemeSwitch />
      </div>
    </header>
  )
}
