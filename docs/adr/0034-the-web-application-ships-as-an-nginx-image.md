# 0034. The web application ships as an nginx image

Status: Accepted
Date: 2026-09-18
Specification: `docs/specs/2026-09-18-development-compose.md`, section 3.1. Written in block 10.
Amends: nothing. It records the tool and not the topology.
Related: `docs/specs/2026-09-10-web-application.md` decision B, which settled "two artefacts, one
reverse proxy, one public origin", and `docs/adr/0026-one-session-two-transports.md` decision 5,
which records why one origin is a condition and not a convenience.

## Context

Two roles need a server and neither had one: the frontal proxy that puts the bundle and the API on
one origin, and the static server inside the web application's image. The topology was decided a lot
ago; which server fills it was never weighed. The only trace of one is the word `Caddyfile` in an
out-of-scope row of the web application's specification, which records no choice.

Measured on 2026-09-18, `docker pull` then `docker images`, amd64, on this workstation:

| Candidate | Serves static files | Proxies | Image |
|-----------|---------------------|---------|-------|
| nginx `1-alpine-slim` (1.31.6) | `try_files`, in the always compiled core module | `proxy_pass` | **21.2 MB** |
| Caddy `2-alpine` (v2.11.4) | `try_files` and `file_server` | `reverse_proxy` | 88.8 MB |
| nginx `alpine` | as above | as above | 94.4 MB |
| Traefik | No file server; it would still need a static server behind it | yes | not measured |

The tag follows the major as every other tag here does, and `1-alpine-slim` and `alpine-slim` resolve
to the same digest, `sha256:578082d6635f...` under `docker manifest inspect -v`, which is the name
the measurement was taken under. Versions read from inside the images: `nginx -v` and
`caddy version`.

Sources for the two directives the configurations rest on, read through Context7 on 2026-09-18:
nginx.org/en/docs/http/ngx_http_proxy_module.html for `proxy_pass` ("If a URI is specified in the
directive, the matching part of the request URI is replaced; otherwise, the original request URI is
passed to the server") and nginx.org/en/docs/http/ngx_http_core_module.html for `try_files`.

The first adversarial review of the specification landed on Caddy. The second overturned it: the
comparison had measured `nginx:alpine`, where `alpine-slim` is the variant a size comparison keeps.

## Decision

1. **nginx `1-alpine-slim` in both roles.** The web application's image is published and pulled by
   everyone who installs Pinry, and the slim variant carries a quarter of Caddy's bytes to serve a
   bundle of about two megabytes. Both roles then take the same syntax, which is the other thing
   worth having and which no longer argues for Caddy.

   **Fails if** a role the frontal proxy grows needs a module the slim variant drops, the slim image
   being the standard one with the dynamic modules and the extra tooling removed.

2. **Traefik is out on the first column.** It routes and does not serve, so choosing it means
   choosing a second server anyway.

3. **Automatic HTTPS buys nothing here.** The site listens on `:6258` with no host name, and no
   document describes running this anywhere but a workstation. The frontal proxy is never published,
   so revisiting it the day such a document exists costs nothing.

## Consequences

- **The static block is seven lines against five, one level of nesting more.** nginx wraps the
  `location` inside a `server` where Caddy's site block holds `root`, `try_files` and `file_server`
  flat.
- **`Host` and the `X-Forwarded-*` headers are written by hand**, four `proxy_set_header` lines that
  Caddy's `reverse_proxy` sets itself. They sit at the `server` level in `proxy.conf`: nginx inherits
  them into a `location` that declares none of its own, so the two routes name them once.
- **The absent URI on `proxy_pass` is the whole of the API route.** `proxy_pass http://api:8080;`
  passes the request URI unchanged; `proxy_pass http://api:8080/;` would replace `/api/` and turn
  `/api/v1/pins` into `/v1/pins`.
- **Nothing bumps these tags.** `.github/dependabot.yml` declares its `docker` ecosystem at `/api`
  alone, so no automation looks at `nginx:1-alpine-slim` in `compose.yml` or in the web
  application's `Dockerfile`. Widening it is out of scope of the lot that writes them.
