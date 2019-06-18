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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewTestFactory;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.util.streams.Iterators;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

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
        CassandraSchemaVersionModule.MODULE,
        CassandraBlobModule.MODULE,
        CassandraMailQueueViewModule.MODULE,
        CassandraEventStoreModule.MODULE));

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ();

    private UpdatableTickingClock clock;
    private RabbitMQMailQueueManagement mqManagementApi;
    private RabbitClient rabbitClient;
    private ThreadLocalRandom random;
    private MimeMessageStore.Factory mimeMessageStoreFactory;

    @BeforeEach
    void setup(CassandraCluster cassandra) throws Exception {
        CassandraBlobsDAO blobsDAO = new CassandraBlobsDAO(cassandra.getConf(), CassandraConfiguration.DEFAULT_CONFIGURATION, BLOB_ID_FACTORY);
        mimeMessageStoreFactory = MimeMessageStore.factory(blobsDAO);
        clock = new UpdatableTickingClock(IN_SLICE_1);
        random = ThreadLocalRandom.current();

        rabbitClient = new RabbitClient(rabbitMQExtension.getRabbitChannelPool());
        mqManagementApi = new RabbitMQMailQueueManagement(rabbitMQExtension.managementAPI());
    }

    @AfterEach
    void tearDown() {
        mqManagementApi.deleteAllQueues();
    }

    private RabbitMQMailQueue getRabbitMQMailQueue(CassandraCluster cassandra, CassandraMailQueueViewConfiguration mailQueueViewConfiguration) throws Exception {
        CassandraMailQueueView.Factory mailQueueViewFactory = CassandraMailQueueViewTestFactory.factory(clock, random,
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            mailQueueViewConfiguration,
            mimeMessageStoreFactory);

        RabbitMQMailQueueFactory.PrivateFactory privateFactory = new RabbitMQMailQueueFactory.PrivateFactory(
            new NoopMetricFactory(),
            new NoopGaugeRegistry(),
            rabbitClient,
            mimeMessageStoreFactory,
            BLOB_ID_FACTORY,
            mailQueueViewFactory,
            clock,
            new RawMailQueueItemDecoratorFactory());
        RabbitMQMailQueueFactory mailQueueFactory = new RabbitMQMailQueueFactory(rabbitClient, mqManagementApi, privateFactory);
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
    void decreasingBucketCountShouldBeRejected(CassandraCluster cassandra) throws Exception {
        getRabbitMQMailQueue(cassandra,
            CassandraMailQueueViewConfiguration.builder()
                    .bucketCount(THREE_BUCKET_COUNT + 2)
                    .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                    .sliceWindow(ONE_HOUR_SLICE_WINDOW)
                    .build());

        assertThatThrownBy(() -> getRabbitMQMailQueue(cassandra, DEFAULT_CONFIGURATION))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void divideSliceWindowShouldAllowBrowsingAllQueueElements(CassandraCluster cassandra) throws Exception {
        RabbitMQMailQueue mailQueue = getRabbitMQMailQueue(cassandra, DEFAULT_CONFIGURATION);

        clock.setInstant(IN_SLICE_1);
        enqueueSomeMails(mailQueue, namePatternForSlice(1), 1);
        clock.setInstant(IN_SLICE_2);
        enqueueSomeMails(mailQueue, namePatternForSlice(2), 1);
        clock.setInstant(IN_SLICE_3);
        enqueueSomeMails(mailQueue, namePatternForSlice(3), 1);

        RabbitMQMailQueue mailQueueWithSmallerSlices = getRabbitMQMailQueue(cassandra,
            CassandraMailQueueViewConfiguration.builder()
                    .bucketCount(THREE_BUCKET_COUNT)
                    .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                    .sliceWindow(Duration.ofMinutes(30))
                    .build());

        clock.setInstant(IN_SLICE_3.plus(35, MINUTES));
        enqueueSomeMails(mailQueue, namePatternForSlice(4), 1);
        clock.setInstant(IN_SLICE_3.plus(65, MINUTES));
        enqueueSomeMails(mailQueue, namePatternForSlice(5), 1);
        clock.setInstant(IN_SLICE_3.plus(95, MINUTES));
        enqueueSomeMails(mailQueue, namePatternForSlice(6), 1);

        Stream<String> names = Iterators.toStream(mailQueueWithSmallerSlices.browse())
            .map(ManageableMailQueue.MailQueueItemView::getMail)
            .map(Mail::getName);

        assertThat(names).containsOnly(
            "1-1", "2-1", "3-1", "4-1", "5-1", "6-1");
    }

    @Test
    void decreaseArbitrarilySliceWindowShouldBeRejected(CassandraCluster cassandra) throws Exception {
        getRabbitMQMailQueue(cassandra, DEFAULT_CONFIGURATION);

        assertThatThrownBy(() -> getRabbitMQMailQueue(cassandra,
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(THREE_BUCKET_COUNT)
                .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                .sliceWindow(Duration.ofMinutes(25))
                .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void increaseSliceWindowShouldBeRejected(CassandraCluster cassandra) throws Exception {
        getRabbitMQMailQueue(cassandra, DEFAULT_CONFIGURATION);

        assertThatThrownBy(() -> getRabbitMQMailQueue(cassandra,
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(THREE_BUCKET_COUNT)
                .updateBrowseStartPace(UPDATE_BROWSE_START_PACE)
                .sliceWindow(Duration.ofHours(2))
                .build()))
            .isInstanceOf(IllegalArgumentException.class);
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
