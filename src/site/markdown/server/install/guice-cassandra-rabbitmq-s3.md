# Guice-Cassandra-Rabbitmq-S3 installation guide

## Building

### Requirements

 - Java 11 SDK
 - Docker ∕ OpenSearch 2.1.0, RabbitMQ Management 3.8.18, compatible S3 ObjectStorage and Cassandra 3.11.10
 - Maven 3

### Building the artifacts

An usual compilation using maven will produce two artifacts into server/apps/distributed-app/target directory:

 * james-server-distributed-app.jar
 * james-server-distributed-app.lib

You can for example run in the base of [this git repository](https://github.com/apache/james-project):

```
mvn clean install
```

## Running

### Requirements

 * Cassandra 3.11.10
 * OpenSearch 2.1.0
 * RabbitMQ-Management 3.8.18
 * Zenko Cloudserver or AWS S3 compatible API

### James Launch

To run james, you have to create a directory containing required configuration files.

James requires the configuration to be in a subfolder of working directory that is called **conf**. You can get a sample
directory for configuration from
[server/apps/distributed-app/sample-configuration](https://github.com/apache/james-project/tree/master/server/apps/distributed-app/sample-configuration). You might need to adapt it to your needs.

You also need to generate a keystore in your conf folder with the following command:

```bash
$ keytool -genkey -alias james -keyalg RSA -keystore conf/keystore
```

You need to have a Cassandra, OpenSearch, S3 and RabbitMQ instance running. You can either install the servers or launch them via docker:

```bash
$ docker run -d -p 9042:9042 --name=cassandra cassandra:3.11.10
$ docker run -d --network james -p 9200:9200 --name=opensearch --env 'discovery.type=single-node' opensearchproject/opensearch:2.1.0
$ docker run -d -p 5672:5672 -p 15672:15672 --name=rabbitmq rabbitmq:3.12.1-management
$ docker run -d --env 'REMOTE_MANAGEMENT_DISABLE=1' --env 'SCALITY_ACCESS_KEY_ID=accessKey1' --env 'SCALITY_SECRET_ACCESS_KEY=secretKey1' --name=s3 registry.scality.com/cloudserver/cloudserver:8.7.25
```

Once everything is set up, you just have to run the jar with:

```bash
$ java -Dworking.directory=. -jar target/james-server-distributed-app.jar
```

#### Using AWS S3 of Zenko Cloudserver
By default, James is configured with [Zenko Cloudserver](https://hub.docker.com/r/zenko/cloudserver) which is compatible with AWS S3, in `blobstore.propeties` as such:

```
implementation=s3
objectstorage.namespace=james
objectstorage.s3.endPoint=http://s3.docker.test:8000/
objectstorage.s3.region=eu-west-1
objectstorage.s3.accessKeyId=accessKey1
objectstorage.s3.secretKey=secretKey1
```
