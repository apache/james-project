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

package org.apache.james.queue.rabbitmq.view.cassandra;

import static org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.BucketId;
import static org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedMail;
import org.apache.james.queue.rabbitmq.view.cassandra.model.MailKey;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class EnqueuedMailsDaoTest {

    private static final MailQueueName OUT_GOING_1 = MailQueueName.fromString("OUT_GOING_1");
    private static final MailKey MAIL_KEY_1 = MailKey.of("mailkey1");
    private static int BUCKET_ID_VALUE = 10;
    private static final BucketId BUCKET_ID = BucketId.of(BUCKET_ID_VALUE);
    private static final Instant NOW = Instant.now();
    private static final Slice SLICE_OF_NOW = Slice.of(NOW, Duration.ofSeconds(100));

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMailQueueViewModule.MODULE);

    private EnqueuedMailsDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new EnqueuedMailsDAO(
            cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            cassandra.getTypesProvider());
    }

    @Test
    void insertShouldWork() throws Exception {
        testee.insert(EnqueuedMail.builder()
                .mail(FakeMail.builder()
                    .name(MAIL_KEY_1.getMailKey())
                    .build())
                .bucketId(BucketId.of(BUCKET_ID_VALUE))
                .timeRangeStart(NOW)
                .enqueuedTime(NOW)
                .mailKey(MAIL_KEY_1)
                .mailQueueName(OUT_GOING_1)
                .build())
            .join();

        Stream<EnqueuedMail> selectedEnqueuedMails = testee
            .selectEnqueuedMails(OUT_GOING_1, SLICE_OF_NOW, BUCKET_ID)
            .join();

        assertThat(selectedEnqueuedMails).hasSize(1);
    }

    @Test
    void selectEnqueuedMailsShouldWork() throws Exception {
        testee.insert(EnqueuedMail.builder()
                .mail(FakeMail.builder()
                    .name(MAIL_KEY_1.getMailKey())
                    .build())
                .bucketId(BucketId.of(BUCKET_ID_VALUE))
                .timeRangeStart(NOW)
                .enqueuedTime(NOW)
                .mailKey(MAIL_KEY_1)
                .mailQueueName(OUT_GOING_1)
                .build())
            .join();

        testee.insert(EnqueuedMail.builder()
                .mail(FakeMail.builder()
                    .name(MAIL_KEY_1.getMailKey())
                    .build())
                .bucketId(BucketId.of(BUCKET_ID_VALUE + 1))
                .timeRangeStart(NOW)
                .enqueuedTime(NOW)
                .mailKey(MAIL_KEY_1)
                .mailQueueName(OUT_GOING_1)
                .build())
            .join();

        Stream<EnqueuedMail> selectedEnqueuedMails = testee.selectEnqueuedMails(OUT_GOING_1, SLICE_OF_NOW, BUCKET_ID)
            .join();

        assertThat(selectedEnqueuedMails)
            .hasSize(1)
            .hasOnlyOneElementSatisfying(selectedEnqueuedMail -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(selectedEnqueuedMail.getMailQueueName()).isEqualTo(OUT_GOING_1);
                softly.assertThat(selectedEnqueuedMail.getBucketId()).isEqualTo(BUCKET_ID);
                softly.assertThat(selectedEnqueuedMail.getTimeRangeStart()).isEqualTo(NOW);
                softly.assertThat(selectedEnqueuedMail.getEnqueuedTime()).isEqualTo(NOW);
                softly.assertThat(selectedEnqueuedMail.getMailKey()).isEqualTo(MAIL_KEY_1);
                softly.assertAll();
            });
    }
}