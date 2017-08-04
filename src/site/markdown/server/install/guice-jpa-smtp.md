# Guice-JPA-SMTP installation guide

## Building

### Requirements

 - Java 8 SDK
 - Docker
 - Maven

### Building the artifacts

An usual compilation using maven of this [Git repository content](https://github.com/apache/james-project) will produce
two artifacts into server/container/guice/jpa-smtp/target directory :

 - james-server-jpa-smtp-${version}.jar
 - james-server-jpa-smtp-${version}.lib

## Running

### James Launch

To run james, you have to create a directory containing required configuration files.

A [sample directory](https://github.com/apache/james-project/tree/master/server/container/guice/jpa-smtp/sample-configuration) is provided with some default value you may need to replace.

You also need to generate a keystore with the following command :

```bash
$ keytool -genkey -alias james -keyalg RSA -keystore conf/keystore
```

Once everything is set up, you just have to run the jar with :

```bash
$ java -Dworking.directory=sample-configuration -jar target/james-server-jpa-smtp-${version}.jar
```