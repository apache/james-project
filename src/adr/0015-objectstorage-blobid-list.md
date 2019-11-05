# 15. Persist BlobIds for avoiding persisting several time the same blobs within ObjectStorage

Date: 2019-10-09

## Status

Proposed

Adoption needs to be backed by some performance tests.

## Context

A given mail is often written to the blob store by different components. And mail traffic is heavily duplicated (several recipients receiving similar email, same attachments). This causes a given blob to often be persisted several times.

Cassandra was the first implementation of the blobStore. Cassandra is a heavily write optimized NoSQL database. One can assume writes to be fast on top of Cassandra. Thus we assumed we could always overwrite blobs.

This usage pattern was also adopted for BlobStore on top of ObjectStorage.

However writing in Object storage:
 - Takes time
 - Is billed by most cloud providers

Thus choosing a right strategy to avoid writing blob twice is desirable.

However, ObjectStorage (OpenStack Swift) `exist` method was not efficient enough to be a real cost and performance saver.

## Decision

Rely on a StoredBlobIdsList API to know which blob is persisted or not in object storage. Provide a Cassandra implementation of it. 
Located in blob-api for convenience, this it not a top level API. It is intended to be used by some blobStore implementations
(here only ObjectStorage). We will provide a CassandraStoredBlobIdsList in blob-cassandra project so that guice products combining
object storage and Cassandra can define a binding to it. 

 - When saving a blob with precomputed blobId, we can check the existence of the blob in storage, avoiding possibly the expensive "save".
 - When saving a blob too big to precompute its blobId, once the blob had been streamed using a temporary random blobId, copy operation can be avoided and the temporary blob could be directly removed.

Cassandra is probably faster doing "write every time" rather than "read before write" so we should not use the stored blob projection for it

Some performance tests will be run in order to evaluate the improvements.

## Consequences

We expect to reduce the amount of writes to the object storage. This is expected to improve:
 - operational costs on cloud providers
 - performance improvement
 - latency reduction under load

As id persistence in StoredBlobIdsList will be done once the blob successfully saved, inconsistencies in StoredBlobIdsList
will lead to duplicated saved blobs, which is the current behaviour.

In case of a less than 5% improvement, the code will not be added to the codebase and the proposal will get the status 'rejected'.

## Reference

Previous optimization proposal using blob existence checks before persist. This work was done using ObjectStorage exist method and was prooven not efficient enough.

https://github.com/linagora/james-project/pull/2011 (V2)

 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2921)
