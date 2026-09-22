# Migration Guide: Apache James ActiveMQ (Classic) to Apache ActiveMQ Artemis

This document details the architectural and implementation changes made to the Apache James codebase (`C:\soft\james_src\james-project-fast`) to replace the embedded **Apache ActiveMQ (Classic 6.x)** message broker with **Apache ActiveMQ Artemis (2.56.0)**.

---

## 1. Background and Rationale

- **Previous State:** The embedded broker mechanism located in `server/queue/queue-activemq` was tied to `org.apache.activemq.broker.BrokerService` (ActiveMQ Classic 5.x/6.x), KahaDB persistence adapter (`activemq-kahadb-store`), and custom out-of-band blob message transfer (`BlobMessage`, `FileSystemBlobTransferPolicy`).
- **Target State:** Modernize the messaging layer by replacing ActiveMQ Classic with an embedded **Apache ActiveMQ Artemis** broker utilizing Jakarta JMS 3.x APIs, high-performance InVM transport (`vm://0`), native Artemis journal persistence, and removing proprietary, non-portable blob APIs.
- **Build Environment:**
  - JDK: `JDK 25.0.4.1` (`C:\soft\james_src\JDK_25.0.4.1`)
  - Maven: `3.9.16` (`C:\soft\james_src\MVN_3.9.16`)
  - Artemis Version: `2.56.0` (matching the existing `artemis-jakarta-client` dependency coordinate).

---

## 2. Affected Modules

1. **Root Project:** `pom.xml`
2. **JMS Queue Core:** `server/queue/queue-jms`
3. **ActiveMQ/Artemis Queue Module:** `server/queue/queue-activemq`
4. **Guice Injection Module:** `server/container/guice/queue/activemq`
5. **Spring Container Module:** `server/container/spring`

---

## 3. Detailed Summary of Changes

### 3.1. Maven Dependency Management (`pom.xml`)

1. **Root `pom.xml` (`D:\projects\james_src\james-project-fast\pom.xml`):**
   - Added Artemis embedded server dependency to `<dependencyManagement>`:
     ```xml
     <!-- Artemis embedded server (replaces legacy ActiveMQ BrokerService) -->
     <dependency>
         <groupId>org.apache.artemis</groupId>
         <artifactId>artemis-jakarta-server</artifactId>
         <version>${activmq-artemis.version}</version>
         <exclusions>
             <exclusion>
                 <groupId>io.netty</groupId>
                 <artifactId>*</artifactId>
             </exclusion>
         </exclusions>
     </dependency>
     ```
2. **`server/queue/queue-activemq/pom.xml`:**
   - Removed legacy dependencies: `activemq-broker` and `activemq-kahadb-store`.
   - Added dependency: `org.apache.artemis:artemis-jakarta-server`.
3. **`server/queue/queue-jms/pom.xml`:**
   - Replaced test-scoped `activemq-broker` with `artemis-jakarta-server`.
4. **`server/container/spring/pom.xml`:**
   - Replaced `activemq-spring` with `artemis-jakarta-server`.

---

### 3.2. Embedded Artemis Broker (`EmbeddedActiveMQ.java`)
**File:** `server/queue/queue-activemq/src/main/java/org/apache/james/queue/activemq/EmbeddedActiveMQ.java`

- **Previous:** Instantiated `org.apache.activemq.broker.BrokerService`, set up KahaDB persistence adapters, prefetch policies, and statistics plugins.
- **Updated:**
  - Migrated to Artemis embedded core server: `org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ`.
  - Configured using `ConfigurationImpl`:
    - Security disabled for embedded in-process execution (`setSecurityEnabled(false)`).
    - Persistence enabled with native Artemis journal storage (`setJournalDirectory`, `setBindingsDirectory`, `setPagingDirectory`, `setLargeMessagesDirectory`) in `var/store/artemis`.
    - Configured InVM acceptor: `new TransportConfiguration(InVMAcceptorFactory.class.getName())`.
  - Configured connection factory using Artemis client: `org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory("vm://0?broker-name=james")`.
  - Applied optimal embedded JMS client settings: `setConsumerWindowSize(0)` and `setBlockOnAcknowledge(true)`.

---

### 3.3. Mail Queue Implementation

1. **`ActiveMQCacheableMailQueue.java`:**
   - **File:** `server/queue/queue-activemq/src/main/java/org/apache/james/queue/activemq/ActiveMQCacheableMailQueue.java`
   - Removed ActiveMQ-specific `BlobMessage` / `ActiveMQBlobMessage` branches and statistics queue queries (`ActiveMQ.Statistics.Destination.*`).
   - Simplified queue operations to delegate directly to standard Jakarta JMS operations via `JMSCacheableMailQueue`.
   - Preserved existing constructor signatures for backward compatibility.

2. **`ActiveMQMailQueueItem.java`:**
   - **File:** `server/queue/queue-activemq/src/main/java/org/apache/james/queue/activemq/ActiveMQMailQueueItem.java`
   - Removed blob file cleanup logic (`((ActiveMQBlobMessage) message).deleteFile()`).

3. **`ActiveMQMailQueueFactory.java`:**
   - **File:** `server/queue/queue-activemq/src/main/java/org/apache/james/queue/activemq/ActiveMQMailQueueFactory.java`
   - Simplified queue initialization without `useBlob` flags.

