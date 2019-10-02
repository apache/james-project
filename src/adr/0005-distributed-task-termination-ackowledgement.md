# 5. Distributed Task termination ackowledgement

Date: 2019-10-02

## Status

Accepted (lazy consensus)

## Context

By switching the task manager to a distributed implementation, we need to be able to execute a `Task` on any node of the cluster.
We need a way for nodes to be signaled of any termination event so that we can notify blocking clients.

## Decision

 * Creating a `RabbitMQEventHandler` which publish `Event`s pushed to the task manager's event system to RabbitMQ
 * All the events which end a `Task` (`Completed`, `Failed`, and `Canceled`) have to be transmitted to other nodes

## Consequences

 * A new kind of `Event`s should be created: `TerminationEvent` which includes `Completed`, `Failed`, and `Canceled`
 * `TerminationEvent`s will be broadcasted on an exchange which will be bound to all interested components later
 * `EventSourcingSystem.dipatch` should use `RabbitMQ` to dispatch `Event`s instead of triggering local `Listener`s
 * Any node can be notified when a `Task` emits a termination event

