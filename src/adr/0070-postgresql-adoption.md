# 68. Native PostgreSQL adoption

Date: 2023-10-31

## Status

Accepted (lazy consensus).

Not implemented yet.

## Context

Today, Apache James is mostly developed towards [the Distributed variant](https://james.staged.apache.org/james-distributed-app/3.8.0/index.html)
which targets big enterprises/organizations with a big and scalable deployment.

Meanwhile, there are still great needs for small/medium mail server deployments in the market.

However, nowadays, Apache James is not really great at serving that small/medium deployments. Obviously, Distributed James with the shiny big data
technologies like Apache Cassandra, OpenSearch, RabbitMQ... is too costly for that size of deployment. JPA James should be the feasible variant that
could fit those deployments. Despite the JPA implementation allowing to deploy James on top of various popular RDBMS which is useful, JPA with the
Object Relational Mapping nature which makes optimizing SQL queries hard and a backed JDBC blocking driver is usually a bad fit for applications requiring high performance like a mail server.
Besides that, JPA as an abstraction layer prevents James from using advanced features of a given RDBMS e.g. search engine, or blob storage...
From a development aspect, bad developing experience with JPA also limited James community from contributing more to JPA James e.g. JMAP is not supported for JPA James.

It has been [a long-term desire of the James community](https://issues.apache.org/jira/browse/JAMES-2586) to have a James variant that fit small/medium deployments without relying on JPA.

## Decision

We would implement a PostgresSQL-specific backend.

PostgreSQL is a rock-solid well-known, proven, and mature open source relational database with great adoption on the market.
Besides a traditional SQL database, PostgreSQL could serve as [a blob storage](https://www.postgresql.org/docs/current/largeobjects.html), [a search engine](https://www.postgresql.org/docs/current/textsearch.html) and [a message queue](https://www.postgresql.org/docs/current/sql-notify.html).

We would start implementing James mailbox backed by PostgreSQL:
- We implement it step by step on a feature branch of James called `postgresql`.
- We start from a copy of JPA modules into new Postgres ones, then migrate code step by step from JPA to Postgresql implementation. This means that during the dev process, both JPA and Postgresql implementation will cohabit until we finally get rid of the JPA code.
- The [r2dbc-postgresql](https://github.com/pgjdbc/r2dbc-postgresql) reactive client would be used, alongside [jOOQ](https://www.jooq.org/) (which supports `r2dbc-postgresql` under the hood) for higher level SQL queries.
- We at first would start with a simple connection pool implementation: 1 fixed Postgresql connection per domain.
  The connection pool implementation could be backed by [r2dbc-pool](https://github.com/r2dbc/r2dbc-pool).

- Row Level Security usage would be optional, which enforces strict tenant isolation: domain A won't access domain B data even if we screw up James access control layer.
- We would need to do some benchmarks along the road to prove the gains we get from the Postgres implementation compared to the JPA one.

Once the Postgres mailbox implementation is stable, we will continue to invest in using Postgres on other blocks:
- Blob storage implementation
- Full-text search implementation
- Message queue implementation

## Consequences

- We expect to have a suitable James for the small/medium deployments with PostgreSQL as an all-in-one solution.
- We expect the native PostgreSQL mailbox implementation with a non-blocking driver to have a better performance than the JPA one.
- Row Level Security would bring more strict tenant isolation as an additional access control layer.
- We accept the risk of working with the freemium model of jOOQ in exchange for a better development experience.
  Some known concerns so far with jOOQ:
    - FOSS jOOQ does not support proprietary databases e.g. Oracle.
    - FOSS jOOQ only supports a few latest version of open source databases.
  
      e.g. Given today the latest version of PostgreSQL is 16, FOSS jOOQ only supports PostgreSQL 15 and 16.
  
    - FOSS jOOQ releases no more patches for old LTS JDK.
  
      e.g. Given today jOOQ officially work with Java 17 with the latest jOOQ 3.18, meanwhile for FOSS jOOQ the latest version supports Java 11 stops at 3.16.
      To avoid those limit, a fee is needed to pay to use jOOQ's premium features.

## Alternatives

- Use a dynamic PostgreSQL connection pool e.g. 100 dynamic connections for 1000 domains.

  James maintaining a lot of connections to PostgreSQL at the same time could degrade the performance.
  However, knowing that we would not reach that many domains for a single James deployment soon, we decided to implement a simpler connection pool first.

- Once we find out that we can not live with jOOQ FOSS limitation, we could consider other drivers:

  - [r2dbc-postgresql](https://github.com/pgjdbc/r2dbc-postgresql). Library requires writing raw SQL queries, less convenient develop experience, but it should do the job without any limitation.
  - [doobie](https://tpolecat.github.io/doobie/). A Scala SQL driver.

## References

- [JIRA](https://issues.apache.org/jira/browse/JAMES-2586)
- [Mailing list discussion](https://www.mail-archive.com/server-dev@james.apache.org/msg73461.html)
- [PostgreSQL](https://www.postgresql.org/)
- [r2dbc-postgresql](https://github.com/pgjdbc/r2dbc-postgresql)
- [Jooq](https://www.jooq.org/)
