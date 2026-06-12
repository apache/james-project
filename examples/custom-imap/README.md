# Creating your own IMAP commands

James allows defining your own handler packages (logical union).
To do that you need to define `imapPackages` in `imapserver.xml` configuration file.

Eg:

```xml
<imapPackages>org.apache.james.modules.protocols.DefaultImapPackage</imapPackages>
<imapPackages>org.apache.james.examples.imap.PingImapPackages</imapPackages>
```
Sample configure file: [imapserver.xml](./sample-configuration/imapserver.xml)

Note that when `imapPackages` is not provided, James will implicit use
`org.apache.James.modules.protocols.DefaultImapPackage`

# Creating your own IMAP SASL mechanisms

This example also demonstrates how to add a custom IMAP SASL mechanism.
The `EXAMPLE-TOKEN` mechanism is declared through `auth.saslMechanisms`,
while `auth.exampleToken` is a custom configuration block owned by the extension:

```xml
<auth>
    <saslMechanisms>PlainSaslMechanism,org.apache.james.examples.imap.sasl.ExampleTokenSaslMechanism</saslMechanisms>
    <exampleToken>
        <expectedToken>secret-token</expectedToken>
        <authorizedUser>bob@domain.tld</authorizedUser>
    </exampleToken>
</auth>
```

The extension module is declared in `extensions.properties`:

```properties
guice.extension.module=org.apache.james.examples.imap.sasl.ExampleTokenSaslModule
```

The module binds a `SaslMechanismFactory` for `ExampleTokenSaslMechanism`.
James still uses `auth.saslMechanisms` to select the mechanism for one IMAP
server, and the factory reads that server's `auth.exampleToken` block.

## Running the example

Build the project:

```
mvn clean install
```

Drop the jar with dependencies in the James `extensions-jars` folder.

Then start James.

## Running the example with docker compose

Compile:

```
mvn clean install
```

Then start James:

```
docker-compose up
```

Test with imap package: `org.apache.james.examples.imap.PingImapPackages`

Command example:
```bash
telnet localhost 143
Trying 127.0.0.1...
Connected to localhost.
Escape character is '^]'.
* OK JAMES IMAP4rev1 Server james.local is ready.
a01 LOGIN bob secret
a01 NO LOGIN failed. Invalid credentials.
a02 LOGIN bob@localhost secret
a02 OK LOGIN completed.
A03 PING
* PONG
A03 OK PING completed.
A04 LOGOUT
```

Test the custom SASL mechanism:

```bash
telnet localhost 143
Trying 127.0.0.1...
Connected to localhost.
Escape character is '^]'.
* OK JAMES IMAP4rev1 Server james.local is ready.
A01 CAPABILITY
* CAPABILITY IMAP4rev1 AUTH=PLAIN SASL-IR AUTH=EXAMPLE-TOKEN PING
A01 OK CAPABILITY completed.
A02 AUTHENTICATE EXAMPLE-TOKEN c2VjcmV0LXRva2Vu
A02 OK AUTHENTICATE completed.
A03 PING
* PONG
A03 OK PING completed.
```

The custom SASL mechanism also supports a continuation when the client does not send
the initial response in the `AUTHENTICATE` command. The continuation payload is
base64-encoded by IMAP, so `R28gYWhlYWQ` decodes to `Go ahead`:

```bash
telnet localhost 143
Trying 127.0.0.1...
Connected to localhost.
Escape character is '^]'.
* OK JAMES IMAP4rev1 Server james.local is ready.
A01 AUTHENTICATE EXAMPLE-TOKEN
+ R28gYWhlYWQ
c2VjcmV0LXRva2Vu
A01 OK AUTHENTICATE completed.
A02 PING
* PONG
A02 OK PING completed.
```

The mechanism can also return final server data on success. The client acknowledges
that final data with an empty line before James sends the tagged `OK`:

```bash
telnet localhost 143
Trying 127.0.0.1...
Connected to localhost.
Escape character is '^]'.
* OK JAMES IMAP4rev1 Server james.local is ready.
A01 AUTHENTICATE EXAMPLE-TOKEN
+ R28gYWhlYWQ
c2VjcmV0LXRva2VuOnNlcnZlci1kYXRh
+ VG9rZW4gYWNjZXB0ZWQ=

A01 OK AUTHENTICATE completed.
```
