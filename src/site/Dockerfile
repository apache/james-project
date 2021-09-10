# Build James
#
# VERSION	1.0

FROM eclipse-temurin:11-jdk-focal

# Install git
RUN apt-get update
RUN apt-get install -y git wget unzip

# Install Maven
WORKDIR /root
RUN wget https://downloads.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz
RUN tar -xvf apache-maven-3.6.3-bin.tar.gz
RUN ln -s /root/apache-maven-3.6.3/bin/mvn /usr/bin/mvn

# Copy the script
COPY compile.sh /root/compile.sh

# Define the entrypoint
WORKDIR /james-project
ENTRYPOINT ["/root/compile.sh"]
