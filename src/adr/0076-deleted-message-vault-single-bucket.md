# 76. Deleted Message Vault: single bucket usage

Date: 2026-02-01

## Status

Accepted (lazy consensus).

## Context

At the moment, the current [deleted message vault](https://issues.apache.org/jira/browse/JAMES-4156) uses multiple buckets to store deleted messages of users. Each bucket is generated
with a name corresponding to a year and a month, following this pattern: `deleted-messages-[year]-[month]-01`.

Then we when run the purge tasks, every bucket that is older than the defined retention period is being deleted.

However, this solution can be a bit costly in terms of bucket count with S3 object storages and can affect performance by 
doing multiple API calls on multiple buckets at once. Also some provider, like OVH, put limits on count of buckets per account.

## Decision

Using a single bucket for storing deleted messages instead. The objects in the single bucket would be following this name pattern:
`[year]/[month]/[blob_id]`. S3 buckets are flat but we can still use the year and month as a prefix for the object name.

For this we can:

- provide a new implementation for the blob store deleted message vault that would store deleted messages on a single bucket.
- write only on the single bucket, fall back if necessary on old buckets for read and delete
- add the single bucket usage case to the purge task, that would do cleaning on both new and old buckets.

## Consequences

- easier to maintain, only one bucket
- keep the bucket count for James low on S3 object storages
- read/write/delete operations on only one bucket, not multiple
- James would no longer require rights to create buckets at runtime when the deleted message vault is enabled
- migration is simple: old buckets will get removed with time until only the new single bucket remains

# Alternatives

Specific James implementation could overload an unchanged deleted message vault and provide their own however we believe 
the problem and complexity of operating atop multiple bucket is detrimental for others in the community for minimal to no gains.

# References

- [0075-deleted-message-vault.md](0075-deleted-message-vault.md)
- [Deleted Message Vault: use a single bucket](https://issues.apache.org/jira/browse/JAMES-4156)

