# 38. Distributed Event bus

Date: 2020-05-25

## Status

Accepted (lazy consensus) & implemented

## Context

Read [Event Bus ADR](0037-eventbus.md) for context.

Given several James servers, we need them to share a common EventBus.

This:
 - Ensures a better load balancing for `group mailbox listners`.
 - Is required for correctness of notifications (like IMAP IDLE).

## Decision

Provide a distributed implementation of the EventBus leveraging RabbitMQ.

Events are emitted to a single Exchange.

Each group will have a corresponding queue, bound to the main exchange, with a default routing key. Each eventBus
will consume this queue and execute the relevant listener, ensuring at least once execution at the cluster level.

Retries are managed via a dedicated exchange for each group: as we need to count retries, the message headers need to 
be altered and we cannot rely on rabbitMQ build in retries. Each time the execution fails locally, a new event is emitted 
via the dedicated exchange, and the original event is acknowledged.

Each eventBus will have a dedicated exclusive queue, bound to the main exchange with the `registrationKeys` used by local 
notification mailboxListeners (to only receive the corresponding subset of events). Errors are not retried for 
notifications, failures are not persisted within `DeadLetter`, achieving at most once event delivery.

## Related ADRs

The implementation of the the distributed EventBus suffers from the following flows:

 - [Removing a configured additional MailboxListener](0026-removing-configured-additional-mailboxListeners.md)
 - [Distributed Mailbox Listeners Configuration](0035-distributed-listeners-configuration.md) also covers more in details
 topology changes and supersedes ADR 0026. 
 
The following enhancement have furthermore been contributed:

 - [EventBus error handling upon dispatch](0027-eventBus-error-handling-upon-dispatch.md)

## References

* [JIRA 1](https://issues.apache.org/jira/browse/MAILBOX-367)
* [JIRA 2](https://issues.apache.org/jira/browse/MAILBOX-368)
* [JIRA 3](https://issues.apache.org/jira/browse/MAILBOX-371)