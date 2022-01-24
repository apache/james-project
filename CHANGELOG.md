# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### Added
 - JAMES-3524 Support symmetric encryption support on top of BlobStore
 - JAMES-3516 Work started toward supporting threads. (see upgrade instructions)
 - JAMES-2157 Introduce a HasMimeTypeAnySubPart matcher
 - JAMES-3588 Allow LMTP to be configured to execute the mailetcontainer (per recipient or grouped executions available)
 - JAMES-3491 JMAP: Configurable websocket url for JMAP configuration
 - JAMES-3574 LMTP: regular stack also should execute Message hooks
 - JAMES-3574 Test suite for LMTP protocol
 - JAMES-3581 JMAP: Allow writing custom State changes
 - JAMES-2330: Give ability to override AbstractConfigurableAsyncServer.buildSSLContext
 - JAMES-3573 Allow specifying DC in Cassandra configuration
 - JAMES-3520 JMAP - Implement MDN - RFC-9007 (#385)
 - JAMES-3532 JMAP: Implement Email/import
 - MAILBOX-405 CreateMissingParentsTask implementation
 - JAMES-3316 Allow to write custom extensions in JMAP session
 - JAMES-3534 JMAP support for Identity/set (part of RFC-8621)
 - JAMES-3487 Java property: MimeMessageInputStreamSource THRESHOLD (#755)
 - JAMES-3673 Separate trust store for S3 (#751)
 - JAMES-3671 Added glowroot instrumentation for POP3 protocol
 - JAMES-3670 Configurable restore location for messages from the Deleted Messages Vault
 - JAMES-3667 Add a WebAdmin route for verifying a user password. (#741)
 - JAMES-3669 Add an option to delay protocol responses on authentication failure, as basic protection against brute-force attacks (#746)
 - JAMES-3668 Added utility to load extra system properties from a configuration file on server start (#744)
 - JAMES-3539 Implement JMAP PushSubscriptions (as per RFC-8620)
 - JAMES-3440 EmailQueryView support for sort by receivedAt (#710)
 - JAMES-3078 Allow to disable user provisioning for JMAP (#708)
 - JAMES-3674 Support salting on top of James user password storage. See related upgrade instructions.
 - JAMES-3639 Allow to configure JMAP crypto with raw public key (#695)
 - JAMES-3657 Modular entity validation for webadmin-data (#678)
 - JAMES-3150 Garbage collection for deduplicated blobs using bloom filters
 - JAMES-3588 Mailet to propagate encoutered error (#655)
 - JAMES-3645 Allow RemoteDelivery to use SMTPS and fallback to SMTP (#632)
 - JAMES-3639 Allow use PEM keys for SSL, JMAP
 - JAMES-3640 Auto generate demo SSL PEM keys
 - JAMES-3638 Allow use PKCS12 keystore for SSL (#625)
 - JAMES-3297 Publish the number of items currently in the mailet pipeline as a metric (#650)
 - JAMES-3623 Provide a (multi-DC firendly) Distributed POP3 Application
 - JAMES-3544 Clean task for JMAP uploads
 - JAMES-3621 WebAdmin task to clear the content of a mailbox
 - JAMES-3516 Implement JMAP Thread/get (RFC-8621)
 - JAMES-3621 Mailbox webadmin routes - unseenMessageCount + messageCount
 - JAMES-3622 Compatibility with Cassandra 4.0.0
 - Adopt Scala checkstyle
 - JAMES-3618 Support LDAP for JPA guice based apps
 - JAMES-3608 email.send.max.size option for JMAP
 - JAMES-3610 SMTP/IMAP: unit for size related options
 - JAMES-3605 Reconnection handlers for RabbitMQ consumers
 - JAMES-3604 RabbitMQ connections should be cluster aware
 - JAMES-3607 Functional healthcheck exercising mail reception
 
### Removed
 - JAMES-3578 Drop Cassandra schema version prior version 8 (see upgrade instructions)
 - JAMES-3596 Drop spring app WAR plugin
 - JAMES-3621 Drop benchmarks and debian/rpm packaging (was unmaintained and broken)
 - Drop Swagger (#706)
 - JAMES-3646 Remove Maildir implementation (#661)
 - Remove GroupMembershipResolver and implement (#670)
 - JAMES-2979 Remove FileMailQueue (#660)
 - JAMES-3631 Drop no longer used MailRepository tables
 - Terminate Apache James HUPA
 - [REFACTORING] Remove unused BayesianAnalyzer and related class (#526)
 
### Changed
 - JAMES-3621 Re-organise server application
   - Use JIB for docker images
   - Guice application should have a ZIP distribution
   - Collocate applications in server/apps folder
   - Example of JPA driver customization
   - Drop no longer needed projects
   - Add some manifest entries when using maven-jar-plugin (#510)
   - Fix related website links
   - Docker: expose volumes used for persistence with volumes
 - JAMES-3261 Update glowroot to 0.13.6
 - JAMES-3591 Warn against CassandraBlobStore usage (can be bypassed via an environment variable)
 - Upgrade to MIME4J latest 0.8.4 release (#459)
 - Cassandra implementation should depend on interfaces and ModSeqProvider
 - JAMES-3587 Deprecate MDCBuild::addContext (relies on potentially expensive implicit toString calls)
 - JAMES-3567 Distributed server should not rely on ActiveMQ
 - Adopt MIME4J 0.8.6 (#682)
 - UPGRADE jackson-dataformat-cbor 2.10.4 -> 2.11.4 (ES 7 driver) (#662)
 - JAMES-3647 Adopt eclipse-temurin:11-jre-focal docker image (#652)
 - JAMES-2968 Move "Time Spent in IMAP-*" Log Entry from INFO to DEBUG
 - JAMES-2287 Encode BlobId with base64 (#572)
 - JAMES-1862 IMAP plainAuthDisallowed should be true by default
 - [UPGRADE] Security upgrade: JSOUP 1.14.1 -> 1.14.2 to address CVE-2021-3771
 - [UPGRADE] Security upgrade: common-compress to 1.21
 - JAMES-2625 Remove stacktrace upon ClosedChannelException
 - JAMES-3261 Add some system properties for TLS (#588)
 - Multiple miscaleneous dependency upgrades:
 
### Performance
 - JAMES-3466 Provision default mailboxes only when listing all mailboxes
 - JAMES-3576 Further denormalize message table (see related upgrade instructions)
 - ResultUtils::haveValidContent is doing needless work
 - Optimise GetMessagesMethod::messagesNotFound
 - SimpleMailboxMessage: userFlags assignment was done twi
 - Prefer DefaultMessageBuilder for Mime message parsing
 - Pre-convert Cassandra rows to lower case (#501)
 - JAMES-3603 AmqpForwardAttribute should reuse RabbitMQ connections
 - ImapDateTimeFormatter can be static
 - JAMES-3491 Do not send two JMAP events upon new messages
 - JAMES-3599 Deliver events to all groups at once to reduce deserialization and messaging overhead
 - Remove regular expression usages where possible
 - Avoid doing JWT parsing twice
 - JAMES-3028 Do not execute downstream requests on AWS driver pool (#485)
 - JAMES-2989 JMAP Preview should not normalize spaces of the entire body (#479)
 - Use buffer output stream upon MessageManager::append
 - Reduce Cassandra chunk length for some read intensive tables
 - JAMES-3594 Migrate to UnboundId as a LDAP implementation. This allows performance gains. See related upgrade instructions.
 - General review of our reactive flows:
   - End to end reactive calls for both JMAP draft and JMAP RFC-8621
   - Migrate where possible from Mono.fatMap to flatMapIterable
   - MailboxChangeListener should be fully reactive
   - Bond Reactive listeners as Reactive
   - JAMES-2393 Allow writing reactive eventSourcing subscribers
   - GroupRegistration was doing (blocking) acks on the parallel pool
   - RabbitMQ receivers can be blocking
 - JAMES-2683 RabbitMQ: Use a single connection per James node
 - MailboxACL.union shortcuts
 - Cassandra: Use static TypeToken for complex CQL types
 - JMAP Draft JSON: Cache and reuse Object mappers for writing JMAP responses (#440)
 - Keywords::fromFlags should avoid intermediate collections
 - JsoupHtmlTextExtractor should use Collectors.joiner
 - JMAP draft was parsing, formatting then re-parsing JSON
 - JAMES-3028 Allow setting up S3 HTTP concurrency at the Netty level
 - JAMES-3028 S3Client should not be pooled
 - JAMES-3586 Cassandra BlobStore: Use LOCAL_ONE for optimistic consistency downgrades
 - JAMES-3107 Deprecate log p99 due to its performance impact
 - JAMES-3107 Switch to HDR histograms
 - Mailboxes metadata: Avoid O(n2) algorithm to compute hasChildren
 - JMAPServer should generate JMAP routes once
 - MessageViewFactory::toHeaderMap was unfolding headers twice
 - MessageResultImpl should use underlying MailboxMessage
 - FlagsFactory::createFlags needlessly call the builder
 - JAMES-3171 Mailbox/get + ids: Avoid reading subscriptions for all mailboxes
 - CassandraSubscriptionMapper should prepare its statements
 - CassandraMailboxSessionMapperFactory should not instantiate one mapper per request
 - Reactor: favor error suppliers (this avoids needlessly filling stacktraces)
 - JAMES-1965 JMAP Draft MessageFullViewFactory: Avoid performing HTML text extraction if not needed
 - JAMES-3407 Applicative read-repair: draw a random number only if needed
 - DefaultMailboxesProvisioner: Avoid re-opening a session
 - JMAP: Avoid MIME re-parsing when sending messages
 - JAMES-3078 Continuation Token signing was done on the Netty event loop thread
 - JAMES-3467 Avoid loading all domains for auto-detection when auto-detection is off
 - JAMES-3435 Cassandra: Allow to avoid LWT for messages operations via message.write.strong.consistency.unsafe
 - [PERFORMANCE] MessageUid::compareTo should not box values (#764)
 - [PERFORMANCE] MessageFullViewFactory should not always evaluate textual content of the message
 - [PERFORMANCE] defer expensive Monos in switchIfEmpty
 - [PERFORMANCE] Limit context switches
 - [PERF] Reactify UsersRepository::contains (#704)
 - JAMES-3652 Avoid locking in protocol task execution (#666)
 - JAMES-3196 Avoid MDC costs when not needed (#667)
 - JAMES-3630 quotaDetailsReactive should group quota limit reads
 - JAMES-3626 Better handle tombstones due to Cassandra empty collections
 - JAMES-3627 Prepared statements for applicable flags
 - [REFACTORING] Remove more REGEX usages (#587)
 - JAMES-3629 enqueuedMailsV4 to use frozen collections
 - [PERFORMANCE] IndexableMessage text field is never used
 - [PERFORMANCE] AttributeValue: object mapper can be static
 - JAMES-3613 IMAP + SMTP should compute transport MDC upon connection
 - [PERFORMANCE] DropWizardMetricFactory: optimize wrapping monos
 - [PERFORMANCE] Record SetMessagesProcessor metrics if executed
 - [PERFORMANCE] SetMessagesMethod metrics are redundant with processor one
 - [PERFORMANCE] Use lenient parsers for MIME4J in more places
 - [PERFORMANCE] CreationMessage: Simplify assertAtLeastOneValidRecipient
 - [PERFORMANCE] SetMessagesUpdateProcessor processing can be done lazily
 - [PERFORMANCE] Optimize Username parsing
 - [PERFORMANCE] JMAP: Fasten accept header parsing

### Fixed
 - JAMES-3589 Fix mailet processing logic upon partial matches by dropping Apache Camel mailetcontainer implementation
 - JAMES-3491 WebSocket should unregister resources on cancels
 - JAMES-3261 JPA-SMTP app: add missing loagback declarations
 - JAMES-3601 [ADR] Distributed Mail Queue Cleanup is now fully implemented
 - JAMES-3600 All JMAP calls should position Content-Length
 - JAMES-3597 JMAP: Exclude deleted messages from JMAP Email/query
 - JAMES-3594 Decrease verbosity of bad credential auth failures
 - JAMES-3595 Spooler processing starts before mailetContainer initialisation
 - JAMES-3492 ElasticSearch: Do not create indices if it already exists
 - JAMES-2886 Fix collection handling for PropertiesProvider
 - JAMES-2813 Long running tasks on the MemoryTaskManager generates stackTraces
 - JAMES-3592 Maildir tests (Unit and MPT) are not representative of Spring product
 - JAMES-3107 Fix some zeroed metrics
 - JAMES-3579 reject verifyIdentity param to true when authRequired is false in SMTP server configuration
 - JAMES-3491 JMAP webSocket can be used to mix responses and state changes
 - JAMES-3467 Domain cache should be refreshed periodically under read load
 - JAMES-3571 MimeMessageWrapper getSize was incorrect for empty messages
 - JAMES-3567 S3: explicitly specify version for netty-codec-http
 - JAMES-3569 preserves all email propertis on recipient rewrite
 - JAMES-3557 */changes: Fail explicitly when too much entries on a single change
 - JAMES-3558 JMAP Email/changes: moves should be considered as updates
 - JAMES-3525 Verify identity should also apply for unauthenticated users
 - JAMES-3458 JMAP Identity/get should support ids field
 - JAMES-3556 JMAP eventUrl s/closeAfter/closeafter/ (#379)
 - JAMES-3432 Upload routes was unstable (#374)
 - JAMES-3554 JMAP: remove pushState field from Server Sent Events
 - JAMES-3481 s/maxChanged/maxChanges
 - JAMES-3553 Disable read_repair_chance & read_repair_chance on table creation
 - JAMES-2884 Email/query s/comparator/sort/
 - JAMES-3256 Dis-ambiguate MailDispatcher error logs
 - JAMES-3522 JMAP routes should position WWW-Authenticate
 - JAMES-3677 BackReference should allow pointing to specific array elements (#765)
 - JAMES-3613 Avoid a NPE due to IMAP MDC (#766)
 - JAMES-2557 Sieve should cleanup email after sending them (#743)
 - JAMES-1618 Fix manage sieve implementation and test it with Thunderbird (#742)
 - JAMES-3600 Fix check Content-Length when ProvisioningTest (#738)
 - JAMES-3666 Fix DSNBounce exception when no Date header is present
 - JAMES-3477 Some email sent via the mailet context were never disposed (#712)
 - JAMES-1930 Configure Memory Users repository in Memory APP (#709)
 - JAMES-3516 Fix Thread/get error management
 - JAMES-3662 Accept CORS headers without the JMAP API restriction on "Accept" headers (#699)
 - JAMES-3369 JMAP EMail/get Fallback to text/plain when no HTML in multipart/alternative (#698)
 - JAMES-3661 Email/* should handle quota exceptions (#696)
 - Fix pom relativePath parent of apache-mailet-test module
 - JAMES-3660 Cassandra mailbox creation unstable when high concurency (#686)
 - MAILBOX-333 Avoid overQuotaMailing failures when no size limit (#676)
 - JAMES-1436 SwitchableLineBasedFrameDecoder: clean up cumulation buffer (#673)
 - Fix invalid json scope for james-json test-jar (#672)
 - JAMES-3655 Fix Quota extensions with delegation
 - JAMES-3477 Mail::duplicate did lead to file leak in various places (#668)
 - JAMES-3640 No longer ship crypto materials in default configuration
 - JAMES-3150 S3BlobStoreDAO listBlob paging (#643)
 - PROTOCOLS-106 CRLFTerminatedInputStream should sanitize lonely \n delimiters
- JAMES-3646 Sanitize some File based components  
   - FileMailRepository shoud reject URL outside of James root
   - SieveFileRepository should validate underlying files belong to its root
- JAMES-1862 Generalize STARTTLS sanitizing fix
- JAMES-1862 Prevent Session fixation via STARTTLS
- JAMES-3634 + JAMES-3635 Apply fuzzing to Apache James
   - Upgrade PrefixedRegex to RE2J
   - Fuzzed input throws String out of bound exception for FETCH
   - Prevent String OutOfBoundException for IMAP APPEND
   - Prevent infinite loop for IMAP STATUS command parser
   - Prevent infinite loop for IMAP APPEND command parser
- MAILBOX-347 NONE Password hashing is actually replace the password with a fixed string (#641)
- PROTOCOLS-118 Fixed continuation request not getting recognised by some clients (#640)
- MAILBOX-407 listShouldReturnEmptyListWhenNoMailboxes fails with NPE
- JAMES-2278 Fix the IMAP QRESYNC "out of bound" issue
- JAMES-1808 if (character > 128) should be changed to if (character >= 128) (#634)
- JAMES-1444 Using HasMailAttributeWithValueRegex matcher causes NPE during startup when JMX is enabled (#633)
- JAMES-3373 Download optional query parameters should comply with advertized URI templates
- JAMES-3440 JMAP RFC-8621: EmailQueryView position handling was wrong
- JAMES-3601 RabbitMQ mailQueue Cassandra projection: Stop browsing buckets concurrently (#577)
- JAMES-3625 JMAP session: Remove trailing / in session download url (#576)
- JAMES-3601 Bind ContentStartDAO as a singleton
- JAMES-3624 RFC 8887 (JMAP over WebSocket) Request needs property 'id' (is 'requestId')
- JAMES-3620 Memory leak at org.apache.james.protocols.smtp.core.AbstractHookableCmdHandler
- JAMES-3611 SearchUtil getBaseSubject do not sanitize empty subject

### Documentation
 - JAMES-3405 Document Prometheus metric config (#373)
 - JAMES-3565 Documentation: fix packaging support matrix
 - JAMES-3255 Demo image now includes the james-cli utility
 - JAMES-3255 Use apache/james images
 - Better document THE release process (#337)
 - Video link for James joining ApacheCON 2021 (#663) (#740)
 - JAMES-2734 Document Sieve and ManageSieve (#745)
 - JAMES-3665 - Add Kubernetes support document (#718)
 - Split the distributed server documentation
 - Update project Roadmap
 - JAMES-3261 JPA: extra JDBC driver running the ZIPped apps (#684)
 - JAMES-3644 Document DKIM + SPF setup with James
 - Improvements of the README
 - Improvements for download page:
   - DOWNLOADS Remove warnings regarding cryptography
   - DOWNLOADS Remove archive reference from the top, fix archive link for server
   - DOWNLOADS Remove verify integrity section
   - DOWNLOADS Remove "miror"section
   - DOWNLOADS Remove dead link to Hupa
 - Imap tutorial can demo Thunderbird connection
 - Do not advertise James 2.3.2 on james.apache.org
 - [DOCUMENTATION] Fix missing content-type in upgrade schema version command
 - [DOCUMENTATION] Small polish of server/dev-build.html page (#645)
 - [DOCUMENTATION] mailbox/cassandra table structure and denormalization (#608)
 - JAMES-3389 Document email transfer routes
 - [SITE] Update copyright footer
 - [DOCUMENTATION] Install guide needs to refer to JRE 11
 - Rework James extension examples
 - Details guide to assemble your own tailor made James server
 - JAMES-3617 Document Prometheus/Grafana setup and provide dashboards (#549)
 - JAMES-3614 The homepage should comply with the ASF release policy
 
### Third party software
 - Upgrading to Apache Tika 1.26 is recommended
     - 1.25 and before are subject to CVE-2021-28657 CVE-2021-27906 CVE-2021-27807
     - 1.24 is subject to CVE-2020-9489
 - Upgrading to RabbitMQ 3.8.18 is recommended. According to [the changelog](https://www.rabbitmq.com/changelog.html) RabbitMQ prior this version is subject to several CVE:
     - https://tanzu.vmware.com/security/cve-2020-5419
     - https://tanzu.vmware.com/security/cve-2021-22117
     - https://tanzu.vmware.com/security/cve-2021-22116
     - [CVE-2021-32718](https://github.com/rabbitmq/rabbitmq-server/security/advisories/GHSA-c3hj-rg5h-2772)
     - [CVE-2021-32719](https://github.com/rabbitmq/rabbitmq-server/security/advisories/GHSA-5452-hxj4-773x)

### Miscaleneous
 - Mock SMTP version 0.5 (#733)
   - report mock email count directly instead of copy+count
   - http GET for mock email count
   - mock email DELETE returning cleared emails

## [3.6.1] - 2021-12-02

### Security

This release fixes the following vulnerability issues, that are present prior to 3.6.1:

 - *CVE-2021-38542*: Apache James vulnerable to STARTTLS command injection (IMAP and POP3)
 - *CVE-2021-40110*: Apache James IMAP vulnerable to a ReDoS
 - *CVE-2021-40111*: Apache James IMAP parsing Denial Of Service
 - *CVE-2021-40525*: Apache James: Sieve file storage vulnerable to path traversal attacks

### Fixed
- JAMES-3676 Avoid S3 connection leaks
- JAMES-3477 Mail::duplicate did lead to file leak in various places
- JAMES-3646 Sanitize some File based components  
   - Prevent directory traversal on top of maildir mailbox (#659)
   - FileMailRepository shoud reject URL outside of James root
   - SieveFileRepository should validate underlying files belong to its root
- JAMES-1862 Generalize STARTTLS sanitizing fix
- JAMES-1862 Prevent Session fixation via STARTTLS
- JAMES-3634 + JAMES-3635 Apply fuzzing to Apache James
   - Upgrade PrefixedRegex to RE2J
   - Fuzzed input throws String out of bound exception for FETCH
   - Prevent String OutOfBoundException for IMAP APPEND
   - Prevent infinite loop for IMAP STATUS command parser
   - Prevent infinite loop for IMAP APPEND command parser
- JAMES-3571 MimeMessageWrapper getSize was incorrect for empty messages
- JAMES-3525 verifyIdentity should not fail on null sender
- JAMES-3556 Fix JMAP eventUrl s/closeAfter/closeafter/
- JAMES-3432 JMAP Uploads could alter the underlying byte source
- JAMES-3537 (Email/set create should allow to attach mails)
- JAMES-3558 JMAP Email/changes: When created + updated return both
- JAMES-3558 JMAP Email/changes: moves should be considered as updates
- JAMES-3557 Changes collectors should be ordered
- JAMES-3277 Distinct uids before calling toRanges
- JAMES-3434 Refactoring: EmailSubmissionSetMethod should not rely on nested clases
- JAMES-3557 JMAP */changes: Increase default maxChanges 5 -> 256
- JAMES-3557 */changes: Fail explicitly when too much entries on a single change
- JAMES-3683 Upgrade to Log4J 2.16.0 (CVE-2021-44228 + CVE-2021-45046)

### Improvements
- JAMES-3261 ZIP packaging for Guice Apps

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
- JAMES-3477 Fix NPE when concurrently updating MimeMessage (always copy the message rather than using shared references, which might impact performance)
- JAMES-3444 Perform JMAP TransportChecks only when JMAP is enabled
- JAMES-3495 Cassandra mailbox: Reproduce and fix the null messageId bug
- JAMES-3490 maxUploadSize should come from configuration
- JAMES-1717 VacationMailet should not return answers when no or empty Reply-To header
- JAMES-1784 JMAP: Users with `_` in their names cannot download attachments

### Removed
 - HybridBlobStore. Introduced to fasten small blob access, its usage could be
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
