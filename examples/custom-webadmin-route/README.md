# Writing custom webadmin routes

Read this page [on the website](http://james.apache.org/howTo/custom-webadmin-routes.html).

The current project demonstrates how to write custom webadmin routes for Apache James. This enables writing new 
administrative features exposed over a REST API. This can allow you to write some additional features, make James 
interact with third party systems, do advance reporting... 

Start by importing the dependencies:

```
<dependency>
    <groupId>org.apache.james</groupId>
    <artifactId>james-server-webadmin-core</artifactId>
</dependency>
```

You can then write your first route using the [Spark Java](https://sparkjava.com/) framework:

```
public class RouteA implements Routes {
    @Override
    public String getBasePath() {
        return "/hello/a";
    }

    @Override
    public void define(Service service) {
        service.get(getBasePath(), (req, res) -> "RouteA\n");
    }
}
```

Knowing that:
 - entending `Routes` will ensure that authentication is requested if configured.
 - extending `PublicRoutes` will not request authentication.
 
You can compile this example project:

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
extensions.routes=org.apache.james.examples.RouteA
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
$ curl -XGET http://172.17.0.2:8000/hello/a
RouteA
```