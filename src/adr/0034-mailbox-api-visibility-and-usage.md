# 34. Mailbox API visibility and usage

Date: 2020-04-27

## Status

Accepted (lazy consensus) & implemented

## Context

All mailboxes implementations rely on `mailbox-store` module that defines some common tools to implement the `mailbox-api`
(representing the API defining how to use a mailbox). 

For example, a `CassandraMailboxmanager` has to extend `StoreMailboxManager` (that implements `Mailboxmanager` from the 
`mailbox-api`) that requires the implementation of some `Mapper`s.

`Mapper`s are designed to provide low-level functions and methods on mailboxes. It's recurrent that we are tempted in 
James, outside of the `mailbox` modules, to rely on some of those common tools located in `mailbox-store` to have an 
easier access on some user's mailboxes or messages. 

Like for example, using a `Mapper` outside to be able to retrieve a message with only its `MessageId`, which is not 
currently possible at the `Manager`'s level, which tends to violate `mailbox-api`'s role and primary mission.

As a matter of fact, we have currently such uses of `mailbox-store` in James:

* `mailbox-adapter` because `Authenticator` and `Authorizator` are part of the `mailbox-store`

The manager layer do further validation including right checking, event dispatching (resulting in message search index 
indexing, current quota calculation mainly), quota validation. Not relying on the manager layer is thus error prone 
and can lead to security vulnerabilities.

## Decision

We should never rely on classes defined in `mailbox-store` outside of the `mailbox` modules (except on some cases 
limited to the test scope). The right way would be to always rely on the `Manager`s defined in `mailbox-api` module to 
access mailboxes and messages, as the `mailbox-api` module defines the API on how to use a mailbox.

We should ensure the correctness of `Manager`s implementations by providing contract tests and not by sharing abstract 
classes.

Regarding the modules wrongly relying already on `mailbox-store`, we can:

* `mailbox-adapter`: move `Authenticator` and `Authorizator` to `mailbox-api`

## Consequences

We need to introduce some refactorings to be able to rely fully on `mailbox-api` in new emerging cases. For example, 
our `mailbox-api` still lacks APIs to handle messages by their MessageId. It 
creates some issues for rebuilding a single message fast view projection, or the reindexation of a single message.

A refactoring of the session would be thus necessary to bypass such limitation access on a single message without 
knowing its user from the `mailbox-api` module. 

## References

* [Discussions around rebuild a single message fast view projection](https://github.com/linagora/james-project/pull/3035#discussion_r363684700)

* [General mailing list discussion on the session refactoring](https://www.mail-archive.com/server-dev@james.apache.org/msg64120.html)
