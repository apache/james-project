# 12. Projections for JMAP Messages

Date: 2019-10-09

## Status

Accepted (lazy consensus)

## Context

JMAP core RFC8620 requires that the server responds only properties requested by the client.

James currently computes all of the properties regardless of their cost, and if it had been asked by the client.

Clearly we can save some latencies and resources by avoiding reading/computing expensive properties that had not been explicitly requested by the client.

This is furthermore an opportunity to conform to /get behavior of released RFC-8620

## Decision

Introduce two new datastructures representing JMAP messages:
 - One with only metadata
 - One with metadata + headers

Given the properties requested by the client, the most appropriate message datastructure will be returned, on top of 
existing message storage APIs that should remain unchanged.

Some performance tests will be run in order to evaluate the improvements.

## Consequences

Fields that were previously returned in Messages might not be returned anymore if not explicitly asked by the client.

In case of a less than 5% improvement, the code will not be added to the codebase and the proposal will get the status 'rejected'.

## References

 - /get method: https://tools.ietf.org/html/rfc8620#section-5.1
 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2919)
