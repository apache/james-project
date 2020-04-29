# 36. Against the use of conditional statements in Guice modules

Date: 2019-12-29

## Status

Accepted (lazy consensus)

## Context

James products rely historically on Spring for injections. Spring version is outdated (4.3.25 instead of 5.3 release line).
Spring enables overriding any component via a configuration file thus endangering overall correctness by giving too much 
power to the user.

James propose several implementations for each of the interfaces we define. We don't run tests for each possible interface
combination. We rather run integration tests for combinations that make sense. By overriding any components, Spring defeats 
this testing logic. Knowing which component implementation combines well with which other one is not intuitive to users,
hard to document and test.

We thus decided to rely on Guice. Components are defined by code in a static fashion. Overriding components requires 
explicit code modification and recompilation, thus warning the user about the impact of the choices he does, and lowering 
the project's responsibility.

With Guice we expose only supported, well tested combinations of components, thus addressing the combination issue.

Instead of having a single big application being able to instantiate each and every component application, we have 
several products defining their dependencies in a minimalistic way, relying only on the components implementation that 
are needed.

Here is the list of products we rely on:

 - memory-guice: A memory based James server, mainly for testing purposes
 - Distributed James: A scalable James server, storing data in various data stores. Cassandra is used for metadata, 
 ElasticSearch for search, RabbitMQ for messaging, and ObjectStorage for byte contents.
 - Cassandra-guice: An implementation step toward Distributed James. It does not include messaging and ObjectStorage.
 - JPA-guice: A JPA and Lucene based implementation of James. Only Derby driver is currently supported.
 - JPA-smtp: A minimalistic SMTP server based on JPA storage technology.

Some components however do have several subtly diverging implementations a user might choose to rely on independently 
of the product he uses. This is the case for:

 - BlobExport: Exporting a blob from the blobStore to an external user. Two implementations are currently supported: 
 localFiles and LinShare.
 - Text extraction: Extracting text from attachment to enable attachment search. There is a Tika implementation, but 
 lighter JSOUP based options are also available.

In order to keep the cardinality of Guice products low, we decided to use conditional statements in modules based on the 
configuration to select which one to enable. Eventually defeating the Guice adoption goals mentioned above.

Finally, Blob Storing technology offers a wide combination of technologies:

 - ObjectStorage in itself could implement either Swift APIs or Amazon S3 APIs
 - We decided to keep supporting Cassandra for blob storing as an upgrade solution from Cassandra-guice to Distributed 
James for existing users.
 - Proposals such as [HybridBlobStore](0014-blobstore-storage-policies.md) and then 
[Cassandra BlobStore cache](0025-cassandra-blob-store-cache.md) proposed to leverage Cassandra as a performance 
(latency) enhancer for ObjectStorage technologies.

Yet again it had been decided to use conditional statements in modules in order to lower the cardinality.

However, [Cassandra BlobStore cache](0025-cassandra-blob-store-cache.md) requires expensive resource initialization
requiring to perform upgrade procedure (usage of an additional cache keyspace) that represents a cost we don't want to
pay if we don't rely on that cache. Not having the cache module thus enables quickly auditing that the caching cassandra 
session is not initialized. See 
[this comment](https://github.com/linagora/james-project/pull/3261#pullrequestreview-389804841) as well as 
[this comment](https://github.com/linagora/james-project/pull/3261#issuecomment-613911695).

### Audit

The following modules perform conditional statements upon injection time:

 - BlobExportMechanismModule : Choice of the export mechanism
 - ObjectStorageDependenciesModule::selectBlobStoreBuilder: Choice between S3 and Swift ObjectStorage technologies
 - TikaMailboxModule::provideTextExtractor: Choice of text extraction technology
 - BlobStoreChoosingModule::provideBlobStore: Choice of BlobStore technology: Cassandra, ObjectStorage or Hybrid
 - [Cached blob store](https://github.com/linagora/james-project/pull/3319) represents a similar problem: should the 
 blobStore be wrapped by a caching layer?

## Decision

We should no longer rely on conditional statements in Guice module.

We should within James main method, upon James startup read the configuration and select the modules that should be 
selected to run it, before calling the Guice injector to perform its full startup.

This enables easy diagnose of the running components via the selected module list. It exposes tested, safe choices to 
the user while limiting the Guice products count.

Basic minimalistic integration tests will be written to cover the possibilities exposed to the user, by statically 
composing the given modules.

Concerning the usages listed above :

 - [Cached blob store pull request](https://github.com/linagora/james-project/pull/3319) addresses 
 ObjectStorageDependenciesModule::selectBlobStoreBuilder and Cassandra Blob Store Cache conditional statement.
 - [S3 native blobStore implementation](https://github.com/linagora/james-project/pull/3099) along side with S3 endpoints
 support as part of Swift removes the need to select the Object Storage implementation.
 - Follow up work needs to be plan concerning `BlobExportMechanismModule` and `TikaMailboxModule::provideTextExtractor`.

## Consequences

Integration testing can not offer conditional, configuration based module composition. This is because:

 - Integration tests don't rely on Main classes but on GuiceJamesServer class with similar guice modules
 - Overriding a configuration file within a same maven module is painful
 
As a consequence, we should define statically the modules an integration test needs to run. Configuration defined Guice
modules declaration logic cannot be tested with the integration technique we have.

Unit tests and integration tests for the possible module composition should limit the risks.
