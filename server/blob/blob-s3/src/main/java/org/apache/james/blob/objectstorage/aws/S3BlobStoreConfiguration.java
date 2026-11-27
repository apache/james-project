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

package org.apache.james.blob.objectstorage.aws;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.objectstorage.aws.sse.S3SSECConfiguration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import reactor.util.retry.Retry;
import software.amazon.awssdk.core.exception.SdkException;

public class S3BlobStoreConfiguration {

    public static Builder.RequireAuthConfiguration builder() {
        return authConfiguration -> region -> new Builder.ReadyToBuild(authConfiguration, region);
    }

    public interface Builder {

        @FunctionalInterface
        interface RequireAuthConfiguration {
            RequireRegion authConfiguration(AwsS3AuthConfiguration authConfiguration);
        }

        @FunctionalInterface
        interface RequireRegion {
            ReadyToBuild region(Region region);
        }

        @FunctionalInterface
        interface RequireSSECConfiguration {
            ReadyToBuild ssecConfiguration(S3SSECConfiguration ssecConfiguration);

            default ReadyToBuild ssecConfiguration(Configuration configuration) {
                return ssecConfiguration(S3SSECConfiguration.from(configuration));
            }
        }

        class ReadyToBuild {

            private final AwsS3AuthConfiguration specificAuthConfiguration;

            private Optional<BucketName> defaultBucketName;
            private Optional<String> bucketPrefix;
            private Optional<Integer> httpConcurrency;
            private Optional<Duration> readTimeout;
            private Optional<Duration> writeTimeout;
            private Optional<Duration> connectionTimeout;
            private Optional<Long> inMemoryReadLimit;
            private Region region;
            private Optional<Retry> uploadRetrySpec;
            private boolean ssecEnabled;
            private Optional<S3SSECConfiguration> ssecConfiguration = Optional.empty();
            private Optional<BucketName> fallbackBucketName;
            private Optional<Long> multipartUploadThreshold;
            private Optional<Long> multipartUploadPartSize;

            public ReadyToBuild(AwsS3AuthConfiguration specificAuthConfiguration, Region region) {
                this.specificAuthConfiguration = specificAuthConfiguration;
                this.region = region;
                this.defaultBucketName = Optional.empty();
                this.bucketPrefix = Optional.empty();
                this.httpConcurrency = Optional.empty();
                this.readTimeout = Optional.empty();
                this.writeTimeout = Optional.empty();
                this.connectionTimeout = Optional.empty();
                this.inMemoryReadLimit = Optional.empty();
                this.uploadRetrySpec = Optional.empty();
                this.fallbackBucketName = Optional.empty();
                this.multipartUploadThreshold = Optional.empty();
                this.multipartUploadPartSize = Optional.empty();
            }

            public ReadyToBuild defaultBucketName(Optional<BucketName> defaultBucketName) {
                this.defaultBucketName = defaultBucketName;
                return this;
            }

            public ReadyToBuild defaultBucketName(BucketName defaultBucketName) {
                this.defaultBucketName = Optional.of(defaultBucketName);
                return this;
            }

            public ReadyToBuild bucketPrefix(Optional<String> bucketPrefix) {
                this.bucketPrefix = bucketPrefix;
                return this;
            }

            public ReadyToBuild writeTimeout(Optional<Duration> writeTimeout) {
                this.writeTimeout = writeTimeout;
                return this;
            }

            public ReadyToBuild inMemoryReadLimit(Optional<Long> inMemoryReadLimit) {
                this.inMemoryReadLimit = inMemoryReadLimit;
                return this;
            }

            public ReadyToBuild connectionTimeout(Optional<Duration> connectionTimeout) {
                this.connectionTimeout = connectionTimeout;
                return this;
            }

            public ReadyToBuild readTimeout(Optional<Duration> readTimeout) {
                this.readTimeout = readTimeout;
                return this;
            }

            public ReadyToBuild bucketPrefix(String bucketPrefix) {
                this.bucketPrefix = Optional.ofNullable(bucketPrefix);
                return this;
            }

            public ReadyToBuild httpConcurrency(Optional<Integer> httpConcurrency) {
                this.httpConcurrency = httpConcurrency;
                return this;
            }

            public ReadyToBuild uploadRetrySpec(Optional<Retry> uploadRetrySpec) {
                this.uploadRetrySpec = uploadRetrySpec;
                return this;
            }

            public RequireSSECConfiguration ssecEnabled() {
                this.ssecEnabled = true;
                return ssecConfiguration -> {
                    Preconditions.checkArgument(ssecConfiguration != null, "SSEC configuration is mandatory when SSEC is enabled");
                    this.ssecConfiguration = Optional.of(ssecConfiguration);
                    return this;
                };
            }

