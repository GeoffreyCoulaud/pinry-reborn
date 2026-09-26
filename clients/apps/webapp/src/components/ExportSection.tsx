import { AlertDialog, Button, Input, Label, TextField, buttonVariants } from "@heroui/react"
import type { ReactNode } from "react"
import { exportFailure } from "../dataFailures"
import { exportRefusal } from "../dataRefusals"
import { useDeleteExport, useLatestExport, useRequestExport, type Export } from "../exports"
import { AccountRefusal } from "../me"
import { m } from "../paraglide/messages.js"
import { getLocale } from "../paraglide/runtime.js"

/** The password in a dialog, as deleting the account asks it: `X-Reauthentication` requires it. */
function RequestExport() {
  const request = useRequestExport()

  return (
    <AlertDialog>
      <Button>{m.export_request()}</Button>
      <AlertDialog.Backdrop>
        <AlertDialog.Container size="sm">
          <AlertDialog.Dialog>
            {({ close }) => (
              <form
                className="flex flex-col gap-3"
                onSubmit={(event) => {
                  event.preventDefault()
                  const fields = new FormData(event.currentTarget)
                  request.mutate(String(fields.get("password")), { onSuccess: close })
                }}
              >
                <AlertDialog.Heading>{m.export_question()}</AlertDialog.Heading>
                <AlertDialog.Body className="flex flex-col gap-3">
                  {m.export_warning()}
                  <TextField
                    name="password"
                    type="password"
                    isRequired
                    autoComplete="current-password"
                  >
                    <Label>{m.password()}</Label>
                    <Input />
                  </TextField>
                  {request.error !== null && (
                    <p role="alert">
                      {exportRefusal(
                        request.error instanceof AccountRefusal ? request.error.code : null,
                      )}
                    </p>
                  )}
                </AlertDialog.Body>
                <AlertDialog.Footer>
                  <Button variant="ghost" onPress={close}>
                    {m.cancel()}
                  </Button>
                  <Button type="submit" isDisabled={request.isPending}>
                    {m.export_confirm()}
                  </Button>
                </AlertDialog.Footer>
              </form>
            )}
          </AlertDialog.Dialog>
        </AlertDialog.Container>
      </AlertDialog.Backdrop>
    </AlertDialog>
  )
}

/** The archive's size and expiry, which the task centre's notice repeats. */
export function exportReadiness(row: Export): string {
  const size = new Intl.NumberFormat(getLocale(), {
    style: "unit",
    unit: "megabyte",
    maximumSignificantDigits: 3,
  }).format((row.byteSize ?? 0) / 1_000_000)
  const date = new Intl.DateTimeFormat(getLocale(), { dateStyle: "long" }).format(
    new Date(row.expiresAt ?? row.requestedAt),
  )
  return m.export_ready({ size, date })
}

/** A plain link: the cookie authenticates it, and the browser streams the archive to disk. */
export const downloadHref = (row: Export) => `/api/v1/me/exports/${row.id}/download`

function ReadyExport({ row }: { row: Export }) {
  const remove = useDeleteExport()

  return (
    <>
      <p>{exportReadiness(row)}</p>
      <div className="flex flex-wrap gap-2">
        <a className={buttonVariants()} href={downloadHref(row)}>
          {m.export_download()}
        </a>
        <Button variant="ghost" isDisabled={remove.isPending} onPress={() => remove.mutate(row.id)}>
          {m.export_delete()}
        </Button>
      </div>
      {remove.isError && <p role="alert">{m.account_refused()}</p>}
    </>
  )
}

// Keyed by the contract's closed union, so a state added there fails the typecheck here.
const VIEWS: Record<Export["state"], (row: Export) => ReactNode> = {
  PENDING: () => <p>{m.export_pending()}</p>,
  READY: (row) => <ReadyExport row={row} />,
  FAILED: (row) => (
    <>
      <p>{exportFailure(row.reasonCode)}</p>
      <RequestExport />
    </>
  ),
  GONE: () => <RequestExport />,
}

/** The latest export only, with no history (specification 2026-09-25, decision J). */
export function ExportSection() {
  const latest = useLatestExport()

  return (
    <section className="flex flex-col items-start gap-2">
      <h3 className="text-lg font-semibold">{m.export_heading()}</h3>
      <p className="text-muted">{m.export_note()}</p>
      {latest.isError && <p role="alert">{m.export_unreadable()}</p>}
      {latest.isSuccess &&
        (latest.data === null ? <RequestExport /> : VIEWS[latest.data.state](latest.data))}
    </section>
  )
}
