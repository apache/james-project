#!/bin/sh -e
#

printUsage() {
   echo "Usage : "
   echo "./compile.sh [-s | --skipTests] SHA1"
   echo "    -s: Skip test"
   echo "    SHA1: SHA1 to build (optional)"
   echo ""
   echo "Environment:"
   echo " - MVN_ADDITIONAL_ARG_LINE: Allow passing additional command arguments to the maven command"
   exit 1
}

GLOW_ROOT_URL=https://github.com/glowroot/glowroot/releases/download/v0.13.4/glowroot-0.13.4-dist.zip
GLOW_ROOT_DIR=glowroot
GLOW_ROOT_ZIP=glowroot.zip
GLOW_ROOT_JAR=${GLOW_ROOT_DIR}/glowroot.jar
GLOW_ROOT_LIB=${GLOW_ROOT_DIR}/lib

ORIGIN=/origin
CASSANDRA_DESTINATION=/cassandra/destination
CASSANDRA_RABBITMQ_DESTINATION=/cassandra-rabbitmq/destination
CASSANDRA_RABBITMQ_LDAP_DESTINATION=/cassandra-rabbitmq-ldap/destination
JPA_DESTINATION=/jpa/destination
JPA_SMTP_DESTINATION=/jpa-smpt/destination
MEMORY_DESTINATION=/memory/destination
SPRING_DESTINATION=/spring/destination
SWAGGER_DESTINATION=/swagger

for arg in "$@"
do
   case $arg in
      -s|--skipTests)
         SKIPTESTS="skipTests"
         ;;
      -*)
         echo "Invalid option: -$OPTARG"
         printUsage
         ;;
      *)
         if ! [ -z "$1" ]; then
            SHA1=$1
         fi
         ;;
   esac
   if [ "0" -lt "$#" ]; then
      shift
   fi
done

if [ -z "$SHA1" ]; then
   SHA1=master
fi

# Sources retrieval
git clone $ORIGIN/.
git checkout $SHA1

# Compilation

if [ "$SKIPTESTS" = "skipTests" ]; then
   mvn package -DskipTests ${MVN_ADDITIONAL_ARG_LINE}
else
   mvn package ${MVN_ADDITIONAL_ARG_LINE}
fi

# download glowroot jar
wget  -O $GLOW_ROOT_ZIP $GLOW_ROOT_URL
unzip $GLOW_ROOT_ZIP
# Retrieve result