            public ReadyToBuild ssecDisabled() {
                this.ssecEnabled = false;
                return this;
            }

            public ReadyToBuild fallbackBucketName(Optional<BucketName> fallbackBucketName) {
                this.fallbackBucketName = fallbackBucketName;
                return this;
            }

            public ReadyToBuild multipartUploadThreshold(Optional<Long> multipartUploadThreshold) {
                this.multipartUploadThreshold = multipartUploadThreshold;
                return this;
            }

            public ReadyToBuild multipartUploadPartSize(Optional<Long> multipartUploadPartSize) {
                this.multipartUploadPartSize = multipartUploadPartSize;
                return this;
            }

            public S3BlobStoreConfiguration build() {
                long threshold = multipartUploadThreshold.orElse(DEFAULT_MULTIPART_UPLOAD_THRESHOLD);
                long partSize = multipartUploadPartSize.orElse(DEFAULT_MULTIPART_UPLOAD_PART_SIZE);
                Preconditions.checkArgument(threshold > 0, "'multipartUploadThreshold' must be strictly positive");
                Preconditions.checkArgument(partSize >= MIN_MULTIPART_UPLOAD_PART_SIZE && partSize <= MAX_MULTIPART_UPLOAD_PART_SIZE,
                    "'multipartUploadPartSize' must be between 5 MB and 5 GB");

                return new S3BlobStoreConfiguration(bucketPrefix, defaultBucketName, region,
                    specificAuthConfiguration, httpConcurrency.orElse(DEFAULT_HTTP_CONCURRENCY),
                    inMemoryReadLimit, readTimeout, writeTimeout, connectionTimeout, uploadRetrySpec.orElse(DEFAULT_UPLOAD_RETRY_SPEC),
                    ssecEnabled, ssecConfiguration, fallbackBucketName, threshold, partSize);
            }
        }

    }

    public static int DEFAULT_HTTP_CONCURRENCY = 100;
    public static final Duration UPLOAD_RETRY_BACKOFF_DURATION_DEFAULT = Duration.ofMillis(10);
    public static final Double UPLOAD_RETRY_BACKOFF_JETTY_DEFAULT = 0.5;
    public static final Predicate<Throwable> UPLOAD_RETRY_EXCEPTION_PREDICATE = SdkException.class::isInstance;
    public static final Retry DEFAULT_UPLOAD_RETRY_SPEC = Retry.backoff(0, UPLOAD_RETRY_BACKOFF_DURATION_DEFAULT).filter(UPLOAD_RETRY_EXCEPTION_PREDICATE);
    // S3 rejects single PUTs larger than 5 GB: bigger blobs need to be uploaded as several parts
    public static final long DEFAULT_MULTIPART_UPLOAD_THRESHOLD = 500L * 1024 * 1024;
    public static final long DEFAULT_MULTIPART_UPLOAD_PART_SIZE = 100L * 1024 * 1024;
    // Bounds S3 enforces on multipart uploads parts, but the last one
    public static final long MIN_MULTIPART_UPLOAD_PART_SIZE = 5L * 1024 * 1024;
    public static final long MAX_MULTIPART_UPLOAD_PART_SIZE = 5L * 1024 * 1024 * 1024;
    private final Region region;
    private final AwsS3AuthConfiguration specificAuthConfiguration;
    private final Optional<BucketName> namespace;
    private final Optional<String> bucketPrefix;
    private final int httpConcurrency;
    private final Optional<Long> inMemoryReadLimit;
    private final Retry uploadRetrySpec;
    private final boolean ssecEnabled;
    private final Optional<S3SSECConfiguration> ssecConfiguration;
    private final Optional<BucketName> fallbackNamespace;
    private final long multipartUploadThreshold;
    private final long multipartUploadPartSize;

    private final Optional<Duration> readTimeout;
    private final Optional<Duration> writeTimeout;
    private final Optional<Duration> connectionTimeout;

