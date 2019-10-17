# 9. Disable ElasticSearch dynamic mapping

Date: 2019-10-10

## Status

Accepted (lazy consensus)

## Context

We rely on dynamic mappings to expose our mail headers as a JSON map. Dynamic mapping is enabled for adding not yet encountered headers in the mapping.

This causes a serie of functional issues:
 - Maximum field count can easily be exceeded
 - Field type 'guess' can be wrong, leading to subsequent headers omissions [1]
 - Document indexation needs to be paused at the index level during mapping changes to avoid concurrent changes, impacting negatively performance.

## Decision

Rely on nested objects to represent mail headers within a mapping

## Consequences

The index needs to be re-created. Document reIndexation is needed.

This solves the aforementionned bugs [1].

Regarding performance:
 - Default message list performance is unimpacted
 - We noticed a 4% performance improvment upon indexing throughput
 - We noticed a 7% increase regarding space per message

## References

 - [1]: https://github.com/linagora/james-project/pull/2726 JAMES-2078 Add an integration test to prove that dynamic mapping can lead to ignored header fields
 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2078)
