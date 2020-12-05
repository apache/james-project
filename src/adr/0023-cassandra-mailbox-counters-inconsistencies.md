# 23. Cassandra Mailbox Counters inconsistencies

Date: 2020-03-07

## Status

Accepted (lazy consensus) & implemented

## Context

Cassandra maintains a per mailbox projection for message count and unseen message count.

As with any projection, it can go out of sync, leading to inconsistent results being returned to the client, which is not acceptable.

Here is the table organisation:

 - `mailbox` Lists the mailboxes
 - `messageIdTable` Holds mailbox and flags for each message, lookup by mailbox ID + UID
 - `imapUidTable` Holds mailbox and flags for each message, lookup by message ID and serves as a source of truth
 - `mailboxCounters` Holds messages count and unseen message count for each mailbox.
 
Failures during the denormalization process will lead to inconsistencies between the counts and the content of `imapUidTable`

This can lead to the following user experience:

 - Invalid message count can be reported in the Mail User Agent (IMAP & JMAP)
 - Invalid message unseen count can be reported in the Mail User Agent (IMAP & JMAP)

## Decision

Implement a webadmin exposed task to recompute mailbox counters.

This endpoints will:

 - List existing mailboxes
 - List their messages using `messageIdTable`
 - Check them against their source of truth `imapUidTable`
 - Compute mailbox counter values
 - And reset the value of the counter if needed in `mailboxCounters`

## Consequences

This endpoint is subject to data races in the face of concurrent operations. Concurrent increments & decrements will be 
ignored during a single mailbox processing. However the source of truth is unaffected hence, upon rerunning the task, 
the result will be eventually correct. To be noted that Cassandra counters can't be reset in an atomic manner anyway.

We rely on the "listing messages by mailbox" projection (that we recheck). Missing entries in there will
be ignored until the given projection is healed (currently unsupported). 

We furthermore can piggy back a partial check of the message denormalization described in 
[this ADR](0021-cassandra-acl-inconsistency.md) upon counter recomputation (partial because 
we cannot detect missing entries in the "list messages in mailbox" denormalization table)

## References

* [Plan for fixing Cassandra ACL inconsistencies](https://github.com/linagora/james-project/pull/3125)

* [General mailing list discussion about inconsistencies](https://www.mail-archive.com/server-dev@james.apache.org/msg64432.html)

* [JAMES-3105 Related JIRA](https://issues.apache.org/jira/browse/JAMES-3105)

* [Pull Request: JAMES-3105 Corrective task for fixing mailbox counters](https://github.com/linagora/james-project/pull/3185)