# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

## [Unreleased]
### Added
- Mail filtering configured via the JMAP protocol
- WebAdmin exposed mail re-indexing tasks
- JPA support for Sieve script storage thanks to [Sebast26](https://github.com/sebast26)
- Mailet DOC: Exclude from documentation annotation thanks to [mschnitzler](https://github.com/mschnitzler)
- `cassandra.pooling.max.queue.size` configuration option Thanks to [matzepan](https://github.com/matzepan)
- `RecipentDomainIs` and `SenderDomainIs` matchers by [athulyaraj](https://github.com/athulyaraj)
- IMAP MOVE commend (RFC-6851) On top of JPA. Thanks to [mschnitzler](https://github.com/mschnitzler)
- Cassandra health checks thanks to [matzepan](https://github.com/matzepan)
- Sieve script upload via the CLI thanks to [matzepan](https://github.com/matzepan)

### Changed
- Multiple libraries updates
- Migration from Cassandra 2 to Cassandra 3
- Mail::getSender was deprecated. Mail::getMaybeSender offers better Null Sender support. Java 8 default API method was used to not break compatibility.

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
