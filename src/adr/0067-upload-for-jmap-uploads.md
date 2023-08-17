# 67. Quota for JMAP uploads

Date: 2023-08-17

## Status

Accepted (lazy consensus).

Implemented.

Complements [ADR-48 Cleaup JMAP uploads](0048-cleanup-jmap-uploads.md).

## Context

The [JMAP] protocol offers a distinct API to upload blobs, that can later be referenced when creating emails. The 
specification mentions that implementers `SHOULD` enforce a quota for this use case. For security reason this quota is 
set by user, and exceeded his quota should result in older data being deleted.

Apache James currently do not implement limitations on data being uploaded by users, meaning that authenticated user can 
essentially store unlimited amount of binary data. This is especially problematic for deployments whose users can be 
attackers (eg SAAS).

## Decision

Implement quota for JMAP uploads. We need a generic interface for JMAP upload quota current values that existing 
implementation can implement.

Store current values on a per user basis. Current value is increased upon uploads, and decreased when a blob is deleted.

Limit is set globally via the JMAP configuration. Default value: 10MB.

## Consequences

Improved security for SaaS operation.

Storing such values in Cassandra incurs a cost as it needs extra tables. The count of tables shall be limited (memory and
operational overhead per table.) We plan complementary work to expose a technical Cassandra storage interface for quota,
that can be used to implement arbitrary quota-like use cases.

Cassandra counters that would be used to keep track of users current space usage are easy to get out of synchronisation
(namely because of counters consistency level ONE usage and non-idempotence causing the driver not to retry failed 
updates). We thus need a corrective task in order to recompute the current values.

JMAP upload storage evolutions:
 - As we add an application behaviour, common for any implementation, we need further layers in the design in
order to mutualize quota handling for all implementations. A service layer `UploadService` would expose the JMAP facade
(today `UploadRepository` interface) and would be responsible to enforce quotas, and related behaviour. Ten it would 
act on the storage layer, `UploadReposiory`, implemented by `cassandra` and `memory`.
 - Upon exceeded quota, we need to delete older uploads. In order to do so, we need to add the date of upload to the
upload metadata. Migration is trivial: we can assume UNIX timestamp when missing, causing the upload to be considered 
the oldest.
 - Recomputation of JMAP upload quotas requires listing stored upload metadata, we need to add a way to list uploads 
of a user on `UploadReposiory` (without retrieving the uploads contents).
 - `UploadService` needs a method to delete 

## Alternatives

Not operating in SaaS mode would allow to better trust users. As such we might simply document the limitation and 
skip the work. Such a proposal is not acceptable for some members of the community.

We might have chose to store maximum limits for JMAP upload quotas. Doing so requires extensive webadmin endpoints, and
incurs extra Cassandra reads upon uploads, which have a slight minor performance negative impact. Aggregating `gobal`, 
`domain` and `user` scopes together might also be some complex logic to write. All this work is of limited use as a moderate 
space like 100MB is plenty enough for dozens of mail to be composed in parallel without issues, even for power user. 
Furthermore, the JMAP specification behaviour is lenient once the space is exceeded, hence we never block te user. 
This clearly claims for the simpler option.

## References

 - [JMAP api for uploads](https://jmap.io/spec-core.html#binary-data)
 - [JIRA ticket: JAMES-3925](https://issues.apache.org/jira/browse/JAMES-3925)
 - [ADR pull request](https://github.com/apache/james-project/pull/1688)