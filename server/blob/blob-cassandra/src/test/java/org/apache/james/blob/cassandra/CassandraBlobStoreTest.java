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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketBlobStoreContract;
import org.apache.james.blob.api.DeleteBlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.MetricableBlobStoreContract;
import org.apache.james.util.ZeroedInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;

public class CassandraBlobStoreTest implements MetricableBlobStoreContract, BucketBlobStoreContract, DeleteBlobStoreContract {
    private static final int CHUNK_SIZE = 10240;
    private static final int MULTIPLE_CHUNK_SIZE = 3;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraBlobModule.MODULE);

    private BlobStore testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();
        testee = new MetricableBlobStore(
            metricsTestExtension.getMetricFactory(),
            new CassandraBlobStore(new CassandraDefaultBucketDAO(cassandra.getConf()),
                new CassandraBucketDAO(blobIdFactory, cassandra.getConf()),
                CassandraConfiguration.builder()
                    .blobPartSize(CHUNK_SIZE)
                    .build(),
                blobIdFactory));
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new HashBlobId.Factory();
    }

    @Override
    @Disabled("Concurrent read and delete can lead to partial reads (no transactions)")
    public void readShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() {

    }

    @Test
    void readBytesShouldReturnSplitSavedDataByChunk() {
        String longString = Strings.repeat("0123456789\n", MULTIPLE_CHUNK_SIZE);
        BlobId blobId = testee.save(testee.getDefaultBucketName(), longString).block();

        byte[] bytes = testee.readBytes(testee.getDefaultBucketName(), blobId).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(longString);
    }

    @Test
    void blobStoreShouldSupport100MBBlob() {
        BlobId blobId = testee.save(testee.getDefaultBucketName(), new ZeroedInputStream(100_000_000)).block();
        InputStream bytes = testee.read(testee.getDefaultBucketName(), blobId);
        assertThat(bytes).hasSameContentAs(new ZeroedInputStream(100_000_000));
    }
}