# 13. Precompute JMAP Email preview

Date: 2019-10-09

## Status

Proposed

Adoption needs to be backed by some performance tests.

## Context

JMAP messages have a handy preview property displaying the firsts 256 characters of meaningful test of a message.

This property is often displayed for message listing in JMAP clients, thus it is queried a lot.

Currently, to get the preview, James retrieves the full message body, parse it using MIME parsers, removes HTML and keep meaningful text.

## Decision

We should pre-compute message preview.

A MailboxListener will compute the preview and store it in a MessagePreviewStore.

We should have a Cassandra and memory implementation.

When the preview is precomputed then for these messages we can consider the "preview" property as a metadata.

When the preview is not precomputed then we should compute the preview for these messages, and save the result for later.

We should provide a webAdmin task allowing to rebuild the projection. The computing and storing in MessagePreviewStore 
is idempotent and the task can be run in live without any concurrency problem.

Some performance tests will be run in order to evaluate the improvements.

## Consequences

We expect a huge performance enhancement for JMAP clients relying on preview for listing mails.

In case of a less than 5% improvement, the code will not be added to the codebase and the proposal will get the status 'rejected'.

## References

 - https://jmap.io/server.html#1-emails JMAP client guice states that preview needs to be quick to retrieve

 - Similar decision had been taken at FastMail: https://fastmail.blog/2014/12/15/dec-15-putting-the-fast-in-fastmail-loading-your-mailbox-quickly/

 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2919)
