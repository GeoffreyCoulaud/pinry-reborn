import { AlertDialog, Button, EmptyState, Spinner, Tabs, toast } from "@heroui/react"
import { Navigate, useSearch } from "@tanstack/react-router"
import { Trash2, Undo2 } from "lucide-react"
import { useState, type ReactNode } from "react"
import { Collection, GridList, GridListItem, GridListLoadMoreItem } from "react-aria-components"
import { AppHeader } from "../components/AppHeader"
import { AppNav } from "../components/AppNav"
import { IconButton } from "../components/IconButton"
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
      {/* The one gesture on this screen that cannot be undone, and the only one marked as such. */}
      <IconButton icon={Trash2} name={m.delete_for_good({ name })} variant="danger-soft" onPress={deleteForGood} />
    </>
  )
}

/** A refusal has no form to speak in, the gesture having happened in a row that stays put. */
const refused = { onError: () => toast.danger(m.bin_refused()) }

/**
 * A tab: its rows, under the gesture that empties it. Disabled on an empty bin rather than hidden,
 * so the tab does not change shape while the user reads it, and asked before it acts: emptying is
 * the one gesture of this screen a user cannot undo, restoring being reversible and a permanent
 * delete acting on the one row it sits in.
 */
function BinPanel(props: { empty: () => void; isEmpty: boolean; children: ReactNode }) {
  const { empty, isEmpty, children } = props
  return (
    <div className="flex h-full flex-col gap-2">
      {/* The button is the trigger: `AlertDialog` is a `DialogTrigger`, which presses its first
          child, so the control keeps its own variant and its disabled state. */}
      <AlertDialog>
        <Button variant="danger-soft" className="self-end" isDisabled={isEmpty}>
          {m.empty_bin()}
        </Button>
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
      <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
    </div>
  )
}

function RecycledPins({ sort }: { sort: RecycledPinSort }) {
  const pins = useRecycledPins(sort)
  const restore = useRestorePins()
  const deleteForGood = useDeletePinForGood()
  const emptied = useEmptyPinBin()
  const rows = pins.data?.pages.flatMap((page) => page.pins) ?? []

  return (
    <BinPanel empty={() => emptied.mutate(undefined, refused)} isEmpty={rows.length === 0}>
      {placeholder(pins, rows.length) ?? (
        <GridList aria-label={m.pins()} className="outline-none">
          <Collection items={rows}>
            {(pin) => (
              <GridListItem id={pin.id} textValue={pin.description} className={ROW}>
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
      )}
    </BinPanel>
  )
}

function RecycledBoards() {
  const boards = useRecycledBoards()
  const restore = useRestoreBoards()
  const deleteForGood = useDeleteBoardForGood()
  const emptied = useEmptyBoardBin()
  const rows = boards.data ?? []

  return (
    <BinPanel empty={() => emptied.mutate(undefined, refused)} isEmpty={rows.length === 0}>
      {placeholder(boards, rows.length) ?? (
        <GridList aria-label={m.boards()} items={rows} className="outline-none">
          {(board) => (
            <GridListItem id={board.id} textValue={board.name} className={ROW}>
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
      )}
    </BinPanel>
  )
}

/**
 * One screen for both collections, which carry the same three gestures (decision J). The order
 * selector belongs to the Pins tab alone: `GET /api/v1/boards/recycled` takes no sort, and passing
 * one would write a search parameter no request reads (specification 2.5).
 */
export function Recycled() {
  const { sort } = useSearch({ from: "/recycled" })
  const session = useSession()
  const [tab, setTab] = useState("pins")

  if (session.isPending) return null
  if (session.isError) return <p role="alert">{m.session_unreadable()}</p>
  if (!session.data) return <Navigate to="/sign-in" />

  return (
    <main className="flex h-screen flex-col gap-4 px-4 pt-4">
      <AppHeader heading={m.recycle_bin()}>
        <AppNav />
        {tab === "pins" && <SortSelect value={sort} values={RECYCLED_PIN_SORTS} />}
      </AppHeader>
      <Tabs
        selectedKey={tab}
        onSelectionChange={(key) => setTab(String(key))}
        className="flex min-h-0 flex-1 flex-col"
      >
        <Tabs.ListContainer className="self-start">
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
        <Tabs.Panel id="pins" className="min-h-0 flex-1">
          <RecycledPins sort={sort} />
        </Tabs.Panel>
        <Tabs.Panel id="boards" className="min-h-0 flex-1">
          <RecycledBoards />
        </Tabs.Panel>
      </Tabs>
    </main>
  )
}
