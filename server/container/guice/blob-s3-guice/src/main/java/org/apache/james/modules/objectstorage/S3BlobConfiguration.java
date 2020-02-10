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

package org.apache.james.modules.objectstorage;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.Region;
import org.apache.james.modules.objectstorage.aws.s3.AwsS3ConfigurationReader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

public class S3BlobConfiguration {

    private static final String OBJECTSTORAGE_NAMESPACE = "objectstorage.namespace";
    private static final String OBJECTSTORAGE_BUCKET_PREFIX = "objectstorage.bucketPrefix";
    private static final String OBJECTSTORAGE_S3_REGION = "objectstorage.s3.region";

    static final String DEFAULT_BUCKET_PREFIX = "";

    public static S3BlobConfiguration from(Configuration configuration) throws ConfigurationException {
        Optional<String> namespace = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_NAMESPACE, null));
        Optional<String> bucketPrefix = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_BUCKET_PREFIX, null));
        Region region = Optional.ofNullable(configuration.getString(OBJECTSTORAGE_S3_REGION, null))
            .map(Region::of)
            .orElseThrow(() -> new ConfigurationException("require a region (" + OBJECTSTORAGE_S3_REGION + " key)"));

        return builder()
            .authConfiguration(authConfiguration(configuration))
            .region(region)
            .defaultBucketName(namespace.map(BucketName::of))
            .bucketPrefix(bucketPrefix)
            .build();
    }

    private static AwsS3AuthConfiguration authConfiguration(Configuration configuration) throws ConfigurationException {
        return AwsS3ConfigurationReader.from(configuration);
    }

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
            private Region region;

            public ReadyToBuild(AwsS3AuthConfiguration specificAuthConfiguration, Region region) {
                this.specificAuthConfiguration = specificAuthConfiguration;
                this.region = region;
                this.defaultBucketName = Optional.empty();
                this.bucketPrefix = Optional.empty();
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

            public S3BlobConfiguration build() {
                return new S3BlobConfiguration(bucketPrefix, defaultBucketName, region, specificAuthConfiguration);
            }
        }

    }

    private final Region region;
    private final AwsS3AuthConfiguration specificAuthConfiguration;
    private final Optional<BucketName> namespace;
    private final Optional<String> bucketPrefix;

    @VisibleForTesting
    S3BlobConfiguration(Optional<String> bucketPrefix,
                        Optional<BucketName> namespace,
                        Region region,
                        AwsS3AuthConfiguration specificAuthConfiguration) {
        this.bucketPrefix = bucketPrefix;
        this.namespace = namespace;
        this.region = region;
        this.specificAuthConfiguration = specificAuthConfiguration;
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

    public Region getRegion() {
        return region;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof S3BlobConfiguration) {
            S3BlobConfiguration that = (S3BlobConfiguration) o;

            return Objects.equals(this.namespace, that.namespace)
                && Objects.equals(this.bucketPrefix, that.bucketPrefix)
                && Objects.equals(this.region, that.region)
                && Objects.equals(this.specificAuthConfiguration, that.specificAuthConfiguration);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(namespace, bucketPrefix, specificAuthConfiguration);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("namespace", namespace)
            .add("bucketPrefix", bucketPrefix)
            .add("region", region)
            .add("specificAuthConfiguration", specificAuthConfiguration)
            .toString();
    }
}
