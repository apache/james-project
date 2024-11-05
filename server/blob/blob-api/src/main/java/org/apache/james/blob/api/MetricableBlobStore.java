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

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.metrics.api.MetricFactory;
import org.reactivestreams.Publisher;

import com.google.common.io.ByteSource;

public class MetricableBlobStore implements BlobStore {

    public static final String BLOB_STORE_IMPLEMENTATION = "blobStoreImplementation";

    static final String BLOB_STORE_METRIC_PREFIX = "blobStore:";
    static final String SAVE_BYTES_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "saveBytes";
    static final String SAVE_INPUT_STREAM_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "saveInputStream";
    static final String READ_BYTES_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "readBytes";
    static final String READ_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "read";
    static final String DELETE_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "delete";
    static final String DELETE_BUCKET_TIMER_NAME = BLOB_STORE_METRIC_PREFIX + "deleteBucket";

    private final MetricFactory metricFactory;
    private final BlobStore blobStoreImpl;

    @Inject
    public MetricableBlobStore(MetricFactory metricFactory,
                               @Named(BLOB_STORE_IMPLEMENTATION) BlobStore blobStoreImpl) {
        this.metricFactory = metricFactory;
        this.blobStoreImpl = blobStoreImpl;
    }

    @Override
    public Publisher<BlobId> save(Bucket bucket, byte[] data, StoragePolicy storagePolicy) {
        return metricFactory.decoratePublisherWithTimerMetric(SAVE_BYTES_TIMER_NAME, blobStoreImpl.save(bucket, data, storagePolicy));
    }

    @Override
    public Publisher<BlobId> save(Bucket bucket, InputStream data, StoragePolicy storagePolicy) {
        return metricFactory.decoratePublisherWithTimerMetric(SAVE_INPUT_STREAM_TIMER_NAME, blobStoreImpl.save(bucket, data, storagePolicy));
    }

    @Override
    public Publisher<BlobId> save(Bucket bucket, ByteSource data, StoragePolicy storagePolicy) {
        return metricFactory.decoratePublisherWithTimerMetric(SAVE_BYTES_TIMER_NAME, blobStoreImpl.save(bucket, data, storagePolicy));
    }

    @Override
    public Publisher<BlobId> save(Bucket bucket, byte[] data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy) {
        return metricFactory.decoratePublisherWithTimerMetric(SAVE_BYTES_TIMER_NAME, blobStoreImpl.save(bucket, data, blobIdProvider, storagePolicy));
    }

    @Override
    public Publisher<BlobId> save(Bucket bucket, InputStream data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy) {
        return metricFactory.decoratePublisherWithTimerMetric(SAVE_INPUT_STREAM_TIMER_NAME, blobStoreImpl.save(bucket, data, blobIdProvider, storagePolicy));
    }

    @Override
    public Publisher<BlobId> save(Bucket bucket, ByteSource data, BlobIdProvider blobIdProvider, StoragePolicy storagePolicy) {
        return metricFactory.decoratePublisherWithTimerMetric(SAVE_INPUT_STREAM_TIMER_NAME, blobStoreImpl.save(bucket, data, blobIdProvider, storagePolicy));
    }

    @Override
    public Publisher<byte[]> readBytes(Bucket bucket, BlobId blobId) {
        return metricFactory.decoratePublisherWithTimerMetric(READ_BYTES_TIMER_NAME, blobStoreImpl.readBytes(bucket, blobId));
    }

    @Override
    public InputStream read(Bucket bucket, BlobId blobId) {
        return metricFactory
            .decorateSupplierWithTimerMetric(READ_TIMER_NAME, () -> blobStoreImpl.read(bucket, blobId));
    }

    @Override
    public Publisher<InputStream> readReactive(Bucket bucket, BlobId blobId) {
        return metricFactory.decoratePublisherWithTimerMetric(READ_TIMER_NAME, blobStoreImpl.readReactive(bucket, blobId));
    }

    @Override
    public Publisher<byte[]> readBytes(Bucket bucket, BlobId blobId, StoragePolicy storagePolicy) {
        return metricFactory.decoratePublisherWithTimerMetric(READ_BYTES_TIMER_NAME, blobStoreImpl.readBytes(bucket, blobId, storagePolicy));
    }

    @Override
    public InputStream read(Bucket bucket, BlobId blobId, StoragePolicy storagePolicy) {
        return metricFactory
            .decorateSupplierWithTimerMetric(READ_TIMER_NAME, () -> blobStoreImpl.read(bucket, blobId, storagePolicy));
    }

    @Override
    public Publisher<Void> deleteBucket(Bucket bucket) {
        return metricFactory.decoratePublisherWithTimerMetric(DELETE_BUCKET_TIMER_NAME, blobStoreImpl.deleteBucket(bucket));
    }

    @Override
    public BucketName getDefaultBucketName() {
        return blobStoreImpl.getDefaultBucketName();
    }

    @Override
    public Publisher<Boolean> delete(Bucket bucket, BlobId blobId) {
        return metricFactory.decoratePublisherWithTimerMetric(DELETE_TIMER_NAME, blobStoreImpl.delete(bucket, blobId));
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return blobStoreImpl.listBuckets();
    }

    @Override
    public Publisher<BlobId> listBlobs(Bucket bucket) {
        return blobStoreImpl.listBlobs(bucket);
    }
}
