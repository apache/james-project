# Run James
#
# VERSION	1.0

FROM openjdk:8u171-jdk

# Ports that are used
#
# 25   SMTP without authentication
# 143  IMAP with startTLS enabled
# 465  SMTP with authentication and socketTLS enabled
# 587  SMTP with authentication and startTLS enabled
# 8000 Web Admin interface (unsecured: expose at your own risks)

EXPOSE 25 143 465 587 8000

WORKDIR /root

# Get data we need to run James : build results and configuration
ADD destination/james-server-jpa-smtp-guice.jar /root/james-server-jpa-smtp-guice.jar
ADD destination/james-server-jpa-smtp-guice.lib /root/james-server-jpa-smtp-guice.lib
ADD destination/james-server-cli.jar /root/james-cli.jar
ADD destination/james-server-cli.lib /root/james-server-cli.lib
ADD destination/conf /root/conf

VOLUME /logs

ENTRYPOINT java -classpath '/root/james-server-jpa-smtp-guice.jar:/root/james-server-jpa-smtp-guice.lib/*' -javaagent:/root/james-server-jpa-smtp-guice.lib/openjpa-3.0.0.jar -Dlogback.configurationFile=/root/conf/logback.xml -Dworking.directory=/root/ org.apache.james.JPAJamesServerMain
