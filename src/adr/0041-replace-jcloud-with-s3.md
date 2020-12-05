# 41. Replace JCloud-based ObjectStorage backend with AWS-S3 SDK backend 

Date: 2020-02-11

## Status

Accepted (lazy consensus) & implemented

Adoption had been backed by some performance tests.

## Context

James has an interface called blob-store that allows to choose how Blobs are stored between several implementations.

For Distributed product, the default backend is Cassandra because it's fast and it doesn't require yet another
server. 

When storage cost concerns are more important than raw performances, James can store Blobs in S3 or Swift 
Object Storage technologies.

Object Storage is known to be cheap and yet offers guarantee about durability.

When we implemented Object Storage blob-store in the past, we decided to implement it with 
[Apache jcloud library](https://jclouds.apache.org/) because we wanted to target both S3 and Swift API.

However, we found that our implementation was complex and it didn't fit the Reactive style we now have in the codebase.
It also contribute negatively to the project build time.

At the same time, we figured out Swift was providing a good compatibility layer for S3 and that we may drop
our Swift code without dropping Swift support at the same time. 

## Decision

* Use AWS S3 v2 SDK to implement a Reactive S3 blob-store (`blobstore-s3`).
* Replace current `blob-store-objectstorage` with `blobstore-s3` 
* Run load tests to ensure there's no penalty when switching from Swift to S3 over Swift

## Consequences

* We have to document carefully how to transition from `blob-store-objectstorage` with `blobstore-s3`  in the
configuration and how to handle existing data

* We need to work on a new implementation of blob encryption.

## References

* [JAMES](https://issues.apache.org/jira/browse/JAMES-3028)
* [Pull request tree](https://github.com/linagora/james-project/pull/3773)
