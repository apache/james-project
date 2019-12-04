# 15. FileMailQueue deprecation

Date: 2019-12-04

## Status

Proposed

## Context

James offers several implementation for MailQueue, a component allowing asynchronous mail processing upon smtp mail 
reception. These includes:
 - Default embedded ActiveMQ mail queue implementation, leveraging the JMS APIs and using the filesystem.
 - RabbitMQMailQueue allowing several James instances to share their MailQueue content.
 - And FileMailQueue directly leveraging the file system.

We introduced a junit5 test contract regarding management features, concurrency issues, and FileMailQueue do not meet this 
contract. This results in some tests being disabled and in an unstable test suite.

FileMailQueue tries to implement a message queue within James code, which does not really makes sense as some other projects
already provides one.

## Decision

Deprecate FileMailQueue components.

Disable FileMailQueue tests.

Target a removal as part of 3.6.0.

## Consequences

FileMailQueue is not exposed to the end user, be it over Spring or Guice, the impact of this deprecation + removal should
be limited.

We also expect our test suite to be more stable.

## Reference

Issues listing FileMailQueue defects:

 - https://issues.apache.org/jira/browse/JAMES-2298 Unsupported remove management feature
 - https://issues.apache.org/jira/browse/JAMES-2954 Incomplete browse implementation + Mixing concurrent operation might lead to a deadlock and missing fields
 - https://issues.apache.org/jira/browse/JAMES-2979 dequeue is not thread safe
