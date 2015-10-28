Apache James Project
====================

This is the parent module for all Apache James artifacts. It contains useful values to be inherited by other Maven projects. 

* the list of Apache James contributors, committers and PMC Members
* Maven plugins managemnt section with common plugins used in the project
* URL's and mailing-lists definitions for the project

How to build and publish the website
====================================

 1. Install Apache Maven 3.0.2+ and make its binary 'mvn' available on your PATH.
    See http://maven.apache.org/download.html#Installation.
 2. run "mvn clean site"
 3. Test the built site in your browser from the {path}/target/site folder
 4. If everything looks OK, deploy the site using "mvn clean site-deploy".
 5. Wait for the changes to replicate to the Apache web server or setup 140.211.11.10:80 as
    a proxy to review the changes (described here: http://www.apache.org/dev/project-site.html)

To deploy the technical reports use the "-Psite-reports" profile.

For wagon-ssh-external configuration see
http://maven.apache.org/plugins/maven-deploy-plugin/examples/deploy-ssh-external.html


Howto release via maven release plugin
======================================

See details on http://www.apache.org/dev/publishing-maven-artifacts.html

In short, just follow the 'standard' process:

* Prepare pom for release
* publish snapshot
* prepare release
* stage the release for a vote (don't forget to close the staging repository)
* vote
* release

Don't forget to add your key to http://www.apache.org/dist/james/KEYS

    $ ssh people.apache.org
    $ cd /www/www.apache.org/dist/james


Howto check the compilation
===========================

In order to have a standard compilation environment, we introduce Dockerfiles.
We need to check the compilation in both Java 6 & Java 8:
- Java 6 is the historical Java release used in James.
- Java 8 is used to compile the Cassandra backend.

* Java 6
First step, you have to build the Docker image
$ docker build -t james/project dockerfiles/compilation/java-6

In order to run the build, you have to launch the following command:
$ docker run -v $PWD/.m2:/root/.m2 -v $PWD:/origin -v $PWD/dockerfiles/run/spring/destination:/destination -t james/project -s SHA1

Where:

- $PWD/.m2:/root/.m2: is the first volume used to share the maven repository, 
as we don't want to download all dependencies on each build
- $PWD/dockerfiles/run/spring/destination:/destination: is the third volume used to get the compiled elements, 
as it is needed by the container that will run James.
- SHA1 (optional): is the given git SHA1 of the james-project repository to build or trunk if none.
- -s option: given tests will not be played while building. Not specifying means play tests.


* Java 8
First step, you have to build the Docker image
$ docker build -t james/project dockerfiles/compilation/java-8

In order to run the build, you have to launch the following command:
$ docker run -v $PWD/.m2:/root/.m2 -v $PWD:/origin -v $PWD/dockerfiles/run/spring/destination:/destination -t james/project -s SHA1

Where:

- $PWD/.m2:/root/.m2: is the first volume used to share the maven repository, 
as we don't want to download all dependencies on each build
- $PWD/dockerfiles/run/spring/destination:/destination: is the third volume used to get the compiled elements, 
as it is needed by the container that will run James.
- SHA1 (optional): is the given git SHA1 of the james-project repository to build or trunk if none.
- -s option: given tests will not be played while building. Not specifying means play tests.

Howto run James in Docker
=========================

This feature is only available for Java 8 / Cassandra mailbox backend yet.


* Requirements
You should have the zip resulting of the build in the ./dockerfiles/run/spring/destination folder.


* Howto ?
You need a running cassandra in docker. To achieve this run :
$ docker run -d --name=cassandra cassandra

You need a running ElasticSearch in docker. To achieve this run :
$ docker run -d --name=elasticsearch elasticsearch:1.5.2

We need to provide the key we will use for TLS. For obvious reasons, this is not provided in this git.

Copy your TSL keys to destination/run/spring/conf/keystore or generate it using the following command. The password must be james72laBalle to match default configuration.
$ keytool -genkey -alias james -keyalg RSA -keystore dockerfiles/run/spring/destination/conf/keystore

Then we need to build james container :
$ docker build -t james_run dockerfiles

To run this container :
$ docker run --hostname HOSTNAME -p "25:25" -p "110:110" -p "143:143" -p "465:465" -p "587:587" -p "993:993" --link cassandra:cassandra --link elasticsearch:elasticsearch --name james_run -t james_run

Where :
- HOSTNAME: is the hostname you want to give to your James container. This DNS entry will be used to send mail to your James server.


* Useful commands

** How to add a domain ?
# Add DOMAIN to 127.0.0.1 in your host /etc/hosts
$ docker exec james_run /root/james-server-app-3.0.0-beta5-SNAPSHOT/bin/james-cli.sh -h 127.0.0.1 -p 9999 adddomain DOMAIN

Where :
- DOMAIN: is the domain you want to add.

** How to add a user ?
$ docker exec james_run /root/james-server-app-3.0.0-beta5-SNAPSHOT/bin/james-cli.sh -h 127.0.0.1 -p 9999 adduser USER_MAIL_ADDRESS PASSWORD

Where :
- USER_MAIL_ADDRESS: is the mail address that will be used by this user.
- PASSWORD: is the password that will be used by this user.

You can then just add DOMAIN to your /etc/hosts and you can connect to your james account with for instance Thunderbird.

** How to manage SIEVE scripts ?
Each user can manage his SIEVE scripts threw the manage SIEVE mailet.

To use the manage SIEVE mailet :

 - You need to create the user sievemanager@DOMAIN ( if you don't, the SMTP server will check the domain, recognize it, and look for an absent local user, and will generate an error ).
 - You can send Manage Sieve commands by mail to sievemanager@DOMAIN. Your subject must contain the command. Scripts needs to be added as attachments and need the ".sieve" extension.

To activate a script for a user, you need the following combinaison :

 - PUTSCRIPT scriptname
 - SETACTIVE scriptname

** I want to retrieve users and password from my previous container
Some james data ( those non related to mailbox, eg : mail queue, domains, users, rrt, SIEVE scripts, mail repositories ) are not yet supported by our Cassandra implementation.

To keep these data when you run a new container, you can mount the following volume :
 -v /root/james-server-app-3.0.0-beta5-SNAPSHOT/var:WORKDIR/destination/var

Where :
- WORKDIR: is the absolute path to your james-parent workdir.

Beware : you will have concurrency issues if multiple containers are running on this single volume.


Running deployement Tests
=========================

We wrote some MPT (James' Mail Protocols Tests subproject) deployement tests to validate a James
deployement.

It uses the External-James module, that uses environment variables to locate a remote
IMAP server and run integration tests against it.

For that, the target James Server needs to be configured with a domain domain and a user imapuser
with password password. Read above documentation to see how you can do this.

You have to run MPT tests inside docker. As you need to use maven, the simplest option is to
use james/parent image, and override the entry point ( as git and maven are already configured
there ) :
$ docker run -t --entrypoint="/root/integration_tests.sh" -v $PWD/.m2:/root/.m2 -v $PWD:/origin james/project JAMES_IP JAMES_PORT SHA1

Where :
 - JAMES_IP: IP address or DNS entry for your James server
 - JAMES_PORT: Port allocated to James' IMAP port (should be 143).
 - SHA1(optional): Branch to use in order to build integration tests or trunk


Howto check the merge of a commit
=================================

First step, you have to build the Docker image
$ docker build -t james/merge dockerfiles/merge

In order to run the build, you have to launch the following command:
$ docker run -v $PWD:/origin -t james/merge SHA1 RESULTING_BRANCH

Where :
- SHA1: is the given git SHA1 of the james-project repository to merge.
- RESULTING_BRANCH: is the branch created when merging.
