FROM openjdk:8u222-jre

# Ports that are used:
#
# 25   SMTP without authentication
# 8000 Web Admin interface
EXPOSE 25 8000

COPY target/mock-smtp-server.jar /root/mock-smtp-server.jar

ENTRYPOINT [ "sh", "-c", "java -jar /root/mock-smtp-server.jar" ]
