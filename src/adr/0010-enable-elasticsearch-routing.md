# 10 Enable ElasticSearch routing

Date: 2019-10-17

## Status

Accepted (lazy consensus)

Additional performance testing is required for adoption.

## Context

Our queries are mostly bounded to a mailbox or a user. We can easily
limit the number of ElasticSearch nodes involved in a given query by
grouping the underlying documents on the same node using a routing key.

Without a routing key, each shard needs to execute the query. The coordinator
needs also to be waiting for the slowest shard.

Using the routing key unlocks significant throughput enhancement (proportional
to the number of shards) and also a possible high percentile latency enhancement.

As most requests are restricted to a single coordination, most search requests will
hit a single shard, as opposed to non routed searches which would have hit each shards 
(each shard would return the number of searched documents, to be ordered and limited 
again in the coordination node). This allows to be more linearly scalable.

## Decision

Enable ElasticSearch routing.

Messages should be indexed by mailbox.

Quota Ratio should be indexed by user.

## Consequences

A data reindex is needed.

On a single ElasticSearch node with 5 shards, we noticed latency reduction for mailbox search (2x mean time and 3x 99 
percentile reduction)

## References

 - https://www.elastic.co/guide/en/elasticsearch/reference/6.3/mapping-routing-field.html
 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2917)
