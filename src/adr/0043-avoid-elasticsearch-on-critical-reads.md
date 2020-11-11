# 43. Avoid ElasticSearch on critical reads

Date: 2020-11-11

## Status

Accepted (lazy consensus).

Scope: Distributed James

## Context

James powers the JMAP protocol.

A user willing to use a webmail will end up doing the following operations:
 - `Mailbox/get` to retrieve the mailboxes. This call is resolved against metadata stored in Cassandra.
 - `Email/query` to retrieve the list of emails. This call is nowadays resolved on ElasticSearch.
 - `Email/get` to retrieve various levels of details. Depending of requested properties, this is either
 resolved on Cassandra alone or on ElasticSearch.

So, ElasticSearch is queried on every JMAP interaction. Administrators thus need to enforce availability and good performance
for this component.

Relying on more software for every read also harms our resiliency as ElasticSearch outages have major impacts.

Also we should mention our ElasticSearch implementation in Distributed James suffer the following flows:
 - Updates of flags leads to updates of the all Email object, leading to sparse segments
 - We currently rely on scrolling for JMAP (in order to ensure messageId uniqueness in the response while respecting limit & position)
 - We noticed some very slow traces against ElasticSearch, even for simple queries.

Regarding Distributed James data-stores responsibilities:
 - Cassandra is the source of truth for metadata, its storage need to be adapted to known access patterns.
 - ElasticSearch allows resolution of arbitrary queries, and perform full text search.

## Decision

Provide an optional view for most common `Email/query` requests both on Draft and RFC-8621 implementations.
This includes filters and sorts on 'sentAt'.

This view will be stored on Cassandra, and updated asynchronously via a MailboxListener.

## Consequences

A migration task will be provided for new adopters.

Administrators would be offered a configuration option to turn this view on and off as needed.

If enabled administrators would no longer need to ensure high availability and good performances for ElasticSearch.
We thus expect a decrease in overall ElasticSearch load, allowing savings compared to actual deployments.
Furthermore, we expected better performances by resolving such queries against Cassandra.

## Alternatives

Those not willing to adopt this view will not be affected. By disabling the listener and the view usage, they will keep
resolving all `Email/query` against ElasticSearch.
