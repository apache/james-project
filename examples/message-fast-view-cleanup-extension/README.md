# How to use message fast view cleanup extension

This extension is used to delete redundant message fast view items. Do the following:

```
mvn clean install
```

Then embed your route into a James server. First configure your route into `webadmin.properties`:

```
enabled=true
port=8000
host=localhost

# List of fully qualified class names that should be exposed over webadmin
# in addition to your product default routes. Routes needs to be located
# within the classpath or in the ./extensions-jars folder.
extensions.routes=org.apache.james.messagefastview.cleanup.MessageFastViewCleanupRoute
```

Then start a James server with your JAR and the configuration:

```
$ docker run -d \
   -v $PWD/webadmin.properties:/root/conf/webadmin.properties \
   -v $PWD/exts:/root/extensions-jars \
   -p 25:25 \
   apache/james:memory-latest --generate-keystore
```

You can play with `curl` utility with the resulting server:

```
$ curl -XPOST http://172.17.0.2:8000/messageFastViewCleanup
```