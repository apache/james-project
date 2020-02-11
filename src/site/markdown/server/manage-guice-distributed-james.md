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
 - **Guice application lifecycle**: Ensures James Guice successfully started, and is up. Logs should contain explanations if
 James did not start well.
 - **MessageFastViewProjection**: Follows MessageFastViewProjection cache miss rates and warns if it is below 10%. If this 
 projection is missing, this results in performance issues for JMAP GetMessages list requests. WebAdmin offers a
 [global](manage-webadmin.html#recomputing-global-jmap-fast-message-view-projection) and 
 [per user](manage-webadmin.html#recomputing-user-jmap-fast-message-view-projection) projection re-computation. Note that
 as computation is asynchronous, this projection can be slightly out of sync on a normally behaving server.