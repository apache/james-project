# Writing custom healthchecks

Read this page [on the website](http://james.apache.org/howTo/custom-healthchecks.html).

The current project demonstrates how to write custom healthchecks for Apache James. 
This enables writing new custom healthcheck that fits your monitoring need.

Start by importing the dependencies:

```
<dependency>
    <groupId>org.apache.james</groupId>
    <artifactId>james-core</artifactId>
</dependency>
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
</dependency>
```

You can then start writing your first HealthCheck by implementing HealthCheck interface.
 
You can compile this example project:

```
mvn clean install
```

Then embed your healthcheck into a James server. First configure your custom healthcheck into `healthcheck.properties`:

```
# List of fully qualified HealthCheck class names in addition to James' default healthchecks.
# Healthchecks need to be located within the classpath or in the ./extensions-jars folder.
additional.healthchecks=org.apache.james.examples.HealthCheckA
```

Then start a James server with your JAR and the configuration:

```
$ docker run -d \
   -v $PWD/healthcheck.properties:/root/conf/healthcheck.properties \
   -v $PWD/healthcheck-extension.jar:/root/extensions-jars \
   -p 25:25 \
   apache/james:memory-latest --generate-keystore
```

You can use `curl` command to get your healthcheck status:

```
$ curl -XGET http://172.17.0.2:8000/healthcheck
```