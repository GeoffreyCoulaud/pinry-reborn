# The web application's build stage: its image, and pnpm at the version `clients/package.json` pins.
# pnpm comes from npm and not corepack, which Node ships deprecated.
FROM node:26-slim@sha256:193fe51b64e77981119c98c2002c9e32a70e2f006fb4d25068ce0558998917f0

RUN npm install --global pnpm@12.3.4
