# 76. Migration from ActiveMQ Classic to ActiveMQ Artemis for Embedded Mail Queue

Date: 2026-09-22

## Status

Accepted & implemented.

## Context

Apache James provides a mail spool queue mechanism implemented via JMS (`server/queue/queue-activemq`).
Previously, the embedded broker relied on Apache ActiveMQ "Classic" (5.x/6.x) with KahaDB persistence adapter and ActiveMQ-specific `BlobMessage` / `FileSystemBlobTransferPolicy`.

Under real-world and high workloads, ActiveMQ Classic suffered from severe design and performance limitations:

1. **Throughput and Latency Bottleneck:**
   - Out-of-the-box ActiveMQ Classic experiences high latency (~100 ms) and limited throughput (~25-400 msgs/s depending on storage sync and KahaDB locking).
   - Java profiling revealed that up to 95% of total request processing time inside James was spent waiting on ActiveMQ / KahaDB queue commits.

2. **Head-of-Line Blocking with Delayed Mails (JAMES-4192):**
   - In James Remote Delivery, retries are scheduled with delays (e.g., 30–60 minutes) upon encountering temporary exceptions (such as greylisting / SMTP 421).
   - Dequeueing uses JMS message selectors (`JAMES_NEXT_DELIVERY <= currentTimeMillis() OR FORCE_DELIVERY = true`).
   - In ActiveMQ Classic, messages are read into memory in batches governed by `maxPageSize` (default: 200). If ≥200 delayed messages reside at the head of the queue, the selector rejects them, but ActiveMQ Classic **stops evaluating further pages**.
   - As a result, the entire outgoing delivery stalls for the duration of the delay window, completely starving non-delayed, ready-to-deliver messages.

3. **Proprietary Blob Messages:**
   - Out-of-band blob messages (`BlobMessage`, `FileSystemBlobTransferPolicy`) in ActiveMQ Classic are non-portable, lack clean garbage-collection guarantees, and complicate embedded deployments.

Apache ActiveMQ Artemis is the modern, next-generation message broker from the ActiveMQ project, designed from the ground up for asynchronous non-blocking I/O, low latency, native journal persistence, and Jakarta Messaging 3.x compliance.

## Decision

Migrate the embedded message queue broker in Apache James from ActiveMQ Classic to **Apache ActiveMQ Artemis**:

1. **Embedded Broker Architecture (`EmbeddedActiveMQ.java`):**
   - Use `org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ` running in-VM (`vm://0`).
   - Use Artemis native high-performance journal storage for bindings, journal, paging, and large messages (`setPersistenceEnabled(true)`).
   - Configure optimal embedded JMS client connection factory with `setConsumerWindowSize(0)` and `setBlockOnAcknowledge(true)`.

2. **Resolution of Delayed Delivery and Head-of-Line Blocking (JAMES-4192):**
   - ActiveMQ Artemis natively supports the Jakarta Messaging delivery delay specification via an internal dedicated scheduler (`ScheduledDeliveryHandler`).
   - Delayed and scheduled messages are held out-of-band by the scheduler and do not occupy active queue paging buffers (`maxPageSize`), completely eliminating the consumer starvation and head-of-line blocking defect seen in ActiveMQ Classic.
   - Delayed messages remain tracked on the destination (accessible via `scheduledCount`), preventing inconsistencies between queue reporting and delivery state.

3. **JMS Mail Queue Implementation:**
   - Standardize on standard Jakarta JMS `ObjectMessage` / `BytesMessage` instead of ActiveMQ proprietary `BlobMessage`.
   - Large messages are handled natively and transparently by Artemis file streaming (`setLargeMessagesDirectory`) without requiring custom blob transfer protocols.
   - Enforce strict compliance with JMS identifier rules for message properties (`AMQ139012`), escaping dots and hyphens into hexadecimal sequences.

4. **Metrics & Health Check:**
   - ActiveMQ Classic's `StatisticsBrokerPlugin` (request-reply destination statistics) is specific to Classic and omitted in Artemis. Legacy collector is substituted by a safe no-op implementation in favor of native Artemis JMX / Management APIs.
   - Maintain `ActiveMQHealthCheck` verifying connectivity and session creation.

## Consequences

- **Performance:** Substantially higher spooling throughput (scaling from ~350-400 msgs/s on ActiveMQ Classic to 1,700–2,000+ msgs/s with Artemis) with minimal spool latency.
- **Reliability:** Complete elimination of [JAMES-4192](https://issues.apache.org/jira/browse/JAMES-4192) head-of-line blocking during temporary remote delivery failures (greylisting/421 backoff).
- **Large Messages:** Robust and standardized large payload streaming without custom blob store lifecycles.
- **Migration & Upgrade:** Artemis uses an independent, high-performance journal format and cannot directly parse legacy KahaDB journals. Operators must drain/flush existing mail queues before upgrading James versions.

## References

- [JAMES-4192: ActiveMQ MailQueue cannot dequeue when lots of delayed mails](https://issues.apache.org/jira/browse/JAMES-4192)
- [Apache James PR #3194](https://github.com/apache/james-project/pull/3194)
- [Artemis Scheduled Message Architecture](https://activemq.apache.org/components/artemis/documentation/latest/scheduled-messages.html)
