ARG BASE=linagora/james-project
ARG BASE_LDAP=linagora/james-ldap-project
ARG BASE_RABBITMQ=linagora/james-rabbitmq-project
ARG BASE_RABBITMQ_LDAP=linagora/james-cassandra-rabbitmq-ldap-project
ARG TAG=latest
FROM ${BASE}:${TAG} as source
FROM ${BASE_LDAP}:${TAG} as sourceLdap
FROM ${BASE_RABBITMQ}:${TAG} as sourceRabbitMQ
FROM ${BASE_RABBITMQ_LDAP}:${TAG} as sourceRabbitMQLdap

FROM debian:8.11

RUN apt-get update
RUN apt-get install -y ruby-dev ruby build-essential
RUN gem install fpm
RUN apt-get install -y rpm

ADD scripts/ /packages

COPY scripts/james.service /packages/package/usr/share/james/
COPY --from=source /root/james-server.jar /packages/package/usr/share/james/james-withoutldap-server.jar
COPY --from=source /root/james-cli.jar /packages/package/usr/share/james/james-cli.jar
COPY --from=sourceLdap /root/james-server.jar /packages/package/usr/share/james/james-ldap-server.jar
COPY --from=sourceRabbitMQ /root/james-server.jar /packages/package/usr/share/james/james-rabbitmq-withoutldap-server.jar
COPY --from=sourceRabbitMQLdap /root/james-server.jar /packages/package/usr/share/james/james-rabbitmq-ldap-server.jar
#COPY --from=source /root/james-server-cassandra-guice.lib/ /packages/package/usr/share/james/james-server-cassandra-guice.lib/
#COPY --from=sourceLdap /root/james-server-cassandra-ldap-guice.lib/ /packages/package/usr/share/james/james-server-cassandra-ldap-guice.lib/
#COPY --from=sourceRabbitMQ /root/james-server-cassandra-rabbitmq-guice.lib/ /packages/package/usr/share/james/james-server-cassandra-rabbitmq-guice.lib/
#COPY --from=sourceRabbitMQLdap /root/james-server-cassandra-rabbitmq-ldap-guice.lib /packages/package/usr/share/james/james-server-cassandra-rabbitmq-ldap-guice.lib
#COPY --from=source /root/james-server-cli.lib/ /packages/package/usr/share/james/james-server-cli.lib/

RUN mkdir -p /packages/package/var/lib/james
RUN mkdir -p /packages/package/var/log/james

ARG RELEASE
ENV RELEASE $RELEASE
ARG ITERATION
ENV ITERATION $ITERATION

WORKDIR /packages

ENTRYPOINT [ "sh", "-c", "/packages/package.sh $RELEASE $ITERATION" ]
