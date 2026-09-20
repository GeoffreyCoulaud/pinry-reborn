import { AlertDialog, Button, EmptyState, Spinner, Tabs, toast } from "@heroui/react"
import { Navigate, useSearch } from "@tanstack/react-router"
import { BrushCleaning, Trash2, Undo2 } from "lucide-react"
import { useState, type ReactNode } from "react"
import { Collection, GridList, GridListItem, GridListLoadMoreItem } from "react-aria-components"
import { AppHeader } from "../components/AppHeader"
import { AppNav } from "../components/AppNav"
import { IconButton } from "../components/IconButton"
import { SelectionBar, SelectionTick, useSelection } from "../components/SelectionBar"
import { SortSelect } from "../components/SortSelect"
import { RECYCLED_PIN_SORTS, type RecycledPinSort } from "../lib/sorts"
import { tileImageSource } from "../lib/tiles"
import { m } from "../paraglide/messages.js"
import {
  useDeleteBoardForGood,
  useDeletePinForGood,
  useEmptyBoardBin,
  useEmptyPinBin,
  useRecycledBoards,
  useRecycledPins,
  useRestoreBoards,
  useRestorePins,
} from "../recycled"
import { useSession } from "../session"

const ROW =
  "flex flex-wrap items-center gap-3 border-b border-separator px-2 py-3 outline-none last:border-0"

const CENTRED = "grid h-full place-content-center justify-items-center gap-2 text-center text-muted"

/** What stands in for the rows: the read, its refusal, or a bin with nothing left in it. */
function placeholder(query: { isPending: boolean; isError: boolean }, rows: number): ReactNode {
  if (query.isPending)
    return (
      <div role="status" className={CENTRED}>
        {/* Hidden from the reader: the spinner carries a `status` role of its own. */}
        <Spinner aria-hidden />
        {m.bin_loading()}
      </div>
    )
  if (query.isError) return <p role="alert">{m.bin_unreadable()}</p>
  if (rows === 0)
    return (
      <EmptyState role="status" className={CENTRED}>
        {m.bin_empty()}
      </EmptyState>
    )
  return null
}

/** The two gestures a row of either bin carries: back out of it, or gone for good (decision F). */
function RowGestures(props: { name: string; restore: () => void; deleteForGood: () => void }) {
  const { name, restore, deleteForGood } = props
  return (
    <>
      <IconButton icon={Undo2} name={m.restore({ name })} variant="ghost" onPress={restore} />
      {/* One of the two gestures on this screen that cannot be undone, and marked as such. */}
      <IconButton icon={Trash2} name={m.delete_for_good({ name })} variant="danger-soft" onPress={deleteForGood} />
    </>
  )
}

/** A refusal has no form to speak in, the gesture having happened in a row that stays put. */
const refused = { onError: () => toast.danger(m.bin_refused()) }

/**
 * The one gesture a selection carries in either bin. Deleting for good stays on its own row,
 * decision F putting in bulk what a user can undo and nothing else.
 */
function RestoreBar({
  selection,
  restore,
}: {
  selection: ReturnType<typeof useSelection>
  restore: (ids: string[]) => void
}) {
  return (
    <SelectionBar count={selection.ids.length} clear={selection.clear}>
      <Button variant="ghost" onPress={() => restore(selection.ids)}>
        {m.restore_selection()}
      </Button>
    </SelectionBar>
  )
}

/**
 * The other gesture that cannot be undone, and the one that reaches every row at once, so it asks
 * before it acts. A brush and not a bin: the bin drawn beside it deletes the one row it sits in,
 * and two gestures of different reach must not share a glyph. The icon is the trigger itself,
 * `AlertDialog` being a `DialogTrigger`, which presses its first child.
 */
function EmptyBin({ empty }: { empty: () => void }) {
  return (
    <AlertDialog>
      <IconButton
        icon={BrushCleaning}
        name={m.empty_bin()}
        variant="danger-soft"
        className="ms-auto"
      />
      <AlertDialog.Backdrop>
        <AlertDialog.Container size="sm">
          <AlertDialog.Dialog>
            {({ close }) => (
              <>
                <AlertDialog.Heading>{m.empty_bin_question()}</AlertDialog.Heading>
                <AlertDialog.Body>{m.empty_bin_warning()}</AlertDialog.Body>
                <AlertDialog.Footer>
                  <Button variant="ghost" onPress={close}>
                    {m.cancel()}
                  </Button>
                  <Button
                    variant="danger"
                    onPress={() => {
                      close()
                      empty()
                    }}
                  >
                    {m.empty_bin()}
                  </Button>
                </AlertDialog.Footer>
              </>
            )}
          </AlertDialog.Dialog>
        </AlertDialog.Container>
      </AlertDialog.Backdrop>
    </AlertDialog>
  )
}

