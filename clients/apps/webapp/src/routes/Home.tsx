import { Navigate, useSearch } from "@tanstack/react-router"
import { Plus } from "lucide-react"
import { useEffect, useRef, useState } from "react"
import { AppHeader } from "../components/AppHeader"
import { AppNav } from "../components/AppNav"
import { CreatePinDialog } from "../components/CreatePinDialog"
import { IconButton } from "../components/IconButton"
import { PinGrid } from "../components/PinGrid"
import { SearchField } from "../components/SearchField"
import { SortSelect } from "../components/SortSelect"
import { judgeDrop, refuse } from "../drops"
import { useHandshake } from "../images"
import { dragDepth, type DragStep } from "../lib/drags"
import type { DropPartition } from "../lib/drops"
import { PIN_SORTS } from "../lib/sorts"
import { m } from "../paraglide/messages.js"
import { useSession } from "../session"

export function Home() {
  const { sort, q } = useSearch({ from: "/" })
  const session = useSession()
  const limits = useHandshake().data?.limits
  const [creating, setCreating] = useState(false)
  const [dropped, setDropped] = useState<DropPartition | null>(null)
  const [depth, setDepth] = useState(0)
  // Read inside the drop rather than closed over: the handshake landing mid-drag would otherwise
  // resubscribe the listeners below and blink the overlay off under the pointer.
  const limitsRef = useRef(limits)
  useEffect(() => {
    limitsRef.current = limits
  }, [limits])

  /**
   * Cancelling is unconditional, and the counting below is not: a drop the browser is left to
   * handle navigates to the file, which while the dialog is open would take the whole queue with it.
   */
  useEffect(() => {
    const cancel = (event: DragEvent) => event.preventDefault()
    window.addEventListener("dragover", cancel)
    window.addEventListener("drop", cancel)
    return () => {
      window.removeEventListener("dragover", cancel)
      window.removeEventListener("drop", cancel)
    }
  }, [])

  /**
   * The target is the window and not an element. `<main>` is one screen tall, so a drop past its
   * box lands on nothing, which is what Firefox was seen doing. While the dialog is open it owns
   * the gesture, and nothing here counts or judges at all.
   */
  useEffect(() => {
    if (creating) return
    const dragged = (step: DragStep) => setDepth((current) => dragDepth(current, step))
    const enter = () => dragged("enter")
    const leave = () => dragged("leave")
    const drop = (event: DragEvent) => {
      dragged("drop")
      const transfer = event.dataTransfer
      if (transfer === null) return
      // The drop is judged in full where it landed, and the form opens on what survived. A drop
      // that kept nothing has said so in a toast and opens no form to empty.
      const judged = judgeDrop(
        [...transfer.files],
        transfer.getData("text/uri-list"),
        limitsRef.current,
      )
      void judged.then((kept) => {
        kept.refusals.forEach(refuse)
        if (kept.files.length === 0 && kept.urls.length === 0) return
        setDropped(kept)
        setCreating(true)
      })
    }
    window.addEventListener("dragenter", enter)
    window.addEventListener("dragleave", leave)
    window.addEventListener("drop", drop)
    return () => {
      window.removeEventListener("dragenter", enter)
      window.removeEventListener("dragleave", leave)
      window.removeEventListener("drop", drop)
      setDepth(0)
    }
  }, [creating])

  if (session.isPending) return null
  // A session the API could not answer for is not an expired one, and only the second sends the
  // user back to the credentials screen.
  if (session.isError) return <p role="alert">{m.session_unreadable()}</p>
  if (!session.data) return <Navigate to="/sign-in" />

  return (
    <>
      {/* No padding at the bottom: the grid is the last child and reaches the viewport's edge. */}
      <main className="flex h-screen flex-col gap-4 px-4 pt-4">
        {/* The home screen is the one with no title of its own, until a search gives it one. */}
        <AppHeader
          heading={q === undefined ? undefined : m.search_results()}
          search={<SearchField term={q} />}
        >
          {/* The screen's primary verb, first for the keyboard and `order-last` on the right. */}
          <IconButton
            icon={Plus}
            name={m.create_pin()}
            className="order-last"
            onPress={() => {
              // Opened by hand, so it opens empty: the last drop's entry is not this one.
              setDropped(null)
              setCreating(true)
            }}
          />
          <SortSelect value={sort} values={PIN_SORTS} />
          <AppNav />
        </AppHeader>
        {/* Full bleed: the scrollbar belongs to the viewport edge, not inside the shell's padding. */}
        <div className="-mx-4 min-h-0 flex-1">
          <PinGrid sort={sort} label={m.home_heading()} term={q} />
        </div>
        {/* Fixed to the viewport, `<main>` scrolling away under the page, and `pointer-events-none`
            so an overlay appearing under the pointer fires no exit at the screen it never left. */}
        {depth > 0 && (
          <div className="pointer-events-none fixed inset-0 grid place-content-center border-2 border-dashed border-accent bg-background/80 text-lg">
            {m.drop_to_add()}
          </div>
        )}
      </main>
      <CreatePinDialog isOpen={creating} onOpenChange={setCreating} dropped={dropped} />
    </>
  )
}
