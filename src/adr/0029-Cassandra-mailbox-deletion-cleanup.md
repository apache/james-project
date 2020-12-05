# 29. Cassandra mailbox deletion cleanup

Date: 2020-04-12

## Status

Accepted (lazy consensus) & implemented

## Context

Cassandra is used within distributed James product to hold messages and mailboxes metadata.

Cassandra holds the following tables:
 - mailboxPathV2 + mailbox allowing to retrieve mailboxes informations
 - acl + UserMailboxACL hold denormalized information
 - messageIdTable & imapUidTable allow to retrieve mailbox context information
 - messageV2 table holds message metadata
 - attachmentV2 holds attachments for messages
 - References to these attachments are contained within the attachmentOwner and attachmentMessageId tables
 
Currently, the deletion only deletes the first level of metadata. Lower level metadata stay unreachable. The data looks 
deleted but references are actually still present.

Concretely:
 - Upon mailbox deletion, only mailboxPathV2 & mailbox content is deleted. messageIdTable, imapUidTable, messageV2, 
 attachmentV2 & attachmentMessageId metadata are left undeleted.
 - Upon mailbox deletion, acl + UserMailboxACL are not deleted.
 - Upon message deletion, only messageIdTable & imapUidTable content are deleted. messageV2, attachmentV2 & 
 attachmentMessageId metadata are left undeleted.

This jeopardize efforts to regain disk space and privacy, for example through blobStore garbage collection.

## Decision

We need to cleanup Cassandra metadata. They can be retrieved from dandling metadata after the delete operation had been 
conducted out. We need to delete the lower levels first so that upon failures undeleted metadata can still be reached.

This cleanup is not needed for strict correctness from a MailboxManager point of view thus it could be carried out 
asynchronously, via mailbox listeners so that it can be retried.

## Consequences

Mailbox listener failures lead to eventBus retrying their execution, we need to ensure the result of the deletion to be 
idempotent. 

## References

 - [JIRA](https://issues.apache.org/jira/browse/JAMES-3148)