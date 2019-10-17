# 10 Disable ElasticSearch dynamic mapping

Date: 2019-10-17

## Status

Proposed

Additional performance testing is required for adoption.

## Context

Our queries are mostly bounded to a mailbox or an user. We can easily
limit the number of ElasticSearch nodes involved in a given query by
grouping the underlying documents on the same node using a routingKey.

Without routing key, each shard needs to execute the query. The coordinator
needs also to be waiting for the slowest shard.

Using the routing key unlocks significant throughput enhancement (proportional
to the number of shard) and also a possible high percentile latencies enhancement.
This allows to be more linearly scalable.

## Decision

Enable ELasticSearch routing.

Messages should be indexed by mailbox.

Quota Ratio should be indexed by user.

## Consequences

A data reindex is needed.

Performance needs to be detailed here once tests are conducted.

## References

 - https://www.elastic.co/guide/en/elasticsearch/reference/6.3/mapping-routing-field.html
 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2917)