#!/bin/sh -e

printUsage() {
   echo "Usage : "
   echo "./package.sh RELEASE ITERATION"
   echo "    RELEASE  : The release to be generated."
   echo "    ITERATION: The iteration to give to the package."
   exit 1
}

if [ "$#" -ne 2 ]; then
    printUsage
fi

RELEASE=$1
ITERATION=$2
cp /jars/james-server-cassandra-guice.jar /debian/package/usr/share/james/james-server.jar
cp -r /jars/james-server-cassandra-guice.lib /debian/package/usr/share/james/
cp /jars/james-server-cli.jar /debian/package/usr/share/james/james-cli.jar
cp -r /jars/james-server-cli.lib /debian/package/usr/share/james/

fpm -s dir -t deb \
 -n james \
 -v $RELEASE \
 -a x86_64 \
 -d openjdk-8-jre \
 -C package \
 --deb-systemd james.service \
 --after-install james.postinst \
 --before-remove james.prerm \
 --provides mail-transport-agent \
 --provides default-mta \
 --iteration $ITERATION \
 --license http://www.apache.org/licenses/LICENSE-2.0 \
 --description "$(printf "James stands for Java Apache Mail Enterprise Server!\nIt has a modular architecture based on a rich set of modern and efficient components which provides at the end complete, stable, secure and extendable Mail Servers running on the JVM.")" \
 --vendor "Apache" \
 --maintainer "Apache" \
 --url http://james.apache.org/ \
 --category web \
 .

cp /debian/james*.deb /result/
