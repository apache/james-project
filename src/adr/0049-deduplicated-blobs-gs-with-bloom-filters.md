# 49. Deduplicated blobs garbage collection with bloom filters

Date: 2021-07-21

## Status

Accepted (lazy consensus).

Implemented.

Proposes a simple way to implement an alternative to  [39. Distributed blob garbage collector](0039-distributed-blob-garbage-collector.md)

### Changes

2021-08-25: Edited to mention Cuckoo filters (Alternative).

## Context

The body, headers, attachments of the mails are stored as blobs in a blob store.

In order to save space in those stores, those blobs are de-duplicated using a hash of their content.

To attain that the current blob store will read the content of the blob before saving it, and generate its id based on
a hash of this content. This way two blobs with the same content will share the same id and thus be saved only once.

This makes the safe deletion of one of those blobs a non-trivial problem as we can't delete one blob without ensuring
that all references to it are themselves deleted. For example if two messages share the same blob, when we delete
one message there is at the time being no way to tell if the blob is still referenced by another message.

## Decision

To solve this, we will propose a simple two steps algorithms to provide a background deduplication job.

The **first step** consists in building a bloom filter using the entities referencing the blobs.

In the **second step** we iterate over blobs and check in the bloom filter to predict if they are referenced on not.

**Bloom filters** are probabilistic data structures. Here the reference prediction can produce false positives: we might 
skip some non referenced blobs that should have been garbage collected. However, the associated probability can be tuned 
and by adding a salt we can ensure subsequent runs will have different sets of false positives and thus that all blobs is
eventually garbage collected.

To avoid concurrency issues, where we could garbage collect a blob at the same time a new reference to it appear,
a `reference generation` notion will be added. The de-duplicating id of the blobs which before where constructed
using only the hash of their content,  will now include this `reference generation` too. To avoid synchronization 
issues, the `generation` will be time based.

So only blobs belonging to the `reference generation` `n-2` will be eligible for garbage collection to avoid 
concurrency issues, and allow for a clock skew.

Finally, we wish to offer the opportunity to configure, and reconfigure, the `generation` duration. In order to do so,
we introduce a `generation family` part in the blobId. Incremented by the administrator on each configuration changes on
the generation duration it allows avoiding conflicts in generations getting the same number before and after the change:
all blobIds with a different family are considered belonging to a distinct generation ready to be garbage collected. This
allows arbitrary changes in the generation duration.

## Consequences

We need to be able to list blobIds of blobs within the BlobStore. This operation should be supported by the blobStore 
to prevent deduplication issues.

We need to introduce a new API: `BlobReferenceSource` listing the blobIds currently referenced by an entity. We will
need to implement it for each entity: Attachment, messages, mail queue and mail repositories.

The garbage collection is a heavy-weight task whose complexity is proportional to the dataset size. 

Regarding the garbage collection generation duration tradeoff:
 - The longer, the longer it will take to effectively delete blobs
 - However, the longer, the more efficient deduplication will be (deduplication is of course scoped to a single 
   generation).
 - Generation duration does not impact the overall process speed.
 
Please note that this design does not require additional responsibilities for blobStore user and is thus fully transparent 
to them.
 
## Alternatives

### Iterative approach

[39. Distributed blob garbage collector](0039-distributed-blob-garbage-collector.md) proposes an alternative that could 
be implemented in the future and attempts to reduce the duration of each iteration.

However, it presents the following flaws:
 - Generation switch is synchronous
 - Needs to keep track of deletions
 - Needs to keep track of references
 
Overall consistency in the face of cassandra failures to store any of the above had not been studied carefully.

The need to track references implies that this algorithm is not transparent to blob store users and requires either 
blobStore API modifications or users to actively track references. This is likely to not be transparent to blob store 
users.

Also, the proof of concept yield full data of a generation in memory, and thus this could have an impact on scalability.

As such we believe that a simpler approach that could be implemented timely yield some benefits.

This alternative can be implemented later, once the limits of the bloom filter approach are better known.

The two algorithms are not mutually exclusive and could very well coexist if need be in the same codebase.

### Alternative to bloom filters

[Cuckoo](https://bdupras.github.io/filter-tutorial/) filters is a probability data structure that tends to have a better
space efficiency for low false-positive rates and might be proposed as an alternative for bloom filters.

### Implementation of the Bloom Filter approach with Apache Spark

An other way to be solving potential scaling issues would be to run the Bloom Filter algorithm on a Spark cluster.
Apache Spark would then take care of scheduling the job in a distributed fashion, allowing to scale the count of
worker nodes.

Requiring minimal applicative knowledge, this would be a very good candidate for such a tooling.

## References

 - [JIRA](https://issues.apache.org/jira/browse/JAMES-3150)
 - [PR of this ADR](https://github.com/apache/james-project/pull/594)
 - [Thread on server-dev mailing list](https://www.mail-archive.com/server-dev@james.apache.org/msg70734.html)