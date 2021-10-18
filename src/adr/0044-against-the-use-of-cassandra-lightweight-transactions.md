# 44. Against the use of Lightweight Transaction in Cassandra code base

Date: 2020-11-11

## Status

Accepted (lazy consensus).

Scope: Distributed James

Implemented.

## Context

As any kind of server James needs to provide some level of consistencies.

Strong consistency can be achieved with Cassandra by relying on LightWeight transactions. This enables
optimistic transactions on a single partition key.

Under the hood, Cassandra relies on the PAXOS algorithm to achieve consensus across replica allowing us
to achieve linearizable consistency at the entry level. To do so, Cassandra tracks consensus in a system.paxos
table. This `system.paxos` table needs to be checked upon reads as well in order to ensure the latest state of the ongoing
consensus is known. This can be achieved by using the SERIAL consistency level.

Experiments on a distributed James cluster (4 James nodes, having 4 CPU and 8 GB of RAM each, and a 3 node Cassandra 
cluster of 32 GB of RAM, 8 CPUs, and SSD disks) demonstrated that the system.paxos table was by far the most read 
and compacted table (ratio 5). 

The table triggering the most reads to the `system.paxos` table was the `acl` table. Deactivating LWT on this table alone
(lightweight transactions & SERIAL consistency level) enabled an instant 80% throughput, latencies reductions
as well as softer degradations when load breaking point is exceeded.

## Decision

Rely on `event sourcing` to maintain a projection of ACLs that do not rely on LWT or SERIAL consistency level. 

Event sourcing is thus responsible for handling concurrency and race conditions as well as governing denormalization
for ACLs. It can be used as a source of truth to re-build ACL projections. 

Note that the ACL projection tables can end up being out of synchronization from the aggregate but we still have a 
non-questionable source of truth handled via event sourcing.

## Consequences

We expect a better load handling, better response time, and cheaper operation costs for Distributed James while not
compromising the data safety of ACL operations.

ACL updates being a rare operation, we do not expect significant degradation of write performance by relying on 
`eventSourcing`.

We need to implement a corrective task to fix the ACL denormalization projections. Applicative read repairs could be 
implemented as well, offering both diagnostic and on-the-fly corrections without admin actions (a low probability should
however be used as loading an event sourcing aggregate is not a cheap thing).

## Complementary work

There are several other places where we rely on Lightweight transaction in the Cassandra code base and 
that we might want to challenge. We need to trigger mailing list discussions, and issue related ADRs:

 - `users` we rely on LWT for throwing "AlreadyExist" exceptions. LWT are likely unnecessary as the webadmin 
presentation layer is offering an idempotent API (and silents the AlreadyExist exceptions). Only the CLI
(soon to be deprecated for Guice products) makes this distinction. Discussions have started on the topic and a proof of
concept is available.
 - `domains` we rely on LWT for throwing "AlreadyExist" exceptions. LWT are likely unnecessary as the webadmin 
presentation layer is offering an idempotent API (and silents the AlreadyExist exceptions). Only the CLI
(soon to be deprecated for Guice products) makes this distinction. Discussions have started on the topic and a proof of
concept is available.
 - `mailboxes` relies on LWT to enforce name unicity. We hit the same pitfalls than for ACLs as this is a very often
 read table (however mailboxes of a given user being grouped together, primary key read are more limited hence this is
 less critical). Similar results could be expected. Discussions on this topic have not been started yet. Further
 impact studies on performance need to be conducted.
 - `messages` as flags update is so far transactional. However, by better relying on the table structure used to store 
flags we could be relying on Cassandra to solve data race issues for us. Note also that IMAP CONDSTORE extension is not 
implemented, and might be a non-viable option performance-wise. We might choose to favor performance other 
transactionality on this topic. Discussions on this topic have not started yet.

LWT are required for `eventSourcing`. As event sourcing usage is limited to low-usage use cases, the performance
degradations are not an issue. Note that `eventSourcing` could be used to maintain LWT free projections for some 
(infrequent writes) of the LWT mentioned before, thus avoiding performance issues while keeping a consistent source of 
truth (see decision for ACL).

LWT usage is required to generate `UIDs`. As append message operations tend to be limited compared to
message update operations, this is likely less critical. UID generation could be handled via alternative systems,
past implementations have been conducted on ZooKeeper.

If not implementing IMAP CONDSTORE, generation of IMAP `MODSEQ` likely no longer makes sense. As such the fate of
`MODSEQ` is linked to decisions on the `message` topic.

Use of other technologies for monotic integer generation could be investigated like [atomix.io](https://atomix.io/) 
or [Zookeeper](https://zookeeper.apache.org/).

Similarly, LWT are used to try to keep the count of emails in MailRepository synchronize. Such a usage is non-performance
critical for a MDA (Mail Delivery Agent) use case but might have a bigger impact for MTA (Mail Transfer Agent). No
discussion or work have been started on the topic.

Other usages of LWT include Sieve script management, initialization of the RabbitMQMailQueue browse start and other
low-impact use cases.

## References

* [Original pull request exploring the topic](https://github.com/apache/james-project/pull/255):
`JAMES-3435 Cassandra: No longer rely on LWT for domain and users`
* [JIRA ticket](https://issues.apache.org/jira/browse/JAMES-3435)
* [Pull request abandoning LWT on reads for mailbox ACL](https://github.com/linagora/james-project/pull/4103)
* [ADR-42 Applicative read repairs](https://github.com/apache/james-project/blob/master/src/adr/0042-applicative-read-repairs.md)
* [ADR-21 ACL inconsistencies](https://github.com/apache/james-project/blob/master/src/adr/0021-cassandra-acl-inconsistency.md)
* [Buggy IMAP CONDSTORE](https://issues.apache.org/jira/browse/JAMES-2055)
* [Link to the Mailing list thread discussing this ADR](https://www.mail-archive.com/server-dev@james.apache.org/msg69124.html)
* [The pull request for this ADR might include useful information as well](https://github.com/apache/james-project/pull/271)
