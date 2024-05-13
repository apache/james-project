ARG ANTORA_VERSION=3.1.7

FROM antora/antora:${ANTORA_VERSION} AS antora_builder

ARG JAMES_CHECKOUT=master

RUN apk update && apk add git

RUN git clone https://github.com/apache/james-project.git /james-project \
    && cd /james-project \
    && git checkout $JAMES_CHECKOUT

WORKDIR /james-project/server/apps/distributed-app/docs

RUN antora antora-playbook-local.yml

FROM nginx:alpine

COPY --from=antora_builder /james-project/server/apps/distributed-app/docs/build/site /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
