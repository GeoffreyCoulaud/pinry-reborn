import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import { passwordRefusal } from "../passwordRefusals"

describe("the sentence a refused password write shows", () => {
  it("Given the wrong current password, Then the user is told which field is wrong", () => {
    expect(passwordRefusal("REAUTHENTICATION_FAILED")).toBe(m.password_wrong())
  })

  it("Given a password this account has held, Then the user is told to pick another", () => {
    expect(passwordRefusal("PASSWORD_PREVIOUSLY_USED")).toBe(m.password_previously_used())
  })

  it("Given a change the API judges too soon, Then the user is told to wait", () => {
    expect(passwordRefusal("PASSWORD_CHANGED_TOO_SOON")).toBe(m.password_changed_too_soon())
  })

  it("Given another change that landed first, Then the user is told to try again", () => {
    expect(passwordRefusal("PASSWORD_CHANGE_COLLISION")).toBe(m.password_change_collision())
  })

  it("Given the attempt limiter, Then the user is told about the attempts", () => {
    expect(passwordRefusal("TOO_MANY_AUTHENTICATION_ATTEMPTS")).toBe(m.too_many_attempts())
  })

  it("Given the two refusals that share a 429, Then they do not share a sentence", () => {
    // The status cannot tell them apart, so a client reading it sends the user round in a circle
    // (specification 2026-09-22, decision D).
    expect(passwordRefusal("PASSWORD_CHANGED_TOO_SOON")).not.toBe(
      passwordRefusal("TOO_MANY_AUTHENTICATION_ATTEMPTS"),
    )
  })

  it("Given the client's own encoding defect, Then the user gets the general sentence", () => {
    expect(passwordRefusal("UNSUPPORTED_REAUTHENTICATION_FACTOR")).toBe(m.account_refused())
  })

  it("Given a refusal carrying no code at all, Then the user gets the general sentence", () => {
    expect(passwordRefusal(null)).toBe(m.account_refused())
  })

  it("Given a code `Object.prototype` answers for, Then the user gets the general sentence", () => {
    // The code is the server's own string and the one input here the bundle does not control.
    // `constructor` reaches an object, which React throws on as a child, taking the screen down.
    expect(passwordRefusal("constructor")).toBe(m.account_refused())
    expect(passwordRefusal("toString")).toBe(m.account_refused())
  })
})
