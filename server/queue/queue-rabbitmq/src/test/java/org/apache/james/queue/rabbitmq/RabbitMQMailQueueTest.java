/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.queue.rabbitmq;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.apache.james.backends.cassandra.Scenario.Builder.executeNormally;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.apache.james.backends.cassandra.Scenario.Builder.returnEmpty;
import static org.apache.james.backends.cassandra.StatementRecorder.Selector.preparedStatementStartingWith;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.queue.api.Mails.defaultMail;
import static org.apache.james.queue.api.Mails.defaultMailNoRecipient;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.BlobTables;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStoreFactory;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.metrics.api.Gauge;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueMetricContract;
import org.apache.james.queue.api.MailQueueMetricExtension;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueueContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.rabbitmq.view.RabbitMQMailQueueConfiguration;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewTestFactory;
import org.apache.james.queue.rabbitmq.view.cassandra.EnqueuedMailsDAO;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice;
import org.apache.james.util.streams.Iterators;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

import com.github.fge.lambdas.Throwing;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

class RabbitMQMailQueueTest {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final int THREE_BUCKET_COUNT = 3;
    private static final int UPDATE_BROWSE_START_PACE = 25;
    private static final Duration ONE_HOUR_SLICE_WINDOW = Duration.ofHours(1);
    private static final org.apache.james.queue.api.MailQueueName SPOOL = org.apache.james.queue.api.MailQueueName.of("spool");
    private static final Instant IN_SLICE_1 = Instant.now().minus(60, DAYS);
    private static final Instant IN_SLICE_2 = IN_SLICE_1.plus(1, HOURS);
    private static final Instant IN_SLICE_3 = IN_SLICE_1.plus(2, HOURS);
    private static final Instant IN_SLICE_5 = IN_SLICE_1.plus(4, HOURS);
    private static final Instant IN_SLICE_7 = IN_SLICE_1.plus(6, HOURS);
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraBlobModule.MODULE,
        CassandraMailQueueViewModule.MODULE,
        CassandraEventStoreModule.MODULE(),
        CassandraSchemaVersionModule.MODULE));

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    private RabbitMQMailQueueFactory mailQueueFactory;
    private UpdatableTickingClock clock;
    private RabbitMQMailQueue mailQueue;
    private RabbitMQMailQueueManagement mqManagementApi;

    @AfterEach
    void tearDown() {
        mqManagementApi.deleteAllQueues();
    }

    @Nested
    class MailQueueSizeMetricsEnabled implements ManageableMailQueueContract, MailQueueMetricContract {
        @BeforeEach
        void setup(CassandraCluster cassandra,
                   MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) throws Exception {
            setUp(cassandra,
                metricTestSystem,
                RabbitMQMailQueueConfiguration.builder()
                    .sizeMetricsEnabled(true)
                    .build(),
                CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
                    .passthrough(),
                MailQueueFactory.prefetchCount(3));
        }

        @Override
        public void enQueue(Mail mail) throws MailQueue.MailQueueException {
            ManageableMailQueueContract.super.enQueue(mail);
            clock.tick();
        }

        @Override
        public RabbitMQMailQueue getMailQueue() {
            return mailQueue;
        }

        @Override
        public ManageableMailQueue getManageableMailQueue() {
            return mailQueue;
        }

        @Test
        void browseShouldReturnCurrentlyEnqueuedMailFromAllSlices() throws Exception {
            ManageableMailQueue mailQueue = getManageableMailQueue();
            int emailCount = 5;

            clock.setInstant(IN_SLICE_1);
            enqueueSomeMails(namePatternForSlice(1), emailCount);

            clock.setInstant(IN_SLICE_2);
            enqueueSomeMails(namePatternForSlice(2), emailCount);

            clock.setInstant(IN_SLICE_3);
            enqueueSomeMails(namePatternForSlice(3), emailCount);

            clock.setInstant(IN_SLICE_5);
            enqueueSomeMails(namePatternForSlice(5), emailCount);

            clock.setInstant(IN_SLICE_7);
            Stream<String> names = Iterators.toStream(mailQueue.browse())
                .map(ManageableMailQueue.MailQueueItemView::getMail)
                .map(Mail::getName);

            assertThat(names).containsExactly(
                "1-1", "1-2", "1-3", "1-4", "1-5",
                "2-1", "2-2", "2-3", "2-4", "2-5",
                "3-1", "3-2", "3-3", "3-4", "3-5",
                "5-1", "5-2", "5-3", "5-4", "5-5");
        }

        @Test
        void browseStartShouldBeUpdated(CassandraCluster cassandraCluster, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) throws Exception {
            setUp(cassandraCluster, metricTestSystem,
                RabbitMQMailQueueConfiguration.builder()
                    .sizeMetricsEnabled(true)
                    .build(),
                CassandraBlobStoreFactory.forTesting(cassandraCluster.getConf(), new RecordingMetricFactory())
                    .passthrough(),
                MailQueueFactory.prefetchCount(1));

            int emailCount = 250;

            StatementRecorder.Selector selector = preparedStatementStartingWith("UPDATE browsestart");
            StatementRecorder statementRecorder = cassandraCluster.getConf()
                .recordStatements(selector);

            clock.setInstant(IN_SLICE_1);
            enqueueSomeMails(namePatternForSlice(1), emailCount);
            dequeueMails(emailCount);

            clock.setInstant(IN_SLICE_2);
            enqueueSomeMails(namePatternForSlice(2), emailCount);
            dequeueMails(emailCount);

            clock.setInstant(IN_SLICE_3);
            enqueueSomeMails(namePatternForSlice(3), emailCount);
            dequeueMails(emailCount);

            // The actual rate of update should actually be lower than the update probability.
            assertThat(statementRecorder.listExecutedStatements(selector))
                .hasSizeBetween(2, 12);
        }

        @Test
        void contentStartShouldBeUpdated(CassandraCluster cassandraCluster, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) throws Exception {
            setUp(cassandraCluster, metricTestSystem,
                RabbitMQMailQueueConfiguration.builder()
                    .sizeMetricsEnabled(true)
                    .build(),
                CassandraBlobStoreFactory.forTesting(cassandraCluster.getConf(), new RecordingMetricFactory())
                    .passthrough(),
                MailQueueFactory.prefetchCount(1));

            int emailCount = 250;

            StatementRecorder.Selector selector = preparedStatementStartingWith("UPDATE contentstart");
            StatementRecorder statementRecorder = cassandraCluster.getConf().recordStatements(selector);

            clock.setInstant(IN_SLICE_1);
            enqueueSomeMails(namePatternForSlice(1), emailCount);
            dequeueMails(emailCount);

            clock.setInstant(IN_SLICE_2);
            enqueueSomeMails(namePatternForSlice(2), emailCount);
            dequeueMails(emailCount);

            clock.setInstant(IN_SLICE_3);
            enqueueSomeMails(namePatternForSlice(3), emailCount);
            dequeueMails(emailCount);

            // The actual rate of update should actually be lower than the update probability.
            assertThat(statementRecorder.listExecutedStatements(selector))
                .hasSizeBetween(2, 12);
        }

        @Test
        void dequeueShouldDeleteBlobs(CassandraCluster cassandra) throws Exception {
            String name1 = "myMail1";
            Flux<MailQueue.MailQueueItem> dequeueFlux = Flux.from(getMailQueue().deQueue());
            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            dequeueFlux.take(1)
                .flatMap(mailQueueItem -> Mono.fromCallable(() -> {
                    mailQueueItem.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
                    return mailQueueItem;
                }).subscribeOn(Schedulers.fromExecutor(EXECUTOR))).blockLast(Duration.ofSeconds(10));

            assertThat(cassandra.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
                .all().build()))
                .isEmpty();
        }

        @Test
        void clearShouldDeleteBlobs(CassandraCluster cassandra) throws Exception {
            String name1 = "myMail1";
            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getManageableMailQueue().clear();

            assertThat(cassandra.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
                .all().build()))
                .isEmpty();
        }

        @Test
        void removeByNameShouldDeleteBlobs(CassandraCluster cassandra) throws Exception {
            String name1 = "myMail1";
            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getManageableMailQueue().remove(ManageableMailQueue.Type.Name, name1);

            assertThat(cassandra.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
                .all().build()))
                .isEmpty();
        }

        @Test
        void removeByRecipientShouldDeleteBlobs(CassandraCluster cassandra) throws Exception {
            String name1 = "myMail1";
            getMailQueue().enQueue(defaultMailNoRecipient()
                .name(name1)
                .recipient(RECIPIENT1)
                .build());

            getManageableMailQueue().remove(ManageableMailQueue.Type.Recipient, RECIPIENT1.asString());

            assertThat(cassandra.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
                .all().build()))
                .isEmpty();
        }

        @Test
        void removeBySenderShouldDeleteBlobs(CassandraCluster cassandra) throws Exception {
            String name1 = "myMail1";
            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .sender(SENDER)
                .build());

            getManageableMailQueue().remove(ManageableMailQueue.Type.Sender, SENDER.asString());

            assertThat(cassandra.getConf().execute(selectFrom(BlobTables.DefaultBucketBlobTable.TABLE_NAME)
                .all().build()))
                .isEmpty();
        }

        @Test
        void browseAndDequeueShouldCombineWellWhenDifferentSlices() throws Exception {
            ManageableMailQueue mailQueue = getManageableMailQueue();
            int emailCount = 5;

            clock.setInstant(IN_SLICE_1);
            enqueueSomeMails(namePatternForSlice(1), emailCount);

            clock.setInstant(IN_SLICE_2);
            enqueueSomeMails(namePatternForSlice(2), emailCount);

            clock.setInstant(IN_SLICE_3);
            enqueueSomeMails(namePatternForSlice(3), emailCount);

            clock.setInstant(IN_SLICE_5);
            enqueueSomeMails(namePatternForSlice(5), emailCount);

            clock.setInstant(IN_SLICE_7);
            dequeueMails(5);
            dequeueMails(5);
            dequeueMails(3);

            Stream<String> names = Iterators.toStream(mailQueue.browse())
                .map(ManageableMailQueue.MailQueueItemView::getMail)
                .map(Mail::getName);

            assertThat(names)
                .containsExactly("3-4", "3-5", "5-1", "5-2", "5-3", "5-4", "5-5");
        }

        @Test
        void enqueuedEmailsShouldEventuallyBeCleaned() {
            ManageableMailQueue mailQueue = getManageableMailQueue();
            int emailCount = 100;

            clock.setInstant(IN_SLICE_1);
            enqueueSomeMails(namePatternForSlice(1), emailCount);

            clock.setInstant(IN_SLICE_2);
            enqueueSomeMails(namePatternForSlice(2), emailCount);

            clock.setInstant(IN_SLICE_3);
            enqueueSomeMails(namePatternForSlice(3), emailCount);

            clock.setInstant(IN_SLICE_5);
            enqueueSomeMails(namePatternForSlice(5), emailCount);

            clock.setInstant(IN_SLICE_7);
            dequeueMails(emailCount);
            dequeueMails(emailCount);
            dequeueMails(emailCount);
            dequeueMails(emailCount);

            // ensure slice 1 was cleaned
            EnqueuedMailsDAO mailsDAO = new EnqueuedMailsDAO(cassandraCluster.getCassandraCluster().getConf(), new HashBlobId.Factory());
            MailQueueName queueName = MailQueueName.fromString(mailQueue.getName().asString());
            Slice slice = Slice.of(currentSliceStartInstant(IN_SLICE_1));

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(mailsDAO.selectEnqueuedMails(queueName, slice, BucketedSlices.BucketId.of(0))
                    .collectList()
                    .block())
                    .isEmpty();
                softly.assertThat(mailsDAO.selectEnqueuedMails(queueName, slice, BucketedSlices.BucketId.of(1))
                    .collectList()
                    .block())
                    .isEmpty();
                softly.assertThat(mailsDAO.selectEnqueuedMails(queueName, slice, BucketedSlices.BucketId.of(2))
                    .collectList()
                    .block())
                    .isEmpty();
            });
        }

        private Instant currentSliceStartInstant(Instant instant) {
            long sliceSize = ONE_HOUR_SLICE_WINDOW.getSeconds();
            long sliceId = instant.getEpochSecond() / sliceSize;
            return Instant.ofEpochSecond(sliceId * sliceSize);
        }

        private Function<Integer, String> namePatternForSlice(int sliceId) {
            return i -> sliceId + "-" + i;
        }

        @Test
        void mailQueueShouldBeInitializedWhenCreating(CassandraCluster cassandra) {
            org.apache.james.queue.api.MailQueueName name = org.apache.james.queue.api.MailQueueName.of("myQueue");
            mailQueueFactory.createQueue(name);

            boolean initialized = CassandraMailQueueViewTestFactory.isInitialized(cassandra.getConf(), MailQueueName.fromString(name.asString()));
            assertThat(initialized).isTrue();
        }

        @Test
        void enQueueShouldNotThrowOnMailNameWithNegativeHash() {
            String negativehashedString = "this sting will have a negative hash"; //hash value: -1256871313

            assertThatCode(() -> getMailQueue().enQueue(defaultMail().name(negativehashedString).build()))
                .doesNotThrowAnyException();
        }

        @Disabled("JAMES-2614 RabbitMQMailQueueTest::concurrentEnqueueDequeueShouldNotFail is unstable." +
            "The related test is disabled, and need to be re-enabled after investigation and a fix.")
        @Test
        @Override
        public void concurrentEnqueueDequeueShouldNotFail() {

        }

        @Test
        void dequeueShouldWorkAfterNetworkOutage() throws Exception {
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";

            List<MailQueue.MailQueueItem> receivedItem = new ArrayList<>();
            Flux.from(getMailQueue().deQueue())
                    .doOnNext(receivedItem::add)
                    .subscribe();

            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            rabbitMQExtension.getRabbitMQ().pause();
            Thread.sleep(2000);

            try {
                getMailQueue().enQueue(defaultMail()
                    .name(name2)
                    .build());
            } catch (Exception e) {
                // Expected
            } finally {
                rabbitMQExtension.getRabbitMQ().unpause();
                Thread.sleep(100);
            }

            getMailQueue().enQueue(defaultMail()
                .name(name3)
                .build());

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(receivedItem)
                            .extracting(item -> item.getMail().getName())
                            .contains(name1, name3));
        }

        @Test
        void enqueuedEmailsShouldNotBeLostDuringRabbitMQOutages() throws Exception {
            String name = "myMail";


            try {
                getMailQueue().enQueue(defaultMail()
                        .name(name)
                        .build());
            } catch (Exception e) {
                // Ignore
            }
            rabbitMQExtension.managementAPI().purgeQueue(rabbitMQExtension.getRabbitMQ().getConfiguration().getVhost().orElse("/"),
                    "JamesMailQueue-workqueue-spool");

            getMailQueue().republishNotProcessedMails(clock.instant().plus(30, ChronoUnit.MINUTES)).blockLast();

            Flux<MailQueue.MailQueueItem> dequeueFlux = Flux.from(getMailQueue().deQueue());

            List<MailQueue.MailQueueItem> items = dequeueFlux.take(1)
                    .collectList().block(Duration.ofSeconds(10));

            assertThat(items)
                    .extracting(item -> item.getMail().getName())
                    .containsOnly(name);
        }

        @Test
        void messagesShouldSurviveRabbitMQRestart() throws Exception {
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";
            Flux<MailQueue.MailQueueItem> dequeueFlux = Flux.from(getMailQueue().deQueue());

            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name2)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name3)
                .build());

            rabbitMQExtension.getRabbitMQ().restart();

            List<MailQueue.MailQueueItem> items = dequeueFlux.take(3).collectList().block(Duration.ofSeconds(10));

            assertThat(items)
                .extracting(item -> item.getMail().getName())
                .containsExactly(name1, name2, name3);
        }

        @Test
        void messagesShouldBeProcessedAfterNotPublishedMailsHaveBeenReprocessed() throws Exception {
            clock.setInstant(Instant.now().minus(Duration.ofHours(2)));
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";
            Flux<MailQueue.MailQueueItem> dequeueFlux = Flux.from(getMailQueue().deQueue());

            // Avoid early processing and prefetching
            Sender sender = rabbitMQExtension.getSender();

            suspendDequeuing(sender);

            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name2)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name3)
                .build());

            resumeDequeuing(sender);
            assertThat(getMailQueue()
                    .republishNotProcessedMails(Instant.now().minus(Duration.ofHours(1)))
                    .collectList()
                    .block())
                .containsExactlyInAnyOrder(name1, name2, name3);

            List<MailQueue.MailQueueItem> items = dequeueFlux.take(Duration.ofSeconds(10)).collectList().block();

            assertThat(items)
                .extracting(item -> item.getMail().getName())
                .containsExactlyInAnyOrder(name1, name2, name3);
        }

        @Test
        void onlyOldMessagesShouldBeProcessedAfterNotPublishedMailsHaveBeenReprocessed() throws Exception {
            clock.setInstant(Instant.now().minus(Duration.ofHours(2)));
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";
            Flux<MailQueue.MailQueueItem> dequeueFlux = Flux.from(getMailQueue().deQueue());

            // Avoid early processing and prefetching
            Sender sender = rabbitMQExtension.getSender();

            suspendDequeuing(sender);

            getMailQueue().enQueue(defaultMail()
                    .name(name1)
                    .build());

            getMailQueue().enQueue(defaultMail()
                    .name(name2)
                    .build());

            clock.setInstant(Instant.now());
            getMailQueue().enQueue(defaultMail()
                    .name(name3)
                    .build());

            resumeDequeuing(sender);
            assertThat(getMailQueue()
                    .republishNotProcessedMails(Instant.now().minus(Duration.ofHours(1)))
                    .collectList()
                    .block())
                .containsExactlyInAnyOrder(name1, name2);

            List<MailQueue.MailQueueItem> items = dequeueFlux.take(Duration.ofSeconds(10)).collectList().block();

            assertThat(items)
                    .extracting(item -> item.getMail().getName())
                    .containsExactlyInAnyOrder(name1, name2);
        }

        @Test
        void messagesShouldBeProcessedAfterTwoMailsReprocessing() throws Exception {
            clock.setInstant(Instant.now().minus(Duration.ofHours(2)));
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";
            Flux<MailQueue.MailQueueItem> dequeueFlux = Flux.from(getMailQueue().deQueue());

            // Avoid early processing and prefetching
            Sender sender = rabbitMQExtension.getSender();

            suspendDequeuing(sender);

            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name2)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name3)
                .build());

            assertThat(getMailQueue()
                    .republishNotProcessedMails(Instant.now().minus(Duration.ofHours(1)))
                    .collectList()
                    .block())
                .containsExactlyInAnyOrder(name1, name2, name3);
            resumeDequeuing(sender);
            assertThat(getMailQueue()
                    .republishNotProcessedMails(Instant.now().minus(Duration.ofHours(1)))
                    .collectList()
                    .block())
                .containsExactlyInAnyOrder(name1, name2, name3);

            List<MailQueue.MailQueueItem> items = dequeueFlux.take(Duration.ofSeconds(10)).collectList().block();

            assertThat(items)
                .extracting(item -> item.getMail().getName())
                .containsExactlyInAnyOrder(name1, name2, name3);
        }

        @Test
        void messagesShouldBeProcessedAfterNotPublishedMailsHaveBeenReprocessedAndNewMessagesShouldNotBeLost() throws Exception {
            clock.setInstant(Instant.now().minus(Duration.ofHours(2)));
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";
            Flux<MailQueue.MailQueueItem> dequeueFlux = Flux.from(getMailQueue().deQueue());

            // Avoid early processing and prefetching
            Sender sender = rabbitMQExtension.getSender();

            suspendDequeuing(sender);
            //mail send when rabbit down
            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());
            resumeDequeuing(sender);

            //mail send when rabbit is up again and before rebuild
            clock.setInstant(Instant.now());
            getMailQueue().enQueue(defaultMail()
                .name(name3)
                .build());

            Flux.merge(
                Mono.fromCallable(() -> {
                    //mail send concurently with rebuild
                    getMailQueue().enQueue(defaultMail()
                        .name(name2)
                        .build());
                    return true;

                }).subscribeOn(Schedulers.fromExecutor(EXECUTOR)),
                Mono.fromRunnable(() ->
                    assertThat(getMailQueue()
                        .republishNotProcessedMails(Instant.now().minus(Duration.ofHours(1)))
                        .collectList()
                        .block())
                        .containsOnly(name1)).subscribeOn(Schedulers.fromExecutor(EXECUTOR)))
            .then()
            .block(Duration.ofSeconds(10));

            List<MailQueue.MailQueueItem> items = dequeueFlux.take(Duration.ofSeconds(10)).collectList().block();

            assertThat(items)
                .extracting(item -> item.getMail().getName())
                .containsExactlyInAnyOrder(name1, name2, name3);
        }

        private void enqueueSomeMails(Function<Integer, String> namePattern, int emailCount) {
            IntStream.rangeClosed(1, emailCount)
                .forEach(Throwing.intConsumer(i -> {
                    FakeMail mail = defaultMail()
                        .name(namePattern.apply(i))
                        .build();
                    enQueue(mail);
                    LifecycleUtil.dispose(mail);
                }));
        }

        private void dequeueMails(int times) {
            AtomicInteger counter = new AtomicInteger(0);

            Disposable disposable = Flux.from(getManageableMailQueue()
                .deQueue())
                .concatMap(mailQueueItem -> Mono.fromCallable(() -> {
                    if (counter.getAndIncrement() < times) {
                        mailQueueItem.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
                        return mailQueueItem;
                    } else {
                        mailQueueItem.done(MailQueue.MailQueueItem.CompletionStatus.RETRY);
                        return null;
                    }
                }).subscribeOn(Schedulers.fromExecutor(EXECUTOR)))
                .subscribe();

            try {
                await()
                    .atMost(Duration.ofMinutes(10))
                    .untilAsserted(() -> assertThat(counter.get()).isGreaterThanOrEqualTo(times));
            } finally {
                disposable.dispose();
            }
        }

        @Test
        void dequeueShouldRetryLoadingErrors(CassandraCluster cassandra) throws Exception {
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";

            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name2)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name3)
                .build());

            cassandra.getConf().registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("SELECT * FROM blobs WHERE id=:id;"));

            List<MailQueue.MailQueueItem> items = Flux.from(getMailQueue().deQueue())
                .take(3)
                .collectList()
                .block(Duration.ofSeconds(10));

            assertThat(items)
                .extracting(item -> item.getMail().getName())
                .containsOnly(name1, name2, name3);
        }

        @Test
        void dequeueShouldNotRetryWhenBlobIsMissing(CassandraCluster cassandra) throws Exception {
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";

            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name2)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name3)
                .build());

            cassandra.getConf().registerScenario(returnEmpty()
                .forever()
                .whenQueryStartsWith("SELECT * FROM blobs WHERE id=:id"));

            ConcurrentLinkedDeque<String> dequeuedNames = new ConcurrentLinkedDeque<>();
            Flux.from(getMailQueue().deQueue())
                .take(3)
                .doOnNext(item -> dequeuedNames.add(item.getMail().getName()))
                .flatMap(item -> Mono.fromRunnable(Throwing.runnable(() -> item.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS)))
                    .subscribeOn(Schedulers.fromExecutor(EXECUTOR)))
                .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
                .subscribe();

            // One second should be enough to attempt dequeues while we fail to load blobs
            Thread.sleep(1000);

            // Restore normal behaviour
            cassandra.getConf().registerScenario(executeNormally()
                .forever()
                .whenQueryStartsWith("SELECT * FROM blobs WHERE id=:id"));

            // Let one second to check if the queue is empty
            Thread.sleep(1000);

            // We expect content missing blob references to be purged from the queue
            assertThat(dequeuedNames).isEmpty();
        }

        @Test
        void dequeueShouldNotAbortProcessingUponSerializationIssuesErrors() throws Exception {
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";

            String emptyRoutingKey = "";
            rabbitMQExtension.getSender()
                .send(Mono.just(new OutboundMessage("JamesMailQueue-exchange-spool",
                    emptyRoutingKey,
                    "BAD_PAYLOAD!".getBytes(StandardCharsets.UTF_8))))
                .block();

            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name2)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name3)
                .build());

            ConcurrentLinkedDeque<String> dequeuedMailNames = new ConcurrentLinkedDeque<>();

            Flux.from(getMailQueue().deQueue())
                .doOnNext(item -> dequeuedMailNames.add(item.getMail().getName()))
                .flatMap(item -> Mono.fromRunnable(Throwing.runnable(() -> item.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS)))
                    .subscribeOn(Schedulers.fromExecutor(EXECUTOR)))
                .subscribe();

            await().atMost(TEN_SECONDS)
                .untilAsserted(() -> assertThat(dequeuedMailNames)
                    .containsExactly(name1, name2, name3));
        }

        @Test
        void manyInvalidMessagesShouldNotAbortProcessing() throws Exception {
            String name1 = "myMail1";
            String name2 = "myMail2";
            String name3 = "myMail3";

            String emptyRoutingKey = "";

            IntStream.range(0, 100)
                .forEach(i -> rabbitMQExtension.getSender()
                    .send(Mono.just(new OutboundMessage("JamesMailQueue-exchange-spool",
                        emptyRoutingKey,
                        ("BAD_PAYLOAD " + i).getBytes(StandardCharsets.UTF_8))))
                    .block());

            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name2)
                .build());

            getMailQueue().enQueue(defaultMail()
                .name(name3)
                .build());

            ConcurrentLinkedDeque<String> dequeuedMailNames = new ConcurrentLinkedDeque<>();

            Flux.from(getMailQueue().deQueue())
                .doOnNext(item -> dequeuedMailNames.add(item.getMail().getName()))
                .flatMap(item -> Mono.fromRunnable(Throwing.runnable(() -> item.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS)))
                    .subscribeOn(Schedulers.fromExecutor(EXECUTOR)))
                .subscribe();

            await().atMost(TEN_SECONDS)
                .untilAsserted(() -> assertThat(dequeuedMailNames)
                    .containsExactly(name1, name2, name3));
        }

        @Test
        void invalidMessagesShouldBeDeadLettered() {
            String emptyRoutingKey = "";
            rabbitMQExtension.getSender()
                .send(Mono.just(new OutboundMessage("JamesMailQueue-exchange-spool",
                    emptyRoutingKey,
                    "BAD_PAYLOAD!".getBytes(StandardCharsets.UTF_8))))
                .block();

            AtomicInteger deadLetteredCount = new AtomicInteger();
            rabbitMQExtension.getRabbitChannelPool()
                .createReceiver()
                .consumeAutoAck("JamesMailQueue-dead-letter-queue-spool")
                .doOnNext(next -> deadLetteredCount.incrementAndGet())
                .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
                .subscribe();

            Flux.from(getMailQueue().deQueue())
                .doOnNext(Throwing.consumer(item -> item.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS)))
                .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
                .subscribe();


            await().atMost(TEN_SECONDS)
                .untilAsserted(() -> assertThat(deadLetteredCount.get()).isEqualTo(1));
        }

        @Test
        void rejectedMessagesShouldBeDeadLettered() throws Exception {
            String name1 = "myMail1";

            getMailQueue().enQueue(defaultMail()
                .name(name1)
                .build());

            getMailQueue().deQueue()
                .flatMap(item -> Mono.fromRunnable(Throwing.runnable(() -> item.done(MailQueue.MailQueueItem.CompletionStatus.REJECT)))
                    .subscribeOn(Schedulers.boundedElastic())
                    .thenReturn(item))
                .blockFirst();

            AtomicInteger deadLetteredCount = new AtomicInteger();
            rabbitMQExtension.getRabbitChannelPool()
                .createReceiver()
                .consumeAutoAck("JamesMailQueue-dead-letter-queue-spool")
                .doOnNext(next -> deadLetteredCount.incrementAndGet())
                .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
                .subscribe();

            await().atMost(TEN_SECONDS)
                .untilAsserted(() -> assertThat(deadLetteredCount.get()).isEqualTo(1));
        }

        private void resumeDequeuing(Sender sender) {
            sender.bindQueue(getMailQueueBindingSpecification()).block();
        }

        private void suspendDequeuing(Sender sender) {
            sender.unbindQueue(getMailQueueBindingSpecification()).block();
        }

        private BindingSpecification getMailQueueBindingSpecification() {
            MailQueueName mailQueueName = MailQueueName.fromString(getMailQueue().getName().asString());
            return BindingSpecification.binding()
                    .exchange(mailQueueName.toRabbitExchangeName().asString())
                    .queue(mailQueueName.toWorkQueueName().asString())
                    .routingKey(EMPTY_ROUTING_KEY);
        }
    }

    @Nested
    class MailQueueSizeMetricsDisabled {
        @RegisterExtension
        MailQueueMetricExtension mailQueueMetricExtension = new MailQueueMetricExtension();

        @BeforeEach
        void setup(CassandraCluster cassandra, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) throws Exception {
            setUp(cassandra,
                metricTestSystem,
                RabbitMQMailQueueConfiguration.builder()
                    .sizeMetricsEnabled(false)
                    .build(),
                CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
                    .passthrough(),
                MailQueueFactory.prefetchCount(3));
        }

        @Test
        void constructorShouldNotRegisterGetQueueSizeGaugeWhenSizeMetricsDisabled(MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) {
            ArgumentCaptor<Gauge<?>> gaugeCaptor = ArgumentCaptor.forClass(Gauge.class);
            verify(metricTestSystem.getSpyGaugeRegistry(), never()).register(any(), gaugeCaptor.capture());
        }
    }

    @Nested
    class DeDuplicationTest {
        @RegisterExtension
        MailQueueMetricExtension mailQueueMetricExtension = new MailQueueMetricExtension();

        @BeforeEach
        void setup(CassandraCluster cassandra, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) throws Exception {
            setUp(cassandra,
                metricTestSystem,
                RabbitMQMailQueueConfiguration.builder()
                    .sizeMetricsEnabled(true)
                    .build(),
                CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
                    .deduplication(),
                MailQueueFactory.prefetchCount(3));
        }

        @Test
        void dequeueShouldStillRetrieveAllBlobsWhenIdenticalContentAndDeduplication() throws Exception {
            String identicalContent = "identical content";
            String identicalSubject = "identical subject";

            mailQueue.enQueue(defaultMail()
                .name("myMail1")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject(identicalSubject)
                    .setText(identicalContent))
                .build());
            mailQueue.enQueue(defaultMail()
                .name("myMail2")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject(identicalSubject)
                    .setText(identicalContent))
                .build());

            Flux.from(mailQueue.deQueue())
                .take(2)
                .concatMap(mailQueueItem ->
                    Mono.fromCallable(() -> {
                        assertThat(mailQueueItem.getMail().getMessage().getContent()).isEqualTo(identicalContent);
                        mailQueueItem.done(MailQueue.MailQueueItem.CompletionStatus.SUCCESS);
                        return mailQueueItem;
                    }).subscribeOn(Schedulers.fromExecutor(EXECUTOR)))
                .collectList()
                .block(Duration.ofSeconds(10));
        }
    }

    private void setUp(CassandraCluster cassandra, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem, RabbitMQMailQueueConfiguration configuration,
                       BlobStore blobStore, MailQueueFactory.PrefetchCount prefetchCount) throws Exception {
        MimeMessageStore.Factory mimeMessageStoreFactory = MimeMessageStore.factory(blobStore);
        clock = new UpdatableTickingClock(IN_SLICE_1);

        MailQueueView.Factory mailQueueViewFactory = CassandraMailQueueViewTestFactory.factory(
                clock,
                cassandra.getConf(),
                CassandraMailQueueViewConfiguration.builder()
                    .bucketCount(THREE_BUCKET_COUNT)
                    .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                    .sliceWindow(ONE_HOUR_SLICE_WINDOW)
                    .build(),
            mimeMessageStoreFactory
        );

        RabbitMQMailQueueFactory.PrivateFactory factory = new RabbitMQMailQueueFactory.PrivateFactory(
            metricTestSystem.getMetricFactory(),
            metricTestSystem.getSpyGaugeRegistry(),
            rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(),
            mimeMessageStoreFactory,
            BLOB_ID_FACTORY,
            mailQueueViewFactory,
            clock,
            new RawMailQueueItemDecoratorFactory(),
            configuration);
        mqManagementApi = new RabbitMQMailQueueManagement(rabbitMQExtension.managementAPI());
        mailQueueFactory = new RabbitMQMailQueueFactory(rabbitMQExtension.getSender(), mqManagementApi, factory, rabbitMQExtension.getRabbitMQ().getConfiguration());
        mailQueue = mailQueueFactory.createQueue(SPOOL, prefetchCount);
    }
}
