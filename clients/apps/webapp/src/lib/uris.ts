/** Only these become pins: the server fetches an address from its own position, and nothing else. */
const FETCHABLE = /^https?:\/\//i

/**
 * The addresses a drop states in `text/uri-list`: one per line, `#` opening a comment (RFC 2483).
 * A browser tab also hands over `blob:` and a file manager `file:`, neither of which names a
 * picture anyone but this browser can reach.
 */
export function urisFromDrop(text: string): string[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => FETCHABLE.test(line))
}
