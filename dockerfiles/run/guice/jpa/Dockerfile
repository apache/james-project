# Run James
#
# VERSION	1.0

FROM java:openjdk-8-jdk

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
ADD destination/james-server-jpa-guice.jar /root/james-server.jar
ADD destination/james-server-jpa-guice.lib /root/james-server-jpa-guice.lib
ADD destination/james-server-cli.jar /root/james-cli.jar
ADD destination/james-server-cli.lib /root/james-server-cli.lib
ADD destination/conf /root/conf

VOLUME /logs

ENTRYPOINT java -classpath '/root/james-server.jar:/root/james-server-jpa-guice.lib/*' -javaagent:/root/james-server-cli.lib/openjpa-2.4.2.jar -Dlogback.configurationFile=/root/conf/logback.xml -Dworking.directory=/root/ org.apache.james.JPAJamesServerMain
