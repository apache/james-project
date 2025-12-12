# 74. Relying on EventBus for message deletion side effects

Date: 2025-12-12

## Status

Accepted (lazy consensus) & implemented.

## Context

James wants to perform actions upon email deletion, for instance, copying events in the Deleted Message Vault or clearing the corresponding message preview (used to serve an optimized data projection over the JMAP protocol).

Deletion action can be long and are thus in Cassandra and Postgres done asynchronously in the `DeleteMessageListener`.

We encountered issues, most notably with mailbox deletion, which triggered those operations on a message set of message, leading to event processing stalling and eventually timeout.

This had been historically solved in the Distributed server by adding a custom RabbitMQ queue for the deleted message vault to sequence and distribute each single deletion. While it leads to a viable use in production, this approach suffers from the following pitfall:

- It duplicates the Event bus code, used for work queues
- It requires a lot of custom code for adoption in other implementations
- It makes it hard to add other "features" in a composable fashion without duplicating a lot of code

## Decision

We will **remove the `DeletionCallback` interface**.

Instead, **message deletion will always publish a `MessageContentDeletionEvent`** on a dedicated EventBus.

Consumers previously relying on deletion callbacks must now register as EventBus listeners. This ensures that heavy deletion side effect workloads do not block or slow down other important mailbox event processing (such as indexing).

## Consequences

### Pros
- heavy, slow deletion workloads no longer block mailbox operations.
- `DeletionCallback` interface removed, simplifying the codebase.
- leverage existing EventBus features: asynchronous execution, listener isolation, retry and dead-lettering.
- deletion side effects are handled the same way as other mailbox events.

### Cons
- Existing deletion callbacks must migrate to asynchronous listeners.

## References

- ADR-0037 – EventBus design
- [JIRA: JAMES-4154 – Generalization of deletion side effects](https://issues.apache.org/jira/browse/JAMES-4154)
