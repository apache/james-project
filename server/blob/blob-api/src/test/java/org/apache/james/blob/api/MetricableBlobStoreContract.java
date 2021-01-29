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

package org.apache.james.blob.api;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.api.MetricableBlobStore.DELETE_BUCKET_TIMER_NAME;
import static org.apache.james.blob.api.MetricableBlobStore.DELETE_TIMER_NAME;
import static org.apache.james.blob.api.MetricableBlobStore.READ_BYTES_TIMER_NAME;
import static org.apache.james.blob.api.MetricableBlobStore.READ_TIMER_NAME;
import static org.apache.james.blob.api.MetricableBlobStore.SAVE_BYTES_TIMER_NAME;
import static org.apache.james.blob.api.MetricableBlobStore.SAVE_INPUT_STREAM_TIMER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;


public interface MetricableBlobStoreContract extends BlobStoreContract {

    class MetricableBlobStoreExtension implements BeforeEachCallback {
        private RecordingMetricFactory metricFactory;

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            this.metricFactory = new RecordingMetricFactory();
        }

        public RecordingMetricFactory getMetricFactory() {
            return metricFactory;
        }
    }

    @RegisterExtension
    MetricableBlobStoreExtension metricsTestExtension = new MetricableBlobStoreExtension();
    String STRING_CONTENT = "blob content";
    byte[] BYTES_CONTENT = STRING_CONTENT.getBytes(StandardCharsets.UTF_8);

    @Test
    default void saveBytesShouldPublishSaveBytesTimerMetrics() {
        BlobStore store = testee();

        Mono.from(store.save(store.getDefaultBucketName(), BYTES_CONTENT, LOW_COST)).block();
        Mono.from(store.save(store.getDefaultBucketName(), BYTES_CONTENT, LOW_COST)).block();

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().executionTimesFor(SAVE_BYTES_TIMER_NAME))
                .hasSize(2));
    }

    @Test
    default void saveStringShouldPublishSaveBytesTimerMetrics() {
        BlobStore store = testee();

        Mono.from(store.save(store.getDefaultBucketName(), STRING_CONTENT, LOW_COST)).block();
        Mono.from(store.save(store.getDefaultBucketName(), STRING_CONTENT, LOW_COST)).block();

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().executionTimesFor(SAVE_BYTES_TIMER_NAME))
                .hasSize(2));
    }

    @Test
    default void saveInputStreamShouldPublishSaveInputStreamTimerMetrics() {
        BlobStore store = testee();

        Mono.from(store.save(store.getDefaultBucketName(), new ByteArrayInputStream(BYTES_CONTENT), LOW_COST)).block();
        Mono.from(store.save(store.getDefaultBucketName(), new ByteArrayInputStream(BYTES_CONTENT), LOW_COST)).block();

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().executionTimesFor(SAVE_INPUT_STREAM_TIMER_NAME))
                .hasSize(2));
    }

    @Test
    default void readBytesShouldPublishReadBytesTimerMetrics() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(store.getDefaultBucketName(), BYTES_CONTENT, LOW_COST)).block();
        Mono.from(store.readBytes(store.getDefaultBucketName(), blobId)).block();
        Mono.from(store.readBytes(store.getDefaultBucketName(), blobId)).block();

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().executionTimesFor(READ_BYTES_TIMER_NAME))
                .hasSize(2));
    }

    @Test
    default void readShouldPublishReadTimerMetrics() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(store.getDefaultBucketName(), BYTES_CONTENT, LOW_COST)).block();
        store.read(store.getDefaultBucketName(), blobId);
        store.read(store.getDefaultBucketName(), blobId);

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().executionTimesFor(READ_TIMER_NAME))
                .hasSize(2));
    }

    @Test
    default void deleteBucketShouldPublishDeleteBucketTimerMetrics() {
        BlobStore store = testee();

        BucketName bucketName = BucketName.of("custom");
        Mono.from(store.save(BucketName.DEFAULT, BYTES_CONTENT, LOW_COST)).block();
        Mono.from(store.save(bucketName, BYTES_CONTENT, LOW_COST)).block();

        Mono.from(store.deleteBucket(bucketName)).block();

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().executionTimesFor(DELETE_BUCKET_TIMER_NAME))
                .hasSize(1));
    }

    @Test
    default void deleteShouldPublishDeleteTimerMetrics() {
        BlobStore store = testee();

        BlobId blobId1 = Mono.from(store.save(store.getDefaultBucketName(), BYTES_CONTENT, LOW_COST)).block();
        BlobId blobId2 = Mono.from(store.save(store.getDefaultBucketName(), BYTES_CONTENT, LOW_COST)).block();

        Mono.from(store.delete(BucketName.DEFAULT, blobId1)).block();
        Mono.from(store.delete(BucketName.DEFAULT, blobId2)).block();

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().executionTimesFor(DELETE_TIMER_NAME))
                .hasSize(2));
    }
}