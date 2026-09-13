# Handoff: the P1 band's simple and actionable fixes

Date: 2026-09-13
Branches: `fix/contract-names-and-session-401` (#114), `feat/task-centre-on-create-pin` (#115),
`feat/manual-theme-switch` (#116), and `docs/closing-the-p1-simple-fixes` for this file
Tier: Direct, on the operator's decision. The recommended tier was Spec, block 1 changing the public
surface; the operator refused the trigger on the ground that the repository owns both the emitter and
the consumer of that surface, so a contract break costs a regeneration and nothing else. Direct
skips Discuss, Spec and both reviews, and the lead wrote every phase inline.

## Current state

`dagger call gate` green at each block's tip and at this one's. Continuous integration green on all
three pull requests, each merged by rebase.

The lot is **486 counted lines** against `lot/0.13.0-workflow-mandate-before-argument`, of which the
generated `contract/openapi.json` is outside the count. No block came near a bound: 45 production
lines under `api/` against 200 for block 1, 8 under `clients/` against 400 for block 2, 129 for
block 3.

The contract is **5.0.0**. Its `info.version` took a major because two components were renamed, and
`contract-guard` accepted the break once the version admitted it.

## What was built

**Block 1, #114.** `SessionTransport` becomes `SessionTransportDto` and moves from
`presentation/quarkus/security/` to `presentation/quarkus/dtos/common/`, beside `CursorDirectionDto`,
the other wire enumeration that crosses input and the rest of presentation. It was the only wire
enumeration carrying neither the suffix nor the package. `SecurityIdentity.getSessionTransport()`
keeps its name: it is an accessor, not a wire type. The bearer security scheme, named
`SecurityScheme`, becomes `BearerScheme`; `CookieScheme` already named what it was and is untouched.
`POST /api/v1/sessions` declares the `401` it has always answered, as `application/problem+json` with
`ProblemDetail`. `@pinry-reborn/auth` follows the schema key, its own exported name staying
`SessionTransport`, which is the client's vocabulary rather than the wire's.

**Block 2, #115.** `<TaskCentre />` joins the creation screen's header. The component was already
built and rendered in exactly one place, `Home`'s header, so `/pins/new` requested downloads and
showed none.

**Block 3, #116.** The manual theme switch of question T. Three states and not two, `system` the
default, because question T asked for light and dark following the system *with* a switch: a
two-state toggle cannot hand the theme back. A native `<select>` carries it. The choice lives in
`localStorage` under `pinry-theme`, every read and write guarded, a private window throwing rather
than answering.

The design decision that carries block 3: **the resolved theme is written to `data-theme` on the
root element, never the preference.** The stylesheet therefore reads no media query of its own. One
`@custom-variant dark (&:where([data-theme="dark"], [data-theme="dark"] *))` carries every `dark:`
utility, and `[data-theme]` carries `color-scheme` for the form controls. A `matchMedia` listener
keeps `system` following the machine while the page lives, and `main.tsx` calls `paintStoredTheme()`
before the first render so a chosen theme costs no flash of the system's own. The form is Tailwind's
own, from its dark mode documentation, "Using a data attribute". The module split follows the one
already here: `src/lib/theme.ts` pure and under the 100% bound `vite.config.ts` sets on `src/lib/**`,
`src/theme.ts` the storage and DOM layer, exactly as `lib/downloads.ts` sits under `images.ts`.

## The backlog item that was refuted

The item **"`POST /api/v1/sessions` declares no `401` although it answers one"** carried a second
claim: that `MeImageDownloadController` is the only controller declaring `application/problem+json`
with `ProblemDetail` "where the others declare a bare description". Measured before building:
`MeExportController` declares 13 error responses, `MeImportController` 15, `BoardController` 2,
`MeImageDownloadController` 2, **every one of them with `ProblemDetail`**, and no controller anywhere
declares an error with a bare description. The whole gap was `SessionController`, six responses and
zero errors, which block 1 closed. Nothing was built for the second claim because there was nothing
to build.

## Pitfalls

- **The bearer scheme's declared name no longer matches `quarkus.smallrye-openapi.security-scheme-name`.**
  SmallRye auto-stamps a `{"SecurityScheme": []}` requirement on every protected operation from that
  default, and nothing dangles only because `SessionSecurityRequirementFilter` replaces each
  operation's security requirement wholesale with both scheme names. Delete the filter and the refs
  dangle silently.
- **A journey that counts polls on `/pins/new` now counts the creation screen's own.**
  `create-a-pin-from-a-URL-through-to-the-tile-appearing` settled its download on the second
  `GET /api/v1/me/image-downloads`; block 2 made that screen poll from its first render, so the
  download settled before the submit and the `Downloads (1)` assertion failed. The counter now starts
  at the image request, which is what "the second poll" was always meant to mean.
- **jsdom implements no `matchMedia`.** `src/test/setup.ts` carries a stub beside the
  `IntersectionObserver` and `createImageBitmap` ones. Removing it takes every journey down at once.
- **`src/lib/journeys.ts` is a registry the gate checks.** A new file under `src/journeys/` fails
  `journeys.test.ts` until the journey is declared there, and the failure names neither the file nor
  the fix.
- **`gh pr checks --watch` exits 0 when no check is registered yet.** Run against a branch pushed a
  moment earlier it reports "no checks reported" and exits green, which reads exactly like a pass.
  Confirm a check is pending before trusting the watch.
- **Without its script, `dark:` utilities do not follow the system**, only `color-scheme` does. Moot
  for an application that renders nothing without its script.

## Not validated

- **`main.tsx`'s pre-paint is exercised by no test.** `renderApp` mounts the router, not the entry
  point, so what the theme journey holds is that a stored choice is read back and painted. The
  absence of a flash on a real first load has been reasoned about, not observed.
- **No block ran `dagger call smoke`.** Block 1 changed a generated contract and two Kotlin names,
  neither of which reaches a deployment value the suite leaves alone, so the image was built by
  continuous integration and started by nothing here.
- **The theme was read back from the compiled stylesheet, not from a browser.** `dark:bg-neutral-900`
  emits `:where([data-theme=dark], [data-theme=dark] *)`, which is the evidence that the variant
  compiled; no one looked at the two themes on screen.

## Tier-2 questions asked

None. No defect surfaced that was larger than its block and reachable inside this lot.

## The review

Tier Direct runs neither the specification review nor the holistic one. The operator reviewed #116
by hand and left three comments, all one finding: comments that restate what the code says.
`ThemeSwitch`'s KDoc was deleted, the `matchMedia` stub's three lines became one naming the jsdom
gap, and both comments in `styles.css` were deleted. The comments in `theme.ts` and in the theme
journey were left: each carries a trap the code does not state, an empty `catch` and the fact that
`renderApp` mounts the router rather than the entry point.

## Next step

The lot's `lot/` tag on this pull request's merge, and the report to the operator. The P1 band still
holds six items, none of which is a simple fix: the extension's CORS origin is blocked on an
extension that does not exist, the import follow-ons and `Session.renewAfter` are features, the
grid's page cap is a limit with no cheap answer, `sourceContextUrl` needs a migration, the settled
download's refetch storm needs a cache patch that interacts with the page cap, and the two cold
Gradle builds need pipeline work.