function RecycledPins({ sort }: { sort: RecycledPinSort }) {
  const pins = useRecycledPins(sort)
  const restore = useRestorePins()
  const deleteForGood = useDeletePinForGood()
  const rows = pins.data?.pages.flatMap((page) => page.pins) ?? []
  const selection = useSelection(rows)

  return (
    placeholder(pins, rows.length) ?? (
      <>
        <RestoreBar
          selection={selection}
          restore={(pinIds) => restore.mutate(pinIds, { ...refused, onSuccess: selection.clear })}
        />
        <GridList aria-label={m.pins()} className="outline-none" {...selection.props}>
          <Collection items={rows}>
            {(pin) => (
              <GridListItem id={pin.id} textValue={pin.description} className={ROW}>
                <SelectionTick />
                {/* Decorative: the description beside it is the row's own name. */}
                {pin.image?.url && (
                  <img
                    src={tileImageSource(pin.image.url, "SMALL")}
                    alt=""
                    className="size-12 shrink-0 rounded object-cover"
                  />
                )}
                <span className="min-w-0 flex-1 truncate">{pin.description}</span>
                <RowGestures
                  name={pin.description}
                  restore={() => restore.mutate([pin.id], refused)}
                  deleteForGood={() => deleteForGood.mutate(pin.id, refused)}
                />
              </GridListItem>
            )}
          </Collection>
          {/* Guarded for the reason `PinGrid` states: the sentinel is re-observed on every
              collection change, its own loading flag included. */}
          <GridListLoadMoreItem
            onLoadMore={() => {
              if (pins.hasNextPage && !pins.isFetchingNextPage) void pins.fetchNextPage()
            }}
            isLoading={pins.isFetchingNextPage}
          />
        </GridList>
      </>
    )
  )
}

function RecycledBoards() {
  const boards = useRecycledBoards()
  const restore = useRestoreBoards()
  const deleteForGood = useDeleteBoardForGood()
  const rows = boards.data ?? []
  const selection = useSelection(rows)

  return (
    placeholder(boards, rows.length) ?? (
      <>
        <RestoreBar
          selection={selection}
          restore={(boardIds) => restore.mutate(boardIds, { ...refused, onSuccess: selection.clear })}
        />
        <GridList aria-label={m.boards()} items={rows} className="outline-none" {...selection.props}>
          {(board) => (
            <GridListItem id={board.id} textValue={board.name} className={ROW}>
              <SelectionTick />
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="font-medium">{board.name}</span>
                <span className="truncate text-muted">{board.description}</span>
              </div>
              <RowGestures
                name={board.name}
                restore={() => restore.mutate([board.id], refused)}
                deleteForGood={() => deleteForGood.mutate(board.id, refused)}
              />
            </GridListItem>
          )}
        </GridList>
      </>
    )
  )
}

/**
 * One screen for both collections, which carry the same three gestures (decision J). One toolbar
 * row holds what belongs to the open tab: which collection it is, the order it is read in, and the
 * gesture that empties it. The order is the Pins tab's alone, `GET /api/v1/boards/recycled` taking
 * no sort, so passing one would write a search parameter no request reads (specification 2.5).
 */
export function Recycled() {
  const { sort } = useSearch({ from: "/recycled" })
  const session = useSession()
  const [tab, setTab] = useState("pins")
  const emptyPins = useEmptyPinBin()
  const emptyBoards = useEmptyBoardBin()

  if (session.isPending) return null
  if (session.isError) return <p role="alert">{m.session_unreadable()}</p>
  if (!session.data) return <Navigate to="/sign-in" />

  const empty = tab === "pins" ? emptyPins : emptyBoards

  return (
    <main className="flex h-screen flex-col gap-4 px-4 pt-4">
      {/* The screen's own header carries what belongs to the screen, the tab's controls being on
          the row below it: the order no longer appears and disappears above the heading. */}
      <AppHeader heading={m.recycle_bin()}>
        <AppNav />
      </AppHeader>
      <Tabs
        selectedKey={tab}
        onSelectionChange={(key) => setTab(String(key))}
        className="flex min-h-0 flex-1 flex-col gap-3"
      >
        <div className="flex flex-wrap items-center gap-3">
          <Tabs.ListContainer>
            <Tabs.List aria-label={m.recycle_bin()}>
              <Tabs.Tab id="pins">
                <Tabs.Indicator />
                {m.pins()}
              </Tabs.Tab>
              <Tabs.Tab id="boards">
                <Tabs.Indicator />
                {m.boards()}
              </Tabs.Tab>
            </Tabs.List>
          </Tabs.ListContainer>
          {tab === "pins" && <SortSelect value={sort} values={RECYCLED_PIN_SORTS} />}
          <EmptyBin empty={() => empty.mutate(undefined, refused)} />
        </div>
        {/* The panel is the scroll box: the toolbar above it stays put while the rows move. */}
        <Tabs.Panel id="pins" className="mt-0! min-h-0 flex-1 overflow-y-auto">
          <RecycledPins sort={sort} />
        </Tabs.Panel>
        <Tabs.Panel id="boards" className="mt-0! min-h-0 flex-1 overflow-y-auto">
          <RecycledBoards />
        </Tabs.Panel>
      </Tabs>
    </main>
  )
}
