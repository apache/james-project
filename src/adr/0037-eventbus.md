# 37. Event bus

Date: 2020-05-05

## Status

Accepted (lazy consensus) & implemented

## Context

Many features rely on behaviors triggered by interactions with the mailbox API main interfaces (`RightManager`,
`MailboxManager`, `MessageManager`, `MessageIdManager`). We need to provide a convenient extension mechanism for 
organizing the execution of these behaviours, provide retries and advanced error handling.

Also, protocols enable notifications upon mailbox modifications. This is for instance the case for `RFC-2177 IMAP IDLE`,
leveraged for `RFC-3501 IMAP unsolicitated notifications` when selecting a Mailbox, as well as maintaining the 
`IMAP Message Sequence Number <-> Unique IDentifier` MSN <-> UID mapping. Changes happening for a specific entity 
(mailbox) need to be propagated to the relevant listeners.

## Decision

James mailbox component, a core component of James handling the storage of mails and mailboxes, should use an event 
driven architecture. 

It means every meaningful action on mailboxes or messages triggers an event for any component to react to that event.

`MailboxListener` allows executing actions upon mailbox events. They could be used for a wide variety of purposes, like 
enriching mailbox managers features or enabling user notifications upon mailboxes operations performed by other devices 
via other protocol sessions.

Interactions happen via the managers (`RightManager`, `MailboxManager`, `MessageManager`, `MessageIdManager`) which emit an
event on the `EventBus`, which will ensure the relevant `MailboxListener`s will be executed at least once.

`MailboxListener` can be registered in a work queue fashion on the `EventBus`. Each work queue corresponds to a given 
MailboxListener class with the same configuration, identified by their group. Each event is executed at least once
within a James cluster, errors are retried with an exponential back-off delay. If the execution keeps failing, the event
 is stored in `DeadLetter` for later reprocessing, triggered via WebAdmin.

Guice products enable the registration of additional mailbox listeners. A user can furthermore define its own 
mailboxListeners via the use of `extension-jars`.

MailboxListener can also be registered to be executed only on events concerning a specific entity (eg. a mailbox). The 
`registrationKey` is identifying entities concerned by the event. Upon event emission, the manager will indicate the 
`registrationKey` this event should be sent to. A mailboxListener will thus only receive the event for the registration 
key it is registered to, in an at least once fashion.

## Consequences

We need to provide an `In VM` implementation of the EventBus for single server deployments.

We also need to provide [a distributed event bus implementation](0038-distributed-eventbus.md).

## Current usages

The following features are implemented as Group mailbox listeners:

 - Email indexing in Lucene or ElasticSearch
 - Deletion of mailbox annotations
 - Cassandra Message metadata cleanup upon deletion
 - Quota updates
 - Quota indexing
 - Over Quota mailing
 - SpamAssassin Spam/Ham reporting
 
## References

* [JIRA](https://issues.apache.org/jira/browse/MAILBOX-364)
