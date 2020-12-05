# 42. Applicative read repairs (POC mailbox & mailbox-counters)

Date: 2020-09-25

## Status

Adopted (lazy consensus) & implemented

Completes [20. Cassandra Mailbox object consistency](0020-cassandra-mailbox-object-consistency.md),
[23. Cassandra Mailbox Counters inconsistencies](0023-cassandra-mailbox-counters-inconsistencies.md)

## Context

Cassandra eventual consistency is all about "replication", but "denormalization" consistency needs
to be handled at the applicative layer (due to the lack of transactions in a NoSQL database).

In the past we did set up "Solve inconsistency" tasks that can be assimilated to Cassandra repairs. Such
tasks, after being scheduled, ensure that the according entity denormalization is correctly denormalized.

However, the inconsistencies persist between runs. We experienced inconsistencies in some production platform
for both the mailbox entity, and the mailbox counter entity (whose table structure is exposed in
[these](0020-cassandra-mailbox-object-consistency.md), [ADRs](0023-cassandra-mailbox-counters-inconsistencies.md)).
Monitoring is required to detect when to run them and is time consuming for the platform administrator.
Given a large dataset, it could even be impossible to run such tasks in a timely fashion.

Another classic eventual consistency mechanism, that enables auto-healing is read-repair. Randomly piggy back upon reads
synchronous or asynchronous consistency checks. If missed a repair is performed.

In order to achieve denormalization auto-healing, we thus need to implement "applicative read repairs".

## Decision

Provide a Proof of concept for "Applicative read repairs" for the mailbox and mailbox-counters entities.

This enables read path simplification (and performance enhancements) for the mailbox object.

IMAP LIST should not read mailbox counters. This information is uneeded and we should avoid paying the
price of read repairs for this operation.

Provide a comprehensive documentation page regarding "Distributed James consistency model".

## Consequences

The expected **auto-healing** inconsistencies on existing deployments (at a limited configuration cost).
This should ease operation of the Distributed James server.

A configuration for James Distributed server will be added to control read repairs, per entity.

## Alternatives

Cassandra provides some alternative by itself:

 - Secondary indexes avoids the denormalization in the first place. However they are not efficient in
 a distributed environment as each node needs to be queried, which limits ability to scale.
 - Materialized view enables Cassandra to maintain a projection on the behalf of the application,
 coming with an expensive write cost, requiring synchronisation, not fit for complex denormalization
 (like the message one: the primary key of the originating table needs to appear in the materialized
 view primary key). Most of all, the updates are performed asynchronously. This mechanism is considered experimental.
 - Cassandra BATCH suffers from the following downsides:
   - A batch containing conditional updates can only operate within a single partition
   - It is unadvised to update many partitions in a single batch, and keep the cardinality low for performance reasons

BATCH could be a good option to keep tables synchronized, but does not apply to mailboxes (conditional update) nor
counters.

We already propose several tasks to solve denormalization inconsistencies. "Applicative read repairs" should be
seen as a complement to it.

Another classical mechanism in eventual consistent system is called hinted-handoff. It consists at retries
(during a given period) when "replicating" data to other replica. We also already have a similar mechanism
in James as we retry several times failures when writing data to denormalization table. Hard shut-down however
defeats this strategy that is otherwise efficient to limit inconsistencies across denormalization tables.

## References

 - [Read repairs in Cassandra](https://cassandra.apache.org/doc/latest/operating/read_repair.html)
 - [20. Cassandra Mailbox object consistency](0020-cassandra-mailbox-object-consistency.md)
 - [23. Cassandra Mailbox Counters inconsistencies](0023-cassandra-mailbox-counters-inconsistencies.md)
 - [Hinted handoff](https://cassandra.apache.org/doc/latest/operating/hints.html)
 - [This link documents materialized views limitations](https://docs.datastax.com/en/dse/6.0/cql/cql/cql_using/knownLimitationsMV.html)
 - [Materialized views considered experimental](https://www.mail-archive.com/user@cassandra.apache.org/msg54073.html)
 - [CQL Batch](https://docs.datastax.com/en/cql-oss/3.x/cql/cql_reference/cqlBatch.html)

Especially:

```
Materialized View Limitations:

    All updates to the view happen asynchronously unless corresponding view replica is the same node.
    We must do this to ensure availability is not compromised.  It's easy to imagine a worst case
    scenario of 10 Materialized Views for which each update to the base table requires writing to 10
    separate nodes. Under normal operation views will see the data quickly and there are new metrics to
    track it (ViewWriteMetrics).

    There is no read repair between the views and the base table.  Meaning a read repair on the view will
    only correct that view's data not the base table's data.  If you are reading from the base table though,
    read repair will send updates to the base and the view.

    Mutations on a base table partition must happen sequentially per replica if the mutation touches
    a column in a view (this will improve after ticket CASSANDRA-10307)
```

* [JIRA](https://issues.apache.org/jira/browse/JAMES-3407)
* [PR discussing this ADR](https://github.com/apache/james-project/pull/248)