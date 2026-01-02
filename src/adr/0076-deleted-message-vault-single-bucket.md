# 76. Deleted Message Vault: single bucket usage

Date: 2026-02-01

## Status

Accepted (lazy consensus).

## Context

At the moment, the current deleted message vault uses multiple buckets to store deleted messages of users. Each bucket is generated
with a name corresponding to a year and a month, following this pattern: `deleted-messages-[year]-[month]-01`.

Then we when run the GC tasks, every bucket that is older than the defined retention period is being deleted.

However, this solution can be a bit costly in terms of bucket count with S3 object storages and can affect performance by 
doing multiple API calls on multiple buckets at once.

## Decision

Using a single bucket for storing deleted messages instead! The objects in the single bucket would be following this name pattern:
`[year]/[month]/[blob_id]`. S3 buckets are flat but we cna still use the year and month as a prefix for the object name.

For this we can:

- provide a new implementation for the blob store deleted message vault that would store deleted messages on a single bucket.
- write only on the single bucket, fall back if necessary on old buckets for read and delete
- add the single bucket usage case to the GC task, that would do cleaning on both new and old buckets.

## Consequences

- easier to maintain, only one bucket!
- keep the bucket count for James low on S3 object storages
- read/write/delete operations on only one bucket, not multiple.

# References

- [0075-deleted-message-vault.md](0075-deleted-message-vault.md)
- [Deleted Message Vault: use a single bucket](https://issues.apache.org/jira/browse/JAMES-4156)

