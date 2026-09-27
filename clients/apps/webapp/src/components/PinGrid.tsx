import { Button, Chip, Dropdown, EmptyState, Modal, Spinner, toast } from "@heroui/react"
import { Link } from "@tanstack/react-router"
import { Pencil, Trash2, X } from "lucide-react"
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
import { downloadReason, retriable } from "../downloadReasons"
import { useHandshake, useSetPinImage } from "../images"
import type { PinSort } from "../lib/sorts"
import { placeableTiles, renditionForColumn, tileAspectRatio, tileImageSource } from "../lib/tiles"
import { m } from "../paraglide/messages.js"
import { useRecyclePins, usePins, type Pin } from "../pins"
import { BoardForm } from "./BoardForm"
import { IconButton } from "./IconButton"
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
 * its column settles once, before a byte of the image arrives (specification 2026-09-10, 4.7).
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

/** The image side: the picture, or what stands in its place (specification 2026-09-27, decision C). */
function PinImage({ pin }: { pin: Pin }) {
  const retry = useSetPinImage()
  const image = pin.image
  const address = pin.sourceMediaUrl

  if (image?.status === "READY" && image.url != null)
    return (
      <img
        src={tileImageSource(image.url, "LARGE")}
        alt={pin.description}
        className="h-full w-full object-contain max-lg:max-h-[70dvh]"
      />
    )
  if (image?.status === "PENDING")
    return (
      <p role="status" className="flex items-center gap-2">
        <Spinner aria-hidden />
        {m.task_running()}
      </p>
    )
  if (image?.status === "FAILED")
    return (
      <div className="flex flex-col items-center gap-3 text-center">
        <p>{downloadReason(image.reasonCode, image.message)}</p>
        {/* From the pin's own address, which the user may have corrected since the download failed. */}
        {retriable(image.reasonCode) && address != null && (
          <Button
            isDisabled={retry.isPending}
            onPress={() => retry.mutate({ pinId: pin.id, source: { url: address } })}
          >
            {m.retry()}
          </Button>
        )}
        {retry.isError && <p role="alert">{m.image_refused()}</p>}
      </div>
    )
  return <p>{m.pin_no_image()}</p>
}

/** The column beside the image, fixed so that what a wider window adds goes to the image. */
function PinDetails({ pin, close, edit }: { pin: Pin; close: () => void; edit: () => void }) {
  const recycle = useRecyclePins()
  const source = pin.sourceContextUrl

  return (
    <div className="flex flex-col gap-4 lg:w-[22.5rem] lg:shrink-0 lg:overflow-y-auto">
      <div className="flex items-center gap-1">
        <Button variant="ghost" onPress={edit}>
          <Pencil aria-hidden />
          {m.edit_pin()}
        </Button>
        {/* Kept away from Close: no confirmation guards it, the bin being how the pin comes back. */}
        <IconButton
          icon={Trash2}
          name={m.delete_pin()}
          variant="ghost"
          isDisabled={recycle.isPending}
          onPress={() =>
            recycle.mutate([pin.id], {
              onSuccess: close,
              onError: () => toast.danger(m.pin_deletion_refused()),
            })
          }
        />
        <IconButton icon={X} name={m.close()} variant="ghost" className="ms-auto" onPress={close} />
      </div>
      <p>{pin.description}</p>
      <dl className="flex flex-col gap-4 [&_dt]:mb-1 [&_dt]:text-sm [&_dt]:text-muted">
        {source && (
          <div>
            <dt>{m.source_page()}</dt>
            <dd>
              <a href={source} target="_blank" rel="noreferrer" className="text-accent hover:underline">
                {URL.parse(source)?.hostname ?? source}
              </a>
            </dd>
          </div>
        )}
        {pin.tags.length > 0 && (
          <div>
            <dt>{m.tags()}</dt>
            <dd className="flex flex-wrap gap-2">
              {pin.tags.map((tag) => (
                <Chip key={tag.name}>{tag.name}</Chip>
              ))}
            </dd>
          </div>
        )}
        {pin.boards.length > 0 && (
          <div>
            <dt>{m.boards()}</dt>
            <dd className="flex flex-col items-start gap-1">
              {pin.boards.map((board) => (
                <Link
                  key={board.id}
                  to="/boards/$boardId"
                  params={{ boardId: board.id }}
                  className="text-accent hover:underline"
                >
                  {board.name}
                </Link>
              ))}
            </dd>
          </div>
        )}
      </dl>
    </div>
  )
}

/**
 * The image beside its details from `lg`, stacked below it (specification 2026-09-27, decision A).
 * Edit still swaps the whole dialog for the form (specification 2026-09-20, decision K).
 */
