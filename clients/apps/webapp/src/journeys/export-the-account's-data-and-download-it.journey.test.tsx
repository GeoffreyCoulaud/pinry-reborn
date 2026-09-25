import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import { downloadsRoute, exportsRoute, renderApp, sessionRoute } from "../test/app"
import { server } from "../test/server"

const ACCOUNT = { id: "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11", name: "ada" }
const EXPORT_ID = "7c1e2a4b-5d6f-4a8b-9c0d-1e2f3a4b5c6d"

/** An export row as the API answers one, in the state the journey names. */
function exportRow(state: string, fields: Record<string, unknown> = {}) {
  return {
    id: EXPORT_ID,
    state,
    requestedAt: "2026-09-23T12:00:00Z",
    completedAt: null,
    expiresAt: null,
    byteSize: null,
    mediaType: null,
    sha256: null,
    failureCode: null,
    formatVersion: 1,
    ...fields,
  }
}

/** The refusal as the API sends one: a problem body whose `code` is what the dialog reads. */
function refused(status: number, code: string) {
  return HttpResponse.json({ status, code }, { status })
}

/** The account screen over the exports the journey serves, the dialog's routes being its own. */
async function openTheAccount(rows: () => unknown[], ...routes: Parameters<typeof server.use>) {
  server.use(
    sessionRoute(() => true),
    http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
    downloadsRoute(),
    exportsRoute(rows),
    ...routes,
  )
  renderApp("/account")
  const user = userEvent.setup()
  await screen.findByRole("heading", { name: m.export_heading() })
  return user
}

/** The dialog opened, the password typed, and the request sent. */
async function requestAnExport(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: m.export_request() }))
  await user.type(await screen.findByLabelText(m.password()), "correct horse")
  await user.click(screen.getByRole("button", { name: m.export_confirm() }))
}

describe("export the account's data and download it", () => {
  it("Given the password, Then the archive is prepared, downloaded and deleted", async () => {
    let rows: unknown[] = []
    let reads = 0
    let factor: string | null = null
    let deleted: string | null = null
    const ready = exportRow("READY", {
      completedAt: "2026-09-23T12:05:00Z",
      // Noon, so the date reads the same in every time zone the gate might run in.
      expiresAt: "2026-09-30T12:00:00Z",
      byteSize: 1_500_000,
    })
    const pending = exportRow("PENDING")
    const user = await openTheAccount(
      () => {
        // Pending on the first read after the request and ready on the next: only a poll gets there.
        if (rows[0] === pending && reads++ > 0) rows = [ready]
        return rows
      },
      http.post("/api/v1/me/exports", ({ request }) => {
        factor = request.headers.get("X-Reauthentication")
        rows = [pending]
        return HttpResponse.json(pending, { status: 202 })
      }),
      http.delete("/api/v1/me/exports/:id", ({ params }) => {
        deleted = String(params.id)
        rows = [exportRow("DELETED")]
        return new HttpResponse(null, { status: 204 })
      }),
    )

    await requestAnExport(user)

    expect(await screen.findByText(m.export_pending())).toBeVisible()
    expect(factor).toBe("password Y29ycmVjdCBob3JzZQ==")
    const link = await screen.findByRole("link", { name: m.export_download() })
    // A plain link: the cookie authenticates it and the browser streams the archive to disk.
    expect(link).toHaveAttribute("href", `/api/v1/me/exports/${EXPORT_ID}/download`)
    const readiness = "The archive is ready: 1.5 MB, available until September 30, 2026."
    expect(screen.getByText(readiness)).toBeVisible()

    await user.click(screen.getByRole("button", { name: m.export_delete() }))

    expect(await screen.findByRole("button", { name: m.export_request() })).toBeVisible()
    expect(deleted).toBe(EXPORT_ID)
    expect(screen.queryByRole("link", { name: m.export_download() })).toBeNull()
  })

  it("Given the refusals, Then each code has its sentence and an unknown one the general", async () => {
    const codes = ["EXPORT_TOO_SOON", "TOO_MANY_AUTHENTICATION_ATTEMPTS", "SOMETHING_NEW"]
    const user = await openTheAccount(
      () => [],
      http.post("/api/v1/me/exports", () => refused(429, codes.shift() ?? "")),
    )

    await requestAnExport(user)
    const alert = await screen.findByRole("alert")
    expect(alert).toHaveTextContent(m.export_too_soon())

    // Both are 429: what is read is the code the body carries, not the status.
    await user.click(screen.getByRole("button", { name: m.export_confirm() }))
    expect(await screen.findByText(m.too_many_attempts())).toBeVisible()

    await user.click(screen.getByRole("button", { name: m.export_confirm() }))
    expect(await screen.findByText(m.account_refused())).toBeVisible()
    expect(codes).toHaveLength(0)
  })

  it("Given an expired archive as the latest, Then the request comes back and no link", async () => {
    await openTheAccount(() => [exportRow("EXPIRED")])

    expect(await screen.findByRole("button", { name: m.export_request() })).toBeVisible()
    expect(screen.queryByRole("link", { name: m.export_download() })).toBeNull()
  })

  it("Given a failed export, Then its code's sentence shows beside the request", async () => {
    await openTheAccount(() => [exportRow("FAILED", { failureCode: "DISK_FULL" })])

    expect(await screen.findByText(m.failure_disk_full())).toBeVisible()
    expect(screen.getByRole("button", { name: m.export_request() })).toBeVisible()
  })

  it("Given a failure code this bundle does not know, Then the general sentence shows", async () => {
    await openTheAccount(() => [exportRow("FAILED", { failureCode: "SOMETHING_NEW" })])

    expect(await screen.findByText(m.failure_unknown())).toBeVisible()
    expect(document.body).not.toHaveTextContent("undefined")
  })
})
