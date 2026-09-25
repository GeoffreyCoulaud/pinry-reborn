import { AlertDialog, Button, Label, ProgressBar, buttonVariants } from "@heroui/react"
import { useState, type ReactNode } from "react"
import { dataFailure } from "../dataFailures"
import { importRefusal } from "../dataRefusals"
import { useHandshake } from "../images"
import {
  resumeUpload,
  useCancelImport,
  useLatestImport,
  useStartImport,
  useUpload,
  type Import,
  type Upload,
} from "../imports"
import { AccountRefusal } from "../me"
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

/** A file past the deployment's bound is refused here, before an import is even opened. */
function ChooseArchive() {
  const limits = useHandshake().data?.limits
  const start = useStartImport()
  const [tooLarge, setTooLarge] = useState(false)

  return (
    <>
      {/* A file picker is no HeroUI control, so the label borrows the variant instead. */}
      <label className={buttonVariants()}>
        {m.import_choose()}
        <input
          type="file"
          accept=".zip,application/zip"
          className="sr-only"
          disabled={limits === undefined || start.isPending}
          onChange={(event) => {
            const file = event.currentTarget.files?.[0]
            if (file === undefined || limits === undefined) return
            setTooLarge(file.size > limits.maxImportArchiveBytes)
            if (file.size <= limits.maxImportArchiveBytes) {
              start.mutate({ file, chunkBytes: limits.maxImportChunkBytes })
            }
          }}
        />
      </label>
      {tooLarge && <p role="alert">{m.import_too_large()}</p>}
      {start.error !== null && (
        <p role="alert">
          {importRefusal(start.error instanceof AccountRefusal ? start.error.code : null)}
        </p>
      )}
    </>
  )
}

function Uploading({ upload }: { upload: Upload }) {
  return (
    <>
      <ProgressBar className="w-full max-w-sm" value={upload.sent} maxValue={upload.file.size}>
        <Label>{m.import_sending()}</Label>
        <ProgressBar.Output />
        <ProgressBar.Track>
          <ProgressBar.Fill />
        </ProgressBar.Track>
      </ProgressBar>
      {upload.state === "PAUSED" && (
        <>
          <p>{m.import_paused()}</p>
          <Button onPress={resumeUpload}>{m.import_resume()}</Button>
        </>
      )}
      {upload.state === "STOPPED" && <p role="alert">{importRefusal(upload.code)}</p>}
      <CancelImport id={upload.importId} />
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
  COMPLETED: () => (
    <>
      <p>{m.import_completed()}</p>
      <ChooseArchive />
    </>
  ),
  FAILED: (row) => (
    <>
      <p>{dataFailure(row.failureCode)}</p>
      <ChooseArchive />
    </>
  ),
  CANCELLED: () => <ChooseArchive />,
  ABANDONED: () => <ChooseArchive />,
}

/** The latest import only, and the upload this tab holds, which outlives the screen. */
export function ImportSection() {
  const latest = useLatestImport()
  const upload = useUpload()

  return (
    <section className="flex flex-col items-start gap-2">
      <h3 className="text-lg font-semibold">{m.import_heading()}</h3>
      <p className="text-muted">{m.import_note()}</p>
      {latest.isError && <p role="alert">{m.import_unreadable()}</p>}
      {upload !== null ? (
        <Uploading upload={upload} />
      ) : (
        latest.isSuccess &&
        (latest.data === null ? <ChooseArchive /> : VIEWS[latest.data.state](latest.data))
      )}
    </section>
  )
}
