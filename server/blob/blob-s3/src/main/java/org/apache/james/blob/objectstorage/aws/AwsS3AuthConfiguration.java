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

import java.net.URI;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class AwsS3AuthConfiguration {

    public static Builder.RequiredEndpoint builder() {
        return endpoint -> accessKeyId -> secretKey -> new Builder.ReadyToBuild(endpoint, accessKeyId, secretKey);
    }

    public interface Builder {

        @FunctionalInterface
        interface RequiredEndpoint {
            RequiredAccessKeyId endpoint(URI endpoint);
        }

        @FunctionalInterface
        interface RequiredAccessKeyId {
            RequiredSecretKey accessKeyId(String accessKeyId);
        }

        @FunctionalInterface
        interface RequiredSecretKey {
            ReadyToBuild secretKey(String secretKey);
        }

        class ReadyToBuild {
            private final URI endpoint;
            private final String accessKeyId;
            private final String secretKey;
            private Optional<Boolean> trustAll;

            private Optional<String> trustStorePath;
            private Optional<String> trustStoreType;
            private Optional<String> trustStoreSecret;
            private Optional<String> trustStoreAlgorithm;

            public ReadyToBuild(URI endpoint, String accessKeyId, String secretKey) {
                this.endpoint = endpoint;
                this.accessKeyId = accessKeyId;
                this.secretKey = secretKey;
                this.trustStorePath = Optional.empty();
                this.trustStoreType = Optional.empty();
                this.trustStoreSecret = Optional.empty();
                this.trustStoreAlgorithm = Optional.empty();
                this.trustAll = Optional.empty();
            }

            public ReadyToBuild trustStorePath(Optional<String> trustStorePath) {
                this.trustStorePath = trustStorePath;
                return this;
            }

            public ReadyToBuild trustStorePath(String trustStorePath) {
                return trustStorePath(Optional.ofNullable(trustStorePath));
            }

            public ReadyToBuild trustStoreType(Optional<String> trustStoreType) {
                this.trustStoreType = trustStoreType;
                return this;
            }

            public ReadyToBuild trustStoreType(String trustStoreType) {
                return trustStoreType(Optional.ofNullable(trustStoreType));
            }

            public ReadyToBuild trustAll(boolean trustAll) {
                this.trustAll = Optional.of(trustAll);
                return this;
            }

            public ReadyToBuild trustStoreSecret(Optional<String> trustStoreSecret) {
                this.trustStoreSecret = trustStoreSecret;
                return this;
            }

            public ReadyToBuild trustStoreSecret(String trustStoreSecret) {
                return trustStoreSecret(Optional.ofNullable(trustStoreSecret));
            }

            public ReadyToBuild trustStoreAlgorithm(Optional<String> trustStoreAlgorithm) {
                this.trustStoreAlgorithm = trustStoreAlgorithm;
                return this;
            }

            public ReadyToBuild trustStoreAlgorithm(String trustStoreAlgorithm) {
                return trustStoreAlgorithm(Optional.ofNullable(trustStoreAlgorithm));
            }

            public AwsS3AuthConfiguration build() {
                Preconditions.checkNotNull(endpoint, "'endpoint' is mandatory");

                Preconditions.checkNotNull(accessKeyId, "'accessKeyId' is mandatory");
                Preconditions.checkArgument(!accessKeyId.isEmpty(), "'accessKeyId' is mandatory");

                Preconditions.checkNotNull(secretKey, "'secretKey' is mandatory");
                Preconditions.checkArgument(!secretKey.isEmpty(), "'secretKey' is mandatory");

                boolean trustAll = this.trustAll.orElse(false);
                Preconditions.checkState(!(trustAll && trustStoreType.isPresent()), "Cannot specify 'trustAll' and 'trustStoreType' simultaneously");
                Preconditions.checkState(!(trustAll && trustStorePath.isPresent()), "Cannot specify 'trustAll' and 'trustStorePath' simultaneously");
                Preconditions.checkState(!(trustAll && trustStoreSecret.isPresent()), "Cannot specify 'trustAll' and 'trustStoreSecret' simultaneously");

                return new AwsS3AuthConfiguration(endpoint, accessKeyId, secretKey,
                    trustStorePath, trustStoreType, trustStoreSecret, trustStoreAlgorithm, trustAll);
            }
        }
    }

    private final URI endpoint;
    private final String accessKeyId;
    private final String secretKey;

    private final Optional<String> trustStorePath;
    private final Optional<String> trustStoreType;
    private final Optional<String> trustStoreSecret;
    private final Optional<String> trustStoreAlgorithm;
    private final boolean trustAll;

    private AwsS3AuthConfiguration(URI endpoint,
                                   String accessKeyId,
                                   String secretKey,
                                   Optional<String> trustStorePath,
                                   Optional<String> trustStoreType,
                                   Optional<String> trustStoreSecret,
                                   Optional<String> trustStoreAlgorithm,
                                   boolean trustAll) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.secretKey = secretKey;
        this.trustStorePath = trustStorePath;
        this.trustStoreType = trustStoreType;
        this.trustStoreSecret = trustStoreSecret;
        this.trustStoreAlgorithm = trustStoreAlgorithm;
        this.trustAll = trustAll;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public Optional<String> getTrustStorePath() {
        return trustStorePath;
    }

    public Optional<String> getTrustStoreType() {
        return trustStoreType;
    }

    public Optional<String> getTrustStoreSecret() {
        return trustStoreSecret;
    }

    public Optional<String> getTrustStoreAlgorithm() {
        return trustStoreAlgorithm;
    }

    public boolean isTrustAll() {
        return trustAll;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof AwsS3AuthConfiguration) {
            AwsS3AuthConfiguration that = (AwsS3AuthConfiguration) o;
            return Objects.equal(endpoint, that.endpoint) &&
                Objects.equal(accessKeyId, that.accessKeyId) &&
                Objects.equal(secretKey, that.secretKey) &&
                Objects.equal(trustStorePath, that.trustStorePath) &&
                Objects.equal(trustStoreType, that.trustStoreType) &&
                Objects.equal(trustStoreSecret, that.trustStoreSecret) &&
                Objects.equal(trustStoreAlgorithm, that.trustStoreAlgorithm) &&
                Objects.equal(trustAll, that.trustAll);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(endpoint, accessKeyId, secretKey,
            trustStorePath, trustStoreType, trustStoreSecret, trustStoreAlgorithm,
            trustAll);
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this)
            .add("endpoint", endpoint)
            .add("accessKeyId", accessKeyId)
            .add("secretKey", secretKey)
            .add("trustStorePath", trustStorePath)
            .add("trustStoreSecret", trustStoreSecret)
            .add("trustStoreAlgorithm", trustStoreAlgorithm)
            .add("trustAll", trustAll)
            .toString();
    }
}
