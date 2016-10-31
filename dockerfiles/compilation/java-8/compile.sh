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
DESTINATION=/destination

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
   mvn package -DskipTests -Pcassandra,elasticsearch,inmemory,with-assembly,with-jetm
else
   mvn package -Pcassandra,inmemory,elasticsearch,with-assembly,with-jetm
fi

# Retrieve result

if [ $? -eq 0 ]; then
   cp server/app/target/james-server-app-*-app.zip $DESTINATION
   cp server/container/guice/cassandra-guice/target/james-server-cassandra-guice-*-SNAPSHOT.jar $DESTINATION
   cp -r server/container/guice/cassandra-guice/target/james-server-cassandra-guice-*-SNAPSHOT.lib $DESTINATION
   cp server/container/cli/target/james-server-cli-*.jar $DESTINATION
   cp -r server/container/cli/target/james-server-cli-*.lib $DESTINATION
fi
