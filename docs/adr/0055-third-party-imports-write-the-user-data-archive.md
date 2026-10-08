# 0055. Third-party imports write the user data archive

Status: Accepted
Date: 2026-10-08
Supersedes: `docs/adr/0054-third-party-imports-are-manual-and-keep-no-credential.md`
Specification: none yet. Two lots follow, each with its own.

## Context

ADR 0054 shaped the third-party import and asked for a proof of concept before any specification. The proof ran on
2026-10-07 (below). The operator then judged the design too heavy for a first version: tags carrying an origin and a
category, a plan step, a server call per batch, a source identity beside the medium's digest. This decision keeps
0054's scope and its credential rule and cuts the rest. Every answer below is the operator's.

## Decision

### Kept from ADR 0054

1. **Its decisions 1 to 4 stand**: the import reads what gallery-dl reads and names no site, it is manual and one-off,
   a site's own data export is not read, and a companion tool run on the user's machine runs gallery-dl, so no
   third-party credential reaches the server.
2. **One pin per medium** (0054, decision 13). **One import per user at a time**, which `uq_user_data_imports_active`
   already enforces (`api/api-persistence-sqlite/src/main/resources/dbmigration/1.26.sql`).

### The archive

3. **The importer writes the user data export's archive** (ADR 0015), and the user imports it from the web
   application's import screen. No new route, and no Pinry credential in the tool.
4. **A pin stays identified by its medium's digest** (ADR 0015, decision 2). The same image in two posts is one pin,
   attached to the post imported first: an accepted limit, no better identity being known. No position in the post is
   stored.
5. **Catch-up is gallery-dl's own download archive**, kept by the importer on the user's machine: a run lists the
   whole remote list and downloads only what is new. Losing it costs a full download; the server still skips
   duplicates by digest.
6. **The importer dates the pins in the remote list's order**, so the grid shows them as the site did.

### The data

7. **A pin gains three optional facts, whatever its entry path**: the publisher, the creators (a list) and the
   publication date. They are editable through the API and the web application, carried by the archive, and the
   catalogue's `q` (ADR 0040) matches the publisher's and the creators' names. No sort reads the date yet.
8. **A person is `{name, urls}`**, one shape for the publisher and for every creator. `urls` is a list: someone who
   publishes on several sites has several addresses, and a person with none is normal (a pin typed by hand,
   Danbooru naming an artist, a deleted Reddit account).
9. **Tags stay plain names**: no origin, no category.
10. **The source's title, text, upstream address and content classification are not stored**, and `description`
    keeps its name.
11. **A remote collection, `{name, url}`, links to a board**: its address is its identity, and it links to at most
    one board per owner. **Several remote collections may always link to the same board**, from one site or from
    several. With no plan step, a collection imported for the
    first time links to the board bearing its name, created if missing, and later follows the link whatever the
    board is now called. Deleting the board drops the link. **Linking to an existing board by name may file a
    collection where the user did not mean it**: accepted for this first version, the plan step being the fix.

### The importer

12. **It reads unprefixed neutral keys** from the metadata gallery-dl writes beside each file: `date` and `tags`,
    which are gallery-dl's own names, and `post_url`, `posted_by`, `creators` and `collection`, which no extractor
    writes today. The user fills the missing ones in their own gallery-dl configuration, with the `metadata`
    post-processor in `modify` mode. The project ships no per-site configuration.
13. **A `check` command runs gallery-dl on one address**, shows each neutral key filled or empty, suggests source keys
    from common names (`author`, `artist`, `tags_artist`), and prints the configuration line to copy. The user
    decides.
14. **It is written in Python, uses gallery-dl as a library, and runs through `uvx --from git+...`**, with no PyPI
    release. It is the repository's fourth root, held to the same norms as the others: test first, full branch
    coverage, `ruff check`, `ruff format --check`, strict mypy, ports and adapters.

### Rejected

15. **From ADR 0054**: tags with an origin and a category (18), a source identity by post address and position with
    its unique index (14), the known-pins call and the local marker (10, 11), `description` becoming `note` (17), the
    title, text, upstream address and classification (16). The plan step (9) moves to the backlog.
16. **Matching a collection to a board by name alone**: renaming either side, or two collections sharing a name,
    breaks it.
17. **Shipping gallery-dl configuration for common sites**: every site to keep up with, and a choice of which.
18. **Keys prefixed with the project's name.**

## Proof of concept

gallery-dl 1.32.15 through `uvx gallery-dl`, on 2026-10-07, against the operator's Reddit saved list
(`--cookies-from-browser firefox`) and the public Danbooru favourite group 64097 (`safebooru.donmai.us`).

- **`modify` then `json` writes the modified keys, types kept**: a list stays a list. In the reverse order the key is
  absent. A `\fE` expression builds a list of objects; `posted_by[name]` alone does not create its parent.
- **A Reddit saved list runs newest saved first**: a post dated 2024-09-24, saved during the proof, came first ahead
  of posts from 2026 (`-j --range 1-3` on the saved list).
- **A full listing of 325 saved posts** takes 18 requests in 26 s at Reddit's default page size, and 5 requests in
  10.5 s with `-o limit=100` (`-v -j`, requests counted in the log). The Danbooru group takes 2 requests for 27 posts.
- **Collections**: Danbooru gives `favgroup` or `pool` as `{id, name, ...}`; Reddit gives none.
- **`num` means two things**: the position in the post on Reddit, the position in the collection on a Danbooru
  favourite group.
- **A medium linked from a Reddit post keeps that post only with `parent-metadata` set to a name with no leading
  underscore**: the default, `_parent`, is private and the `json` mode drops it.
- **Reddit refuses even a public post without cookies** ("You've been blocked by network security").
- **Of gallery-dl's 289 extractor modules**, `date` is written by 194, `tags` by 115, `publisher` by 3 (where it means
  a publishing company), and `posted_by` and `creators` by none (a static search of the source for the key as a
  dictionary key).

## Consequences

- **The first lot** gives the pin its three facts and the board its remote collections, in the domain, the API, the
  web application and the archive. **The second** builds the importer against that archive.
- The backlog gains the import flow's improvements: the plan step, the import screen's discoverability and its
  interface.
- The items ADR 0054 filed in the backlog stand, except tags carrying an origin.
