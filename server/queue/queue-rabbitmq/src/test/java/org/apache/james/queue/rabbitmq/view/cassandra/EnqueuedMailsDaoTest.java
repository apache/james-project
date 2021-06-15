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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.EnqueuedItem;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.BucketId;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedItemWithSlicingContext;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class EnqueuedMailsDaoTest {

    private static final MailQueueName OUT_GOING_1 = MailQueueName.fromString("OUT_GOING_1");
    private static final EnqueueId ENQUEUE_ID = EnqueueId.ofSerialized("110e8400-e29b-11d4-a716-446655440000");
    private static final String NAME = "name";
    private static int BUCKET_ID_VALUE = 10;
    private static final BucketId BUCKET_ID = BucketId.of(BUCKET_ID_VALUE);
    private static final Instant NOW = Instant.now();
    private static final Slice SLICE_OF_NOW = Slice.of(NOW);

    private static final BlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private static final BlobId HEADER_BLOB_ID = BLOB_ID_FACTORY.from("header blob id");
    private static final BlobId BODY_BLOB_ID = BLOB_ID_FACTORY.from("body blob id");
    private static final MimeMessagePartsId MIME_MESSAGE_PARTS_ID = MimeMessagePartsId.builder()
        .headerBlobId(HEADER_BLOB_ID)
        .bodyBlobId(BODY_BLOB_ID)
        .build();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            CassandraModule.aggregateModules(CassandraSchemaVersionModule.MODULE, CassandraMailQueueViewModule.MODULE));

    private EnqueuedMailsDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        BlobId.Factory blobFactory = new HashBlobId.Factory();
        testee = new EnqueuedMailsDAO(
            cassandra.getConf(),
            blobFactory);
    }

    @Test
    void insertShouldWork() throws Exception {
        testee.insert(EnqueuedItemWithSlicingContext.builder()
                .enqueuedItem(EnqueuedItem.builder()
                    .enqueueId(ENQUEUE_ID)
                    .mailQueueName(OUT_GOING_1)
                    .mail(FakeMail.builder()
                        .name(NAME)
                        .build())
                    .enqueuedTime(NOW)
                    .mimeMessagePartsId(MIME_MESSAGE_PARTS_ID)
                    .build())
                .slicingContext(EnqueuedItemWithSlicingContext.SlicingContext.of(BucketId.of(BUCKET_ID_VALUE), NOW))
                .build())
            .block();

        List<EnqueuedItemWithSlicingContext> selectedEnqueuedMails = testee
            .selectEnqueuedMails(OUT_GOING_1, SLICE_OF_NOW, BUCKET_ID)
            .collectList().block();

        assertThat(selectedEnqueuedMails).hasSize(1);
    }

    @Test
    void selectEnqueuedMailsShouldWork() throws Exception {
        testee.insert(EnqueuedItemWithSlicingContext.builder()
                .enqueuedItem(EnqueuedItem.builder()
                    .enqueueId(ENQUEUE_ID)
                    .mailQueueName(OUT_GOING_1)
                    .mail(FakeMail.builder()
                        .name(NAME)
                        .build())
                    .enqueuedTime(NOW)
                    .mimeMessagePartsId(MIME_MESSAGE_PARTS_ID)
                    .build())
                .slicingContext(EnqueuedItemWithSlicingContext.SlicingContext.of(BucketId.of(BUCKET_ID_VALUE), NOW))
                .build())
            .block();

        testee.insert(EnqueuedItemWithSlicingContext.builder()
                .enqueuedItem(EnqueuedItem.builder()
                    .enqueueId(ENQUEUE_ID)
                    .mailQueueName(OUT_GOING_1)
                    .mail(FakeMail.builder()
                        .name(NAME)
                        .build())
                    .enqueuedTime(NOW)
                    .mimeMessagePartsId(MIME_MESSAGE_PARTS_ID)
                    .build())
                .slicingContext(EnqueuedItemWithSlicingContext.SlicingContext.of(BucketId.of(BUCKET_ID_VALUE + 1), NOW))
                .build())
            .block();

        List<EnqueuedItemWithSlicingContext> selectedEnqueuedMails = testee.selectEnqueuedMails(OUT_GOING_1, SLICE_OF_NOW, BUCKET_ID)
            .collectList().block();

        assertThat(selectedEnqueuedMails)
            .hasSize(1)
            .hasOnlyOneElementSatisfying(selectedEnqueuedMail -> {
                EnqueuedItem enqueuedItem = selectedEnqueuedMail.getEnqueuedItem();
                EnqueuedItemWithSlicingContext.SlicingContext slicingContext = selectedEnqueuedMail.getSlicingContext();
                assertSoftly(softly -> {
                    softly.assertThat(slicingContext.getBucketId()).isEqualTo(BUCKET_ID);
                    softly.assertThat(slicingContext.getTimeRangeStart()).isEqualTo(NOW.truncatedTo(ChronoUnit.MILLIS));
                    softly.assertThat(enqueuedItem.getMailQueueName()).isEqualTo(OUT_GOING_1);
                    softly.assertThat(enqueuedItem.getEnqueuedTime()).isEqualTo(NOW.truncatedTo(ChronoUnit.MILLIS));
                    softly.assertThat(enqueuedItem.getEnqueueId()).isEqualTo(ENQUEUE_ID);
                    softly.assertThat(enqueuedItem.getMail().getName()).isEqualTo(NAME);
                    softly.assertThat(enqueuedItem.getPartsId()).isEqualTo(MIME_MESSAGE_PARTS_ID);
                });
            });
    }

    @Test
    void selectShouldNotReturnEmailsInDeletedSlice() throws Exception {
        testee.insert(EnqueuedItemWithSlicingContext.builder()
                .enqueuedItem(EnqueuedItem.builder()
                    .enqueueId(ENQUEUE_ID)
                    .mailQueueName(OUT_GOING_1)
                    .mail(FakeMail.builder()
                        .name(NAME)
                        .build())
                    .enqueuedTime(NOW)
                    .mimeMessagePartsId(MIME_MESSAGE_PARTS_ID)
                    .build())
                .slicingContext(EnqueuedItemWithSlicingContext.SlicingContext.of(BucketId.of(BUCKET_ID_VALUE), NOW))
                .build())
            .block();

        testee.insert(EnqueuedItemWithSlicingContext.builder()
                .enqueuedItem(EnqueuedItem.builder()
                    .enqueueId(ENQUEUE_ID)
                    .mailQueueName(OUT_GOING_1)
                    .mail(FakeMail.builder()
                        .name(NAME)
                        .build())
                    .enqueuedTime(NOW)
                    .mimeMessagePartsId(MIME_MESSAGE_PARTS_ID)
                    .build())
                .slicingContext(EnqueuedItemWithSlicingContext.SlicingContext.of(BucketId.of(BUCKET_ID_VALUE + 1), NOW))
                .build())
            .block();

        testee.deleteBucket(OUT_GOING_1, SLICE_OF_NOW, BUCKET_ID).block();

        List<EnqueuedItemWithSlicingContext> selectedEnqueuedMails = testee.selectEnqueuedMails(OUT_GOING_1, SLICE_OF_NOW, BUCKET_ID)
            .collectList().block();

        assertThat(selectedEnqueuedMails)
            .isEmpty();
    }
}