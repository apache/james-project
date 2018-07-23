# Run James
#
# VERSION	1.0

FROM openjdk:8u171-jdk

# Ports that are used
#
# 25   SMTP without authentication
# 110  POP3
# 143  IMAP with startTLS enabled
# 465  SMTP with authentication and socketTLS enabled
# 587  SMTP with authentication and startTLS enabled
# 993  IMAP with socketTLS enabled
# 8000 Web Admin interface (unsecured: expose at your own risks)

EXPOSE 25 110 143 465 587 993 8000

WORKDIR /root

# Get data we need to run James : build results and configuration
ADD destination/james-server-cassandra-guice.jar /root/james-server.jar
ADD destination/james-server-cassandra-guice.lib /root/james-server-cassandra-guice.lib
ADD destination/james-server-cli.jar /root/james-cli.jar
ADD destination/james-server-cli.lib /root/james-server-cli.lib
ADD destination/conf /root/conf

VOLUME /logs
VOLUME /root/conf

ENTRYPOINT java -Dlogback.configurationFile=/root/conf/logback.xml -Dworking.directory=/root/ -jar james-server.jar 
