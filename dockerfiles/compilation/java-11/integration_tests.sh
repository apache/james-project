#!/bin/sh -e

printUsage() {
   echo "Usage : "
   echo "./integration_tests.sh URL BRANCH JAMES_IP JAMES_IMAP_PORT"
   echo "    JAMES_IP: IP of the James server to be tests"
   echo "    JAMES_IMAP_PORT: Exposed IMAP port of this James server"
   echo "    JAMES_SMTP_PORT: Exposed SMTP port of this James server"
   echo "    SHA1(optional): Branch to build or master if none"
   echo ""
   echo "Environment:"
   echo " - MVN_ADDITIONAL_ARG_LINE: Allow passing additional command arguments to the maven command"
   exit 1
}

ORIGIN=/origin

for arg in "$@"
do
   case $arg in
      -*)
         echo "Invalid option: -$OPTARG"
         printUsage
         ;;
      *)
         if ! [ -z "$1" ]; then
            JAMES_ADDRESS=$1
         fi
         if ! [ -z "$2" ]; then
            JAMES_IMAP_PORT=$2
         fi
         if ! [ -z "$3" ]; then
            JAMES_SMTP_PORT=$3
         fi
         if ! [ -z "$4" ]; then
            SHA1=$4
         fi
         ;;
   esac
done

if [ -z "$JAMES_ADDRESS" ]; then
   echo "You must provide a JAMES_ADDRESS"
   printUsage
fi

if [ -z "$JAMES_IMAP_PORT" ]; then
   echo "You must provide a JAMES_IMAP_PORT"
   printUsage
fi

if [ -z "JAMES_SMTP_PORT" ]; then
   echo "You must provide a JAMES_SMTP_PORT"
   printUsage
fi

if [ -z "$SHA1" ]; then
   SHA1=master
fi

export JAMES_ADDRESS=$JAMES_ADDRESS
export JAMES_IMAP_PORT=$JAMES_IMAP_PORT
export JAMES_SMTP_PORT=$JAMES_SMTP_PORT

git clone $ORIGIN/.
git checkout $SHA1


mvn -T 1C -DskipTests -pl org.apache.james:apache-james-mpt-external-james -am install ${MVN_ADDITIONAL_ARG_LINE}
mvn -T 1C -pl org.apache.james:apache-james-mpt-external-james test -Pintegration-tests ${MVN_ADDITIONAL_ARG_LINE}
