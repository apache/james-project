# 25. Cassandra Blob Store Cache

Date: 2020-04-03

## Status

Accepted (lazy consensus) & implemented

Supercedes [14. Add storage policies for BlobStore](0014-blobstore-storage-policies.md)

## Context

James exposes a simple BlobStore API for storing raw data. However such raw data often vary in size and access patterns.

As an example:

 - Mailbox message headers are expected to be small and frequently accessed
 - Mailbox message body are expected to have sizes ranging from small to big but are unfrequently accessed
 - DeletedMessageVault message headers are expected to be small and unfrequently accessed

The access pattern of some of these kind of blobs does not fit Object Storage characteristics: good at storing big blobs, but 
it induces high latencies for reading small blobs. We observe latencies of around 50-100ms while Cassandra latency is of 4ms.

This gets some operations slow (for instance IMAP FETCH headers, or listing JMAP messages).

## Decision

Implement a write through cache to have better read latency for smaller objects.

Such a cache needs to be distributed in order to be more efficient.

Given that we don't want to introduce new technologies, we will implement it using Cassandra.

The cache should be implemented as a key-value table on a dedicated 'cache' keyspace, with a replication factor of 1, 
and be queried with a consistency level of ONE. 

We will leverage a configurable TTL as an eviction policy. Cache will be populated upon writes and missed read, if the 
blob size is below a configurable threashold. We will use the TimeWindow compaction strategy.

Failure to read the cache, or cache miss will result in a read in the object storage.

## Consequences

Metadata queries are expected not to query the object storage anymore.

[Performance tests](https://github.com/linagora/james-project/pull/3031#issuecomment-572865478) proved such strategies
to be highly effective. We expect comparable performance improvements compared to an un-cached ObjectStorage blob store.

HybridBlobStore should be removed.

## Alternatives

[14. Add storage policies for BlobStore](0014-blobstore-storage-policies.md) proposes to use the CassandraBlobStore to
mimic a cache.

This solution needed further work as we decided to add an option to write all blobs to the object storage in order:
 - To get a centralized source of truth
 - Being able to instantly rollback Hybrid blob store adoption
 
See [this pull request](https://github.com/linagora/james-project/pull/3162)

With such a proposal there is no eviction policy. Also, the storage is done on the main keyspace with a high replication
factor, and QUORUM consistency level (high cost).

To be noted, as cached entries are small, we can assume they are small enough to fit in a single Cassandra row. This is more 
optimized than the large blob handling through blobParts the CassandraBlobStore is doing.
