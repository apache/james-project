# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Added
- Distributed task management for Guice cassandra-rabbitmq product. This enables several James servers to share a consistent view
of tasks being currently executed.
- JAMES-2563 Health check for ElasticSearch
- JAMES-2904 Authentication and SSL support for Cassandra backend
- JAMES-2904 Authentication and SSL support for ElasticSearch backend

### Changed
- Multiple changes have been made to enhance ElasticSearch performance:
  - Use of routing keys to collocate documents per mailbox
  - Under some configuration, html was not extracted before document indexing
  - Removed unnecessary fields from mailbox mapping
  - Disable dynamic mapping thanks to a change of the header structure 
  - Read related [upgrade instructions](upgrade-instructions.md)
- JAMES-2855 Multiple library/plugin/docker images/build tool upgrades
  
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

### Removed
- Classes marked as deprecated whose removal was planned after 3.4.0 release (See JAMES-2703). This includes:
  - SieveDefaultRepository. Please use SieveFileRepository instead.
  - JDBCRecipientRewriteTable, XMLRecipientRewriteTable, UsersRepositoryAliasingForwarding, JDBCAlias mailets. Please use RecipientRewriteTable mailet instead.
  - JDBCRecipientRewriteTable implementation. Please use JPARecipientRewriteTable instead.
  - JamesUsersJdbcRepository, DefaultUsersJdbcRepository. Please use JpaUsersRepository instead.
  - MailboxQuotaFixed matcher. Please use IsOverQuota instead.
- UsersFileRepository, which was marked as deprecated for years

### Third party softwares
 - Tika prior 1.22 is subject to multiple CVEs. We recommend the upgrade.

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
