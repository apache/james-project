# 43. Avoid ElasticSearch on critical reads

Date: 2020-11-11

## Status

Accepted (lazy consensus) & implemented

Scope: Distributed James

## Context

A user willing to use a webmail powered by the JMAP protocol will end up doing the following operations:
 - `Mailbox/get` to retrieve the mailboxes. This call is resolved against metadata stored in Cassandra.
 - `Email/query` to retrieve the list of emails. This call is nowadays resolved on ElasticSearch for Email search after
 a right resolution pass against Cassandra.
 - `Email/get` to retrieve various levels of details. Depending on requested properties, this is either
 retrieved from Cassandra alone or from ObjectStorage.

So, ElasticSearch is queried on every JMAP interaction for listing emails. Administrators thus need to enforce availability and good performance
for this component.

Relying on more services for every read also harms our resiliency as ElasticSearch outages have major impacts.

Also we should mention our ElasticSearch implementation in Distributed James suffers the following flaws:
 - Updates of flags lead to updates of the all Email object, leading to sparse segments
 - We currently rely on scrolling for JMAP (in order to ensure messageId uniqueness in the response while respecting limit & position)
 - We noticed some very slow traces against ElasticSearch, even for simple queries.

Regarding Distributed James data-stores responsibilities:
 - Cassandra is the source of truth for metadata, its storage needs to be adapted to known access patterns.
 - ElasticSearch allows resolution of arbitrary queries, and performs full text search.

## Decision

Provide an optional view for most common `Email/query` requests both on Draft and RFC-8621 implementations.
This includes filters and sorts on 'sentAt'.

This view will be stored into Cassandra, and updated asynchronously via a MailboxListener.

## Consequences

A migration task will be provided for new adopters.

Administrators would be offered a configuration option to turn this view on and off as needed.

If enabled, given clients following well defined Email/query requests, administrators would no longer need
to ensure high availability and good performances for ElasticSearch to ensure availability of basic usages
(mailbox content listing).

Given these pre-requisites, we thus expect a decrease in overall ElasticSearch load, allowing savings compared
to actual deployments. Furthermore, we expect better performances by resolving such queries against Cassandra.

The expected added load to Cassandra is low, as the search is a simple Cassandra read. As we only store messageId,
Cassandra dataset size will only grow of a few percents if enabled.

## Alternatives

Those not willing to adopt this view will not be affected. By disabling the listener and the view usage, they will keep
resolving all `Email/query` against ElasticSearch.

Another solution is to implement the projecting using a in-memory datagrid such as infinispan. The projection
would be computed using a MailboxListener and the data would be first fetched from this cache and fallback to
ElasticSearch. We did not choose it as Cassandra is already there, well mastered, as disk storage is cheaper than
memory. InfiniSpan would moreover need additional datastore to allow a persistent state. Infinispan on the other hand
would be faster and would have less restrictions on data filtering and sorting. Also this would require one more software dependency.

## Example of optimized JMAP requests

### A: Email list sorted by sentAt, with limit

RFC-8621:

```
["Email/query",
 {
   "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
   "filter: {
       "inMailbox":"abcd"
   }
   "comparator": [{
     "property":"sentAt",
     "isAscending": false
   }],
   "position": 30,
   "limit": 30
 },
 "c1"]
```

Draft:

```
[["getMessageList", {"filter":{"inMailboxes": ["abcd"]}, "sort": ["date desc"]}, "#0"]]
```

### B: Email list sorted by sentAt, with limit, after a given receivedAt date

RFC-8621:

```
["Email/query",
 {
   "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
   "filter: {
       "inMailbox":"abcd",
       "after": "aDate"
   }
   "comparator": [{
     "property":"sentAt",
     "isAscending": false
   }],
   "position": 30,
   "limit": 30
 },
 "c1"]
```

Draft: Draft do only expose a single date property thus do not differenciate sentAt from receivedAt. Draft adopts sentAt
to back the date property up, thus the above request cannot be written using draft syntax.

### C: Email list sorted by sentAt, with limit, after a given sentAt date

Draft:

```
[["getMessageList", {"filter":{"after":"aDate", "inMailboxes": ["abcd"]}, "sort": ["date desc"]}, "#0"]]
```

RFC-8621: There is no filter properties targeting "sentAt" thus the above request cannot be written.

## Cassandra table structure

Several tables are required in order to implement this view on top of Cassandra.

Eventual denormalization consistency can be enforced by using BATCH statements.

A table allows sorting messages of a mailbox by sentAt, allows answering A and C:

```
TABLE email_query_view_sent_at
PRIMARY KEY mailboxId
CLUSTERING COLUMN sentAt
CLUSTERING COLUMN messageId
ORDERED BY sentAt
```

A table allows filtering emails after a receivedAt date. Given a limited number of results, soft sorting and limits can
be applied using the sentAt column. This allows answering B:

```
TABLE email_query_view_sent_at
PRIMARY KEY mailboxId
CLUSTERING COLUMN receivedAt
CLUSTERING COLUMN messageId
COLUMN sentAt
ORDERED BY receivedAt
```

Finally upon deletes, receivedAt and sentAt should be known. Thus we need to provide a lookup table:

```
TABLE email_query_view_date_lookup
PRIMARY KEY mailboxId
CLUSTERING COLUMN messageId
COLUMN sentAt
COLUMN receivedAt
```

Note that to handle position & limit, we need to fetch `position + limit` ordered items then removing `position` firsts items.

## References

* [JIRA](https://issues.apache.org/jira/browse/JAMES-3440)
* [PR discussing this ADR](https://github.com/apache/james-project/pull/259)