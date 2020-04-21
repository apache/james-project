# Run James
#
# VERSION	1.0

FROM adoptopenjdk:11-jre-hotspot

# Install unzip
RUN apt-get update
RUN apt-get install -y unzip

# Ports that are used
#
# 25  SMTP without authentication
# 110 POP3
# 143 IMAP with startTLS enabled
# 465 SMTP with authentication and socketTLS enabled
# 587 SMTP with authentication and startTLS enabled
# 993 IMAP with socketTLS enabled

EXPOSE 25 110 143 465 587 993 4000

WORKDIR /root

ARG VERSION=3.6.0-SNAPSHOT

# Get data we need to run James : build results and configuration
ADD "destination/james-server-app-$VERSION-app.zip" "/root/james-server-app-$VERSION-app.zip"
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

# Unzip build result
RUN unzip "james-server-app-$VERSION-app.zip"

# Copy configuration.
# Warning : we want to use the wrapper.conf file we just compiled.
RUN cp "james-server-app-$VERSION/conf/wrapper.conf" .
RUN rm -r "james-server-app-$VERSION/conf" && mkdir "james-server-app-$VERSION/conf"
RUN cp -r conf/* "james-server-app-$VERSION/conf"
RUN cp wrapper.conf "james-server-app-$VERSION/conf"

ENV PATH "$PATH:/root/glowroot/lib"

WORKDIR "/root/james-server-app-$VERSION/bin"
ENTRYPOINT /root/run_james.sh