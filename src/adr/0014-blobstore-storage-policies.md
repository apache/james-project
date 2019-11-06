# 14. Add storage policies for BlobStore

Date: 2019-10-09

## Status

Proposed

Adoption needs to be backed by some performance tests, as well as data repartition between Cassandra and object storage shifts.

## Context

James exposes a simple BlobStore API for storing raw data. However such raw data often vary in size and access patterns.

As an example:

 - Mailbox message headers are expected to be small and frequently accessed
 - Mailbox message body are expected to have sizes ranging from small to big but are unfrequently accessed
 - DeletedMessageVault message headers are expected to be small and unfrequently accessed

Also, the capabilities of the various implementations of BlobStore have different strengths:

 - CassandraBlobStore is efficient for small blobs and offers low latency. However it is known to be expensive for big blobs. Cassandra storage is expensive.
 - Object Storage blob store is good at storing big blobs, but it induces higher latencies than Cassandra for small blobs for a cost gain that isn't worth it.

Thus, significant performance and cost ratio refinement could be unlocked by using the right blob store for the right blob.

## Decision

Introduce StoragePolicies at the level of the BlobStore API.

The proposed policies include:

 - SizeBasedStoragePolicy: The blob underlying storage medium will be chosen depending on its size.
 - LowCostStoragePolicy: The blob is expected to be saved in low cost storage. Access is expected to be unfrequent.
 - PerformantStoragePolicy: The blob is expected to be saved in performant storage. Access is expected to be frequent.

An HybridBlobStore will replace current UnionBlobStore and will allow to choose between Cassandra and ObjectStorage implementations depending on the policies.

DeletedMessageVault, BlobExport & MailRepository will rely on LowCostStoragePolicy. Other BlobStore users will rely on SizeBasedStoragePolicy.

Some performance tests will be run in order to evaluate the improvements.

## Consequences

We expect small frequently accessed blobs to be located in Cassandra, allowing ObjectStorage to be used mainly for large costly blobs.

In case of a less than 5% improvement, the code will not be added to the codebase and the proposal will get the status 'rejected'.

We expect more data to be stored in Cassandra. We need to quantify this for adoption.

As reads will be reading the two blobStores, no migration is required to use this composite blobstore on top an existing implementation,
however we will benefits of the performance enhancements only for newly stored blobs.

## References

 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2921)