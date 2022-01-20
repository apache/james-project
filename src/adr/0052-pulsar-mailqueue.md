# 52. Pulsar MailQueue

Date: 2022-01-07

## Status

Accepted (lazy consensus).

Implemented.

Provides an alternative to [ADR-31 Distributed MailQueue (RabbitMQ + Cassandra)](0031-distributed-mail-queue.md).

## Context

### Mail Queue

MailQueue is a central component of SMTP infrastructure allowing asynchronous mail processing. This enables a short 
SMTP reply time despite a potentially longer mail processing time. It also works as a buffer during SMTP peak workload
to not overload a server. 

Furthermore, when used as a Mail Exchange server (MX), the ability to add delays being observed before dequeing elements
allows, among others:

 - Delaying retries upon MX delivery failure to a remote site.
 - Throttling, which could be helpful for not being considered a spammer.

A mailqueue also enables advanced administration operations like traffic review, discarding emails, resetting wait 
delays, purging the queue, etc.

### Existing distributed MailQueue

Distributed James currently ships a distributed MailQueue composing the following software with the following 
responsibilities:

 - **RabbitMQ** for messaging. A rabbitMQ consumer will trigger dequeue operations.
 - A time series projection of the queue content (order by time list of mail metadata) will be maintained in **Cassandra** . 
 Time series avoid the aforementioned tombstone anti-pattern, and no polling is performed on this projection.
 - **ObjectStorage** (Swift or S3) holds large byte content. This avoids overwhelming other software which do not scale
 as well in term of Input/Output operation per seconds.
 
This implementation suffers from the following pitfall:

 - **RabbitMQ** is hard to reliably operate in a cluster. Cluster queues were only added in the 3.8 release line. Consistency 
 guaranties for exchanges are unclear.
 - The RabbitMQ Java driver is boiler plate and error prone. Things like retries, 
 exponential back-offs, dead-lettering do not come out of the box. Publish confirms are tricky. Blocking calls are 
 often performed. The driver is not cluster aware and would operate connected to a single host.
 - The driver reliability is questionable: we experienced some crashed consumers that are never restarted.
 - Throughput and scalability of RabbitMQ is questionable.
 - The current implementation does not support priorities, delays.
 - The current implementation is known to be complex, hard to maintain, with some non-obvious tradeoffs.

### A few words about Apache Pulsar

Apache Pulsar is a cloud-native, distributed messaging and streaming platform. It is horizontally scalable, low latency 
with durability, persistent, multi-tenant, geo replicated. The count of topics can reach several millions, making it suitable
for all queuing usages existing in James, including the one of the Event Bus (cf [ADR 37](0037-eventbus.md) and
[ADR 38](0038-distributed-eventbus.md)).

Pulsar supports advanced features like delayed messages, priorities, for instance, making it suitable to a MailQueue 
implementation.

Helm charts to ease deployments are available.

Pulsar is however complex to deploy and relies on the following components:

 - Stateless brokers
 - Bookies (Bookkeeper) maintaining the persistent log of messages
 - ZooKeeper quorum used for cluster-level configuration and coordination
 
This would make it suitable for large to very-large deployments or PaaS.

The Pulsar SDK is handy and handles natively reactive calls, retries, dead lettering, making implementation less 
boiler plate.

## Decision

Provide a distributed mail queue implemented on top of Pulsar for email metadata, using the blobStore to store email 
content.

Package this mail queue in a simple artifact dedicated to distributed mail processing.

## Consequences

We expect an easier way to operate a cheaper and more reliable MailQueue.

We expect delays being supported as well.

## Complementary work

Pulsar technology would benefit from a broader adoption in James, eventually becoming the de-facto standard solution 
backing Apache James messaging capabilities.

