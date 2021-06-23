# 32. Distributed Mail Queue Cleanup

Date: 2020-04-13

## Status

Accepted (lazy consensus) and implemented

## Context

Read [Distributed Mail Queue](0031-distributed-mail-queue.md) for full context.

**enqueuedMailsV3** and **deletedMailsV2** is never cleaned up and the corresponding blobs are always referenced. This is not
ideal both from a privacy and space storage costs point of view.

Note that **enqueuedMailsV3** and **deletedMailsV2** rely on timeWindowCompactionStrategy.

## Decision

Add a new `contentStart` table referencing the point in time from which a given mailQueue holds data, for each mail queue.

The values contained between `contentStart` and `browseStart` can safely be deleted.

We can perform this cleanup upon `browseStartUpdate`: once finished we can browse then delete content of **enqueuedMailsV3**
and **deletedMailsV2** contained between `contentStart` and the new `browseStart` then we can safely set `contentStart` 
to the new `browseStart`.

Content before `browseStart` can safely be considered deletable, and is applicatively no longer exposed. We don't need an
additional grace period mechanism for `contentStart`.

Failing cleanup will lead to the content being eventually updated upon next `browseStart` update.

We will furthermore delete blobStore content upon dequeue, also when the mail had been deleted or purged via MailQueue
management APIs.

## Consequences

All Cassandra SSTable before `browseStart` can safely be dropped as part of the timeWindowCompactionStrategy.

Updating browse start will then be two times more expensive as we need to unreference passed slices.

Eventually this will allow reclaiming Cassandra disk space and enforce mail privacy by removing dandling metadata.

## Alternative

A [proposal](https://github.com/linagora/james-project/pull/3291#pullrequestreview-393501339) was made to piggy back 
cleanup upon dequeue/delete operations. The dequeuer/deleter then directly removes the related metadata from 
`enqueuedMailsV3` and `deletedMailsV2`. This simpler design however have several flaws:

 - if the cleanup fails for any reason then it cannot be retried in the future. There will be no way of cleaning up the 
 related data.
 - this will end up tumbstoning live slices potentially harming browse/delete/browse start updates performance.
 - this proposition don't leverage as efficiently timeWindowCompactionStrategy.

## References

* [JIRA](https://issues.apache.org/jira/browse/JAMES-3319)