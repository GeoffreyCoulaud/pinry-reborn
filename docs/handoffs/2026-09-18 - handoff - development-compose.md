# Handoff: the web application ships as an image, and compose runs the three containers

Date: 2026-09-18
Spec: `docs/specs/2026-09-18-development-compose.md`
ADR: `docs/adr/0034-the-web-application-ships-as-an-nginx-image.md`
Blocks: 5 `chore/an-image-name-is-not-a-repository-name` (PR #144, merged), 10
`chore/development-compose` (PR #146, merged), 20 `chore/webapp-image-in-the-pipeline` (PR #147,
merged), 30 `chore/publish-the-webapp-image` (this pull request, the lot's last code block)
Tier: Spec. Four blocks, so the holistic review at the head of Wrap is not offered as a waiver.

## Current state

The repository is `GeoffreyCoulaud/pinry-reborn`, renamed by hand between blocks 5 and 10. The
product runs locally the way it is released: `docker compose up --build` brings up three containers
behind one nginx on <http://localhost:6258>. The web application has an image, built and started by
`dagger call ci`, and the release path publishes it to
`ghcr.io/geoffreycoulaud/pinry-reborn-webapp` beside the API's. Both deferrals
`docs/specs/2026-09-10-web-application.md` recorded, the `Caddyfile` row and the deployment
documentation row, are closed, the second by being declared out of scope rather than written.

`docs/backlog.md` holds no item adjacent to this lot: the grep for `compose`, `dockerignore`,
`dependabot` and the web application's image finds nothing there, so the lot closes none and leaves
none open.

## What was built

- **No image name is derived from a repository name** (block 5). `validate.yml` and
  `grype-scan.yml` built `ghcr.io/${GITHUB_REPOSITORY,,}` where they wanted the API's image, which
  held only while the two coincided. Both write `ghcr.io/geoffreycoulaud/pinry-reborn-api` now. The
  VEX document's `@id` points at the coming URL, with `version` 2 and the day's `timestamp`.
- **`compose.yml`, `proxy.conf`, `clients/apps/webapp/Dockerfile`, `clients/apps/webapp/nginx.conf`,
  a root `.dockerignore` and the README's third run path** (block 10), with ADR 0034 recording the
  tool: nginx `1-alpine-slim` in both roles, 21.2 MB against Caddy's 88.8 MB, measured on
  2026-09-18. The routing lives in the frontal proxy alone, so the published image stays a carrier
  of static files.
- **`webapp-image` and `webapp-smoke` in `.dagger/src/index.ts`, called by `ci`** (block 20). The
  smoke asks the started image for `/` and for a path that exists in the browser alone, and reads
  the body both times: the `index.html` fallback answers `200` to anything, so the status code
  discriminates nothing.
- **The second image in `validate.yml`'s `publish` job, and `SECURITY.md`** (block 30). Two
  platforms, the two SBOMs and a keyless cosign attestation, and three things it does not copy from
  the API's: no `type=semver` tag, no build cache, no OpenVEX predicate. `SECURITY.md` says what
  each image carries, which it had to: it claimed three attestations per image and one scan reading
  them, and a second image made that false.

## Pitfalls

- **The session cookie needs `localhost`.** `Secure` is unconditional, and browsers except
  `localhost` alone, so reaching the proxy at `http://192.168.x.x:6258` gives a session the browser
  stores and never sends.
- **`proxy_set_header Host $host` drops the port, and the API's origin check reads it.** Block 10's
  fourth observation caught `403 CORS Rejected - Invalid origin` on sign-up. `$http_host` is the
  Host header as the client sent it, and is what makes the same-origin check true;
  `X-Forwarded-Host` carries the same variable for the same reason.
- **Editing `proxy.conf` needs the container recreated, not reloaded**, the file being bind mounted:
  `docker compose up -d --force-recreate proxy`.
- **`docker compose build` on a stale fast jar builds a stale API in silence**, and fails with
  `COPY failed` and no word about Gradle when the directory is absent. The README carries
  `(cd api && ./gradlew :api-application:quarkusBuild)` in the same block for that reason.
- **The root `.dockerignore` is exercised by nothing in the gate.** Dagger filters its own contexts
  through `IGNORE` in `.dagger/src/index.ts`, which that file's first block copies by hand; a wrong
  entry shows up on a workstation's `docker compose build webapp` or on the release path.
- **The web application's build stage is 1.51 GB**, against the 21.5 MB the image ships: it is
  `node:24-slim` plus the workspace's `node_modules`, and it is what `ci` now keeps in the engine's
  cache.
- **The engine state archive is 5.1 GB, or 4.73 GiB, where
  `docs/adr/0031-the-gate-builds-once-and-keeps-its-cache.md` decision 8 measured about three
  gigabytes.** Block 20 is what took it there. Its merge run, `35381866356`, reports
  `13G /mnt/dagger/state` at its archive step against `12G` at its restore, and `ls -l` on the file
  it saves reads 5 101 289 549 bytes; the entry `prune` then leaves alone under
  `dagger-state-v0.21.9-93b849f8` is that file. `gh cache list` totals 6 364 519 004 bytes for the
  repository, the remaining 1.2 GiB being the release path's buildkit blobs, so **about 3.6 GB of
  the ten-gigabyte quota are free, not the seven the ADR's figure implied**. Both units are written
  out here because the tools disagree: `du` and `gh` count in GiB and the quota is stated in GB.
  `AGENTS.md` and `validate.yml`'s comment carry the current figure; ADR 0031 is dated and keeps its
  own. **That margin is why block 30's second image declines a cache**, and it is now smaller than
  one entry: `verify` saves the new archive before `prune` deletes the old, so two five-gigabyte
  entries and the buildkit blobs are momentarily over the quota, and GitHub evicts by least recent
  use. Nothing has gone red, and no block of this lot can fix it: it is ADR 0031's mechanism, and
  the next lot's to weigh.
- **The release path does not cache the web application's build**, which spends a `pnpm install` per
  release. The margin above is the reason, and GitHub's own eviction would take the state archive
  with it.
- **Nothing bumps the new image tags.** `nginx:1-alpine-slim` and `node:24-slim` follow a major, and
  `.github/dependabot.yml` declares its `docker` ecosystem at `/api` alone, so no automation reads a
  tag in `clients/apps/webapp/Dockerfile` or in `compose.yml`.
- **The rename left the daily scan red for an hour, and the window is closed.**
  `gh workflow run grype-scan.yml` failed at 15:47 UTC (run `35364517385`), the attestations on
  `latest` still carrying the old workflow path, and succeeded at 16:43 UTC (run `35370169707`),
  ten minutes after block 10's merge republished and re-signed `latest`. That is block 10's sixth
  observation.

## What is not validated

- **Block 30 is observed only after it merges**, the release path running on `main` alone. Its
  criterion is the run that follows: two packages under
  <https://github.com/GeoffreyCoulaud?tab=packages>, and
  `cosign verify-attestation --type cyclonedx` on `pinry-reborn-webapp:latest` with the identity
  regexp and the issuer the specification's block table writes out.
- **The holistic review has not run.** It is the head of Wrap, over
  `git diff lot/0.23.0-raw-sql-audited..origin/main`.
- **The web application's image has never been built for arm64.** A pull request builds one
  architecture, and the second is emulated on the release path alone, so a defect showing on arm64
  surfaces at the release rather than here.
- **Nothing reads the web application's SBOMs.** `grype-scan.yml` still names one image, and the
  OpenVEX document still holds `"statements": []`.
- **No deployment document, no TLS, no host name other than `localhost`, and no prefixed tag
  series.** All four are out of scope rows of the specification, with their observables.

## Next step

The closing block: the holistic review's findings with their exits, the backlog reconciled, this
handoff corrected, then the annotated tag `lot/0.24.0-development-compose` on the closing merge,
pushed.
