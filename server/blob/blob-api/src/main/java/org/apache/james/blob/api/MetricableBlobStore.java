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

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import org.apache.james.metrics.api.MetricFactory;

public class MetricableBlobStore implements BlobStore {

    static final String BLOB_STORE_METRIC_PREFIX = "blobStore:";
    static final String SAVE_BYTES_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "saveBytes";
    static final String SAVE_INPUT_STREAM_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "saveInputStream";
    static final String READ_BYTES_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "readBytes";
    static final String READ_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "read";

    private final MetricFactory metricFactory;
    private final BlobStore blobStoreImpl;

    public MetricableBlobStore(MetricFactory metricFactory, BlobStore blobStoreImpl) {
        this.metricFactory = metricFactory;
        this.blobStoreImpl = blobStoreImpl;
    }

    @Override
    public CompletableFuture<BlobId> save(byte[] data) {
        return metricFactory
            .runPublishingTimerMetric(SAVE_BYTES_TIMER_NAME, blobStoreImpl.save(data));
    }

    @Override
    public CompletableFuture<BlobId> save(InputStream data) {
        return metricFactory
            .runPublishingTimerMetric(SAVE_INPUT_STREAM_TIMER_NAME, blobStoreImpl.save(data));
    }

    @Override
    public CompletableFuture<byte[]> readBytes(BlobId blobId) {
        return metricFactory
            .runPublishingTimerMetric(READ_BYTES_TIMER_NAME, blobStoreImpl.readBytes(blobId));
    }

    @Override
    public InputStream read(BlobId blobId) {
        return metricFactory
            .runPublishingTimerMetric(READ_TIMER_NAME, () -> blobStoreImpl.read(blobId));
    }
}