if [ $? -eq 0 ]; then
   if [ -d "$CASSANDRA_RABBITMQ_LDAP_DESTINATION" ]; then
      echo "Copying cassandra - rabbitMQ - Ldap JARs"
      cp server/container/guice/cassandra-rabbitmq-guice/target/james-server-cassandra-rabbitmq-guice.jar $CASSANDRA_RABBITMQ_LDAP_DESTINATION || true
      cp -r server/container/guice/cassandra-rabbitmq-guice/target/james-server-cassandra-rabbitmq-guice.lib $CASSANDRA_RABBITMQ_LDAP_DESTINATION || true
      cp server/container/cli/target/james-server-cli.jar $CASSANDRA_RABBITMQ_LDAP_DESTINATION || true
      cp -r server/container/cli/target/james-server-cli.lib $CASSANDRA_RABBITMQ_LDAP_DESTINATION || true
      mkdir -p ${CASSANDRA_RABBITMQ_LDAP_DESTINATION}/glowroot
      cp -r ${GLOW_ROOT_DIR}/* ${CASSANDRA_RABBITMQ_LDAP_DESTINATION}/glowroot || true
   fi

   if [ -d "$CASSANDRA_RABBITMQ_DESTINATION" ]; then
      echo "Copying cassandra RabbitMQ JARs"
      cp server/container/guice/cassandra-rabbitmq-guice/target/james-server-cassandra-rabbitmq-guice.jar $CASSANDRA_RABBITMQ_DESTINATION || true
      cp -r server/container/guice/cassandra-rabbitmq-guice/target/james-server-cassandra-rabbitmq-guice.lib $CASSANDRA_RABBITMQ_DESTINATION || true
      cp server/container/cli/target/james-server-cli.jar $CASSANDRA_RABBITMQ_DESTINATION || true
      cp -r server/container/cli/target/james-server-cli.lib $CASSANDRA_RABBITMQ_DESTINATION || true
      mkdir -p ${CASSANDRA_RABBITMQ_DESTINATION}/glowroot
      cp -r ${GLOW_ROOT_DIR}/* ${CASSANDRA_RABBITMQ_DESTINATION}/glowroot || true
   fi

   if [ -d "$CASSANDRA_DESTINATION" ]; then
      echo "Copying cassandra JARs"
      cp server/container/guice/cassandra-guice/target/james-server-cassandra-guice.jar $CASSANDRA_DESTINATION || true
      cp -r server/container/guice/cassandra-guice/target/james-server-cassandra-guice.lib $CASSANDRA_DESTINATION || true
      cp server/container/cli/target/james-server-cli.jar $CASSANDRA_DESTINATION || true
      cp -r server/container/cli/target/james-server-cli.lib $CASSANDRA_DESTINATION || true

      cp server/container/guice/cassandra-guice/target/james-server-cassandra-guice.jar $CASSANDRA_DESTINATION || true
      cp -r server/container/guice/cassandra-guice/target/james-server-cassandra-guice.lib $CASSANDRA_DESTINATION || true
      mkdir -p ${CASSANDRA_DESTINATION}/glowroot
      cp -r ${GLOW_ROOT_DIR}/* ${CASSANDRA_DESTINATION}/glowroot || true
   fi

   if [ -d "$JPA_DESTINATION" ]; then
      echo "Copying JPA jars"
      cp server/container/guice/jpa-guice/target/james-server-jpa-guice.jar $JPA_DESTINATION || true
      cp -r server/container/guice/jpa-guice/target/james-server-jpa-guice.lib $JPA_DESTINATION || true
      cp server/container/cli/target/james-server-cli.jar $JPA_DESTINATION || true
      cp -r server/container/cli/target/james-server-cli.lib $JPA_DESTINATION || true
      mkdir -p ${JPA_DESTINATION}/glowroot
      cp -r ${GLOW_ROOT_DIR}/* ${JPA_DESTINATION}/glowroot || true
   fi

   if [ -d "$JPA_SMTP_DESTINATION" ]; then
      echo "Copying JPA-SMTP jars"
      cp server/container/guice/jpa-smpt/target/james-server-jpa-smtp-guice.jar $JPA_SMTP_DESTINATION || true
      cp -r server/container/guice/jpa-smpt/target/james-server-jpa-smtp-guice.lib $JPA_SMTP_DESTINATION || true
      cp server/container/cli/target/james-server-cli.jar $JPA_SMTP_DESTINATION || true
      cp -r server/container/cli/target/james-server-cli.lib $JPA_SMTP_DESTINATION || true
      mkdir -p ${JPA_SMTP_DESTINATION}/glowroot
      cp -r ${GLOW_ROOT_DIR}/* ${JPA_SMTP_DESTINATION}/glowroot || true
   fi

   if [ -d "$MEMORY_DESTINATION" ]; then
      echo "Copying memory JARs"
      cp server/container/guice/memory-guice/target/james-server-memory-guice.zip $MEMORY_DESTINATION || true
      cp server/container/cli/target/james-server-cli.jar $MEMORY_DESTINATION || true
      cp -r server/container/cli/target/james-server-cli.lib $MEMORY_DESTINATION || true
      mkdir -p ${MEMORY_DESTINATION}/glowroot
      cp -r ${GLOW_ROOT_DIR}/* ${MEMORY_DESTINATION}/glowroot || true
      unzip $MEMORY_DESTINATION/james-server-memory-guice.zip
   fi

   if [ -d "$SPRING_DESTINATION" ]; then
      echo "Copying SPRING jars"
      cp server/app/target/james-server-app-*-app.zip $SPRING_DESTINATION
      mkdir -p ${SPRING_DESTINATION}/glowroot
      cp -r ${GLOW_ROOT_DIR}/* ${SPRING_DESTINATION}/glowroot || true
   fi

   if [ -d "$SWAGGER_DESTINATION" ]; then
      cp server/protocols/webadmin/webadmin-data/target/webadmin-data.json $SWAGGER_DESTINATION || true
      cp server/protocols/webadmin/webadmin-mailbox/target/webadmin-mailbox.json $SWAGGER_DESTINATION || true
      cp server/protocols/webadmin/webadmin-swagger/target/webadmin-swagger.json $SWAGGER_DESTINATION || true
   fi
fi

# delete temporary glowroot files
rm $GLOW_ROOT_ZIP
rm -rf $GLOW_ROOT_DIR