To reach this status the following work needs to be under-taken:
 - The event bus (described in [ADR 37](0037-eventbus.md)) would benefit from a Pulsar implementation, replacing the 
 existing RabbitMQ one (described in [ADR-38](0038-distributed-eventbus.md)). See [JIRA-3699](https://issues.apache.org/jira/browse/JAMES-3699).
 - While being less critical, a task manager implementation would be needed as well to replace the RabbitMQ one
 described in [ADR 2](0002-make-taskmanager-distributed.md) [ADR 3](0003-distributed-workqueue.md) 
 [ADR 4](0004-distributed-tasks-listing.md) [ADR 5](0005-distributed-task-termination-ackowledgement.md) 
 [ADR 6](0006-task-serialization.md) [ADR 7](0007-distributed-task-cancellation.md) 
 [ADR 8](0008-distributed-task-await.md), eventually allowing to drop the RabbitMQ technology all-together.

We could then create a new artifact relying solely on Pulsar, and deprecate the RabbitMQ based artifact.

A broader adoption of Pulsar would benefit from performance insights.

This work could be continued, for instance under the form of a Google Summer of Code for 2022.

### Nice to have complementary work

 - The Pulsar MailQueue needs to work on top of a deduplicated blob store. To do this we need to be able to list blobs 
 referenced by the Pulsar MailQueue, see [JIRA-3703](https://issues.apache.org/jira/browse/JAMES-3703).
 
 The support of deduplicated blobs in a queue, that is short lived have less benefits in terms of storage space. Yet it enables to do a single blob creation accross the full message lifecycle for message bodies.
 
 Priorities are not yet supported by the current implementation. See [JIRA-XXXX](TODO).
 
## Technical details

[[This section requires a deep review]]

[Akka](https://akka.io/) actor system is used in single node mode as a processing framework.

The MailQueue relies on the following topology:

 - out topic :  contains the mails that are ready to be dequeued.
 - scheduled topic: emails that are delayed are first enqueued there.
 - filter topic: Deletions (name, sender, recipients) prior a given sequence are synchronized between nodes using this topic.
 - filter scheduled topic: Deletions for the scheduled topic, applied while moving items from the scheduled topic to the out topic.
 
 
 The consumers on out topic and scheduled topic use the same subscription name and shared consumers. On filter topic, each consumer uses a unique subscription name and will therefore receive a copy of every messages in the topic. this ensures a full distribution of the filter state to all nodes in the cluster.

Upon enqueue, the blobs are first saved, then the Pulsar message payload is generated and published to the relevant 
topic (out or scheduled).

Scheduled messages have their `deliveredAt` property set to the desired value. When the delay is 
expired, the message will be consumed and thus moved to the out topic. Flushes simply copy content of the scheduled
topic to the out topic then reset the offset of the scheduled queue, atomically. Expired filters are removed.

Note that in current versions of pulsar there is a scheduled job that handles scheduled messages, the accuracy of scheduling is limited by the frequency at which this job runs.


The size of the mail queue can be simply computed from the out and scheduled topics.

Upon deletes, the condition of this deletion, as well as the sequence before which it applies is synchronized across
nodes an in-memory datastructures wrapped in an actor. Each instance uses a unique subscription and thus will maintain a
set of all deletions ever performed. This mechanism is repeated for both the out
and the scheduled topic, using the respective sequence values for each set of filters.

Upon dequeues, messages of the out and scheduled topics are filtered using that 
in-memory data structure, then exposed as a reactive publisher.

Upon browsing, both the out and scheduled topics are read from the consumption offset and filtering is applied.

Upon clear, the out topic is deleted.


Miscellaneous remarks:

 - By design the implementation doesn't try to offer absolute consistency guarantees. We tend to lean on the side of eventual consistency. For instance message moved for scheduled to out topics might temporarily be browsed in double or deletes might not apply instantly.
 - The pulsar admin client is used to list existing queues and to move the current offset of scheduled message subscription upon flushes.
 - Priorities are not yet supported.
 - Only metadata transits through Pulsar. The general purpose of James blobStore, backed by a S3 compatible API, is used to
   store the underlying email content. Please note that in the context of a mail queue, the tradeoffs behind a blob store choice are different: in long term storage cheaper storage cost would be preferable to higher latencies, especially for writes done asynchronously whereas a mailQueue would benefit from more expensive low latency storage (email do not stay there long) with low write latencies (user facing in SMTP). We might benefit in the future to support usage of a distinct blob store solution to back the queue.

## References
 
 - [Apache Pulsar](https://pulsar.apache.org/)
 
Materials regarding this ADR:

 - [PR of this ADR](https://github.com/apache/james-project/pull/829)
 - [Mailing list discussions](https://www.mail-archive.com/server-dev@james.apache.org/msg71462.html)
 - [JIRA: JAMES-3687](https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3687)
 - [PR contributing the original Pulsar MailQueue](https://github.com/apache/james-project/pull/808)
