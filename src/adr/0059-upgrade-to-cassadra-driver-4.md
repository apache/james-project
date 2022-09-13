# 59. Upgrade to Cassandra driver 4

Date: 2022-09-13

## Status

Accepted (lazy consensus).

Implemented. 

## Context

Cassandra driver 3 latest release took pace in 201X. An outdated dependency pauses a security risk.

Moreover Cassandra driver 4:
 - Have prime reactive support and do not need to pass through an intermediate "CompletableFuture"
 representation which is more optimised.
 - In Cassandra driver 3, paging is blocking. Handling paging the reactive way means we do not 
 need to aggressively switch the processing to an elastic thread, which helps keeping the count of 
 threads low.
 - Cassandra driver 4 has built in support for advanced driver configuration tuning without the 
 need for the application to programmatically configure the driver. This means great flexibility without
 the need for the application to proxy the driver configuration parameters.

## Decision

Upgrade James to Cassandra driver 4.

Handle Cassandra requests callbacks on the Cassandra driver thread.

## Consequences

We expect a performance enhancement for native reactive support from the Cassandra driver, as well
as a better thread handling which translate to improved latencies/throughput.

This code change needs a rewrite of all the Cassandra data access layer.

Associated risk:
 - Blocking on the Cassandra driver thread is prohibited. Tools like Blockhound can be adapted to 
 mitigate this.
 - Blocking a Cassandra request from within a Cassandra driver thread result in a request timeout.
 - Long running computation should be switched to other threads as it can prevent other Cassandra 
 requests from being executed.
 
Mechanisms related to Cassandra driver configuration will need to be reworked. This leads to a breaking
change but our Cassandra driver configuration will be simpler and more standard.

## References

- [JIRA JAMES-3774](https://issues.apache.org/jira/browse/JAMES-3774)
- [Cassandra driver upgrade instructions](https://docs.datastax.com/en/developer/java-driver/4.0/upgrade_guide/)
- [BlockHound](https://github.com/reactor/BlockHound)
