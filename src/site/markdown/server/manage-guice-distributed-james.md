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
 - [Mail Processing](#mail-processing)

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
