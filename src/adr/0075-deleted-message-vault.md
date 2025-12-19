# 75. Deletes Message Vault

Date: 2025-12-19

## Status

Accepted (lazy consensus) & implemented.

This ADR is written at posteriori in order to capture knowlege of the team following the write up 
of [0074-dedicated-eventbus-for-message-content-deletion.md](0074-dedicated-eventbus-for-message-content-deletion.md)

## Context

In standard email systems, when a message is deleted—whether by a user, an administrator, or automatically—it is often permanently lost. This creates several 
problems in environments where email is a critical business record.

As such we want a mechanism that protects against:

 - **Accidental or malicious deletion**: Users can accidentally delete important emails, or malicious actors may intentionally remove messages to hide evidence. Recovery 
shall be possible.
 - **Legal and regulatory compliance**: Many organizations must comply with regulations that require retention of business communications and the ability to produce 
deleted emails during audits or legal discovery.
 - **Administrator control and traceability**: administrator needs to control and understand destructive actions done on user account (date of deletion and what had been deleted).

However we want a clear **separation of user experience and data retention**. Users expect that “delete” means the message disappears from their mailbox.

However incident recovery, and Recovery Point Objectives are a non objective that shall be addressed through global database level backups.

## Decision

Provide a James mailbox plugin, bundled in Guice application, the **Deleted Message Vault**.

Provide an implementation of this vault atop the object store, which stores data in a time organized fashion (per month) in a dedicated buckets. Minimal 
metadata are to be kept onto the Cassandra / Postgres  database.

Provide a webadmin endpoints for restoring user data, with a minimal and simple fliter logic to allow restoring specific content. This action is not exposed to the end user.

Provide a webadmin endpoint to access deleted messages if need be.

Provide a webadmin endpoint to delete the vault data that no longer needs to be retained.

Plug this vault onto the mailbox deletion process. We leverage [0029-Cassandra-mailbox-deletion-cleanup.md](0029-Cassandra-mailbox-deletion-cleanup.md) asynchronous deletion listener as well as
the [0074-dedicated-eventbus-for-message-content-deletion.md](0074-dedicated-eventbus-for-message-content-deletion.md) dedicated event bus for effective content deletion to do so. Please
note that blob deduplication mentioned in [0049-deduplicated-blobs-gs-with-bloom-filters.md](0049-deduplicated-blobs-gs-with-bloom-filters.md) needs to be active. 

## Consequences

Aforementioned objectives are attained.

An extra copy is needed upon deletes which can be expensive. That is why we needed [0074-dedicated-eventbus-for-message-content-deletion.md](0074-dedicated-eventbus-for-message-content-deletion.md) 
especially upon large mailbox deletion.

Disk space impact:
 - Deleted message vault is not counted onto the user quota
 - Content is not deduplicated onto the deleted message vault

# References 

 - [0029-Cassandra-mailbox-deletion-cleanup.md](0029-Cassandra-mailbox-deletion-cleanup.md)
 - [0049-deduplicated-blobs-gs-with-bloom-filters.md](0049-deduplicated-blobs-gs-with-bloom-filters.md)
 - [0074-dedicated-eventbus-for-message-content-deletion.md](0074-dedicated-eventbus-for-message-content-deletion.md)
 - [Asynchronous deletions with DeletedMessageVault on top of Cassandra](https://issues.apache.org/jira/browse/JAMES-3882)

