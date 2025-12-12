# 73. Relying on EventBus for message deletion side effects

Date: 2025-12-12

## Status

Accepted (lazy consensus) & implemented.

## Context

We supported a `DeletionCallback` mechanism that allowed components to execute custom side effects when messages were deleted. These callbacks were invoked synchronously within the deletion execution path of `DeleteMessageListener`.

This mechanism could be unsafe to use in production: implementations of `DeletionCallback` could perform heavy I/O (reading blobs, writing to the Deleted Messages Vault, calling external systems). Running such logic synchronously as part of the event processing, especially upon a high-volume messages deletion, could lead to:
   - slow down other important mailbox events processing,
   - increase user-visible latency e.g. delay OpenSearch indexing
   - risk of timeouts consuming the RabbitMQ message.

We attempted to mitigate this issue for the Deleted Messages Vault, by using a dedicated RabbitMQ work-queue for its deletion side effect.

However, the mitigation was not reusable for other deletion side effects. We need a more robust and general solution that can be used for other deletion side effects.

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
