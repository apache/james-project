# Pulsar backed SMTP processing

## App goals

Ultimately this app will provide an easily scalable smtp processing module based on Apache Pulsar for small to large deployments. The dependency on cassandra is temporary and will be removed incrementally as suitable alternative implementations for the guice modules are integrated.

Cassandra is pretty expensive to operate and only makes sense for really large scale deployments. On the other hand, there are cost-efficient SAAS deployments of pulsar. For small scale deployments destined to grow this app will instead rely on 
- A blobstore (for mail repository) 
- A PostgresSQL database for reference data (domain list, recipient rewrite table and users)

The idea being that it will be easy enough to operate this assembly at a "reasonable" cost in the cloud until the scale is large enough to warrant switching to the fully distributed app. 

## Building

In order to build the docker packaging, run:

```
mvn clean install -DskipTests
```

## ZIP distribution

Available in `target` directory, the ZIP include detailed instructions.

## Docker distribution

To import the image locally:

```
docker image load -i target/jib-image.tar
```

Then run it:

```
docker run apache/james:scaling-pulsar-smtp-latest
```


For security reasons you are required to generate your own keystore, that you can mount into the container via a volume:

```
keytool -genkey -alias james -keyalg RSA -keystore keystore
docker run -v $PWD/keystore:/root/conf/keystore apache/james:scaling-pulsar-smtp-latest
```

In the case of quick start James without manually creating a keystore (e.g. for development), just input the command argument `--generate-keystore` when running,
James will auto-generate keystore file with the default setting that is declared in `jmap.properties` (tls.keystoreURL, tls.secret)

```
docker run --network james apache/james:scaling-pulsar-smtp-latest --generate-keystore
```

Use the [JAVA_TOOL_OPTIONS environment option](https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#jvm-flags) 
to pass extra JVM flags. For instance:

```
docker run -e "JAVA_TOOL_OPTIONS=-Xmx500m -Xms500m" apache/james:scaling-pulsar-smtp-latest
```

[Glowroot APM](https://glowroot.org/) is packaged as part of the docker distribution to easily enable valuable performances insights.
Disabled by default, its java agent can easily be enabled:

```
docker run -e "JAVA_TOOL_OPTIONS=-javaagent:/root/glowroot.jar" apache/james:scaling-pulsar-smtp-latest
```
The [CLI](https://james.apache.org/server/manage-cli.html) can easily be used:

```
docker exec CONTAINER-ID james-cli ListDomains
```

Note that you can create a domain via an environment variable. This domain will be created upon James start:

```
--environment DOMAIN=domain.tld
```
