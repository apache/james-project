# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

## [Unreleased]

## [3.6.0] - 2021-03-16

### Added
- JAMES-2884 Partial Support for JMAP RFC-8621: The current implementation status allow reading mailboxes, emails, vacation responses.
  - JAMES-3457 Implement JMAP eventSource 
  - JAMES-3491 JMAP over websocket (RFC-8887)
  - JAMES-3470 JMAP RFC-8621 Email/changes + Mailbox/changes support
- JAMES-3117 Add PeriodicalHealthChecks for periodical calling all health checks
- JAMES-3143 WebAdmin endpoint to solve Cassandra message inconsistencies
- JAMES-3138 Webadmin endpoint to recompute users current quotas on top of Guice products
- JAMES-3296 Webadmin endpoint to rebuild RabbitMQMailQueue in the Distributed Server
- JAMES-3266 Offer an option to disable ElasticSearch in Distributed James product
- JAMES-3202 Reindex only outdated documents with the Mode option set to CORRECT in reindexing tasks
- JAMES-3405 Expose metrics of Guice servers over HTTP - enables easy Prometheus metrics collection
- JAMES-3407 Distributed server: Read-repairs for the mailbox entity
- JAMES-3428 Distributed server: Read-repairs for the mailbox counters entity
- JAMES-3139 Expose RabbitMQ channel & connection configuration
- JAMES-3441 Make possible and document Distributed Server setup with specialized instances
- JAMES-3337 Document the use of JWT
- JAMES-3399 Allow JSON logging with logback - enables structure logging with FluentBit
- JAMES-3396 WebAdmin should try to prevent RRT addresses redirection loops when possible
- JAMES-3402 JMAP MDN messages should have a Date header
- JAMES-3028 Distributed server: allow choosing whether blobs should be deduplicated
- JAMES-3196 CanSendFromImpl: enable to send email from aliases for SMTP and JMAP
- JAMES-3196 Add an IMAP SessionId to correlate logs
- JAMES-3502 DistributedServer: SSL and authentication support for RabbitMQ
- JAMES-3504 Metrics and log for POP3
- JAMES-3431 Optional DSN support
- JAMES-3202 Allow search index Reindexing without cleanup

### Changed
- Switch to Java 11 for build and run
- JAMES-2760 mailqueue.size.metricsEnabled should be false by default
- JAMES-3252 DomainList autoDetection should be turned off by default. Operators relying on implicit values for enabling DomainList autoDetection now needs to explicitly configure it.
- JAMES-3184 Throttling mechanism allow an admin to specify the throughput desired for a given WebAdmin task
- JAMES-3224 Configuration for Cassandra ConsistencyLevel.{QUORUM, SERIAL} (for multi-dc configuration)
- JAMES-3176 Rewritte MDN parsing with Parboiled scala (avoid asm library dependency clash within the Distributed Server)
- JAMES-3194 Rely on DTOConverter in TaskRoute
- JAMES-3430 Restructure message properties storage within Cassandra Mailbox. See upgrade instructions.
- JAMES-3435 Use EventSourcing to manage ACL - avoid SERIAL reads for ACL thus unlocking a performance enhancement for the Distributed James server. Read upgrade instructions.
- JAMES-2124 Sorts module declarations in reactors (thanks to Jean Helou)
- JAMES-3440 JMAP users can now avoid relying on ElasticSearch reads for basic listing operations thanks to the EmailQueryView 
- JAMES-3252 DomainList autoDection should be turned off by 
- JAMES-3192 Upgrade Apache configuration to 2.7
- JAMES-3492 Upgrade ElasticSearch dependency for DistributedServer to 7.10
- JAMES-2514 Upgrade Cassandra dependency for DistributedServer 3.11.3 -> 3.11.10
- JAMES-3497 Multiple dependencies upgrades
- JAMES-3499 Package LDAP in Distributed Server
- JAMES-3225 Set up of the Apache CI
- [REFACTORING] Switch most of the test suite to JUNIT 5

### Deprecated
- HybridBlobStore. This will be removed after 3.6.0 release. Introduced to fasten small blob access, its usage could be
compared to a cache, but with a sub-optimal implementation (no eviction, default replication factor, no  circuit breaking).
Use BlobStore cache instead.

