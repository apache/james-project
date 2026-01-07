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

package org.apache.james.blob.cassandra;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.cassandra.CassandraBlobStoreDAO.CASSANDRA_BLOBSTORE_CL_ONE_HIT_COUNT_METRIC_NAME;
import static org.apache.james.blob.cassandra.CassandraBlobStoreDAO.CASSANDRA_BLOBSTORE_CL_ONE_MISS_COUNT_METRIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DeduplicationBlobStoreContract;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;

import reactor.core.publisher.Mono;

class CassandraBlobStoreClOneTest implements CassandraBlobStoreContract, DeduplicationBlobStoreContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraBlobDataDefinition.MODULE);

    private BlobStore testee;
    private CassandraDefaultBucketDAO defaultBucketDAO;
    private CassandraCluster cassandra;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        this.cassandra = cassandra;
        this.testee = createBlobStore();
    }

    @Override
    public MetricableBlobStore createBlobStore() {
        PlainBlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        CassandraBucketDAO bucketDAO = new CassandraBucketDAO(blobIdFactory, this.cassandra.getConf());
        defaultBucketDAO = spy(new CassandraDefaultBucketDAO(this.cassandra.getConf(), blobIdFactory));
        CassandraConfiguration cassandraConfiguration = CassandraConfiguration.builder()
                .blobPartSize(CHUNK_SIZE)
                .optimisticConsistencyLevel(true)
                .build();
        MetricFactory metricFactory = metricsTestExtension.getMetricFactory();
        return new MetricableBlobStore(
                metricFactory,
                BlobStoreFactory.builder()
                        .blobStoreDAO(new CassandraBlobStoreDAO(defaultBucketDAO, bucketDAO, cassandraConfiguration, metricFactory))
                        .blobIdFactory(blobIdFactory)
                        .deduplication());
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new PlainBlobId.Factory();
    }

    @Override
    public CassandraDefaultBucketDAO defaultBucketDAO() {
        return defaultBucketDAO;
    }

    @Override
    @Test
    public void readShouldNotReturnInvalidResultsWhenPartialDataPresent() {
        int repeatCount = MULTIPLE_CHUNK_SIZE * CHUNK_SIZE;
        String longString = Strings.repeat("0123456789\n", repeatCount);
        BlobId blobId = Mono.from(testee().save(BucketName.DEFAULT, longString, LOW_COST)).block();

        when(defaultBucketDAO().readPartClOne(blobId, 1)).thenReturn(Mono.empty());
        when(defaultBucketDAO().readPart(blobId, 1)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> IOUtils.toString(testee().read(BucketName.DEFAULT, blobId), StandardCharsets.UTF_8))
            .isInstanceOf(ObjectStoreException.class)
            .hasMessageContaining("Missing blob part for blobId");
    }

    @Override
    @Test
    public void readBytesShouldNotReturnInvalidResultsWhenPartialDataPresent() {
        int repeatCount = MULTIPLE_CHUNK_SIZE * CHUNK_SIZE;
        String longString = Strings.repeat("0123456789\n", repeatCount);
        BlobId blobId = Mono.from(testee().save(BucketName.DEFAULT, longString, LOW_COST)).block();

        when(defaultBucketDAO().readPartClOne(blobId, 1)).thenReturn(Mono.empty());
        when(defaultBucketDAO().readPart(blobId, 1)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> Mono.from(testee().readBytes(BucketName.DEFAULT, blobId)).block())
            .isInstanceOf(ObjectStoreException.class)
            .hasMessageContaining("Missing blob part for blobId");
    }

    @Test
    void readShouldReturnValidResultWhenDataMissingInOneNodeButPresentInOthers() throws IOException {
        int repeatCount = MULTIPLE_CHUNK_SIZE * CHUNK_SIZE;
        String longString = Strings.repeat("0123456789\n", repeatCount);
        BlobId blobId = Mono.from(testee().save(BucketName.DEFAULT, longString, LOW_COST)).block();

        when(defaultBucketDAO().selectRowCountClOne(blobId)).thenReturn(Mono.empty());

        String data = IOUtils.toString(testee().read(BucketName.DEFAULT, blobId), StandardCharsets.UTF_8);

        assertThat(data).isEqualTo(longString);
    }

    @Test
    void readBytesShouldReturnValidResultWhenDataMissingInOneNodeButPresentInOthers() {
        int repeatCount = MULTIPLE_CHUNK_SIZE * CHUNK_SIZE;
        String longString = Strings.repeat("0123456789\n", repeatCount);
        BlobId blobId = Mono.from(testee().save(BucketName.DEFAULT, longString, LOW_COST)).block();

        when(defaultBucketDAO().selectRowCountClOne(blobId)).thenReturn(Mono.empty());

        byte[] bytes = Mono.from(testee().readBytes(BucketName.DEFAULT, blobId)).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(longString);
    }

    @Test
    void readShouldReturnValidResultWhenPartialDataMissingInOneNodeButPresentInOthers() throws IOException {
        int repeatCount = MULTIPLE_CHUNK_SIZE * CHUNK_SIZE;
        String longString = Strings.repeat("0123456789\n", repeatCount);
        BlobId blobId = Mono.from(testee().save(BucketName.DEFAULT, longString, LOW_COST)).block();

        when(defaultBucketDAO().readPartClOne(blobId, 1)).thenReturn(Mono.empty());

        String data = IOUtils.toString(testee().read(BucketName.DEFAULT, blobId), StandardCharsets.UTF_8);

        assertThat(data).isEqualTo(longString);
    }

    @Test
    void readBytesShouldReturnValidResultWhenPartialDataMissingInOneNodeButPresentInOthers() {
        int repeatCount = MULTIPLE_CHUNK_SIZE * CHUNK_SIZE;
        String longString = Strings.repeat("0123456789\n", repeatCount);
        BlobId blobId = Mono.from(testee().save(BucketName.DEFAULT, longString, LOW_COST)).block();

        when(defaultBucketDAO().readPartClOne(blobId, 1)).thenReturn(Mono.empty());

        byte[] bytes = Mono.from(testee().readBytes(BucketName.DEFAULT, blobId)).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(longString);
    }

    @Test
    void readShouldPublishHitRatioClOneMetric() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(BucketName.DEFAULT, BYTES_CONTENT, LOW_COST)).block();
        store.read(BucketName.DEFAULT, blobId);

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().countFor(CASSANDRA_BLOBSTORE_CL_ONE_HIT_COUNT_METRIC_NAME))
                .isEqualTo(2));
    }

    @Test
    void readBytesShouldPublishHitRatioClOneMetric() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(BucketName.DEFAULT, BYTES_CONTENT, LOW_COST)).block();
        Mono.from(store.readBytes(BucketName.DEFAULT, blobId)).block();

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().countFor(CASSANDRA_BLOBSTORE_CL_ONE_HIT_COUNT_METRIC_NAME))
                .isEqualTo(2));
    }

    @Test
    void readShouldPublishMissRatioClOneMetric() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(BucketName.DEFAULT, BYTES_CONTENT, LOW_COST)).block();

        when(defaultBucketDAO().selectRowCountClOne(blobId)).thenReturn(Mono.empty());
        store.read(BucketName.DEFAULT, blobId);

        when(defaultBucketDAO().readPartClOne(blobId, 1)).thenReturn(Mono.empty());
        store.read(BucketName.DEFAULT, blobId);

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().countFor(CASSANDRA_BLOBSTORE_CL_ONE_MISS_COUNT_METRIC_NAME))
                .isGreaterThanOrEqualTo(2));
    }

    @Test
    void readBytesShouldPublishMissRatioClOneMetric() {
        BlobStore store = testee();

        BlobId blobId = Mono.from(store.save(BucketName.DEFAULT, BYTES_CONTENT, LOW_COST)).block();

        when(defaultBucketDAO().selectRowCountClOne(blobId)).thenReturn(Mono.empty());
        Mono.from(store.readBytes(BucketName.DEFAULT, blobId)).block();

        when(defaultBucketDAO().readPartClOne(blobId, 1)).thenReturn(Mono.empty());
        Mono.from(store.readBytes(BucketName.DEFAULT, blobId)).block();

        await().atMost(FIVE_SECONDS)
            .untilAsserted(() ->  assertThat(metricsTestExtension.getMetricFactory().countFor(CASSANDRA_BLOBSTORE_CL_ONE_MISS_COUNT_METRIC_NAME))
                .isGreaterThanOrEqualTo(2));
    }
}
