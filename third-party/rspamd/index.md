# James' extensions for RSpamD

This module is for developing and delivering extensions to James for the [RSpamD](https://rspamd.com/) (the spam filtering system) 

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

- Declare the RSpamD mailet for custom mail processing. Mailet pipeline Eg:

```
<mailet match="All" class="org.apache.james.rspamd.RSpamDScanner"></mailet>
<mailet match="IsMarkedAsSpam=org.apache.james.rspamd.status" class="WithStorageDirective">
    <targetFolderName>Spam</targetFolderName>
</mailet>
```

- Declare the webadmin for RSpamD in `webadmin.properties`

```
extensions.routes=org.apache.james.rspamd.route.FeedMessageRoute
```
How to use admin endpoint, see more at [Additional webadmin endpoints](README.md)

- Docker compose file example: [docker-compose.yml](docker-compose.yml) or [docker-compose-distributed.yml](docker-compose-distributed.yml)
- The sample-configuration: [sample-configuration](sample-configuration)
- For running docker-compose, first compile this project 

```
mvn clean install -DskipTests
```
then run it: `docker-compose up`