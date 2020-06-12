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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.HIGH_PERFORMANCE;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.blob.api.BucketName.DEFAULT;
import static org.apache.james.blob.cassandra.cache.BlobStoreCacheContract.EIGHT_KILOBYTES;
import static org.apache.james.blob.cassandra.cache.CachedBlobStore.BLOBSTORE_BACKEND_LATENCY_METRIC_NAME;
import static org.apache.james.blob.cassandra.cache.CachedBlobStore.BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME;
import static org.apache.james.blob.cassandra.cache.CachedBlobStore.BLOBSTORE_CACHED_LATENCY_METRIC_NAME;
import static org.apache.james.blob.cassandra.cache.CachedBlobStore.BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.TestBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStore;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;

import reactor.core.publisher.Mono;

public class CachedBlobStoreTest implements BlobStoreContract {

    private static final BucketName DEFAULT_BUCKETNAME = DEFAULT;
    private static final BucketName TEST_BUCKETNAME = BucketName.of("test");
    byte[] APPROXIMATELY_FIVE_KILOBYTES = Strings.repeat("0123456789\n", 500).getBytes(StandardCharsets.UTF_8);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraBlobModule.MODULE, CassandraBlobCacheModule.MODULE));

    private BlobStore testee;
    private BlobStore backend;
    private BlobStoreCache cache;
    private RecordingMetricFactory metricFactory;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        backend = CassandraBlobStore.forTesting(cassandra.getConf());
        CassandraCacheConfiguration cacheConfig = new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(EIGHT_KILOBYTES.length + 1)
            .timeOut(Duration.ofSeconds(60))
            .build();
        metricFactory = new RecordingMetricFactory();
        cache = new CassandraBlobStoreCache(cassandra.getConf(), cacheConfig);
        testee = new CachedBlobStore(cache, backend, cacheConfig, metricFactory);
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
    public void shouldCacheWhenDefaultBucketName() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, EIGHT_KILOBYTES, SIZE_BASED)).block();

        byte[] actual = Mono.from(cache.read(blobId)).block();
        assertThat(actual).containsExactly(EIGHT_KILOBYTES);
    }

    @Test
    public void shouldNotCacheWhenNotDefaultBucketName() {
        BlobId blobId = Mono.from(testee().save(TEST_BUCKETNAME, EIGHT_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThat(Mono.from(backend.readBytes(TEST_BUCKETNAME, blobId)).block()).containsExactly(EIGHT_KILOBYTES);
        });
    }

    @Test
    public void shouldNotCacheWhenDefaultBucketNameAndBigByteDataAndSizeBase() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, TWELVE_MEGABYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKETNAME, blobId)).block()).containsExactly(TWELVE_MEGABYTES);
        });
    }

    @Test
    public void shouldSavedBothInCacheAndBackendWhenSizeBase() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, EIGHT_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).block()).containsExactly(EIGHT_KILOBYTES);
            soflty.assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKETNAME, blobId)).block()).containsExactly(EIGHT_KILOBYTES);
        });
    }

    @Test
    public void shouldSavedBothInCacheAndBackendWhenHighPerformance() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, EIGHT_KILOBYTES, HIGH_PERFORMANCE)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).block()).containsExactly(EIGHT_KILOBYTES);
            soflty.assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKETNAME, blobId)).block()).containsExactly(EIGHT_KILOBYTES);
        });
    }

    @Test
    public void shouldNotCacheWhenLowCost() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, EIGHT_KILOBYTES, LOW_COST)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKETNAME, blobId)).block()).containsExactly(EIGHT_KILOBYTES);
        });
    }

    @Test
    public void shouldCacheWhenEmptyStream() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, new ByteArrayInputStream(EMPTY_BYTEARRAY), SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(new ByteArrayInputStream(Mono.from(cache.read(blobId)).block())).hasSameContentAs(new ByteArrayInputStream(EMPTY_BYTEARRAY));
            soflty.assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKETNAME, blobId)).block()).containsExactly(EMPTY_BYTEARRAY);
        });
    }

    @Test
    public void shouldNotCacheWhenEmptyByteArray() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, EMPTY_BYTEARRAY, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(new ByteArrayInputStream(Mono.from(cache.read(blobId)).block())).hasSameContentAs(new ByteArrayInputStream(EMPTY_BYTEARRAY));
            soflty.assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKETNAME, blobId)).block()).containsExactly(EMPTY_BYTEARRAY);
        });
    }

    @Test
    public void shouldCacheWhenFiveKilobytesSteam() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES), SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(new ByteArrayInputStream(Mono.from(cache.read(blobId)).block()))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
            soflty.assertThat(new ByteArrayInputStream(Mono.from(backend.readBytes(DEFAULT_BUCKETNAME, blobId)).block()))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
        });
    }

    @Test
    public void shouldCacheWhenFiveKilobytesBytes() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(new ByteArrayInputStream(Mono.from(cache.read(blobId)).block()))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
            soflty.assertThat(new ByteArrayInputStream(Mono.from(backend.readBytes(DEFAULT_BUCKETNAME, blobId)).block()))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
        });
    }

    @Test
    public void shouldRemoveBothInCacheAndBackendWhenDefaultBucketName() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKETNAME, EIGHT_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThatCode(Mono.from(testee().delete(DEFAULT_BUCKETNAME, blobId))::block)
                .doesNotThrowAnyException();
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThatThrownBy(() -> Mono.from(backend.readBytes(DEFAULT_BUCKETNAME, blobId)).block())
                .isInstanceOf(ObjectNotFoundException.class);
        });
    }

    @Test
    public void shouldCacheWhenReadBytesWithDefaultBucket() {
        BlobId blobId = Mono.from(backend.save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThat(new ByteArrayInputStream(Mono.from(testee().readBytes(DEFAULT_BUCKETNAME, blobId)).block()))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
            soflty.assertThat(new ByteArrayInputStream(Mono.from(cache.read(blobId)).block()))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
        });
    }

    @Test
    public void shouldCacheWhenReadWithDefaultBucket() {
        BlobId blobId = Mono.from(backend.save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThat(testee().read(DEFAULT_BUCKETNAME, blobId))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
            soflty.assertThat(new ByteArrayInputStream(Mono.from(cache.read(blobId)).block()))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
        });
    }

    @Test
    public void shouldNotCacheWhenReadBytesWithOutDefaultBucket() {
        BlobId blobId = Mono.from(backend.save(TEST_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThat(new ByteArrayInputStream(Mono.from(testee().readBytes(TEST_BUCKETNAME, blobId)).block()))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
        });
    }

    @Test
    public void shouldNotCacheWhenReadWithOutDefaultBucket() {
        BlobId blobId = Mono.from(backend.save(TEST_BUCKETNAME, new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES), SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThat(testee().read(TEST_BUCKETNAME, blobId))
                .hasSameContentAs(new ByteArrayInputStream(APPROXIMATELY_FIVE_KILOBYTES));
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
        });
    }

    @Test
    public void shouldNotCacheWhenReadWithBigByteArray() {
        BlobId blobId = Mono.from(backend.save(DEFAULT_BUCKETNAME, new ByteArrayInputStream(TWELVE_MEGABYTES), SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThat(new ByteArrayInputStream(Mono.from(testee().readBytes(DEFAULT_BUCKETNAME, blobId)).block()))
                .hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES));
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
        });
    }

    @Test
    public void shouldNotCacheWhenReadWithBigStream() {
        BlobId blobId = Mono.from(testee.save(DEFAULT_BUCKETNAME, new ByteArrayInputStream(TWELVE_MEGABYTES), SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
            soflty.assertThat(testee().read(DEFAULT_BUCKETNAME, blobId))
                .hasSameContentAs(new ByteArrayInputStream(TWELVE_MEGABYTES));
            soflty.assertThat(Mono.from(cache.read(blobId)).blockOptional()).isEmpty();
        });
    }

    @Nested
    class MetricsTest {
        @Test
        void readBlobStoreCacheWithNoneDefaultBucketNameShouldNotImpact() {
            BlobId blobId = Mono.from(testee.save(TEST_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            testee.read(TEST_BUCKETNAME, blobId);
            testee.read(TEST_BUCKETNAME, blobId);

            SoftAssertions.assertSoftly(soflty -> {
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_CACHED_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_LATENCY_METRIC_NAME)
                    .hasSize(0);
            });
        }

        @Test
        void readBlobStoreWithNoneDefaultBucketNameShouldRecordByBackendLatency() {
            BlobId blobId = Mono.from(testee.save(TEST_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            testee.read(TEST_BUCKETNAME, blobId);
            testee.read(TEST_BUCKETNAME, blobId);

            SoftAssertions.assertSoftly(soflty ->
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME)
                    .hasSize(2));
        }

        @Test
        void readBytesWithNoneDefaultBucketNameShouldNotImpact() {
            BlobId blobId = Mono.from(testee.save(TEST_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            Mono.from(testee.readBytes(TEST_BUCKETNAME, blobId)).block();
            Mono.from(testee.readBytes(TEST_BUCKETNAME, blobId)).block();


            SoftAssertions.assertSoftly(soflty -> {
                assertThat(metricFactory.countFor(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_CACHED_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_LATENCY_METRIC_NAME)
                    .hasSize(0);
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME)
                    .hasSize(2);
            });
        }

        @Test
        void readBytesWithNoneDefaultBucketNameShouldPublishBackendTimerMetrics() {
            BlobId blobId = Mono.from(testee.save(TEST_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            Mono.from(testee.readBytes(TEST_BUCKETNAME, blobId)).block();
            Mono.from(testee.readBytes(TEST_BUCKETNAME, blobId)).block();

            SoftAssertions.assertSoftly(soflty ->
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME)
                    .hasSize(2));
        }

        @Test
        void readBlobStoreCacheShouldPublishTimerMetrics() {
            BlobId blobId = Mono.from(testee.save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            testee.read(DEFAULT_BUCKETNAME, blobId);
            testee.read(DEFAULT_BUCKETNAME, blobId);

            SoftAssertions.assertSoftly(soflty -> {
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_CACHED_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_LATENCY_METRIC_NAME)
                    .hasSize(2);
            });
        }

        @Test
        void readBytesCacheShouldPublishTimerMetrics() {
            BlobId blobId = Mono.from(testee.save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId)).block();
            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId)).block();

            SoftAssertions.assertSoftly(soflty -> {
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_CACHED_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_LATENCY_METRIC_NAME)
                    .hasSize(2);
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME)
                    .isEqualTo(2);
            });
        }

        @Test
        void readBytesShouldPublishBackendTimerMetricsForBigBlobs() {
            BlobId blobId = Mono.from(backend.save(DEFAULT_BUCKETNAME, ELEVEN_KILOBYTES, SIZE_BASED)).block();

            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId)).block();
            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId)).block();

            SoftAssertions.assertSoftly(soflty ->
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME)
                    .hasSize(2));
        }

        @Test
        void readInputStreamShouldPublishBackendTimerForBigBlobs() {
            BlobId blobId = Mono.from(backend.save(DEFAULT_BUCKETNAME, ELEVEN_KILOBYTES, SIZE_BASED)).block();

            testee.read(DEFAULT_BUCKETNAME, blobId);
            testee.read(DEFAULT_BUCKETNAME, blobId);

            SoftAssertions.assertSoftly(soflty ->
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME)
                    .hasSize(2));
        }

        @Test
        void readBytesShouldNotIncreaseCacheCounterForBigBlobs() {
            BlobId blobId = Mono.from(backend.save(DEFAULT_BUCKETNAME, ELEVEN_KILOBYTES, SIZE_BASED)).block();

            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId)).block();
            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId)).block();

            SoftAssertions.assertSoftly(soflty -> {
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME)
                    .isEqualTo(0);
            });
        }

        @Test
        void readInputStreamShouldNotIncreaseCacheCounterForBigBlobs() {
            BlobId blobId = Mono.from(backend.save(DEFAULT_BUCKETNAME, ELEVEN_KILOBYTES, SIZE_BASED)).block();

            testee.read(DEFAULT_BUCKETNAME, blobId);
            testee.read(DEFAULT_BUCKETNAME, blobId);

            SoftAssertions.assertSoftly(soflty -> {
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME)
                    .isEqualTo(0);
            });
        }

        @Test
        void readBytesShouldRecordDistinctTimingsWhenRepeatAndBackendRead() {
            BlobId blobId = Mono.from(testee.save(DEFAULT_BUCKETNAME, ELEVEN_KILOBYTES, SIZE_BASED)).block();

            Duration delay = Duration.ofMillis(500);
            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId))
                .then(Mono.delay(delay))
                .repeat(2)
                .blockLast();

            assertThat(metricFactory.executionTimesFor(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME))
                .hasSize(3)
                .allSatisfy(timing -> assertThat(timing).isLessThan(delay));
        }

        @Test
        void readBytesShouldRecordDistinctTimingsWhenRepeat() {
            BlobId blobId = Mono.from(testee.save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            Duration delay = Duration.ofMillis(500);
            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId))
                .then(Mono.delay(delay))
                .repeat(2)
                .blockLast();

            assertThat(metricFactory.executionTimesFor(BLOBSTORE_CACHED_LATENCY_METRIC_NAME))
                .hasSize(3)
                .allSatisfy(timing -> assertThat(timing).isLessThan(delay));
        }

        @Test
        void readBlobStoreCacheShouldCountWhenHit() {
            BlobId blobId = Mono.from(testee.save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            testee.read(DEFAULT_BUCKETNAME, blobId);
            testee.read(DEFAULT_BUCKETNAME, blobId);

            assertThat(metricFactory.countFor(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME)).isEqualTo(2);
        }

        @Test
        void readBytesCacheShouldCountWhenHit() {
            BlobId blobId = Mono.from(testee.save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId)).block();
            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId)).block();

            assertThat(metricFactory.countFor(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME)).isEqualTo(2);
        }


        @Test
        void readBlobStoreCacheShouldCountWhenMissed() {
            BlobId blobId = Mono.from(backend.save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            Mono.from(cache.remove(blobId)).block();
            testee.read(DEFAULT_BUCKETNAME, blobId);

            assertThat(metricFactory.countFor(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME)).isEqualTo(1);
        }

        @Test
        void readBytesCacheShouldCountWhenMissed() {
            BlobId blobId = Mono.from(testee.save(DEFAULT_BUCKETNAME, APPROXIMATELY_FIVE_KILOBYTES, SIZE_BASED)).block();

            Mono.from(cache.remove(blobId)).block();
            Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, blobId)).block();

            assertThat(metricFactory.countFor(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME)).isEqualTo(1);
        }

        @Test
        void metricsShouldNotWorkExceptLatencyWhenReadNonExistingBlob() {
            SoftAssertions.assertSoftly(soflty -> {
                soflty.assertThatThrownBy(() -> testee.read(DEFAULT_BUCKETNAME, new TestBlobId.Factory().randomId()))
                    .isInstanceOf(ObjectNotFoundException.class);

                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_CACHED_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_LATENCY_METRIC_NAME)
                    .hasSize(1);
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME)
                    .hasSize(1);
            });
        }

        @Test
        void metricsShouldNotWorkExceptLatencyWhenReadNonExistingBlobAsBytes() {
            SoftAssertions.assertSoftly(soflty -> {
                soflty.assertThatThrownBy(() -> Mono.from(testee.readBytes(DEFAULT_BUCKETNAME, new TestBlobId.Factory().randomId())).blockOptional())
                    .isInstanceOf(ObjectNotFoundException.class);

                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_MISS_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.countFor(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_HIT_COUNT_METRIC_NAME)
                    .isEqualTo(0);
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_CACHED_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_CACHED_LATENCY_METRIC_NAME)
                    .hasSize(1);
                soflty.assertThat(metricFactory.executionTimesFor(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME))
                    .describedAs(BLOBSTORE_BACKEND_LATENCY_METRIC_NAME)
                    .hasSize(1);
            });
        }
    }
}
