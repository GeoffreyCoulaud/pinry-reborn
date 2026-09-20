/**
 * The user journeys the gate requires a test for. A block adds its own and none is ever
 * removed (docs/specs/2026-09-10-web-application.md, section 4.6).
 */
export const REQUIRED_JOURNEYS = [
  "open the application",
  "sign up",
  "sign in",
  "sign out",
  "session expiry",
  "session renewal",
  "browse the grid and load a second page",
  "open a pin",
  "create a pin from a URL through to the tile appearing",
  "create a pin by uploading a file",
  "drop an image on the grid to add a pin",
  "add several pins from one drop",
  "a failed download surfacing in the task centre",
  "an account with no pins",
  "choosing a theme against the system",
  "choosing the grid's order",
  "create a board and rename it",
  "open a board and browse its pins",
  "edit a pin's description, tags and boards",
  "replace a pin's image with a file",
  "delete a pin and restore it from the recycle bin",
  "add several selected pins to a board",
]

/** The file under `src/journeys/` that holds a journey's test. */
export function journeyTestFile(journey: string): string {
  return `${journey.replaceAll(" ", "-")}.journey.test.tsx`
}
