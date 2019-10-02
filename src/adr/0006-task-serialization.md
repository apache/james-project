# 6. Task serialization

Date: 2019-10-02

## Status

Accepted (lazy consensus)

## Context

By switching the task manager to a distributed implementation, we need to be able to execute a `Task` on any node of the cluster.
We need to have a way to describe the `Task` to be executed and serialize it in order to be able to store it in the `Created` event. Which will be persisted in the Event Store, and will be send in the event bus.

At this point in time a `Task` can contain any arbitrary code. It's not an element of a finite set of actions.

## Decision

 * Create a `Factory` for one `Task`
 * Inject a `Factory` `Registry` via a Guice Module
 * The `Task` `Serialization` will be done in JSON, We will get inspired by `EventSerializer`
 * Every `Task`s should have a specific integration test demonstrating that serialization works
 * Each `Task` is responsible of eventually dealing with the different versions of the serialized information


## Consequences

 * Every `Task`s should be serializable.
 * Every `Task`s should provide a `Factory` which would be responsible to deserialize the task and instantiate it.
 * Every `Factory` should be registered through a Guice module to be created for each project containing a `Factory`
