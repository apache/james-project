# 12. Projections for JMAP Messages

Date: 2019-10-09

## Status

Accepted

## Context

JMAP core RFC8620 requires that the server responds only properties requested by the client.

James currently computes all of the properties regardless of their cost, and if it had been asked by the client.

Clearly we can save some latencies and resources by avoiding reading/computing expensive properties that had not been explicitly requested by the client.

## Decision

Introduce two new datastructures representing JMAP messages:
 - One with only metadata
 - One with metadata + headers

Given the properties requested by the client, the most appropriate message datastructure will be computed, on top of 
existing message storage APIs that should remain unchanged.

Some performance tests will be run in order to evaluate the improvements.

## Consequences

GetMessages with a limited set of requested properties no longer result necessarily in full database message read. We
thus have a significant improvement, for instance when only metadata are requested.

Given the following scenario played by 5000 users per hour (constant rate)
 - Authenticate
 - List mailboxes
 - List messages in one of their mailboxes
 - Get 10 times the mailboxIds and keywords of the given messages

We went from:
 - A 20% failure and timeout rate before this change to no failure
 - Mean time for GetMessages went from 27 159 ms to 27 ms (1000 time improvment), for all operation from
 27 591 ms to 60 ms (460 time improvment)
 - P99 is a metric that did not make sense because the initial simulation exceeded Gatling (the performance measuring tool 
 we use) timeout (60s) at the p50 percentile. After this proposal p99 for the entire scenario is of 1 383 ms

## References

 - /get method: https://tools.ietf.org/html/rfc8620#section-5.1
 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2919)
