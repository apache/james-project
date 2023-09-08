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
 - [Cassandra table level configuration](#Cassandra_table_level_configuration)
 - [Deleted Messages Vault](#Deleted_Messages_Vault)
 - [OpenSearch Indexing](#Opensearch_Indexing)
 - [Mailbox Event Bus](#Mailbox_Event_Bus)
 - [Mail Processing](#Mail_Processing)
 - [Mail Queue](#Mail_Queue)
 - [Setting Cassandra user permissions](#Setting_Cassandra_user_permissions)
 - [Solving cassandra inconsistencies](#Solving_cassandra_inconsistencies)
 - [Updating Cassandra schema version](#Updating_Cassandra_schema_version)

## Overall architecture

Guice distributed James server intends to provide a horizontally scalable email server.

In order to achieve this goal, this product leverages the following technologies:

 - **Cassandra** for meta-data storage
 - **ObjectStorage** (S3) for binary content storage
 - **OpenSearch** for search
 - **RabbitMQ** for messaging

A [docker-compose](https://github.com/apache/james-project/blob/master/server/apps/distributed-app/docker-compose.yml) file is 
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

Here is a list of [available metric boards](https://github.com/apache/james-project/tree/d2cf7c8e229d9ed30125871b3de5af3cb1553649/server/grafana-reporting)

Monitoring these graphs on a regular basis allows diagnosing early some performance issues. 

If some metrics seem abnormally slow despite in depth database performance tuning, feedback is appreciated as well on 
the bug tracker, the user mailing list or our Gitter channel (see our [community page](http://james.apache.org/#second))
. Any additional details categorizing the slowness are appreciated as well (details of the slow requests for instance).

### WebAdmin HealthChecks

James webadmin API allows to run healthChecks for a quick health overview.

Here is related [webadmin documentation](manage-webadmin.html#HealthCheck)

Here are the available checks alongside the insight they offer:

 - **Cassandra backend**: Cassandra storage. Ensure queries can be executed on the connection James uses.
 - **OpenSearch Backend**: OpenSearch storage. Triggers an OpenSearch health request on indices James uses.
 - **EventDeadLettersHealthCheck**: EventDeadLetters checking.
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

In order to prevent unbounded processing that could consume unbounded resources. We can provide a CRON with `limit` parameter.
Ex: 10 reprocessed per minute
Note that it only support the reprocessing all mails.

Also, one can decide to 
[delete all the mails of a mail repository](manage-webadmin.html#Removing_all_mails_from_a_mail_repository) 
or [delete a single mail of a mail repository](manage-webadmin.html#Removing_a_mail_from_a_mail_repository).

Performance of mail processing can be monitored via the 
[mailet grafana board](https://github.com/apache/james-project/blob/d2cf7c8e229d9ed30125871b3de5af3cb1553649/server/grafana-reporting/es-datasource/MAILET-1490071694187-dashboard.json) 
and [matcher grafana board](https://github.com/apache/james-project/blob/fabfdf4874da3aebb04e6fe4a7277322a395536a/server/mailet/rate-limiter-redis/redis.properties).

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
 - Message indexation with OpenSearch
 - Mailbox annotations cleanup
 - Ham/spam reporting to SpamAssassin
 - ...

It is possible for the administrator of James to define the mailbox listeners he wants to use, by adding them in the
[listeners.xml](https://github.com/apache/james-project/blob/master/server/apps/distributed-app/sample-configuration/listeners.xml) configuration file.
It's possible also to add your own custom mailbox listeners. This enables to enhance capabilities of James as a Mail Delivery Agent.
You can get more information about those [here](config-listeners.html).

Currently, an administrator can monitor listeners failures through `ERROR` log review. 
Metrics regarding mailbox listeners can be monitored via
[mailbox_listeners grafana board](https://github.com/apache/james-project/blob/d2cf7c8e229d9ed30125871b3de5af3cb1553649/server/grafana-reporting/es-datasource/MailboxListeners-1528958667486-dashboard.json) 
and [mailbox_listeners_rate grafana board](https://github.com/apache/james-project/blob/d2cf7c8e229d9ed30125871b3de5af3cb1553649/server/grafana-reporting/es-datasource/MailboxListeners%20rate-1552903378376.json).

Upon exceptions, a bounded number of retries are performed (with exponential backoff delays). 
If after those retries the listener is still failing to perform its operation, then the event will be stored in the 
[Event Dead Letter](manage-webadmin.html#Event_Dead_Letter). 
This API allows diagnosing issues, as well as redelivering the events. 

To check that you have undelivered events in your system, you can first run the associated with [event dead letter health check](manage-webadmin.html#Event_Dead_Letter)
.You can explore Event DeadLetter content through WebAdmin. For this, [list mailbox listener groups](manage-webadmin.html#Listing_mailbox_listener_groups) you will get a list of groups back, allowing you to check if those contain registered events in each by
[listing their failed events](manage-webadmin.html#Listing_failed_events).

If you get failed events IDs back, you can as well [check their details](manage-webadmin.html#Getting_event_details).

An easy way to solve this is just to trigger then the
[redeliver all events](manage-webadmin.html#Redeliver_all_events) task. It will start 
reprocessing all the failed events registered in event dead letters.

In order to prevent unbounded processing that could consume unbounded resources. We can provide a CRON with `limit` parameter.
Ex: 10 redelivery per minute

If for some other reason you don't need to redeliver all events, you have more fine-grained operations allowing you to
[redeliver group events](manage-webadmin.html#Redeliver_group_events) or even just
[redeliver a single event](manage-webadmin.html#Redeliver_a_single_event).

## OpenSearch Indexing

A projection of messages is maintained in OpenSearch via a listener plugged into the mailbox event bus in order to enable search features.

You can find more information about OpenSearch configuration [here](config-opensearch.html).

### Usual troubleshooting procedures

As explained in the [Mailbox Event Bus](#Mailbox_Event_Bus) section, processing those events can fail sometimes.

Currently, an administrator can monitor indexation failures through `ERROR` log review. You can as well
[list failed events](manage-webadmin.html#Listing_failed_events) by looking with the group called 
`org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex$OpenSearchListeningMessageSearchIndexGroup`.
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

### On the fly OpenSearch Index setting update

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
[opensearch.properties](https://github.com/apache/james-project/blob/master/server/apps/distributed-app/sample-configuration/opensearch.properties)
by setting the parameter `opensearch.index.mailbox.name` to the name of your new index. This is to avoid that James 
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
 - [Jmap message fast view projections](#Jmap_message_fast_view_projections)
 - [Mailboxes](#Mailboxes)
 - [Mailboxes Counters](#Mailboxes_counters)
 - [Messages](#Messages)
 - [Quotas](#Quotas)
 - [RRT (RecipientRewriteTable) mapping sources](#Rrt_RecipientRewriteTable_mapping_sources)

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

An admin can run offline webadmin 
[solve Cassandra mailbox object inconsistencies task](manage-webadmin.html#Fixing_mailboxes_inconsistencies) in order 
to sanitize his mailbox denormalization.
                                        
In order to ensure being offline, stop the traffic on SMTP, JMAP and IMAP ports, for example via re-configuration or 
firewall rules.

### Mailboxes Counters

James maintains a per mailbox projection for message count and unseen message count. Failures during the denormalization 
process will lead to incorrect results being returned.

#### How to detect the inconsistencies

Incorrect message count/message unseen count could be seen in the `Mail User Agent` (IMAP or JMAP). Invalid values are reported in the logs 
as warning with the following class `org.apache.james.mailbox.model.MailboxCounters` and the following message prefix: `Invalid mailbox counters`.

#### How to solve

Execute the [recompute Mailbox counters task](manage-webadmin.html#Recomputing mailbox counters). 
This task is not concurrent-safe. Concurrent increments & decrements will be ignored during a single mailbox processing. 
Re-running this task may eventually return the correct result.

### Messages

Messages are denormalized and stored in both `imapUidTable` (source of truth) and `messageIdTable`. Failure in the denormalization 
process will cause inconsistencies between the two tables.

#### How to detect the inconsistencies

User can see a message in JMAP but not in IMAP, or mark a message as 'SEEN' in JMAP but the message flag is still unchanged in IMAP.

#### How to solve

Execute the [solve Cassandra message inconsistencies task](manage-webadmin.html#Fixing_messages_inconsistencies).
This task is not concurrent-safe. User actions concurrent to the inconsistency fixing task could result in new inconsistencies 
being created. However the source of truth `imapUidTable` will not be affected and thus re-running this task may eventually 
fix all issues.

### Quotas

User can monitor the amount of space and message count he is allowed to use, and that he is effectively using. James relies on 
an event bus and Cassandra to track the quota of an user. Upon Cassandra failure, this value can be incorrect.

#### How to detect the inconsistencies

Incorrect quotas could be seen in the `Mail User Agent` (IMAP or JMAP).

#### How to solve

Execute the [recompute Quotas counters task](manage-webadmin.html#Recomputing current quotas for users). 
This task is not concurrent-safe. Concurrent operations will result in an invalid quota to be persisted. Re-running this task may 
eventually return the correct result.

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

Have a look at [cassandra documentation](http://cassandra.apache.org/doc/3.11.11/cql/security.html) section `CREATE ROLE` for more information

E.g.
```
CREATE ROLE james_one WITH PASSWORD = 'james_one' AND LOGIN = true;
```
#### Create a keyspace

Have a look at [cassandra documentation](http://cassandra.apache.org/doc/3.11.11/cql/ddl.html) section `CREATE KEYSPACE` for more information

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

For more information, have a look at [cassandra documentation](http://cassandra.apache.org/doc/3.11.11/cql/security.html) section `REVOKE PERMISSION`.

Except for the case above, the permissions are not auto available for 
a specific role unless they are granted by `GRANT` command. Therefore, 
if you didn't provide more permissions than [granting section](#Grant_permissions_on_created_keyspace_to_the_role), there's no need to revoke.

## Cassandra table level configuration

While *Distributed James* is shipped with default table configuration options, these settings should be refined 
depending of your usage.

These options are:
 - The [compaction algorithms](https://cassandra.apache.org/doc/latest/operating/compaction.html)
 - The [bloom filter sizing](https://cassandra.apache.org/doc/latest/operating/bloom_filters.html)
 - The [chunk size](https://cassandra.apache.org/doc/latest/operating/compression.html?highlight=chunk%20size)
 - The [caching options](https://www.datastax.com/blog/2011/04/maximizing-cache-benefit-cassandra)
 
The compaction algorithms allow a tradeoff between background IO upon writes and reads. We recommend:
 - Using **Leveled Compaction Strategy** on read intensive tables subject to updates. This limits the count of SStables
 being read at the cost of more background IO. High garbage collections can be caused by an inappropriate use of Leveled 
 Compaction Strategy.
 - Otherwise use the default **Size Tiered Compaction Strategy**.
 
Bloom filters help avoiding unnecessary reads on SSTables. This probabilistic data structure can tell an entry absence 
from a SSTable, as well as the presence of an entry with an associated probability. If a lot of false positives are 
noticed, the size of the bloom filters can be increased.
 
As explained in [this post](https://thelastpickle.com/blog/2018/08/08/compression_performance.html), chunk size used 
upon compression allows a tradeoff between reads and writes. A smaller size will mean decreasing compression, thus it
increases data being stored on disk, but allow lower chunks to be read to access data, and will favor reads. A bigger 
size will mean better compression, thus writing less, but it might imply reading bigger chunks.

Cassandra enables a key cache and a row cache. Key cache enables to skip reading the partition index upon reads,
thus performing 1 read to the disk instead of 2. Enabling this cache is globally advised. Row cache stores the entire 
row in memory. It can be seen as an optimization, but it might actually use memory no longer available for instance for 
file system cache. We recommend turning it off on modern SSD hardware.

A review of your usage can be conducted using 
[nodetool](https://cassandra.apache.org/doc/latest/tools/nodetool/nodetool.html) utility. For example 
`nodetool tablestats {keyspace}` allows reviewing the number of SSTables, the read/write ratios, bloom filter efficiency. 
`nodetool tablehistograms {keyspace}.{table}` might give insight about read/write performance.

Table level options can be changed using **ALTER TABLE** for example with the 
[cqlsh](https://cassandra.apache.org/doc/latest/tools/cqlsh.html) utility. A full compaction might be 
needed in order for the changes to be taken into account.

## Mail Queue

An email queue is a mandatory component of SMTP servers. It is a system that creates a queue of emails that are waiting to be processed for delivery. Email queuing is a form of Message Queuing – an asynchronous service-to-service communication. A message queue is meant to decouple a producing process from a consuming one. An email queue decouples email reception from email processing. It allows them to communicate without being connected. As such, the queued emails wait for processing until the recipient is available to receive them. As James is an Email Server, it also supports mail queue as well.

### Why Mail Queue is necessary

You might often need to check mail queue to make sure all emails are delivered properly. At first, you need to know why email queues get clogged. Here are the two core reasons for that:

- Exceeded volume of emails

Some mailbox providers enforce email rate limits on IP addresses. The limits are based on the sender reputation. If you exceeded this rate and queued too many emails, the delivery speed will decrease.

- Spam-related issues

Another common reason is that your email has been busted by spam filters. The filters will let the emails gradually pass to analyze how the rest of the recipients react to the message. If there is slow progress, it’s okay. Your email campaign is being observed and assessed. If it’s stuck, there could be different reasons including the blockage of your IP address. 

### Why combining Cassandra, RabbitMQ and Object storage for MailQueue

 - RabbitMQ ensures the messaging function, and avoids polling.
 - Cassandra enables administrative operations such as browsing, deleting using a time series which might require fine performance tuning (see [Operating Casandra documentation](http://cassandra.apache.org/doc/latest/operating/index.html)).
 - Object Storage stores potentially large binary payload.

However the current design do not implement delays. Delays allow to define the time a mail have to be living in the 
mailqueue before being dequeued and is used for example for exponential wait delays upon remote delivery retries, or
SMTP traffic rate limiting.

### Fine tune configuration for RabbitMQ

In order to adapt mail queue settings to the actual traffic load, an administrator needs to perform fine configuration tunning as explained in [rabbitmq.properties](https://github.com/apache/james-project/blob/master/src/site/xdoc/server/config-rabbitmq.xml).

Be aware that `MailQueue::getSize` is currently performing a browse and thus is expensive. Size recurring metric 
reporting thus introduces performance issues. As such, we advise setting `mailqueue.size.metricsEnabled=false`.

### Managing email queues

Managing an email queue is an easy task if you follow this procedure:

- First, [List mail queues](manage-webadmin.html#Listing_mail_queues) and [get a mail queue details](manage-webadmin.html#Getting_a_mail_queue_details).
- And then [List the mails of a mail queue](manage-webadmin.html#Listing_the_mails_of_a_mail_queue).
- If all mails in the mail queue are needed to be delivered you will [flush mails from a mail queue](manage-webadmin.html#Flushing_mails_from_a_mail_queue).

In case, you need to clear an email queue because there are only spam or trash emails in the email queue you have this procedure to follow:

- All mails from the given mail queue will be deleted with [Clearing a mail queue](manage-webadmin.html#Clearing_a_mail_queue).

## Updating Cassandra schema version

A schema version indicates you which schema your James server is relying on. The schema version number tracks if a migration is required. For instance, when the latest schema version is 2, and the current schema version is 1, you might think that you still have data in the deprecated Message table in the database. Hence, you need to migrate these messages into the MessageV2 table. Once done, you can safely bump the current schema version to 2.

Relying on outdated schema version prevents you to benefit from the newest performance and safety improvements. Otherwise, there's something very unexpected in the way we manage cassandra schema: we create new tables without asking the admin about it. That means your James version is always using the last tables but may also take into account the old ones if the migration is not done yet.

### How to detect when we should update Cassandra schema version

When you see in James logs `org.apache.james.modules.mailbox.CassandraSchemaVersionStartUpCheck` showing a warning like `Recommended version is versionX`, you should perform an update of the Cassandra schema version.

Also, we keep track of changes needed when upgrading to a newer version. You can read this [upgrade instructions](https://github.com/apache/james-project/blob/master/upgrade-instructions.md).

### How to update Cassandra schema version

These schema updates can be triggered by webadmin using the Cassandra backend. Following steps are for updating Cassandra schema version:

- At the very first step, you need to [retrieve current Cassandra schema version](manage-webadmin.html#Retrieving_current_Cassandra_schema_version)
- And then, you [retrieve latest available Cassandra schema version](manage-webadmin.html#Retrieving_latest_available_Cassandra_schema_version) to make sure there is a latest available version
- Eventually, you can update the current schema version to the one you got with [upgrading to the latest version](manage-webadmin.html#Upgrading_to_the_latest_version)

Otherwise, if you need to run the migrations to a specific version, you can use [Upgrading to a specific version](manage-webadmin.html#Upgrading_to_a_specific_version)

## Deleted Messages Vault

Deleted Messages Vault is an interesting feature that will help James users have a chance to:

- retain users deleted messages for some time.
- restore & export deleted messages by various criteria.
- permanently delete some retained messages.

If the Deleted Messages Vault is enabled when users delete their mails, and by that we mean when they try to definitely delete them by emptying the trash, James will retain these mails into the Deleted Messages Vault, before an email or a mailbox is going to be deleted. And only administrators can interact with this component via [WebAdmin REST APIs](manage-webadmin.html#deleted-messages-vault).

However, mails are not retained forever as you have to configure a retention period before using it (with one-year retention by default if not defined). It's also possible to permanently delete a mail if needed and we recommend the administrator to [run it](#Cleaning_expired_deleted_messages) in cron job to save storage volume.

### How to configure deleted messages vault

To setup James with Deleted Messages Vault, you need to follow those steps:

- Enable Deleted Messages Vault by configuring Pre Deletion Hooks.
- Configuring the retention time for the Deleted Messages Vault.

#### Enable Deleted Messages Vault by configuring Pre Deletion Hooks

You need to configure this hook in [listeners.xml](https://github.com/apache/james-project/blob/master/server/apps/distributed-app/sample-configuration/listeners.xml) configuration file. More details about configuration & example can be found at [Pre Deletion Hook Configuration](http://james.apache.org/server/config-listeners.html)

#### Configuring the retention time for the Deleted Messages Vault

In order to configure the retention time for the Deleted Messages Vault, an administrator needs to perform fine configuration tunning as explained in [deletedMessageVault.properties](https://github.com/apache/james-project/blob/master/server/apps/distributed-app/sample-configuration/deletedMessageVault.properties). Mails are not retained forever as you have to configure a retention period (by `retentionPeriod`) before using it (with one-year retention by default if not defined).

### Restore deleted messages after deletion

After users deleted their mails and emptied the trash, the admin can use [Restore Deleted Messages](manage-webadmin.html#deleted-messages-vault) to restore all the deleted mails.  

### Cleaning expired deleted messages

You can delete all deleted messages older than the configured `retentionPeriod` by using [Purge Deleted Messages](manage-webadmin.html#deleted-messages-vault). We recommend calling this API in CRON job on 1st day each month.
