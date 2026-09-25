import { AlertDialog, Button } from "@heroui/react"
import type { ReactNode } from "react"
import { dataFailure } from "../dataFailures"
import { useCancelImport, useLatestImport, type Import } from "../imports"
import { m } from "../paraglide/messages.js"
import { getLocale } from "../paraglide/runtime.js"

/** Behind a confirmation, which says what cancelling does not undo. */
function CancelImport({ id }: { id: string }) {
  const cancel = useCancelImport()

  return (
    <>
      <AlertDialog>
        <Button variant="secondary">{m.import_cancel()}</Button>
        <AlertDialog.Backdrop>
          <AlertDialog.Container size="sm">
            <AlertDialog.Dialog>
              {({ close }) => (
                <>
                  <AlertDialog.Heading>{m.import_cancel_question()}</AlertDialog.Heading>
                  <AlertDialog.Body>{m.import_cancel_warning()}</AlertDialog.Body>
                  <AlertDialog.Footer>
                    <Button variant="ghost" onPress={close}>
                      {m.import_keep()}
                    </Button>
                    <Button
                      variant="danger"
                      isDisabled={cancel.isPending}
                      onPress={() => cancel.mutate(id, { onSettled: close })}
                    >
                      {m.import_cancel_confirm()}
                    </Button>
                  </AlertDialog.Footer>
                </>
              )}
            </AlertDialog.Dialog>
          </AlertDialog.Container>
        </AlertDialog.Backdrop>
      </AlertDialog>
      {cancel.isError && <p role="alert">{m.account_refused()}</p>}
    </>
  )
}

function Running({ row }: { row: Import }) {
  const count = new Intl.NumberFormat(getLocale())
  return (
    <>
      <p>
        {row.announcedPins === null
          ? m.import_starting()
          : m.import_running({
              processed: count.format(row.processedPins),
              announced: count.format(row.announcedPins),
            })}
      </p>
      <CancelImport id={row.id} />
    </>
  )
}

// Keyed by the contract's closed union, so a state added there fails the typecheck here.
const VIEWS: Record<Import["state"], (row: Import) => ReactNode> = {
  AWAITING_ARCHIVE: (row) => (
    <>
      <p>{m.import_awaiting()}</p>
      <CancelImport id={row.id} />
    </>
  ),
  PENDING: (row) => (
    <>
      <p>{m.import_pending()}</p>
      <CancelImport id={row.id} />
    </>
  ),
  RUNNING: (row) => <Running row={row} />,
  COMPLETED: () => <p>{m.import_completed()}</p>,
  FAILED: (row) => <p>{dataFailure(row.failureCode)}</p>,
  CANCELLED: () => null,
  ABANDONED: () => null,
}

/** The latest import only (specification 2026-09-25, decision J). */
export function ImportSection() {
  const latest = useLatestImport()

  return (
    <section className="flex flex-col items-start gap-2">
      <h3 className="text-lg font-semibold">{m.import_heading()}</h3>
      <p className="text-muted">{m.import_note()}</p>
      {latest.isError && <p role="alert">{m.import_unreadable()}</p>}
      {latest.isSuccess && latest.data !== null && VIEWS[latest.data.state](latest.data)}
    </section>
  )
}
