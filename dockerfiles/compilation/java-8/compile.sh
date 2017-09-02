#!/bin/sh -e
#

printUsage() {
   echo "Usage : "
   echo "./compile.sh [-s | --skipTests] SHA1"
   echo "    -s: Skip test"
   echo "    SHA1: SHA1 to build (optional)"
   exit 1
}

ORIGIN=/origin
CASSANDRA_DESTINATION=/cassandra/destination
JPA_DESTINATION=/jpa/destination
SPRING_DESTINATION=/spring/destination

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
   mvn package -DskipTests -Pcassandra,inmemory,jpa,elasticsearch,lucene,with-assembly,with-jetm
else
   mvn package -Pcassandra,inmemory,jpa,elasticsearch,lucene,with-assembly,with-jetm
fi

# Retrieve result

if [ $? -eq 0 ]; then
   if [ -d "$CASSANDRA_DESTINATION" ]; then
      echo "Copying cassandra JARs"
      cp server/container/guice/cassandra-guice/target/james-server-cassandra-guice.jar $CASSANDRA_DESTINATION || true
      cp -r server/container/guice/cassandra-guice/target/james-server-cassandra-guice.lib $CASSANDRA_DESTINATION || true
      cp server/container/cli/target/james-server-cli.jar $CASSANDRA_DESTINATION || true
      cp -r server/container/cli/target/james-server-cli.lib $CASSANDRA_DESTINATION || true

      cp server/container/guice/cassandra-ldap-guice/target/james-server-cassandra-ldap-guice.jar $CASSANDRA_DESTINATION || true
      cp -r server/container/guice/cassandra-ldap-guice/target/james-server-cassandra-ldap-guice.lib $CASSANDRA_DESTINATION || true
   fi

   if [ -d "$JPA_DESTINATION" ]; then
      echo "Copying JPA jars"
      cp server/container/guice/jpa-guice/target/james-server-jpa-guice.jar $JPA_DESTINATION || true
      cp -r server/container/guice/jpa-guice/target/james-server-jpa-guice.lib $JPA_DESTINATION || true
      cp server/container/cli/target/james-server-cli.jar $JPA_DESTINATION || true
      cp -r server/container/cli/target/james-server-cli.lib $JPA_DESTINATION || true
   fi

   if [ -d "$SPRING_DESTINATION" ]; then
      echo "Copying SPRING jars"
      cp server/app/target/james-server-app-*-app.zip $SPRING_DESTINATION
   fi
fi
