import { describe, expect, it } from "vitest"
import { downloadPollInterval, settledPinIds } from "./downloads"

const running = (pinId: string) => ({ pinId, status: "PENDING" as const })
const failed = (pinId: string) => ({ pinId, status: "FAILED" as const })

describe("the task centre's polling", () => {
  it("Given a download the server is still running, Then the list is asked again", () => {
    expect(downloadPollInterval([running("a")])).toBeGreaterThan(0)
  })

  it("Given an empty list, Then the polling stops", () => {
    expect(downloadPollInterval([])).toBe(false)
  })

  it("Given nothing but failures, Then the polling stops: a failure waits for the user", () => {
    expect(downloadPollInterval([failed("a")])).toBe(false)
  })

  it("Given a list the server has not answered yet, Then there is nothing to poll for", () => {
    expect(downloadPollInterval(undefined)).toBe(false)
  })
})

describe("what the grid has to reread", () => {
  it("Given a download that left the list, Then its pin now carries an image", () => {
    expect(settledPinIds([running("a")], [])).toEqual(["a"])
  })

  it("Given a download that failed, Then its pin's tile has a reason to show", () => {
    expect(settledPinIds([running("a")], [failed("a")])).toEqual(["a"])
  })

  it("Given two that settled at once, Then both pins are named", () => {
    expect(settledPinIds([running("a"), running("b")], [])).toEqual(["a", "b"])
  })

  it("Given a download still running, Then the grid is unchanged", () => {
    expect(settledPinIds([running("a")], [running("a")])).toEqual([])
  })

  it("Given a failure the user dismissed, Then the grid is unchanged", () => {
    expect(settledPinIds([failed("a")], [])).toEqual([])
  })
})
