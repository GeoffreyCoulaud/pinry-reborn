# Pinry Reborn

The API server lives in `api/`. It is in charge of all the business logic, to be called by clients.  
The OpenAPI contract it generates lives in `contract/`, which is what a client is written against
(`docs/adr/0024-three-projects-share-one-repository.md`).  
The clients live in `clients/`, a pnpm workspace: the web application in `clients/apps/webapp`, and the
browser extension when it arrives.

> **Alpha. Do not deploy this yet.** Breaking changes and data loss are expected between versions.

## Running

The API stores its data in PostgreSQL 18.6. To start the database `compose.yml` declares, alone and on
`localhost:5432`, with a password of your choosing:

```sh
POSTGRES_PASSWORD=<password> docker compose run --rm --publish 5432:5432 postgres
```

The password is set when the database's volume is created; a later one must match it.

To start the API locally in dev mode against it, from another terminal:

```sh
cd api && DB_PASSWORD=<password> MEDIA_DATA_DIR=/tmp/pinry/media EXPORTS_DATA_DIR=/tmp/pinry/exports \
  IMPORTS_DATA_DIR=/tmp/pinry/imports ./gradlew quarkusDev
```

`DB_URL` and `DB_USERNAME` default to that database, `jdbc:postgresql://localhost:5432/pinry` and `pinry`. The
password has no default, and the API refuses to boot without it. The three directories default to directories under
`/var/lib/pinry`, which the API must be able to write to.

To start the web application against it, with Node 26 and any recent pnpm installed
(`clients/package.json` names the exact pnpm and an installed one switches itself to it):

```sh
cd clients && pnpm install
pnpm --filter @pinry-reborn/webapp run dev
```

The development server proxies `/api` to `http://localhost:8080`, so the application and the API share
one origin, which is what the session cookie needs (`docs/adr/0026-one-session-two-transports.md`).
`clients/AGENTS.md` carries the rest of the commands.

To run the whole product the way it is released, the containers `compose.yml` declares behind one proxy on
<http://localhost:6258>:

```sh
(cd api && ./gradlew :api-application:quarkusBuild) && POSTGRES_PASSWORD=<password> docker compose up --build
```

`POSTGRES_PASSWORD` is the database's password, which compose hands to the API as well, and compose refuses to start
without it.

The Gradle task is not optional: the API's image copies a fast jar Gradle produced before
`docker build` ran, so a stale one builds a stale API in silence and a missing one fails the build
with `COPY failed`. Docker Engine 25 or later is required, here and wherever the API's image is
deployed: its `HEALTHCHECK` sets `--start-interval`, which `compose.yml`'s `service_healthy` waits on.
Reach the proxy at `localhost` and not at an address on the network: the session
cookie is `Secure` unconditionally, and browsers except `localhost` alone.

## Git hooks

This repo ships its git hooks in `.githooks/`. Enable them once per clone:

```sh
git config core.hooksPath .githooks
```

- `pre-commit` refuses an em dash or an en dash in what you staged.
- `pre-push` runs `dagger call gate`, the same command CI runs: the API's build, the clients' build and
  tests, the prose rules, the contract's synchronisation and the breaking-change guard. It needs Docker
  and the Dagger CLI.

## Architecture

The API follows the clean architecture principle, with each part in its own submodule.

