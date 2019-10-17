# 11. Disable ElasticSearch source

Date: 2019-10-17

## Status

Rejected

The benefits do not outweigh the costs.

## Context

Though very handy to have around, the source field does incur storage overhead within the index. 

## Decision

Disable _source for ELasticSearch indexed documents.

## Consequences

Given a dataset composed of small text/plain messages, we notice a 20% space reduction of data stored on ElasticSearch.

However, patch updates can no longer be performed upon flags updates. Upon flag update we need to fully read the mail 
content, then mime-parse it, potentially html parse it, extract attachment content again and finally index again the full 
document.

Without _source field, flags update is two time slower, 99 percentile 4 time slower, and this impact negatively other 
requests.

## References

 - https://www.elastic.co/guide/en/elasticsearch/reference/6.3/mapping-source-field.html
 - [JIRA](https://issues.apache.org/jira/browse/JAMES-2906)
