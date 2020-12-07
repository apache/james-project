# 26. Removing a configured additional MailboxListener

Date: 2020-04-03

## Status

Accepted (lazy consensus)

Not implemented yet.

Superseded by [35. Distributed Mailbox Listener Configuration](0035-distributed-listeners-configuration.md)

## Context

James enables a user to register additional mailbox listeners.

The distributed James server is handling mailbox event processing (mailboxListener execution) using a RabbitMQ work-queue
per listener.

The distributed James server then declares a queue upon start for each one of these user registered listeners, that it
binds to the main event exchange. 

More information about this component, and its distributed, RabbitMQ based implementation, can be found in 
[ADR 0036](0037-eventbus.md).

If the user unconfigures the listener, the queue and the binding are still present but not consumed. This results in 
unbounded queue growth eventually causing RabbitMQ resource exhaustion and failure.

## Vocabulary

A **required group** is a group configured within James additional mailbox listener or statically binded via Guice. We 
should have a queue for that mailbox listener binded to the main exchange.

A **registered group** is a group whose queue exists in RabbitMQ and is bound to the exchange, independently of its James 
usage. If it is required, a consumer will consume the queue. Otherwise the queue might grow unbounded.

## Decision

We need a clear consensus and auditability across the James cluster about **required groups** (and their changes). Thus 
Event sourcing will maintain an aggregate tracking **required groups** (and their changes). Audit will be enabled by 
adding host and date information upon changes. A subscriber will perform changes (binds and unbinds) in registered groups 
following the changes of the aggregate.

Event sourcing is desirable as it allows:
 - Detecting previously removed MailboxListener upon start
 - Audit of unbind decisions
 - Enables writing more complex business rules in the future

The event sourcing system will have the following command:

 - **RequireGroups** the groups that the **EventBus** is starting with.

And the following events:

 - **RequiredGroupAdded** a group is added to the required groups.
 - **RequiredGroupRemoved** a group is removed from the required groups.

Upon start the aggregate will be updated if needed and bindings will be adapted accordingly.

Note that upon failure, registered groups will diverge from required groups. We will add a health check to diagnose 
such issues. Eventually, we will expose a webadmin task to reset registered groups to required groups.

The queues should not be deleted to prevent message loss.

Given a James topology with a non uniform configuration, the effective RabbitMQ routing will be the one of the latest 
started James server.

## Alternatives

We could also consider adding a webadmin endpoint to sanitize eventBus bindings, allowing more predictability than the
above solution but it would require admin intervention.

## References

 - [Discussion](https://github.com/linagora/james-project/pull/3280) around the overall design proposed here.
