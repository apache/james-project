# Guice-Cassandra installation guide

## Building

### Requirements

 - Java 11 SDK
 - Docker ∕ OpenSearch 2.1.0 and Cassandra 4.0
 - Maven 3

*WARNING*: JAMES-3591 Cassandra is not made to store large binary content, its use will be suboptimal compared to
alternatives (namely S3 compatible BlobStores backed by for instance S3, MinIO or Ozone)

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

 * Cassandra 4.0
 * OpenSearch 2.1.0

### James Launch

To run james, you have to create a directory containing required configuration files.

James requires the configuration to be in a subfolder of working directory that is called **conf**. You can get a sample
directory for configuration from
[server/apps/cassandra-app/sample-configuration](https://github.com/apache/james-project/tree/master/server/apps/cassandra-app/sample-configuration). You might need to adapt it to your needs.

You also need to generate a keystore in your conf folder with the following command:

```bash
$ keytool -genkey -alias james -keyalg RSA -keystore conf/keystore
```

You need to have a Cassandra and an OpenSearch instance running. You can either install the servers or launch them via docker:

```bash
$ docker run -d -p 9042:9042 --name=cassandra cassandra:4.1.3
$ docker run -d --network james -p 9200:9200 --name=opensearch --env 'discovery.type=single-node' opensearchproject/opensearch:2.1.0
```

Once everything is set up, you just have to run the jar with:

```bash
$ java -Dworking.directory=. -jar target/james-server-cassandra-guice.jar
```