### Fixed
- JAMES-3305 Avoid crashes upon deserialization issues when consuming RabbitMQ messages, leverage dead-letter feature
- JAMES-3212 JMAP Handle subcrible/unsubcrible child's folder when update mailbox
- JAMES-3416 Fix ElasticSearch email address search
- JAMES-1677 Upgrade default hasing algorithm to SHA-512
- JAMES-3454 Use a callback mechanism to re-create RabbitMQ auto-delete queues upon reconnections
- JAMES-3296 Recover email sent during RabbitMQ outages
- JAMES-2046 SentDateComparator should fallback to Mimle4J parsers
- JAMES-3416 ElasticSearch address indexing fixes
- JAMES-3386 add test to ensure blank mailbox paths are not allowed in jmap draft
- MAILBOX-392 WebAdmin documentation: creation of mailboxes with '&' is allowed
- JAMES-3380 use non am/pm dependent hour format
- JAMES-2220 JMAP Draft: Flags update should not fail when a user is missing its Outbox
- JAMES-3364 DeletedMessageVault: deleting many messages dead-locks
- JAMES-3361 JMAP Draft: sharee should not be able to modify mailbox rights
- JAMES-3308 RabbitMQTerminationSubscriberTest should be thread safe
- JAMES-3177 Applicable flags updates needs to be thread safe (IMAP SELECT)
- JAMES-3309 Avoid a NPE in FetchProcessor when SelectedMailbox is unselected
- JAMES-3300 Fix default Cassandra LDAP configuration
- JAMES-3267 Stop forcefully delete ImapRequestFrameDecoder.decode temporary file
- JAMES-3167 Reactify MailboxMapper - unlocks better concurrency management
- JAMES-3170 Fix metric measurement upon reactor publisher replay
- JAMES-3213 Source ReplyTo in ICALToJsonAttribute
- JAMES-3204 Push limit to Cassandra backend when reading messages - before that message listing queries where always reading at least 5000 rows, and triggering other reads for these rows.
- JAMES-3201 ReIndexing enhancements
- JAMES-3179 Fix UpdatableTickingClock thread safety issue
- MAILBOX-405 Renaming too much mailboxes at once was failing on top of the Cassandra mailbox
- JAMES-3513 Wrong UID dispatched on the EventBus for StoreMessageIdManager::setInMailboxes
- JAMES-3512 DigestUtil: close base64 encoding stream
- JAMES-3487 Allow setting on*Exception parameters for Bounce
- JAMES-3511 Solve java.util.NoSuchElementException: heartbeatHandler
- JAMES-3507 Fix broken IMAP APPEND literalSizeLimit option preventing from buffering large requests to files
- JAMES-3438 des-ambiguity error message for Email/set create Content-Transfer-Encoding rejection
- JAMES-3477 Fix NPE when concurrently updating MimeMessage
- JAMES-3444 Perform JMAP TransportChecks only when JMAP is enabled
- JAMES-3495 Cassandra mailbox: Reproduce and fix the null messageId bug
- JAMES-3490 maxUploadSize should come from configuration
- JAMES-1717 VacationMailet should not return answers when no or empty Reply-To header
- JAMES-1784 JMAP: Users with `_` in their names cannot download attachments

### Removed
 - HybridBlobStore. This will be removed after 3.6.0 release. Introduced to fasten small blob access, its usage could be
 compared to a cache, but with a sub-optimal implementation (no eviction, default replication factor, no  circuit breaking).
 Use BlobStore cache instead.
 
### Performance
- JAMES-3295 Multiple IMAP performance enhancements for the Distributed Server. Some enhancement might transfer to other servers as well.
  - JAMES-3295 Use MessageManager::listMessagesMetadata more widely (IMAP)
  - JAMES-3265 IMAP FETCH reading lastUid and lastModseq should be optional
  - JAMES-3265 CassandraMessageMapper should limit modseq allocation upon flags updates
  - JAMES-3265 Impement a MessageMapper method to reset all recents
