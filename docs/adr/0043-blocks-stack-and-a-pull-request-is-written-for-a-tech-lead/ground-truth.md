# Quiz reference answers

Three questions per pull request, answered by a reader who has only the title and the visible body:
1. Context: what is the situation before this pull request, and what is missing?
2. Why: why is this change needed now? What does it make possible, or what would break without it?
3. How: what is the approach, at the level of the architecture, and what justifies it over the obvious alternative?

## #219, block 10 (API and contract)

1. The API already serves export and import, and the web application is about to build their screens. The contract publishes the export's and the import's `state` as free strings, and publishes neither the import's chunk size nor its archive bound: those two exist only in the server's configuration.
2. The screens that follow need both. The client's behaviour depends on the state (whether to poll, which buttons, whether a download link exists), so it must know every state and be told at compile time when one is added; and it must cut an archive of up to 20 GiB into chunks the server accepts and refuse an oversized file before sending, which it cannot do without the bounds.
3. The two states become closed enums in the contract, a major version (18.0.0) because a new state is a real break for a client; the issue kind and the failure code stay open strings, because they only choose a sentence with a fallback, and closing them would make every new value a major. The two bounds are published in the handshake, which already publishes the image limits, rather than hard-coded in the client; they reach the presentation layer through a value built in the application module, because the presentation module may not depend on the module that holds the import configuration.

## #224, block 37 (web application: the upload)

1. The account screen shows the latest import and can cancel it, and a store that can send an archive in chunks exists, but nothing lets a user pick a file and start an import.
2. Without it, importing an archive is still impossible from the web application, which is the point of the lot. Archives can weigh up to 20 GiB, so the upload has to survive network cuts and screen changes rather than being one request.
3. A file picker opens an import and hands the file to the existing upload store, which sends one chunk at a time at the size the handshake publishes and resumes at the length the server reports; lost requests are retried, then the upload pauses behind a resume button; a file past the bound is refused before any request. The store lives above the router, so leaving the account screen does not stop the upload, and closing the tab asks for confirmation while bytes remain. The alternative, one request for the whole file, would restart from zero on any cut.

## #229, the closing block (review findings)

1. All the lot's code is written and stacked, not yet merged. The review of the whole lot found that once an upload stopped, the screen kept showing the local upload instead of the server's import, with Cancel as the only action, including when the answer to the final "close the upload" request was merely lost and the server may already be running the import. It also found smaller defects (a wrong message flashing after a successful upload, a refused cancel showing nothing, a race erasing the resume record of a just-opened import) and test and document hygiene.
2. As it stands, a user can be led to cancel an import that is actually running, which loses work; the lot also cannot close with its handoff, backlog and specification out of step with the code.
3. The server becomes the source of truth for the upload's end: the final request is retried like a chunk; an answer saying the import no longer awaits its archive means the server has moved on, and the screen gives way to the server's import once re-read; the local upload is dropped only once the cache holds the server's new state or the cancel has settled; a tab never erases its own upload's record. The same change makes the two-tabs case harmless, which the operator accepted as a known limit with no guard.
