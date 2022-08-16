# James' extensions for RSpamD

This module is for developing and delivering extensions to James for the [RSpamD](https://rspamd.com/) (the spam filtering system)
and [ClamAV](https://www.clamav.net/) (the antivirus engine).

## How to run

- The RSpamD extension requires an extra configuration file `rspamd.properties` to configure RSpamd connection
Configuration parameters:
    - `rSpamDUrl` : URL defining the RSpamD's server. Eg: http://rspamd:11334
    - `rSpamDPassword` : Password for pass authentication when request to RSpamD's server. Eg: admin
  
- Declare the `extensions.properties` for this module.

```
guice.extension.module=org.apache.james.rspamd.module.RSpamDModule
guice.extension.task=org.apache.james.rspamd.module.RSpamDTaskExtensionModule
```

- Declare the RSpamD mailbox listeners in `listeners.xml`. Eg:

```
<listener>
    <class>org.apache.james.rspamd.RSpamDListener</class>
</listener>
```

- Declare the RSpamD mailet for custom mail processing. 

  You can specify the `virusProcessor` if you want to enable virus scanning for mail. Upon configurable `virusProcessor`
you can specify how James process mail virus. We provide a sample Rspamd mailet and `virusProcessor` configuration:

```xml
<processor state="local-delivery" enableJmx="true">
    <mailet match="All" class="org.apache.james.rspamd.RSpamDScanner">
        <rewriteSubject>true</rewriteSubject>
        <virusProcessor>virus</virusProcessor>
    </mailet>
    <mailet match="IsMarkedAsSpam=org.apache.james.rspamd.status" class="WithStorageDirective">
        <targetFolderName>Spam</targetFolderName>
    </mailet>
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
</processor>
```

- Declare the webadmin for RSpamD in `webadmin.properties`

```
extensions.routes=org.apache.james.rspamd.route.FeedMessageRoute
```
How to use admin endpoint, see more at [Additional webadmin endpoints](README.md)

- Docker compose file example: [docker-compose.yml](docker-compose.yml) or [docker-compose-distributed.yml](docker-compose-distributed.yml).
  
  Please configure `ClamAV` integration into `Rspamd` if you want to enable virus scanning.
- The sample-configuration: [sample-configuration](sample-configuration)
- For running docker-compose, first compile this project 

```
mvn clean install -DskipTests
```
then run it: `docker-compose up`

## Additional webadmin endpoints

### Report spam messages to RSpamD
One can use this route to schedule a task that reports spam messages to RSpamD for its spam classify learning.

```bash
curl -XPOST 'http://ip:port/rspamd?action=reportSpam
```

This endpoint has the following param:
- `action` (required): need to be `reportSpam`
- `messagesPerSecond` (optional): Concurrent learns performed for RSpamD, default to 10
- `period` (optional): duration (support many time units, default in seconds), only messages between `now` and `now - duration` are reported. By default, 
all messages are reported. 
   These inputs represent the same duration: `1d`, `1day`, `86400 seconds`, `86400`...
- `samplingProbability` (optional): float between 0 and 1, represent the chance to report each given message to RSpamD. 
By default, all messages are reported.

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

The scheduled task will have the following type `FeedSpamToRSpamDTask` and the following additionalInformation:

```json
{
  "errorCount": 1,
  "reportedSpamMessageCount": 2,
  "runningOptions": {
    "messagesPerSecond": 10,
    "periodInSecond": 3600,
    "samplingProbability": 1.0
  },
  "spamMessageCount": 4,
  "timestamp": "2007-12-03T10:15:30Z",
  "type": "FeedSpamToRSpamDTask"
}
```