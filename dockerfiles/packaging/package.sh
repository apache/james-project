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

fpm -s dir -t deb \
 -n james \
 -v $RELEASE \
 -a x86_64 \
 -d openjdk-8-jre \
 -C package \
 --deb-systemd james.service \
 --after-install james.postinst \
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

#Workaround waiting for https://github.com/jordansissel/fpm/issues/1163 to be released
cp james.service.rhel package/usr/share/james/james.service

fpm -s dir -t rpm \
 -n james \
 -v $RELEASE \
 -a x86_64 \
 -d java-1.8.0-openjdk-headless \
 -C package \
 --after-install james.rpm.postinst \
 --after-upgrade james.rpm.postupgrade \
 --after-remove james.rpm.postremove \
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

cp /packages/james*.deb /result/
cp /packages/james*.rpm /result/
