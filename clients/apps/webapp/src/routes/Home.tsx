import { Button, EmptyState, Modal, Spinner } from "@heroui/react"
import { Navigate, useSearch } from "@tanstack/react-router"
import { LogOut, Plus } from "lucide-react"
import { useEffect, useLayoutEffect, useRef, useState, type RefObject } from "react"
import {
  Collection,
  GridList,
  GridListItem,
  GridListLoadMoreItem,
  Size,
  Virtualizer,
  WaterfallLayout,
} from "react-aria-components"
import { AppHeader } from "../components/AppHeader"
import { AppNav } from "../components/AppNav"
import { CreatePinDialog } from "../components/CreatePinDialog"
import { IconButton } from "../components/IconButton"
import { SortSelect } from "../components/SortSelect"
import { TaskCentre } from "../components/TaskCentre"
import { downloadReason } from "../downloadReasons"
import { judgeDrop, refuse } from "../drops"
import { useHandshake } from "../images"
import { dragDepth, type DragStep } from "../lib/drags"
import type { DropPartition } from "../lib/drops"
import { PIN_SORTS, type PinSort } from "../lib/sorts"
import { placeableTiles, renditionForColumn, tileAspectRatio, tileImageSource } from "../lib/tiles"
import { m } from "../paraglide/messages.js"
import { usePins, type Pin } from "../pins"
import { useSession, useSignOut } from "../session"

/**
 * Every bound here is finite, and two of them have to be. `WaterfallLayout` reads the scroll
 * view's width, which react-aria reports as infinite under test, so an unbounded `maxColumns`,
 * `maxItemSize` or `maxHorizontalSpace` lays the grid out at `NaN`. A finite column is also what
 * a tile wants: past the medium rendition the server has no more pixels to give it.
 */
const LAYOUT = {
  minItemSize: new Size(200, 200),
  maxItemSize: new Size(480, 960),
  maxHorizontalSpace: 16,
  maxColumns: 8,
}

/** The column's width, which the layout gives the item and only a browser can measure. */
function useColumnWidth(ref: RefObject<HTMLElement | null>): number {
  const [width, setWidth] = useState(0)
  useLayoutEffect(() => {
    const measured = ref.current?.clientWidth ?? 0
    if (measured !== width) setWidth(measured)
  })
  return width
}

/**
 * The tile carries its ratio so the layout measures it at its true height on the first pass and
 * its column settles once, before a byte of the image arrives (specification 4.7).
 */
function Tile({ pin, smallRenditionPx }: { pin: Pin; smallRenditionPx?: number }) {
  const ref = useRef<HTMLDivElement>(null)
  const columnWidth = useColumnWidth(ref)
  const image = pin.image
  const ratio = { aspectRatio: tileAspectRatio(image?.width, image?.height) }
  const rendition = renditionForColumn(columnWidth, window.devicePixelRatio, smallRenditionPx)

  return (
    <div ref={ref} className="w-full">
      {image?.url ? (
        <img
          src={tileImageSource(image.url, rendition)}
          alt={pin.description}
          style={ratio}
          className="w-full rounded object-cover"
        />
      ) : (
        // A failed download keeps its tile and says why; a pin with no image at all says what it is.
        <p
          style={ratio}
          className="grid place-content-center rounded bg-surface p-2 text-center shadow-surface"
        >
          {downloadReason(image?.reasonCode, image?.message) ?? pin.description}
        </p>
      )}
    </div>
  )
}

function PinDialog({ pin, close }: { pin: Pin; close: () => void }) {
  return (
    <div className="flex flex-col gap-3">
      {pin.image?.url && (
        <img
          src={tileImageSource(pin.image.url, "MEDIUM")}
          alt={pin.description}
          className="max-h-[60vh] w-full object-contain"
        />
      )}
      <p>{pin.description}</p>
      <ul className="flex flex-wrap gap-2">
        {pin.tags.map((tag) => (
          <li key={tag.name}>{tag.name}</li>
        ))}
      </ul>
      <ul className="flex flex-wrap gap-2">
        {pin.boards.map((board) => (
          <li key={board.id}>{board.name}</li>
        ))}
      </ul>
      <Button className="self-end" onPress={close}>
        {m.close()}
      </Button>
    </div>
  )
}

