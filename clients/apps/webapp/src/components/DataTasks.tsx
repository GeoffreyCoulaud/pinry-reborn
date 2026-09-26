import { Button, buttonVariants } from "@heroui/react"
import { useEffect, useState, type ReactNode } from "react"
import { exportFailure, importFailure } from "../dataFailures"
import { useLatestExport } from "../exports"
import { useLatestImport, useUpload } from "../imports"
import { dataNotices } from "../lib/notices"
import { m } from "../paraglide/messages.js"
import { downloadHref, exportReadiness } from "./ExportSection"
import { ImportCounters, UploadProgress, importProgress } from "./ImportSection"

// Per browser: another one shows a dismissed notice once more, which is harmless.
const DISMISSED = "pinry-dismissed-notices"

function readDismissed(): string[] {
  try {
    return JSON.parse(localStorage.getItem(DISMISSED) ?? "[]") as string[]
  } catch {
    return []
  }
}

function Item({ title, children }: { title: string; children: ReactNode }) {
  return (
    <li className="flex flex-col items-start gap-1 border-b border-separator py-2 last:border-0">
      <span className="font-medium">{title}</span>
      {children}
    </li>
  )
}

/**
 * The upload, the import and the export while they run, then what the latest of each ended with
 * until it is dismissed (specification 2026-09-25, decisions F4 and G3). One item per task.
 */
export function useDataTasks(): ReactNode[] {
  const upload = useUpload()
  const latestExport = useLatestExport()
  const latestImport = useLatestImport()
  const [dismissed, setDismissed] = useState(readDismissed)
  const exported = latestExport.data ?? null
  const imported = latestImport.data ?? null
  const notices = dataNotices(exported, imported, dismissed, Date.now())
  const read = latestExport.isSuccess && latestImport.isSuccess
  const kept = JSON.stringify(notices.dismissed)

  // Only once both rows are read: an unread row would drop the dismissal of its notice.
  useEffect(() => {
    try {
      if (read && kept === "[]") localStorage.removeItem(DISMISSED)
      else if (read) localStorage.setItem(DISMISSED, kept)
    } catch {
      // A private window forgets the dismissals with the tab.
    }
  }, [read, kept])

  const dismiss = (id: string) => (
    <Button size="sm" variant="secondary" onPress={() => setDismissed((ids) => [...ids, id])}>
      {m.dismiss()}
    </Button>
  )
  const tasks: ReactNode[] = []

  if (upload !== null) {
    tasks.push(
      <Item key="upload" title={m.task_import()}>
        <UploadProgress upload={upload} />
      </Item>,
    )
  } else if (imported?.state === "PENDING" || imported?.state === "RUNNING") {
    tasks.push(
      <Item key="import" title={m.task_import()}>
        <p className="text-sm">
          {imported.state === "PENDING" ? m.import_pending() : importProgress(imported)}
        </p>
      </Item>,
    )
  }
  if (exported?.state === "PENDING") {
    tasks.push(
      <Item key="export" title={m.task_export()}>
        <p className="text-sm">{m.export_pending()}</p>
      </Item>,
    )
  }
  if (notices.export !== null) {
    const row = notices.export
    const ready = row.state === "READY"
    tasks.push(
      <Item key={row.id} title={m.task_export()}>
        <p className="text-sm">{ready ? exportReadiness(row) : exportFailure(row.reasonCode)}</p>
        <div className="flex flex-wrap items-center gap-2">
          {ready && (
            <a className={buttonVariants({ size: "sm" })} href={downloadHref(row)}>
              {m.export_download()}
            </a>
          )}
          {dismiss(row.id)}
        </div>
      </Item>,
    )
  }
  if (notices.import !== null) {
    const row = notices.import
    tasks.push(
      <Item key={row.id} title={m.task_import()}>
        {row.state === "COMPLETED" ? (
          <>
            <p className="text-sm">{m.import_completed()}</p>
            <div className="text-sm">
              <ImportCounters row={row} />
            </div>
          </>
        ) : (
          <p className="text-sm">
            {row.state === "ABANDONED" ? m.import_abandoned() : importFailure(row.failureCode)}
          </p>
        )}
        {dismiss(row.id)}
      </Item>,
    )
  }
  return tasks
}
