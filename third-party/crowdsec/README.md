# James' extensions for Crowdsec

This module is for developing and delivering extensions to James for the [Crowdsec](https://www.crowdsec.net/) (IP filtering system)

## How to run

- The Crowdsec extension requires an extra configuration file `crowdsec.properties` to configure Crowdsec connection
  Configuration parameters:
    - `crowdsecUrl` : String. Required. URL defining the Crowdsec's bouncer. Eg: http://crowdsec:8080/v1
    - `apiKey` : String. Required. Api key for pass authentication when request to Crowdsec.
    - `timeout` : Duration. Optional. Default to `500ms`. Timeout questioning to CrowdSec. 
      E.g. `500ms`, `1 second`,...

- Declare the `extensions.properties` for this module.

```
guice.extension.module=org.apache.james.crowdsec.module.CrowdsecModule
```

- Mount the configuration file onto the classpath

You can find a `logback.xml` configuration file in the `sample-configuration` directory. \
To use this configuration at runtime, ensure the file is available in the application's classpath by placing it in a directory accessible by the application.
Use a `-Dlogback.configurationFile` JVM argument to specify the file's location when starting the application.

### CrowdSec support for SMTP
- Declare the Crowdsec EhloHook in `smtpserver.xml`. Eg:

```
<handlerchain>
    <handler class="org.apache.james.smtpserver.fastfail.ValidRcptHandler"/>
    <handler class="org.apache.james.smtpserver.CoreCmdHandlerLoader"/>
    <handler class="org.apache.james.crowdsec.CrowdsecEhloHook"/>
</handlerchain>
```
or 
```
<handlerchain>
    <handler class="org.apache.james.smtpserver.fastfail.ValidRcptHandler"/>
    <handler class="org.apache.james.smtpserver.CoreCmdHandlerLoader"/>
    <handler class="org.apache.james.crowdsec.CrowdsecSMTPConnectHandler"/>
</handlerchain>
```

The EHLO hook will block banned clients with `554 Email rejected` whereas the connect handler will terminate the connection even before the SMTP greeting. 

### CrowdSec support for IMAP
- Declare the `CrowdsecImapConnectionCheck` in `imapserver.xml`. Eg:

```
<imapserver enabled="true">
        ...
        <additionalConnectionChecks>org.apache.james.crowdsec.CrowdsecImapConnectionCheck</additionalConnectionChecks>
</imapserver>
```

### CrowdSec support for POP3
- Declare the `CrowdsecPOP3CheckHandler` in `pop3server.xml`. Eg:
- 
```
<pop3server enabled="true">
    <handlerchain>
        <handler class="org.apache.james.pop3server.core.CoreCmdHandlerLoader"/>
        <handler class="org.apache.james.crowdsec.CrowdsecPOP3CheckHandler"/>
    </handlerchain>
</pop3server>
```

- Docker compose file example: [docker-compose.yml](docker-compose.yml).
- The sample-configuration: [sample-configuration](sample-configuration)
- For running docker-compose, first compile this project

```
mvn clean install -DskipTests
```

Customise the crowdsec image:

```
docker build -t apache/james:memory-crowdsec .
```

Download extra dependencies:

```
$ wget https://repo1.maven.org/maven2/ch/qos/logback/contrib/logback-jackson/0.1.5/logback-jackson-0.1.5.jar
$ wget https://repo1.maven.org/maven2/ch/qos/logback/contrib/logback-json-core/0.1.5/logback-json-core-0.1.5.jar
```

Then run it: `docker-compose up`

## Crowdsec endpoints

### Check IP blocked by Crowdsec (decisions)

Crowdsec will expose port 8080 for queries to get the list of IP addresses blocked (get decisions)

```bash
curl -XGET http://localhost:8080/v1/decisions -H "X-Api-Key: default_api_key" -H 'accept: application/json' | jq .
```

Response codes:
- 200: Success
- 403: Invalid apikey. Try with a different value for apikey.

Responses:
- It will be null if there is no decision.
- Return a list of decisions. E.g:
```
[
  {
    "duration": "3h59m50.276482904s",
    "id": 4,
    "origin": "cscli",
    "scenario": "manual 'ban' from 'localhost'",
    "scope": "Ip",
    "type": "ban",
    "value": "1.2.3.4"
  }
]
```