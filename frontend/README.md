# Frontend

Standalone React 17 application. From this directory run `npm ci --legacy-peer-deps --no-audit --no-fund`, then `npm run build`. Output is `dist/`. The root Docker Compose file builds this app and serves it through Nginx with same-origin `/api` forwarding.

Migrated workflow pages live under `src/features`; application shell components live under `src/app`. Existing catalog screens and Redux remain under their compatible source paths. Unsupported legacy modules remain disabled in Spring mode. Laravel Mix is only a webpack build tool here; PHP is not required.

Optional browser smoke: install dependencies in `tests/`, install Playwright Chromium, build this app, then run `mvn -Dmpos.browser=true verify` from `../backend`. Browser smoke has not passed in the authoring environment because Chromium cannot launch there.
