# 48. Cleanup of JMAP uploads

Date: 2021-07-21

## Status

Accepted (lazy consensus).

Implemented.

Overridden by [Quota for JMAP uploads](0067-quota-for-jmap-uploads.md)

## Context

JMAP allows users to upload binary content called blobs to be later referenced via method calls. This includes but is not
limited to `Email/set` for specifying the blobId of attachments and `Email/import`.

The [specification](https://jmap.io/spec-core.html#binary-data) strongly encourages enforcing the cleanup of these uploads:

```
A blob that is not referenced by a JMAP object (e.g., as a message attachment) MAY be deleted by the server to free up 
resources. Uploads (see below) are initially unreferenced blobs.

[...] An unreferenced blob MUST NOT be deleted for at least 1 hour from the time of upload; if reuploaded, the same 
blobId MAY be returned, but this SHOULD reset the expiry time.
```

Deleting such uploads in a timely manner is important as:

 - It enables freeing server resources.
 - failing to do so may compromise privacy: content the user have uploaded and long forgotten might still be accessible
 in the underlying data-store. Failing to delete uploads in a timely fashion may jeopardize for instance GDPR compliance.
 
Today, uploads are stored along side email attachments. This means:
 - We can hardly apply a specific lifecycle that cleans up uploads, as distinguishing attachment from uploads is not 
 trivial.
 - We currently have a complex right resolution system on attachment, handling both the upload case (were the attachment
 is linked to a user) and the 'true' attachment case (linked to a message, those who can access the message can access 
 the attachment). This leads to sub-optimal code (slow).

## Decision

We need to create a separate interface `UploadRepository` in `data-jmap` to store uploads for each user. We would provide a memory 
implementation as well as a distributed implementation of it.

The distributed implementation would host metadata of the upload in Cassandra, and the content using the BlobStore API,
so object storage.

This `UploadRepository` would be used by JMAP RFC-8620 to back uploads (instead of the attachment manager), we will 
provide a `BlobResolver` to enable interactions with the uploaded blob. Similarly, we will use the `UploadRepository` to
back uploads of JMAP draft.

We will implement cleanup of the distributed `UploadRepository`. This will be done via:
 - TTLs on the Cassandra metadata.
 - Organisation of the blobs in time ranged buckets, only the two most recent buckets are kept.
 - A WebAdmin endpoint would allow to plan a CRON triggering the cleanup.

## Consequences

Upon migrating to the `UploadRepository`, previous uploads will not be carried over. No migration plan is provided as 
the impact is minimal. Upload prior this change will never be cleaned up. This is acceptable as JMAP implementations are
marked as experimental.

We can clean up attachment storage within the `mailbox-api` and its implementation:
 - Drop `attachmentOwners` cassandra table
 - Remove `getOwners` `storeAttachmentForOwner` methods in the Attachment mapper
 - Rename `storeAttachmentsForMessage*` -> `storeAttachments*` in attachment mapper
 - Simplify resolution logic for `StoreAttachmentManager` (looking message ownership is then enough)
 - Fusion of `attachmentMessageId` and `attachmentV2` table, `attachmentMessageId` to be dropped in next release, 
 `attachmentV2` can be altered to add the referencing `messageId`, and a migration task will be provided to populate it.
 In the meantime a fallback strategy can be supplied: If the messageId cell is null we should default to reading the 
 (old) `attachmentMessageId` table.
 
## Alternatives

[JMAP blob draft](https://datatracker.ietf.org/doc/draft-ietf-jmap-blob/) had been proposed to have the clients explicitly
delete its uploads once the blob had been used to create other entities, as this extension introduce a mean to delete 
blobs.

However, relying on clients to enforce effective deletion seems brittle as:
 - In case of client failures (or malicious client), no mechanisms would ensure effective deletion
 - The main JMAP specification does not mandate nor encourage clients to clean up their uploads using the blob extension
 and as such interoperability issues would arise.

## References

 - [JIRA](https://issues.apache.org/jira/browse/JAMES-3544)
 - [PR of this ADR](https://github.com/apache/james-project/pull/544)
 - [Thread on server-dev mailing list](https://www.mail-archive.com/server-dev@james.apache.org/msg70591.html)