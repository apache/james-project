# 12. Projections for JMAP Messages

Date: 2019-10-09

## Status

Proposed

Adoption needs to be backed by some performance tests.

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

GetMessages with a limited set of requested properties will no longer result necessarily in full database message read. We
thus expect a significant improvement, for instance when only metadata are requested.

In case of a less than 5% improvement, the code will not be added to the codebase and the proposal will get the status 'rejected'.

## References

 - /get method: https://tools.ietf.org/html/rfc8620#section-5.1
 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2919)
