# How to

This project is used to generate custom mailet / matcher used in unit tests of the underlying Guice project.

This associated JAR is in the test resources.

Run:

```
mvn clean install
```

And overwrite it in *server/container/guice/mailet/src/test/resources/recursive/extensions-jars/\*.jar* to update it.