function PinGrid({ sort }: { sort: PinSort }) {
  const pins = usePins(sort)
  // The breakpoint a tile picks its rendition on is the deployment's, not a constant: `small`
  // lowered in the configuration would otherwise upscale every tile (specification 4.3).
  const renditionSizes = useHandshake().data?.renditionSizes
  const [openedId, setOpenedId] = useState<string | null>(null)
  const tiles = placeableTiles(pins.data?.pages.flatMap((page) => page.pins) ?? [])
  const opened = tiles.find((pin) => pin.id === openedId)

  // Neither a first load nor an account with nothing in it draws a tile, and both said so with
  // a blank rectangle until now.
  if (pins.isPending)
    return (
      <div role="status" className="grid h-full place-content-center justify-items-center gap-2 text-muted">
        {/* Hidden from the reader: the spinner carries a `status` role of its own, and the
            sentence beside it is the one this region should announce. */}
        <Spinner aria-hidden />
        {m.pins_loading()}
      </div>
    )
  // A refusal is not an empty account, and the empty state below would state one.
  if (pins.isError) return <p role="alert">{m.pins_unreadable()}</p>
  if (tiles.length === 0)
    return (
      <EmptyState role="status" className="grid h-full place-content-center text-center">
        {m.pins_empty()}
      </EmptyState>
    )

  return (
    <>
      <Virtualizer layout={WaterfallLayout} layoutOptions={LAYOUT}>
        <GridList
          aria-label={m.home_heading()}
          layout="grid"
          selectionMode="multiple"
          // react-aria writes no `overflow` on what it virtualizes: without this the window scrolls.
          className="h-full overflow-x-hidden overflow-y-auto outline-none"
          onAction={(key) => setOpenedId(String(key))}
        >
          <Collection items={tiles}>
            {(pin) => (
              <GridListItem textValue={pin.description}>
                <Tile pin={pin} smallRenditionPx={renditionSizes?.small} />
              </GridListItem>
            )}
          </Collection>
          {/*
            The sentinel is re-observed on every collection change, and its own loading flag is
            one such change, so an unguarded `onLoadMore` re-enters until the test times out.
          */}
          <GridListLoadMoreItem
            onLoadMore={() => {
              if (pins.hasNextPage && !pins.isFetchingNextPage) void pins.fetchNextPage()
            }}
            isLoading={pins.isFetchingNextPage}
          />
        </GridList>
      </Virtualizer>
      {/* The backdrop is the root here: a tile opens this modal, and the `Modal` root is a
          `DialogTrigger` that warns when it has no pressable child. */}
      <Modal.Backdrop
        isOpen={opened !== undefined}
        onOpenChange={() => setOpenedId(null)}
        isDismissable
      >
        <Modal.Container size="lg">
          <Modal.Dialog aria-label={opened?.description}>
            {opened && <PinDialog pin={opened} close={() => setOpenedId(null)} />}
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </>
  )
}

export function Home() {
  const { sort } = useSearch({ from: "/" })
  const session = useSession()
  const signOut = useSignOut()
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
        <AppHeader heading={m.home_heading()}>
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
          <TaskCentre />
          <IconButton
            icon={LogOut}
            name={m.sign_out()}
            variant="ghost"
            onPress={() => signOut.mutate()}
          />
        </AppHeader>
        {/* Full bleed: the scrollbar belongs to the viewport edge, not inside the shell's padding. */}
        <div className="-mx-4 min-h-0 flex-1">
          <PinGrid sort={sort} />
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