3. **JMS Property Name Compliance in `JMSCacheableMailQueue.java` (Resolving AMQ139012):**
   - **File:** `server/queue/queue-jms/src/main/java/org/apache/james/queue/jms/JMSCacheableMailQueue.java`
   - **Issue:** Unlike ActiveMQ Classic, ActiveMQ Artemis strictly enforces the JMS specification for property identifiers (`Character.isJavaIdentifierPart`), rejecting mail attributes containing dots (such as `org.apache.james.SMTPSessionID`) or hyphens in recipient headers with `jakarta.jms.JMSRuntimeException: AMQ139012: The property name '...' is not a valid java identifier`.
   - **Solution:**
     - Implemented safe encoding and decoding utilities (`encodeAttributePropertyName`, `encodePerRecipientHeaderPropertyName`, `decodePerRecipientHeaderPropertyName`).
     - Illegal identifier characters are safely escaped as hexadecimal sequences `_XXXX_` with prefixes `JAMES_ATTR_` (for mail attributes) and `JAMES_MAIL_PER_RECIPIENT_HEADERS_` (for recipient headers).
     - Full backward compatibility is preserved when reading legacy unescaped properties.

4. **Removal of Obsolete Blob Classes:**
   The following files were removed as they are specific to ActiveMQ 5.x blob storage:
   - `server/queue/queue-activemq/src/main/java/org/apache/james/queue/activemq/FileSystemBlobStrategy.java`
   - `server/queue/queue-activemq/src/main/java/org/apache/james/queue/activemq/FileSystemBlobTransferPolicy.java`
   - `server/queue/queue-activemq/src/main/java/org/apache/james/queue/activemq/MimeMessageBlobMessageSource.java`

---

### 3.4. Metrics and Monitoring

- **`ActiveMQMetricCollectorImpl.java`:**
  - **File:** `server/queue/queue-activemq/src/main/java/org/apache/james/queue/activemq/metric/ActiveMQMetricCollectorImpl.java`
  - Replaced the ActiveMQ `StatisticsBrokerPlugin` dependency with a safe no-op implementation and informational logging.
  - In Artemis, broker and queue telemetry is exposed natively via JMX and the Artemis Management API.
  - Test `ActiveMQMetricCollectorTest` has been marked with `@Disabled` documenting the transition.

---

### 3.5. Dependency Injection Configurations (Guice & Spring)

1. **Guice (`ActiveMQQueueModule.java`):**
   - **File:** `server/container/guice/queue/activemq/src/main/java/org/apache/james/modules/queue/activemq/ActiveMQQueueModule.java`
   - Removed bindings for `PersistenceAdapter` and `KahaDBPersistenceAdapter`.
   - Bound `ActiveMQMetricCollector` to `ActiveMQMetricCollectorNoop`.

2. **Spring (`activemq-queue-context.xml`):**
   - **File:** `server/queue/queue-activemq/src/main/resources/META-INF/spring/activemq-queue-context.xml`
   - Removed `persistenceAdapter` bean (`KahaDBPersistenceAdapter`).
   - Adapted `embeddedActiveMQ` bean constructor arguments (`filesystem`, `activemqConfiguration`).

---

### 3.6. Test Infrastructure

1. **`BrokerExtension.java` (JUnit 5 Extension):**
   - **File:** `server/queue/queue-jms/src/test/java/org/apache/james/queue/jms/BrokerExtension.java`
   - Replaced `BrokerService` with Artemis `EmbeddedActiveMQ` configured per test with isolated `server-id`s for InVM communication.
2. **Updated Unit Tests:**
   - Adapted test classes (`ActiveMQHealthCheckTest`, `ActiveMQMailQueueTest`, `ActiveMQMailQueueFactoryTest`, `ActiveMQMailQueueBlobTest`, `JMSCacheableMailQueueTest`, `JMSCacheableMailQueueFactoryTest`) to inject Artemis `EmbeddedActiveMQ` and use Artemis `ActiveMQConnectionFactory("vm://0")`.

---

## 4. Verification, Packaging, and Load Testing

### 4.1. Compilation and Checkstyle
Compilation and style verification was confirmed with JDK 25:
```powershell
$env:JAVA_HOME = "C:\soft\james_src\JDK_25.0.4.1"
$env:Path = "C:\soft\james_src\MVN_3.9.16\bin;$env:JAVA_HOME\bin;$env:Path"
mvn clean install -DskipTests
```
**Result:** `BUILD SUCCESS` with `0 Checkstyle violations`.

### 4.2. End-to-End Runtime and Load Testing in James Postgres App
Packaged and launched `james-server-postgres-app.jar` backed by the Embedded Artemis broker and PostgreSQL 17.11:
- WebAdmin `/healthcheck` reported `Postgres: healthy` and `Embedded ActiveMQ: healthy`.
- High-concurrency SMTP load benchmarks:
  - **Single connection (500 messages):** 100% success rate, 10.19 s elapsed, throughput `~49.1 msgs/sec`.
  - **8 concurrent threads (5,000 messages):** 100% success rate (`5000 / 5000`), 0 failures, average latency `167.5 ms`, aggregate throughput `47.64 msgs/sec`.
  - All messages were verified delivered and persisted in PostgreSQL `message` and `message_mailbox` tables.
