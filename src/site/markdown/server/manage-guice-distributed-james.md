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

 - [Overall architecture](#overall-architecture)
 - [Basic Monitoring](#basic-monitoring)
 - [Mailbox Event Bus](#mailbox-event-bus)
 - [Mail Processing](#mail-processing)
 - [ElasticSearch Indexing](#elasticsearch-indexing)

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
 - [Structured logging into Kibana](#structured-logging-into-kibana)
 - [Metrics graphs into Grafana](#metrics-graphs-into-grafana)
 - [WebAdmin HealthChecks](#webadmin-healthchecks)

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
 [global](manage-webadmin.html#recomputing-global-jmap-fast-message-view-projection) and 
 [per user](manage-webadmin.html#recomputing-user-jmap-fast-message-view-projection) projection re-computation. Note that
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
     loop. We recommend verifying user mappings via [User Mappings webadmin API](manage-webadmin.html#user-mappings) 
     then once identified break the loop by removing some Recipient Rewrite Table entry via the 
     [Delete Alias](manage-webadmin.html#removing-an-alias-of-an-user), 
     [Delete Group member](manage-webadmin.html#removing-a-group-member), 
     [Delete forward](manage-webadmin.html#removing-a-destination-of-a-forward), 
     [Delete Address mapping](manage-webadmin.html#remove-an-address-mapping), 
     [Delete Domain mapping](manage-webadmin.html#removing-a-domain-mapping) or 
     [Delete Regex mapping](manage-webadmin.html#removing-a-regex-mapping) APIs (as needed). The `Mail.error` field can 
     help diagnose the issue as well. Then once the root cause has been addressed, the mail can be reprocessed.

Read [this](config-mailetcontainer.html) to discover mail processing configuration, including error management.

Currently, an administrator can monitor mail processing failure through `ERROR` log review. We also recommend watching 
in Kibana INFO logs using the `org.apache.james.transport.mailets.ToProcessor` value as their `logger`. Metrics about 
mail repository size, and the corresponding Grafana boards are yet to be contributed.

WebAdmin exposes all utilities for 
[reprocessing all mails in a mail repository](manage-webadmin.html#reprocessing-mails-from-a-mail-repository)
or 
[reprocessing a single mail in a mail repository](manage-webadmin.html#reprocessing-a-specific-mail-from-a-mail-repository).

Also, one can decide to 
[delete all the mails of a mail repository](manage-webadmin.html#removing-all-mails-from-a-mail-repository) 
or [delete a single mail of a mail repository](manage-webadmin.html#removing-a-mail-from-a-mail-repository).

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

As explained in the [Mailbox Event Bus](#mailbox-event-bus) section, processing those events can fail sometimes.

Currently, an administrator can monitor indexation failures through `ERROR` log review. You can as well
[list failed events](manage-webadmin.html#Listing_failed_events) by looking with the group called 
`org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex$ElasticSearchListeningMessageSearchIndexGroup`.
A first on-the-fly solution could be to just 
[redeliver those group events with event dead letter](#mailbox-event-bus).

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
