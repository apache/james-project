# 8. Distributed Task await

Date: 2019-10-02

## Status

Accepted (lazy consensus)

## Context

By switching the task manager to a distributed implementation, we need to be able to `await` a `Task` running on any node of the cluster.

## Decision

 * Broadcast `Event`s in `RabbitMQ`

## Consequences

 * `RabbitMQTaskManager` should broadcast termination `Event`s (`Completed`|`Failed`|`Canceled`)
 * `RabbitMQTaskManager.await` should: first, check the `Task`'s state; and if it's not terminated, listen to RabbitMQ
 * The await should have a timeout limit