    @VisibleForTesting
    S3BlobStoreConfiguration(Optional<String> bucketPrefix,
                             Optional<BucketName> namespace,
                             Region region,
                             AwsS3AuthConfiguration specificAuthConfiguration,
                             int httpConcurrency,
                             Optional<Long> inMemoryReadLimit,
                             Optional<Duration> readTimeout,
                             Optional<Duration> writeTimeout,
                             Optional<Duration> connectionTimeout,
                             Retry uploadRetrySpec,
                             boolean ssecEnabled,
                             Optional<S3SSECConfiguration> ssecConfiguration,
                             Optional<BucketName> fallbackNamespace,
                             long multipartUploadThreshold,
                             long multipartUploadPartSize) {
        this.bucketPrefix = bucketPrefix;
        this.namespace = namespace;
        this.region = region;
        this.specificAuthConfiguration = specificAuthConfiguration;
        this.httpConcurrency = httpConcurrency;
        this.inMemoryReadLimit = inMemoryReadLimit;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.connectionTimeout = connectionTimeout;
        this.uploadRetrySpec = uploadRetrySpec;
        this.ssecEnabled = ssecEnabled;
        this.ssecConfiguration = ssecConfiguration;
        this.fallbackNamespace = fallbackNamespace;
        this.multipartUploadThreshold = multipartUploadThreshold;
        this.multipartUploadPartSize = multipartUploadPartSize;
    }

    public Optional<Long> getInMemoryReadLimit() {
        return inMemoryReadLimit;
    }

    public Optional<BucketName> getNamespace() {
        return namespace;
    }

    public AwsS3AuthConfiguration getSpecificAuthConfiguration() {
        return specificAuthConfiguration;
    }

    public Optional<String> getBucketPrefix() {
        return bucketPrefix;
    }

    public int getHttpConcurrency() {
        return httpConcurrency;
    }

    public Region getRegion() {
        return region;
    }

    public Optional<Duration> getReadTimeout() {
        return readTimeout;
    }

    public Optional<Duration> getWriteTimeout() {
        return writeTimeout;
    }

    public Optional<Duration> getConnectionTimeout() {
        return connectionTimeout;
    }

    public Retry uploadRetrySpec() {
        return uploadRetrySpec;
    }

    public boolean ssecEnabled() {
        return ssecEnabled;
    }

    public Optional<S3SSECConfiguration> getSSECConfiguration() {
        return ssecConfiguration;
    }

    public Optional<BucketName> getFallbackNamespace() {
        return fallbackNamespace;
    }

    public long getMultipartUploadThreshold() {
        return multipartUploadThreshold;
    }

    public long getMultipartUploadPartSize() {
        return multipartUploadPartSize;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof S3BlobStoreConfiguration that) {
            return Objects.equals(this.namespace, that.namespace)
                && Objects.equals(this.bucketPrefix, that.bucketPrefix)
                && Objects.equals(this.region, that.region)
                && Objects.equals(this.httpConcurrency, that.httpConcurrency)
                && Objects.equals(this.inMemoryReadLimit, that.inMemoryReadLimit)
                && Objects.equals(this.readTimeout, that.readTimeout)
                && Objects.equals(this.writeTimeout, that.writeTimeout)
                && Objects.equals(this.connectionTimeout, that.connectionTimeout)
                && Objects.equals(this.uploadRetrySpec, that.uploadRetrySpec)
                && Objects.equals(this.specificAuthConfiguration, that.specificAuthConfiguration)
                && Objects.equals(this.ssecEnabled, that.ssecEnabled)
                && Objects.equals(this.ssecConfiguration, that.ssecConfiguration)
                && Objects.equals(this.fallbackNamespace, that.fallbackNamespace)
                && Objects.equals(this.multipartUploadThreshold, that.multipartUploadThreshold)
                && Objects.equals(this.multipartUploadPartSize, that.multipartUploadPartSize);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(namespace, bucketPrefix, httpConcurrency, specificAuthConfiguration,
            readTimeout, writeTimeout, connectionTimeout, uploadRetrySpec, ssecConfiguration, region,
            inMemoryReadLimit, ssecEnabled, fallbackNamespace, multipartUploadThreshold, multipartUploadPartSize);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("namespace", namespace)
            .add("httpConcurrency", httpConcurrency)
            .add("inMemoryReadLimit", inMemoryReadLimit)
            .add("bucketPrefix", bucketPrefix)
            .add("region", region)
            .add("specificAuthConfiguration", specificAuthConfiguration)
            .add("readTimeout", readTimeout)
            .add("writeTimeout", writeTimeout)
            .add("connectionTimeout", connectionTimeout)
            .add("uploadRetrySpec", uploadRetrySpec)
            .add("ssecEnabled", ssecEnabled)
            .add("ssecConfiguration", ssecConfiguration)
            .add("fallbackNamespace", fallbackNamespace)
            .add("multipartUploadThreshold", multipartUploadThreshold)
            .add("multipartUploadPartSize", multipartUploadPartSize)
            .toString();
    }
}
