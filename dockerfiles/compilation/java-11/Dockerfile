# Build James
#
# VERSION	1.0

FROM adoptopenjdk:11-jdk-hotspot

ENV GIT_VERSION 1:2.11.0-3

# Install git
RUN apt-get update
RUN apt-get install -y git wget unzip

# Install Maven
WORKDIR /root
RUN wget https://downloads.gradle-dn.com/distributions/gradle-6.5.1-bin.zip
RUN unzip gradle-6.5.1-bin.zip
RUN ln -s /root/gradle-6.5.1/bin/gradle /usr/bin/gradle

# Copy the script
COPY compile.sh /root/compile.sh
COPY integration_tests.sh /root/integration_tests.sh
COPY .testcontainers.properties /root/.testcontainers.properties

# Define the entrypoint
WORKDIR /james-project
ENTRYPOINT ["/root/compile.sh"]
