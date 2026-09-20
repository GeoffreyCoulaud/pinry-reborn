import { Button, Dropdown, EmptyState, Modal, Spinner, toast } from "@heroui/react"
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
import { useAddPinsToBoard, useBoards, useRemovePinsFromBoard } from "../boards"
import { downloadReason } from "../downloadReasons"
import { useHandshake } from "../images"
import type { PinSort } from "../lib/sorts"
import { placeableTiles, renditionForColumn, tileAspectRatio, tileImageSource } from "../lib/tiles"
import { m } from "../paraglide/messages.js"
import { useRecyclePins, usePins, type Pin } from "../pins"
import { PinEditForm } from "./PinEditForm"
import { SelectionBar, SelectionTick, useSelection } from "./SelectionBar"

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
  const recycle = useRecyclePins()

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
        {/* Kept away from Close at the other end: it is the one button here that changes the
            account, and no confirmation guards it, the bin being how the pin comes back. */}
        <Button
          variant="ghost"
          className="me-auto"
          isDisabled={recycle.isPending}
          onPress={() =>
            recycle.mutate([pin.id], {
              onSuccess: close,
              onError: () => toast.danger(m.pin_deletion_refused()),
            })
          }
        >
          {m.delete_pin()}
        </Button>
        <Button variant="ghost" onPress={() => setEditing(true)}>
          {m.edit_pin()}
        </Button>
        <Button onPress={close}>{m.close()}</Button>
      </div>
    </div>
  )
}

/**
 * What the selection bar offers over tiles. Two gestures on the catalogue and three on a board's
 * grid: taking pins out of a board is only a gesture where there is a board to take them out of,
 * and every one of them is a single all-or-nothing request (ADR 0039).
 */
function PinGestures({
  pinIds,
  boardId,
  clear,
}: {
  pinIds: readonly string[]
  boardId?: string
  clear: () => void
}) {
  const boards = useBoards()
  const add = useAddPinsToBoard()
  const remove = useRemovePinsFromBoard()
  const recycle = useRecyclePins()
  const spend = (message: string) => ({ onSuccess: clear, onError: () => toast.danger(message) })

  return (
    <>
      {/* The board is chosen in the gesture rather than before it: the menu is the second half
          of one press, and there is nothing to undo if it is dismissed. */}
      <Dropdown>
        <Button variant="ghost" isDisabled={add.isPending}>
          {m.add_to_board()}
        </Button>
        <Dropdown.Popover>
          <Dropdown.Menu
            aria-label={m.boards()}
            items={boards.data ?? []}
            onAction={(key) =>
              add.mutate({ boardId: String(key), pinIds }, spend(m.membership_refused()))
            }
          >
            {(held) => <Dropdown.Item id={held.id}>{held.name}</Dropdown.Item>}
          </Dropdown.Menu>
        </Dropdown.Popover>
      </Dropdown>
      {boardId !== undefined && (
        <Button
          variant="ghost"
          isDisabled={remove.isPending}
          onPress={() => remove.mutate({ boardId, pinIds }, spend(m.membership_refused()))}
        >
          {m.remove_from_board()}
        </Button>
      )}
      <Button
        variant="danger-soft"
        isDisabled={recycle.isPending}
        onPress={() => recycle.mutate(pinIds, spend(m.pin_deletion_refused()))}
      >
        {m.delete_pin()}
      </Button>
    </>
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
  const selection = useSelection(tiles)

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
    <div className="flex h-full flex-col gap-2">
      <SelectionBar count={selection.ids.length} clear={selection.clear}>
        <PinGestures pinIds={selection.ids} boardId={boardId} clear={selection.clear} />
      </SelectionBar>
      <Virtualizer layout={WaterfallLayout} layoutOptions={LAYOUT}>
        <GridList
          aria-label={label}
          layout="grid"
          {...selection.props}
          // react-aria writes no `overflow` on what it virtualizes: without this the window scrolls.
          className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto outline-none"
          onAction={(key) => setOpenedId(String(key))}
        >
          <Collection items={tiles}>
            {(pin) => (
              <GridListItem textValue={pin.description}>
                {/* Over the picture's top corner, on a plate of its own: a tick drawn straight
                    onto an image is invisible on half the images in a catalogue. */}
                <SelectionTick className="absolute start-2 top-2 z-10 rounded bg-background/80 p-1" />
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
    </div>
  )
}
