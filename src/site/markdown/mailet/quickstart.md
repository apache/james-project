Mailets for developers
======================

## Artifact names

All binary (and source) artifacts are available via [Maven Central](http://repo.maven.apache.org/maven2).

The project **groupId** is *org.apache.james* and the artifact names are:

* **apache-mailet-api** - for the Mailet API
* **apache-mailet-base** - for base Mailets
* **apache-mailet-standard** - for Standard Mailets
* **apache-mailet-crypto** - for Crypto Mailets
* **mailetdocs-maven-plugin** if you wish to extract documentation from sources

Just include something like this in your *pom.xml*

~~~
<dependencies>
    <dependency>
        <groupId>org.apache.james</groupId>
        <artifactId>apache-mailet-api</artifactId>
        <version>3.8.0</version>
    </dependency>
    <!-- other dependencies -->
</dependencies>
~~~

### Write your own mailets

To learn how to write your own mailets please have a look at
<a href="https://github.com/apache/james-project/blob/master/mailet/base/src/main/java/org/apache/mailet/base/GenericMatcher.java">Generic Matcher</a> and
<a href="https://github.com/apache/james-project/blob/master/mailet/base/src/main/java/org/apache/mailet/base/GenericMailet.java">Generic Mailet</a>.

Another good learning source are the unit tests from
<a href="https://github.com/apache/james-project/tree/master/mailet/standard/src/main/java/org/apache/james/transport">Standard Mailets</a>

