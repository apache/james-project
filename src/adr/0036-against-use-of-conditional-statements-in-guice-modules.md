# 36. Against the use of conditional statements in Guice modules

Date: 2019-12-29

## Status

Accepted (lazy consensus) & implemented

## Context

James products rely historically on Spring for dependency injection. It doesn't use last major Spring version (4.x instead of 5.x).
James uses Spring in a way that enables overriding any class via a configuration file thus endangering overall correctness by giving too much 
power to the user.

James proposes several implementations for each of the interfaces it defines. The number of possible combinations of
implementations is thus really high (like factorial(n) with n > 10). It makes it unpractical to run tests for each 
possible component combination. We run integration tests for combinations that we decide brings the more value to
the users. Spring product defeats this testing logic 
by allowing the user arbitrary classes combination, which is likely not being tested.

Instead of having a single product allowing all component combination, we rather have 
several products each one exposing a single component combination. Components are defined by code in a static fashion. 
We thus can provide a decent level of QA for these products. Overriding components requires explicit code modification 
and recompilation, warning the user about the impact of the choices he does, and lowering the project's responsibility. 
Guice had been enacted as a way to reach that goal.

With Guice we expose only supported, well tested combinations of components, thus addressing the combination issue.

Spring application often bring dependencies conflicts, for example between Lucene and ElasticSearch 
components, leading to potential runtime or compile time issues. Instead of having a single big application being able 
to instantiate each and every component application, we have several products defining their dependencies in a 
minimalistic way, relying only on the components implementation that are needed.

Here is the list of products we provide:

 - In-Memory: A memory based James server, mainly for testing purposes
 - Distributed James: A scalable James server, storing data in various data stores. Cassandra is used for metadata, 
 ElasticSearch for search, RabbitMQ for messaging, and ObjectStorage for blobs.
 - Cassandra: An implementation step toward Distributed James. It does not include messaging and ObjectStorage and 
 should not be run in a cluster way but is still relevant for good performance.
 - JPA: A JPA and Lucene based implementation of James. Only Derby driver is currently supported.
 - JPA with SMTP only using Derby: A minimalist SMTP server based on JPA storage technology and Derby driver
 - JPA with SMTP only using MariaDB: A minimalist SMTP server based on JPA storage technology and MariaDB driver

Some components however do have several implementations a user can choose from in a given product. This is the case for:

 - BlobExport: Exporting a blob from the blobStore to an external user. Two implementations are currently supported: 
 localFiles and LinShare.
 - Text extraction: Extracting text from attachment to enable attachment search. There is a Tika implementation, but 
 lighter JSOUP based, as well as no text extraction options are also available.

In order to keep the number of products low, we decided to use conditional statements in modules based on the 
configuration to select which one to enable at runtime. Eventually defeating the Guice adoption goals mentioned above.

Finally, Blob Storing technology offers a wide combination of technologies:

 - ObjectStorage in itself could implement either Swift APIs or Amazon S3 APIs
 - We decided to keep supporting Cassandra for blob storing as an upgrade solution from Cassandra product to Distributed 
James for existing users. This option also makes sense for small data-sets (typically less than a TB) where storage cost are less 
of an issue and don't need to be taken into account when reasoning about performance.
 - Proposals such as [HybridBlobStore](0014-blobstore-storage-policies.md) and then 
[Cassandra BlobStore cache](0025-cassandra-blob-store-cache.md) proposed to leverage Cassandra as a performance 
(latency) enhancer for ObjectStorage technologies.

Yet again it had been decided to use conditional statements in modules in order to lower the number of products.

However, some components requires expensive resource initialization. These operations are performed via a separate module
that needs to be installed based on the configuration. For instance 
[Cassandra BlobStore cache](0025-cassandra-blob-store-cache.md) requires usage of an additional cache keyspace that 
represents a cost and an inconvenience we don't want to pay if we don't rely on that cache. Not having the cache module 
thus enables quickly auditing that the caching cassandra session is not initialized. See 
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
 
Cassandra and Distributed products are furthermore duplicated to offer a version supporting LDAP authentication. JPA 
product does not offer LDAP support.

## Decision

We should no longer rely on conditional statements in Guice module.

Guice modules combination choice should be decided before starting the dependency injection stage.

Each component choice needs to be abstracted by a related configuration POJO.

Products will, given the set of configuration POJOs, generated the modules it should rely on during the dependency 
injection stage.

An INFO log with the list of modules used to create its Guice injector. This enables easy diagnose of the running 
components via the selected module list. It exposes tested, safe choices to the user while limiting the Guice products 
count.

## Consequences

Component combination count keeps unchanged for Guice products, but the run combination is explicit. QA needs are 
unchanged.

Integration tests needs to be adapted to accept component choice configuration POJO.

The following conditional statements in guice modules needs to be removed :

 - [Cached blob store pull request](https://github.com/linagora/james-project/pull/3319) addresses 
 ObjectStorageDependenciesModule::selectBlobStoreBuilder and Cassandra Blob Store Cache conditional statement.
 - [S3 native blobStore implementation](https://github.com/linagora/james-project/pull/3099) along side with S3 endpoints
 support as part of Swift removes the need to select the Object Storage implementation.
 - Follow up work needs to be plan concerning `BlobExportMechanismModule` and `TikaMailboxModule::provideTextExtractor`.
 
We furthermore need to enable a module choice for LDAP on top of other existing products. We should remove LDAP variations
for LDAP products. Corresponding docker image will be based on their non LDAP version, overriding the `usersrepository.xml`
configuration file, be marked as deprecated and eventually removed.

## References

* [PR discussing this ADR](https://github.com/apache/james-project/pull/188)