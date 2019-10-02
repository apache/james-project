# 7. Distributed Task cancellation

Date: 2019-10-02

## Status

Accepted (lazy consensus)

## Context

A `Task` could be run on any node of the cluster. To interrupt it we need to notify all nodes of the cancel request.

## Decision

* We will add an EventHandler to broadcast the `CancelRequested` event to all the workers listening on a RabbitMQ broadcasting exchange.

* The `TaskManager` should register to the exchange and will apply `cancel` on the `TaskManagerWorker` if the `Task` is waiting or in progress on it.

## Consequences

* The task manager's event system should be bound to the RabbitMQ exchange which publish the `TerminationEvent`s
