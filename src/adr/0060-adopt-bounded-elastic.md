# 60. Adopt bounded elastic

Date: 2022-09-13

## Status

Accepted (lazy consensus).

Implemented. 

## Context

See description of blocking vs reactive programming odel described in [ADR-57](0057-reactive-imap.md).

Sometimes reactive code needs to execute blocking tasks. 

 - This is the case when inter-operating with blocking libraries
 - James reactive adoption is progressive, some parts of our application
 are thus not reactive yet, which can result in reactive code calling
 blocking code calling reactive code.
 
Historically James used the elastic scheduler for scheduling such blocking calls. This scheduler
starts a thread for each task submitted (and attempt to reuse threads when possible) and results
in high thread count.

That is why project reactor deprecated the elastic scheduler in favor of bounded-elastic (similar to
a thread pool).

## Decision

Migrate from elastic scheduler to bounded-elastic scheduler.

## Consequences

We expect a reduction in used threads under load from such a change.

Also, getting rid of elastic scheduler might be a requirement to upgrade to reactor 3.5 upward.

Associated risk:
 - In some places, James protocol code is reactive, calls blocking intermediates API, to 
 end up calling reactive data access code. This results in nested blocking calls. Nested blocking
 calls, when using the same scheduler with a bounded thread cap, can result in a dead lock.

To prevent such a dead-lock code executed on bounded-elastic should not depend on scheduling nested
blocking call on bounded-elastic scheduler for its completion. We can thus avoid such a situation by 
using a scheduler dedicated by nested blocking calls.

## Alternatives

Alternatives to the "blocking call wrapper" scheduler described above includes a full reactive 
migration for Apache James (ie no blocking calls meaning no nested blocking calls).

While this clearly is the target such a work is not realistically done within a limited time frame. 
As such we need a transitional model allowing reactive code to inter-operate with legacy blocking 
James code.

## References

- [JIRA JAMES-3773](https://issues.apache.org/jira/browse/JAMES-3773)
- [Reactor deprecating elastic scheduler](https://github.com/reactor/reactor-core/issues/1893)
