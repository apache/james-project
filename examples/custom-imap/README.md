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
a01 NO LOGIN failed. Invalid login/password.
a02 LOGIN bob@localhost secret
a02 OK LOGIN completed.
A03 PING
* PONG
A03 OK PING completed.
```
