import { Button, Modal, Spinner } from "@heroui/react"
import { importIssue } from "../importIssues"
import { useImportIssues, type Import } from "../imports"
import { m } from "../paraglide/messages.js"
import { getLocale } from "../paraglide/runtime.js"

/** Mounted with the dialog's content, so the report is read once the dialog opens. */
function Issues({ row }: { row: Import }) {
  const issues = useImportIssues(row.id)
  const count = new Intl.NumberFormat(getLocale()).format(row.issueCount)

  return (
    <div className="flex flex-col items-start gap-3">
      {row.issueDetailTruncated && (
        <p className="text-muted">{m.import_issues_truncated({ count })}</p>
      )}
      <ul className="flex w-full flex-col gap-3">
        {issues.data?.pages
          .flatMap((page) => page.issues)
          .map((issue) => (
            <li key={issue.id} className="flex flex-col">
              <span className="text-foreground">{importIssue(issue.kind)}</span>
              <span className="flex flex-wrap gap-x-3 text-sm text-muted">
                {issue.line !== null && (
                  <span>{m.import_issue_line({ line: String(issue.line) })}</span>
                )}
                {issue.subject !== null && <span className="break-all">{issue.subject}</span>}
              </span>
            </li>
          ))}
      </ul>
      {issues.isPending && <Spinner aria-label={m.import_issues_loading()} />}
      {issues.isError && <p role="alert">{m.import_unreadable()}</p>}
      {issues.hasNextPage && (
        <Button
          variant="secondary"
          isDisabled={issues.isFetchingNextPage}
          onPress={() => void issues.fetchNextPage()}
        >
          {m.import_issues_more()}
        </Button>
      )}
    </div>
  )
}

/** The report's rows, paged with a "load more" (specification 2026-09-25, decision H). */
export function ImportIssuesDialog({ row }: { row: Import }) {
  const count = new Intl.NumberFormat(getLocale()).format(row.issueCount)

  return (
    <Modal>
      <Button variant="secondary">{m.import_issues_open({ count })}</Button>
      <Modal.Backdrop isDismissable>
        <Modal.Container size="md" scroll="inside">
          <Modal.Dialog>
            <Modal.CloseTrigger aria-label={m.close()} />
            <Modal.Header>
              <Modal.Heading level={2} className="pe-8">
                {m.import_issues_heading()}
              </Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              <Issues row={row} />
            </Modal.Body>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  )
}
