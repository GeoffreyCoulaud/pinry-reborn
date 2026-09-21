# 0040. Search is a parameter of the catalogue

Status: Accepted
Date: 2026-09-21
Specification: `docs/specs/2026-09-21-the-header-searches-and-wears-the-name.md`, decisions A to F.
Related: `docs/adr/0024-three-projects-share-one-repository.md`, which makes
`contract/openapi.json` the surface this repository negotiates on, and which is why a route removed
here is a decision and not an edit.

## Context

`GET /api/v1/pins/search` loads every pin the caller owns into memory, scores each description
against the query with Jaro-Winkler and trigrams, and answers at most `limit` results sorted by
score. It reads descriptions alone, so a pin tagged `cat` is not found by `cat`. It has no cursor,
no order the caller chooses, and a body of its own, `PinSearchOutputDto`, which is not the body
`GET /api/v1/pins` answers.

`GET /api/v1/tags/search` does the same over the tags of one account.

A client that wants to search therefore leaves the catalogue: another route, another body, another
pagination, and a result set that stops at the first page. Searching inside one board is not
expressible at all.

## Decision

1. **Search is an optional `q` on the two catalogue routes**, `GET /api/v1/pins` and
   `GET /api/v1/boards/{boardId}/pins`. No route of its own.

   **Fails if** a search needs a body or a pagination the catalogue does not have. A page of
   results is a page of catalogue with one predicate more, and it is what makes searching inside a
   board free: that route already carries the catalogue's signature.

2. **A pin matches when its description contains `q`, or when the name of one of its tags does.**
   The whole of `q` is one term, never split on spaces, and the match is a case-insensitive
   substring over ASCII, SQLite's `LIKE` being what performs it.

   **Fails if** a user expects `cat dog` to find a pin tagged `cat` and described as a dog.
   Splitting asks what joins the parts, which is a question a filter does not have to answer.

3. **An absent `q` is the whole catalogue; a `q` that is present and blank earns 400 under the
   `code` `SEARCH_EMPTY_QUERY`**, thrown by the use case. `GET /api/v1/tags/search` answers the
   same `code` where it used to answer `VALIDATION_ERROR`.

   **Fails if** a client sends `q=` meaning "everything". It means a caller that did not mean to
   search, and the refusal says so where a silent full catalogue would not. No bean-validation
   annotation expresses it: `@NotBlank` refuses the absent parameter and `@Size(min = 1)` admits a
   single space, so the use case throws and `BaseErrorMapper` maps it.

4. **No relevance and no score.** The results keep the catalogue's own orders and its cursor.

   **Fails if** a user expects the best match first. `LIKE` gives no ranking, and inventing one in
   SQL buys an order nobody asked for; the order stays the caller's, through the `sort` parameter
   the catalogue already carries.

5. **`GET /api/v1/pins/search` is removed**, with `PinSearcher`, `PinSearchOutputDto`, the pin half
   of `SearchResultMapper` and `TextSimilarity`.

   **Fails if** something still calls it. Nothing does: the web application reaches it through no
   hand-written line, and the browser extension is not built.

6. **`GET /api/v1/tags/search` matches in the database too**: the names beginning with `q` first,
   then the names containing it, and no `score` in the body.

   **Fails if** a caller reads the score. A suggestion list has an order worth deciding, and what
   you are typing the start of comes first.

## Consequences

- **Similarity matching leaves the product.** A typo no longer finds anything, and nothing in
  SQLite restores it at the speed a keystroke needs. The backlog carries the study of a storage
  engine that can index for it.
- **The contract breaks, and says so.** `quarkus.smallrye-openapi.info-version` reaches `7.3.0`
  where `q` is added, `8.0.0` where the pin route is removed and `9.0.0` where the tag body loses
  its score. `contract/frozen/` holds no major, and the README states that the alpha breaks freely.
- **The scan moves from the JVM to SQLite.** A `%term%` match uses no index, so no index is added
  and no migration is written; what changes is that the page is built by the database instead of by
  loading the whole account into memory.
- **A cursor is read with the `q` it was minted under.** `findCursorPivot` resolves the pivot by
  identifier alone, so a cursor taken from an unfiltered page and replayed with a `q` names a row
  the filtered page cannot hold. Nothing guards the pair: a client whose query key carries the term
  restarts its pagination when the term changes, which is what the web application does.
- **No accent folding.** `cafe` does not find `café`, SQLite's `lower()` being ASCII-only, and a
  repository test asserts that miss so the day someone adds folding the test says so out loud.
