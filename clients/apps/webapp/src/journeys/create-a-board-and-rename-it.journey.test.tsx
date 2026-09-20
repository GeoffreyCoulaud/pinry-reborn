import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import {
  board,
  boardRoutes,
  downloadsRoute,
  handshakeRoute,
  onePinPage,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

/** The account, its boards being whatever the journey left in the array it passes. */
function account(boards: Parameters<typeof boardRoutes>[0]) {
  server.use(
    sessionRoute(() => true),
    onePinPage(() => []),
    downloadsRoute(),
    handshakeRoute(),
    ...boardRoutes(boards),
  )
}

/** The row a board holds, which is where its own rename and delete live (decision O). */
function row(name: string) {
  return screen.getByRole("row", { name: new RegExp(name) })
}

async function fill(field: string, value: string) {
  const input = screen.getByRole("textbox", { name: field })
  await userEvent.clear(input)
  await userEvent.type(input, value)
}

/** The dialog's own submit, the header carrying a button worded the same way. */
async function submit(name: string) {
  await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name }))
}

describe("create a board and rename it", () => {
  it("Given the grid, Then the boards icon opens the screen and a board created joins the list", async () => {
    const boards = [board("Harbours", "Where the boats are", 3)]
    account(boards)

    renderApp("/")
    await userEvent.click(await screen.findByRole("link", { name: "Boards" }))

    expect(await screen.findByRole("heading", { name: "Boards" })).toBeVisible()
    // The list carries what the contract serves and nothing else: no cover, so no tile.
    expect(within(row("Harbours")).getByText("Where the boats are")).toBeVisible()
    expect(within(row("Harbours")).getByText("Pins: 3")).toBeVisible()

    await userEvent.click(screen.getByRole("button", { name: "Add a board" }))
    await fill("Name", "Mountains")
    await fill("Description", "Up high")
    await submit("Add a board")

    expect(await screen.findByRole("row", { name: /Mountains/ })).toBeVisible()
    expect(boards.map((one) => one.name)).toEqual(["Harbours", "Mountains"])
  })

  it("Given a board renamed, Then a reload of the address carries the new name", async () => {
    const boards = [board("Harbours", "Where the boats are")]
    account(boards)

    const { unmount } = renderApp("/boards")
    await userEvent.click(await screen.findByRole("button", { name: "Rename Harbours" }))
    await fill("Name", "Ports")
    await submit("Save")

    expect(await screen.findByRole("row", { name: /Ports/ })).toBeVisible()
    // The description the dialog opened on is sent back with the name: the route replaces the board.
    expect(boards).toEqual([{ ...boards[0], name: "Ports", description: "Where the boats are" }])

    unmount()
    renderApp("/boards")
    expect(await screen.findByRole("row", { name: /Ports/ })).toBeVisible()
  })

  it("Given a board deleted, Then it leaves the list", async () => {
    const boards = [board("Harbours"), board("Mountains")]
    account(boards)

    renderApp("/boards")
    await userEvent.click(await screen.findByRole("button", { name: "Delete Harbours" }))

    await waitFor(() => expect(screen.queryByRole("row", { name: /Harbours/ })).toBeNull())
    expect(screen.getByRole("row", { name: /Mountains/ })).toBeVisible()
    expect(boards.map((one) => one.name)).toEqual(["Mountains"])
  })

  it("Given a name the account already holds, Then the dialog says so", async () => {
    account([board("Harbours")])
    server.use(http.post("/api/v1/boards", () => new HttpResponse(null, { status: 409 })))

    renderApp("/boards")
    await userEvent.click(await screen.findByRole("button", { name: "Add a board" }))
    await fill("Name", "Harbours")
    await submit("Add a board")

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You already have a board with that name.",
    )
  })

  it("Given the credentials screen, Then it carries the theme control and no navigation icon", async () => {
    server.use(sessionRoute(() => false))

    renderApp("/sign-in")

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeVisible()
    expect(screen.getByRole("button", { name: "System theme" })).toBeVisible()
    // The bar is the same component on both screens, and the icons are the signed-in screens' own.
    expect(screen.queryByRole("link", { name: "Boards" })).toBeNull()
  })
})
