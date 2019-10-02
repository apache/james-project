# 2. Make TaskManager Distributed

Date: 2019-10-02

## Status

Accepted (lazy consensus)

## Context

In order to have a distributed version of James we need to have an homogeneous way to deal with `Task`.

Currently, every James nodes of a cluster have their own instance of `TaskManager` and they have no knowledge of others, making it impossible to orchestrate task execution at the cluster level.
Tasks are scheduled and ran on the same node they are scheduled.

We are also unable to list or access to the details of all the `Task`s of a cluster.

## Decision

Create a distribution-aware implementation of `TaskManager`.

## Consequences

 * Split the `TaskManager` part dealing with the coordination (`Task` management and view) and the `Task` execution (located in `TaskManagerWorker`)
 * The distributed `TaskManager` will rely on RabbitMQ to coordinate and the event system to synchronize states
