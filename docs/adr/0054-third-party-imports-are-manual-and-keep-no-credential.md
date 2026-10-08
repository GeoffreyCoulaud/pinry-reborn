# 0054. Third-party imports are manual and keep no credential

Status: Superseded by `docs/adr/0055-third-party-imports-write-the-user-data-archive.md`
Date: 2026-10-07
Specification: none yet. The proof of concept below comes first, then a specification, then the implementation.

## Context

The backlog carried "Import from 3rd party sites" since 2026-10-04: a list of candidate sites, a one-off or periodic
sync, and gallery-dl as the lead. The operator refined it in a Discuss session with no lot on 2026-10-07; every answer
below is theirs. A third-party credential gives full access to an account, so where it may go is decided by design
here, not left to careful code.

## Decision

### Scope

1. **The import reads what gallery-dl reads**, and the project names no site and carries no code for one. A user
   points the tool at the lists they keep elsewhere: favourites, bookmarks, saved posts.
2. **An import is manual and one-off.** No periodic sync. A catch-up is a new run that adds only what is missing.
3. **A site's own data export is not read.** A user may still pass its addresses to gallery-dl, which takes a file of
   addresses natively.

### A companion tool, one import

4. **A companion tool, run by the user on their own machine, runs gallery-dl and writes an archive.** gallery-dl can
   read the browser's cookies. **No third-party credential ever reaches the server.**
5. **The tool reads one documented table of keys** from the metadata gallery-dl writes beside each file: one neutral
   name per fact, reusing gallery-dl's own names where many extractors already provide them (`title`, `tags`, `date`,
   `num`). Where a site names a fact otherwise, the user adds the missing keys in their own gallery-dl configuration,
   with the `metadata` post-processor in `modify` mode. The project ships no per-site configuration; its
   documentation works one example.
6. **A `check` command runs gallery-dl on one address and shows each key of the table, filled or empty, beside the
   raw keys the extractor offers.** It is what makes the configuration easy to write.
7. **The converter is generic and small**: it reads each file and its metadata, takes the table's keys, and writes
   the archive.
8. **The archive is the user data export's format, every medium's bytes included**, extended with the fields below
   and the remote collections. The server keeps one import, the user data import of ADR 0015. The format becomes a
   versioned contract between the tool and the server.
9. **The import gains a plan step.** The server lists the archive's boards and remote collections, the web application
   collects the user's choice for each, and only then does the run start. A native archive goes through it too.
10. **The tool asks the server which pins already exist before downloading their media.** One bounded, owner-scoped
    call takes a batch of (post address, position) and answers known or not per entry.
11. **The tool lists the whole remote list on every run**, skipping what the server knows. It keeps a local marker per
    list, the newest item it saw, and stops there on the next run; a missing marker means a full listing, and the
    report says which happened. The server is not asked about the marker: an archive never imported leaves a gap
    before it, an edge case accepted until it shows.
12. **One import runs per user at a time**, held by a partial unique index on the owner among active imports.

### The data

13. **One pin per medium**: a post with several media gives several pins.
14. **A pin's source identity is its post address plus its position in the post.** `sourceContextUrl` is stored as
    given, with no normalisation: a generic server cannot know a site's canonical form, and two spellings of one
    post are a rare duplicate. The position is a new field, empty for a single-medium post.
    `(owner, post address, position)` is unique in the database.
15. **A conflict is ignored**: no completion, no overwrite, no failure. It is counted in the run's summary and not
    reported as an issue (ADR 0015's skip rule).
16. **The pin stores the facts the source declares, each optional**: the publisher (name, profile address), the
    creators (a list: collaborations are normal), the upstream source address (stored, never followed), the
    publication date, the raw content classification in the source's own vocabulary, and the source's title and text.
17. **`description` becomes `note`**, the user's own text. The export breaks freely during the alpha.
18. **A source's tags enter the tag system with their origin and their category in that origin**; one name under two
    origins is two tags. This is a prerequisite: tags imported flat would lose their origin for good.
19. **There is no post entity.** Each pin of a post carries its own copy of the facts, and the shared post address
    is the grouping key, so a post entity can come later with no loss. A post whose fields each pin may override was
    weighed and rejected: three states per field, a resolution in every read and search, an ambiguous edit.
20. **A board links to zero or more remote collections, and a remote collection to at most one board per owner.** In
    the plan step the user picks, per collection: skip it, import without a board, its dedicated board (linked,
    created or existing; the default), or another board. A linked collection returns to its board without asking.

### Rejected

21. **The server reading the sites itself**, gallery-dl in the API and the user's cookie held in process memory for the
    run: it needed a vault, a type no layer may persist, a proxy confined to the source's domains, and a worker tied
    to the API's process, all to protect a credential the tool never sends.
22. **An archive of addresses that the server downloads**: two import paths, and a signed media address may expire
    before the download.
23. **Stopping after N known items in a row**: opaque, and nothing tells whether it stopped too early.
24. **Per-site adapters in the project, and readers for sites' data exports**: code to keep for each site, where
    gallery-dl already keeps it.

### The proof of concept

25. **Before the specification, a proof of concept answers, on a few test sources:**
    - whether `modify` mode changes the metadata the `json` mode then writes;
    - how much of the key table the extractors fill unconfigured;
    - whether a list runs newest saved first, which the marker assumes, and what a full listing costs on a site that
      pages small;
    - which remote collections gallery-dl lists, and under which keys.

## Consequences

- The repository gains a fourth project beside the three of ADR 0024. The specification chooses its packaging and
  how it gets a Pinry Reborn token, which the browser extension will need as well.
- The specification decides whether a pin in the recycle bin counts as known. A hard-deleted pin returns on a full
  listing.
- Every pin gains optional fields, whatever its entry path, and the export carries them.
- The import waits on tags carrying an origin. The backlog files what this decision opens and does not need:
  - **content classification**, for every pin and not only imported ones: by reason (erotic, gore, others) and by
    intensity, a threshold per viewing context (friends, colleagues), safe by default so that a forgotten setting
    shows nothing it should not. It reads the raw classification of decision 16 and replaces nothing. Tags were
    rejected for it: they flatten the fact, and a list of tags to hide fails open;
  - **tag implications and aliases**, edited in the web application. Implications form no cycle, aliases counted;
    an alias is suggested for one name under two origins; implications get no suggestion until visual understanding
    can offer some;
  - **board merging**, the merged boards' remote collection links joined;
  - **applying a field to every pin of one post**, by the shared post address of decision 19;
  - **adding a pin from an image post's public address through gallery-dl**, a fallback after yt-dlp with no
    credential. The earlier backlog item carried it as a by-product of a server reading the sites, which decision 21
    rejects; the browser extension will need it.
