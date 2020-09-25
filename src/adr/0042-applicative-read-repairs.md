# 42. Applicative read repairs (POC mailbox & mailbox-counters)

Date: 2020-09-25

## Status

Adopted (lazy consensus)

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

Another classic eventual consistency mechanism, that enables auto-healing and completes

In order to achieve denormalization auto-healing, we thus need to implement "applicative read repairs".

## Decision

Provide a Proof of concept for "Applicative read repairs" for the mailbox and mailbox-counters entities.

This enables read path simplification (and performance enhancements) for the mailbox object.

IMAP LIST should not read mailbox counters. This information is uneeded and we should avoid paying the
price of read repairs for this operation.

Provide a comprehensive documentation page regarding "Distributed James consistency model".

## Consequences

The expect **auto-healing** inconsistencies on existing deployments (at a limited configuration cost).
This should ease operation of the Distributed James server.

A configuration for James Distributed server will be added to control read repairs, per entity.

## Alternatives

We already propose several tasks to solve denormalization inconsistencies. "Applicative read repairs" should be
seen as a complement to it.

Another classical mechanism in eventual consistent system is called hinted-handoff. It consist at retries
(during a given period) when "replicating" data to other replica. We also already have a similar mechanism
in James as we retry several times failures when writing data to denormalization table. Hard shut-down however
defeats this strategy that is otherwise efficient to limit inconsistencies across denormalization tables.

## References

 - [Read repairs in Cassandra](https://cassandra.apache.org/doc/latest/operating/read_repair.html)
 - [20. Cassandra Mailbox object consistency](0020-cassandra-mailbox-object-consistency.md)
 - [23. Cassandra Mailbox Counters inconsistencies](0023-cassandra-mailbox-counters-inconsistencies.md)
 - [Hinted handoff](https://cassandra.apache.org/doc/latest/operating/hints.html)
