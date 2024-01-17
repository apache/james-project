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
then run it: `docker-compose up`

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