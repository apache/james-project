# Build James
#
# VERSION	1.0

FROM java:openjdk-6-jdk

ENV GIT_VERSION 1:1.7.10.4-1+wheezy1

# Install Maven
WORKDIR /root
RUN wget http://mirrors.ircam.fr/pub/apache/maven/maven-3/3.2.5/binaries/apache-maven-3.2.5-bin.tar.gz
RUN tar -xvf apache-maven-3.2.5-bin.tar.gz
RUN ln -s /root/apache-maven-3.2.5/bin/mvn /usr/bin/mvn

# Install git
RUN apt-get update
RUN apt-get install -y git="$GIT_VERSION"

# Copy the script
COPY compile.sh /root/compile.sh

# Define the entrypoint
WORKDIR /james-parent
ENTRYPOINT ["/root/compile.sh"]
