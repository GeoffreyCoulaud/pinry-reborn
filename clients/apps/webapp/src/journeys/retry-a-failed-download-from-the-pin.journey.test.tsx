import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import { downloadsRoute, handshakeRoute, onePinPage, pin, renderApp, sessionRoute } from "../test/app"
import { server } from "../test/server"

describe("retry a failed download from the pin", () => {
  it("Given a download that failed on the way, Then Retry fetches the pin's address and the pin stays open", async () => {
    const user = userEvent.setup()
    const failed = {
      ...pin("a cat asleep", { status: "FAILED", reasonCode: "UNREACHABLE" }),
      sourceMediaUrl: "https://example.test/corrected.png",
    }
    let sent: unknown = null
    server.use(
      sessionRoute(() => true),
      onePinPage(() => [sent === null ? failed : { ...failed, image: { status: "PENDING" } }]),
      downloadsRoute(),
      handshakeRoute(),
      http.put("/api/v1/pins/:pinId/image", async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ status: "PENDING" }, { status: 202 })
      }),
    )

    renderApp("/")
    await user.click(await screen.findByText(m.reason_unreachable()))
    const dialog = await screen.findByRole("dialog")
    expect(within(dialog).getByText(m.reason_unreachable())).toBeVisible()

    await user.click(within(dialog).getByRole("button", { name: m.retry() }))

    await waitFor(() => expect(sent).toEqual({ sourceUrl: failed.sourceMediaUrl }))
    // A pending pin leaves the grid's tiles, and the dialog keeps it all the same.
    expect(await within(dialog).findByText(m.task_running())).toBeVisible()
    expect(screen.getByRole("dialog")).toBe(dialog)
  })

  it("Given a download the address refused, Then the pin says why and offers no Retry", async () => {
    const user = userEvent.setup()
    const failed = {
      ...pin("a cat asleep", { status: "FAILED", reasonCode: "FETCH_FAILED" }),
      sourceMediaUrl: "https://example.test/cat.png",
    }
    server.use(sessionRoute(() => true), onePinPage(() => [failed]), downloadsRoute(), handshakeRoute())

    renderApp("/")
    await user.click(await screen.findByText(m.reason_fetch_failed()))
    const dialog = await screen.findByRole("dialog")

    expect(within(dialog).getByText(m.reason_fetch_failed())).toBeVisible()
    expect(within(dialog).queryByRole("button", { name: m.retry() })).toBeNull()
  })
})
