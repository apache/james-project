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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.MetricableBlobStoreContract;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.apache.james.util.io.ZeroedInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import reactor.core.publisher.Mono;

public class CassandraPassTroughBlobStoreTest implements MetricableBlobStoreContract {
    private static final int CHUNK_SIZE = 10240;
    private static final int MULTIPLE_CHUNK_SIZE = 3;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraBlobModule.MODULE);

    private BlobStore testee;
    private CassandraDefaultBucketDAO defaultBucketDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();
        CassandraBucketDAO bucketDAO = new CassandraBucketDAO(blobIdFactory, cassandra.getConf());
        defaultBucketDAO = spy(new CassandraDefaultBucketDAO(cassandra.getConf()));
        CassandraConfiguration cassandraConfiguration = CassandraConfiguration.builder()
            .blobPartSize(CHUNK_SIZE)
            .build();
        testee = new MetricableBlobStore(
            metricsTestExtension.getMetricFactory(),
                BlobStoreFactory.builder()
                    .dumbBlobStore(new CassandraDumbBlobStore(defaultBucketDAO, bucketDAO, cassandraConfiguration, BucketName.DEFAULT))
                    .blobIdFactory(blobIdFactory)
                    .defaultBucketName()
                    .passthrough());
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new HashBlobId.Factory();
    }

    @Test
    void readBytesShouldReturnSplitSavedDataByChunk() {
        String longString = Strings.repeat("0123456789\n", MULTIPLE_CHUNK_SIZE);
        BlobId blobId = Mono.from(testee.save(testee.getDefaultBucketName(), longString, LOW_COST)).block();

        byte[] bytes = Mono.from(testee.readBytes(testee.getDefaultBucketName(), blobId)).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(longString);
    }

    @Test
    void readBytesShouldNotReturnInvalidResultsWhenPartialDataPresent() {
        int repeatCount = MULTIPLE_CHUNK_SIZE * CHUNK_SIZE;
        String longString = Strings.repeat("0123456789\n", repeatCount);
        BlobId blobId = Mono.from(testee.save(testee.getDefaultBucketName(), longString, LOW_COST)).block();

        when(defaultBucketDAO.readPart(blobId, 1)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> Mono.from(testee.readBytes(testee.getDefaultBucketName(), blobId)).block())
            .isInstanceOf(ObjectStoreException.class)
            .hasMessageContaining("Missing blob part for blobId");
    }

    @Test
    void readShouldNotReturnInvalidResultsWhenPartialDataPresent() {
        int repeatCount = MULTIPLE_CHUNK_SIZE * CHUNK_SIZE;
        String longString = Strings.repeat("0123456789\n", repeatCount);
        BlobId blobId = Mono.from(testee.save(testee.getDefaultBucketName(), longString, LOW_COST)).block();

        when(defaultBucketDAO.readPart(blobId, 1)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> IOUtils.toString(testee.read(testee.getDefaultBucketName(), blobId), StandardCharsets.UTF_8))
            .isInstanceOf(ObjectStoreException.class)
            .hasMessageContaining("Missing blob part for blobId");
    }

    @Test
    void deleteBucketShouldThrowWhenDeletingDefaultBucket() {
        assertThatThrownBy(() ->  testee.deleteBucket(testee.getDefaultBucketName()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Deleting the default bucket is forbidden");
    }

    @Test
    void blobStoreShouldSupport100MBBlob() throws IOException {
        ZeroedInputStream data = new ZeroedInputStream(100_000_000);
        HashingInputStream writeHash = new HashingInputStream(Hashing.sha256(), data);
        BlobId blobId = Mono.from(testee.save(testee.getDefaultBucketName(), writeHash, LOW_COST)).block();

        InputStream bytes = testee.read(testee.getDefaultBucketName(), blobId);
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