# 31. Distributed Mail Queue

Date: 2020-04-13

## Status

Accepted (lazy consensus) & implemented

## Context

MailQueue is a central component of SMTP infrastructure allowing asynchronous mail processing. This enables a short 
SMTP reply time despite a potentially longer mail processing time. It also works as a buffer during SMTP peak workload
to not overload a server. 

Furthermore, when used as a Mail Exchange server (MX), the ability to add delays to be observed before dequeing elements
allows, among others:

 - Delaying retries upon MX delivery failure to a remote site.
 - Throttling, which could be helpful for not being considered a spammer.

A mailqueue also enables advanced administration operations like traffic review, discarding emails, resetting wait 
delays, purging the queue, etc.

Spring implementation and non distributed implementations rely on an embedded ActiveMQ to implement the MailQueue. 
Emails are being stored in a local file system. An administrator wishing to administrate the mailQueue will thus need 
to interact with all its James servers, which is not friendly in a distributed setup.

Distributed James relies on the following third party softwares (among other):

 - **RabbitMQ** for messaging. Good at holding a queue, however some advanced administrative operations can't be 
implemented with this component alone. This is the case for `browse`, `getSize` and `arbitrary mail removal`.
 - **Cassandra** is the metadata database. Due to **tombstone** being used for delete, queue is a well known anti-pattern.
 - **ObjectStorage** (Swift or S3) holds byte content.

## Decision

Distributed James should ship a distributed MailQueue composing the following softwares with the following 
responsibilities:

 - **RabbitMQ** for messaging. A rabbitMQ consumer will trigger dequeue operations.
 - A time series projection of the queue content (order by time list of mail metadata) will be maintained in **Cassandra** (see later). Time series avoid the 
aforementioned tombstone anti-pattern, and no polling is performed on this projection.
 - **ObjectStorage** (Swift or S3) holds large byte content. This avoids overwhelming other softwares which do not scale
 as well in term of Input/Output operation per seconds.
 
Here are details of the tables composing Cassandra MailQueue View data-model:

 - **enqueuedMailsV3** holds the time series. The primary key holds the queue name, the (rounded) time of enqueue 
designed as a slice, and a bucketCount. Slicing enables listing a large amount of items from a given point in time, in an 
fashion that is not achievable with a classic partition approach. The bucketCount enables sharding and avoids all writes 
at a given point in time to go to the same Cassandra partition. The clustering key is composed of an enqueueId - a 
unique identifier. The content holds the metadata of the email. This table enables, from a starting date, to load all of
the emails that have ever been in the mailQueue. Its content is never deleted.
 - **deletedMailsV2** tells wether a mail stored in *enqueuedMailsV3* had been deleted or not. The queueName and 
enqueueId are used as primary key. This table is updated upon dequeue and deletes. This table is queried upon dequeue 
to filter out deleted/purged items. 
 - **browseStart** store the latest known point in time from which all previous emails had been deleted/dequeued. It 
enables to skip most deleted items upon browsing/deleting queue content. Its update is probability based and 
asynchronously piggy backed on dequeue.
 
Here are the main mail operation sequences:

 - Upon **enqueue** mail content is stored in the *object storage*, an entry is added in *enqueuedMailsV3* and a message 
 is fired on *rabbitMQ*.
 - **dequeue** is triggered by a rabbitMQ message to be received. *deletedMailsV2* is queried to know if the message had
already been deleted. If not, the mail content is retrieved from the *object storage*, then an entry is added in 
*deletedMailsV2* to notice the email had been dequeued. A dequeue has a random probability to trigger a browse start
update. If so, from current browse start, *enqueuedMailsV3* content is iterated, and checked against *deletedMailsV2*
until the first non deleted / dequeued email is found. This point becomes the new browse start. BrowseStart can never 
point after the start of the current slice. A grace period upon browse start update is left to tolerate clock skew.
Update of the browse start is done randomly as it is a simple way to avoid synchronisation in a distributed system: we
ensure liveness while uneeded browseStart updates being triggered would simply waste a few resources.
 - Upon **browse**, *enqueuedMailsV3* content is iterated, and checked against *deletedMailsV2*, starting from the 
current browse start.
 - Upon **delete/purge**, *enqueuedMailsV3* content is iterated, and checked against *deletedMailsV2*. Mails matching 
the condition are marked as deleted in *enqueuedMailsV3*.
 - Upon **getSize**, we perform a browse and count the returned elements.
 
The distributed mail queue requires a fine tuned configuration, which mostly depends of the count of Cassandra servers, 
and of the mailQueue throughput:
 - **sliceWindow** is the time period of a slice. All the elements of **enqueuedMailsV3** sharing the same slice are 
retrieved at once. The bigger, the more elements are going to be read at once, the less frequent browse start update 
will be. Lower values might result in many almost empty slices to be read, generating higher read load. We recommend 
**sliceWindow** to be chosen from users maximum throughput so that approximately 10.000 emails be contained in a slice.
Only values dividing the current *sliceWindow* are allowed as new values (otherwize previous slices might not be found).
 - **bucketCount** enables spreading the writes in your Cassandra cluster using a bucketting strategy. Low values will 
lead to workload not to be spread evenly, higher values might result in uneeded reads upon browse. The count of Cassandra 
servers should be a good starting value. Only increasing the count of buckets is supported as a configuration update as
decreasing the bucket count might result in some buckets to be lost.
 - **updateBrowseStartPace** governs the probability of updating browseStart upon dequeue/deletes. We recommend choosing 
a value guarantying a reasonable probability of updating the browse start every few slices. Too big values will lead to
uneeded update of not yet finished slices. Too low values will end up in a more expensive browseStart update and browse
iterating through slices with all their content deleted. This value can be changed freely.

We rely on eventSourcing to validate the mailQueue configuration changes upon James start following the aforementioned rules.

## Limitations

Delays are not supported. This mail queue implementation is thus not suited for a Mail Exchange (MX) implementation.
The [following proposal](https://issues.apache.org/jira/browse/JAMES-2896) could be a solution to support delays.

**enqueuedMailsV3** and **deletedMailsV2** is never cleaned up and the corresponding blobs are always referenced. This is not
ideal both from a privacy and space storage costs point of view.

**getSize** operation is sub-optimal and thus not efficient. Combined with metric reporting of mail queue size being 
periodically performed by all James servers this can lead, upon increasing throughput to a Cassandra overload. A configuration
parameter allows to disable mail queue size reporting as a temporary solution. Some alternatives had been presented like 
[an eventually consistent per slice counters approach](https://github.com/linagora/james-project/pull/2565). An other 
proposed solution is [to rely on RabbitMQ management API to retrieve mail queue size](https://github.com/linagora/james-project/pull/2325)
however by design it cannot take into account purge/delete operations. Read 
[the corresponding JIRA](https://issues.apache.org/jira/browse/JAMES-2733).

## Consequences

Distributed mail queue allows a better spreading of Mail processing workload. It enables a centralized mailQueue
management for all James servers.

Yet some additional work is required to use it as a Mail Exchange scenario.

## References

* [JIRA](https://issues.apache.org/jira/browse/JAMES-2541)