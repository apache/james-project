# Guice-JPA installation guide

## Building

### Requirements

 - Java 11 SDK
 - Maven 3 (optional)

### Download the artifacts

Download james-jpa-guice-3.8.0.zip from [the download page](http://james.apache.org/download.cgi#Apache_James_Server) and deflate it.

### (alternative) Building the artifacts

An usual compilation using maven of this [Git repository content](https://github.com/apache/james-project) will produce
two artifacts into server/container/guice/jpa-guice/target directory:

 - james-server-jpa-guice.jar
 - james-server-jpa-guice.lib

You can for example run in the base of this git repository:

```
mvn clean install
```

To run james, you have to create a directory containing required configuration files.

James requires the configuration to be in a subfolder of working directory that is called **conf**. You can get a sample
directory for configuration from [server/apps/jpa-app/sample-configuration](https://github.com/apache/james-project/tree/master/server/apps/jpa-app/sample-configuration). You might need to adapt it to your needs.


## Running

### James Launch

Edit the configuration to match your needs.

You also need to generate a keystore in your conf folder with the following command:

```bash
$ keytool -genkey -alias james -keyalg RSA -keystore conf/keystore
```

Once everything is set up, you just have to run the jar with:

```bash
$ java -classpath 'james-server-jpa-guice.jar:james-server-jpa-guice.lib/*' \
    -javaagent:james-server-jpa-guice.lib/openjpa-3.0.0.jar \
    -Dlogback.configurationFile=conf/logback.xml \
    -Dworking.directory=. \
    org.apache.james.JPAJamesServerMain
```
