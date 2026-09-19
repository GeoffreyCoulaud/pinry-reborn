import type { ReactNode } from "react"
import { ThemeSwitch } from "./ThemeSwitch"

/** The theme is the only control every screen carries, so the bar renders it and not its caller. */
export function AppHeader({ heading, children }: { heading: string; children?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-center justify-between gap-3 border-b border-separator pb-3">
      <h1 className="text-2xl font-semibold">{heading}</h1>
      <div className="flex flex-wrap items-center gap-2">
        {children}
        <ThemeSwitch />
      </div>
    </header>
  )
}
