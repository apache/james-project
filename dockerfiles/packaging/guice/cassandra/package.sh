#!/bin/sh -e

printUsage() {
   echo "Usage : "
   echo "./package.sh RELEASE ITERATION SHA1 DIRECTORY"
   echo "    RELEASE  : The release to be generated."
   echo "    ITERATION: The iteration to give to the package."
   echo "    SHA1: The SHA-1 to build packages against"
   echo "    DIRECTORY: The directory where to put build results"
   exit 1
}


if [ "$#" -ne 4 ]; then
    printUsage
fi

RELEASE=$1
ITERATION=$2
SHA1=$3
DIRECTORY=$4

# Build Compiler environment
docker build -t james/project dockerfiles/compilation/java-8

# Compile James with Cassandra
docker run \
   --rm \
   --volume $PWD/.m2:/root/.m2 \
   --volume $PWD:/origin \
   --volume $PWD/dockerfiles/run/guice/cassandra/destination:/cassandra/destination \
   -t james/project -s $SHA1
docker run \
   --rm \
   --volume $PWD/.m2:/root/.m2 \
   --volume $PWD:/origin \
   --volume $PWD/dockerfiles/run/guice/cassandra-ldap/destination:/cassandra/destination \
   -t james/project -s $SHA1

# Compile James with Cassandra + RabbitMQ + Swift BlobStore
docker run \
   --rm \
   --volume $PWD/.m2:/root/.m2 \
   --volume $PWD:/origin \
   --volume $PWD/dockerfiles/run/guice/cassandra-rabbitmq/destination:/cassandra-rabbitmq/destination \
   -t james/project -s $SHA1

# Build image James with Cassandra
docker build -t james_run dockerfiles/run/guice/cassandra
docker build -t james_run_ldap dockerfiles/run/guice/cassandra-ldap

# Build image James with Cassandra + RabbitMQ
docker build -t james_rabbitmq_run dockerfiles/run/guice/cassandra-rabbitmq

# Build packages
docker build -t build-james-packages \
  --build-arg RELEASE=$RELEASE-$SHA1 \
  --build-arg ITERATION=$ITERATION \
  --build-arg BASE=james_run \
  --build-arg BASE_LDAP=james_run_ldap \
  --build-arg BASE_RABBITMQ=james_rabbitmq_run \
  dockerfiles/packaging/guice/cassandra
docker run --rm --name james-packages -v $DIRECTORY:/result build-james-packages
