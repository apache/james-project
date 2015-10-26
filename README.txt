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
$ docker run -v $PWD/.m2:/root/.m2 -v $PWD:/origin -v $PWD/dockerfiles/destination:/destination -t james/project -s SHA1

Where:

- $PWD/.m2:/root/.m2: is the first volume used to share the maven repository, 
as we don't want to download all dependencies on each build
- $PWD/dockerfiles/destination:/destination: is the third volume used to get the compiled elements, 
as it is needed by the container that will run James.
- SHA1 (optional): is the given git SHA1 of the james-project repository to build or trunk if none.
- -s option: given tests will not be played while building. Not specifying means play tests.


* Java 8
First step, you have to build the Docker image
$ docker build -t james/project dockerfiles/compilation/java-8

In order to run the build, you have to launch the following command:
$ docker run -v $PWD/.m2:/root/.m2 -v $PWD:/origin -v $PWD/dockerfiles/destination:/destination -t james/project -s SHA1

Where:

- $PWD/.m2:/root/.m2: is the first volume used to share the maven repository, 
as we don't want to download all dependencies on each build
- $PWD/dockerfiles/destination:/destination: is the third volume used to get the compiled elements, 
as it is needed by the container that will run James.
- SHA1 (optional): is the given git SHA1 of the james-project repository to build or trunk if none.
- -s option: given tests will not be played while building. Not specifying means play tests.
