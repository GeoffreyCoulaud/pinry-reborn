import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { File as NodeFile } from "node:buffer"
import { afterEach, describe, expect, it } from "vitest"
import { dropUpload } from "../imports"
import { m } from "../paraglide/messages.js"
import {
  downloadsRoute,
  exportRow,
  exportsRoute,
  handshakeRoute,
  importRow,
  importsRoute,
  pinsRoute,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const ACCOUNT = { id: "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11", name: "ada" }

/** Every screen carries the centre, so the journey reads it on the home screen. */
function serve(exports: () => unknown[], imports: () => unknown[], ...routes: Parameters<typeof server.use>) {
  server.use(
    sessionRoute(() => true),
    http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
    pinsRoute([]),
    downloadsRoute(),
    handshakeRoute({ maxImportChunkBytes: 4 }),
    exportsRoute(exports),
    importsRoute(imports),
    ...routes,
  )
}

// The store is module state, and module state outlives a journey (specification, section 9).
afterEach(() => dropUpload())

describe("an upload and an export surfacing in the task centre", () => {
  it("Given an upload started on the account screen, Then the home screen's centre follows it", async () => {
    let latest: unknown[] = []
    let gate = () => {}
    serve(
      () => [],
      () => latest,
      http.post("/api/v1/me/imports", () => {
        const opened = importRow("AWAITING_ARCHIVE")
        latest = [opened]
        return HttpResponse.json(opened, { status: 202 })
      }),
      http.put("/api/v1/me/imports/:id/archive", async ({ request }) => {
        const offset = Number(new URL(request.url).searchParams.get("offset"))
        // The second chunk waits, so the upload is caught running.
        if (offset > 0) await new Promise<void>((open) => (gate = open))
        return HttpResponse.json(importRow("AWAITING_ARCHIVE", { uploadedBytes: offset + 4 }))
      }),
      http.post("/api/v1/me/imports/:id/archive/complete", () => {
        const running = importRow("RUNNING", { announcedPins: 10, processedPins: 3 })
        latest = [running]
        return HttpResponse.json(running, { status: 202 })
      }),
    )
    const { router } = renderApp("/account")
    const user = userEvent.setup()
    const picker = await screen.findByLabelText(m.import_choose())
    await waitFor(() => expect(picker).toBeEnabled())
    // Node's own `File`: the fetch under test sends jsdom's slices as the text "undefined".
    await user.upload(picker, new NodeFile(["01234567"], "pinry.zip") as File)

    await router.navigate({ to: "/" })
    await user.click(await screen.findByRole("button", { name: "Tasks (1)" }))

    const progress = await screen.findByRole("progressbar", { name: m.import_sending() })
    expect(progress).toHaveAttribute("aria-valuenow", "4")

    gate()

    // Once the archive is closed, the centre follows the import on the server.
    expect(await screen.findByText("Importing: 3 of 10 pins.")).toBeVisible()
  })

  it("Given an export being prepared, Then the centre counts it and says so", async () => {
    serve(() => [exportRow("PENDING")], () => [])
    renderApp("/")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Tasks (1)" }))

    expect(await screen.findByText(m.export_pending())).toBeVisible()
  })
})
