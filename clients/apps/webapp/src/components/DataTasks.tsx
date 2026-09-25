import type { ReactNode } from "react"
import { useLatestExport } from "../exports"
import { useLatestImport, useUpload } from "../imports"
import { m } from "../paraglide/messages.js"
import { UploadProgress, importProgress } from "./ImportSection"

function Item({ title, children }: { title: string; children: ReactNode }) {
  return (
    <li className="flex flex-col items-start gap-1 border-b border-separator py-2 last:border-0">
      <span className="font-medium">{title}</span>
      {children}
    </li>
  )
}

/**
 * The upload, the import and the export while they run (specification 2026-09-25, decisions F4
 * and G3). One item per task.
 */
export function useDataTasks(): ReactNode[] {
  const upload = useUpload()
  const exported = useLatestExport().data ?? null
  const imported = useLatestImport().data ?? null
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
  return tasks
}
