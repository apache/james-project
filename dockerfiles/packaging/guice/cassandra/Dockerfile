ARG BASE=linagora/james-project:latest
FROM ${BASE} as source

FROM debian:8.1

RUN apt-get update
RUN apt-get install -y ruby-dev ruby build-essential
RUN gem install fpm
RUN apt-get install -y rpm

ADD scripts/ /packages

COPY scripts/james.service /packages/package/usr/share/james/
COPY --from=source /root/*.jar /packages/package/usr/share/james/
COPY --from=source /root/james-server-cassandra-guice.lib/ /packages/package/usr/share/james/james-server-cassandra-guice.lib/
COPY --from=source /root/james-server-cli.lib/ /packages/package/usr/share/james/james-server-cli.lib/

ARG RELEASE
ENV RELEASE $RELEASE
ARG ITERATION
ENV ITERATION $ITERATION

WORKDIR /packages

ENTRYPOINT [ "sh", "-c", "/packages/package.sh $RELEASE $ITERATION" ]
