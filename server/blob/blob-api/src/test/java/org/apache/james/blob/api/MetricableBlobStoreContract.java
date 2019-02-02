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

import static org.apache.james.blob.api.MetricableBlobStore.READ_BYTES_TIMER_NAME;
import static org.apache.james.blob.api.MetricableBlobStore.READ_TIMER_NAME;
import static org.apache.james.blob.api.MetricableBlobStore.SAVE_BYTES_TIMER_NAME;
import static org.apache.james.blob.api.MetricableBlobStore.SAVE_INPUT_STREAM_TIMER_NAME;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

public interface MetricableBlobStoreContract extends BlobStoreContract {

    class MetricableBlobStoreExtension implements BeforeEachCallback {
        private MetricFactory metricFactory;
        private TimeMetric saveBytesTimeMetric;
        private TimeMetric saveInputStreamTimeMetric;
        private TimeMetric readBytesTimeMetric;
        private TimeMetric readTimeMetric;

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            this.metricFactory = spy(MetricFactory.class);
            this.saveBytesTimeMetric = spy(TimeMetric.class);
            this.saveInputStreamTimeMetric = spy(TimeMetric.class);
            this.readBytesTimeMetric = spy(TimeMetric.class);
            this.readTimeMetric = spy(TimeMetric.class);
            setupExpectations();
        }

        public MetricFactory getMetricFactory() {
            return metricFactory;
        }

        private void setupExpectations() {
            when(metricFactory.timer(SAVE_BYTES_TIMER_NAME))
                .thenReturn(saveBytesTimeMetric);
            when(metricFactory.timer(SAVE_INPUT_STREAM_TIMER_NAME))
                .thenReturn(saveInputStreamTimeMetric);
            when(metricFactory.timer(READ_BYTES_TIMER_NAME))
                .thenReturn(readBytesTimeMetric);
            when(metricFactory.timer(READ_TIMER_NAME))
                .thenReturn(readTimeMetric);
        }
    }

    @RegisterExtension
    MetricableBlobStoreExtension metricsTestExtension = new MetricableBlobStoreExtension();
    byte[] BYTES_CONTENT = "bytes content".getBytes(StandardCharsets.UTF_8);

    @Test
    default void saveBytesShouldPublishSaveBytesTimerMetrics() {
        testee().save(BYTES_CONTENT).block();
        testee().save(BYTES_CONTENT).block();
        verify(metricsTestExtension.saveBytesTimeMetric, times(2)).stopAndPublish();
    }

    @Test
    default void saveInputStreamShouldPublishSaveInputStreamTimerMetrics() {
        testee().save(new ByteArrayInputStream(BYTES_CONTENT)).block();
        testee().save(new ByteArrayInputStream(BYTES_CONTENT)).block();
        testee().save(new ByteArrayInputStream(BYTES_CONTENT)).block();
        verify(metricsTestExtension.saveInputStreamTimeMetric, times(3)).stopAndPublish();
    }

    @Test
    default void readBytesShouldPublishReadBytesTimerMetrics() {
        BlobId blobId = testee().save(BYTES_CONTENT).block();
        testee().readBytes(blobId).block();
        testee().readBytes(blobId).block();
        verify(metricsTestExtension.readBytesTimeMetric, times(2)).stopAndPublish();
    }

    @Test
    default void readShouldPublishReadTimerMetrics() {
        BlobId blobId = testee().save(BYTES_CONTENT).block();
        testee().read(blobId);
        testee().read(blobId);
        verify(metricsTestExtension.readTimeMetric, times(2)).stopAndPublish();
    }
}