- JAMES-3263 Optimize RecipientRewriteTable::getMappingsForType
- JAMES-3458 Limit Cassandra statements when retrieving all quota limits
- JAMES-2037 CassandraMessageMapper::listAllMessageUids should not rely on ComposedMessageIdWithMetaData
- JAMES-3453 Specify explicitly lower safer defaults for Reactor flatMaps, filterWhens
- JAMES-3444 Allow moving JMAP mailets in a local-delivery processor - this enables calling `RecipientIsLocal` only one time in the mailet processing pipeline.
- JAMES-2037 Use Flux for MessageManager::search
- JAMES-3409 Better denormalize mailboxes within the Distributed Server. This enables reading only one table of the projection instead of two. Read repairs are implemented for keeping eventual consistency checks. Read upgrade instructions.
- JAMES-3433 Distributed Server: use caching blobstore only for frequently accessed data (callers can specify the level of performance they expect). This ensures the cache is read when it is useful.
- JAMES-3408 Limit concurrency when retrieving mailbox counters
- JAMES-3430 Restructure message properties storage within Cassandra Mailbox. See upgrade instructions.
- JAMES-3277 SetMessagesUpdateProcessor should read less mailboxes - enhance performance for JMAP-draft and JMAP RFC-8621.
- JAMES-3408 Enforce IMAP List not reading counters for Distributed James
- JAMES-3377 Remove unused text criterion - newly indexed mails indexed in ElasticSearch will take less space
- JAMES-3095 Avoid listing all subscriptions for each mailbox (IMAP)
- JAMES-2629 Use a future supplier in CassandraAsyncExecutor
- JAMES-2904 Avoid loading attachment when not needed (IMAP & JMAP) + attachment content streaming (JMAP)
- JAMES-3155 Limit the number of flags updated at the same time
- JAMES-3264 MAILBOX details are read 3 times upon indexing
- JAMES-3506 Avoid a full body read within VacationMailet
- JAMES-3508 Improved performance for IMAP APPEND
- JAMES-3506 SMTP performance enhancement
- JAMES-3505 Make mail remote delivery multi-threaded
- JAMES-3488 Support TLS 1.3
- JAMES-3484 Cassandra mailbox should group copies/moves

### Third party softwares
- James is no longer tested against Cassandra 3.11.3 but instead against Cassandra 3.11.10. Users are recommended to upgrade to this
version as well. See related upgrade instructions.

## [3.5.0] - 2020-04-06

### Added
- JAMES-2813 task management for Distributed James product. This enables several James servers to share a consistent view
of tasks being currently executed.
- JAMES-2563 Health check for ElasticSearch
- JAMES-2904 Authentication and SSL support for Cassandra backend
- JAMES-2904 Authentication and SSL support for ElasticSearch backend
- JAMES-3066 Add support alias when sending emails, with a "allowed From headers" webadmin endpoint
- JAMES-3062 HealthCheck for EventDeadLetters
- JAMES-3058 WebAdmin offline task to correct mailbox inconsistencies on top of Cassandra products
- JAMES-3105 WebAdmin offline task to recompute mailbox counters on top of Cassandra products
- JAMES-3072 WebAdmin endpoint to export mailbox backup

### Changed
  - Use of routing keys to collocate documents per mailbox
  - Under some configuration, html was not extracted before document indexing
  - Removed unnecessary fields from mailbox mapping
  - Disable dynamic mapping thanks to a change of the header structure
- Multiple changes have been made to enhance Distributed James indexing performance:
  - JAMES-2917 Use of routing keys to collocate documents per mailbox
  - JAMES-2910 Under some configuration, html was not extracted before document indexing
  - JAMES-2079 Removed unnecessary fields from mailbox mapping
  - JAMES-2078 Disable dynamic mapping thanks to a change of the header structure
  - Read related [upgrade instructions](upgrade-instructions.md)
- JAMES-2855 Multiple library/plugin/docker images/build tool upgrades
- JAMES-2981 By default the cassandra keyspace creation by James is now disabled by default. This allow to have credentials limited to a keyspace. It can be enabled by setting cassandra.keyspace.create=true in the cassandra.properties file.
- Usernames are assumed to be always lower cased. Many users recently complained about mails non received when sending to upper cased local recipients. We decided to simplify the handling of case for local recipients and users by always storing them lower cased.
- JAMES-2576 Unhealthy health checks now return HTTP 503 instead of 500, degraded now returns 200 instead of 500. See JAMES-2576.
- JAMES-2992 In order to fasten JMAP-draft message retrieval upon calls on properties expected to be fast to fetch, we now compute the preview and hasAttachment properties asynchronously and persist them in Cassandra to improve performance. See JAMES-2919.
- JAMES-2950 It is now forbidden to create new Usernames with the following set of characters in its local part : `"(),:; <>@\[]`, as we prefer it to stay simple to handle. However, the read of Usernames already existing with some of those characters is still allowed, to not introduce any breaking change. See JAMES-2950.
- JAMES-3040 Linshare blob export configuration and mechanism change.
- JAMES-3112 Differentiation between domain alias and domain mapping. Read upgrade instructions.
- JAMES-3122 Log4J2 adoption for Spring product. Log file configuration needs to be updated. See upgrade instructions.

