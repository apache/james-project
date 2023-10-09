# James' extensions for Rspamd

This module is for developing and delivering extensions to James for the [Rspamd](https://rspamd.com/) (the spam filtering system)
and [ClamAV](https://www.clamav.net/) (the antivirus engine).

## How to run

- The Rspamd extension requires an extra configuration file `rspamd.properties` to configure Rspamd connection
Configuration parameters:
    - `rSpamdUrl` : URL defining the Rspamd's server. Eg: http://rspamd:11334
    - `rSpamdPassword` : Password for pass authentication when request to Rspamd's server. Eg: admin
    - `rspamdTimeout` : Timeout for HTTP requests called to Rspamd. Default to 15 seconds.
    - `perUserBayes` : Use per-user Bayes for mail scanning/feedback. Default to false.
  
- Declare the `extensions.properties` for this module.

```
guice.extension.module=org.apache.james.rspamd.module.RspamdModule
guice.extension.task=org.apache.james.rspamd.module.RspamdTaskExtensionModule
```

- Declare the Rspamd mailbox listeners in `listeners.xml`. Eg:

```
<listener>
    <class>org.apache.james.rspamd.RspamdListener</class>
</listener>
```

  This listener can report mails to per-user Bayes by configure `perUserBayes` in `rspamd.properties`.

- Declare the Rspamd mailet for custom mail processing. 

  You can specify the `virusProcessor` if you want to enable virus scanning for mail. Upon configurable `virusProcessor`
you can specify how James process mail virus. We provide a sample Rspamd mailet and `virusProcessor` configuration:

  You can specify the `rejectSpamProcessor`. Emails marked as `rejected` by Rspamd will be redirected to this
processor. This corresponds to emails with the highest spam score, thus delivering them to users as marked as spam 
might not even be desirable.

  The `rewriteSubject` option allows to rewritte subjects when asked by Rspamd.  

  This mailet can scan mails against per-user Bayes by configure `perUserBayes` in `rspamd.properties`. This is achieved 
through the use of Rspamd `Deliver-To` HTTP header. If true, Rspamd will be called for each recipient of the mail, which comes at a performance cost. If true, subjects are not rewritten.
If true `virusProcessor` and `rejectSpamProcessor` are honnered per user, at the cost of email copies. Default to false.

```xml
<processor state="local-delivery" enableJmx="true">
    <mailet match="All" class="org.apache.james.rspamd.RspamdScanner">
        <rewriteSubject>true</rewriteSubject>
        <virusProcessor>virus</virusProcessor>
        <rejectSpamProcessor>spam</rejectSpamProcessor>
        <onMailetException>ignore</onMailetException>
    </mailet>
    <mailet match="IsMarkedAsSpam=org.apache.james.rspamd.status" class="WithStorageDirective">
        <targetFolderName>Spam</targetFolderName>
    </mailet>
    <mailet match="All" class="LocalDelivery"/>
</processor>

<!--Choose one between these two following virus processor, or configure a custom one if you want-->
<!--Hard reject virus mail-->
<processor state="virus" enableJmx="false">
    <mailet match="All" class="ToRepository">
        <repositoryPath>file://var/mail/virus/</repositoryPath>
    </mailet>
</processor>

<!--Soft reject virus mail-->
<processor state="virus" enableJmx="false">
    <mailet match="All" class="StripAttachment">
        <remove>all</remove>
        <pattern>.*</pattern>
    </mailet>
    <mailet match="All" class="AddSubjectPrefix">
        <subjectPrefix>[VIRUS]</subjectPrefix>
    </mailet>
    <mailet match="All" class="LocalDelivery"/>
</processor>

<!--Store rejected spam emails (with a very high score) -->
<processor state="spam" enableJmx="false">
    <mailet match="All" class="ToRepository">
        <repositoryPath>cassandra://var/mail/spam</repositoryPath>
    </mailet>
</processor>
```

- Declare the webadmin for Rspamd in `webadmin.properties`

```
extensions.routes=org.apache.james.rspamd.route.FeedMessageRoute
```
How to use admin endpoint, see more at [Additional webadmin endpoints](README.md)

- Declare the Rspamd healthcheck in `healthcheck.properties`

```
additional.healthchecks=org.apache.james.rspamd.healthcheck.RspamdHealthCheck
```

- Docker compose file example: [docker-compose.yml](docker-compose.yml) or [docker-compose-distributed.yml](docker-compose-distributed.yml).
  
  Please configure `ClamAV` integration into `Rspamd` if you want to enable virus scanning.
- The sample-configuration: [sample-configuration](sample-configuration)
- For running docker-compose, first compile this project 

```
mvn clean install -DskipTests
```
then run it: `docker-compose up`

## Additional webadmin endpoints

### Report spam messages to Rspamd

#### Use a webadmin task

One can use this route to schedule a task that reports spam messages to Rspamd for its spam classify learning.
This task can be configured to report spam messages to per-user Bayes via `perUserBayes` in `rspamd.properties`.

```bash
curl -XPOST 'http://ip:port/rspamd?action=reportSpam
```

This endpoint has the following param:
- `action` (required): need to be `reportSpam`
- `messagesPerSecond` (optional): Concurrent learns performed for Rspamd, default to 10
- `period` (optional): duration (support many time units, default in seconds), only messages between `now` and `now - duration` are reported. By default, 
all messages are reported. 
   These inputs represent the same duration: `1d`, `1day`, `86400 seconds`, `86400`...
- `samplingProbability` (optional): float between 0 and 1, represent the chance to report each given message to Rspamd. 
By default, all messages are reported.
- `classifiedAsSpam` (optional): Boolean, true to only include messages tagged as Spam by Rspamd, false for only
messages tagged as ham by Rspamd. If omitted all messages are included.
- `rspamdTimeout` (optional): duration, Default is 15 seconds. Provide configuration timeout when HTTP request to rspamd for learning. 
Will return the task id. E.g:
```
{
    "taskId": "70c12761-ab86-4321-bb6f-fde99e2f74b0"
}
```

Response codes:
- 201: Task generation succeeded. Corresponding task id is returned.
- 400: Invalid arguments supplied in the user request.

[More details about endpoints returning a task](https://james.apache.org/server/manage-webadmin.html#Endpoints_returning_a_task).

The scheduled task will have the following type `FeedSpamToRspamdTask` and the following additionalInformation:

```json
{
  "errorCount": 1,
  "reportedSpamMessageCount": 2,
  "runningOptions": {
    "messagesPerSecond": 10,
    "rspamdTimeoutInSeconds": 15,
    "periodInSecond": 3600,
    "samplingProbability": 1.0
  },
  "spamMessageCount": 4,
  "timestamp": "2007-12-03T10:15:30Z",
  "type": "FeedSpamToRspamdTask"
}
```

### Report ham messages to Rspamd
One can use this route to schedule a task that reports ham messages to Rspamd for its spam classify learning.
This task can be configured to report ham messages to per-user Bayes via `perUserBayes` in `rspamd.properties`.

```bash
curl -XPOST 'http://ip:port/rspamd?action=reportHam
```

This endpoint has the following param:
- `action` (required): need to be `reportHam`
- `messagesPerSecond` (optional): Concurrent learns performed for Rspamd, default to 10
- `period` (optional): duration (support many time units, default in seconds), only messages between `now` and `now - duration` are reported. By default,
  all messages are reported.
  These inputs represent the same duration: `1d`, `1day`, `86400 seconds`, `86400`...
- `samplingProbability` (optional): float between 0 and 1, represent the chance to report each given message to Rspamd.
  By default, all messages are reported.
- `classifiedAsSpam` (optional): Boolean, true to only include messages tagged as Spam by Rspamd, false for only
messages tagged as ham by Rspamd. If omitted all messages are included.
- `rspamdTimeout` (optional): duration, Default is 15 seconds. Provide configuration timeout when HTTP request to rspamd for learning.
Will return the task id. E.g:
```
{
    "taskId": "70c12761-ab86-4321-bb6f-fde99e2f74b0"
}
```

Response codes:
- 201: Task generation succeeded. Corresponding task id is returned.
- 400: Invalid arguments supplied in the user request.

[More details about endpoints returning a task](https://james.apache.org/server/manage-webadmin.html#Endpoints_returning_a_task).

The scheduled task will have the following type `FeedHamToRspamdTask` and the following additionalInformation:

```json
{
  "errorCount": 1,
  "reportedHamMessageCount": 2,
  "runningOptions": {
    "messagesPerSecond": 10,
    "rspamdTimeoutInSeconds": 15,
    "periodInSecond": 3600,
    "samplingProbability": 1.0
  },
  "hamMessageCount": 4,
  "timestamp": "2007-12-03T10:15:30Z",
  "type": "FeedHamToRspamdTask"
}
```

#### Use live reporting

Alternatively, ham/spam can be reported by using a mailbox listener. To do so enable `RspamdListener` within `listeners.xml`
configuration file:

```xml
<listeners>
    <listener>
        <class>org.apache.james.rspamd.RspamdListener</class>
        <async>true</async>
    </listener>
</listeners>
```

Note that you can turn off `reportAdded` (which reports incoming messages as Ham) resulting in lesser work:


```xml
<listeners>
    <listener>
        <class>org.apache.james.rspamd.RspamdListener</class>
        <async>true</async>
        <configuration>
          <reportAdded>false</reportAdded>
        </configuration>
    </listener>
</listeners>
```