import { Button, EmptyState, Modal, Spinner } from "@heroui/react"
import { useLayoutEffect, useRef, useState, type RefObject } from "react"
import {
  Collection,
  GridList,
  GridListItem,
  GridListLoadMoreItem,
  Size,
  Virtualizer,
  WaterfallLayout,
} from "react-aria-components"
import { downloadReason } from "../downloadReasons"
import { useHandshake } from "../images"
import type { PinSort } from "../lib/sorts"
import { placeableTiles, renditionForColumn, tileAspectRatio, tileImageSource } from "../lib/tiles"
import { m } from "../paraglide/messages.js"
import { usePins, type Pin } from "../pins"
import { PinEditForm } from "./PinEditForm"

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

/** The pin as it reads, until the Edit button swaps it for the form that writes it (decision K). */
function PinDialog({ pin, close }: { pin: Pin; close: () => void }) {
  const [editing, setEditing] = useState(false)

  if (editing) return <PinEditForm pin={pin} close={() => setEditing(false)} />

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
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onPress={() => setEditing(true)}>
          {m.edit_pin()}
        </Button>
        <Button onPress={close}>{m.close()}</Button>
      </div>
    </div>
  )
}

/**
 * The catalogue as tiles, or one board's share of it. The home screen and a board's screen render
 * the same grid; what surrounds it, the drop that creates a pin included, is the screen's own.
 */
export function PinGrid({ sort, label, boardId }: { sort: PinSort; label: string; boardId?: string }) {
  const pins = usePins(sort, boardId)
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
          aria-label={label}
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
        {/* `inside`, the default, clips whatever the dialog cannot hold and scrolls nothing of its
            own: at phone width the edit form's last fields and its buttons were unreachable. */}
        <Modal.Container size="lg" scroll="outside">
          <Modal.Dialog aria-label={opened?.description}>
            {opened && <PinDialog pin={opened} close={() => setOpenedId(null)} />}
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </>
  )
}
