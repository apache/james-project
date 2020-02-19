# Managing Guice distributed James

This guide aims to be an entry-point to the James documentation for user managing a distributed Guice James server.

It includes:

 - Simple architecture explanations
 - Propose some diagnostics for some common issues
 - Present procedures that can be set up to address these issues

In order to not duplicate information, existing documentation will be linked.

Please note that this product is under active development, should be considered experimental and thus targets 
advanced users.

## Table of content

 - [Overall architecture](#Overall_architecture)
 - [Basic Monitoring](#Basic_Monitoring)
 - [Mailbox Event Bus](#Mailbox_Event_Bus)
 - [Mail Processing](#Mail_Processing)
 - [ElasticSearch Indexing](#Elasticsearch_Indexing)
 - [Solving cassandra inconsistencies](#Solving_cassandra_inconsistencies) 
 - [Setting Cassandra user permissions](#Setting_Cassandra_user_permissions)

## Overall architecture

Guice distributed James server intends to provide a horizontally scalable email server.

In order to achieve this goal, this product leverages the following technologies:

 - **Cassandra** for meta-data storage
 - **ObjectStorage** (S3) for binary content storage
 - **ElasticSearch** for search
 - **RabbitMQ** for messaging

A [docker-compose](https://github.com/apache/james-project/blob/master/dockerfiles/run/docker-compose.yml) file is 
available to allow you to quickly deploy locally this product.

## Basic Monitoring

A toolbox is available to help an administrator diagnose issues:
 - [Structured logging into Kibana](#Structured_logging_into_Kibana)
 - [Metrics graphs into Grafana](#Metrics_graphs_into_Grafana)
 - [WebAdmin HealthChecks](#Webadmin_Healthchecks)

### Structured logging into Kibana

Read this page regarding [setting up structured logging](monitor-logging.html#Guice_products_and_logging).

We recommend to closely monitoring **ERROR** and **WARNING** logs. Those logs should be considered not normal.

If you encounter some suspicious logs:
 - If you have any doubt about the log being caused by a bug in James source code, please reach us via  
the bug tracker, the user mailing list or our Gitter channel (see our [community page](http://james.apache.org/#second))
 - They can be due to insufficient performance from tier applications (eg Cassandra timeouts). In such case we advise
 you to conduct a close review of performances at the tier level.

Leveraging filters in Kibana discover view can help filtering out "already known" frequently occurring logs.

When reporting ERROR or WARNING logs, consider adding the full logs, and related data (eg the raw content of a mail 
triggering an issue) to the bug report in order to ease resolution.

### Metrics graphs into Grafana

James keeps tracks of various metrics and allow to easily visualize them.

Read this page for [explanations on metrics](metrics.html).

Here is a list of [available metric boards](https://github.com/apache/james-project/tree/master/grafana-reporting)

Configuration of [ElasticSearch metric exporting](config-elasticsearch.html) allows a direct display within 
[Grafana](https://grafana.com/)

Monitoring these graphs on a regular basis allows diagnosing early some performance issues. 

If some metrics seem abnormally slow despite in depth database performance tuning, feedback is appreciated as well on 
the bug tracker, the user mailing list or our Gitter channel (see our [community page](http://james.apache.org/#second))
. Any additional details categorizing the slowness are appreciated as well (details of the slow requests for instance).

### WebAdmin HealthChecks

James webadmin API allows to run healthChecks for a quick health overview.

Here is related [webadmin documentation](manage-webadmin.html#HealthCheck)

Here are the available checks alongside the insight they offer:

 - **Cassandra backend**: Cassandra storage. Ensure queries can be executed on the connection James uses.
 - **ElasticSearch Backend**: ElasticSearch storage. Triggers an ElasticSearch health request on indices James uses.
 - **RabbitMQ backend**: RabbitMQ messaging. Verifies an open connection and an open channel are well available.
 - **Guice application lifecycle**: Ensures James Guice successfully started, and is up. Logs should contain 
 explanations if James did not start well.
 - **MessageFastViewProjection**: Follows MessageFastViewProjection cache miss rates and warns if it is below 10%. If 
 this projection is missing, this results in performance issues for JMAP GetMessages list requests. WebAdmin offers a
 [global](manage-webadmin.html#Recomputing_Global_JMAP_fast_message_view_projection) and 
 [per user](manage-webadmin.html#Recomputing_Global_JMAP_fast_message_view_projection) projection re-computation. Note that
 as computation is asynchronous, this projection can be slightly out of sync on a normally behaving server.

## Mail Processing

Mail processing allows to take asynchronously business decisions on received emails.

Here are its components:

 - The `spooler` takes mail out of the mailQueue and executes mail processing within the `mailet container`.
 - The `mailet container` synchronously executes the user defined logic. This 'logic' is written through the use of
  `mailet`, `matcher` and `processor`.
 - A `mailet` represents an action: mail modification, envelop modification, a side effect, or stop processing.
 - A `matcher` represents a condition to execute a mailet.
 - A `processor` is a flow of pair of `matcher` and `mailet` executed sequentially. The `ToProcessor` mailet is a `goto` 
 instruction to start executing another `processor`
 - A `mail repository` allows storage of a mail as part of its processing. Standard configuration relies on the 
 following mail repository:
     - `cassandra://var/mail/error/` : unexpected errors that occurred during mail processing. Emails impacted by 
     performance related exceptions, or logical bug within James code are typically stored here. These mails could be 
     reprocessed once the cause of the error is fixed. The `Mail.error` field can help diagnose the issue. Correlation 
     with logs can be achieved via the use of the `Mail.name` field.
     - `cassandra://var/mail/address-error/` : mail addressed to a non-existing recipient of a handled local domain. 
     These mails could be reprocessed once the user is created, for instance.
     - `cassandra://var/mail/relay-denied/` : mail for whom relay was denied: missing authentication can, for instance, 
     be a cause. In addition to prevent disasters upon miss configuration, an email review of this mail repository can 
     help refine a host spammer blacklist.
     - `cassandra://var/mail/rrt-error/` : runtime error upon Recipient Rewritting occurred. This is typically due to a 
     loop. We recommend verifying user mappings via [User Mappings webadmin API](manage-webadmin.html#User_Mappings) 
     then once identified break the loop by removing some Recipient Rewrite Table entry via the 
     [Delete Alias](manage-webadmin.html#Removing_an_alias_of_an_user), 
     [Delete Group member](manage-webadmin.html#Removing_a_group_member), 
     [Delete forward](manage-webadmin.html#Removing_a_destination_of_a_forward), 
     [Delete Address mapping](manage-webadmin.html#Remove_an_address_mapping), 
     [Delete Domain mapping](manage-webadmin.html#Removing_a_domain_mapping) or 
     [Delete Regex mapping](manage-webadmin.html#Removing_a_regex_mapping) APIs (as needed). The `Mail.error` field can 
     help diagnose the issue as well. Then once the root cause has been addressed, the mail can be reprocessed.

Read [this](config-mailetcontainer.html) to discover mail processing configuration, including error management.

Currently, an administrator can monitor mail processing failure through `ERROR` log review. We also recommend watching 
in Kibana INFO logs using the `org.apache.james.transport.mailets.ToProcessor` value as their `logger`. Metrics about 
mail repository size, and the corresponding Grafana boards are yet to be contributed.

WebAdmin exposes all utilities for 
[reprocessing all mails in a mail repository](manage-webadmin.html#Reprocessing_mails_from_a_mail_repository)
or 
[reprocessing a single mail in a mail repository](manage-webadmin.html#Reprocessing_a_specific_mail_from_a_mail_repository).

Also, one can decide to 
[delete all the mails of a mail repository](manage-webadmin.html#Removing_all_mails_from_a_mail_repository) 
or [delete a single mail of a mail repository](manage-webadmin.html#Removing_a_mail_from_a_mail_repository).

Performance of mail processing can be monitored via the 
[mailet grafana board](https://github.com/apache/james-project/blob/master/grafana-reporting/MAILET-1490071694187-dashboard.json) 
and [matcher grafana board](https://github.com/apache/james-project/blob/master/grafana-reporting/MATCHER-1490071813409-dashboard.json).

## Mailbox Event Bus

James relies on an event bus system to enrich mailbox capabilities. Each operation performed on the mailbox will trigger 
related events, that can be processed asynchronously by potentially any James node on a distributed system.

Many different kind of events can be triggered during a mailbox operation, such as:

 - `MailboxEvent`: event related to an operation regarding a mailbox:
   - `MailboxDeletion`: a mailbox has been deleted
   - `MailboxAdded`: a mailbox has been added
   - `MailboxRenamed`: a mailbox has been renamed
   - `MailboxACLUpdated`: a mailbox got its rights and permissions updated
 - `MessageEvent`: event related to an operation regarding a message:
   - `Added`: messages have been added to a mailbox
   - `Expunged`: messages have been expunged from a mailbox 
   - `FlagsUpdated`: messages had their flags updated
   - `MessageMoveEvent`: messages have been moved from a mailbox to an other
 - `QuotaUsageUpdatedEvent`: event related to quota update

Mailbox listeners can register themselves on this event bus system to be called when an event is fired,
allowing to do different kind of extra operations on the system, like:

 - Current quota calculation
 - Message indexation with ElasticSearch
 - Mailbox annotations cleanup
 - Ham/spam reporting to SpamAssassin
 - ...

It is possible for the administrator of James to define the mailbox listeners he wants to use, by adding them in the
[listeners.xml](https://github.com/apache/james-project/blob/master/dockerfiles/run/guice/cassandra-rabbitmq/destination/conf/listeners.xml) configuration file.
It's possible also to add your own custom mailbox listeners. This enables to enhance capabilities of James as a Mail Delivery Agent.
You can get more information about those [here](config-listeners.html).

Currently, an administrator can monitor listeners failures through `ERROR` log review. 
Metrics regarding mailbox listeners can be monitored via
[mailbox_listeners grafana board](https://github.com/apache/james-project/blob/master/grafana-reporting/MailboxListeners-1528958667486-dashboard.json) 
and [mailbox_listeners_rate grafana board](https://github.com/apache/james-project/blob/master/grafana-reporting/MailboxListeners%20rate-1552903378376.json).

Upon exceptions, a bounded number of retries are performed (with exponential backoff delays). 
If after those retries the listener is still failing to perform its operation, then the event will be stored in the 
[Event Dead Letter](manage-webadmin.html#Event_Dead_Letter). 
This API allows diagnosing issues, as well as redelivering the events. 

To check that you have undelivered events in your system, you can first
[list mailbox listener groups](manage-webadmin.html#Listing_mailbox_listener_groups).
You will get a list of groups back, allowing you to check if those contain registered events in each by
[listing their failed events](manage-webadmin.html#Listing_failed_events).

If you get failed events IDs back, you can as well [check their details](manage-webadmin.html#Getting_event_details).

An easy way to solve this is just to trigger then the
[redeliver all events](manage-webadmin.html#Redeliver_all_events) task. It will start 
reprocessing all the failed events registered in event dead letters.

If for some other reason you don't need to redeliver all events, you have more fine-grained operations allowing you to
[redeliver group events](manage-webadmin.html#Redeliver_group_events) or even just
[redeliver a single event](manage-webadmin.html#Redeliver_a_single_event).

## ElasticSearch Indexing

A projection of messages is maintained in ElasticSearch via a listener plugged into the mailbox event bus in order to enable search features.

You can find more information about ElasticSearch configuration [here](config-elasticsearch.html).

### Usual troubleshooting procedures

As explained in the [Mailbox Event Bus](#Mailbox_Event_Bus) section, processing those events can fail sometimes.

Currently, an administrator can monitor indexation failures through `ERROR` log review. You can as well
[list failed events](manage-webadmin.html#Listing_failed_events) by looking with the group called 
`org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex$ElasticSearchListeningMessageSearchIndexGroup`.
A first on-the-fly solution could be to just 
[redeliver those group events with event dead letter](#Mailbox_Event_Bus).

If the event storage in dead-letters fails (for instance in the face of Cassandra storage exceptions), 
then you might need to use our WebAdmin reIndexing tasks.

From there, you have multiple choices. You can
[reIndex all mails](manage-webadmin.html#ReIndexing_all_mails),
[reIndex mails from a mailbox](manage-webadmin.html#ReIndexing_a_mailbox_mails)
or even just [reIndex a single mail](manage-webadmin.html#ReIndexing_a_single_mail).

When checking the result of a reIndexing task, you might have failed reprocessed mails. You can still use the task ID to
[reprocess previously failed reIndexing mails](manage-webadmin.html#Fixing_previously_failed_ReIndexing).

### On the fly ElasticSearch Index setting update

Sometimes you might need to update index settings. Cases when an administrator might want to update index settings include:

 - Scaling out: increasing the shard count might be needed.
 - Changing string analysers, for instance to target another language
 - etc.

In order to achieve such a procedure, you need to:

 - [Create the new index](https://www.elastic.co/guide/en/elasticsearch/reference/6.3/indices-create-index.html) with the right
settings and mapping
 - James uses two aliases on the mailbox index: one for reading (`mailboxReadAlias`) and one for writing (`mailboxWriteAlias`).
First [add an alias](https://www.elastic.co/guide/en/elasticsearch/reference/6.3/indices-aliases.html) `mailboxWriteAlias` to that new index,
so that now James writes on the old and new indexes, while only keeping reading on the first one
 - Now trigger a [reindex](https://www.elastic.co/guide/en/elasticsearch/reference/6.3/docs-reindex.html)
from the old index to the new one (this actively relies on `_source` field being present)
 - When this is done, add the `mailboxReadAlias` alias to the new index
 - Now that the migration to the new index is done, you can 
[drop the old index](https://www.elastic.co/guide/en/elasticsearch/reference/6.3/indices-delete-index.html)
 - You might want as well modify the James configuration file 
[elasticsearch.properties](https://github.com/apache/james-project/blob/master/dockerfiles/run/guice/cassandra-rabbitmq/destination/conf/elasticsearch.properties)
by setting the parameter `elasticsearch.index.mailbox.name` to the name of your new index. This is to avoid that James 
re-creates index upon restart

_Note_: keep in mind that reindexing can be a very long operation depending on the volume of mails you have stored.

## Solving cassandra inconsistencies

Cassandra backend uses data duplication to workaround Cassandra query limitations. 
However, Cassandra is not doing transaction when writing in several tables, 
this can lead to consistency issues for a given piece of data. 
The consequence could be that the data is in a transient state (that should never appear outside of the system).

Because of the lack of transactions, it's hard to prevent these kind of issues. We had developed some features to 
fix some existing cassandra inconsistency issues that had been reported to James. 

Here is the list of known inconsistencies:
 - [RRT (RecipientRewriteTable) mapping sources](#Rrt_RecipientRewriteTable_mapping_sources)
 - [Jmap message fast view projections](#Jmap_message_fast_view_projections)
 - [Mailboxes](#Mailboxes)

### RRT (RecipientRewriteTable) mapping sources

`rrt` and `mappings_sources` tables store information about address mappings. 
The source of truth is `rrt` and `mappings_sources` is the projection table containing all 
mapping sources.

#### How to detect the inconsistencies

Right now there's no tool for detecting that, we're proposing a [development plan](https://issues.apache.org/jira/browse/JAMES-3069). 
By the mean time, the recommendation is to execute the `SolveInconsistencies` task below 
in a regular basis. 

#### How to solve

Execute the Cassandra mapping `SolveInconsistencies` task described in [webadmin documentation](manage-webadmin.html#Operations_on_mappings_sources) 

### Jmap message fast view projections

When you read a Jmap message, some calculated properties are expected to be fast to retrieve, like `preview`, `hasAttachment`. 
James achieves it by pre-calculating and storing them into a caching table (`message_fast_view_projection`). 
Missing caches are populated on message reads and will temporary decrease the performance.

#### How to detect the outdated projections

You can watch the `MessageFastViewProjection` health check at [webadmin documentation](manage-webadmin.html#Check_all_components). 
It provides a check based on the ratio of missed projection reads.  

#### How to solve
 
Since the MessageFastViewProjection is self healing, you should be concerned only if 
the health check still returns `degraded` for a while, there's a possible thing you 
can do is looking at James logs for more clues. 

### Mailboxes

`mailboxPath` and `mailbox` tables share common fields like `mailboxId` and mailbox `name`. 
A successful operation of creating/renaming/delete mailboxes has to succeed at updating `mailboxPath` and `mailbox` table. 
Any failure on creating/updating/delete records in `mailboxPath` or `mailbox` can produce inconsistencies.

#### How to detect the inconsistencies

If you found the suspicious `MailboxNotFoundException` in your logs. 
Currently, there's no dedicated tool for that, we recommend scheduling 
the SolveInconsistencies task below for the mailbox object on a regular basis, 
avoiding peak traffic in order to address both inconsistencies diagnostic and fixes.

#### How to solve

Under development: Task for solving mailbox inconsistencies ([JAMES-3058](https://issues.apache.org/jira/browse/JAMES-3058)).

## Setting Cassandra user permissions

When a Cassandra cluster is serving more than a James cluster, the keyspaces need isolation. 
It can be achieved by configuring James server with credentials preventing access or modification of other keyspaces.

We recommend you to not use the initial admin user of Cassandra and provide 
a different one with a subset of permissions for each application. 

### Prerequisites

We're gonna use the Cassandra super users to create roles and grant permissions for them. 
To do that, Cassandra requires you to login via username/password authentication 
and enable granting in cassandra configuration file.

For example:
```
echo -e "\nauthenticator: PasswordAuthenticator" >> /etc/cassandra/cassandra.yaml
echo -e "\nauthorizer: org.apache.cassandra.auth.CassandraAuthorizer" >> /etc/cassandra/cassandra.yaml
```
### Prepare Cassandra roles & keyspaces for James  

#### Create a role

Have a look at [cassandra documentation](http://cassandra.apache.org/doc/3.11.3/cql/security.html) section `CREATE ROLE` for more information

E.g.
```
CREATE ROLE james_one WITH PASSWORD = 'james_one' AND LOGIN = true;
```
#### Create a keyspace

Have a look at [cassandra documentation](http://cassandra.apache.org/doc/3.11.3/cql/ddl.html) section `CREATE KEYSPACE` for more information

#### Grant permissions on created keyspace to the role

The role to be used by James needs to have full rights on the keyspace 
that James is using. Assuming the keyspace name is `james_one_keyspace` 
and the role be `james_one`.
```
GRANT CREATE ON KEYSPACE james_one_keyspace TO james_one; // Permission to create tables on the appointed keyspace
GRANT SELECT ON	KEYSPACE james_one_keyspace TO james_one; // Permission to select from tables on the appointed keyspace
GRANT MODIFY ON	KEYSPACE james_one_keyspace TO james_one; // Permission to update data in tables on the appointed keyspace
```
**Warning**: The granted role doesn't have the right to create keyspaces, 
thus, if you haven't created the keyspace, James server will fail to start 
is expected.  

**Tips**

Since all of Cassandra roles used by different James are supposed to 
have a same set of permissions, you can reduce the works by creating a 
base role set like `typical_james_role` with all of necessary permissions. 
After that, with each James, create a new role and grant the `typical_james_role` 
to the newly created one. Note that, once a base role set is updated ( 
granting or revoking rights) all granted roles are automatically updated.  

E.g.
```
CREATE ROLE james1 WITH PASSWORD = 'james1' AND LOGIN = true;
GRANT typical_james_role TO james1;

CREATE ROLE james2 WITH PASSWORD = 'james2' AND LOGIN = true;
GRANT typical_james_role TO james2;
```
#### Revoke harmful permissions from the created role

We want a specific role that cannot describe or query the information of other 
keyspaces or tables used by another application. 
By default, Cassandra allows every role created to have the right to 
describe any keyspace and table. There's no configuration that can make 
effect on that topic. Consequently, you have to accept that your data models 
are still being exposed to anyone having credentials to Cassandra. 

For more information, have a look at [cassandra documentation](http://cassandra.apache.org/doc/3.11.3/cql/security.html) section `REVOKE PERMISSION`. 

Except for the case above, the permissions are not auto available for 
a specific role unless they are granted by `GRANT` command. Therefore, 
if you didn't provide more permissions than [granting section](#Grant_permissions_on_created_keyspace_to_the_role), there's no need to revoke.