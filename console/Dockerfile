FROM node:20-alpine AS build

WORKDIR /workspace/console

COPY console/package.json console/package-lock.json ./
RUN npm ci

COPY console/scripts ./scripts
COPY src/main/resources/static/console /workspace/src/main/resources/static/console
RUN npm run build

FROM nginx:1.27-alpine

COPY --from=build /workspace/console/dist/ /usr/share/nginx/html/
COPY console/docker-entrypoint.sh /docker-entrypoint.d/40-reycom-console-config.sh

RUN chmod +x /docker-entrypoint.d/40-reycom-console-config.sh

EXPOSE 80
