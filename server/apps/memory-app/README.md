# James Memory server

In order to build both the ZIP and docker packaging, run:

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
docker run apache/james:memory-latest
```


For security reasons you are required to generate your own keystore, that you can mount into the container via a volume:

```
keytool -genkey -alias james -keyalg RSA -keystore keystore
docker run -v $PWD/keystore:/root/conf/keystore apache/james:memory-latest
```

In the case of quick start James without manually creating a keystore (e.g. for development), just input the command argument `start-dev` when running,
James will auto-generate keystore file with the default setting:

```
docker run apache/james:memory-latest start-dev
```


Use the [JAVA_TOOL_OPTIONS environment option](https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#jvm-flags) 
to pass extra JVM flags. For instance:

```
docker run -e "JAVA_TOOL_OPTIONS=-Xmx500m -Xms500m" apache/james:memory-latest
```

[Glowroot APM](https://glowroot.org/) is packaged as part of the docker distribution to easily enable valuable performances insights.
Disabled by default, its java agent can easily be enabled:

```
docker run -e "JAVA_TOOL_OPTIONS=-javaagent:/root/glowroot.jar" apache/james:memory-latest
```
The [CLI](https://james.apache.org/server/manage-cli.html) can easily be used:

```
docker exec CONTAINER-ID james-cli ListDomains
```

Note that you can create a domain via an environment variable. This domain will be created upon James start:

```
--environment DOMAIN=domain.tld
```
