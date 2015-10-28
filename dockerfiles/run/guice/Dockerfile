# Run James
#
# VERSION	1.0

FROM java:openjdk-8-jdk

# Ports that are used
#
# 25  SMTP without authentication
# 110 POP3
# 143 IMAP with startTLS enabled
# 465 SMTP with authentication and socketTLS enabled
# 587 SMTP with authentication and startTLS enabled
# 993 IMAP with socketTLS enabled

EXPOSE 25 110 143 465 587 993

WORKDIR /root

# Get data we need to run James : build results and configuration
ADD destination/james-server-cassandra-guice-*.jar /root/james-server.jar
ADD destination/james-server-cassandra-guice-3.0.0-beta5-SNAPSHOT.lib /root/james-server-cassandra-guice-3.0.0-beta5-SNAPSHOT.lib
ADD destination/james-server-cli-3.0.0-beta5-SNAPSHOT.jar /root/james-cli.jar
ADD destination/james-server-cli-3.0.0-beta5-SNAPSHOT.lib /root/james-server-cli-3.0.0-beta5-SNAPSHOT.lib
ADD destination/conf /root/conf

ENTRYPOINT java -Dworking.directory=/root/ -jar james-server.jar 
