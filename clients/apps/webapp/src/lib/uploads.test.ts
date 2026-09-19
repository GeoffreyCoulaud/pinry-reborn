import { describe, expect, it } from "vitest"
import { isImageFile, uploadRefusal } from "./uploads"

const LIMITS = { maxFileBytes: 1000, maxPixels: 10_000 }

describe("the upload a deployment refuses", () => {
  it("Given a file inside both limits, Then nothing is refused", () => {
    expect(uploadRefusal({ size: 1000, width: 100, height: 100 }, LIMITS)).toBeNull()
  })

  it("Given more bytes than the deployment stores, Then the file is refused for its weight", () => {
    expect(uploadRefusal({ size: 1001, width: 1, height: 1 }, LIMITS)).toBe("TOO_MANY_BYTES")
  })

  it("Given more pixels than the deployment decodes, Then the file is refused for its size", () => {
    expect(uploadRefusal({ size: 1, width: 101, height: 100 }, LIMITS)).toBe("TOO_MANY_PIXELS")
  })

  it("Given a handshake that has not answered yet, Then the server is what refuses", () => {
    expect(uploadRefusal({ size: 10_000, width: 10_000, height: 10_000 }, undefined)).toBeNull()
  })
})

describe("the file a pin's image may come from", () => {
  it("Given a media type the browser decodes as a picture, Then the file is an image", () => {
    expect(isImageFile({ type: "image/png" })).toBe(true)
  })

  it("Given anything else, Then it is not, whatever a drop or a picker handed over", () => {
    expect(isImageFile({ type: "application/pdf" })).toBe(false)
    expect(isImageFile({ type: "" })).toBe(false)
  })
})
