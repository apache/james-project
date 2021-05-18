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

import java.util.Objects;
import java.util.Optional;

import org.apache.james.blob.api.BucketName;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

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

        class ReadyToBuild {

            private final AwsS3AuthConfiguration specificAuthConfiguration;

            private Optional<BucketName> defaultBucketName;
            private Optional<String> bucketPrefix;
            private Optional<Integer> httpConcurrency;
            private Region region;

            public ReadyToBuild(AwsS3AuthConfiguration specificAuthConfiguration, Region region) {
                this.specificAuthConfiguration = specificAuthConfiguration;
                this.region = region;
                this.defaultBucketName = Optional.empty();
                this.bucketPrefix = Optional.empty();
                this.httpConcurrency = Optional.empty();
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

            public ReadyToBuild bucketPrefix(String bucketPrefix) {
                this.bucketPrefix = Optional.ofNullable(bucketPrefix);
                return this;
            }

            public ReadyToBuild httpConcurrency(Optional<Integer> httpConcurrency) {
                this.httpConcurrency = httpConcurrency;
                return this;
            }

            public S3BlobStoreConfiguration build() {
                return new S3BlobStoreConfiguration(bucketPrefix, defaultBucketName, region, specificAuthConfiguration, httpConcurrency.orElse(DEFAULT_HTTP_CONCURRENCY));
            }
        }

    }

    public static int DEFAULT_HTTP_CONCURRENCY = 100;

    private final Region region;
    private final AwsS3AuthConfiguration specificAuthConfiguration;
    private final Optional<BucketName> namespace;
    private final Optional<String> bucketPrefix;
    private final int httpConcurrency;

    @VisibleForTesting
    S3BlobStoreConfiguration(Optional<String> bucketPrefix,
                             Optional<BucketName> namespace,
                             Region region,
                             AwsS3AuthConfiguration specificAuthConfiguration,
                             int httpConcurrency) {
        this.bucketPrefix = bucketPrefix;
        this.namespace = namespace;
        this.region = region;
        this.specificAuthConfiguration = specificAuthConfiguration;
        this.httpConcurrency = httpConcurrency;
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

    @Override
    public final boolean equals(Object o) {
        if (o instanceof S3BlobStoreConfiguration) {
            S3BlobStoreConfiguration that = (S3BlobStoreConfiguration) o;

            return Objects.equals(this.namespace, that.namespace)
                && Objects.equals(this.bucketPrefix, that.bucketPrefix)
                && Objects.equals(this.region, that.region)
                && Objects.equals(this.httpConcurrency, that.httpConcurrency)
                && Objects.equals(this.specificAuthConfiguration, that.specificAuthConfiguration);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(namespace, bucketPrefix, httpConcurrency, specificAuthConfiguration);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("namespace", namespace)
            .add("httpConcurrency", httpConcurrency)
            .add("bucketPrefix", bucketPrefix)
            .add("region", region)
            .add("specificAuthConfiguration", specificAuthConfiguration)
            .toString();
    }
}
