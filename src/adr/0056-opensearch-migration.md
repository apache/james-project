# 56. OpenSearch migration

Date: 2022-08-11

## Status

Accepted (lazy consensus).

Implemented. 

Migration from OpenSearch high-level-rest-client to OpenSearch rest-client remains to be done.

## Context

James distributed server historically uses ElasticSearch software to empower search features. As off Apache James
3.7.0, we relied on ElasticSearch 7.10.

However, [ElasticSearch changed their licenses terms](https://www.elastic.co/pricing/faq/licensing), both for the servers
and driver libraries, choosing the SSPL (Server Side Public License), a [non-OSI compliant license](https://opensource.org/node/1099). 
As such, this clashes from a requirement for Apache projects to rely on ASF v2 compatible client libraries. Similarly, 
requiring a non-free software as a dependency would have been allowed but is a clear infringement to the ASF philosophy 
when free-er alternatives are available.

Alternative forks have emerged from ElasticSearch 7.10, one could mention 
[Open Distro for ElasticSearch](https://opendistro.github.io/for-elasticsearch/) which was renamed 
[OpenSearch](https://opensearch.org/), whose 1.x release line is fully compatible with ElasticSearch 7.10 driver, 
meaning that Apache James 3.7.0 users can use OpenSearch as an alternative to OpenSearch.

However, to fix security issues in its dependencies, we need to upgrade the ElasticSearch driver, which cannot be done
without license infringement and hurting OpenSearch compatibility.

Similarly, OpenSearch 2.x introduces subtle differences that makes its driver no longer compatible with ElasticSearch 7.10.

## Decision

Switch to OpenSearch drivers and fully adopt OpenSearch as the search backend for the Distributed James server.

First, conduct the easy migration toward OpenSearch high-level-rest-client (with dependencies to the whole openSearch 
eco-system), then conduct the more expensive migration to the rest-client (only dependencies to an http client and JSON 
parsing libraries).

## Consequences

Users are expected to deploy and use OpenSearch. Configuration need to be adapted too.

Users will not be able to use James Distributed server on top of ElasticSearch product anymore.

## Alternatives

Not upgrading exposes us to security vulnerabilities and as such is not an option.

Upgrade to eg ElasticSearch 8.2 is also not an option due to license clashes.

An alternative to OpenSearch could have been [Apache SolR](https://solr.apache.org) yet while this distributed 
search engine is also based on [Lucene](https://lucene.apache.org) it is very different from ElasticSearch 
and would have had demanded a full re-implementation effort, which we could hardly afford. The migration to OpenSearch
is a tinier step, both in terms of operation and code.

Users willing to maintain their own search system can still assemble a James server with the technology of their choice,
which incurs a maintenance cost.

## References

- [JIRA](https://issues.apache.org/jira/browse/JAMES-3771)
- [Discussion on the mailing list](https://www.mail-archive.com/server-dev@james.apache.org/msg72113.html)