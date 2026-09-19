import { describe, expect, it } from "vitest"
import en from "../../messages/en.json"
import fr from "../../messages/fr.json"
import { keysMissingFrom } from "./catalogues"

describe("the catalogues", () => {
  it("Given a locale that never translated a message, Then its key is named", () => {
    const incomplete = { greeting: "Bonjour" }
    const complete = { greeting: "Hello", farewell: "Goodbye" }

    expect(keysMissingFrom(incomplete, complete)).toEqual(["farewell"])
  })

  it("Given a theme name written into a sentence, Then French reads it as one", () => {
    // The three were `ListBox` items before this lot and kept their capital; `theme_current`
    // now interpolates them, and in French the fragment sits inside the sentence.
    const named = (theme: string) => fr.theme_current.replace("{theme}", theme)

    expect([named(fr.theme_system), named(fr.theme_light), named(fr.theme_dark)]).toEqual([
      "Thème système",
      "Thème clair",
      "Thème sombre",
    ])
  })

  it("Given the two catalogues, Then neither locale is missing a message the other carries", () => {
    expect(keysMissingFrom(fr, en)).toEqual([])
    expect(keysMissingFrom(en, fr)).toEqual([])
  })
})
