# 21. Cassandra ACL inconsistencies

Date: 2020-02-27

## Status

Proposed

Not implemented yet.

## Context

Mailboxes ACLs are denormalized in Cassandra in order to:

 - given a mailbox, list its ACL (enforcing rights for example)
 - discover which mailboxes are delegated to a given user (used to list mailboxes)

Here is the tables organisation:

 - `acl` stores the ACLs of a given mailbox
 - `UserMailboxACL` stores which mailboxes had been delegated to which user

Failures during the denormalization process will lead to inconsistencies between the two tables.

This can lead to the following user experience:

```
ALICE delegates her INBOX mailbox to BOB
The denormalisation process fails
ALICE INBOX does not appear in BOB mailbox list

Given a delegated mailbox INBOX.delegated
ALICE undo the sharing of her INBOX.delegated mailbox
The denormalisation process fails
ALICE INBOX.delegated mailbox still appears in BOB mailbox list
When BOB tries to select it, he is being denied
```

## Decision

We can adopt a retry policy of the `UserMailboxACL` projection update as a mitigation strategy.

Using `acl` table as a source of truth, we can rebuild the `UserMailboxACL` projection:

 - Iterating `acl` entries, we can rewrite entries in `UserMailboxACL`
 - Iterating `UserMailboxACL` we can remove entries not referenced in `acl`
 - Adding a delay and a re-check before the actual fix can decrease the occurrence of concurrency issues

We will expose a webAdmin task for doing this.

## Consequences

User actions concurrent to the inconsistency fixing task could result in concurrency issues. New inconsistencies could be
created. However table of truth would not be impacted hence rerunning the inconsistency fixing task will eventually fix 
all issues.

This task could be run safely online and can be scheduled on a recurring basis outside of peak traffic by an admin to
ensure Cassandra acl consistency.

## References

* [Plan for fixing Cassandra ACL inconsistencies](https://github.com/linagora/james-project/pull/3125)

* [General mailing list discussion about inconsistencies](https://www.mail-archive.com/server-dev@james.apache.org/msg64432.html)

* [Pull Request: JAMES-3058 Concurrency testing for fixing Cassandra mailbox inconsistencies](https://github.com/linagora/james-project/pull/3130)

The delay strategy to decrease concurrency issue occurrence is described here.