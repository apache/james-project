# 19. Reactor-netty adoption for JMAP server implementation

Date: 2020-02-28

## Status

Accepted (lazy consensus) & implemented

## Context

After adopting the last specifications of JMAP (see 
[new JMAP specifications adoption ADR](https://github.com/apache/james-project/blob/master/src/adr/0018-jmap-new-specs.md)), 
it was agreed that we need to be able to serve both `jmap-draft` and the new `jmap` with a reactive server. 

The current outdated implementation of JMAP in James is currently using a non-reactive [Jetty server](https://www.eclipse.org/jetty/).

There are many possible candidates as reactive servers. Among the most popular ones for Java:

* [Spring](https://spring.io)
* [Reactor-netty](https://github.com/reactor/reactor-netty)
* [Akka HTTP](https://doc.akka.io/docs/akka-http/current/introduction.html)
* ...

## Decision

We decide to use `reactor-netty` for the following reasons:

* It's a reactive server
* It's using [Reactor](https://projectreactor.io/), which is the same technology that we use in the rest of our codebase
* Implementing JMAP does not require high level HTTP server features

## Consequences

* Porting current `jmap-draft` to use a `reactor-netty` server instead of a Jetty server
* The `reactor-netty` server should serve as well the new `jmap` implementation
* We will be able to refactor and get end-to-end reactive operations for JMAP, unlocking performance gains

## References

* JIRA: [JAMES-3078](https://issues.apache.org/jira/browse/JAMES-3078)
* JMAP new specifications adoption ADR: https://github.com/apache/james-project/blob/master/src/adr/0018-jmap-new-specs.md