### Fixed
- JAMES-2828 & JAMES-2929 bugs affecting JDBCMailRepository usage with PostgresSQL thanks to Jörg Thomas & Sergey B
- JAMES-2936 Creating a mailbox using consecutive delimiter character leads to creation of list of unnamed mailbox
- JAMES-2911 Unable to send mail from James using an SMTP gateway
- JAMES-2944 Inlined attachments should be wrapped in multipart/related by JMAP draft
- JAMES-2941 Return NO when an IMAP command unexpectedly fails
- JAMES-2943 Deleting auto detected domain should fail
- JAMES-2957 dlp.Dlp matcher should parse emails containing attachments
- JAMES-2958 Limit domain name size to not longer than 255 characters
- JAMES-2939 Prevent mixed case INBOX creation
- JAMES-2903 Rework default LOG4J log file for Spring
- JAMES-2739 fixed browse mails from queue over JMX
- JAMES-2375 DSNBounce mailet should provide a subject
- JAMES-2097 RemoteDelivery: Avoid retrying already succeeded recipients when sendPartial
- MAILBOX-392 Mailbox name validation upon mailbox creation is stricter: forbid `#&*%` and empty sub-mailboxes names.
- JAMES-2972 Incorrect attribute name in the mailet configuration thanks to jtconsol
- JAMES-2632 JMAP Draft GetMailboxes performance enhancements when retrieving all mailboxes of a user
- JAMES-2964 Forbid to create User quota/ Domain quota/ Global quota using negative number
- JAMES-3074 Fixing UidValidity generation, sanitizing of invalid values upon reads. Read upgrade instructions.

### Removed
- Classes marked as deprecated whose removal was planned after 3.4.0 release (See JAMES-2703). This includes:
  - SieveDefaultRepository. Please use SieveFileRepository instead.
  - JDBCRecipientRewriteTable, XMLRecipientRewriteTable, UsersRepositoryAliasingForwarding, JDBCAlias mailets. Please use RecipientRewriteTable mailet instead.
  - JDBCRecipientRewriteTable implementation. Please use JPARecipientRewriteTable instead.
  - JamesUsersJdbcRepository, DefaultUsersJdbcRepository. Please use JpaUsersRepository instead.
  - MailboxQuotaFixed matcher. Please use IsOverQuota instead.
- UsersFileRepository, which was marked as deprecated for years
  - We accordingly removed deprecated methods within UsersRepositoryManagementMBean exposed over JMX (unsetAlias, getAlias, unsetForwardAddress, getForwardAddress). RecipientRewriteTables should be used instead.
- JAMES-3016 RemoteDelivery now doesn't enable `allow8bitmime` property by default. 
This parameter could cause body content alteration leading to DKIM invalid DKIM signatures to be positioned. 
Thanks to Sergey B. for the report. 
More details about the property is at [java mail doc](https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html)
 - JAMES-3122 LogEnabled API in Spring product had been removed for Log4J2 adoption for Java 9+ runtime compatibility.
 - JAMES-3122 LogEnabled API in Spring product had been removed for Log4J2 adoption for Java 9+ runtime compatibility. 
 - HybridBlobStore. This will be removed after 3.6.0 release. Introduced to fasten small blob access, its usage could be
 compared to a cache, but with a sub-optimal implementation (no eviction, default replication factor, no  circuit breaking).
 Use BlobStore cache instead.
 - JAMES-3028 OpenStack's Swift support. It was very complex, slow and even slowed down AWS S3 implementation.
 
### Third party softwares
 - The distributed James server product (relying on Guice, Cassandra, ElasticSearch, RabbitMQ and optionally Swift) now needs at least RabbitMQ 3.8.1.
 - Tika prior 1.24 is subject to multiple CVEs. We recommend the upgrade.

## [3.4.0] - 2019-09-05
### Added
- Add in-memory docker image
- Support of AWS S3 as blobstore
- WebAdmin routes for mapping management (AddAddressMapping, AddRegexMapping, ListMappings), previously only manageable by CLI
- Metrics for Deleted Message Vault
- More metrics for BlobStore (new delete & deleteBucket configuration)

### Changed
- (experimental) New implementation of the deleted message vault on top of an object store, not relying anymore on a repository. This avoids exposing messages via webAdmin. Messages previously stored in the vault will be lost.
- Migration to ElasticSearch 6.3
- Blob export to LinShare
- Native DKIM support for outgoing mails. The mailets from james-jdkim have been included in main James project.

### Deprecated
- Zookeeper component. This will be removed after 3.4 release. If you have development skills, and are willing to maintain this component, please reach us.

### Removed
- Karaf OSGi implementation. It was not compiled nor tested for a year. We didn't get any feedback about that and we don't have the resources nor the skills to maintain it any more.

