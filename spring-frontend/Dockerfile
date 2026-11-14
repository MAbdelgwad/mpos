FROM node:22-bookworm-slim AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps --no-audit --no-fund
COPY resources resources
COPY webpack.spring.mix.js ./
COPY spring-frontend/index.html spring-frontend/index.html
RUN npx mix --mix-config=webpack.spring.mix.js --production
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY spring-frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/spring-frontend/dist /usr/share/nginx/html
EXPOSE 8080
