import { Button, Chip, Dropdown, EmptyState, Modal, Spinner, toast } from "@heroui/react"
import { Link } from "@tanstack/react-router"
import { ChevronLeft, ChevronRight, Pencil, Trash2, X } from "lucide-react"
import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent, type RefObject } from "react"
import { useMove } from "react-aria"
import { preload } from "react-dom"
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
import {
  neighbours,
  placeableTiles,
  renditionForColumn,
  tileAspectRatio,
  tileImageSource,
  type Rendition,
} from "../lib/tiles"
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
function Tile({
  pin,
  smallRenditionPx,
  onRendition,
}: {
  pin: Pin
  smallRenditionPx?: number
  onRendition: (rendition: Rendition) => void
}) {
  const ref = useRef<HTMLDivElement>(null)
  const columnWidth = useColumnWidth(ref)
  const image = pin.image
  const ratio = { aspectRatio: tileAspectRatio(image?.width, image?.height) }
  const rendition = renditionForColumn(columnWidth, window.devicePixelRatio, smallRenditionPx)
  // Every column is as wide, so the viewer loads under the rendition any tile chose.
  useEffect(() => onRendition(rendition), [onRendition, rendition])

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

/**
 * The original in a box of the size it is drawn at, its own or less to fit, never more. The grid's
 * rendition fills that box until the original arrives, from the cache when its tile was drawn.
 */
function OriginalImage({
  url,
  width,
  height,
  alt,
  placeholder,
}: {
  url: string
  width: number
  height: number
  alt: string
  placeholder: Rendition
}) {
  const [loaded, setLoaded] = useState(false)
  const size = {
    aspectRatio: `${width} / ${height}`,
    width: `min(${width}px, 100%, calc(var(--fit-height) * ${width / height}))`,
  }

  return (
    <div className="relative" style={size}>
      {!loaded && (
        <img src={tileImageSource(url, placeholder)} alt="" className="absolute inset-0 h-full w-full" />
      )}
      {/* Transparent rather than hidden until it loads: Firefox draws the alt text over the placeholder. */}
      <img
        src={url}
        alt={alt}
        onLoad={() => setLoaded(true)}
        className={`absolute inset-0 h-full w-full ${loaded ? "" : "opacity-0"}`}
      />
    </div>
  )
}

/** The image side: the picture, or what stands in its place (specification 2026-09-27, decision C). */
function PinImage({ pin, placeholder }: { pin: Pin; placeholder: Rendition }) {
  const retry = useSetPinImage()
  const image = pin.image
  const address = pin.sourceMediaUrl

  if (image?.status === "READY" && image.url != null)
    return image.width != null && image.height != null ? (
      <OriginalImage
        key={image.url}
        url={image.url}
        width={image.width}
        height={image.height}
        alt={pin.description}
        placeholder={placeholder}
      />
    ) : (
      <img src={image.url} alt={pin.description} className="max-h-full max-w-full max-lg:max-h-[70dvh]" />
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

/** How far a touch travels sideways to step. No measurement set it: it is the knob to tune on a phone. */
const SWIPE_PX = 48

/** A horizontal touch swipe steps, summed over the move since a cancelled pan ends it too (decision F). */
function useSwipe(previous?: () => void, next?: () => void) {
  const travel = useRef({ x: 0, y: 0 })
  const { moveProps } = useMove({
    onMoveStart: () => {
      travel.current = { x: 0, y: 0 }
    },
    onMove: ({ deltaX, deltaY }) => {
      travel.current.x += deltaX
      travel.current.y += deltaY
    },
    onMoveEnd: () => {
      const { x, y } = travel.current
      if (Math.abs(x) >= SWIPE_PX && Math.abs(x) > Math.abs(y)) (x < 0 ? next : previous)?.()
    },
  })
  // Its pointer down alone, for touch alone: it prevents a mouse from dragging the image out, and
  // its key handler would swallow the arrows the dialog steps on.
  return (event: PointerEvent<HTMLElement>) => {
    if (event.pointerType === "touch") moveProps.onPointerDown?.(event)
  }
}

/** `←` and `→` on the document: the dialog holds the focus once open, and passes on no key handler. */
function useArrowKeys(previous?: () => void, next?: () => void) {
  useEffect(() => {
    const step = (event: KeyboardEvent) => {
      if (event.key === "ArrowLeft") previous?.()
      if (event.key === "ArrowRight") next?.()
    }
    document.addEventListener("keydown", step)
    return () => document.removeEventListener("keydown", step)
  }, [previous, next])
}

/**
 * The image beside its details from `lg`, stacked below it (specification 2026-09-27, decision A).
 * Edit still swaps the whole dialog for the form (specification 2026-09-20, decision K), and
 * nothing steps while it is open.
 */
function PinDialog({
  pin,
  close,
  placeholder,
  previous,
  next,
}: {
  pin: Pin
  close: () => void
  placeholder: Rendition
  previous?: () => void
  next?: () => void
}) {
  const [editing, setEditing] = useState(false)
  const swipe = useSwipe(previous, next)
  useArrowKeys(editing ? undefined : previous, editing ? undefined : next)

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
      {/* The height the image fits in: its own side from `lg`, a share of the screen once stacked.
          `pan-y` leaves the vertical scroll to the browser, which then cancels the swipe. */}
      <div
        className="relative flex touch-pan-y items-center justify-center [--fit-height:70dvh] lg:min-w-0 lg:flex-1 lg:[--fit-height:100cqh] lg:[container-type:size]"
        onPointerDown={swipe}
      >
        <PinImage pin={pin} placeholder={placeholder} />
        <IconButton
          icon={ChevronLeft}
          name={m.pin_previous()}
          variant="secondary"
          isDisabled={previous === undefined}
          onPress={previous}
          className="absolute start-2 top-1/2 -translate-y-1/2"
        />
        <IconButton
          icon={ChevronRight}
          name={m.pin_next()}
          variant="secondary"
          isDisabled={next === undefined}
          onPress={next}
          className="absolute end-2 top-1/2 -translate-y-1/2"
        />
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
  const [rendition, setRendition] = useState<Rendition>("SMALL")
  const loaded = pins.data?.pages.flatMap((page) => page.pins) ?? []
  const tiles = placeableTiles(loaded)
  // Among every loaded pin, so a retried download that turns it `PENDING` keeps it open (decision E).
  const opened = loaded.find((pin) => pin.id === openedId)
  const { previous, next } = neighbours(loaded, openedId)
  const selection = useSelection(tiles)
  const selecting = selection.ids.length > 0

  // Past the last loaded pin the viewer asks for the page itself, the grid's sentinel not being in view.
  const fetchThenStep = async () => {
    const from = openedId
    const { data } = await pins.fetchNextPage()
    const arrived = neighbours(data?.pages.flatMap((page) => page.pins) ?? [], from).next
    // Only from the pin it left, so a viewer closed meanwhile stays closed.
    if (arrived) setOpenedId((current) => (current === from ? arrived.id : current))
  }
  // The neighbours' placeholder and never their original, so a step shows an image at once (decision F).
  for (const neighbour of opened ? [previous, next] : [])
    if (neighbour?.image?.url) preload(tileImageSource(neighbour.image.url, rendition), { as: "image" })
  const stepToPrevious = previous ? () => setOpenedId(previous.id) : undefined
  const canFetch = pins.hasNextPage && !pins.isFetchingNextPage
  const stepToNext = next
    ? () => setOpenedId(next.id)
    : canFetch
      ? () => void fetchThenStep()
      : undefined

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
                <Tile pin={pin} smallRenditionPx={renditionSizes?.small} onRendition={setRendition} />
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
            {opened && (
              <PinDialog
                pin={opened}
                close={() => setOpenedId(null)}
                placeholder={rendition}
                previous={stepToPrevious}
                next={stepToNext}
              />
            )}
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  )
}
