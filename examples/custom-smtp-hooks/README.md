# Writing custom behaviour for Apache James SMTP server

Read this page [on the website](http://james.apache.org/howTo/custom-smtp-hooks.html).

The current project demonstrates how to write custom behaviours for Apache James SMTP server
by the means of SMTP hooks.

SMTP hooks allow integrating third party systems with the SMTP stack and writing additional SMTP extensions, for
instance.

Start by importing the dependencies:

```
<dependency>
    <groupId>org.apache.james.protocols</groupId>
    <artifactId>protocols-smtp/artifactId>
</dependency>
```

Allows writing the following hooks:

 - `AuthHook`: hook in the AUTH Command
 - `HeloHook`: hook in the HELO Command
 - `MailHook`: hook in the MAIL Command
 - `MailParametersHook`: hook for MAIL Command parameters
 - `RcptHook`: hook in the RCPT Command
 - `MessageHook`: hook in the DATA Command
 - `QuitHook`: hook in the QUIT Command
 - `UnknownHook`: hook for unknown commands
 
```
<dependency>
    <groupId>org.apache.james</groupId>
    <artifactId>james-server-protocols-smtp</artifactId>
</dependency>
```

Allows writing the following hooks:

 - `JamesMessageHook`: DATA command. Allows access to the message content.
 
James comes bundled with [provided SMTP hooks for common features](http://james.apache.org/server/dev-provided-smtp-hooks.html).
We encourage you to review them before starting writing your own hooks.

## Writing your own hooks
 
In this example we implement a single RCPT hook:

```
public class LoggingRcptHook implements RcptHook {
    @Override
    public HookResult doRcpt(SMTPSession session, MaybeSender sender, MailAddress rcpt, Map<String, String> parameters) {
        System.out.println("RCPT TO " + rcpt + "with parameters " + parameters);

        // Continue the SMTP transaction
        return HookResult.DECLINED;
    }
}
```

You can compile this example project:

```
mvn clean install
```

Then embed your extension into a James server. First configure your hook:

```
<?xml version="1.0"?>
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
        <handlerchain>
            <handler class="org.apache.james.smtpserver.fastfail.ValidRcptHandler"/>
            <handler class="org.apache.james.smtpserver.CoreCmdHandlerLoader"/>
            <handler class="org.apache.james.examples.LoggingRcptHook"/>
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
EHLO toto.com
250-177b73020637 Hello toto.com [172.17.0.1])
250-PIPELINING
250-ENHANCEDSTATUSCODES
250 8BITMIME
RCPT TO: <b@c.com>
503 5.5.0 Need MAIL before RCPT
MAIL FROM: <b@c.com>
250 2.1.0 Sender <b@c.com> OK
RCPT TO: <c@d.com>
550 5.7.1 Requested action not taken: relaying denied
quit
```

Now looking at the logs we can verify that our RCPT have well been executed:

```
06:49:15.734 [INFO ] o.a.j.GuiceJamesServer - JAMES server started
06:49:30.275 [INFO ] o.a.j.p.n.BasicChannelUpstreamHandler - Connection established from 172.17.0.1
06:50:18.892 [INFO ] o.a.j.i.n.ImapChannelUpstreamHandler - Connection established from 172.17.0.1
06:50:19.152 [INFO ] o.a.j.i.n.ImapChannelUpstreamHandler - Connection closed for 172.17.0.1
RCPT TO c@d.comwith parameters {TO:=, <c@d.com>=}
```

Note that hooks can also be written for the LMTP protocol.
