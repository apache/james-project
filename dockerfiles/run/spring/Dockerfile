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
ADD destination/james-server-app-3.1.0-SNAPSHOT-app.zip /root/james-server-app-3.1.0-SNAPSHOT-app.zip
ADD destination/conf /root/conf

# Unzip build result
RUN unzip james-server-app-3.1.0-SNAPSHOT-app.zip

# Copy configuration.
# Warning : we want to use the wrapper.conf file we just compiled.
RUN cp james-server-app-3.1.0-SNAPSHOT/conf/wrapper.conf .
RUN rm -r james-server-app-3.1.0-SNAPSHOT/conf/*
RUN cp -r conf/* james-server-app-3.1.0-SNAPSHOT/conf
RUN cp wrapper.conf james-server-app-3.1.0-SNAPSHOT/conf

WORKDIR /root/james-server-app-3.1.0-SNAPSHOT/bin
ENTRYPOINT ["./wrapper-linux-x86-64","../conf/wrapper.conf", "wrapper.syslog.ident=james", "wrapper.pidfile=../var/james.pid", "wrapper.daemonize=FALSE"]
