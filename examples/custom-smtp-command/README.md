# Creating your own SMTP commands

Read this page [on the website](http://james.apache.org/howTo/custom-smtp-commands.html).

The current project demonstrates how to write custom commands for Apache James SMTP server.

Start by importing the dependencies:

```
<dependency>
    <groupId>org.apache.james.protocols</groupId>
    <artifactId>protocols-smtp/artifactId>
</dependency>
```

You can write your commands by extending the `CommandHandler<SMTPSession>` class. For instance:

```
/**
  * Copy of NoopCmdHandler
  */
public class MyNoopCmdHandler implements CommandHandler<SMTPSession> {
    private static final Collection<String> COMMANDS = ImmutableSet.of("MYNOOP");

    private static final Response NOOP = new SMTPResponse(SMTPRetCode.MAIL_OK,
        DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.UNDEFINED_STATUS) + " OK")
        .immutable();

    @Override
    public Response onCommand(SMTPSession session, Request request) {
        return NOOP;
    }
    
    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
```

You then need to list the exposed SMTP commands with a `HandlersPackage`. For instance:

```
/**
 * This class copies CoreCmdHandlerLoader adding support for MYNOOP command
 */
public class MyCmdHandlerLoader implements HandlersPackage {

    private final List<String> commands = new LinkedList<>();

    public MyCmdHandlerLoader() {
        Stream.of(
            JamesWelcomeMessageHandler.class,
            CommandDispatcher.class,
            AuthCmdHandler.class,
            JamesDataCmdHandler.class,
            EhloCmdHandler.class,
            ExpnCmdHandler.class,
            HeloCmdHandler.class,
            HelpCmdHandler.class,
            JamesMailCmdHandler.class,
            NoopCmdHandler.class,
            QuitCmdHandler.class,
            JamesRcptCmdHandler.class,
            RsetCmdHandler.class,
            VrfyCmdHandler.class,
            MailSizeEsmtpExtension.class,
            UsersRepositoryAuthHook.class,
            AuthRequiredToRelayRcptHook.class,
            SenderAuthIdentifyVerificationHook.class,
            PostmasterAbuseRcptHook.class,
            ReceivedDataLineFilter.class,
            DataLineJamesMessageHookHandler.class,
            StartTlsCmdHandler.class,
            AddDefaultAttributesMessageHook.class,
            SendMailHandler.class,
            UnknownCmdHandler.class,
            CommandHandlerResultLogger.class,
            HookResultLogger.class,
            // Support MYNOOP
            MyNoopCmdHandler.class)
        .map(Class::getName)
        .forEachOrdered(commands::add);
    }

    @Override
    public List<String> getHandlers() {
        return commands;
    }
}
```

Then compile this little project:

```
mvn clean install
```

Write a configuration file telling James to use your `HandlerPackage`:

```
<smtpservers>
    <smtpserver enabled="true">
        <jmxName>smtpserver-global</jmxName>
        <bind>0.0.0.0:25</bind>
        <connectionBacklog>200</connectionBacklog>
        <tls socketTLS="false" startTLS="false">
            <keystore>file://conf/keystore</keystore>
            <secret>james72laBalle</secret>
            <provider>org.bouncycastle.jce.provider.BouncyCastleProvider</provider>
            <algorithm>SunX509</algorithm>
        </tls>
        <!-- ... -->
        <handlerchain coreHandlersPackage="org.apache.james.examples.MyCmdHandlerLoader">
            <handler class="org.apache.james.smtpserver.fastfail.ValidRcptHandler"/>
        </handlerchain>
    </smtpserver>
</smtpservers>
```

Then start a James server with your JAR and the configuration:

```
docker run -d \
  -v $PWD/smtpserver.xml:/root/conf/smtpserver.xml \
  -v $PWD/exts:/root/extensions-jars \
  -p 25:25 \
  apache/james:memory-latest --generate-keystore
```

You can play with `telnet` utility with the resulting server:

```
$ telnet 127.0.0.1 25
Trying 127.0.0.1...
Connected to 127.0.0.1.
Escape character is '^]'.
220 Apache JAMES awesome SMTP Server
MYNOOP
250 2.0.0 OK
quit
221 2.0.0 1f0274082fc6 Service closing transmission channel
Connection closed by foreign host.
```
