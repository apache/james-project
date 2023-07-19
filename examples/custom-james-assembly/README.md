# Assemble a James server tailored to your needs

Read this page [on the website](http://james.apache.org/howTo/custom-james-assembly.html).

The current project demonstrates how to write a custom assembly in order to write your
own tailor-made server.

This enables:

 - Arbitrary composition of technologies (example JPA mailbox with Cassandra user management)
 - Write any additional components
 - Drop any unneeded component
 - You have control on the dependencies and can reduce the classpath size

## Example: Write an IMAP+SMTP only memory server

In order to do this select the modules you wished to assemble [in the Guice building blocks](https://github.com/apache/james-project/tree/master/server/container/guice). We encourage you to have
a fine grain control of your dependencies but for the sake of simplicity this example will reuse the dependencies of an
existing James application:

```
<dependency>
    <groupId>${james.groupId}</groupId>
    <artifactId>james-server-memory-app</artifactId>
    <version>${project.version}</version>
</dependency>
```

Once done assemble the guice modules together in a class implementing `JamesServerMain`:

```
public class CustomJamesServerMain implements JamesServerMain {
       public static final Module PROTOCOLS = Modules.combine(
           new IMAPServerModule(),
           new ProtocolHandlerModule(),
           new MailRepositoryTaskSerializationModule(),
           new SMTPServerModule());
   
       public static final Module CUSTOM_SERVER_MODULE = Modules.combine(
           new MailetProcessingModule(),
           new MailboxModule(),
           new MemoryDataModule(),
           new MemoryEventStoreModule(),
           new MemoryMailboxModule(),
           new MemoryMailQueueModule(),
           new TaskManagerModule(),
           new RawPostDequeueDecoratorModule(),
           binder -> binder.bind(MailetContainerModule.DefaultProcessorsConfigurationSupplier.class)
               .toInstance(BaseHierarchicalConfiguration::new));
   
       public static final Module CUSTOM_SERVER_AGGREGATE_MODULE = Modules.combine(
           CUSTOM_SERVER_MODULE,
           PROTOCOLS);
   
       public static void main(String[] args) throws Exception {
           Configuration configuration = Configuration.builder()
               .useWorkingDirectoryEnvProperty()
               .build();
   
           JamesServerMain.main(GuiceJamesServer.forConfiguration(configuration)
               .combineWith(CUSTOM_SERVER_AGGREGATE_MODULE));
       }
   }
```

You need to write a minimal main method launching your guice module composition.

We do provide in this example [JIB](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin) to package this custom James assembly into docker:

```
<plugin>
    <groupId>com.google.cloud.tools</groupId>
    <artifactId>jib-maven-plugin</artifactId>
    <version>3.3.2</version>
    <configuration>
        <from>
            <image>adoptopenjdk:11-jdk-hotspot</image>
        </from>
        <to>
            <image>apache/james</image>
            <tags>
                <tag>custom-latest</tag>
            </tags>
        </to>
        <container>
            <mainClass>org.apache.james.examples.CustomJamesServerMain</mainClass>
            <ports>
                <port>25</port> <!-- JMAP -->
                <port>143</port> <!-- IMAP -->
            </ports>
            <appRoot>/root</appRoot>
            <jvmFlags>
                <jvmFlag>-Dlogback.configurationFile=/root/conf/logback.xml</jvmFlag>
                <jvmFlag>-Dworking.directory=/root/</jvmFlag>
            </jvmFlags>
            <creationTime>USE_CURRENT_TIMESTAMP</creationTime>
        </container>
        <extraDirectories>
            <paths>
                <path>
                    <from>sample-configuration</from>
                    <into>/root/conf</into>
                </path>
            </paths>
        </extraDirectories>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>buildTar</goal>
            </goals>
            <phase>package</phase>
        </execution>
    </executions>
</plugin>
```

We provide a minimal [sample configuration](https://github.com/apache/james-project/tree/master/examples/custom-james-assembly/sample-configuration).

You can compile this example project:

```
mvn clean install
```

Create a keystore (default password being `james72laBalle`):

```
keytool -genkey -alias james -keyalg RSA -keystore keystore
```

Import the build result:

```
$ docker load -i target/jib-image.tar
```

Then launch your custom server with docker:

```
docker run \
    -v $PWD/keystore:/root/conf/keystore \
    -p 25:25 \
    -p 143:143 \
    -ti  \ 
    apache/james:custom-latest
```

You will see that your custom James server starts smoothly:

```
...
09:40:25.884 [INFO ] o.a.j.GuiceJamesServer - JAMES server started
```
