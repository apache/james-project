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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.io.InputStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DeduplicationBlobStoreContract;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.apache.james.util.io.ZeroedInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;

import reactor.core.publisher.Mono;

public class CassandraBlobStoreTest implements CassandraBlobStoreContract, DeduplicationBlobStoreContract {
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
                .build();
        MetricFactory metricFactory = metricsTestExtension.getMetricFactory();
        return new MetricableBlobStore(
                metricFactory,
                BlobStoreFactory.builder()
                        .blobStoreDAO(new CassandraBlobStoreDAO(defaultBucketDAO, bucketDAO, cassandraConfiguration, BucketName.DEFAULT, metricFactory))
                        .blobIdFactory(blobIdFactory)
                        .defaultBucketName()
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

    @Test
    void blobStoreShouldSupport100MBBlob() throws IOException {
        ZeroedInputStream data = new ZeroedInputStream(100_000_000);
        HashingInputStream writeHash = new HashingInputStream(Hashing.sha256(), data);
        BlobId blobId = Mono.from(testee().save(testee().getDefaultBucketName(), writeHash, LOW_COST)).block();

        InputStream bytes = testee().read(testee().getDefaultBucketName(), blobId);
        HashingInputStream readHash = new HashingInputStream(Hashing.sha256(), bytes);
        consumeStream(readHash);

        assertThat(readHash.hash().toString()).isEqualTo(writeHash.hash().toString());
    }

    private void consumeStream(InputStream tmpMsgIn) throws IOException {
        byte[] discard = new byte[4096];
        while (tmpMsgIn.read(discard) != -1) {
            // consume the rest of the stream
        }
    }
}
