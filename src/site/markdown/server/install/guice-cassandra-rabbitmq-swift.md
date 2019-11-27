# Guice-Cassandra-Rabbitmq-Swift installation guide

## Building

### Requirements

 - Java 8 SDK
 - Docker ∕ ElasticSearch 6.3.2, RabbitMQ Management 3.3.7, Swift ObjectStorage 2.15.1 and Cassandra 3.11.3
 - Maven 3

### Building the artifacts

An usual compilation using maven will produce two artifacts into server/container/guice/cassandra-rabbitmq-guice/target directory:

 * james-server-cassandra-rabbitmq-guice.jar
 * james-server-cassandra-rabbitmq-guice.lib

You can for example run in the base of [this git repository](https://github.com/apache/james-project):

```
mvn clean install
```

## Running

### Requirements

 * Cassandra 3.11.3
 * ElasticSearch 6.3.2
 * RabbitMQ-Management 3.8.1
 * Swift ObjectStorage 2.15.1 or Scality S3 server or AWS S3

### James Launch

To run james, you have to create a directory containing required configuration files.

James requires the configuration to be in a subfolder of working directory that is called **conf**. You can get a sample
directory for configuration from
[dockerfiles/run/guice/cassandra-rabbitmq/destination/conf](https://github.com/apache/james-project/tree/master/dockerfiles/run/guice/cassandra-rabbitmq/destination/conf). You might need to adapt it to your needs.

You also need to generate a keystore in your conf folder with the following command:

```bash
$ keytool -genkey -alias james -keyalg RSA -keystore conf/keystore
```

You need to have a Cassandra, ElasticSearch and RabbitMQ instance running. You can either install the servers or launch them via docker:

```bash
$ docker run -d -p 9042:9042 --name=cassandra cassandra:3.11.3
$ docker run -d -p 9200:9200 --name=elasticsearch --env 'discovery.type=single-node' docker.elastic.co/elasticsearch/elasticsearch:6.3.2
$ docker run -d -p 5672:5672 -p 15672:15672 --name=rabbitmq rabbitmq:3.8.1-management
$ docker run -d -p 5000:5000 -p 8080:8080 -p 35357:35357 --name=swift linagora/openstack-keystone-swift:pike
```

Once everything is set up, you just have to run the jar with:

```bash
$ java -Dworking.directory=. -jar target/james-server-cassandra-rabbitmq-guice.jar
```

#### Using AWS S3 of Scality S3 server
In order to use AWS S3 or a compatible implementation, `blobstore.propeties` has to be filled with:

```
objectstorage.provider=aws-s3
objectstorage.namespace=james
objectstorage.s3.endPoint=http://scality:8080/
objectstorage.s3.accessKeyId=accessKey1
objectstorage.s3.secretKey=verySecretKey1
```

To use Scality S3 server you have to launch it instead of swift container:

```
$ docker run -d -p 8080:8000 --name=s3 scality/s3server:6018536a
```

More information about available options [here](https://hub.docker.com/r/scality/s3server).

## Guice-cassandra-rabbitmq-ldap

You can follow the same guide to build and run guice-cassandra-rabbitmq-swift-ldap artifact, except that:
 - The **jar** and **libs** needs to be retrieve from server/container/guice/cassandra-rabbitmq-ldap-guice/target after compilation
 - The sample configuration can be found in [dockerfiles/run/guice/cassandra-rabbitmq-ldap/destination/conf](https://github.com/apache/james-project/tree/master/dockerfiles/run/guice/cassandra-rabbitmq-ldap/destination/conf)
 - You need to configure James to be connecting to a running LDAP server. The configuration file is located in [dockerfiles/run/guice/cassandra-rabbitmq-ldap/destination/conf/usersrepository.xml](https://github.com/apache/james-project/tree/master/dockerfiles/run/guice/cassandra-rabbitmq-ldap/destination/conf/usersrepository.xml)
 - You can then launch James via this command:

```bash
$ java -Dworking.directory=. -jar target/james-server-cassandra-rabbitmq-ldap-guice.jar
```
