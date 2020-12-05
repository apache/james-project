# 20. Cassandra Mailbox object consistency

Date: 2020-02-27

## Status

Accepted (lazy consensus) & implemented

## Context

Mailboxes are denormalized in Cassandra in order to access them both by their immutable identifier and their mailbox 
path (name):

 - `mailbox` table stores mailboxes by their immutable identifier
 - `mailboxPathV2` table stores mailboxes by their mailbox path

We furthermore maintain two invariants on top of these tables:
 - **mailboxPath** unicity. Each mailbox path can be used maximum once. This is ensured by writing the mailbox path first
 using Lightweight Transactions.
 - **mailboxId** unicity. Each mailbox identifier is used by only a single path. We have no real way to ensure a given mailbox
 is not referenced by two paths.

Failures during the denormalization process will lead to inconsistencies between the two tables.

This can lead to the following user experience:

```
BOB creates mailbox A
Denormalization fails and an error is returned to A

BOB retries mailbox A creation
BOB is being told mailbox A already exist

BOB tries to access mailbox A
BOB is being told mailbox A does not exist
```

## Decision

We should provide an offline (meaning absence of user traffic via for exemple SMTP, IMAP or JMAP) webadmin task to 
solve mailbox object inconsistencies.

This task will read `mailbox` table and adapt path registrations in `mailboxPathV2`:
 - Missing registrations will be added
 - Orphan registrations will be removed
 - Mismatch in content between the two tables will require merging the two mailboxes together.

## Consequences

As an administrator, if some of my users reports the bugs mentioned above, I have a way to sanitize my Cassandra 
mailbox database.

However, due to the two invariants mentioned above, we can not identify a clear source of trust based on existing 
tables for the mailbox object. The task previously mentioned is subject to concurrency issues that might cancel 
legitimate concurrent user actions.

Hence this task must be run offline (meaning absence of user traffic via for exemple SMTP, IMAP or JMAP). This can be
achieved via reconfiguration (disabling the given protocols and restarting James) or via firewall rules.

Due to all of those risks, a Confirmation header `I-KNOW-WHAT-I-M-DOING` should be positioned to 
`ALL-SERVICES-ARE-OFFLINE` in order to prevent accidental calls.

In the future, we should revisit the mailbox object data-model and restructure it, to identify a source of truth to 
base the inconsistency fixing task on. Event sourcing is a good candidate for this.

## References

* [JAMES-3058 Webadmin task to solve Cassandra Mailbox inconsistencies](https://issues.apache.org/jira/browse/JAMES-3058)

* [Pull Request: mailbox-cassandra utility to solve Mailbox inconsistency](https://github.com/linagora/james-project/pull/3110)

* [Pull Request: JAMES-3058 Concurrency testing for fixing Cassandra mailbox inconsistencies](https://github.com/linagora/james-project/pull/3130)

This [thread](https://github.com/linagora/james-project/pull/3130#discussion_r383349596) provides significant discussions leading to this Architecture Decision Record

* [Discussion on the mailing list](https://www.mail-archive.com/server-dev@james.apache.org/msg64432.html)