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
   - Configure the embedded JMS client connection factory with `setConsumerWindowSize(0)`; `blockOnDurableSend` and `blockOnAcknowledge` are driven by configuration (`activemq.properties`) and default to `false` (durability is guaranteed by the transactional journal sync, see `artemis.journal.sync.transactional`).

2. **Delayed Delivery Handling (JAMES-4192):**
   - The root cause of JAMES-4192 is a JMS **consumer selector** (`JAMES_NEXT_DELIVERY <= now OR FORCE_DELIVERY = true`): a broker evaluates selectors only against in-memory messages, so once paging starts, ready mail sitting in page files behind rejected delayed mail is never delivered (head-of-line blocking / starvation). The fix removes selector evaluation from the delivery path entirely.
   - Delayed mail is routed to a dedicated companion queue (`<queueName>-delayed`) as **ordinary** (non-scheduled) JMS messages. A background task moves *due* mail to the main queue; the main queue is consumed with **no selector** (`getMessageSelector()` returns `null`) at wire speed. Ready mail is therefore never blocked by delayed mail.
   - The transfer task itself is **paging-safe**: it consumes the delayed queue **without a selector**, evaluates `JAMES_NEXT_DELIVERY <= now` **in memory**, and re-enqueues not-yet-due messages within the same transacted session (bounded per pass by a batch limit). It never asks the broker to evaluate a selector against paged-out messages, so the JAMES-4192 starvation cannot reappear inside the delayed queue.
   - Artemis **native** scheduled delivery (`_AMQ_SCHED_DELIVERY` / `setDeliveryDelay`) was deliberately **not** used: natively-scheduled messages are hidden from `browse()`/`getSize()` and their body is truncated by the management API (ARTEMIS-3141/3128/3175), which would break the `ManageableMailQueue` contract for delayed mail (browse, size, remove, flush). Keeping delayed mail as ordinary messages on a companion queue preserves full manageability.
   - `getSize()`, `browse()`, `remove()`, `clear()` and `flush()` span both queues, so the `ManageableMailQueue` contract holds for delayed mail; `flush()` force-delivers delayed mail by draining the companion queue (selector-less) and re-enqueuing with `FORCE_DELIVERY`.

3. **JMS Mail Queue Implementation:**
   - Standardize on standard Jakarta JMS `ObjectMessage` / `BytesMessage` instead of ActiveMQ proprietary `BlobMessage`.
   - Large messages are handled natively and transparently by Artemis file streaming (`setLargeMessagesDirectory`) without requiring custom blob transfer protocols.
   - Enforce strict compliance with JMS identifier rules for message properties (`AMQ139012`), escaping dots and hyphens into hexadecimal sequences.

4. **Metrics & Health Check:**
   - ActiveMQ Classic's `StatisticsBrokerPlugin` (request-reply destination statistics) is specific to Classic and omitted in Artemis. Legacy collector is substituted by a safe no-op implementation in favor of native Artemis JMX / Management APIs.
   - Maintain `ActiveMQHealthCheck` verifying connectivity and session creation.

## Consequences

- **Performance:** Substantially higher spooling throughput (scaling from ~350-400 msgs/s on ActiveMQ Classic to 1,700–2,000+ msgs/s with Artemis) with minimal spool latency.
- **Reliability:** The paged-out selector evaluation problem (JAMES-4192) is resolved by removing the selector from the delivery path and confining delayed mail to a companion queue with a paging-safe, selector-less transfer; see the "Delayed Delivery Handling" decision above. Trade-off: a background transfer task adds up to one transfer-interval of latency before a due mail is delivered, and re-enqueues not-yet-due messages each pass (bounded by a batch limit).
- **Large Messages:** Robust and standardized large payload streaming without custom blob store lifecycles.
- **Migration & Upgrade:** Artemis uses an independent, high-performance journal format and cannot directly parse legacy KahaDB journals. Operators must drain/flush existing mail queues before upgrading James versions.

## References

- [JAMES-4192: ActiveMQ MailQueue cannot dequeue when lots of delayed mails](https://issues.apache.org/jira/browse/JAMES-4192)
- [Apache James PR #3194](https://github.com/apache/james-project/pull/3194)
- [Artemis Scheduled Message Architecture](https://activemq.apache.org/components/artemis/documentation/latest/scheduled-messages.html)
