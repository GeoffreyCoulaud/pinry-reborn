import { describe, expect, it } from "vitest"
import { passwordFactor } from "./reauthentication"

describe("the reauthentication factor a deletion carries", () => {
  it("Given a password whose bytes end on a plus, Then the header carries a hyphen", () => {
    // `??>` encodes to `Pz8+`, which `Base64.getUrlDecoder()` refuses with `Illegal base64
    // character 2b` and the parser answers 400 for (specification 2026-09-22, decision G).
    expect(passwordFactor("??>")).toBe("password Pz8-")
  })

  it("Given a password whose bytes end on a slash, Then the header carries an underscore", () => {
    expect(passwordFactor("???")).toBe("password Pz8_")
  })

  it("Given both, Then nothing outside the base64url alphabet reaches the header", () => {
    // The character class and not a round trip through a decoder: a decoder that accepts both
    // alphabets passes on the very bytes the API's refuses.
    expect(passwordFactor("??>???")).toMatch(/^password [A-Za-z0-9_-]+=*$/)
  })

  it("Given a non-ASCII password, Then its UTF-8 bytes are what is encoded", () => {
    // Two bytes for one character: encoding per character would produce `6Q==` and decode to
    // something the API never stored.
    expect(passwordFactor("é")).toBe("password w6k=")
  })

  it("Given a length btoa pads, Then the padding stays", () => {
    expect(passwordFactor("secre")).toBe("password c2VjcmU=")
  })
})
