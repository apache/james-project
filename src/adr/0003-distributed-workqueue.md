# 3. Distributed WorkQueue

Date: 2019-10-02

## Status

Accepted (lazy consensus)

Superceded by [16. Distributed WorkQueue](0016-distributed-workqueue.md)

## Context

By switching the task manager to a distributed implementation, we need to be able to run a `Task` on any node of the cluster.

## Decision

  For the time being we will keep the sequential execution property of the task manager.
  This is an intermediate milestone toward the final implementation which will drop this property.

 * Use a RabbitMQ queue as a workqueue where only the `Created` events are pushed into.
   This queue will be exclusive and events will be consumed serially. Technically this means the queue will be consumed with a `prefetch = 1`.
   The queue will listen to the worker on the same node and will ack the message only once it is finished (`Completed`, `Failed`, `Cancelled`).

## Consequences

 * It's a temporary and not safe to use in production solution: if the node promoted to exclusive listener of the queue dies, no more tasks will be run
 * The serial execution of tasks does not leverage cluster scalability.

