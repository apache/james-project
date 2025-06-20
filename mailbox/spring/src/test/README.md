
Running these tests requires applying the openjpa javaagent. This is configured for you when running the tests through maven. 
In your IDE you will have to add the following JVM argument (the version may vary check the apache.openjpa.version property value in the top level pom.xml): 

```
-javaagent:${HOME}/.m2/repository/org/apache/openjpa/openjpa/4.1.1/openjpa-4.1.1.jar
```