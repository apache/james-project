# 33. Use scala in event sourcing modules

Date: 2019-12-13

## Status

Accepted (lazy consensus) & implemented

## Context

At the time being James use the scala programming language in some parts of its code base, particularily for implementing the Distributed Task Manager,
which uses the event sourcing modules.

The module `event-store-memory` already uses Scala.

## Decision

What is proposed here, is to convert in Scala the event sourcing modules.
The modules concerned by this change are:
  -  `event-sourcing-core`
  -  `event-sourcing-pojo`
  -  `event-store-api`
  -  `event-store-cassandra`

## Rationales

This will help to standardize the `event-*` modules as `event-store-memory` is already written in Scala.
This change will avoid interopability concerns with the main consumers of those modules which are already written in Scala: see the distributed task manager.
In the long run this will allow to have a stronger typing in those parts of the code and to have a much less verbose code.

## Consequences

We will have to mitigate the pervading of the Scale API in the Java code base by implementing Java facade.

## References

* [JIRA](https://issues.apache.org/jira/browse/JAMES-3009)