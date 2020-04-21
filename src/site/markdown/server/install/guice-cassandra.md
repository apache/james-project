# Guice-Cassandra installation guide

## Building

### Requirements

 - Java 11 SDK
 - Docker ∕ ElasticSearch 6.3.2 and Cassandra 3.11.3
 - Maven 3

### Building the artifacts

An usual compilation using maven will produce two artifacts into server/container/guice/cassandra-guice/target directory:

 * james-server-cassandra-guice.jar
 * james-server-cassandra-guice.lib

You can for example run in the base of [this git repository](https://github.com/apache/james-project):

```
mvn clean install
```

## Running

### Requirements

 * Cassandra 3.11.3
 * ElasticSearch 6.3.2

### James Launch

To run james, you have to create a directory containing required configuration files.

James requires the configuration to be in a subfolder of working directory that is called **conf**. You can get a sample
directory for configuration from
[dockerfiles/run/guice/cassandra/destination/conf](https://github.com/apache/james-project/tree/master/dockerfiles/run/guice/cassandra/destination/conf). You might need to adapt it to your needs.

You also need to generate a keystore in your conf folder with the following command:

```bash
$ keytool -genkey -alias james -keyalg RSA -keystore conf/keystore
```

You need to have a Cassandra and an ElasticSearch instance running. You can either install the servers or launch them via docker:

```bash
$ docker run -d -p 9042:9042 --name=cassandra cassandra:3.11.3
$ docker run -d -p 9200:9200 --name=elasticsearch --env 'discovery.type=single-node' docker.elastic.co/elasticsearch/elasticsearch:6.3.2
```

Once everything is set up, you just have to run the jar with:

```bash
$ java -Dworking.directory=. -jar target/james-server-cassandra-guice.jar
```

## Guice-cassandra-ldap

You can follow the same guide to build and run guice-cassandra-ldap artifact, except that:
 - The **jar** and **libs** needs to be retrieve from server/container/guice/cassandra-ldap-guice/target after compilation
 - The sample configuration can be found in [dockerfiles/run/guice/cassandra-ldap/destination/conf](https://github.com/apache/james-project/tree/master/dockerfiles/run/guice/cassandra-ldap/destination/conf)
 - You need a running LDAP server to connect to.
 - You can then launch James via this command:

```bash
$ java -Dworking.directory=. -jar target/james-server-cassandra-ldap-guice.jar
```
