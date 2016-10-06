#!/bin/sh -e
#

printUsage() {
   echo "Usage : "
   echo "./compile.sh SHA1"
   echo "    SHA1: SHA1 to build (optional)"
   exit 1
}

ORIGIN=/origin
DESTINATION=/destination

for arg in "$@"
do
   case $arg in
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

mvn clean install -DskipTests
mvn clean site:site -Dmaven.javadoc.skip=true

# Retrieve result

if [ $? -eq 0 ]; then
   cp -r target/site/* $DESTINATION/
   cp -r server/target/site $DESTINATION/server
   cp -r mailbox/target/site $DESTINATION/mailbox
   cp -r protocols/target/site $DESTINATION/protocols
   cp -r mailet/target/site $DESTINATION/mailet
   cp -r mpt/target/site $DESTINATION/mpt
 
fi
