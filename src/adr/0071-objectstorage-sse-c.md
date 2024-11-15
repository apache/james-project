# 71. SSE-C for S3 Object Storage

Date: 2024-11-15

## Status

In progress

## Context

To enhance data security for users, Apache James currently supports client-side encryption through AESBlobStoreDAO. However, this solution consumes significant CPU resources for encryption and decryption, impacting overall system performance.

To provide a more efficient encryption option, the team has decided to integrate Server-Side Encryption with Customer-provided keys (SSE-C) for S3 Object Storage. This will allow for enhanced security with optimized performance. SSE-C provides high-level security without the CPU overhead experienced with client-side encryption in James.

## Decision

Integrate SSE-C with S3 Object Storage in Apache James to improve user data security. 
James will manage the master key and salt, using them to create customer keys when calling S3 APIs, such as PUT, GET, and HEAD on objects.M

Two approaches are proposed for providing the customer key:

1. Single customer key: A single master key and salt will be used across all objects in the system. These values will be configured in the configuration file, similar to the current `AESBlobStoreDAO` approach.

2. Derived customer key: A more secure option where the master key and salt are dynamic, based on the bucketName and blobId. This approach generates a unique customer key for each different blobId, enhancing security. However, it also has a higher CPU resource impact and depends on the configured key generation algorithm.

The current library (`awssdk s3`), fully supports the required APIs for this feature, enabling seamless integration of SSE-C without any compatibility issues.

Enabling SSE-C is fully optional. By default, it is disabled and requires configuration changes to activate, allowing users to retain their existing configuration without enabling SSE-C automatically.

## Consequences

### Benefits

- Performance Improvement: SSE-C takes advantage of S3’s security capabilities without taxing James’s CPU.
- Security: Provides robust security without fully shifting encryption to the client or to S3 alone.

### Limitations

- Incompatibility with Deduplication feature.
- Data Migration Challenges: Currently, S3 APIs do not support migrating encrypted data from AESBlobStoreDAO (client-side encryption) to the new SSE-C endpoint.
- Replication: SSE-C does not support bucket replication.

## Alternatives

- Continue using client-side encryption with AESBlobStoreDAO.
- Using SSE-S3 (Server-Side Encryption with S3-managed keys) for enhanced security without the need for client-side management.

## References
- [AWS SSE-C Documentation](https://docs.aws.amazon.com/AmazonS3/latest/userguide/ServerSideEncryptionCustomerKeys.html)
