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
package org.apache.james.blob.cassandra.cache;

import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.UUID;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class CassandraBlobStoreCacheTest implements BlobStoreCacheContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraBlobCacheModule.MODULE);

    private static final int DEFAULT_THRESHOLD_IN_BYTES = EIGHT_KILOBYTES.length;
    private static final Duration _2_SEC_TTL = Duration.ofSeconds(2);

    private BlobStoreCache testee;
    private PlainBlobId.Factory blobIdFactory;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        blobIdFactory = new PlainBlobId.Factory();
        CassandraCacheConfiguration cacheConfiguration = new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(DEFAULT_THRESHOLD_IN_BYTES)
            .ttl(_2_SEC_TTL)
            .build();
        testee = new CassandraBlobStoreCache(cassandra.getConf(), cacheConfiguration);
    }

    @Override
    public BlobStoreCache testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return blobIdFactory;
    }

    @Test
    void cacheShouldNotPropagateFailures(CassandraCluster cassandra) {
        cassandra.getConf().registerScenario(fail()
            .forever()
            .whenQueryStartsWith("INSERT INTO blob_cache"));

        BlobId blobId = blobIdFactory().of(UUID.randomUUID().toString());

        assertThatCode(() -> Mono.from(testee.cache(blobId, EIGHT_KILOBYTES)).block())
            .doesNotThrowAnyException();
    }
}
