# 16. Distributed WorkQueue

Date: 2019-12-03

## Status

Accepted (lazy consensus)

Supercedes [3. Distributed WorkQueue](0003-distributed-workqueue.md)

## Context

By switching the task manager to a distributed implementation, we need to be able to run a `Task` on any node of the cluster.

## Decision

  For the time being we will keep the sequential execution property of the task manager.
  This is an intermediate milestone toward the final implementation which will drop this property.

 * Use a RabbitMQ queue as a workqueue where only the `Created` events are pushed into.
   Instead of using the brittle exclusive queue mechanism described in [3. Distributed WorkQueue](0003-distributed-workqueue.md), we will
   now use the natively supported [Single Active Consumer](https://www.rabbitmq.com/consumers.html#single-active-consumer) mechanism. 


## Consequences

 * This solution is safer to use in production: if the active consumer dies, an other one is promoted instead.
 * This change needs RabbitMQ version to be at least 3.8.0.
 * The serial execution of tasks still does not leverage cluster scalability.
