FROM nginx:1.27-alpine

COPY src/main/resources/static/console/ /usr/share/nginx/html/
COPY console/docker-entrypoint.sh /docker-entrypoint.d/40-reycom-console-config.sh

RUN chmod +x /docker-entrypoint.d/40-reycom-console-config.sh

EXPOSE 80
