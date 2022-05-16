# 54. ElasticSearch search overrides

Date: 2022-05-16

## Status

Accepted (lazy consensus).

Implemented.

## Context

IMAP SEARCH requests can of course be used for user searches. But many applications (eg Samsung email client) also uses
IMAP SEARCH for resynchronization.

The use of ElasticSearch in the context of resynchronization is a problem as:

 - Resynchronization incurs many requests that ElasticSearch is not made to cope with.
 - This has a performance impact, and some 60+ seconds can be spotted.
 - ElasticSearch indexing is asynchronous which is not ideal for resynchronization
 - An ElasticSearch downtime would imply a full downtime for these clients.
 
Those resynchronization queries would better be served by the metadata database

## Decision

Define an API, `SearchOverride` that can be called on matching search queries and thus offload ElasticSearch.

Provide an extension mechanism for search overrides, where potentially custom implementations can be loaded from the
configuration.

Provide well targeted `SearchOverride`, implemented on top of `Cassandra` database.

## Consequences

We expect to :

 - Observe less IMAP SEARCH slow requests
 - ElasticSearch downtime should not impact IMAP clients using IMAP SEARCH like IMAP Samsung application
 - Thus also be able to support a higher IMAP workload as Cassandra is more suited to resynchronisation requests

## Sample IMAP requests


```
SearchOperation{key=SearchKey{type=TYPE_UID, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=[IdRange : TYPE: FROM UID: MessageUid{uid=1}:MessageUid{uid=9223372036854775807}], sequences=null, keys=Optional.empty}, options=[]}
```

```
SearchOperation{key=SearchKey{type=TYPE_AND, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=null, keys=Optional[[
    SearchKey{type=TYPE_SEQUENCE_SET, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=[IdRange ( 1->9223372036854775807 )], keys=Optional.empty}, 
    SearchKey{type=TYPE_DELETED, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=null, keys=Optional.empty}]]},
    options=[COUNT]}
```

```
SearchOperation{key=SearchKey{type=TYPE_AND, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=null, keys=Optional[[
    SearchKey{type=TYPE_UID, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=[IdRange : TYPE: FROM UID: MessageUid{uid=1}:MessageUid{uid=9223372036854775807}], sequences=null, keys=Optional.empty}, 
    SearchKey{type=TYPE_UNSEEN, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=null, keys=Optional.empty}]]}, options=[COUNT]}
```

```
SearchOperation{key=SearchKey{type=TYPE_AND, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=null, keys=Optional[[
    SearchKey{type=TYPE_SEQUENCE_SET, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=[IdRange ( 1->9223372036854775807 )], keys=Optional.empty}, 
     SearchKey{type=TYPE_NOT, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=null, keys=Optional[[
        SearchKey{type=TYPE_DELETED, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=null, keys=Optional.empty}]]}]]}, options=[ALL]}
```

```
SearchOperation{key=SearchKey{type=TYPE_ALL, date=null, size=0, value=null, seconds=-1, modSeq=-1, uids=null, sequences=null, keys=Optional.empty}, options=[]}
```


## References

- [JIRA](https://issues.apache.org/jira/browse/JAMES-3769)