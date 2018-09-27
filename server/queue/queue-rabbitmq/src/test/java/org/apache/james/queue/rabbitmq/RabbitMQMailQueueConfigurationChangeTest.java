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

import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.apache.james.queue.api.Mails.defaultMail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.mail.internet.MimeMessage;

import org.apache.james.backend.rabbitmq.DockerRabbitMQ;
import org.apache.james.backend.rabbitmq.RabbitChannelPool;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewTestFactory;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

@ExtendWith(RabbitMQExtension.class)
class RabbitMQMailQueueConfigurationChangeTest {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final int THREE_BUCKET_COUNT = 3;
    private static final int UPDATE_BROWSE_START_PACE = 2;
    private static final Duration ONE_HOUR_SLICE_WINDOW = Duration.ofHours(1);
    private static final CassandraMailQueueViewConfiguration DEFAULT_CONFIGURATION = CassandraMailQueueViewConfiguration.builder()
        .bucketCount(THREE_BUCKET_COUNT)
        .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
        .sliceWindow(ONE_HOUR_SLICE_WINDOW)
        .build();
    private static final String SPOOL = "spool";
    private static final Instant IN_SLICE_1 = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final Instant IN_SLICE_2 = IN_SLICE_1.plus(1, HOURS);
    private static final Instant IN_SLICE_3 = IN_SLICE_1.plus(2, HOURS);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraBlobModule.MODULE,
        CassandraMailQueueViewModule.MODULE));

    private Clock clock;
    private RabbitMQManagementApi mqManagementApi;
    private RabbitClient rabbitClient;
    private ThreadLocalRandom random;
    private Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;

    @BeforeEach
    void setup(DockerRabbitMQ rabbitMQ, CassandraCluster cassandra) throws Exception {

        CassandraBlobsDAO blobsDAO = new CassandraBlobsDAO(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION, BLOB_ID_FACTORY);
        mimeMessageStore = MimeMessageStore.factory(blobsDAO).mimeMessageStore();
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(rabbitMQ.amqpUri())
            .managementUri(rabbitMQ.managementUri())
            .build();
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(IN_SLICE_1);
        random = ThreadLocalRandom.current();

        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(rabbitMQConfiguration,
            new AsyncRetryExecutor(Executors.newSingleThreadScheduledExecutor()));

        rabbitClient = new RabbitClient(new RabbitChannelPool(rabbitMQConnectionFactory));
        mqManagementApi = new RabbitMQManagementApi(rabbitMQ.managementUri(), new RabbitMQManagementCredentials("guest", "guest".toCharArray()));
    }

    private RabbitMQMailQueue getRabbitMQMailQueue(CassandraCluster cassandra, CassandraMailQueueViewConfiguration mailQueueViewConfiguration) throws Exception {
        MailQueueView mailQueueView = CassandraMailQueueViewTestFactory.factory(clock, random, cassandra.getConf(), cassandra.getTypesProvider(),
            mailQueueViewConfiguration)
            .create(MailQueueName.fromString(SPOOL));

        RabbitMQMailQueue.Factory factory = new RabbitMQMailQueue.Factory(
            new NoopMetricFactory(),
            rabbitClient,
            mimeMessageStore,
            BLOB_ID_FACTORY,
            mailQueueView,
            clock);
        RabbitMQMailQueueFactory mailQueueFactory = new RabbitMQMailQueueFactory(rabbitClient, mqManagementApi, factory);
        return mailQueueFactory.createQueue(SPOOL);
    }

    @Test
    void increasingBucketCountShouldAllowBrowsingAllQueueElements(CassandraCluster cassandra) throws Exception {
        RabbitMQMailQueue mailQueue = getRabbitMQMailQueue(cassandra, DEFAULT_CONFIGURATION);

        enqueueSomeMails(mailQueue, namePatternForSlice(1), 10);


        RabbitMQMailQueue mailQueueWithMoreBuckets = getRabbitMQMailQueue(cassandra,
            CassandraMailQueueViewConfiguration.builder()
                    .bucketCount(THREE_BUCKET_COUNT + 2)
                    .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                    .sliceWindow(ONE_HOUR_SLICE_WINDOW)
                    .build());

        enqueueSomeMails(mailQueueWithMoreBuckets, namePatternForSlice(2), 10);

        Stream<String> names = Iterators.toStream(mailQueueWithMoreBuckets.browse())
            .map(ManageableMailQueue.MailQueueItemView::getMail)
            .map(Mail::getName);

        assertThat(names).containsOnly(
            "1-1", "1-2", "1-3", "1-4", "1-5",
            "1-6", "1-7", "1-8", "1-9", "1-10",
            "2-1", "2-2", "2-3", "2-4", "2-5",
            "2-6", "2-7", "2-8", "2-9", "2-10");
    }

    @Test
    @Disabled("decreasing bucket count lead to losing emails")
    void decreasingBucketCountShouldAllowBrowsingAllQueueElements(CassandraCluster cassandra) throws Exception {
        RabbitMQMailQueue mailQueueWithMoreBuckets = getRabbitMQMailQueue(cassandra,
            CassandraMailQueueViewConfiguration.builder()
                    .bucketCount(THREE_BUCKET_COUNT + 2)
                    .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                    .sliceWindow(ONE_HOUR_SLICE_WINDOW)
                    .build());

        enqueueMailsInSlice(mailQueueWithMoreBuckets, 1, 10);

        RabbitMQMailQueue mailQueue = getRabbitMQMailQueue(cassandra, DEFAULT_CONFIGURATION);

        enqueueMailsInSlice(mailQueue, 2, 10);

        Stream<String> names = Iterators.toStream(mailQueue.browse())
            .map(ManageableMailQueue.MailQueueItemView::getMail)
            .map(Mail::getName);

        assertThat(names).containsOnly(
            "1-1", "1-2", "1-3", "1-4", "1-5",
            "1-6", "1-7", "1-8", "1-9", "1-10",
            "2-1", "2-2", "2-3", "2-4", "2-5",
            "2-6", "2-7", "2-8", "2-9", "2-10");
    }

    @Test
    void divideSliceWindowShouldAllowBrowsingAllQueueElements(CassandraCluster cassandra) throws Exception {
        RabbitMQMailQueue mailQueue = getRabbitMQMailQueue(cassandra, DEFAULT_CONFIGURATION);

        when(clock.instant()).thenReturn(IN_SLICE_1);
        enqueueSomeMails(mailQueue, namePatternForSlice(1), 1);
        when(clock.instant()).thenReturn(IN_SLICE_2);
        enqueueSomeMails(mailQueue, namePatternForSlice(2), 1);
        when(clock.instant()).thenReturn(IN_SLICE_3);
        enqueueSomeMails(mailQueue, namePatternForSlice(3), 1);

        RabbitMQMailQueue mailQueueWithSmallerSlices = getRabbitMQMailQueue(cassandra,
            CassandraMailQueueViewConfiguration.builder()
                    .bucketCount(THREE_BUCKET_COUNT)
                    .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                    .sliceWindow(Duration.ofMinutes(30))
                    .build());

        when(clock.instant()).thenReturn(IN_SLICE_3.plus(35, MINUTES));
        enqueueSomeMails(mailQueue, namePatternForSlice(4), 1);
        when(clock.instant()).thenReturn(IN_SLICE_3.plus(65, MINUTES));
        enqueueSomeMails(mailQueue, namePatternForSlice(5), 1);
        when(clock.instant()).thenReturn(IN_SLICE_3.plus(95, MINUTES));
        enqueueSomeMails(mailQueue, namePatternForSlice(6), 1);

        Stream<String> names = Iterators.toStream(mailQueueWithSmallerSlices.browse())
            .map(ManageableMailQueue.MailQueueItemView::getMail)
            .map(Mail::getName);

        assertThat(names).containsOnly(
            "1-1", "2-1", "3-1", "4-1", "5-1", "6-1");
    }

    @Test
    @Disabled("decrease arbitrarily slice window leads to lose emails due to non matching slice start")
    void decreaseArbitrarilySliceWindowShouldAllowBrowsingAllQueueElements(CassandraCluster cassandra) throws Exception {
        RabbitMQMailQueue mailQueue = getRabbitMQMailQueue(cassandra, DEFAULT_CONFIGURATION);

        when(clock.instant()).thenReturn(IN_SLICE_1);
        enqueueMailsInSlice(mailQueue, 1, 1);
        when(clock.instant()).thenReturn(IN_SLICE_2);
        enqueueMailsInSlice(mailQueue, 2, 1);
        when(clock.instant()).thenReturn(IN_SLICE_3);
        enqueueMailsInSlice(mailQueue, 3, 1);

        RabbitMQMailQueue mailQueueWithSmallerSlices = getRabbitMQMailQueue(cassandra,
            CassandraMailQueueViewConfiguration.builder()
                    .bucketCount(THREE_BUCKET_COUNT)
                    .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                    .sliceWindow(Duration.ofMinutes(25))
                    .build());

        when(clock.instant()).thenReturn(IN_SLICE_3.plus(35, MINUTES));
        enqueueMailsInSlice(mailQueue, 4, 1);
        when(clock.instant()).thenReturn(IN_SLICE_3.plus(65, MINUTES));
        enqueueMailsInSlice(mailQueue, 5, 1);
        when(clock.instant()).thenReturn(IN_SLICE_3.plus(95, MINUTES));
        enqueueMailsInSlice(mailQueue, 6, 1);

        Stream<String> names = Iterators.toStream(mailQueueWithSmallerSlices.browse())
            .map(ManageableMailQueue.MailQueueItemView::getMail)
            .map(Mail::getName);

        assertThat(names).containsOnly(
            "1-1", "2-1", "3-1", "4-1", "5-1", "6-1");
    }

    @Test
    @Disabled("increase slice window leads to lose emails due to non matching slice start")
    void increaseSliceWindowShouldAllowBrowsingAllQueueElements(CassandraCluster cassandra) throws Exception {
        RabbitMQMailQueue mailQueue = getRabbitMQMailQueue(cassandra, DEFAULT_CONFIGURATION);

        when(clock.instant()).thenReturn(IN_SLICE_1);
        enqueueMailsInSlice(mailQueue, 1, 1);
        when(clock.instant()).thenReturn(IN_SLICE_2);
        enqueueMailsInSlice(mailQueue, 2, 1);
        when(clock.instant()).thenReturn(IN_SLICE_3);
        enqueueMailsInSlice(mailQueue, 3, 1);

        RabbitMQMailQueue mailQueueWithSmallerSlices = getRabbitMQMailQueue(cassandra,
            CassandraMailQueueViewConfiguration.builder()
                    .bucketCount(THREE_BUCKET_COUNT)
                    .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                    .sliceWindow(Duration.ofHours(2))
                    .build());

        when(clock.instant()).thenReturn(IN_SLICE_3.plus(35, MINUTES));
        enqueueMailsInSlice(mailQueue, 4, 1);
        when(clock.instant()).thenReturn(IN_SLICE_3.plus(65, MINUTES));
        enqueueMailsInSlice(mailQueue, 5, 1);
        when(clock.instant()).thenReturn(IN_SLICE_3.plus(95, MINUTES));
        enqueueMailsInSlice(mailQueue, 6, 1);

        Stream<String> names = Iterators.toStream(mailQueueWithSmallerSlices.browse())
            .map(ManageableMailQueue.MailQueueItemView::getMail)
            .map(Mail::getName);

        assertThat(names).containsOnly(
            "1-1", "2-1", "3-1", "4-1", "5-1", "6-1");
    }

    private Function<Integer, String> namePatternForSlice(int sliceId) {
        return i -> sliceId + "-" + i;
    }

    private void enqueueSomeMails(MailQueue mailQueue, Function<Integer, String> namePattern, int emailCount) {
        IntStream.rangeClosed(1, emailCount)
            .forEach(Throwing.intConsumer(i -> mailQueue.enQueue(defaultMail()
                .name(namePattern.apply(i))
                .build())));
    }

}