function PinDialog({ pin, close }: { pin: Pin; close: () => void }) {
  const [editing, setEditing] = useState(false)

  if (editing)
    return (
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-lg">
          <PinEditForm pin={pin} close={() => setEditing(false)} />
        </div>
      </div>
    )

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto lg:flex-row lg:overflow-hidden">
      <div className="flex items-center justify-center lg:min-w-0 lg:flex-1">
        <PinImage pin={pin} />
      </div>
      <PinDetails pin={pin} close={close} edit={() => setEditing(true)} />
    </div>
  )
}

/** The menu's key for "New board…", which no board's identifier, a UUID, can take. */
const NEW_BOARD = "new"

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
  const [creating, setCreating] = useState(false)
  const spend = (message: string) => ({ onSuccess: clear, onError: () => toast.danger(message) })

  return (
    <>
      {/* The board is chosen in the gesture rather than before it: the menu is the second half
          of one press, and there is nothing to undo if it is dismissed. */}
      <Dropdown>
        <Button variant="outline" isDisabled={add.isPending}>
          {m.add_to_board()}
        </Button>
        <Dropdown.Popover>
          <Dropdown.Menu
            aria-label={m.boards()}
            onAction={(key) =>
              key === NEW_BOARD
                ? setCreating(true)
                : add.mutate({ boardId: String(key), pinIds }, spend(m.membership_refused()))
            }
          >
            <Dropdown.Item id={NEW_BOARD}>{m.new_board()}</Dropdown.Item>
            <Collection items={boards.data ?? []}>
              {(held) => <Dropdown.Item id={held.id}>{held.name}</Dropdown.Item>}
            </Collection>
          </Dropdown.Menu>
        </Dropdown.Popover>
      </Dropdown>
      {/* A refusal keeps the dialog and the selection, so the gesture can be tried again. */}
      <Modal.Backdrop isOpen={creating} onOpenChange={setCreating} isDismissable>
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger aria-label={m.close()} />
            <Modal.Heading level={2} className="mb-3 pe-8">
              {m.create_board()}
            </Modal.Heading>
            <BoardForm
              edited="new"
              pinIds={pinIds}
              close={() => {
                setCreating(false)
                clear()
              }}
            />
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
      {boardId !== undefined && (
        <Button
          variant="outline"
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
export function PinGrid({
  sort,
  label,
  boardId,
  term,
}: {
  sort: PinSort
  label: string
  boardId?: string
  term?: string
}) {
  const pins = usePins(sort, boardId, term)
  // The breakpoint a tile picks its rendition on is the deployment's, not a constant: `small`
  // lowered in the configuration would otherwise upscale every tile (specification 4.3).
  const renditionSizes = useHandshake().data?.renditionSizes
  const [openedId, setOpenedId] = useState<string | null>(null)
  const loaded = pins.data?.pages.flatMap((page) => page.pins) ?? []
  const tiles = placeableTiles(loaded)
  // Among every loaded pin, so a retried download that turns it `PENDING` keeps it open (decision E).
  const opened = loaded.find((pin) => pin.id === openedId)
  const selection = useSelection(tiles)
  const selecting = selection.ids.length > 0

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
  // A search that matched nothing is not an empty account, and the recourse differs: add a pin,
  // or search for something else (specification 2026-09-21, decision N).
  // An open pin gone `PENDING` may leave no tile, and the empty state would take its dialog with it.
  if (tiles.length === 0 && opened === undefined)
    return (
      <EmptyState role="status" className="grid h-full place-content-center text-center">
        {term === undefined ? m.pins_empty() : m.search_empty({ term })}
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
          {/* A collection renders its items once and keeps them: without `dependencies` the ticks
              would not hear that the grid now holds a selection. */}
          <Collection items={tiles} dependencies={[selecting]}>
            {/* `group` is what the tick's reveal on hover and on focus hangs off. It replaces
                react-aria's own class name, which nothing in this application styles. A selected
                tile is ringed and tinted so the selection reads at a glance. */}
            {(pin) => (
              <GridListItem
                textValue={pin.description}
                className="group rounded data-selected:ring-4 data-selected:ring-accent data-selected:after:pointer-events-none data-selected:after:absolute data-selected:after:inset-0 data-selected:after:rounded data-selected:after:bg-accent/25"
              >
                <SelectionTick shown={selecting} className="absolute start-2 top-2 z-10" />
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
        {/* The window less HeroUI's margin, and the whole screen below `sm`, where `cover` keeps
            both the margin and the corners (specification 2026-09-27, decision A). */}
        <Modal.Container size="cover" scroll="inside" className="max-sm:p-0">
          <Modal.Dialog aria-label={opened?.description} className="max-sm:rounded-none">
            {opened && <PinDialog pin={opened} close={() => setOpenedId(null)} />}
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  )
}
