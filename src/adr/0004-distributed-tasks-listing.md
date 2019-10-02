# 4. Distributed Tasks listing

Date: 2019-10-02

## Status

Accepted (lazy consensus)

## Context

By switching the task manager to a distributed implementation, we need to be able to `list` all `Task`s running on the cluster.

## Decision

 * Read a Cassandra projection to get all `Task`s and their `Status`

## Consequences

 * A Cassandra projection has to be done
 * The `EventSourcingSystem` should have a `Listener` updating the `Projection`
