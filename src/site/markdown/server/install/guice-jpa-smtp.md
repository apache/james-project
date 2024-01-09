# Guice-JPA-SMTP installation guide

## Building

### Requirements

 - Java 11 SDK
 - Docker
 - Maven (optional)

### Download the artifacts

Download james-jpa-smtp-guice-3.8.1.zip from [the download page](http://james.apache.org/download.cgi#Apache_James_Server) and deflate it.

### (alternative) Building the artifacts

An usual compilation using maven of this [Git repository content](https://github.com/apache/james-project) will produce
two artifacts into server/container/guice/jpa-smtp/target directory :

 - james-server-jpa-smtp-${version}.jar
 - james-server-jpa-smtp-${version}.lib

 To run james, you have to create a directory containing required configuration files names **conf**.

 A [sample directory](https://github.com/apache/james-project/tree/master/server/apps/jpa-smtp-app/sample-configuration) is provided with some default value you may need to replace.


## Running

### James Launch

Edit the configuration to match your needs.

You also need to generate a keystore with the following command :

```bash
$ keytool -genkey -alias james -keyalg RSA -keystore conf/keystore
```

Once everything is set up, you just have to run the jar with :

```bash
$ java -javaagent:james-server-jpa-smtp-app.lib/openjpa-3.2.0.jar \
  -Dworking.directory=. \
  -Djdk.tls.ephemeralDHKeySize=2048 \
  -Dlogback.configurationFile=conf/logback.xml \
  -jar james-server-jpa-smtp-app.jar
```
