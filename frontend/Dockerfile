FROM node:22-bookworm-slim AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --legacy-peer-deps --no-audit --no-fund
COPY frontend/src src
COPY frontend/images images
COPY frontend/webpack.mix.js ./
COPY frontend/index.html index.html
RUN npm run build && chmod -R a+rX dist
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY --chmod=0644 infra/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