## [3.3.0] - 2019-03-26
### Added
- Metrics for BlobStore
- New Guice product using Cassandra RabbitMQ ElasticSearch, OpenStack Swift and optional LDAP dependency (experimental)
- JPA SMTP dockerFile contributed by [matzepan](https://github.com/matzepan)
- Listing healthchecks, thanks to [Madhu Bhat](https://github.com/kratostaine)
- Configuring the ElasticSearch clusterName
- Logging and Metrics now supports Elasticsearch 6 (previously only Elasticsearch 2 was supported)
- Implementation of the RabbitMQ EventBus
- DeadLetter APIs and memory implementation for storing events that failed delivery
- RecipientRewriteTable Aliases and associated WebAdmin routes
- EventBus DeadLetter reDeliver routes on top of WebAdmin
- EventBus DeadLetter Cassandra implementation
- WebAdmin routes for restoring and exporting deleted messages from the Deleted Messages Vault
- PreDeletionHook extension mechanism

### Fixed
- MAILBOX-350 Potential invalid UID <-> MSN mapping upon IMAP COPY
- Possibility to better zoom in Grafana boards
- default ElasticSearch shards & replica configured values
- Move & copy batch sizes are now loaded from configuration

### Changed
- WebAdmin ReIndexing API had been reworked
- MailboxListener and mailbox event system were reworked. Custom listeners will need to be adapted. Please see Upgrade instuctions.
- Docker images are now using a JRE instead of a JDK
- Replacement of the old mailbox event system with the EventBus
- Progressive use of [reactor](https://github.com/reactor/reactor) for concurrency management (in progress)

### Removed
- Drop HBase and JCR components (mailbox and server/data).

### Third party softwares
 - Tika prior 1.20 is subject to multiple CVEs. We recommend the upgrade

## [3.2.0] - 2018-11-14
### Added
- Mail filtering configured via the JMAP protocol
- WebAdmin exposed mail re-indexing tasks
- WebAdmin exposed health checks. This includes:
   - Possibility to perform a single healthcheck, thanks to [mschnitzler](https://github.com/mschnitzler)
   - Cassandra health checks thanks to [matzepan](https://github.com/matzepan)
- IMAP MOVE commend (RFC-6851) On top of JPA. Thanks to [mschnitzler](https://github.com/mschnitzler)
- JPA support for Sieve script storage thanks to [Sebast26](https://github.com/sebast26)
- Sieve script upload via the CLI thanks to [matzepan](https://github.com/matzepan)
- Mailet DOC: Exclude from documentation annotation thanks to [mschnitzler](https://github.com/mschnitzler)
- `cassandra.pooling.max.queue.size` configuration option Thanks to [matzepan](https://github.com/matzepan)
- `RecipentDomainIs` and `SenderDomainIs` matchers by [athulyaraj](https://github.com/athulyaraj)

### Changed
- Multiple libraries updates
- Migration from Cassandra 2 to Cassandra 3
- Mail::getSender was deprecated. Mail::getMaybeSender offers better Null Sender support. Java 8 default API method was used to not break compatibility.

### Deprecated
 - HBase and JCR components (mailbox and server/data). This will be removed as part of 3.3.0. If you have development skills, and are willing to maintain these components, please reach us.

### Removed
- Drop partially implemented Kafka distributed events

### Third party softwares
 - SpamAssassin prior 3.4.2 is subject to multiple CVEs. We recommend the upgrade
 - Tika prior 1.19.1 is subject to multiple CVEs. We recommend the upgrade

## [3.1.0] - 2018-07-31
### Added
- Delegating folders
- Introduce an object store
- Configurable listeners
- MDN (Message Disposition notification) parsing and handling
- SpamAssassin support with per user reports
- Search in attachments
- Data Leak Prevention
- JPA SMTP Guice product
- Cassandra migration process
- Structured logging
- RPM packaging (in addition to deb packaging)

### Changed
- Move to Java 8
- Improve Mail Repositories handling, including a nice web API
- Improve Mail Queues handling, including a nice web API
- Improve RRT (Recipient Rewrite Table) implementation
- Quota handling improvments, and in particular users can receive an email when they are near the limit of their quota
- Many performances enhancement, in particular on Cassandra backend
- Documentation updates

## [3.0.1] - 2017-10-20
### Changed
- Fix CVE-2017-12628: java deserialization issue exposed by JMX

## [3.0.0] - 2017-07-20
Too many untracked changes, sorry. But you can have a look at our latest news: [James posts](http://james.apache.org/posts.html)

## Before
Refer too [Old changelog](http://james.apache.org/server/2.3.0/changelog.html)
