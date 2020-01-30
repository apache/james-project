# Run James
#
# VERSION	1.0

FROM openjdk:8u222-jre

# Ports that are used
#
# 25   SMTP without authentication
# 143  IMAP with startTLS enabled
# 465  SMTP with authentication and socketTLS enabled
# 587  SMTP with authentication and startTLS enabled
# 8000 Web Admin interface (unsecured: expose at your own risks)

EXPOSE 25 143 465 587 4000 8000

WORKDIR /root

# Get data we need to run James : build results and configuration
ADD destination/james-server-jpa-smtp-guice.jar /root/james-server-jpa-smtp-guice.jar
ADD destination/james-server-jpa-smtp-guice.lib /root/james-server-jpa-smtp-guice.lib
ADD destination/james-server-cli.jar /root/james-cli.jar
ADD destination/james-server-cli.lib /root/james-server-cli.lib
ADD destination/conf /root/conf
ADD destination/glowroot/plugins /root/glowroot/plugins
ADD destination/glowroot/glowroot.jar /root/glowroot/glowroot.jar
ADD destination/glowroot/lib/glowroot-embedded-collector.jar /root/glowroot/lib/glowroot-embedded-collector.jar
ADD destination/glowroot/lib/glowroot-logging-logstash.jar /root/glowroot/lib/glowroot-logging-logstash.jar
ADD destination/glowroot/admin.json /root/glowroot/admin.json
ADD destination/run_james.sh /root/run_james.sh

VOLUME /logs
VOLUME /root/conf
VOLUME /root/glowroot/plugins
VOLUME /root/glowroot/data

ENV PATH="$PATH:/root/glowroot/lib" \
    JVM_OPTIONS="" \
    GLOWROOT_ACTIVATED="false"

ENTRYPOINT ./run_james.sh
