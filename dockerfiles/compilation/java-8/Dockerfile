# Build James
#
# VERSION	1.0

FROM openjdk:8u171-jdk

ENV GIT_VERSION 1:2.11.0-3

# Install Maven
WORKDIR /root
RUN wget https://archive.apache.org/dist/maven/maven-3/3.5.4/binaries/apache-maven-3.5.4-bin.tar.gz
RUN tar -xvf apache-maven-3.5.4-bin.tar.gz
RUN ln -s /root/apache-maven-3.5.4/bin/mvn /usr/bin/mvn

# Install git
RUN apt-get update
RUN apt-get install -y git

# Copy the script
COPY compile.sh /root/compile.sh
COPY integration_tests.sh /root/integration_tests.sh
COPY .testcontainers.properties /root/.testcontainers.properties

# Define the entrypoint
WORKDIR /james-project
ENTRYPOINT ["/root/compile.sh"]
