# The web application's image: the bundle built by pnpm, served by nginx.
#
# The build context is the repository root, not this directory: @pinry-reborn/api-client
# generates its types from ../../../contract/openapi.json in its `prepare` script, so the
# install fails on a context that carries clients/ without contract/.
#
# $BUILDPLATFORM keeps the compile on the runner's own architecture in a two architecture
# build; what it emits is architecture independent, exactly as the API's fast jar is.
FROM --platform=$BUILDPLATFORM node:24-slim AS build

RUN npm install --global pnpm@12.3.4

WORKDIR /src
COPY contract/ contract/
COPY clients/ clients/

WORKDIR /src/clients
RUN pnpm install --frozen-lockfile
RUN pnpm --filter @pinry-reborn/webapp run build

FROM nginx:1-alpine-slim
COPY clients/apps/webapp/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/clients/apps/webapp/dist/ /srv/
