# 71. SSE-C for S3 Object Storage

Date: 2024-11-15

## Status

Accepted (lazy consensus)

## Context

To enhance data security for users, Apache James currently supports client-side encryption through AESBlobStoreDAO. However, this solution consumes significant CPU resources for encryption and decryption, impacting overall system performance. Moreover, it pushes synchronous concepts like `InputStream` into an otherwise reactive interface.

Some object storage providers offer Server-Side Encryption with Customer-provided keys (SSE-C), where users supply their own encryption keys, while the service performs encryption and securely stores the data.

## Decision

We have decided to integrate SSE-C option for James S3 Object Storage implement. 

James will manage the master key and salt, using them to create customer keys when calling S3 APIs, such as PUT, GET, and HEAD on objects.

In order to provide the customer key, we have made it an interface to allow for potential variations in key and salt derivation. The default implementation will be:

- Single customer key: A single master key and salt will be used across all objects in the system. These values will be configured in the configuration file, similar to the current `AESBlobStoreDAO` approach.

The current library (`awssdk s3`), fully supports the required APIs for this feature, enabling seamless integration of SSE-C without any compatibility issues.

Enabling SSE-C is fully optional. By default, it is disabled and requires configuration changes to activate, allowing users to retain their existing configuration without enabling SSE-C automatically.

## Consequences

### Benefits

- Potential Performance Improvement: SSE-C leverages S3’s security capabilities, potentially reducing CPU load on James servers by offloading encryption and decryption tasks to S3.
- Security: Provides robust security without fully shifting encryption to the client or to S3 alone.

### Limitations

- Data Migration Challenges: Currently, S3 APIs do not support migrating encrypted data from AESBlobStoreDAO (client-side encryption) to the new SSE-C endpoint.
- Replication: Some object storage provider does not support bucket replication when enable SSE-C. Eg: OVH Object Storage, S3-Minio only support from version 2024-03-30.
- SSE-C may be considered less secure than AESBlobStoreDAO (Client-Side Encryption) because the encryption key must be provided to the S3 service for encryption and decryption operations. Even if the S3 storage provider do not persist the keys.

## Alternatives

- Continue using client-side encryption with AESBlobStoreDAO.
- Using SSE-S3 (Server-Side Encryption with S3-managed keys) for enhanced security eliminates the need for client-side key management. However, this trade-off means SSE-S3 relies entirely on Object storage provider for key management, which may not satisfy stricter security or compliance requirements.

## References
- [AWS SSE-C Documentation](https://docs.aws.amazon.com/AmazonS3/latest/userguide/ServerSideEncryptionCustomerKeys.html)
