# 22. Cassandra Message inconsistencies

Date: 2020-02-27

## Status

Accepted (lazy consensus) & implemented

## Context

Messages are denormalized in Cassandra in order to:

 - access them by their unique identifier (messageId), for example through the JMAP protocol
 - access them by their mailbox identifier and Unique IDentifier within that mailbox (mailboxId + uid), for example 
 through the IMAP protocol

Here is the table organisation:

 - `messageIdTable` Holds mailbox and flags for each message, lookup by mailbox ID + UID
 - `imapUidTable` Holds mailbox and flags for each message, lookup by message ID

Failures during the denormalization process will lead to inconsistencies between the two tables.

This can lead to the following user experience:

```
BOB receives a message
The denormalization process fails
BOB can read the message via JMAP
BOB cannot read the message via IMAP

BOB marks a message as SEEN
The denormalization process fails
The message is SEEN in JMAP
The message is UNSEEN in IMAP
```

### Current operations

 - Adding a message:
   - (CassandraMessageMapper) First reference the message in `messageIdTable` then in `imapUidTable`.
   - (CassandraMessageIdMapper) First reference the message in `imapUidTable` then in `messageIdTable`.
 - Deleting a message:
   - (CassandraMessageMapper) First delete the message in `imapUidTable` then in `messageIdTable`.
   - (CassandraMessageIdMapper) Read the message metadata using `imapUidTable`, then first delete the message in 
   `imapUidTable` then in `messageIdTable`.
 - Copying a message:
   - (CassandraMessageMapper) Read the message first, then first reference the message in `messageIdTable` then
    in `imapUidTable`.
 - Moving a message:
   - (CassandraMessageMapper) Logically copy then delete. A failure in the chain migh lead to duplicated message (present 
   in both source and destination mailbox) as well as different view in IMAP/JMAP.
   - (CassandraMessageIdMapper) First reference the message in `imapUidTable` then in `messageIdTable`.
 - Updating a message flags:
   - (CassandraMessageMapper) First update conditionally the message in `imapUidTable` then in `messageIdTable`.
   - (CassandraMessageIdMapper) First update conditionally the message in `imapUidTable` then in `messageIdTable`.

## Decision

Adopt `imapUidTable` as a source of truth. Because `messageId` allows tracking changes to messages accross mailboxes 
upon copy and moves. Furthermore, that is the table on which conditional flags updates are performed.

All writes will be performed to `imapUidTable` then performed on `messageIdTable` if successful.

We thus need to modify CassandraMessageMapper 'add' + 'copy' to first write to the source of truth (`imapUidTable`)

We can adopt a retry policy of the `messageIdTable` projection update as a mitigation strategy.

Using `imapUidTable` table as a source of truth, we can rebuild the `messageIdTable` projection:

 - Iterating `imapUidTable` entries, we can rewrite entries in `messageIdTable`
 - Iterating `messageIdTable` we can remove entries not referenced in `imapUidTable`
 - Adding a delay and a re-check before the actual fix can decrease the occurrence of concurrency issues

We will expose a webAdmin task for doing this.

## Consequences

User actions concurrent to the inconsistency fixing task could result in concurrency issues. New inconsistencies could be
created. However table of truth would not be impacted hence rerunning the inconsistency fixing task will eventually fix 
all issues.

This task could be run safely online and can be scheduled on a recurring basis outside of peak traffic by an admin to
ensure Cassandra message consistency.

## References

* [Plan for fixing Cassandra ACL inconsistencies](https://github.com/linagora/james-project/pull/3125)

* [General mailing list discussion about inconsistencies](https://www.mail-archive.com/server-dev@james.apache.org/msg64432.html)

* [Pull Request: JAMES-3058 Concurrency testing for fixing Cassandra mailbox inconsistencies](https://github.com/linagora/james-project/pull/3130)

The delay strategy to decrease concurrency issue occurrence is described here.