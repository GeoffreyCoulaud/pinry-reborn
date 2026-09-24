# The web application's build stage: its image, and pnpm at the version `clients/package.json` pins.
# pnpm comes from npm and not corepack, which Node ships deprecated.
FROM node:25-slim@sha256:81db02c4b671288a03915da9534dbd54f96d0e7c24d80ccc54f5b36b2e684370

RUN npm install --global pnpm@12.3.4
